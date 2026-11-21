package micro.soporte.soporte_backend.exception;

/**
 * Kafka rechazó la operación por los datos enviados (por ejemplo, bajar particiones
 * o pedir más réplicas que brokers). Se responde con 400.
 */
public class SolicitudKafkaInvalidaException extends RuntimeException {

    public SolicitudKafkaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
