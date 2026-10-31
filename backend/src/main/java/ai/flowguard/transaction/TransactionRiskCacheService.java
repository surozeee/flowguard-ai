package ai.flowguard.transaction;

import ai.flowguard.transaction.payload.CachedTransactionEvent;
import ai.flowguard.transaction.payload.CustomerRiskSnapshot;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class TransactionRiskCacheService {

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TransactionRiskCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void onTransactionEvent(Map<String, Object> event) {
        Object accountId = event.get("accountId");
        if (accountId == null) {
            return;
        }
        String accountKey = accountId.toString();
        CachedTransactionEvent cached = new CachedTransactionEvent(
                str(event.get("eventType")),
                accountKey,
                str(event.get("idempotencyKey")),
                str(event.getOrDefault("riskTier", "UNKNOWN")));
        redis.opsForValue().set("customer:txn:last:" + accountKey, writeJson(cached), TTL);

        if (cached.idempotencyKey() != null) {
            redis.opsForValue().set("idem:global:" + cached.idempotencyKey(), writeJson(cached), TTL);
        }

        if ("PAYMENT_SETTLED".equals(cached.eventType())) {
            if (cached.idempotencyKey() != null) {
                String settledOnceKey = "customer:txn:settled-idem:" + cached.idempotencyKey();
                Boolean first = redis.opsForValue().setIfAbsent(settledOnceKey, "1", TTL);
                if (!Boolean.TRUE.equals(first)) {
                    return;
                }
            }
            redis.opsForHash().put("customer:risk:profile:" + accountKey, "riskTier",
                    cached.riskTier() == null ? "UNKNOWN" : cached.riskTier());
            redis.expire("customer:risk:profile:" + accountKey, TTL);
            redis.opsForValue().increment("customer:txn:count:" + accountKey);
            redis.expire("customer:txn:count:" + accountKey, TTL);
        }
    }

    public Optional<CachedTransactionEvent> getLastTransaction(String accountId) {
        return readEvent(redis.opsForValue().get("customer:txn:last:" + accountId));
    }

    public Optional<CachedTransactionEvent> getIdempotencyRecord(String idempotencyKey) {
        return readEvent(redis.opsForValue().get("idem:global:" + idempotencyKey));
    }

    public CustomerRiskSnapshot getRiskSnapshot(String accountId) {
        Map<Object, Object> profile = redis.opsForHash().entries("customer:risk:profile:" + accountId);
        String count = redis.opsForValue().get("customer:txn:count:" + accountId);
        Object tier = profile.getOrDefault("riskTier", "UNKNOWN");
        return new CustomerRiskSnapshot(
                accountId,
                String.valueOf(tier),
                count == null ? 0 : Long.parseLong(count));
    }

    private Optional<CachedTransactionEvent> readEvent(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, CachedTransactionEvent.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
