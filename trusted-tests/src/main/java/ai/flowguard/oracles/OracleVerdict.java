package ai.flowguard.oracles;

import java.util.List;

public record OracleVerdict(
        boolean passed,
        String summary,
        List<InvariantViolation> violations
) {
    public static OracleVerdict pass() {
        return new OracleVerdict(true, "All checked invariants satisfied", List.of());
    }

    public static OracleVerdict fail(String summary, List<InvariantViolation> violations) {
        return new OracleVerdict(false, summary, violations);
    }
}
