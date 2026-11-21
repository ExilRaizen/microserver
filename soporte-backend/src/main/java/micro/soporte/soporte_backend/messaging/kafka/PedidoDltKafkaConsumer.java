package micro.soporte.soporte_backend.messaging.kafka;

import micro.soporte.soporte_backend.config.KafkaConfig;
import micro.soporte.soporte_backend.model.Ticket;
import micro.soporte.soporte_backend.service.TicketService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Consumidor del tópico de errores "pedidos.eventos.DLT".
 *
 * Cada evento que llega aquí es uno que Soporte o Catálogo no pudieron procesar.
 * Se lee como texto (no como DTO) porque puede ser un JSON roto, y por cada uno
 * se abre un ticket para que el equipo de soporte lo revise.
 */
@Component
public class PedidoDltKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoDltKafkaConsumer.class);
    private static final String SIN_DATO = "desconocido";

    private final TicketService ticketService;

    public PedidoDltKafkaConsumer(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @KafkaListener(
            id = "soporte-errores-listener",
            topics = KafkaConfig.PEDIDOS_EVENTOS_DLT,
            groupId = KafkaConfig.GRUPO_ERRORES,
            concurrency = "1",
            properties = "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
    )
    public void recibir(ConsumerRecord<String, String> registro) {
        String grupoOrigen = leerCabecera(registro, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP);
        String error = leerCabecera(registro, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        String detalle = "Evento de Kafka no procesado"
                + " | grupo: " + grupoOrigen
                + " | origen: " + leerCabecera(registro, KafkaHeaders.DLT_ORIGINAL_TOPIC)
                + "-" + registro.partition()
                + " | error: " + error
                + " | contenido: " + registro.value();

        log.warn("[KAFKA-DLT] Evento fallido recibido | grupo {} | partición {} | offset {} | error: {}",
                grupoOrigen, registro.partition(), registro.offset(), error);
        try {
            Ticket ticket = ticketService.crearTicketPorEventoKafkaFallido(recortar(detalle));
            log.info("[KAFKA-DLT] Ticket #{} creado para revisar el evento", ticket.getId());
        } catch (RuntimeException e) {
            // Si ni siquiera se puede crear el ticket, se deja en el log y se sigue con el próximo
            log.error("[KAFKA-DLT] No se pudo crear el ticket: {} | detalle: {}", e.getMessage(), detalle);
        }
    }

    private String leerCabecera(ConsumerRecord<String, String> registro, String nombre) {
        Header cabecera = registro.headers().lastHeader(nombre);
        if (cabecera == null || cabecera.value() == null) {
            return SIN_DATO;
        }
        return new String(cabecera.value(), StandardCharsets.UTF_8);
    }

    private String recortar(String texto) {
        // La columna "mensaje" del ticket acepta hasta 2000 caracteres
        return texto.length() > 2000 ? texto.substring(0, 1997) + "..." : texto;
    }
}
