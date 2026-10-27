package ai.flowguard.oracles;

import java.util.Map;

public record InvariantViolation(
        InvariantId invariantId,
        String message,
        Map<String, Object> details
) {
}
