package ai.flowguard.investigation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Service
public class EvidenceCacheService {

    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public EvidenceCacheService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void cacheInvestigationState(String investigationId, Map<String, Object> state) {
        redis.opsForValue().set(key(investigationId), writeJson(state), TTL);
    }

    public Optional<Map> getInvestigationState(String investigationId) {
        String raw = redis.opsForValue().get(key(investigationId));
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, Map.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void storeProcessedEvidence(Map<String, Object> message) {
        Object experimentId = message.get("experimentId");
        if (experimentId == null) {
            return;
        }
        redis.opsForValue().set("evidence:" + experimentId, writeJson(message), TTL);
    }

    public Optional<Map> getProcessedEvidence(String experimentId) {
        String raw = redis.opsForValue().get("evidence:" + experimentId);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(raw, Map.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static String key(String investigationId) {
        return "investigation:" + investigationId;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
