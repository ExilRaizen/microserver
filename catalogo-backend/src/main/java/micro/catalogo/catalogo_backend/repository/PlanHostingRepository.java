package micro.catalogo.catalogo_backend.repository;

import micro.catalogo.catalogo_backend.model.PlanHosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlanHostingRepository extends JpaRepository<PlanHosting, Long> {

    // Los pedidos guardan el nombre del plan como producto
    Optional<PlanHosting> findFirstByNombreIgnoreCase(String nombre);
}
