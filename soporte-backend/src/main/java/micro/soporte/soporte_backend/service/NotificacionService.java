package micro.soporte.soporte_backend.service;

import micro.soporte.soporte_backend.exception.PedidoInvalidoException;
import micro.soporte.soporte_backend.messaging.pedidos.PedidoCreadoMensaje;
import micro.soporte.soporte_backend.model.Notificacion;
import micro.soporte.soporte_backend.repository.NotificacionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Lógica de negocio cuando se crea un pedido.
 * No conoce RabbitMQ: recibe los datos ya leídos desde el consumidor.
 */
@Service
public class NotificacionService {

    // Regla de negocio: un pedido puede incluir de 1 a 10 servidores
    private static final int CANTIDAD_MINIMA = 1;
    private static final int CANTIDAD_MAXIMA = 10;

    private final NotificacionRepository notificacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository) {
        this.notificacionRepository = notificacionRepository;
    }

    public Notificacion registrarPedidoCreado(PedidoCreadoMensaje pedido) {
        if (pedido.cantidad() < CANTIDAD_MINIMA || pedido.cantidad() > CANTIDAD_MAXIMA) {
            throw new PedidoInvalidoException("La cantidad " + pedido.cantidad()
                    + " está fuera del rango permitido (" + CANTIDAD_MINIMA + " a " + CANTIDAD_MAXIMA + ")");
        }

        String mensaje = "Tu pedido #" + pedido.pedidoId() + " de " + pedido.cantidad()
                + " x " + pedido.producto() + " fue recibido y está en preparación.";

        return notificacionRepository.save(
                new Notificacion(pedido.pedidoId(), pedido.clienteEmail(), mensaje, LocalDateTime.now()));
    }
}