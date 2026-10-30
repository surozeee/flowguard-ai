package ai.flowguard.gateway.internal;

import ai.flowguard.gateway.messaging.GatewayInboxOutboxRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/gateway")
public class InternalGatewayMessagingController {

    private final GatewayInboxOutboxRepository repository;

    public InternalGatewayMessagingController(GatewayInboxOutboxRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(repository.summary());
    }

    @GetMapping("/inbox")
    public ResponseEntity<List<Map<String, Object>>> inbox(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(repository.listInbox(Math.min(limit, 200)));
    }

    @GetMapping("/outbox")
    public ResponseEntity<List<Map<String, Object>>> outbox(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(repository.listOutbox(Math.min(limit, 200)));
    }

    @GetMapping("/outbox/dlq")
    public ResponseEntity<List<Map<String, Object>>> outboxDlq(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(repository.listOutboxDlq(Math.min(limit, 200)));
    }

    @PostMapping("/outbox/dlq/{id}/replay")
    public ResponseEntity<Map<String, Object>> replayDlq(@PathVariable UUID id) {
        repository.requeueOutboxFromDlq(id);
        return ResponseEntity.ok(Map.of("replayed", true));
    }
}
