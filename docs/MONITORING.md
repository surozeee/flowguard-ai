# Monitoring (Prometheus + Grafana)

## Compose stack

With the full stack running:

```powershell
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres redis kafka fixture-wallet flowguard-backend prometheus grafana
```

Prometheus loads alert rules from `docker/monitoring/alerts/flowguard.yml` (DLQ growth, saga reaper, idempotency contention).

| Service | URL |
|---------|-----|
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (login `admin` / `flowguard`) |
| Backend metrics | http://localhost:8080/actuator/prometheus |
| Fixture metrics | http://localhost:8081/actuator/prometheus |

Dashboard **FlowGuard Saga & Outbox** is provisioned from `docker/monitoring/grafana/dashboards/flowguard-saga.json`.

## Operations API (backend)

All routes use `GlobalResponse` under `/api/v1/ops`:

- `GET /outbox/summary` — DLQ counts (proxied to fixture)
- `GET /outbox/saga-dlq` · `POST /outbox/saga-dlq/{id}/replay`
- `GET /outbox/transaction-dlq` · `POST /outbox/transaction-dlq/{id}/replay`
- `GET /sagas/stale` — active fixture sagas past inactivity threshold
- `GET /sagas/choreography-gaps` — started choreography without terminal event

UI: **Ops** in the frontend nav (`/ops`).

## Fixture internal (direct)

- `GET /internal/outbox/summary`
- `GET /internal/outbox/saga-dlq` · `POST /internal/outbox/saga-dlq/{id}/replay`
- `GET /internal/sagas/stale`

Replay moves a DLQ row back into the pending outbox for the poller to publish.
