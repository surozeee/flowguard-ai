package ai.flowguard.fixture.saga;

import java.util.Map;

public interface SagaEventPublisher {

    void publish(Map<String, Object> event);
}
