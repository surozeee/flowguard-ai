package ai.flowguard.saga.payload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record SagaVerifyVerdict(
        boolean passed,
        String idempotencyKey,
        int eventCount,
        List<String> eventTypes,
        String reason
) {
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("passed", passed);
        map.put("idempotencyKey", idempotencyKey);
        map.put("eventCount", eventCount);
        map.put("eventTypes", eventTypes == null ? List.of() : eventTypes);
        map.put("reason", reason);
        return map;
    }
}
