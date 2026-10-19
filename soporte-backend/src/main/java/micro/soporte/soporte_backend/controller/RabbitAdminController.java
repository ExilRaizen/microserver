package micro.soporte.soporte_backend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import micro.soporte.soporte_backend.dto.ColaInfoResponse;
import micro.soporte.soporte_backend.dto.CrearBindingRequest;
import micro.soporte.soporte_backend.dto.CrearColaRequest;
import micro.soporte.soporte_backend.dto.CrearExchangeRequest;
import micro.soporte.soporte_backend.dto.RespuestaAdmin;
import micro.soporte.soporte_backend.dto.ValidacionRabbit;
import micro.soporte.soporte_backend.service.RabbitAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST para administrar RabbitMQ.
 * Solo recibe y valida la petición: la lógica está en RabbitAdminService.
 */
@RestController
@RequestMapping("/api/rabbitmq")
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    // ========== Colas ==========

    @PostMapping("/queues")
    public ResponseEntity<RespuestaAdmin> crearCola(@Valid @RequestBody CrearColaRequest request) {
        rabbitAdminService.crearCola(request.nombre(), request.esDurable());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RespuestaAdmin("Cola '" + request.nombre() + "' creada"));
    }

    @GetMapping("/queues/{nombre}")
    public ColaInfoResponse obtenerCola(
            @PathVariable @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE,
                    message = "El nombre de la cola " + ValidacionRabbit.MENSAJE_NOMBRE) String nombre) {
        return rabbitAdminService.obtenerCola(nombre);
    }

    @DeleteMapping("/queues/{nombre}")
    public RespuestaAdmin eliminarCola(
            @PathVariable @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE,
                    message = "El nombre de la cola " + ValidacionRabbit.MENSAJE_NOMBRE) String nombre) {
        rabbitAdminService.eliminarCola(nombre);
        return new RespuestaAdmin("Cola '" + nombre + "' eliminada");
    }

    @PostMapping("/queues/{nombre}/purge")
    public RespuestaAdmin purgarCola(
            @PathVariable @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE,
                    message = "El nombre de la cola " + ValidacionRabbit.MENSAJE_NOMBRE) String nombre) {
        int eliminados = rabbitAdminService.purgarCola(nombre);
        return new RespuestaAdmin("Cola '" + nombre + "' purgada, " + eliminados + " mensajes eliminados");
    }

    // ========== Exchanges ==========

    @PostMapping("/exchanges")
    public ResponseEntity<RespuestaAdmin> crearExchange(@Valid @RequestBody CrearExchangeRequest request) {
        rabbitAdminService.crearExchange(request.nombre(), request.tipo(), request.esDurable());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RespuestaAdmin("Exchange '" + request.nombre() + "' creado"));
    }

    @DeleteMapping("/exchanges/{nombre}")
    public RespuestaAdmin eliminarExchange(
            @PathVariable @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE,
                    message = "El nombre del exchange " + ValidacionRabbit.MENSAJE_NOMBRE) String nombre) {
        rabbitAdminService.eliminarExchange(nombre);
        return new RespuestaAdmin("Exchange '" + nombre + "' eliminado");
    }

    // ========== Bindings ==========

    @PostMapping("/bindings")
    public ResponseEntity<RespuestaAdmin> crearBinding(@Valid @RequestBody CrearBindingRequest request) {
        rabbitAdminService.crearBinding(request.cola(), request.exchange(), request.routingKey());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RespuestaAdmin("Binding creado: " + request.exchange()
                        + " --(" + request.routingKey() + ")--> " + request.cola()));
    }

    @DeleteMapping("/bindings")
    public RespuestaAdmin eliminarBinding(
            @RequestParam @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE,
                    message = "El nombre de la cola " + ValidacionRabbit.MENSAJE_NOMBRE) String cola,
            @RequestParam @Pattern(regexp = ValidacionRabbit.PATRON_NOMBRE,
                    message = "El nombre del exchange " + ValidacionRabbit.MENSAJE_NOMBRE) String exchange,
            @RequestParam(defaultValue = "") @Size(max = 255,
                    message = "La routing key no puede superar los 255 caracteres") String routingKey) {
        rabbitAdminService.eliminarBinding(cola, exchange, routingKey);
        return new RespuestaAdmin("Binding eliminado: " + exchange + " --(" + routingKey + ")--> " + cola);
    }
}