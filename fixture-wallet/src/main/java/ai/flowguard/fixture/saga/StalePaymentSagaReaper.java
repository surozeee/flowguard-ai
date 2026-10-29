package ai.flowguard.fixture.saga;

import ai.flowguard.fixture.config.FixtureProperties;
import ai.flowguard.fixture.idempotency.IdempotencyStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StalePaymentSagaReaper {

    private final PaymentSagaRepository paymentSagaRepository;
    private final IdempotencyStore idempotencyStore;
    private final FixtureProperties properties;
    private final Counter reapedCounter;

    public StalePaymentSagaReaper(
            PaymentSagaRepository paymentSagaRepository,
            IdempotencyStore idempotencyStore,
            FixtureProperties properties,
            MeterRegistry meterRegistry) {
        this.paymentSagaRepository = paymentSagaRepository;
        this.idempotencyStore = idempotencyStore;
        this.properties = properties;
        this.reapedCounter = meterRegistry.counter("flowguard.fixture.saga.reaped");
    }

    @Scheduled(fixedDelayString = "${flowguard.fixture.stale-saga-reap-ms:60000}")
    public void reapStaleActiveSagas() {
        Instant cutoff = Instant.now().minus(properties.staleSagaMinutes(), ChronoUnit.MINUTES);
        List<Map<String, Object>> stale = paymentSagaRepository.findStaleActive(cutoff);
        for (Map<String, Object> row : stale) {
            UUID sagaId = (UUID) row.get("id");
            String idempotencyKey = (String) row.get("idempotency_key");
            if (properties.usesRedisIdempotencyLock()) {
                idempotencyStore.release(idempotencyKey);
            }
            paymentSagaRepository.fail(sagaId, "Stale saga reaped after " + properties.staleSagaMinutes() + "m inactivity");
            reapedCounter.increment();
        }
    }
}
