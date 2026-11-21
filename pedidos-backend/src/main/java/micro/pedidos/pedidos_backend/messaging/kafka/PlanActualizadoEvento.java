package micro.pedidos.pedidos_backend.messaging.kafka;

import java.time.LocalDateTime;

/**
 * DTO del evento que Catálogo publica en "catalogo.planes" con el estado completo de un plan.
 * Es la copia local de Pedidos: solo comparte el formato JSON con Catálogo.
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
}
