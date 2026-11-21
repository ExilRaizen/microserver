package micro.soporte.soporte_backend.dto.kafka;

import java.util.List;

public record GrupoDetalle(
        String grupoId,
        String estado,
        String asignador,
        Integer coordinador,
        List<MiembroInfo> miembros,
        List<LagParticion> lag,
        long lagTotal
) {
}
