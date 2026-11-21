package micro.soporte.soporte_backend.dto.kafka;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Datos para crear un tópico. Solo el nombre es obligatorio: el resto tiene valores por defecto.
 */
public record CrearTopicRequest(

        @NotNull(message = "es obligatorio")
        @Pattern(regexp = ValidacionKafka.PATRON_TOPIC, message = ValidacionKafka.MENSAJE_TOPIC)
        String nombre,

        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = ValidacionKafka.MAX_PARTICIONES, message = "no puede superar " + ValidacionKafka.MAX_PARTICIONES)
        Integer particiones,

        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = ValidacionKafka.MAX_REPLICAS, message = "no puede superar la cantidad de brokers (" + ValidacionKafka.MAX_REPLICAS + ")")
        Integer replicas,

        @Pattern(regexp = ValidacionKafka.PATRON_CLEANUP, message = ValidacionKafka.MENSAJE_CLEANUP)
        String cleanupPolicy,

        // -1 significa "sin límite de tiempo"
        @Min(value = -1, message = "debe ser -1 (sin límite) o un número positivo de milisegundos")
        Long retentionMs
) {
    public int particionesOPorDefecto() {
        return particiones != null ? particiones : 3;
    }

    public short replicasOPorDefecto() {
        return (short) (replicas != null ? replicas : 3);
    }
}
