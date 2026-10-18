package micro.pedidos.pedidos_backend.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración centralizada de RabbitMQ para Pedidos.
 *
 * Todos los nombres de exchanges, colas y routing keys están definidos aquí.
 * Ninguna otra clase escribe estos nombres a mano: los toma desde estas constantes.
 *
 * Flujo "pedido creado":
 *   pedidos.exchange --(pedido.creado)--> pedidos.creados.queue
 *   Si el mensaje falla en Soporte:
 *   pedidos.dlx --(pedido.fallido)--> pedidos.creados.dlq
 */
@Configuration
public class RabbitMQConfig {

    // ========== Flujo principal: pedido creado ==========
    public static final String PEDIDOS_EXCHANGE = "pedidos.exchange";
    public static final String PEDIDOS_CREADOS_QUEUE = "pedidos.creados.queue";
    public static final String PEDIDO_CREADO_ROUTING_KEY = "pedido.creado";

    // ========== Mensajes fallidos (DLX y DLQ) ==========
    public static final String PEDIDOS_DLX = "pedidos.dlx";
    public static final String PEDIDOS_CREADOS_DLQ = "pedidos.creados.dlq";
    public static final String PEDIDO_FALLIDO_ROUTING_KEY = "pedido.fallido";

    // ========== Contrato del mensaje ==========
    // Formato: pedidoId|clienteEmail|producto|cantidad
    public static final String SEPARADOR_MENSAJE = "|";

    @Bean
    public DirectExchange pedidosExchange() {
        return ExchangeBuilder.directExchange(PEDIDOS_EXCHANGE).durable(true).build();
    }

    /**
     * Cola principal. Si Soporte rechaza un mensaje sin reencolarlo,
     * RabbitMQ lo reenvía al DLX con la routing key "pedido.fallido".
     */
    @Bean
    public Queue pedidosCreadosQueue() {
        return QueueBuilder.durable(PEDIDOS_CREADOS_QUEUE)
                .withArgument("x-dead-letter-exchange", PEDIDOS_DLX)
                .withArgument("x-dead-letter-routing-key", PEDIDO_FALLIDO_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding pedidosCreadosBinding(Queue pedidosCreadosQueue, DirectExchange pedidosExchange) {
        return BindingBuilder.bind(pedidosCreadosQueue)
                .to(pedidosExchange)
                .with(PEDIDO_CREADO_ROUTING_KEY);
    }

    @Bean
    public DirectExchange pedidosDeadLetterExchange() {
        return ExchangeBuilder.directExchange(PEDIDOS_DLX).durable(true).build();
    }

    /**
     * DLQ: guarda los pedidos que no se pudieron procesar, para revisión.
     */
    @Bean
    public Queue pedidosCreadosDlq() {
        return QueueBuilder.durable(PEDIDOS_CREADOS_DLQ).build();
    }

    @Bean
    public Binding pedidosCreadosDlqBinding(Queue pedidosCreadosDlq, DirectExchange pedidosDeadLetterExchange) {
        return BindingBuilder.bind(pedidosCreadosDlq)
                .to(pedidosDeadLetterExchange)
                .with(PEDIDO_FALLIDO_ROUTING_KEY);
    }
}