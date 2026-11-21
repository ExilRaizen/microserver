package micro.pedidos.pedidos_backend.exception;

import micro.pedidos.pedidos_backend.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Convierte las excepciones en respuestas con un formato controlado.
 * Solo se muestran los mensajes que escribimos nosotros; cualquier error inesperado
 * se registra completo en el log y al cliente le llega un mensaje genérico.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Errores que lanzamos a propósito (400, 404, 409) con un motivo pensado para el usuario
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> manejarErrorControlado(ResponseStatusException ex) {
        String motivo = ex.getReason() != null ? ex.getReason() : "Solicitud rechazada";
        return responder(ex.getStatusCode(), List.of(motivo));
    }

    // Falta el scope requerido en el token (@PreAuthorize)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarSinPermiso(AccessDeniedException ex) {
        return responder(HttpStatus.FORBIDDEN, List.of("No tienes permiso para realizar esta acción"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, List.of("El cuerpo de la petición no es un JSON válido"));
    }

    // Cualquier otro error: el detalle queda en el log, no en la respuesta
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorInesperado(Exception ex) {
        log.error("Error inesperado", ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR,
                List.of("Ocurrió un error interno, intenta nuevamente"));
    }

    private ResponseEntity<ErrorResponse> responder(HttpStatusCode status, List<String> detalles) {
        HttpStatus estado = HttpStatus.valueOf(status.value());
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(), estado.value(), estado.getReasonPhrase(), detalles);
        return ResponseEntity.status(estado).body(body);
    }
}
