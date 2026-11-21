package micro.soporte.soporte_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Cuántos pedidos y unidades se han vendido de cada producto.
 * Se agrupa por el nombre del producto, así sirve para los planes del catálogo
 * y también para productos especiales que se acuerdan aparte con el cliente.
 */
@Entity
@Table(name = "soporte_estadistica_plan")
public class EstadisticaPlan {

    @Id
    private String producto;

    @Column(nullable = false)
    private Long totalPedidos;

    @Column(nullable = false)
    private Long unidadesVendidas;

    @Column(nullable = false)
    private LocalDateTime ultimoPedido;

    // Bloqueo optimista: si dos hilos actualizan la misma fila a la vez, uno falla y se reintenta
    @Version
    private Long version;

    public EstadisticaPlan() {
    }

    public EstadisticaPlan(String producto) {
        this.producto = producto;
        this.totalPedidos = 0L;
        this.unidadesVendidas = 0L;
    }

    public void sumarPedido(int cantidad, LocalDateTime fecha) {
        this.totalPedidos++;
        this.unidadesVendidas += cantidad;
        this.ultimoPedido = fecha;
    }

    public String getProducto() {
        return producto;
    }

    public Long getTotalPedidos() {
        return totalPedidos;
    }

    public Long getUnidadesVendidas() {
        return unidadesVendidas;
    }

    public LocalDateTime getUltimoPedido() {
        return ultimoPedido;
    }
}
