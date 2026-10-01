package micro.soporte.soporte_backend.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrdersRabbitMQConfig {

    // ========== COLAS Y EXCHANGES PRINCIPALES ==========
    public static final String ORDERS_EXCHANGE = "orders.exchange";
    public static final String ORDERS_QUEUE = "orders.queue";
    public static final String ORDERS_ROUTING_KEY = "order.created";

    // ========== DLX (Dead Letter Exchange) ==========
    public static final String DLX_EXCHANGE = "orders.dlx";
    public static final String DLQ_QUEUE = "orders.dlq";
    public static final String DLX_ROUTING_KEY = "order.dead";

    // ========== Exchange Principal (Direct) ==========
    @Bean
    public DirectExchange ordersExchange() {
        return new DirectExchange(ORDERS_EXCHANGE, true, false);
    }

    // ========== COLA PRINCIPAL CON DLX CONFIGURADO ==========
    /**
     * La magia está aquí. Esta cola:
     * 1. Es durable (sobrevive reinicio de RabbitMQ)
     * 2. Tiene TTL de 30 segundos para los mensajes
     * 3. Está configurada para enviar mensajes muertos al DLX
     * 4. Reintentará con exponential backoff (lo configura Spring)
     */
    @Bean
    public Queue ordersQueue() {
        return QueueBuilder.durable(ORDERS_QUEUE)
                // TTL: Los mensajes expiran después de 30 segundos
                .withArgument("x-message-ttl", 30000)
                // DLX: Si el mensaje falla, va aquí
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                // Max retries a nivel de cola (opcional)
                .withArgument("x-max-length", 1000) // Máximo 1000 mensajes
                .build();
    }

    @Bean
    public Binding ordersBinding(Queue ordersQueue, DirectExchange ordersExchange) {
        return BindingBuilder.bind(ordersQueue)
                .to(ordersExchange)
                .with(ORDERS_ROUTING_KEY);
    }

    // ========== DEAD LETTER EXCHANGE (DLX) Y COLA (DLQ) ==========
    /**
     * El DLX recibe todos los mensajes que fallaron en la cola principal.
     * Usamos Fanout para que todos los listeners lo vean.
     */
    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX_EXCHANGE, true, false);
    }

    /**
     * La DLQ guarda los mensajes fallidos para auditoría y análisis.
     * TTL de 24 horas para mantener un histórico.
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE)
                // TTL: Los mensajes en DLQ se borran después de 24h
                .withArgument("x-message-ttl", 86400000) // 24 horas en ms
                .build();
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, FanoutExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange);
    }
}