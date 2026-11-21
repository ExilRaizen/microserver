package micro.soporte.soporte_backend.repository;

import micro.soporte.soporte_backend.model.EstadisticaPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstadisticaPlanRepository extends JpaRepository<EstadisticaPlan, String> {

    List<EstadisticaPlan> findAllByOrderByTotalPedidosDesc();
}
