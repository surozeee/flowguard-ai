package ai.flowguard.saga;

import ai.flowguard.saga.payload.SagaTraceRow;
import ai.flowguard.saga.payload.SagaVerifyVerdict;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class SagaTraceVerifier {

    private final SagaChoreographyRepository repository;

    public SagaTraceVerifier(SagaChoreographyRepository repository) {
        this.repository = repository;
    }

    public SagaVerifyVerdict verifyWithRetry(String idempotencyKey, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            SagaVerifyVerdict verdict = verify(idempotencyKey);
            if (verdict.passed()) {
                return verdict;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return verify(idempotencyKey);
    }

    public SagaVerifyVerdict verify(String idempotencyKey) {
        List<SagaTraceRow> trace = repository.findByIdempotencyKey(idempotencyKey);
        if (trace.isEmpty()) {
            return new SagaVerifyVerdict(false, idempotencyKey, 0, List.of(), "NO_CHOREOGRAPHY_EVENTS");
        }
        List<String> eventTypes = trace.stream().map(SagaTraceRow::eventType).toList();
        boolean started = eventTypes.contains("SAGA_STARTED");
        boolean terminal = eventTypes.stream().anyMatch(t ->
                t.equals("SAGA_COMPLETED") || t.equals("SAGA_IDEMPOTENT_REPLAY"));
        boolean passed = started && terminal;
        return new SagaVerifyVerdict(
                passed,
                idempotencyKey,
                trace.size(),
                eventTypes,
                passed ? "OK" : "MISSING_START_OR_TERMINAL");
    }
}
