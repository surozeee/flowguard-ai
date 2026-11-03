# Kafka, Redis, idempotency & customer risk

## Customer transactions (fixture-wallet)

Every credit (`POST /payments`) and transfer (`POST /transfers`) runs through:

1. **Risk profile** (`customer_risk_profiles`) — tier, per-txn limit, daily volume, hourly velocity.
2. **Idempotency** — for non-vulnerable variants, Redis key `idem:payment:{key}` plus Postgres audit `payment_idempotency_audit`.
3. **Kafka** — events on `flowguard.transactions`:
   - `PAYMENT_SETTLED`
   - `PAYMENT_IDEMPOTENT_REPLAY`
   - `PAYMENT_RISK_DENIED`

`dup-credit-vulnerable` **disables Redis idempotency locking** so race experiments still reproduce I4; risk checks and Kafka events still run.

## Backend orchestrator

- Consumes `flowguard.transactions` and mirrors state in Redis:
  - `customer:txn:last:{accountId}`
  - `idem:global:{idempotencyKey}`
  - `customer:risk:profile:{accountId}`
- Query APIs:
  - `GET /api/v1/customers/{accountId}/transaction-risk`
  - `GET /api/v1/customers/{accountId}/last-transaction`
  - `GET /api/v1/customers/idempotency-keys/lookup?key=...`

## Fixture APIs

- `GET /customers/{accountId}/risk-profile`
- `GET /customers/{accountId}/risk-usage`

## Local stack

```powershell
docker compose up -d postgres redis kafka fixture-wallet evidence-processor
./gradlew :backend:bootRun
```

Set `FIXTURE_MESSAGING_ENABLED=true` and `FIXTURE_REDIS_IDEMPOTENCY_ENABLED=true` when running the fixture outside Compose with infra up.
