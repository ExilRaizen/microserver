package micro.catalogo.catalogo_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Eventos de pedidos que ya descontaron stock. Evita descontar dos veces
 * si Kafka entrega de nuevo un evento (entrega "al menos una vez").
 */
@Entity
@Table(name = "catalogo_evento_procesado")
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
