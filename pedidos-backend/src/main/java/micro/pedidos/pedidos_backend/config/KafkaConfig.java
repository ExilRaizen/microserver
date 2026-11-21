package micro.pedidos.pedidos_backend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configuración centralizada de Kafka para Pedidos.
 *
 * Pedidos es dueño del flujo "pedidos.eventos", así que declara sus tópicos.
 * Al arrancar, Spring los crea en el cluster si no existen.
 *
 *   pedidos.eventos      -> historial de pedidos creados (lo leen Soporte y Catálogo)
 *   pedidos.eventos.DLT  -> eventos que un consumidor no pudo procesar
 *
 * Además consume "catalogo.planes" (lo declara Catálogo) para tener una copia local de los planes.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    // ========== Tópicos ==========
    public static final String PEDIDOS_EVENTOS_TOPIC = "pedidos.eventos";
    public static final String PEDIDOS_EVENTOS_DLT = "pedidos.eventos.DLT";
    public static final String CATALOGO_PLANES_TOPIC = "catalogo.planes";

    // ========== Grupos de consumidores ==========
    public static final String GRUPO_CATALOGO = "pedidos-catalogo";

    // ========== Parámetros comunes ==========
    private static final int PARTICIONES = 3;
    private static final int REPLICAS = 3;
    private static final String MIN_REPLICAS_SINCRONIZADAS = "2";

    // ========== Retención por flujo ==========
    private static final String RETENCION_7_DIAS = String.valueOf(7L * 24 * 60 * 60 * 1000);
    private static final String RETENCION_30_DIAS = String.valueOf(30L * 24 * 60 * 60 * 1000);

    /**
     * Eventos de pedidos: 3 particiones para que hasta 3 consumidores de un mismo grupo
     * se repartan el trabajo. Se borran a los 7 días: es un historial, no el registro oficial
     * (ese está en la base de datos).
     */
    @Bean
    public NewTopic pedidosEventosTopic() {
        return TopicBuilder.name(PEDIDOS_EVENTOS_TOPIC)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENCION_7_DIAS)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_REPLICAS_SINCRONIZADAS)
                .build();
    }

    /**
     * Tópico de errores: mismas particiones que el original, porque el recuperador de errores
     * reenvía cada evento fallido a la misma partición. Se guardan 30 días para alcanzar a revisarlos.
     */
    @Bean
    public NewTopic pedidosEventosDltTopic() {
        return TopicBuilder.name(PEDIDOS_EVENTOS_DLT)
                .partitions(PARTICIONES)
                .replicas(REPLICAS)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENCION_30_DIAS)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_REPLICAS_SINCRONIZADAS)
                .build();
    }

    /**
     * Manejo de errores del consumidor de "catalogo.planes": 2 reintentos con 1 segundo de espera.
     * Si sigue fallando, el evento se registra en el log y se salta. No hace falta un DLT:
     * el tópico es compactado, así que el próximo cambio de ese plan trae su estado completo.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler manejador = new DefaultErrorHandler(
                (registro, error) -> log.error("[KAFKA] Se omite el evento {}-{} offset {}: {}",
                        registro.topic(), registro.partition(), registro.offset(), error.getMessage()),
                new FixedBackOff(1000L, 2L));
        manejador.setRetryListeners((registro, error, intento) ->
                log.warn("[KAFKA-REINTENTO] Intento {} falló | {}-{} offset {}",
                        intento, registro.topic(), registro.partition(), registro.offset()));
        return manejador;
    }
}
