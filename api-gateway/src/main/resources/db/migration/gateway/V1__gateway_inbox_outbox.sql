CREATE TABLE IF NOT EXISTS api_inbox (
    id UUID PRIMARY KEY,
    correlation_id VARCHAR(128) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    path VARCHAR(2048) NOT NULL,
    query_string VARCHAR(2048),
    principal_name VARCHAR(256),
    client_ip VARCHAR(64),
    idempotency_key VARCHAR(256),
    request_headers_json JSONB,
    request_body TEXT,
    state VARCHAR(32) NOT NULL,
    response_status INT,
    response_body TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_api_inbox_correlation ON api_inbox (correlation_id);
CREATE INDEX IF NOT EXISTS idx_api_inbox_state_created ON api_inbox (state, created_at DESC);

CREATE TABLE IF NOT EXISTS partner_outbox (
    id UUID PRIMARY KEY,
    inbox_id UUID NOT NULL REFERENCES api_inbox (id),
    partner_id VARCHAR(128) NOT NULL,
    target_url VARCHAR(2048) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    request_body TEXT,
    state VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    response_status INT,
    response_body TEXT,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatched_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_partner_outbox_state ON partner_outbox (state, created_at);
CREATE INDEX IF NOT EXISTS idx_partner_outbox_inbox ON partner_outbox (inbox_id);

CREATE TABLE IF NOT EXISTS partner_outbox_dlq (
    id UUID PRIMARY KEY,
    outbox_id UUID NOT NULL,
    inbox_id UUID NOT NULL,
    partner_id VARCHAR(128) NOT NULL,
    payload_json JSONB NOT NULL,
    attempt_count INT NOT NULL,
    last_error TEXT,
    dead_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
