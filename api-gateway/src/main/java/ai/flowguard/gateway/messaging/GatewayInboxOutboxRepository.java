package ai.flowguard.gateway.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class GatewayInboxOutboxRepository {

    private static final Logger log = LoggerFactory.getLogger(GatewayInboxOutboxRepository.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public GatewayInboxOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbc = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void insertInbox(
            UUID id,
            String correlationId,
            String method,
            String path,
            String query,
            String principal,
            String clientIp,
            String idempotencyKey,
            String requestHeadersJson,
            String requestBody,
            String state) {
        jdbc.update("""
                INSERT INTO api_inbox (
                    id, correlation_id, http_method, path, query_string, principal_name, client_ip,
                    idempotency_key, request_headers_json, request_body, state)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """,
                id, correlationId, method, path, query, principal, clientIp, idempotencyKey,
                requestHeadersJson == null ? "{}" : requestHeadersJson, requestBody, state);
    }

    public void insertPartnerOutbox(
            UUID id,
            UUID inboxId,
            String partnerId,
            String targetUrl,
            String method,
            String requestBody,
            String state) {
        jdbc.update("""
                INSERT INTO partner_outbox (
                    id, inbox_id, partner_id, target_url, http_method, request_body, state, attempt_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """,
                id, inboxId, partnerId, targetUrl, method, requestBody, state);
    }

    public void markInboxState(UUID inboxId, String state) {
        jdbc.update("UPDATE api_inbox SET state = ? WHERE id = ?", state, inboxId);
    }

    public void markOutboxDispatching(UUID outboxId, int attempt) {
        jdbc.update("""
                UPDATE partner_outbox
                SET state = ?, attempt_count = ?, dispatched_at = now()
                WHERE id = ?
                """, GatewayMessageState.OUTBOX_DISPATCHING, attempt, outboxId);
    }

    public void completeInbox(UUID inboxId, int status, String responseBody) {
        jdbc.update("""
                UPDATE api_inbox
                SET state = ?, response_status = ?, response_body = ?, completed_at = now()
                WHERE id = ?
                """, GatewayMessageState.INBOX_COMPLETED, status, responseBody, inboxId);
    }

    public void failInbox(UUID inboxId, String error) {
        jdbc.update("""
                UPDATE api_inbox
                SET state = ?, error_message = ?, completed_at = now()
                WHERE id = ?
                """, GatewayMessageState.INBOX_FAILED, truncate(error, 4000), inboxId);
    }

    public void succeedOutbox(UUID outboxId, int status, String responseBody) {
        jdbc.update("""
                UPDATE partner_outbox
                SET state = ?, response_status = ?, response_body = ?, completed_at = now(), last_error = NULL
                WHERE id = ?
                """, GatewayMessageState.OUTBOX_SUCCESS, status, responseBody, outboxId);
    }

    public void failOutbox(UUID outboxId, String error) {
        jdbc.update("""
                UPDATE partner_outbox
                SET state = ?, last_error = ?
                WHERE id = ?
                """, GatewayMessageState.OUTBOX_FAILED, truncate(error, 4000), outboxId);
    }

    public void moveOutboxToDlq(UUID outboxId, String error) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, inbox_id, partner_id, http_method, target_url, request_body, attempt_count
                FROM partner_outbox WHERE id = ?
                """, outboxId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.getFirst();
        try {
            String payload = objectMapper.writeValueAsString(row);
            jdbc.update("""
                    INSERT INTO partner_outbox_dlq (id, outbox_id, inbox_id, partner_id, payload_json, attempt_count, last_error)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                    """,
                    UUID.randomUUID(),
                    outboxId,
                    row.get("inbox_id"),
                    row.get("partner_id"),
                    payload,
                    row.get("attempt_count"),
                    truncate(error, 4000));
        } catch (Exception ex) {
            log.warn("Could not persist partner outbox DLQ for outboxId={}: {}", outboxId, ex.getMessage());
        }
        jdbc.update("""
                UPDATE partner_outbox SET state = ?, last_error = ?, completed_at = now() WHERE id = ?
                """, GatewayMessageState.OUTBOX_DLQ, truncate(error, 4000), outboxId);
    }

    public Map<String, Object> summary() {
        Integer inboxOpen = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM api_inbox WHERE state IN ('RECEIVED','DISPATCHED')", Integer.class);
        Integer outboxPending = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM partner_outbox WHERE state IN ('PENDING','DISPATCHING','FAILED')",
                Integer.class);
        Integer outboxDlq = jdbc.queryForObject("SELECT COUNT(*)::int FROM partner_outbox_dlq", Integer.class);
        Integer inboxFailed = jdbc.queryForObject(
                "SELECT COUNT(*)::int FROM api_inbox WHERE state = 'FAILED'", Integer.class);
        return Map.of(
                "inboxOpenCount", inboxOpen == null ? 0 : inboxOpen,
                "partnerOutboxPendingCount", outboxPending == null ? 0 : outboxPending,
                "partnerOutboxDlqCount", outboxDlq == null ? 0 : outboxDlq,
                "inboxFailedCount", inboxFailed == null ? 0 : inboxFailed);
    }

    public List<Map<String, Object>> listInbox(int limit) {
        return jdbc.queryForList("""
                SELECT id, correlation_id, http_method, path, state, response_status, created_at, completed_at
                FROM api_inbox ORDER BY created_at DESC LIMIT ?
                """, limit);
    }

    public List<Map<String, Object>> listOutbox(int limit) {
        return jdbc.queryForList("""
                SELECT id, inbox_id, partner_id, target_url, http_method, state, attempt_count,
                       response_status, last_error, created_at, completed_at
                FROM partner_outbox ORDER BY created_at DESC LIMIT ?
                """, limit);
    }

    public List<Map<String, Object>> listOutboxDlq(int limit) {
        return jdbc.queryForList("""
                SELECT id, outbox_id, inbox_id, partner_id, attempt_count, last_error, dead_at
                FROM partner_outbox_dlq ORDER BY dead_at DESC LIMIT ?
                """, limit);
    }

    public Optional<Map<String, Object>> findOutbox(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM partner_outbox WHERE id = ?", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Optional<Map<String, Object>> findCompletedInboxByIdempotency(
            String httpMethod,
            String path,
            String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT response_status, response_body
                FROM api_inbox
                WHERE idempotency_key = ? AND http_method = ? AND path = ? AND state = ?
                ORDER BY completed_at DESC NULLS LAST
                LIMIT 1
                """,
                idempotencyKey,
                httpMethod,
                path,
                GatewayMessageState.INBOX_COMPLETED);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public void requeueOutboxFromDlq(UUID dlqId) {
        List<Map<String, Object>> dlq = jdbc.queryForList("""
                SELECT outbox_id, payload_json FROM partner_outbox_dlq WHERE id = ?
                """, dlqId);
        if (dlq.isEmpty()) {
            throw new IllegalArgumentException("DLQ row not found");
        }
        UUID outboxId = (UUID) dlq.getFirst().get("outbox_id");
        jdbc.update("""
                UPDATE partner_outbox
                SET state = ?, attempt_count = 0, last_error = NULL, completed_at = NULL, dispatched_at = NULL
                WHERE id = ?
                """, GatewayMessageState.OUTBOX_PENDING, outboxId);
        jdbc.update("DELETE FROM partner_outbox_dlq WHERE id = ?", dlqId);
    }

    public List<Map<String, Object>> findRetryableOutbox(int limit) {
        return jdbc.queryForList("""
                SELECT id, inbox_id, partner_id, target_url, http_method, request_body, attempt_count
                FROM partner_outbox
                WHERE state = 'FAILED'
                ORDER BY created_at ASC
                LIMIT ?
                """, limit);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
