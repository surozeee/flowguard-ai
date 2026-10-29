package ai.flowguard.fixture.saga;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentSagaMetrics {

    private final Counter completed;
    private final Counter replay;
    private final Counter inProgressRejected;
    private final Counter compensated;

    public PaymentSagaMetrics(MeterRegistry meterRegistry) {
        this.completed = meterRegistry.counter("flowguard.fixture.saga.completed");
        this.replay = meterRegistry.counter("flowguard.fixture.saga.replay");
        this.inProgressRejected = meterRegistry.counter("flowguard.fixture.saga.in_progress_rejected");
        this.compensated = meterRegistry.counter("flowguard.fixture.saga.compensated");
    }

    public void recordCompleted() {
        completed.increment();
    }

    public void recordReplay() {
        replay.increment();
    }

    public void recordInProgressRejected() {
        inProgressRejected.increment();
    }

    public void recordCompensated() {
        compensated.increment();
    }
}
