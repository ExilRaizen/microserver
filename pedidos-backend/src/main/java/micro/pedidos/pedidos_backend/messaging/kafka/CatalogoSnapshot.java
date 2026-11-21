package micro.pedidos.pedidos_backend.messaging.kafka;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copia local del catálogo armada con los eventos de "catalogo.planes".
 *
 * Así Pedidos puede revisar si un plan está disponible sin llamar a Catálogo por HTTP:
 * si Catálogo está caído, Pedidos sigue funcionando con el último estado conocido.
 * La copia es "eventualmente consistente": puede ir unos milisegundos atrasada respecto a Catálogo.
 */
@Component
public class CatalogoSnapshot {

    // key = id del plan (la misma key del tópico)
    private final Map<String, PlanActualizadoEvento> planes = new ConcurrentHashMap<>();

    public void actualizar(String planId, PlanActualizadoEvento plan) {
        planes.put(planId, plan);
    }

    public void eliminar(String planId) {
        planes.remove(planId);
    }

    /** Los pedidos guardan el nombre del plan como producto. */
    public Optional<PlanActualizadoEvento> buscarPorNombre(String nombre) {
        return planes.values().stream()
                .filter(p -> p.nombre() != null && p.nombre().equalsIgnoreCase(nombre))
                .findFirst();
    }

    public List<PlanActualizadoEvento> listar() {
        Collection<PlanActualizadoEvento> valores = planes.values();
        return valores.stream()
                .sorted(Comparator.comparing(PlanActualizadoEvento::planId))
                .toList();
    }

    public int cantidad() {
        return planes.size();
    }
}
