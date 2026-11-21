package micro.soporte.soporte_backend.dto.kafka;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Nuevo total de particiones de un tópico. Kafka solo permite aumentarlas, nunca disminuirlas.
 */
public record AgregarParticionesRequest(

        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe ser al menos 1")
        @Max(value = ValidacionKafka.MAX_PARTICIONES, message = "no puede superar " + ValidacionKafka.MAX_PARTICIONES)
        Integer total
) {
}
