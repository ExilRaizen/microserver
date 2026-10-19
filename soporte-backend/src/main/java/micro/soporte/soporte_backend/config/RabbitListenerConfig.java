package micro.soporte.soporte_backend.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de los contenedores de listeners.
 *
 * Todos los consumidores de Soporte usan esta fábrica: confirman cada
 * mensaje a mano (ACK manual) y reciben uno a la vez.
 */
@Configuration
public class RabbitListenerConfig {

    public static final String MANUAL_ACK_FACTORY = "manualAckListenerFactory";

    @Bean(name = MANUAL_ACK_FACTORY)
    public SimpleRabbitListenerContainerFactory manualAckListenerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        // El consumidor decide cuándo confirmar (ACK) o rechazar (NACK) cada mensaje
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // Procesa un mensaje a la vez: no recibe el siguiente hasta confirmar el actual
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        return factory;
    }
}