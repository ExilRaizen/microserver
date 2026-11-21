package micro.soporte.soporte_backend.dto.kafka;

import java.util.List;

/**
 * Un consumidor dentro de un grupo y las particiones que Kafka le asignó.
 */
public record MiembroInfo(String clienteId, String host, List<String> particiones) {
}
