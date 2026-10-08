package micro.soporte.soporte_backend.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ListenerMGService {

    private static final Logger log = LoggerFactory.getLogger(ListenerMGService.class);

    private final RabbitListenerEndpointRegistry registry;

    public ListenerMGService(RabbitListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    public void pauseListener(String listenerId) {
        try {
            MessageListenerContainer container = registry.getListenerContainer(listenerId);
            if (container != null && container.isRunning()) {
                container.stop();
                log.info("  Listener pausado: {}", listenerId);
            }
        } catch (Exception e) {
            log.error("Error al pausar listener: {}", e.getMessage());
        }
    }

    public void resumeListener(String listenerId) {
        try {
            MessageListenerContainer container = registry.getListenerContainer(listenerId);
            if (container != null) {
                container.start();
                log.info("  Listener reanudado: {}", listenerId);
            }
        } catch (Exception e) {
            log.error("Error al reanudar listener: {}", e.getMessage());
        }
    }

    public List<String> getAllListenerIds() {
        return new ArrayList<>(registry.getListenerContainerIds());
    }
}