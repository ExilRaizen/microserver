package micro.soporte.soporte_backend.dto.kafka;

import java.util.List;
import java.util.Map;

public record TopicDetalle(String nombre, boolean interno, List<ParticionInfo> particiones, Map<String, String> configuracion) {
}
