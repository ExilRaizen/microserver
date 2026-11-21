package micro.soporte.soporte_backend.dto.kafka;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Configuraciones de un tópico que se pueden cambiar en caliente. Se envían solo las que se quieren cambiar.
 */
public record ModificarConfigRequest(

        @Min(value = -1, message = "debe ser -1 (sin límite) o un número positivo de milisegundos")
        Long retentionMs,

        @Pattern(regexp = ValidacionKafka.PATRON_CLEANUP, message = ValidacionKafka.MENSAJE_CLEANUP)
        String cleanupPolicy,

        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = ValidacionKafka.MAX_REPLICAS, message = "no puede superar la cantidad de brokers (" + ValidacionKafka.MAX_REPLICAS + ")")
        Integer minInsyncReplicas
) {
    public boolean estaVacio() {
        return retentionMs == null && cleanupPolicy == null && minInsyncReplicas == null;
    }
}
