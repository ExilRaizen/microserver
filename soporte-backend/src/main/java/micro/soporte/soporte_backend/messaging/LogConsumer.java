package micro.soporte.soporte_backend.messaging;

import micro.soporte.soporte_backend.config.LogsRabbitMQConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class LogConsumer {

    @RabbitListener(queues = LogsRabbitMQConfig.ALL_LOGS_QUEUE)
    public void receiveAllLogs(String message) {
        System.out.println("[MONITOR GENERAL] Log recibido: " + message);
    }

    @RabbitListener(queues = LogsRabbitMQConfig.ERRORS_ONLY_QUEUE)
    public void receiveErrorLogs(String message) {
        // Simular una alerta
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.out.println("[ALERTA CRÍTICA] Error detectado: " + message);
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    }
}