package micro.soporte.soporte_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Resumen de compras de cada cliente: cuántos pedidos hizo, cuántas unidades
 * y cuál fue lo último que pidió. Sirve para saber a quién contactar si quiere algo especial.
 */
@Entity
@Table(name = "soporte_estadistica_cliente")
public class EstadisticaCliente {

    @Id
    private String clienteEmail;

    @Column(nullable = false)
    private Long totalPedidos;

    @Column(nullable = false)
    private Long unidadesCompradas;

    @Column(nullable = false)
    private String ultimoProducto;

    @Column(nullable = false)
    private LocalDateTime ultimoPedido;

    // Un mismo cliente puede pedir planes que caen en particiones distintas (hilos distintos)
    @Version
    private Long version;

    public EstadisticaCliente() {
    }

    public EstadisticaCliente(String clienteEmail) {
        this.clienteEmail = clienteEmail;
        this.totalPedidos = 0L;
        this.unidadesCompradas = 0L;
    }

    public void sumarPedido(String producto, int cantidad, LocalDateTime fecha) {
        this.totalPedidos++;
        this.unidadesCompradas += cantidad;
        this.ultimoProducto = producto;
        this.ultimoPedido = fecha;
    }

    public String getClienteEmail() {
        return clienteEmail;
    }

    public Long getTotalPedidos() {
        return totalPedidos;
    }

    public Long getUnidadesCompradas() {
        return unidadesCompradas;
    }

    public String getUltimoProducto() {
        return ultimoProducto;
    }

    public LocalDateTime getUltimoPedido() {
        return ultimoPedido;
    }
}
