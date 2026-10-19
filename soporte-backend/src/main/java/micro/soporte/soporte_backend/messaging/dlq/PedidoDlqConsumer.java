package micro.soporte.soporte_backend.messaging.dlq;

import com.rabbitmq.client.Channel;
import micro.soporte.soporte_backend.config.RabbitListenerConfig;
import micro.soporte.soporte_backend.config.RabbitMQConfig;
import micro.soporte.soporte_backend.model.Ticket;
import micro.soporte.soporte_backend.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Consumidor de la DLQ de pedidos.
 *
 * Registra en los logs cada pedido que no se pudo procesar
 * y crea un ticket automático para que soporte lo revise.
 */
@Component
public class PedidoDlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoDlqConsumer.class);
    private static final String CLIENTE_DESCONOCIDO = "desconocido";
    private static final long ESPERA_ANTES_DE_REINTENTAR_MS = 5000;

    private final TicketService ticketService;

    public PedidoDlqConsumer(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @RabbitListener(
            id = RabbitMQConfig.PEDIDOS_DLQ_LISTENER_ID,
            queues = RabbitMQConfig.PEDIDOS_CREADOS_DLQ,
            containerFactory = RabbitListenerConfig.MANUAL_ACK_FACTORY
    )
    public void recibirPedidoFallido(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String contenido = new String(message.getBody(), StandardCharsets.UTF_8);

        log.error("[DLQ] Mensaje no procesado recibido: '{}'", contenido);

        try {
            Ticket ticket = ticketService.crearTicketPorPedidoFallido(
                    extraerClienteEmail(contenido),
                    "Revisar el mensaje que no se pudo procesar: " + contenido);

            channel.basicAck(deliveryTag, false);
            log.info("[DLQ -> TICKET] Ticket #{} creado para revisión", ticket.getId());

        } catch (Exception e) {
            // Si no se pudo crear el ticket, el mensaje vuelve a la DLQ para no perderlo
            log.error("[DLQ] No se pudo crear el ticket, el mensaje se mantiene en la DLQ: {}", e.getMessage());
            esperar();
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private String extraerClienteEmail(String contenido) {
        String[] campos = contenido.split(Pattern.quote(RabbitMQConfig.SEPARADOR_MENSAJE), -1);
        if (campos.length >= 2 && !campos[1].isBlank()) {
            return campos[1].trim();
        }
        return CLIENTE_DESCONOCIDO;
    }

    private void esperar() {
        try {
            Thread.sleep(ESPERA_ANTES_DE_REINTENTAR_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}