package ai.flowguard.meta;

public record MetaProposal(
        InvestigationPlan plan,
        String source,
        Integer httpStatus,
        Integer inputTokens,
        Integer outputTokens,
        String errorClass
) {
    public static final String LIVE = "LIVE";
    public static final String STUB = "STUB";
    public static final String FALLBACK = "FALLBACK";

    public boolean live() {
        return LIVE.equals(source);
    }

    public boolean fallback() {
        return FALLBACK.equals(source);
    }

    public static MetaProposal stub(InvestigationPlan plan) {
        return new MetaProposal(plan, STUB, null, null, null, null);
    }

    public static MetaProposal live(
            InvestigationPlan plan, Integer httpStatus, Integer inputTokens, Integer outputTokens) {
        return new MetaProposal(plan, LIVE, httpStatus, inputTokens, outputTokens, null);
    }

    public static MetaProposal fallback(
            InvestigationPlan plan, Integer httpStatus, Integer inputTokens, Integer outputTokens, String errorClass) {
        return new MetaProposal(plan, FALLBACK, httpStatus, inputTokens, outputTokens, errorClass);
    }
}
