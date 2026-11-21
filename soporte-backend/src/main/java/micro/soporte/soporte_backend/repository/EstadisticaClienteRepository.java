package micro.soporte.soporte_backend.repository;

import micro.soporte.soporte_backend.model.EstadisticaCliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EstadisticaClienteRepository extends JpaRepository<EstadisticaCliente, String> {

    List<EstadisticaCliente> findAllByOrderByTotalPedidosDesc();
}
