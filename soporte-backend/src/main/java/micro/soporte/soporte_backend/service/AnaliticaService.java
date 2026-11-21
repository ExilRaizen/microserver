package micro.soporte.soporte_backend.service;

import micro.soporte.soporte_backend.messaging.kafka.PedidoCreadoEvento;
import micro.soporte.soporte_backend.model.EstadisticaCliente;
import micro.soporte.soporte_backend.model.EstadisticaPlan;
import micro.soporte.soporte_backend.model.EventoProcesado;
import micro.soporte.soporte_backend.repository.EstadisticaClienteRepository;
import micro.soporte.soporte_backend.repository.EstadisticaPlanRepository;
import micro.soporte.soporte_backend.repository.EventoProcesadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Analítica de ventas armada a partir de los eventos de Kafka.
 * Lleva dos estadísticas: por producto (qué se vende más) y por cliente (quién compra más).
 */
@Service
public class AnaliticaService {

    private final EstadisticaPlanRepository estadisticaPlanRepository;
    private final EstadisticaClienteRepository estadisticaClienteRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;

    public AnaliticaService(EstadisticaPlanRepository estadisticaPlanRepository,
                            EstadisticaClienteRepository estadisticaClienteRepository,
                            EventoProcesadoRepository eventoProcesadoRepository) {
        this.estadisticaPlanRepository = estadisticaPlanRepository;
        this.estadisticaClienteRepository = estadisticaClienteRepository;
        this.eventoProcesadoRepository = eventoProcesadoRepository;
    }

    /**
     * Suma un pedido a las estadísticas. Todo ocurre en una sola transacción:
     * si algo falla, no queda nada a medias y Kafka puede reintentar el evento completo.
     *
     * @return false si el evento ya se había procesado antes (duplicado)
     */
    @Transactional
    public boolean registrarPedido(PedidoCreadoEvento evento) {
        if (eventoProcesadoRepository.existsById(evento.eventoId())) {
            return false;
        }
        LocalDateTime fecha = evento.fechaCreacion() != null ? evento.fechaCreacion() : LocalDateTime.now();

        EstadisticaPlan plan = estadisticaPlanRepository.findById(evento.producto())
                .orElseGet(() -> new EstadisticaPlan(evento.producto()));
        plan.sumarPedido(evento.cantidad(), fecha);
        estadisticaPlanRepository.save(plan);

        EstadisticaCliente cliente = estadisticaClienteRepository.findById(evento.clienteEmail())
                .orElseGet(() -> new EstadisticaCliente(evento.clienteEmail()));
        cliente.sumarPedido(evento.producto(), evento.cantidad(), fecha);
        estadisticaClienteRepository.save(cliente);

        eventoProcesadoRepository.save(new EventoProcesado(evento.eventoId(), LocalDateTime.now()));
        return true;
    }

    @Transactional(readOnly = true)
    public List<EstadisticaPlan> estadisticasPorPlan() {
        return estadisticaPlanRepository.findAllByOrderByTotalPedidosDesc();
    }

    @Transactional(readOnly = true)
    public List<EstadisticaCliente> estadisticasPorCliente() {
        return estadisticaClienteRepository.findAllByOrderByTotalPedidosDesc();
    }
}
