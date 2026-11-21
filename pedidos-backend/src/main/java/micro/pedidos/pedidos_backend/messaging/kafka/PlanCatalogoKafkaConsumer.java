package micro.pedidos.pedidos_backend.messaging.kafka;

import micro.pedidos.pedidos_backend.config.KafkaConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.PartitionOffset;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.stereotype.Component;

/**
 * Consumidor de "catalogo.planes" que arma la copia local del catálogo en Pedidos.
 *
 * La copia vive en memoria, así que se pierde al reiniciar Pedidos y hay que releer el tópico completo.
 * Por eso este consumidor no se une a un grupo para que Kafka le reparta particiones: se asigna
 * directamente las 3 particiones y parte siempre desde el offset 0. Así no espera ningún rebalanceo
 * y el catálogo queda cargado apenas arranca el servicio. Como el tópico está compactado,
 * releerlo es básicamente un evento por plan.
 *
 * El groupId "pedidos-catalogo" solo se usa para guardar los offsets leídos (se ven en Kafka-UI).
 */
@Component
public class PlanCatalogoKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PlanCatalogoKafkaConsumer.class);

    private final CatalogoSnapshot catalogoSnapshot;

    public PlanCatalogoKafkaConsumer(CatalogoSnapshot catalogoSnapshot) {
        this.catalogoSnapshot = catalogoSnapshot;
    }

    @KafkaListener(
            id = "pedidos-catalogo-listener",
            groupId = KafkaConfig.GRUPO_CATALOGO,
            topicPartitions = @TopicPartition(
                    topic = KafkaConfig.CATALOGO_PLANES_TOPIC,
                    partitions = "0-2",
                    partitionOffsets = @PartitionOffset(partition = "*", initialOffset = "0")
            )
    )
    public void recibir(ConsumerRecord<String, PlanActualizadoEvento> registro) {
        String planId = registro.key();
        PlanActualizadoEvento plan = registro.value();

        // Tombstone: evento sin valor = el plan se eliminó del catálogo
        if (plan == null) {
            catalogoSnapshot.eliminar(planId);
            log.info("[KAFKA] Plan #{} eliminado del catálogo local | partición {} | offset {}",
                    planId, registro.partition(), registro.offset());
            return;
        }

        catalogoSnapshot.actualizar(planId, plan);
        log.info("[KAFKA] Catálogo local: '{}' stock {} disponible {} | partición {} | offset {} | {} planes en memoria",
                plan.nombre(), plan.stock(), plan.disponible(), registro.partition(), registro.offset(),
                catalogoSnapshot.cantidad());
    }
}
