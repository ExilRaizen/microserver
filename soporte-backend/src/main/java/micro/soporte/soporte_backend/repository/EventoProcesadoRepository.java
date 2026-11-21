package micro.soporte.soporte_backend.repository;

import micro.soporte.soporte_backend.model.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, String> {
}
