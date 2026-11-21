package micro.catalogo.catalogo_backend.exception;

/**
 * Un evento de Kafka llegó con datos inválidos. No se reintenta: va directo al tópico de errores (DLT).
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
