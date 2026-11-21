package micro.catalogo.catalogo_backend.repository;

import micro.catalogo.catalogo_backend.model.EventoProcesado;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, String> {
}
