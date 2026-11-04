package ai.flowguard.day1;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class ApiEnvelope {

    private ApiEnvelope() {
    }

    public static Object data(Object body) {
        if (body instanceof Map<?, ?> map && map.containsKey("data")) {
            return map.get("data");
        }
        return body;
    }

    public static Map<String, Object> dataAsMap(Object body) {
        Object value = data(body);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("Expected map payload, got " + value);
    }

    public static List<Map<String, Object>> dataAsList(Object body) {
        Object value = data(body);
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        throw new IllegalStateException("Expected list payload, got " + value);
    }
}
