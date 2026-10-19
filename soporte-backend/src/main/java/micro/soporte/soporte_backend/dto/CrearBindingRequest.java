package micro.soporte.soporte_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearBindingRequest(

        @NotNull(message = "es obligatorio")
        @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE, message = ValidacionRabbit.MENSAJE_NOMBRE)
        String cola,

        @NotNull(message = "es obligatorio")
        @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE, message = ValidacionRabbit.MENSAJE_NOMBRE)
        String exchange,

        // Puede ir vacía, por ejemplo en un exchange fanout que ignora la routing key
        @NotNull(message = "es obligatoria (puede ir vacía)")
        @Size(max = 255, message = "no puede superar los 255 caracteres")
        String routingKey
) {
}