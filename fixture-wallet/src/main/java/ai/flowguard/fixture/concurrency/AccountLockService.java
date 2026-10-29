package ai.flowguard.fixture.concurrency;

import ai.flowguard.fixture.config.FixtureProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class AccountLockService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final FixtureProperties properties;
    private final StringRedisTemplate redis;

    public AccountLockService(FixtureProperties properties, ObjectProvider<StringRedisTemplate> redis) {
        this.properties = properties;
        this.redis = redis.getIfAvailable();
    }

    public AccountLock acquire(UUID accountId) {
        if (!properties.usesRedisIdempotencyLock() || redis == null) {
            return AccountLock.noop();
        }
        String token = UUID.randomUUID().toString();
        String key = "lock:account:" + accountId;
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return new AccountLock(key, token, redis);
        }
        throw new AccountLockException("Account lock busy: " + accountId);
    }

    public record AccountLock(String key, String token, StringRedisTemplate redis) implements AutoCloseable {

        static AccountLock noop() {
            return new AccountLock(null, null, null);
        }

        @Override
        public void close() {
            if (redis == null || key == null) {
                return;
            }
            String current = redis.opsForValue().get(key);
            if (token.equals(current)) {
                redis.delete(key);
            }
        }
    }
}
