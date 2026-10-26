CREATE TABLE IF NOT EXISTS accounts (
  id UUID PRIMARY KEY,
  currency CHAR(3) NOT NULL CHECK (currency = 'USD'),
  opening_balance_minor BIGINT NOT NULL,
  current_balance_minor BIGINT NOT NULL,
  overdraft_allowed BOOLEAN NOT NULL DEFAULT FALSE,
  row_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS payments (
  id UUID PRIMARY KEY,
  client_reference VARCHAR(100) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  source_account_id UUID REFERENCES accounts(id),
  target_account_id UUID NOT NULL REFERENCES accounts(id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  currency CHAR(3) NOT NULL CHECK (currency = 'USD'),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS journals (
  id UUID PRIMARY KEY,
  payment_id UUID REFERENCES payments(id),
  event_type VARCHAR(40) NOT NULL,
  currency CHAR(3) NOT NULL CHECK (currency = 'USD'),
  posting_status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS journal_lines (
  id UUID PRIMARY KEY,
  journal_id UUID NOT NULL REFERENCES journals(id),
  account_id UUID NOT NULL REFERENCES accounts(id),
  signed_amount_minor BIGINT NOT NULL,
  currency CHAR(3) NOT NULL CHECK (currency = 'USD')
);

CREATE INDEX IF NOT EXISTS idx_journal_lines_account ON journal_lines(account_id);
CREATE INDEX IF NOT EXISTS idx_payments_idempotency ON payments(idempotency_key);
