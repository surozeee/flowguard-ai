package ai.flowguard.meta;

import com.fasterxml.jackson.databind.JsonNode;

public record MetaJsonResult(
        JsonNode json,
        String source,
        Integer httpStatus,
        Integer inputTokens,
        Integer outputTokens,
        String errorClass
) {
    public boolean fallback() {
        return MetaProposal.FALLBACK.equals(source);
    }

    public boolean stub() {
        return MetaProposal.STUB.equals(source);
    }
}
