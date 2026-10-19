package micro.soporte.soporte_backend.service;

import micro.soporte.soporte_backend.model.Ticket;
import micro.soporte.soporte_backend.repository.TicketRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Crea tickets de soporte. Se usa cuando un pedido no se pudo procesar
 * y terminó en la DLQ, para que el equipo de soporte lo revise.
 */
@Service
public class TicketService {

    private static final String ASUNTO_PEDIDO_FALLIDO = "Pedido no procesado";
    private static final String ESTADO_INICIAL = "ABIERTO";

    private final TicketRepository ticketRepository;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public Ticket crearTicketPorPedidoFallido(String clienteEmail, String detalle) {
        Ticket ticket = new Ticket(
                clienteEmail,
                ASUNTO_PEDIDO_FALLIDO,
                detalle,
                ESTADO_INICIAL,
                LocalDateTime.now()
        );
        return ticketRepository.save(ticket);
    }
}