package ai.flowguard.fixture.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.messaging-enabled", havingValue = "false")
public class NoOpTransactionEventPublisher implements TransactionEventPublisher {

    @Override
    public void publish(Map<String, Object> event) {
        // no-op for unit tests without Kafka
    }
}
