package micro.catalogo.catalogo_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class PlanHosting {

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

    public PlanHosting() {
    }

    public PlanHosting(String nombre, String tipoServidor, Integer ram, Integer slots, Double precio, Boolean disponible) {
        this.nombre = nombre;
        this.tipoServidor = tipoServidor;
        this.ram = ram;
        this.slots = slots;
        this.precio = precio;
        this.disponible = disponible;
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
}
