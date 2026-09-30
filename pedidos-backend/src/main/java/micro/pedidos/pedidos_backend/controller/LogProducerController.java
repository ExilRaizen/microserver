package micro.pedidos.pedidos_backend.controller;

import micro.pedidos.pedidos_backend.config.LogsRabbitMQConfig;
import micro.pedidos.pedidos_backend.messaging.Sender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin(origins = "http://localhost:5173") // Permite peticiones desde Vite
public class LogProducerController {

    @Autowired
    private Sender sender;

    @PostMapping("/log")
    public String sendLog(@RequestBody LogMessage logMessage) {
        // La routing key es el nivel del log (INFO, ERROR, etc.)
        sender.sendMessage(
                LogsRabbitMQConfig.EXCHANGE_NAME,
                logMessage.getLevel(),
                logMessage.getMessage()
        );
        return "Log enviado: " + logMessage.getMessage();
    }

    // DTO para el cuerpo de la petición
    public static class LogMessage {
        private String level;
        private String message;

        // Getters y Setters
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}