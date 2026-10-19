package micro.soporte.soporte_backend.exception;

/**
 * La cola o el exchange que se pidió no existe en RabbitMQ. Se responde con 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}