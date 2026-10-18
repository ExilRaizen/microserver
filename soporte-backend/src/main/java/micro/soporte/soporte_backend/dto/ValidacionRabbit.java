package micro.soporte.soporte_backend.dto;

/**
 * Reglas de validación de nombres de RabbitMQ, compartidas por todos los DTO y endpoints.
 */
public final class ValidacionRabbit {

    // Letras, números, punto, guion y guion bajo, hasta 255 caracteres.
    // No puede empezar con "amq." porque RabbitMQ reserva ese prefijo.
    public static final String PATRON_NOMBRE = "^(?!amq\\.)[a-zA-Z0-9._-]{1,255}$";
    public static final String MENSAJE_NOMBRE =
            "solo puede tener letras, números, punto, guion o guion bajo (máximo 255) y no puede empezar con 'amq.'";

    public static final String PATRON_TIPO_EXCHANGE = "(?i)(direct|topic|fanout|headers)";
    public static final String MENSAJE_TIPO_EXCHANGE = "debe ser direct, topic, fanout o headers";

    private ValidacionRabbit() {
    }
}