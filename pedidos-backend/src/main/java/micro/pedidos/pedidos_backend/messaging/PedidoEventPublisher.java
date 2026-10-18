package micro.pedidos.pedidos_backend.messaging;

import micro.pedidos.pedidos_backend.config.RabbitMQConfig;
import micro.pedidos.pedidos_backend.model.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor del dominio de pedidos.
 *
 * Es la única clase de Pedidos que conoce RabbitMQ. El controller solo
 * llama a publicarPedidoCreado(...) y no sabe a qué exchange ni con qué
 * routing key se envía el mensaje.
 */
@Component
public class PedidoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PedidoEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publica el evento "pedido creado" con el formato:
     * pedidoId|clienteEmail|producto|cantidad
     *
     * Si RabbitMQ no está disponible, el pedido ya quedó guardado en la base de datos:
     * se registra el error y la petición del usuario no falla.
     */
    public void publicarPedidoCreado(Pedido pedido) {
        String mensaje = String.join(RabbitMQConfig.SEPARADOR_MENSAJE,
                String.valueOf(pedido.getId()),
                pedido.getClienteEmail(),
                pedido.getProducto(),
                String.valueOf(pedido.getCantidad()));

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PEDIDOS_EXCHANGE,
                    RabbitMQConfig.PEDIDO_CREADO_ROUTING_KEY,
                    mensaje,
                    message -> {
                        message.getMessageProperties().setMessageId("pedido-" + pedido.getId());
                        return message;
                    });
            log.info("Evento pedido.creado publicado: pedido #{} de {}", pedido.getId(), pedido.getClienteEmail());
        } catch (AmqpException e) {
            log.error("No se pudo publicar el evento del pedido #{}: {}", pedido.getId(), e.getMessage());
        }
    }
}