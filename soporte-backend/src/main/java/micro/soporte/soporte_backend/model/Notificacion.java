package micro.soporte.soporte_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

@Entity
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pedidoId;

    @Column(nullable = false)
    private String clienteEmail;

    @Column(nullable = false, length = 500)
    private String mensaje;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    public Notificacion() {
    }

    public Notificacion(Long pedidoId, String clienteEmail, String mensaje, LocalDateTime fechaCreacion) {
        this.pedidoId = pedidoId;
        this.clienteEmail = clienteEmail;
        this.mensaje = mensaje;
        this.fechaCreacion = fechaCreacion;
    }

    public Long getId() {
        return id;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public String getClienteEmail() {
        return clienteEmail;
    }

    public String getMensaje() {
        return mensaje;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}