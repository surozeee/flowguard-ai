package ai.flowguard.fixture.internal;

import ai.flowguard.fixture.outbox.FixtureOutboxPendingRepository;
import ai.flowguard.fixture.outbox.OutboxDlqRepository;
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
@RequestMapping("/internal/outbox")
public class InternalOutboxAdminController {

    private final OutboxDlqRepository outboxDlqRepository;
    private final FixtureOutboxPendingRepository pendingRepository;

    public InternalOutboxAdminController(
            OutboxDlqRepository outboxDlqRepository,
            FixtureOutboxPendingRepository pendingRepository) {
        this.outboxDlqRepository = outboxDlqRepository;
        this.pendingRepository = pendingRepository;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(Map.of(
                "sagaPendingCount", pendingRepository.countSagaPending(),
                "transactionPendingCount", pendingRepository.countTransactionPending(),
                "sagaDlqCount", outboxDlqRepository.countSagaDlq(),
                "transactionDlqCount", outboxDlqRepository.countTransactionDlq()));
    }

    @GetMapping("/saga-pending")
    public ResponseEntity<List<Map<String, Object>>> sagaPending(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(pendingRepository.listSagaPending(Math.min(limit, 200)));
    }

    @GetMapping("/transaction-pending")
    public ResponseEntity<List<Map<String, Object>>> transactionPending(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(pendingRepository.listTransactionPending(Math.min(limit, 200)));
    }

    @GetMapping("/saga-dlq")
    public ResponseEntity<List<Map<String, Object>>> sagaDlq(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(outboxDlqRepository.listSagaDlq(Math.min(limit, 200)));
    }

    @GetMapping("/transaction-dlq")
    public ResponseEntity<List<Map<String, Object>>> transactionDlq(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(outboxDlqRepository.listTransactionDlq(Math.min(limit, 200)));
    }

    @PostMapping("/saga-dlq/{id}/replay")
    public ResponseEntity<Map<String, Object>> replaySagaDlq(@PathVariable UUID id) {
        UUID outboxId = outboxDlqRepository.requeueSagaFromDlq(id);
        return ResponseEntity.ok(Map.of("replayed", true, "outboxId", outboxId));
    }

    @PostMapping("/transaction-dlq/{id}/replay")
    public ResponseEntity<Map<String, Object>> replayTransactionDlq(@PathVariable UUID id) {
        UUID outboxId = outboxDlqRepository.requeueTransactionFromDlq(id);
        return ResponseEntity.ok(Map.of("replayed", true, "outboxId", outboxId));
    }

}
