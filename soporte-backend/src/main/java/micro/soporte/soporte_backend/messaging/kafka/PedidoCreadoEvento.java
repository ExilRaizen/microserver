package micro.soporte.soporte_backend.messaging.kafka;

import micro.soporte.soporte_backend.exception.EventoInvalidoException;

import java.time.LocalDateTime;

/**
 * DTO del evento que Pedidos publica en "pedidos.eventos".
 *
 * Es una copia del record de Pedidos: cada microservicio tiene su propia clase
 * y solo comparten el formato JSON, así no dependen uno del código del otro.
 */
public record PedidoCreadoEvento(
        String eventoId,
        Long pedidoId,
        String clienteEmail,
        String producto,
        Integer cantidad,
        LocalDateTime fechaCreacion
) {

    /**
     * Revisa que el evento tenga lo mínimo para procesarlo.
     * Si falta algo, reintentar no lo va a arreglar: se lanza una excepción no reintentable
     * y el evento va directo al tópico de errores.
     */
    public void validar() {
        if (eventoId == null || eventoId.isBlank()) {
            throw new EventoInvalidoException("El evento no tiene eventoId");
        }
        if (pedidoId == null) {
            throw new EventoInvalidoException("El evento " + eventoId + " no tiene pedidoId");
        }
        if (clienteEmail == null || clienteEmail.isBlank()) {
            throw new EventoInvalidoException("El pedido #" + pedidoId + " no tiene cliente");
        }
        if (producto == null || producto.isBlank()) {
            throw new EventoInvalidoException("El pedido #" + pedidoId + " no tiene producto");
        }
        if (cantidad == null || cantidad <= 0) {
            throw new EventoInvalidoException("El pedido #" + pedidoId + " tiene una cantidad inválida: " + cantidad);
        }
    }
}
