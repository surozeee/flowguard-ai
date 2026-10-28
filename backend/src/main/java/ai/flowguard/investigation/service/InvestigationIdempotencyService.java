package ai.flowguard.investigation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class InvestigationIdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String PREFIX = "idem:investigation:start:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public InvestigationIdempotencyService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<UUID> findExisting(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        String raw = redis.opsForValue().get(PREFIX + idempotencyKey);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            Map<?, ?> map = objectMapper.readValue(raw, Map.class);
            Object id = map.get("investigationId");
            return id == null ? Optional.empty() : Optional.of(UUID.fromString(id.toString()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void record(String idempotencyKey, UUID investigationId, Map<String, Object> responseBody) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try {
            Map<String, Object> payload = Map.of(
                    "investigationId", investigationId.toString(),
                    "response", responseBody);
            redis.opsForValue().set(PREFIX + idempotencyKey, objectMapper.writeValueAsString(payload), TTL);
        } catch (JsonProcessingException ignored) {
            // best-effort idempotency cache
        }
    }
}
