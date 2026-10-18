package micro.soporte.soporte_backend.messaging.pedidos;

import com.rabbitmq.client.Channel;
import micro.soporte.soporte_backend.config.RabbitListenerConfig;
import micro.soporte.soporte_backend.config.RabbitMQConfig;
import micro.soporte.soporte_backend.exception.PedidoInvalidoException;
import micro.soporte.soporte_backend.model.Notificacion;
import micro.soporte.soporte_backend.service.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Consumidor del dominio de pedidos.
 *
 * Confirma cada mensaje a mano y decide qué hacer según el resultado:
 * - Procesado bien                -> ACK
 * - Pedido inválido (no recuperable) -> NACK sin reencolar -> DLQ
 * - Error técnico, primer intento  -> NACK reencolando (se reintenta una vez)
 * - Error técnico, ya reintentado  -> NACK sin reencolar -> DLQ
 */
@Component
public class PedidoCreadoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoCreadoConsumer.class);

    private final NotificacionService notificacionService;

    public PedidoCreadoConsumer(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @RabbitListener(
            id = RabbitMQConfig.PEDIDOS_CREADOS_LISTENER_ID,
            queues = RabbitMQConfig.PEDIDOS_CREADOS_QUEUE,
            containerFactory = RabbitListenerConfig.MANUAL_ACK_FACTORY
    )
    public void recibirPedidoCreado(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        boolean esReintento = Boolean.TRUE.equals(message.getMessageProperties().getRedelivered());
        String contenido = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            PedidoCreadoMensaje pedido = PedidoCreadoMensaje.desdeTexto(contenido);
            Notificacion notificacion = notificacionService.registrarPedidoCreado(pedido);

            channel.basicAck(deliveryTag, false);
            log.info("[ACK] Pedido #{} procesado, notificación #{} creada para {}",
                    pedido.pedidoId(), notificacion.getId(), pedido.clienteEmail());

        } catch (PedidoInvalidoException e) {
            // No recuperable: reintentar no sirve, va directo a la DLQ
            channel.basicNack(deliveryTag, false, false);
            log.warn("[NACK -> DLQ] Pedido inválido: {} | mensaje: '{}'", e.getMessage(), contenido);

        } catch (Exception e) {
            if (!esReintento) {
                // Error técnico: puede ser pasajero, se reintenta una vez
                channel.basicNack(deliveryTag, false, true);
                log.warn("[NACK -> REINTENTO] Error técnico, se reintenta una vez: {}", e.getMessage());
            } else {
                // Ya se reintentó y volvió a fallar: va a la DLQ
                channel.basicNack(deliveryTag, false, false);
                log.error("[NACK -> DLQ] Falló también el reintento: {} | mensaje: '{}'", e.getMessage(), contenido);
            }
        }
    }
}