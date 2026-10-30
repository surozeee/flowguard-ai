package ai.flowguard.gateway.core;

import java.nio.charset.StandardCharsets;

public final class GatewayApiEnvelope {

    private GatewayApiEnvelope() {
    }

    public static byte[] errorJson(String message) {
        String safe = message == null ? "Error" : message.replace("\"", "'");
        return ("{\"status\":\"ERROR\",\"message\":\"" + safe + "\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
