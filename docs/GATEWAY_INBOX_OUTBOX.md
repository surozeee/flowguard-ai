# Gateway inbox & partner outbox

## Model

| Direction | Table | Meaning |
|-----------|--------|---------|
| Client → Gateway | `gateway.api_inbox` | Ingress audit (ERP activity-log style) |
| Gateway → Backend (partner) | `gateway.partner_outbox` | Persist before downstream call; store response / errors |
| Exhausted retries | `gateway.partner_outbox_dlq` | DLQ + replay API |

Fixture Kafka outbox and backend `saga_choreography_log` (consumer inbox) are exposed via **`/api/v1/ops/processing/summary`**.

## Internal APIs (gateway)

- `GET /internal/gateway/summary`
- `GET /internal/gateway/inbox`
- `GET /internal/gateway/outbox`
- `GET /internal/gateway/outbox/dlq`
- `POST /internal/gateway/outbox/dlq/{id}/replay`

## Ops APIs (backend, requires `flowguard:ops`)

- `GET /api/v1/ops/processing/summary`
- `GET /api/v1/ops/gateway/*`
- `GET /api/v1/ops/inbox/saga-events`
- `GET /api/v1/ops/outbox/saga-pending` · `transaction-pending`

## Verification

```powershell
.\gradlew.bat :api-gateway:test
.\gradlew.bat :integration:e2eEnterprise
```

UI: **Dashboard** at `/dashboard` (ops login). Grafana: **FlowGuard Enterprise Processing**.
