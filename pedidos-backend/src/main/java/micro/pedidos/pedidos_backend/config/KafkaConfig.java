package micro.pedidos.pedidos_backend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuración centralizada de Kafka para Pedidos.
 *
 * Pedidos es dueño del flujo "pedidos.eventos", así que declara sus tópicos.
 * Al arrancar, Spring los crea en el cluster si no existen.
 *
 *   pedidos.eventos      -> historial de pedidos creados (lo leen Soporte y Catálogo)
 *   pedidos.eventos.DLT  -> eventos que un consumidor no pudo procesar
 */
@Configuration
public class KafkaConfig {

    // ========== Tópicos ==========
    public static final String PEDIDOS_EVENTOS_TOPIC = "pedidos.eventos";
    public static final String PEDIDOS_EVENTOS_DLT = "pedidos.eventos.DLT";

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
}