package micro.soporte.soporte_backend.exception;

import micro.soporte.soporte_backend.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Convierte las excepciones en respuestas HTTP con un formato común.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Errores de validación en el body (DTO con @Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarBodyInvalido(MethodArgumentNotValidException ex) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "Parámetros inválidos", detalles);
    }

    // Errores de validación en la URL (@PathVariable y @RequestParam)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> manejarParametroInvalido(HandlerMethodValidationException ex) {
        List<String> detalles = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "Parámetros inválidos", detalles);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> manejarParametroFaltante(MissingServletRequestParameterException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Parámetros inválidos",
                List.of("Falta el parámetro obligatorio '" + ex.getParameterName() + "'"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(HttpMessageNotReadableException ex) {
        return responder(HttpStatus.BAD_REQUEST, "Petición mal formada",
                List.of("El cuerpo de la petición no es un JSON válido"));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(RecursoNoEncontradoException ex) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado", List.of(ex.getMessage()));
    }

    @ExceptionHandler(ConflictoRecursoException.class)
    public ResponseEntity<ErrorResponse> manejarConflicto(ConflictoRecursoException ex) {
        return responder(HttpStatus.CONFLICT, "Conflicto", List.of(ex.getMessage()));
    }

    // RabbitMQ no disponible o rechazó la operación
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponse> manejarErrorRabbit(AmqpException ex) {
        log.error("[ADMIN] Error al comunicarse con RabbitMQ: {}", ex.getMessage());
        return responder(HttpStatus.SERVICE_UNAVAILABLE, "RabbitMQ no disponible",
                List.of("No se pudo completar la operación en RabbitMQ, intenta nuevamente"));
    }

    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String error, List<String> detalles) {
        ErrorResponse body = new ErrorResponse(LocalDateTime.now(), status.value(), error, detalles);
        return ResponseEntity.status(status).body(body);
    }
}