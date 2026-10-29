package ai.flowguard.fixture.messaging;

import java.util.Map;

public interface TransactionEventPublisher {

    void publish(Map<String, Object> event);
}
