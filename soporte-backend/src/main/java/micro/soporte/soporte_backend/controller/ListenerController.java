package micro.soporte.soporte_backend.controller;

import micro.soporte.soporte_backend.Service.ListenerMGService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/listeners")
public class ListenerController {

    private final ListenerMGService listenerService;

    public ListenerController(ListenerMGService listenerService) {
        this.listenerService = listenerService;
    }

    @GetMapping
    public ResponseEntity<Object> getAllListeners() {
        return ResponseEntity.ok(listenerService.getAllListenerIds());
    }

    @PostMapping("/{listenerId}/pause")
    public ResponseEntity<String> pauseListener(@PathVariable String listenerId) {
        listenerService.pauseListener(listenerId);
        return ResponseEntity.ok("Listener pausado: " + listenerId);
    }

    @PostMapping("/{listenerId}/resume")
    public ResponseEntity<String> resumeListener(@PathVariable String listenerId) {
        listenerService.resumeListener(listenerId);
        return ResponseEntity.ok("Listener reanudado: " + listenerId);
    }
}