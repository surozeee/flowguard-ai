package ai.flowguard.fixture.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "flowguard.fixture.redis-idempotency-enabled", matchIfMissing = true)
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String KEY_PREFIX = "idem:payment:";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;

    public RedisIdempotencyStore(StringRedisTemplate redis, JdbcTemplate jdbcTemplate) {
        this.redis = redis;
        this.jdbc = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findPaymentId(String idempotencyKey) {
        String cached = redis.opsForValue().get(redisKey(idempotencyKey));
        if (cached != null && isPaymentId(cached)) {
            return Optional.of(UUID.fromString(cached));
        }
        return jdbc.query(
                "SELECT payment_id FROM payment_idempotency_audit WHERE idempotency_key = ?",
                rs -> rs.next() ? Optional.of((UUID) rs.getObject("payment_id")) : Optional.empty(),
                idempotencyKey);
    }

    @Override
    public IdempotencyClaimResult tryClaim(String idempotencyKey, int ttlSeconds) {
        Optional<UUID> existing = findPaymentId(idempotencyKey);
        if (existing.isPresent()) {
            return IdempotencyClaimResult.replay(existing.get());
        }
        String pendingToken = "PENDING:" + UUID.randomUUID();
        Boolean claimed = redis.opsForValue().setIfAbsent(
                redisKey(idempotencyKey), pendingToken, Duration.ofSeconds(ttlSeconds));
        if (Boolean.TRUE.equals(claimed)) {
            return IdempotencyClaimResult.granted(pendingToken);
        }
        String current = redis.opsForValue().get(redisKey(idempotencyKey));
        if (current != null && isPaymentId(current)) {
            return IdempotencyClaimResult.replay(UUID.fromString(current));
        }
        return IdempotencyClaimResult.inProgress();
    }

    @Override
    public void confirm(String idempotencyKey, UUID accountId, UUID paymentId, int ttlSeconds) {
        redis.opsForValue().set(redisKey(idempotencyKey), paymentId.toString(), Duration.ofSeconds(ttlSeconds));
        jdbc.update("""
                INSERT INTO payment_idempotency_audit (idempotency_key, payment_id, account_id)
                VALUES (?, ?, ?)
                ON CONFLICT (idempotency_key) DO UPDATE SET payment_id = EXCLUDED.payment_id, recorded_at = now()
                """, idempotencyKey, paymentId, accountId);
    }

    @Override
    public void release(String idempotencyKey) {
        String current = redis.opsForValue().get(redisKey(idempotencyKey));
        if (current != null && current.startsWith("PENDING:")) {
            redis.delete(redisKey(idempotencyKey));
        }
    }

    @Override
    public void clearKey(String idempotencyKey) {
        redis.delete(redisKey(idempotencyKey));
    }

    private static String redisKey(String idempotencyKey) {
        return KEY_PREFIX + idempotencyKey;
    }

    private static boolean isPaymentId(String value) {
        return UUID_PATTERN.matcher(value).matches();
    }
}
