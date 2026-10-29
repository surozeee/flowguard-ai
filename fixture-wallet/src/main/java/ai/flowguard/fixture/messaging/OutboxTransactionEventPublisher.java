package ai.flowguard.fixture.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.messaging-enabled", matchIfMissing = true)
public class OutboxTransactionEventPublisher implements TransactionEventPublisher {

    private final TransactionEventOutboxRepository outboxRepository;

    public OutboxTransactionEventPublisher(TransactionEventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void publish(Map<String, Object> event) {
        Object accountId = event.getOrDefault("accountId", "unknown");
        outboxRepository.enqueue(UUID.randomUUID(), accountId.toString(), event);
    }
}
