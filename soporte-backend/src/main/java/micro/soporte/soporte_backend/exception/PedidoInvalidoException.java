package micro.soporte.soporte_backend.exception;

/**
 * Error no recuperable: el pedido no se puede procesar aunque se reintente,
 * por ejemplo si el mensaje no cumple el formato o no respeta una regla de negocio.
 * Los mensajes con este error van directo a la DLQ, sin reintentos.
 */
public class PedidoInvalidoException extends RuntimeException {

    public PedidoInvalidoException(String mensaje) {
        super(mensaje);
    }
}