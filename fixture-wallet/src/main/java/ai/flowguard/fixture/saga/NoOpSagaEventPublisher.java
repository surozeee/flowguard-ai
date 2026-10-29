package ai.flowguard.fixture.saga;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.messaging-enabled", havingValue = "false")
public class NoOpSagaEventPublisher implements SagaEventPublisher {

    @Override
    public void publish(Map<String, Object> event) {
        // no-op
    }
}
