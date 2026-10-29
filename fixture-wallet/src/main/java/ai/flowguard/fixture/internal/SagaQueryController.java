package ai.flowguard.fixture.internal;

import ai.flowguard.fixture.saga.PaymentSagaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/sagas")
public class SagaQueryController {

    private final PaymentSagaRepository paymentSagaRepository;

    public SagaQueryController(PaymentSagaRepository paymentSagaRepository) {
        this.paymentSagaRepository = paymentSagaRepository;
    }

    @GetMapping("/idempotency/{idempotencyKey}")
    public ResponseEntity<List<Map<String, Object>>> byIdempotencyKey(@PathVariable String idempotencyKey) {
        return ResponseEntity.ok(paymentSagaRepository.findByIdempotencyKey(idempotencyKey));
    }

    @GetMapping("/stale")
    public ResponseEntity<List<Map<String, Object>>> stale(
            @RequestParam(defaultValue = "5") int olderThanMinutes) {
        java.time.Instant cutoff = java.time.Instant.now().minus(Math.max(1, olderThanMinutes), java.time.temporal.ChronoUnit.MINUTES);
        return ResponseEntity.ok(paymentSagaRepository.findStaleActive(cutoff));
    }
}
