package ai.flowguard.fixture.risk;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.redis-idempotency-enabled", matchIfMissing = true)
public class RedisRiskUsageStore implements RiskUsageStore {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final StringRedisTemplate redis;

    public RedisRiskUsageStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long dailyVolumeUsed(UUID accountId) {
        return parseLong(redis.opsForValue().get(dailyKey(accountId)));
    }

    @Override
    public void addDailyVolume(UUID accountId, long amountMinor) {
        String key = dailyKey(accountId);
        redis.opsForValue().increment(key, amountMinor);
        redis.expire(key, Duration.ofDays(2));
    }

    @Override
    public long hourlyTxnCount(UUID accountId) {
        return parseLong(redis.opsForValue().get(hourKey(accountId)));
    }

    @Override
    public void incrementHourlyTxn(UUID accountId) {
        String key = hourKey(accountId);
        redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofHours(2));
    }

    private static String dailyKey(UUID accountId) {
        return "risk:daily:" + accountId + ":" + LocalDate.now().format(DAY);
    }

    private static String hourKey(UUID accountId) {
        return "risk:velocity:" + accountId + ":" + (System.currentTimeMillis() / TimeUnit.HOURS.toMillis(1));
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }
}
