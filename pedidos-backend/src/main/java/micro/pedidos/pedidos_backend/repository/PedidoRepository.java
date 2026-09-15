package micro.pedidos.pedidos_backend.repository;

import micro.pedidos.pedidos_backend.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}
