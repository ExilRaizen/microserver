package micro.soporte.soporte_backend.exception;

/**
 * Kafka no respondió a tiempo o la operación falló por un problema del cluster. Se responde con 503.
 */
public class KafkaNoDisponibleException extends RuntimeException {

    public KafkaNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
