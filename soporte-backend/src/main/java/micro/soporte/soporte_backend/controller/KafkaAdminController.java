package micro.soporte.soporte_backend.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import micro.soporte.soporte_backend.dto.RespuestaAdmin;
import micro.soporte.soporte_backend.dto.kafka.AgregarParticionesRequest;
import micro.soporte.soporte_backend.dto.kafka.CrearTopicRequest;
import micro.soporte.soporte_backend.dto.kafka.GrupoDetalle;
import micro.soporte.soporte_backend.dto.kafka.GrupoResumen;
import micro.soporte.soporte_backend.dto.kafka.ModificarConfigRequest;
import micro.soporte.soporte_backend.dto.kafka.TopicDetalle;
import micro.soporte.soporte_backend.dto.kafka.TopicResumen;
import micro.soporte.soporte_backend.dto.kafka.ValidacionKafka;
import micro.soporte.soporte_backend.service.KafkaAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints REST para administrar Kafka: tópicos, su configuración y los grupos de consumidores.
 * Solo recibe y valida la petición: la lógica está en KafkaAdminService.
 */
@RestController
@RequestMapping("/api/kafka")
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;

    public KafkaAdminController(KafkaAdminService kafkaAdminService) {
        this.kafkaAdminService = kafkaAdminService;
    }

    // ========== Tópicos ==========

    @GetMapping("/topics")
    public List<TopicResumen> listarTopics() {
        return kafkaAdminService.listarTopics();
    }

    @GetMapping("/topics/{nombre}")
    public TopicDetalle describirTopic(
            @PathVariable @Pattern(regexp = ValidacionKafka.PATRON_TOPIC,
                    message = "El nombre del tópico " + ValidacionKafka.MENSAJE_TOPIC) String nombre) {
        return kafkaAdminService.describirTopic(nombre);
    }

    @PostMapping("/topics")
    public ResponseEntity<RespuestaAdmin> crearTopic(@Valid @RequestBody CrearTopicRequest request) {
        kafkaAdminService.crearTopic(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RespuestaAdmin("Tópico '" + request.nombre() + "' creado"));
    }

    @PutMapping("/topics/{nombre}/config")
    public RespuestaAdmin modificarConfig(
            @PathVariable @Pattern(regexp = ValidacionKafka.PATRON_TOPIC,
                    message = "El nombre del tópico " + ValidacionKafka.MENSAJE_TOPIC) String nombre,
            @Valid @RequestBody ModificarConfigRequest request) {
        kafkaAdminService.modificarConfig(nombre, request);
        return new RespuestaAdmin("Configuración de '" + nombre + "' actualizada");
    }

    @PostMapping("/topics/{nombre}/partitions")
    public RespuestaAdmin aumentarParticiones(
            @PathVariable @Pattern(regexp = ValidacionKafka.PATRON_TOPIC,
                    message = "El nombre del tópico " + ValidacionKafka.MENSAJE_TOPIC) String nombre,
            @Valid @RequestBody AgregarParticionesRequest request) {
        kafkaAdminService.aumentarParticiones(nombre, request.total());
        return new RespuestaAdmin("Tópico '" + nombre + "' ahora tiene " + request.total() + " particiones");
    }

    @DeleteMapping("/topics/{nombre}")
    public RespuestaAdmin eliminarTopic(
            @PathVariable @Pattern(regexp = ValidacionKafka.PATRON_TOPIC,
                    message = "El nombre del tópico " + ValidacionKafka.MENSAJE_TOPIC) String nombre) {
        kafkaAdminService.eliminarTopic(nombre);
        return new RespuestaAdmin("Tópico '" + nombre + "' eliminado");
    }

    // ========== Grupos de consumidores ==========

    @GetMapping("/consumer-groups")
    public List<GrupoResumen> listarGrupos() {
        return kafkaAdminService.listarGrupos();
    }

    @GetMapping("/consumer-groups/{grupoId}")
    public GrupoDetalle describirGrupo(
            @PathVariable @Pattern(regexp = ValidacionKafka.PATRON_GRUPO,
                    message = "El grupo " + ValidacionKafka.MENSAJE_GRUPO) String grupoId) {
        return kafkaAdminService.describirGrupo(grupoId);
    }
}
