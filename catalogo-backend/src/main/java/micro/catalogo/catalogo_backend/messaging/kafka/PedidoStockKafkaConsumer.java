package micro.catalogo.catalogo_backend.messaging.kafka;

import micro.catalogo.catalogo_backend.config.KafkaConfig;
import micro.catalogo.catalogo_backend.service.StockService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor Kafka de "pedidos.eventos" para el stock del catálogo.
 *
 * Es otro grupo ("catalogo-stock"), distinto al de Soporte: cada grupo recibe TODOS los eventos
 * y lleva su propio offset. Dentro del grupo, los 3 consumidores se reparten las particiones.
 * Como la key es el plan, los pedidos de un mismo plan llegan en orden al mismo consumidor.
 */
@Component
public class PedidoStockKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoStockKafkaConsumer.class);

    private final StockService stockService;
    private final PlanKafkaPublisher planKafkaPublisher;

    public PedidoStockKafkaConsumer(StockService stockService, PlanKafkaPublisher planKafkaPublisher) {
        this.stockService = stockService;
        this.planKafkaPublisher = planKafkaPublisher;
    }

    @KafkaListener(
            id = "catalogo-stock-listener",
            topics = KafkaConfig.PEDIDOS_EVENTOS_TOPIC,
            groupId = KafkaConfig.GRUPO_STOCK
    )
    public void recibir(ConsumerRecord<String, PedidoCreadoEvento> registro) {
        PedidoCreadoEvento evento = registro.value();
        log.info("[KAFKA] Recibido pedido #{} | partición {} | offset {} | key '{}' | hilo {}",
                evento.pedidoId(), registro.partition(), registro.offset(), registro.key(),
                Thread.currentThread().getName());

        evento.validar();
        // Si el stock cambió, se publica el nuevo estado del plan para que Pedidos lo sepa
        stockService.descontarStock(evento).ifPresent(planKafkaPublisher::publicarPlan);
    }
}
