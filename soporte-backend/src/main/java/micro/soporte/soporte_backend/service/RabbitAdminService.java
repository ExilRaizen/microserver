package micro.soporte.soporte_backend.service;

import micro.soporte.soporte_backend.config.RabbitMQConfig;
import micro.soporte.soporte_backend.dto.ColaInfoResponse;
import micro.soporte.soporte_backend.exception.ConflictoRecursoException;
import micro.soporte.soporte_backend.exception.RecursoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Administración de recursos de RabbitMQ: colas, exchanges y bindings.
 *
 * Valida que los recursos existan antes de operar sobre ellos y protege
 * los que usa el sistema, para que no se puedan borrar desde la API.
 * Si algo falla, lanza una excepción que el GlobalExceptionHandler
 * convierte en una respuesta HTTP clara.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    // Recursos del flujo de pedidos: borrarlos rompería el sistema
    private static final Set<String> RECURSOS_PROTEGIDOS = Set.of(
            RabbitMQConfig.PEDIDOS_EXCHANGE,
            RabbitMQConfig.PEDIDOS_CREADOS_QUEUE,
            RabbitMQConfig.PEDIDOS_DLX,
            RabbitMQConfig.PEDIDOS_CREADOS_DLQ
    );

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;

    public RabbitAdminService(RabbitAdmin rabbitAdmin, RabbitTemplate rabbitTemplate) {
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ========== Colas ==========

    public void crearCola(String nombre, boolean durable) {
        if (existeCola(nombre)) {
            throw new ConflictoRecursoException("La cola '" + nombre + "' ya existe");
        }
        Queue cola = durable ? QueueBuilder.durable(nombre).build() : QueueBuilder.nonDurable(nombre).build();
        rabbitAdmin.declareQueue(cola);
        log.info("[ADMIN] Cola creada: {} (durable={})", nombre, durable);
    }

    public ColaInfoResponse obtenerCola(String nombre) {
        QueueInformation info = rabbitAdmin.getQueueInfo(nombre);
        if (info == null) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
        return new ColaInfoResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    public void eliminarCola(String nombre) {
        validarNoProtegido(nombre);
        obtenerCola(nombre);
        rabbitAdmin.deleteQueue(nombre);
        log.info("[ADMIN] Cola eliminada: {}", nombre);
    }

    public int purgarCola(String nombre) {
        obtenerCola(nombre);
        int eliminados = rabbitAdmin.purgeQueue(nombre);
        log.info("[ADMIN] Cola purgada: {} ({} mensajes eliminados)", nombre, eliminados);
        return eliminados;
    }

    // ========== Exchanges ==========

    public void crearExchange(String nombre, String tipo, boolean durable) {
        if (existeExchange(nombre)) {
            throw new ConflictoRecursoException("El exchange '" + nombre + "' ya existe");
        }
        Exchange exchange = new ExchangeBuilder(nombre, tipo.toLowerCase()).durable(durable).build();
        rabbitAdmin.declareExchange(exchange);
        log.info("[ADMIN] Exchange creado: {} (tipo={}, durable={})", nombre, tipo.toLowerCase(), durable);
    }

    public void eliminarExchange(String nombre) {
        validarNoProtegido(nombre);
        if (!existeExchange(nombre)) {
            throw new RecursoNoEncontradoException("El exchange '" + nombre + "' no existe");
        }
        rabbitAdmin.deleteExchange(nombre);
        log.info("[ADMIN] Exchange eliminado: {}", nombre);
    }

    // ========== Bindings ==========

    public void crearBinding(String cola, String exchange, String routingKey) {
        verificarColaYExchange(cola, exchange);
        rabbitAdmin.declareBinding(construirBinding(cola, exchange, routingKey));
        log.info("[ADMIN] Binding creado: {} --({})--> {}", exchange, routingKey, cola);
    }

    public void eliminarBinding(String cola, String exchange, String routingKey) {
        validarNoProtegido(cola);
        verificarColaYExchange(cola, exchange);
        rabbitAdmin.removeBinding(construirBinding(cola, exchange, routingKey));
        log.info("[ADMIN] Binding eliminado: {} --({})--> {}", exchange, routingKey, cola);
    }

    // ========== Validaciones internas ==========

    private void validarNoProtegido(String nombre) {
        if (RECURSOS_PROTEGIDOS.contains(nombre)) {
            throw new ConflictoRecursoException("'" + nombre + "' es un recurso del sistema y no se puede eliminar");
        }
    }

    private void verificarColaYExchange(String cola, String exchange) {
        if (!existeCola(cola)) {
            throw new RecursoNoEncontradoException("La cola '" + cola + "' no existe");
        }
        if (!existeExchange(exchange)) {
            throw new RecursoNoEncontradoException("El exchange '" + exchange + "' no existe");
        }
    }

    private boolean existeCola(String nombre) {
        return rabbitAdmin.getQueueInfo(nombre) != null;
    }

    /**
     * RabbitAdmin no tiene un método para consultar exchanges, así que se usa
     * una declaración pasiva: solo comprueba si existe, sin crearlo.
     */
    private boolean existeExchange(String nombre) {
        try {
            rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(nombre));
            return true;
        } catch (AmqpConnectException e) {
            // RabbitMQ no responde: no es que el exchange no exista
            throw e;
        } catch (AmqpException e) {
            return false;
        }
    }

    private Binding construirBinding(String cola, String exchange, String routingKey) {
        return new Binding(cola, Binding.DestinationType.QUEUE, exchange, routingKey, null);
    }
}