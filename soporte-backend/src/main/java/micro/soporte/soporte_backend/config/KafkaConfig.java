package micro.soporte.soporte_backend.config;

import micro.soporte.soporte_backend.exception.EventoInvalidoException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;
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
 * Configuración de Kafka para Soporte.
 *
 * Soporte consume "pedidos.eventos" (analítica) y "pedidos.eventos.DLT" (tickets),
 * y además administra el cluster con AdminClient. Los tópicos de pedidos los declara
 * Pedidos, que es su dueño; aquí solo se guardan sus nombres.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    // ========== Tópicos que consume ==========
    public static final String PEDIDOS_EVENTOS_TOPIC = "pedidos.eventos";
    public static final String PEDIDOS_EVENTOS_DLT = "pedidos.eventos.DLT";
    public static final String SUFIJO_DLT = ".DLT";

    // ========== Grupos de consumidores ==========
    public static final String GRUPO_ANALITICA = "soporte-analitica";
    public static final String GRUPO_ERRORES = "soporte-errores";

    // ========== Reintentos: 1s, 2s y 4s antes de mandar el evento al DLT ==========
    private static final int MAX_REINTENTOS = 3;
    private static final long ESPERA_INICIAL_MS = 1000;
    private static final double MULTIPLICADOR = 2.0;

    /**
     * Manejo de errores de todos los @KafkaListener de Soporte.
     *
     * Si el listener lanza una excepción, el evento se reintenta 3 veces con espera creciente.
     * Si sigue fallando, el recuperador lo publica en "<tópico>.DLT", en la misma partición,
     * con cabeceras que explican el error. Los errores que no se arreglan reintentando
     * (evento inválido o JSON que no se puede leer) van directo al DLT.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                ProducerFactory<Object, Object> producerFactory) {
        // Si el JSON no se pudo leer, el valor original son bytes: se reenvían tal cual.
        // Si el error fue al procesar, el valor es el DTO y se reenvía como JSON.
        Map<Class<?>, KafkaOperations<?, ?>> templates = new LinkedHashMap<>();
        templates.put(byte[].class, new KafkaTemplate<>(producerFactory,
                Map.of(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class)));
        templates.put(Object.class, kafkaTemplate);

        DeadLetterPublishingRecoverer recuperador = new DeadLetterPublishingRecoverer(templates,
                (registro, error) -> {
                    // Un evento que ya viene del DLT no se vuelve a mandar al DLT (evita un ciclo)
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

    /**
     * Cliente de administración del cluster (crear, listar, modificar y borrar tópicos,
     * revisar grupos de consumidores). Usa la misma conexión que el resto de Kafka.
     */
    @Bean(destroyMethod = "close")
    public AdminClient kafkaAdminClient(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    private static Throwable causaRaiz(Throwable error) {
        Throwable actual = error;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        return actual;
    }
}
