CREATE INDEX IF NOT EXISTS idx_api_inbox_idempotency_lookup
    ON api_inbox (idempotency_key, http_method, path, completed_at DESC)
    WHERE idempotency_key IS NOT NULL AND state = 'COMPLETED';
