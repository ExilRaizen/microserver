package micro.pedidos.pedidos_backend.messaging.kafka;

import micro.pedidos.pedidos_backend.config.KafkaConfig;
import micro.pedidos.pedidos_backend.model.Pedido;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Productor Kafka del dominio de pedidos.
 *
 * Es la única clase de Pedidos que conoce Kafka. Publica cada pedido creado
 * en "pedidos.eventos" usando el plan como key: así todos los eventos de un mismo plan
 * caen en la misma partición y se procesan en orden, lo que importa para descontar su stock.
 */
@Component
public class PedidoKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoKafkaPublisher.class);

    private final KafkaTemplate<String, PedidoCreadoEvento> kafkaTemplate;

    public PedidoKafkaPublisher(KafkaTemplate<String, PedidoCreadoEvento> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * El envío es asíncrono: el resultado se registra en el log cuando Kafka confirma.
     * Si Kafka no está disponible, el pedido ya quedó guardado y la petición del usuario no falla.
     */
    public void publicarPedidoCreado(Pedido pedido) {
        PedidoCreadoEvento evento = new PedidoCreadoEvento(
                UUID.randomUUID().toString(),
                pedido.getId(),
                pedido.getClienteEmail(),
                pedido.getProducto(),
                pedido.getCantidad(),
                pedido.getFechaCreacion()
        );
        String key = pedido.getProducto();

        try {
            kafkaTemplate.send(KafkaConfig.PEDIDOS_EVENTOS_TOPIC, key, evento)
                    .whenComplete((resultado, error) -> {
                        if (error != null) {
                            log.error("[KAFKA] No se pudo publicar el pedido #{}: {}",
                                    pedido.getId(), error.getMessage());
                            return;
                        }
                        RecordMetadata metadata = resultado.getRecordMetadata();
                        log.info("[KAFKA] Pedido #{} publicado en {} | partición {} | offset {} | key '{}'",
                                pedido.getId(), metadata.topic(), metadata.partition(), metadata.offset(), key);
                    });
        } catch (RuntimeException e) {
            log.error("[KAFKA] Error al enviar el pedido #{}: {}", pedido.getId(), e.getMessage());
        }
    }
}