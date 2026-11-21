package micro.catalogo.catalogo_backend.config;

import micro.catalogo.catalogo_backend.exception.EventoInvalidoException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuración de Kafka para Catálogo.
 *
 *   catalogo.planes  -> estado actual de cada plan (Catálogo lo produce, Pedidos lo consume)
 *   pedidos.eventos  -> pedidos creados: Catálogo los consume para descontar stock
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    // ========== Tópicos ==========
    public static final String CATALOGO_PLANES_TOPIC = "catalogo.planes";
    public static final String PEDIDOS_EVENTOS_TOPIC = "pedidos.eventos";
    public static final String SUFIJO_DLT = ".DLT";

    // ========== Grupos de consumidores ==========
    public static final String GRUPO_STOCK = "catalogo-stock";

    // ========== Parámetros del tópico ==========
    private static final int PARTICIONES = 3;
    private static final int REPLICAS = 3;
    private static final String MIN_REPLICAS_SINCRONIZADAS = "2";
    private static final String UN_DIA_MS = String.valueOf(24L * 60 * 60 * 1000);
    private static final String UNA_HORA_MS = String.valueOf(60L * 60 * 1000);

    // ========== Reintentos: 1s, 2s y 4s antes de mandar el evento al DLT ==========
    private static final int MAX_REINTENTOS = 3;
    private static final long ESPERA_INICIAL_MS = 1000;
    private static final double MULTIPLICADOR = 2.0;

    /**
     * Tópico compactado: Kafka guarda al menos el último evento de cada key (el id del plan)
     * y con el tiempo borra los anteriores. Así el tópico funciona como una "tabla" con el
     * estado actual del catálogo, y quien lo lee desde el inicio reconstruye todos los planes.
     *
     * - segment.ms 1 hora: cierra segmentos seguido para que la compactación tenga qué limpiar.
     * - delete.retention.ms 1 día: cuánto se mantienen los tombstones (planes eliminados)
     *   para que los consumidores alcancen a enterarse del borrado.
     */
    @Bean
    public NewTopic catalogoPlanesTopic() {
        return TopicBuilder.name(CATALOGO_PLANES_TOPIC)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_REPLICAS_SINCRONIZADAS)
                .config(TopicConfig.SEGMENT_MS_CONFIG, UNA_HORA_MS)
                .config(TopicConfig.DELETE_RETENTION_MS_CONFIG, UN_DIA_MS)
                .config(TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG, "0.1")
                .build();
    }

    /**
     * Mismo manejo de errores que Soporte: 3 reintentos con espera creciente y después
     * el evento va a "pedidos.eventos.DLT", donde Soporte lo convierte en ticket.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                ProducerFactory<Object, Object> producerFactory) {
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, new KafkaTemplate<>(producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)));
        templates.put(Object.class, kafkaTemplate);

        DeadLetterPublishingRecoverer recuperador = new DeadLetterPublishingRecoverer(templates,
                (registro, error) -> {
                    if (registro.topic().endsWith(SUFIJO_DLT)) {
                        log.error("[KAFKA-DLT] No se pudo procesar el evento de {} y no se reenvía: {}",
                                registro.topic(), error.getMessage());
                        return null;
                    }
                    return new TopicPartition(registro.topic() + SUFIJO_DLT, registro.partition());
                });

        ExponentialBackOffWithMaxRetries espera = new ExponentialBackOffWithMaxRetries(MAX_REINTENTOS);
        espera.setInitialInterval(ESPERA_INICIAL_MS);
        espera.setMultiplier(MULTIPLICADOR);

        DefaultErrorHandler manejador = new DefaultErrorHandler(recuperador, espera);
        manejador.addNotRetryableExceptions(EventoInvalidoException.class);
        manejador.setRetryListeners((registro, error, intento) ->
                log.warn("[KAFKA-REINTENTO] Intento {} falló | {}-{} offset {} | {}",
                        intento, registro.topic(), registro.partition(), registro.offset(),
                        causaRaiz(error).getMessage()));
        return manejador;
    }

    private static Throwable causaRaiz(Throwable error) {
        Throwable actual = error;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        return actual;
    }
}
