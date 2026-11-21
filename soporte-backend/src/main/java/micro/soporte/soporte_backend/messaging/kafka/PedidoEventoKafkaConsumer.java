package micro.soporte.soporte_backend.messaging.kafka;

import micro.soporte.soporte_backend.config.KafkaConfig;
import micro.soporte.soporte_backend.service.AnaliticaService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor Kafka de "pedidos.eventos" para la analítica de Soporte.
 *
 * Pertenece al grupo "soporte-analitica" con 3 consumidores (uno por partición).
 * Kafka reparte las particiones entre ellos; si uno cae, sus particiones pasan a los otros.
 *
 * No hace ACK a mano: si el método termina bien, Spring confirma el offset del evento.
 * Si lanza una excepción, el manejador de errores de KafkaConfig reintenta y luego lo manda al DLT.
 */
@Component
public class PedidoEventoKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventoKafkaConsumer.class);

    private final AnaliticaService analiticaService;

    public PedidoEventoKafkaConsumer(AnaliticaService analiticaService) {
        this.analiticaService = analiticaService;
    }

    @KafkaListener(
            id = "soporte-analitica-listener",
            topics = KafkaConfig.PEDIDOS_EVENTOS_TOPIC,
            groupId = KafkaConfig.GRUPO_ANALITICA
    )
    public void recibir(ConsumerRecord<String, PedidoCreadoEvento> registro) {
        PedidoCreadoEvento evento = registro.value();
        log.info("[KAFKA] Recibido pedido #{} | partición {} | offset {} | key '{}' | hilo {}",
                evento.pedidoId(), registro.partition(), registro.offset(), registro.key(),
                Thread.currentThread().getName());

        evento.validar();
        boolean registrado = analiticaService.registrarPedido(evento);

        if (registrado) {
            log.info("[KAFKA] Analítica actualizada con el pedido #{} ({} x '{}', cliente {})",
                    evento.pedidoId(), evento.cantidad(), evento.producto(), evento.clienteEmail());
        } else {
            log.info("[KAFKA] Evento {} ya estaba procesado, se ignora el duplicado", evento.eventoId());
        }
    }
}
