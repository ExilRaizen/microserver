package micro.soporte.soporte_backend.controller;

import micro.soporte.soporte_backend.model.EstadisticaCliente;
import micro.soporte.soporte_backend.model.EstadisticaPlan;
import micro.soporte.soporte_backend.service.AnaliticaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta de la analítica que arma el consumidor de Kafka.
 */
@RestController
@RequestMapping("/api/analitica")
public class AnaliticaController {

    private final AnaliticaService analiticaService;

    public AnaliticaController(AnaliticaService analiticaService) {
        this.analiticaService = analiticaService;
    }

    // Productos ordenados del más pedido al menos pedido
    @GetMapping("/planes")
    public List<EstadisticaPlan> porPlan() {
        return analiticaService.estadisticasPorPlan();
    }

    // Clientes ordenados del que más pedidos ha hecho al que menos
    @GetMapping("/clientes")
    public List<EstadisticaCliente> porCliente() {
        return analiticaService.estadisticasPorCliente();
    }
}
