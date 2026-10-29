package ai.flowguard.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class KafkaRecordJsonMaps {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private KafkaRecordJsonMaps() {
    }

    public static Map<String, Object> parse(ObjectMapper objectMapper, byte[] payload) {
        if (payload == null || payload.length == 0) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.isArray() && root.size() == 2 && root.get(1).isObject()) {
                return objectMapper.convertValue(root.get(1), MAP);
            }
            if (root.isObject()) {
                return objectMapper.convertValue(root, MAP);
            }
            throw new IllegalArgumentException("Expected JSON object Kafka payload");
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid Kafka JSON payload", ex);
        }
    }

    public static Map<String, Object> parse(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return parse(objectMapper, json.getBytes(StandardCharsets.UTF_8));
    }
}
