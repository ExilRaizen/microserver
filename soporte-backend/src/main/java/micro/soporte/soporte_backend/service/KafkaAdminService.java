package micro.soporte.soporte_backend.service;

import micro.soporte.soporte_backend.config.KafkaConfig;
import micro.soporte.soporte_backend.dto.kafka.CrearTopicRequest;
import micro.soporte.soporte_backend.dto.kafka.GrupoDetalle;
import micro.soporte.soporte_backend.dto.kafka.GrupoResumen;
import micro.soporte.soporte_backend.dto.kafka.LagParticion;
import micro.soporte.soporte_backend.dto.kafka.MiembroInfo;
import micro.soporte.soporte_backend.dto.kafka.ModificarConfigRequest;
import micro.soporte.soporte_backend.dto.kafka.ParticionInfo;
import micro.soporte.soporte_backend.dto.kafka.TopicDetalle;
import micro.soporte.soporte_backend.dto.kafka.TopicResumen;
import micro.soporte.soporte_backend.exception.ConflictoRecursoException;
import micro.soporte.soporte_backend.exception.KafkaNoDisponibleException;
import micro.soporte.soporte_backend.exception.RecursoNoEncontradoException;
import micro.soporte.soporte_backend.exception.SolicitudKafkaInvalidaException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.PolicyViolationException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Administración del cluster de Kafka con AdminClient.
 *
 * Todas las operaciones de AdminClient son asíncronas (devuelven un KafkaFuture).
 * Aquí se espera cada resultado con un tiempo máximo y los errores de Kafka
 * se traducen a excepciones propias, que GlobalExceptionHandler convierte en 400, 404, 409 o 503.
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);
    private static final long TIEMPO_MAXIMO_SEGUNDOS = 10;

    // Tópicos del flujo de pedidos y catálogo: borrarlos rompería el sistema
    private static final Set<String> TOPICS_PROTEGIDOS = Set.of(
            KafkaConfig.PEDIDOS_EVENTOS_TOPIC,
            KafkaConfig.PEDIDOS_EVENTOS_DLT,
            "catalogo.planes"
    );

    // Configuraciones que se muestran al describir un tópico
    private static final List<String> CONFIGS_VISIBLES = List.of(
            TopicConfig.CLEANUP_POLICY_CONFIG,
            TopicConfig.RETENTION_MS_CONFIG,
            TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
            TopicConfig.SEGMENT_MS_CONFIG,
            TopicConfig.DELETE_RETENTION_MS_CONFIG,
            TopicConfig.MIN_CLEANABLE_DIRTY_RATIO_CONFIG
    );

    private final AdminClient adminClient;

    public KafkaAdminService(AdminClient adminClient) {
        this.adminClient = adminClient;
    }

    // ========== Tópicos ==========

    /** Lista los tópicos del cluster, sin los internos de Kafka (como __consumer_offsets). */
    public List<TopicResumen> listarTopics() {
        Set<String> nombres = esperar(adminClient.listTopics().names(), "listar los tópicos");
        if (nombres.isEmpty()) {
            return List.of();
        }
        Map<String, TopicDescription> descripciones =
                esperar(adminClient.describeTopics(nombres).allTopicNames(), "describir los tópicos");

        return descripciones.values().stream()
                .map(d -> new TopicResumen(d.name(), d.partitions().size(),
                        d.partitions().isEmpty() ? 0 : d.partitions().get(0).replicas().size()))
                .sorted(Comparator.comparing(TopicResumen::nombre))
                .toList();
    }

    /** Particiones (líder, réplicas, ISR) y configuración principal de un tópico. */
    public TopicDetalle describirTopic(String nombre) {
        TopicDescription descripcion = esperar(
                adminClient.describeTopics(List.of(nombre)).allTopicNames(), "describir el tópico '" + nombre + "'")
                .get(nombre);

        List<ParticionInfo> particiones = descripcion.partitions().stream()
                .map(this::aParticionInfo)
                .toList();

        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        Config config = esperar(adminClient.describeConfigs(List.of(recurso)).all(),
                "leer la configuración de '" + nombre + "'").get(recurso);

        Map<String, String> configuracion = new LinkedHashMap<>();
        for (String clave : CONFIGS_VISIBLES) {
            ConfigEntry entrada = config.get(clave);
            if (entrada != null) {
                configuracion.put(clave, entrada.value());
            }
        }
        return new TopicDetalle(descripcion.name(), descripcion.isInternal(), particiones, configuracion);
    }

    public void crearTopic(CrearTopicRequest request) {
        NewTopic topic = new NewTopic(request.nombre(), request.particionesOPorDefecto(), request.replicasOPorDefecto());

        Map<String, String> configs = new HashMap<>();
        // Con 3 réplicas se exige que al menos 2 estén al día para aceptar escrituras con acks=all
        if (request.replicasOPorDefecto() >= 2) {
            configs.put(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2");
        }
        if (request.cleanupPolicy() != null) {
            configs.put(TopicConfig.CLEANUP_POLICY_CONFIG, request.cleanupPolicy().toLowerCase());
        }
        if (request.retentionMs() != null) {
            configs.put(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(request.retentionMs()));
        }
        topic.configs(configs);

        esperar(adminClient.createTopics(List.of(topic)).all(), "crear el tópico '" + request.nombre() + "'");
        log.info("[KAFKA-ADMIN] Tópico creado: {} (particiones={}, réplicas={}, configs={})",
                request.nombre(), request.particionesOPorDefecto(), request.replicasOPorDefecto(), configs);
    }

    /** Cambia configuraciones de un tópico en caliente, sin reiniciar nada. */
    public void modificarConfig(String nombre, ModificarConfigRequest request) {
        if (request.estaVacio()) {
            throw new SolicitudKafkaInvalidaException("Indica al menos una configuración a cambiar");
        }
        List<AlterConfigOp> cambios = new ArrayList<>();
        if (request.retentionMs() != null) {
            cambios.add(fijar(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(request.retentionMs())));
        }
        if (request.cleanupPolicy() != null) {
            cambios.add(fijar(TopicConfig.CLEANUP_POLICY_CONFIG, request.cleanupPolicy().toLowerCase()));
        }
        if (request.minInsyncReplicas() != null) {
            cambios.add(fijar(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(request.minInsyncReplicas())));
        }

        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        Map<ConfigResource, Collection<AlterConfigOp>> peticion = Map.of(recurso, cambios);
        esperar(adminClient.incrementalAlterConfigs(peticion).all(), "modificar la configuración de '" + nombre + "'");
        log.info("[KAFKA-ADMIN] Configuración de {} modificada: {}", nombre, request);
    }

    /** Aumenta las particiones de un tópico. Ojo: cambia a qué partición va cada key de ahí en adelante. */
    public void aumentarParticiones(String nombre, int total) {
        esperar(adminClient.createPartitions(Map.of(nombre, NewPartitions.increaseTo(total))).all(),
                "aumentar las particiones de '" + nombre + "'");
        log.info("[KAFKA-ADMIN] Tópico {} ahora tiene {} particiones", nombre, total);
    }

    public void eliminarTopic(String nombre) {
        if (TOPICS_PROTEGIDOS.contains(nombre) || nombre.startsWith("_")) {
            throw new ConflictoRecursoException("'" + nombre + "' es un tópico del sistema y no se puede eliminar");
        }
        esperar(adminClient.deleteTopics(List.of(nombre)).all(), "eliminar el tópico '" + nombre + "'");
        log.info("[KAFKA-ADMIN] Tópico eliminado: {}", nombre);
    }

    // ========== Grupos de consumidores ==========

    @SuppressWarnings("deprecation") // listConsumerGroups funciona con brokers 3.x; listGroups requiere brokers más nuevos
    public List<GrupoResumen> listarGrupos() {
        return esperar(adminClient.listConsumerGroups().all(), "listar los grupos de consumidores").stream()
                .map(g -> new GrupoResumen(g.groupId(),
                        g.groupState().map(GroupState::toString).orElse("DESCONOCIDO")))
                .sorted(Comparator.comparing(GrupoResumen::grupoId))
                .toList();
    }

    /** Miembros del grupo, particiones asignadas a cada uno y lag por partición. */
    public GrupoDetalle describirGrupo(String grupoId) {
        ConsumerGroupDescription descripcion = esperar(
                adminClient.describeConsumerGroups(List.of(grupoId)).all(), "describir el grupo '" + grupoId + "'")
                .get(grupoId);

        List<MiembroInfo> miembros = descripcion.members().stream()
                .map(m -> new MiembroInfo(m.clientId(), m.host(),
                        m.assignment().topicPartitions().stream()
                                .sorted(Comparator.comparing(TopicPartition::topic).thenComparing(TopicPartition::partition))
                                .map(TopicPartition::toString)
                                .toList()))
                .sorted(Comparator.comparing(MiembroInfo::clienteId))
                .toList();

        Map<TopicPartition, OffsetAndMetadata> confirmados = esperar(
                adminClient.listConsumerGroupOffsets(grupoId).partitionsToOffsetAndMetadata(),
                "leer los offsets del grupo '" + grupoId + "'");

        // Un grupo "DEAD" sin miembros ni offsets es un grupo que no existe
        if (descripcion.groupState() == GroupState.DEAD && miembros.isEmpty() && confirmados.isEmpty()) {
            throw new RecursoNoEncontradoException("El grupo '" + grupoId + "' no existe");
        }

        List<LagParticion> lag = calcularLag(confirmados);
        long lagTotal = lag.stream().mapToLong(l -> l.lag() != null ? l.lag() : 0).sum();
        Node coordinador = descripcion.coordinator();

        return new GrupoDetalle(
                descripcion.groupId(),
                descripcion.groupState().toString(),
                descripcion.partitionAssignor(),
                coordinador != null ? coordinador.id() : null,
                miembros,
                lag,
                lagTotal
        );
    }

    // ========== Internos ==========

    private List<LagParticion> calcularLag(Map<TopicPartition, OffsetAndMetadata> confirmados) {
        if (confirmados.isEmpty()) {
            return List.of();
        }
        Map<TopicPartition, OffsetSpec> pedirUltimos = new HashMap<>();
        confirmados.keySet().forEach(tp -> pedirUltimos.put(tp, OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> ultimos =
                esperar(adminClient.listOffsets(pedirUltimos).all(), "leer los últimos offsets");

        return confirmados.entrySet().stream()
                .map(e -> {
                    TopicPartition tp = e.getKey();
                    long ultimo = ultimos.get(tp).offset();
                    Long confirmado = e.getValue() != null ? e.getValue().offset() : null;
                    Long lag = confirmado != null ? Math.max(0, ultimo - confirmado) : null;
                    return new LagParticion(tp.topic(), tp.partition(), confirmado, ultimo, lag);
                })
                .sorted(Comparator.comparing(LagParticion::topic).thenComparing(LagParticion::particion))
                .toList();
    }

    private ParticionInfo aParticionInfo(TopicPartitionInfo p) {
        return new ParticionInfo(
                p.partition(),
                p.leader() != null ? p.leader().id() : null,
                p.replicas().stream().map(Node::id).toList(),
                p.isr().stream().map(Node::id).toList()
        );
    }

    private AlterConfigOp fijar(String clave, String valor) {
        return new AlterConfigOp(new ConfigEntry(clave, valor), AlterConfigOp.OpType.SET);
    }

    /**
     * Espera el resultado de una operación de AdminClient y traduce los errores de Kafka.
     */
    private <T> T esperar(KafkaFuture<T> futuro, String operacion) {
        try {
            return futuro.get(TIEMPO_MAXIMO_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaNoDisponibleException("Se interrumpió la operación: " + operacion);
        } catch (TimeoutException e) {
            log.error("[KAFKA-ADMIN] Kafka no respondió al {}", operacion);
            throw new KafkaNoDisponibleException("Kafka no respondió a tiempo al " + operacion);
        } catch (ExecutionException e) {
            throw traducir(e.getCause(), operacion);
        }
    }

    private RuntimeException traducir(Throwable causa, String operacion) {
        String detalle = causa != null ? causa.getMessage() : "error desconocido";
        if (causa instanceof TopicExistsException) {
            return new ConflictoRecursoException("No se pudo " + operacion + ": el tópico ya existe");
        }
        if (causa instanceof UnknownTopicOrPartitionException || causa instanceof GroupIdNotFoundException) {
            return new RecursoNoEncontradoException("No se pudo " + operacion + ": no existe");
        }
        if (causa instanceof InvalidPartitionsException
                || causa instanceof InvalidReplicationFactorException
                || causa instanceof InvalidConfigurationException
                || causa instanceof InvalidTopicException
                || causa instanceof InvalidRequestException
                || causa instanceof PolicyViolationException) {
            return new SolicitudKafkaInvalidaException("No se pudo " + operacion + ": " + detalle);
        }
        log.error("[KAFKA-ADMIN] Error al {}: {}", operacion, detalle);
        return new KafkaNoDisponibleException("No se pudo " + operacion + ", intenta nuevamente");
    }
}
