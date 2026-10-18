package micro.soporte.soporte_backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CrearExchangeRequest(

        @NotNull(message = "es obligatorio")
        @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE, message = ValidacionRabbit.MENSAJE_NOMBRE)
        String nombre,

        @NotNull(message = "es obligatorio")
        @Pattern(regexp = ValidacionRabbit.PATRON_TIPO_EXCHANGE, message = ValidacionRabbit.MENSAJE_TIPO_EXCHANGE)
        String tipo,

        Boolean durable
) {
    public boolean esDurable() {
        return durable == null || durable;
    }
}