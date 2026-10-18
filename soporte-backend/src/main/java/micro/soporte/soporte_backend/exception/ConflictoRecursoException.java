package micro.soporte.soporte_backend.exception;

/**
 * El recurso ya existe o está protegido porque lo usa el sistema. Se responde con 409.
 */
public class ConflictoRecursoException extends RuntimeException {

    public ConflictoRecursoException(String mensaje) {
        super(mensaje);
    }
}