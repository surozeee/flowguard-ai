package ai.flowguard.fixture.risk;

public record RiskDecision(
        boolean allowed,
        String code,
        String message,
        String riskTier
) {
    public static RiskDecision allow(String tier) {
        return new RiskDecision(true, "ALLOW", "Within risk limits", tier);
    }

    public static RiskDecision deny(String tier, String code, String message) {
        return new RiskDecision(false, code, message, tier);
    }
}
