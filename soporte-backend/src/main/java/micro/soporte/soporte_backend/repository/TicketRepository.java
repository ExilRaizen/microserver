package micro.soporte.soporte_backend.repository;

import micro.soporte.soporte_backend.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
