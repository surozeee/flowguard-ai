package ai.flowguard.fixture.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class OutboxPayloads {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private OutboxPayloads() {
    }

    public static Map<String, Object> parse(ObjectMapper objectMapper, Object raw) {
        if (raw == null) {
            return Map.of();
        }
        try {
            if (raw instanceof String json) {
                return objectMapper.readValue(json, MAP);
            }
            String pgJson = pgObjectJson(raw);
            if (pgJson != null) {
                if (pgJson.isBlank()) {
                    return Map.of();
                }
                return objectMapper.readValue(pgJson, MAP);
            }
            if (raw instanceof Map<?, ?> map) {
                if (map.containsKey("eventType") || map.containsKey("sagaId") || map.containsKey("accountId")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> direct = (Map<String, Object>) map;
                    return direct;
                }
                Object nested = map.get("value");
                if (nested instanceof String json) {
                    return objectMapper.readValue(json, MAP);
                }
                if (nested instanceof Map<?, ?> nestedMap) {
                    Object inner = nestedMap.get("value");
                    if (inner instanceof String json) {
                        return objectMapper.readValue(json, MAP);
                    }
                }
            }
            return objectMapper.convertValue(raw, MAP);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse outbox payload_json", ex);
        }
    }

    private static String pgObjectJson(Object raw) {
        if (raw == null || !"org.postgresql.util.PGobject".equals(raw.getClass().getName())) {
            return null;
        }
        try {
            Object value = raw.getClass().getMethod("getValue").invoke(raw);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
