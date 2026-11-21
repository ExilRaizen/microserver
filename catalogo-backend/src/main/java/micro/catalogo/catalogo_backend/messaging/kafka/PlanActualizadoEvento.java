package micro.catalogo.catalogo_backend.messaging.kafka;

import micro.catalogo.catalogo_backend.model.PlanHosting;

import java.time.LocalDateTime;

/**
 * DTO del evento que Catálogo publica en "catalogo.planes" cada vez que un plan cambia.
 * Lleva el estado completo del plan (no solo lo que cambió), porque en un tópico
 * compactado solo se garantiza que sobreviva el último evento de cada plan.
 */
public record PlanActualizadoEvento(
        Long planId,
        String nombre,
        String tipoServidor,
        Integer ram,
        Integer slots,
        Double precio,
        Boolean disponible,
        Integer stock,
        LocalDateTime fechaActualizacion
) {

    public static PlanActualizadoEvento desde(PlanHosting plan) {
        return new PlanActualizadoEvento(
                plan.getId(),
                plan.getNombre(),
                plan.getTipoServidor(),
                plan.getRam(),
                plan.getSlots(),
                plan.getPrecio(),
                plan.getDisponible(),
                plan.getStock(),
                LocalDateTime.now()
        );
    }
}
