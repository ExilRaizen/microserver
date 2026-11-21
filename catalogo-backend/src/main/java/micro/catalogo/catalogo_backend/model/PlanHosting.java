package micro.catalogo.catalogo_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class PlanHosting {

    public static final int STOCK_POR_DEFECTO = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String tipoServidor;

    @Column(nullable = false)
    private Integer ram;

    @Column(nullable = false)
    private Integer slots;

    @Column(nullable = false)
    private Double precio;

    @Column(nullable = false)
    private Boolean disponible;

    // Los planes que ya existían reciben 10 al agregar la columna
    @Column(nullable = false, columnDefinition = "integer default 10")
    private Integer stock = PlanHosting.STOCK_POR_DEFECTO;

    public PlanHosting() {
    }

    public PlanHosting(String nombre, String tipoServidor, Integer ram, Integer slots, Double precio,
                       Boolean disponible, Integer stock) {
        this.nombre = nombre;
        this.tipoServidor = tipoServidor;
        this.ram = ram;
        this.slots = slots;
        this.precio = precio;
        this.disponible = disponible;
        this.stock = stock;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTipoServidor() {
        return tipoServidor;
    }

    public void setTipoServidor(String tipoServidor) {
        this.tipoServidor = tipoServidor;
    }

    public Integer getRam() {
        return ram;
    }

    public void setRam(Integer ram) {
        this.ram = ram;
    }

    public Integer getSlots() {
        return slots;
    }

    public void setSlots(Integer slots) {
        this.slots = slots;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public Boolean getDisponible() {
        return disponible;
    }

    public void setDisponible(Boolean disponible) {
        this.disponible = disponible;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    /**
     * Descuenta unidades vendidas. El stock nunca queda negativo y,
     * si llega a 0, el plan deja de estar disponible para nuevos pedidos.
     */
    public void descontarStock(int cantidad) {
        this.stock = Math.max(0, this.stock - cantidad);
        if (this.stock == 0) {
            this.disponible = false;
        }
    }
}
