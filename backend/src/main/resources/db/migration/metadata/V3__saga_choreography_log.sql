CREATE TABLE IF NOT EXISTS saga_choreography_log (
  id UUID PRIMARY KEY,
  saga_id UUID NOT NULL,
  idempotency_key VARCHAR(100),
  event_type VARCHAR(80) NOT NULL,
  saga_state VARCHAR(40),
  step VARCHAR(60),
  payload_json JSONB,
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_saga_choreography_saga_id ON saga_choreography_log(saga_id);
CREATE INDEX IF NOT EXISTS idx_saga_choreography_idempotency ON saga_choreography_log(idempotency_key);
