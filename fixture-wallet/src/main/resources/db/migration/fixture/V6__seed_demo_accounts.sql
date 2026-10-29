INSERT INTO accounts (id, currency, opening_balance_minor, current_balance_minor, overdraft_allowed, row_version)
VALUES
  ('00000000-0000-4000-8000-000000000001', 'USD', 0, 0, false, 0),
  ('00000000-0000-4000-8000-000000000002', 'USD', 0, 0, false, 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO customer_risk_profiles (account_id, risk_tier, single_txn_limit_minor,
    daily_volume_limit_minor, velocity_max_txn_per_hour, idempotency_ttl_seconds)
VALUES ('00000000-0000-4000-8000-000000000002', 'STANDARD', 500000, 2000000, 60, 86400)
ON CONFLICT (account_id) DO NOTHING;
