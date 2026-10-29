package ai.flowguard.fixture.saga;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.messaging-enabled", matchIfMissing = true)
public class OutboxSagaEventPublisher implements SagaEventPublisher {

    private final SagaEventOutboxRepository outboxRepository;

    public OutboxSagaEventPublisher(SagaEventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void publish(Map<String, Object> event) {
        String idempotencyKey = event.get("idempotencyKey") == null ? null : event.get("idempotencyKey").toString();
        outboxRepository.enqueue(UUID.randomUUID(), idempotencyKey, event);
    }
}
