package micro.soporte.soporte_backend.dto.kafka;

/**
 * Reglas de validación de nombres y parámetros de Kafka, compartidas por los DTO y endpoints.
 */
public final class ValidacionKafka {

    // Kafka acepta letras, números, punto, guion y guion bajo, hasta 249 caracteres
    public static final String PATRON_TOPIC = "^[a-zA-Z0-9._-]{1,249}$";
    public static final String MENSAJE_TOPIC =
            "solo puede tener letras, números, punto, guion o guion bajo (máximo 249)";

    // Nombre de un grupo de consumidores (mismo criterio, un poco más permisivo en largo)
    public static final String PATRON_GRUPO = "^[a-zA-Z0-9._-]{1,255}$";
    public static final String MENSAJE_GRUPO =
            "solo puede tener letras, números, punto, guion o guion bajo (máximo 255)";

    public static final String PATRON_CLEANUP = "(?i)(delete|compact|compact,delete|delete,compact)";
    public static final String MENSAJE_CLEANUP = "debe ser delete, compact o compact,delete";

    // Límites del cluster local: 3 brokers
    public static final int MAX_PARTICIONES = 12;
    public static final int MAX_REPLICAS = 3;

    private ValidacionKafka() {
    }
}
