package micro.pedidos.pedidos_backend.controller;

import micro.pedidos.pedidos_backend.messaging.Sender;
import micro.pedidos.pedidos_backend.model.Pedido;
import micro.pedidos.pedidos_backend.repository.PedidoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoRepository pedidoRepository;
    private final Sender sender;

    public PedidoController(PedidoRepository pedidoRepository, Sender sender) {
        this.pedidoRepository = pedidoRepository;
        this.sender = sender;
    }

    @GetMapping("/ping")
    public String ping() {
        return "Pedidos backend activo y autenticado";
    }

    @PreAuthorize("hasAuthority('SCOPE_Pedidos.Create')")
    @GetMapping("/crear")
    public String crear() {
        return "Tienes permiso para crear pedidos";
    }

    /**
     * Crea un pedido, lo guarda en la base de datos
     * y envía un mensaje a RabbitMQ usando el Sender
     */
    @PreAuthorize("hasAuthority('SCOPE_Pedidos.Create')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido crearPedido(@RequestBody PedidoRequest request, Authentication authentication) {
        Pedido pedido = new Pedido(
                extraerEmail(authentication),
                request.producto(),
                request.cantidad(),
                "PENDIENTE",
                LocalDateTime.now()
        );
        Pedido guardado = pedidoRepository.save(pedido);

        sender.sendMessage(String.format(
                "Pedido #%d | %s x%d | cliente: %s",
                guardado.getId(),
                guardado.getProducto(),
                guardado.getCantidad(),
                guardado.getClienteEmail()
        ));

        return guardado;
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
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