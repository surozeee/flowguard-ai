CREATE TABLE IF NOT EXISTS payment_sagas (
  id UUID PRIMARY KEY,
  idempotency_key VARCHAR(100) NOT NULL,
  saga_type VARCHAR(40) NOT NULL,
  state VARCHAR(40) NOT NULL,
  current_step VARCHAR(60) NOT NULL,
  account_id UUID NOT NULL,
  payment_id UUID,
  payload_json JSONB,
  failure_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_payment_sagas_idempotency ON payment_sagas(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_payment_sagas_state ON payment_sagas(state);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_sagas_active_idempotency
  ON payment_sagas(idempotency_key)
  WHERE state NOT IN ('FAILED', 'COMPENSATED', 'COMPLETED');
