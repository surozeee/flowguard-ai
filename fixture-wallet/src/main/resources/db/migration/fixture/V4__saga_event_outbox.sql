CREATE TABLE IF NOT EXISTS saga_event_outbox (
  id UUID PRIMARY KEY,
  idempotency_key VARCHAR(100),
  payload_json JSONB NOT NULL,
  published_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_saga_event_outbox_pending
  ON saga_event_outbox (created_at)
  WHERE published_at IS NULL;
