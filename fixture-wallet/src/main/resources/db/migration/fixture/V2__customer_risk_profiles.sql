CREATE TABLE IF NOT EXISTS customer_risk_profiles (
  account_id UUID PRIMARY KEY REFERENCES accounts(id),
  risk_tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
  single_txn_limit_minor BIGINT NOT NULL DEFAULT 500000,
  daily_volume_limit_minor BIGINT NOT NULL DEFAULT 2000000,
  velocity_max_txn_per_hour INT NOT NULL DEFAULT 60,
  idempotency_ttl_seconds INT NOT NULL DEFAULT 86400,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS payment_idempotency_audit (
  idempotency_key VARCHAR(100) PRIMARY KEY,
  payment_id UUID NOT NULL REFERENCES payments(id),
  account_id UUID NOT NULL REFERENCES accounts(id),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
