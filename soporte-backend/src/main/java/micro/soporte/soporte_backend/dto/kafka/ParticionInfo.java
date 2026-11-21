package micro.soporte.soporte_backend.dto.kafka;

import java.util.List;

/**
 * Estado de una partición: qué broker es el líder, dónde están sus copias
 * y cuáles de esas copias están al día (ISR).
 */
public record ParticionInfo(int particion, Integer lider, List<Integer> replicas, List<Integer> isr) {
}
