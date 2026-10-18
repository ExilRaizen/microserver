package micro.soporte.soporte_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CrearColaRequest(

        @NotNull(message = "es obligatorio")
        @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE, message = ValidacionRabbit.MENSAJE_NOMBRE)
        String nombre,

        Boolean durable
) {
    // Si no se indica, la cola es durable (sobrevive a reinicios de RabbitMQ)
    public boolean esDurable() {
        return durable == null || durable;
    }
}