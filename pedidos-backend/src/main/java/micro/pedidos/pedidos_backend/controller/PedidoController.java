package micro.pedidos.pedidos_backend.controller;

import micro.pedidos.pedidos_backend.messaging.PedidoEventPublisher;
import micro.pedidos.pedidos_backend.messaging.kafka.CatalogoSnapshot;
import micro.pedidos.pedidos_backend.messaging.kafka.PedidoKafkaPublisher;
import micro.pedidos.pedidos_backend.messaging.kafka.PlanActualizadoEvento;
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
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoRepository pedidoRepository;
    private final PedidoEventPublisher pedidoEventPublisher;
    private final PedidoKafkaPublisher pedidoKafkaPublisher;
    private final CatalogoSnapshot catalogoSnapshot;

    public PedidoController(PedidoRepository pedidoRepository,
                            PedidoEventPublisher pedidoEventPublisher,
                            PedidoKafkaPublisher pedidoKafkaPublisher,
                            CatalogoSnapshot catalogoSnapshot) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoEventPublisher = pedidoEventPublisher;
        this.pedidoKafkaPublisher = pedidoKafkaPublisher;
        this.catalogoSnapshot = catalogoSnapshot;
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
     * Crea un pedido, revisa que el plan tenga stock según la copia local del catálogo,
     * lo guarda en la base de datos y publica el evento:
     * - en RabbitMQ, para que Soporte lo procese con ACK y DLQ;
     * - en Kafka, como historial que leen la analítica de Soporte y el stock de Catálogo.
     */
    @PreAuthorize("hasAuthority('SCOPE_Pedidos.Create')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido crearPedido(@RequestBody PedidoRequest request, Authentication authentication) {
        validarPedido(request);
        Pedido pedido = new Pedido(
                extraerEmail(authentication),
                request.producto(),
                request.cantidad(),
                "PENDIENTE",
                LocalDateTime.now()
        );
        Pedido guardado = pedidoRepository.save(pedido);
        pedidoEventPublisher.publicarPedidoCreado(guardado);
        pedidoKafkaPublisher.publicarPedidoCreado(guardado);
        return guardado;
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    /**
     * Copia del catálogo que Pedidos tiene en memoria, armada desde el tópico "catalogo.planes".
     */
    @GetMapping("/catalogo-local")
    public List<PlanActualizadoEvento> catalogoLocal() {
        return catalogoSnapshot.listar();
    }

    /**
     * Rechaza el pedido con 400 si los datos vienen mal, y con 409 si el plan no está disponible
     * o no le queda stock suficiente. Si el producto no está en el catálogo (por ejemplo, un servicio
     * especial acordado con el cliente), se acepta: no hay stock que revisar.
     */
    private void validarPedido(PedidoRequest request) {
        if (request.producto() == null || request.producto().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El producto es obligatorio");
        }
        if (request.cantidad() == null || request.cantidad() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor a 0");
        }
        catalogoSnapshot.buscarPorNombre(request.producto()).ifPresent(plan -> {
            if (!Boolean.TRUE.equals(plan.disponible())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "El plan '" + plan.nombre() + "' no está disponible");
            }
            if (plan.stock() != null && plan.stock() < request.cantidad()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Stock insuficiente para '" + plan.nombre() + "': quedan " + plan.stock());
            }
        });
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