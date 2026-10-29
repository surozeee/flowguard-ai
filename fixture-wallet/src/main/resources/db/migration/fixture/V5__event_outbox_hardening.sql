CREATE TABLE IF NOT EXISTS transaction_event_outbox (
  id UUID PRIMARY KEY,
  partition_key VARCHAR(100),
  payload_json JSONB NOT NULL,
  published_at TIMESTAMPTZ,
  publish_attempts INT NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transaction_event_outbox_pending
  ON transaction_event_outbox (created_at)
  WHERE published_at IS NULL;

ALTER TABLE saga_event_outbox
  ADD COLUMN IF NOT EXISTS publish_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE saga_event_outbox
  ADD COLUMN IF NOT EXISTS last_error TEXT;

CREATE TABLE IF NOT EXISTS saga_event_outbox_dlq (
  id UUID PRIMARY KEY,
  idempotency_key VARCHAR(100),
  payload_json JSONB NOT NULL,
  publish_attempts INT NOT NULL,
  last_error TEXT,
  dead_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS transaction_event_outbox_dlq (
  id UUID PRIMARY KEY,
  partition_key VARCHAR(100),
  payload_json JSONB NOT NULL,
  publish_attempts INT NOT NULL,
  last_error TEXT,
  dead_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
