package micro.catalogo.catalogo_backend.service;

import micro.catalogo.catalogo_backend.messaging.kafka.PedidoCreadoEvento;
import micro.catalogo.catalogo_backend.model.EventoProcesado;
import micro.catalogo.catalogo_backend.model.PlanHosting;
import micro.catalogo.catalogo_backend.repository.EventoProcesadoRepository;
import micro.catalogo.catalogo_backend.repository.PlanHostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Descuenta stock de los planes según los pedidos que llegan por Kafka.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final PlanHostingRepository planHostingRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;

    public StockService(PlanHostingRepository planHostingRepository,
                        EventoProcesadoRepository eventoProcesadoRepository) {
        this.planHostingRepository = planHostingRepository;
        this.eventoProcesadoRepository = eventoProcesadoRepository;
    }

    /**
     * Descuenta la cantidad pedida del plan, en una sola transacción.
     *
     * @return el plan actualizado, o vacío si el evento ya se había procesado
     *         o si el producto no es un plan del catálogo (por ejemplo, un servicio especial)
     */
    @Transactional
    public Optional<PlanHosting> descontarStock(PedidoCreadoEvento evento) {
        if (eventoProcesadoRepository.existsById(evento.eventoId())) {
            log.info("[STOCK] Evento {} ya estaba procesado, se ignora el duplicado", evento.eventoId());
            return Optional.empty();
        }
        eventoProcesadoRepository.save(new EventoProcesado(evento.eventoId(), LocalDateTime.now()));

        Optional<PlanHosting> encontrado = planHostingRepository.findFirstByNombreIgnoreCase(evento.producto());
        if (encontrado.isEmpty()) {
            log.info("[STOCK] '{}' no es un plan del catálogo, no se descuenta stock", evento.producto());
            return Optional.empty();
        }

        PlanHosting plan = encontrado.get();
        int stockAnterior = plan.getStock();
        plan.descontarStock(evento.cantidad());
        planHostingRepository.save(plan);

        log.info("[STOCK] Plan '{}': stock {} -> {} (pedido #{}){}",
                plan.getNombre(), stockAnterior, plan.getStock(), evento.pedidoId(),
                plan.getStock() == 0 ? " | SIN STOCK, se marca como no disponible" : "");
        return Optional.of(plan);
    }
}
