package ai.flowguard.fixture.service;

import ai.flowguard.fixture.config.FixtureProperties;
import ai.flowguard.fixture.domain.PaymentStatus;
import ai.flowguard.fixture.race.RaceCheckpoint;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentLedgerService {

    private final JdbcTemplate jdbc;
    private final FixtureProperties properties;

    public PaymentLedgerService(JdbcTemplate jdbcTemplate, FixtureProperties properties) {
        this.jdbc = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID credit(String clientReference, String idempotencyKey, UUID targetAccountId, long amountMinor) {
        if ("dup-credit-vulnerable".equals(properties.variant())) {
            return creditVulnerable(clientReference, idempotencyKey, targetAccountId, amountMinor);
        }
        return creditSafe(clientReference, idempotencyKey, targetAccountId, amountMinor);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID debit(UUID sourceAccountId, UUID targetAccountId, long amountMinor, String idempotencyKey) {
        if ("debit-race-vulnerable".equals(properties.variant())) {
            return debitVulnerable(sourceAccountId, targetAccountId, amountMinor, idempotencyKey);
        }
        return debitSafe(sourceAccountId, targetAccountId, amountMinor, idempotencyKey);
    }

    private UUID creditVulnerable(String clientReference, String idempotencyKey, UUID targetAccountId, long amountMinor) {
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id FROM payments WHERE idempotency_key = ?",
                idempotencyKey);
        RaceCheckpoint.pauseIfEnabled(RaceCheckpoint.Point.AFTER_IDEMPOTENCY_LOOKUP);
        if (!existing.isEmpty()) {
            return (UUID) existing.getFirst().get("id");
        }

        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payments (id, client_reference, idempotency_key, target_account_id, amount_minor, currency, status)
                VALUES (?, ?, ?, ?, ?, 'USD', ?)
                """, paymentId, clientReference, idempotencyKey, targetAccountId, amountMinor, PaymentStatus.SETTLED.name());

        postCreditJournal(paymentId, null, targetAccountId, amountMinor);
        jdbc.update("UPDATE accounts SET current_balance_minor = current_balance_minor + ? WHERE id = ?",
                amountMinor, targetAccountId);
        return paymentId;
    }

    private UUID creditSafe(String clientReference, String idempotencyKey, UUID targetAccountId, long amountMinor) {
        List<Map<String, Object>> existing = jdbc.queryForList(
                "SELECT id FROM payments WHERE idempotency_key = ? FOR UPDATE",
                idempotencyKey);
        if (!existing.isEmpty()) {
            return (UUID) existing.getFirst().get("id");
        }

        UUID paymentId = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO payments (id, client_reference, idempotency_key, target_account_id, amount_minor, currency, status)
                    VALUES (?, ?, ?, ?, ?, 'USD', ?)
                    """, paymentId, clientReference, idempotencyKey, targetAccountId, amountMinor, PaymentStatus.SETTLED.name());
        } catch (Exception e) {
            List<Map<String, Object>> retry = jdbc.queryForList(
                    "SELECT id FROM payments WHERE idempotency_key = ?", idempotencyKey);
            if (!retry.isEmpty()) {
                return (UUID) retry.getFirst().get("id");
            }
            throw e;
        }

        postCreditJournal(paymentId, null, targetAccountId, amountMinor);
        jdbc.update("UPDATE accounts SET current_balance_minor = current_balance_minor + ? WHERE id = ?",
                amountMinor, targetAccountId);
        return paymentId;
    }

    private UUID debitVulnerable(UUID sourceAccountId, UUID targetAccountId, long amountMinor, String idempotencyKey) {
        Long balance = jdbc.queryForObject(
                "SELECT current_balance_minor FROM accounts WHERE id = ?",
                Long.class, sourceAccountId);
        RaceCheckpoint.pauseIfEnabled(RaceCheckpoint.Point.AFTER_BALANCE_READ);
        if (balance == null || balance < amountMinor) {
            throw new IllegalStateException("INSUFFICIENT_FUNDS");
        }
        jdbc.update("UPDATE accounts SET current_balance_minor = ? WHERE id = ?",
                balance - amountMinor, sourceAccountId);

        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payments (id, client_reference, idempotency_key, source_account_id, target_account_id,
                amount_minor, currency, status)
                VALUES (?, ?, ?, ?, ?, ?, 'USD', ?)
                """, paymentId, "transfer-" + idempotencyKey, idempotencyKey, sourceAccountId, targetAccountId,
                amountMinor, PaymentStatus.SETTLED.name());

        postCreditJournal(paymentId, sourceAccountId, targetAccountId, amountMinor);
        jdbc.update("UPDATE accounts SET current_balance_minor = current_balance_minor + ? WHERE id = ?",
                amountMinor, targetAccountId);
        return paymentId;
    }

    private UUID debitSafe(UUID sourceAccountId, UUID targetAccountId, long amountMinor, String idempotencyKey) {
        int updated = jdbc.update("""
                UPDATE accounts SET current_balance_minor = current_balance_minor - ?, row_version = row_version + 1
                WHERE id = ? AND current_balance_minor >= ?
                """, amountMinor, sourceAccountId, amountMinor);
        if (updated == 0) {
            throw new IllegalStateException("INSUFFICIENT_FUNDS");
        }

        UUID paymentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO payments (id, client_reference, idempotency_key, source_account_id, target_account_id,
                amount_minor, currency, status)
                VALUES (?, ?, ?, ?, ?, ?, 'USD', ?)
                """, paymentId, "transfer-" + idempotencyKey, idempotencyKey, sourceAccountId, targetAccountId,
                amountMinor, PaymentStatus.SETTLED.name());

        postCreditJournal(paymentId, sourceAccountId, targetAccountId, amountMinor);
        jdbc.update("UPDATE accounts SET current_balance_minor = current_balance_minor + ? WHERE id = ?",
                amountMinor, targetAccountId);
        return paymentId;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reverseIfSettled(UUID paymentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, source_account_id, target_account_id, amount_minor, status FROM payments WHERE id = ? FOR UPDATE",
                paymentId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> payment = rows.getFirst();
        if (!PaymentStatus.SETTLED.name().equals(payment.get("status"))) {
            return;
        }
        UUID sourceAccountId = (UUID) payment.get("source_account_id");
        UUID targetAccountId = (UUID) payment.get("target_account_id");
        long amountMinor = ((Number) payment.get("amount_minor")).longValue();

        if (sourceAccountId == null) {
            jdbc.update("UPDATE accounts SET current_balance_minor = current_balance_minor - ? WHERE id = ?",
                    amountMinor, targetAccountId);
        } else {
            jdbc.update("UPDATE accounts SET current_balance_minor = current_balance_minor + ? WHERE id = ?",
                    amountMinor, sourceAccountId);
            jdbc.update("UPDATE accounts SET current_balance_minor = current_balance_minor - ? WHERE id = ?",
                    amountMinor, targetAccountId);
        }

        UUID journalId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO journals (id, payment_id, event_type, currency, posting_status)
                VALUES (?, ?, 'REVERSAL', 'USD', 'POSTED')
                """, journalId, paymentId);

        if (sourceAccountId != null) {
            jdbc.update("""
                    INSERT INTO journal_lines (id, journal_id, account_id, signed_amount_minor, currency)
                    VALUES (?, ?, ?, ?, 'USD')
                    """, UUID.randomUUID(), journalId, sourceAccountId, amountMinor);
        }
        jdbc.update("""
                INSERT INTO journal_lines (id, journal_id, account_id, signed_amount_minor, currency)
                VALUES (?, ?, ?, ?, 'USD')
                """, UUID.randomUUID(), journalId, targetAccountId, -amountMinor);

        jdbc.update("UPDATE payments SET status = ?, updated_at = now() WHERE id = ?",
                PaymentStatus.REVERSED.name(), paymentId);
    }

    private void postCreditJournal(UUID paymentId, UUID sourceAccountId, UUID targetAccountId, long amountMinor) {
        UUID journalId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO journals (id, payment_id, event_type, currency, posting_status)
                VALUES (?, ?, 'CREDIT', 'USD', 'POSTED')
                """, journalId, paymentId);

        if (sourceAccountId != null) {
            jdbc.update("""
                    INSERT INTO journal_lines (id, journal_id, account_id, signed_amount_minor, currency)
                    VALUES (?, ?, ?, ?, 'USD')
                    """, UUID.randomUUID(), journalId, sourceAccountId, -amountMinor);
        }
        jdbc.update("""
                INSERT INTO journal_lines (id, journal_id, account_id, signed_amount_minor, currency)
                VALUES (?, ?, ?, ?, 'USD')
                """, UUID.randomUUID(), journalId, targetAccountId, amountMinor);
    }
}
