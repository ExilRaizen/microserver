package micro.pedidos.pedidos_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @GetMapping("/ping")
    public String ping() {
        return "Pedidos backend activo y autenticado";
    }

    @PreAuthorize("hasAuthority('SCOPE_Pedidos.Create')")
    @GetMapping("/crear")
    public String crear() {
        return "Tienes permiso para crear pedidos";
    }
}