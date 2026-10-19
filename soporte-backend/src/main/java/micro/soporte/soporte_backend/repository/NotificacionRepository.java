package micro.soporte.soporte_backend.repository;

import micro.soporte.soporte_backend.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByClienteEmailOrderByFechaCreacionDesc(String clienteEmail);
}