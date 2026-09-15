package micro.soporte.soporte_backend.controller;

import micro.soporte.soporte_backend.model.Ticket;
import micro.soporte.soporte_backend.repository.TicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/soporte")
public class TicketController {

    private final TicketRepository ticketRepository;

    public TicketController(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @PreAuthorize("hasAuthority('SCOPE_Soporte.Create')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket crear(@RequestBody TicketRequest request, Authentication authentication) {
        Ticket ticket = new Ticket(
                extraerEmail(authentication),
                request.asunto(),
                request.mensaje(),
                "ABIERTO",
                LocalDateTime.now()
        );
        return ticketRepository.save(ticket);
    }

    @GetMapping
    public List<Ticket> listar() {
        return ticketRepository.findAll();
    }

    @GetMapping("/{id}")
    public Ticket obtener(@PathVariable Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket no encontrado"));
    }

    private String extraerEmail(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            String email = jwt.getClaimAsString("preferred_username");
            if (email == null) {
                email = jwt.getClaimAsString("upn");
            }
            if (email == null) {
                email = jwt.getClaimAsString("email");
            }
            return email != null ? email : "desconocido";
        }
        return "desconocido";
    }
}
