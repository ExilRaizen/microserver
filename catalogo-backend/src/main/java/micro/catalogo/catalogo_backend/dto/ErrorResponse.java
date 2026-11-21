package micro.catalogo.catalogo_backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Formato común de los errores. No incluye la ruta, la clase de la excepción ni el stack trace,
 * para no exponer detalles internos del servicio.
 */
public record ErrorResponse(LocalDateTime timestamp, int status, String error, List<String> detalles) {
}
