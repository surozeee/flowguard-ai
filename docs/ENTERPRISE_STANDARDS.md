# Enterprise & industry-standard alignment

## API platform

| Standard | Implementation |
|----------|----------------|
| Versioned REST | `/api/v1/*` envelope `GlobalResponse` |
| OpenAPI 3 | Swagger UI at `/swagger-ui.html`, spec `/v3/api-docs` (backend) |
| Idempotency | `Idempotency-Key` on `POST /investigations`; Redis-backed replay |
| Correlation | `X-Correlation-Id` on all orchestrator requests (MDC logging) |
| Optional auth | `X-API-Key` when `FLOWGUARD_API_KEY` is set |

## Event-driven ledger flow

```text
Client → Fixture (risk + Redis idempotency) → Kafka flowguard.transactions
      → Backend projection (Redis) + Investigation pipeline
      → Kafka evidence → Python hash chain → evidence.processed → Backend cache
```

## Observability

- Actuator: liveness, readiness, Prometheus (`/actuator/prometheus`)
- Structured console logs with `correlationId`
- Dependency health: Postgres, Redis, Kafka

## Resilience

- Kafka: idempotent producer, consumer retry handler (transactions)
- HTTP to fixture: connect 5s / read 60s timeouts
- Graceful shutdown (`application-prod.yml`)

## Environment separation

| Profile | Use |
|---------|-----|
| default | Local dev |
| `prod` | Pool tuning, reduced health detail |
| `prod-e2e-it` | Reference-safe fixture + full messaging (tests) |

## Compliance-oriented practices

- Secrets via environment only (`.env.example` template)
- Immutable financial oracles in isolated `trusted-tests` module
- Audit tables: `model_calls`, `investigation_events`, `payment_idempotency_audit`

Run full enterprise gate locally:

```powershell
.\gradlew.bat milestoneVerification
```
