package micro.catalogo.catalogo_backend.messaging.kafka;

import micro.catalogo.catalogo_backend.config.KafkaConfig;
import micro.catalogo.catalogo_backend.model.PlanHosting;
import micro.catalogo.catalogo_backend.repository.PlanHostingRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Productor Kafka del catálogo. Publica el estado de cada plan en "catalogo.planes".
 *
 * La key es el id del plan: así todos los cambios de un plan van a la misma partición
 * (quedan en orden) y la compactación conserva solo el último estado de cada uno.
 */
@Component
public class PlanKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(PlanKafkaPublisher.class);

    private final KafkaTemplate<String, PlanActualizadoEvento> kafkaTemplate;
    private final PlanHostingRepository planHostingRepository;

    public PlanKafkaPublisher(KafkaTemplate<String, PlanActualizadoEvento> kafkaTemplate,
                              PlanHostingRepository planHostingRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.planHostingRepository = planHostingRepository;
    }

    public void publicarPlan(PlanHosting plan) {
        enviar(String.valueOf(plan.getId()), PlanActualizadoEvento.desde(plan),
                "Plan '" + plan.getNombre() + "' (stock " + plan.getStock() + ", disponible " + plan.getDisponible() + ")");
    }

    /**
     * Tombstone: evento con la key del plan y valor vacío (null).
     * La compactación entiende que ese plan se borró y termina eliminando todos sus eventos.
     */
    public void publicarEliminacion(Long planId) {
        enviar(String.valueOf(planId), null, "Plan #" + planId + " eliminado (tombstone)");
    }

    /**
     * Al arrancar se publica el catálogo completo. Así el tópico queda con todos los planes,
     * incluso los que se crearon antes de tener Kafka.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void publicarCatalogoCompleto() {
        List<PlanHosting> planes = planHostingRepository.findAll();
        planes.forEach(this::publicarPlan);
        log.info("[KAFKA] Sincronización inicial: {} planes publicados en {}",
                planes.size(), KafkaConfig.CATALOGO_PLANES_TOPIC);
    }

    private void enviar(String key, PlanActualizadoEvento evento, String descripcion) {
        try {
            kafkaTemplate.send(KafkaConfig.CATALOGO_PLANES_TOPIC, key, evento)
                    .whenComplete((resultado, error) -> {
                        if (error != null) {
                            log.error("[KAFKA] No se pudo publicar: {} | {}", descripcion, error.getMessage());
                            return;
                        }
                        RecordMetadata metadata = resultado.getRecordMetadata();
                        log.info("[KAFKA] {} publicado en {} | partición {} | offset {} | key '{}'",
                                descripcion, metadata.topic(), metadata.partition(), metadata.offset(), key);
                    });
        } catch (RuntimeException e) {
            // Si Kafka no está disponible, el cambio ya quedó en la base de datos: no se cae la petición
            log.error("[KAFKA] Error al enviar: {} | {}", descripcion, e.getMessage());
        }
    }
}
