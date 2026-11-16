package micro.pedidos.pedidos_backend.messaging.kafka;

import java.time.LocalDateTime;

/**
 * Evento que Pedidos publica en "pedidos.eventos" cada vez que se crea un pedido.
 * Viaja como JSON. eventoId identifica el evento, por si un consumidor lo recibe dos veces.
 */
public record PedidoCreadoEvento(
        String eventoId,
        Long pedidoId,
        String clienteEmail,
        String producto,
        Integer cantidad,
        LocalDateTime fechaCreacion
) {
}