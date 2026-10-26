package ai.flowguard.fixture.risk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.redis-idempotency-enabled", havingValue = "false")
public class InMemoryRiskUsageStore implements RiskUsageStore {

    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public long dailyVolumeUsed(UUID accountId) {
        return counter("daily:" + accountId + ":" + LocalDate.now()).get();
    }

    @Override
    public void addDailyVolume(UUID accountId, long amountMinor) {
        counter("daily:" + accountId + ":" + LocalDate.now()).addAndGet(amountMinor);
    }

    @Override
    public long hourlyTxnCount(UUID accountId) {
        return counter("hour:" + accountId + ":" + currentHour()).get();
    }

    @Override
    public void incrementHourlyTxn(UUID accountId) {
        counter("hour:" + accountId + ":" + currentHour()).incrementAndGet();
    }

    private AtomicLong counter(String key) {
        return counters.computeIfAbsent(key, k -> new AtomicLong());
    }

    private static long currentHour() {
        return System.currentTimeMillis() / 3_600_000L;
    }

    @Override
    public void clearAll() {
        counters.clear();
    }
}
