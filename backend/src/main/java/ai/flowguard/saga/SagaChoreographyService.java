package ai.flowguard.saga;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import ai.flowguard.saga.payload.SagaTraceRow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SagaChoreographyService {

    private final SagaChoreographyRepository repository;
    private final Counter duplicateSkipped;

    public SagaChoreographyService(SagaChoreographyRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.duplicateSkipped = meterRegistry.counter("flowguard.saga.choreography.duplicate_skipped");
    }

    public void onSagaEvent(Map<String, Object> event) {
        String sagaIdRaw = stringField(event, "sagaId");
        if (sagaIdRaw.isBlank()) {
            return;
        }
        UUID sagaId = UUID.fromString(sagaIdRaw);
        boolean inserted = repository.appendIfAbsent(
                sagaId,
                stringField(event, "idempotencyKey"),
                stringField(event, "eventType"),
                stringField(event, "sagaState"),
                stringField(event, "step"),
                event);
        if (!inserted) {
            duplicateSkipped.increment();
        }
    }

    public List<SagaTraceRow> traceByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    private static String stringField(Map<String, Object> event, String key) {
        Object value = event.get(key);
        if (value == null && "sagaId".equals(key)) {
            value = event.get("saga_id");
        } else if (value == null && "idempotencyKey".equals(key)) {
            value = event.get("idempotency_key");
        } else if (value == null && "eventType".equals(key)) {
            value = event.get("event_type");
        } else if (value == null && "sagaState".equals(key)) {
            value = event.get("saga_state");
        }
        return value == null ? "" : value.toString();
    }
}
