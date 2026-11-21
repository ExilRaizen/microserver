package micro.soporte.soporte_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Registro de los eventos de Kafka que la analítica ya contó.
 *
 * Kafka garantiza entrega "al menos una vez": si el consumidor cae justo antes de confirmar
 * el offset, el evento llega de nuevo. Guardando el eventoId se evita contar dos veces el mismo pedido.
 */
@Entity
@Table(name = "soporte_evento_procesado")
public class EventoProcesado {

    @Id
    private String eventoId;

    @Column(nullable = false)
    private LocalDateTime fechaProcesado;

    public EventoProcesado() {
    }

    public EventoProcesado(String eventoId, LocalDateTime fechaProcesado) {
        this.eventoId = eventoId;
        this.fechaProcesado = fechaProcesado;
    }

    public String getEventoId() {
        return eventoId;
    }

    public LocalDateTime getFechaProcesado() {
        return fechaProcesado;
    }
}
