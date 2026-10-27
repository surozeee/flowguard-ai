package ai.flowguard.oracles;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable JDBC/SQL financial invariant checker — not modifiable by AI patches at runtime.
 */
public class FinancialOracle {

    public static final String VERSION = "1.0.0";

    private final JdbcTemplate jdbc;

    public FinancialOracle(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public OracleVerdict verifyAll() {
        List<InvariantViolation> violations = new ArrayList<>();
        violations.addAll(checkJournalBalanced());
        violations.addAll(checkAccountReconciles());
        violations.addAll(checkNonNegative());
        violations.addAll(checkPaymentEffectOnce());
        if (violations.isEmpty()) {
            return OracleVerdict.pass();
        }
        return OracleVerdict.fail("One or more invariants violated", violations);
    }

    public OracleVerdict verify(InvariantId invariantId) {
        List<InvariantViolation> violations = switch (invariantId) {
            case JOURNAL_BALANCED -> checkJournalBalanced();
            case ACCOUNT_RECONCILES -> checkAccountReconciles();
            case NONNEGATIVE -> checkNonNegative();
            case PAYMENT_EFFECT_ONCE -> checkPaymentEffectOnce();
            case VALID_STATE_TRANSITION -> List.of();
        };
        if (violations.isEmpty()) {
            return OracleVerdict.pass();
        }
        return OracleVerdict.fail(invariantId + " violated", violations);
    }

    private List<InvariantViolation> checkJournalBalanced() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT jl.journal_id, jl.currency, SUM(jl.signed_amount_minor) AS net_minor
                FROM journal_lines jl
                JOIN journals j ON j.id = jl.journal_id
                WHERE j.posting_status = 'POSTED'
                GROUP BY jl.journal_id, jl.currency
                HAVING SUM(jl.signed_amount_minor) <> 0
                """);
        return rows.stream()
                .map(r -> new InvariantViolation(
                        InvariantId.JOURNAL_BALANCED,
                        "Unbalanced journal posting",
                        Map.of("journalId", r.get("journal_id"), "netMinor", r.get("net_minor"))))
                .toList();
    }

    private List<InvariantViolation> checkAccountReconciles() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id,
                       a.current_balance_minor AS stored,
                       a.opening_balance_minor + COALESCE(
                         SUM(CASE WHEN j.id IS NOT NULL THEN jl.signed_amount_minor ELSE 0 END),0
                       ) AS derived
                FROM accounts a
                LEFT JOIN journal_lines jl ON jl.account_id = a.id
                LEFT JOIN journals j ON j.id = jl.journal_id AND j.posting_status = 'POSTED'
                GROUP BY a.id, a.current_balance_minor, a.opening_balance_minor
                HAVING a.current_balance_minor <>
                       a.opening_balance_minor + COALESCE(
                         SUM(CASE WHEN j.id IS NOT NULL THEN jl.signed_amount_minor ELSE 0 END),0
                       )
                """);
        return rows.stream()
                .map(r -> new InvariantViolation(
                        InvariantId.ACCOUNT_RECONCILES,
                        "Stored balance does not match ledger-derived balance",
                        Map.of("accountId", r.get("id"), "stored", r.get("stored"), "derived", r.get("derived"))))
                .toList();
    }

    private List<InvariantViolation> checkNonNegative() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, current_balance_minor FROM accounts
                WHERE overdraft_allowed = false AND current_balance_minor < 0
                """);
        return rows.stream()
                .map(r -> new InvariantViolation(
                        InvariantId.NONNEGATIVE,
                        "Negative balance on non-overdraft account",
                        Map.of("accountId", r.get("id"), "balanceMinor", r.get("current_balance_minor"))))
                .toList();
    }

    private List<InvariantViolation> checkPaymentEffectOnce() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.idempotency_key,
                       COUNT(DISTINCT p.id) AS payment_count,
                       COALESCE(SUM(jl.signed_amount_minor) FILTER (WHERE jl.signed_amount_minor > 0), 0) AS credited_minor
                FROM payments p
                LEFT JOIN journals j ON j.payment_id = p.id AND j.posting_status = 'POSTED'
                LEFT JOIN journal_lines jl ON jl.journal_id = j.id
                GROUP BY p.idempotency_key
                HAVING COUNT(DISTINCT p.id) > 1
                """);
        List<InvariantViolation> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> details = new HashMap<>();
            details.put("idempotencyKey", r.get("idempotency_key"));
            details.put("paymentCount", r.get("payment_count"));
            details.put("creditedMinor", r.get("credited_minor"));
            out.add(new InvariantViolation(
                    InvariantId.PAYMENT_EFFECT_ONCE,
                    "Duplicate payment records for same idempotency key",
                    details));
        }
        return out;
    }

    public long sumCreditsForKey(String idempotencyKey) {
        Long sum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(jl.signed_amount_minor), 0)
                FROM payments p
                JOIN journals j ON j.payment_id = p.id AND j.posting_status = 'POSTED'
                JOIN journal_lines jl ON jl.journal_id = j.id AND jl.signed_amount_minor > 0
                WHERE p.idempotency_key = ?
                """, Long.class, idempotencyKey);
        return sum == null ? 0L : sum;
    }
}
