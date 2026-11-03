# Production readiness

## Hardening checklist

| Area | Implementation |
|------|----------------|
| Health | Spring Boot Actuator liveness/readiness on backend + fixture |
| Dependencies | Custom readiness checks Postgres, Redis, Kafka (backend `FlowGuardReadinessHealthIndicator`) |
| Kafka | Producer `acks=all`, idempotent producer, `JsonSerializer` without type headers; backend consumers use byte[] + Jackson parse; retry error handler |
| Redis | Idempotency + risk usage + backend transaction projection |
| Sagas | Orchestrated payments, transactional outbox (saga + settlement events), stale saga reaper, choreography dedupe |
| API | `X-Correlation-Id` filter, `Idempotency-Key` on fixture payments, saga trace/verify on backend (`GlobalResponse`) |
| Edge auth | `auth-server` + `api-gateway` (opaque OAuth2 introspection, Redis rate limit); see [AUTH_AND_GATEWAY.md](./AUTH_AND_GATEWAY.md) |
| Shutdown | `server.shutdown=graceful` |
| Config | `application-prod.yml` (pool sizes, logging) |
| Container | `backend/Dockerfile`, `fixture-wallet/Dockerfile`, full `docker-compose.yml` stack |

## E2E verification

**Automated (CI-friendly, requires Docker):**

```powershell
.\gradlew.bat e2eAll
```

Runs all `ai.flowguard.e2e.*` suites:

| Suite | Gradle task | Coverage |
|-------|-------------|----------|
| Production stack | `e2eProduction` | Fixture messaging, sagas, investigation, readiness |
| Ops | (in `e2eProduction`) | API key ops APIs, consumer inbox |
| Enterprise inbox/outbox | `e2eEnterprise` | Gateway partner outbox, internal token, ops ↔ gateway |
| Auth + gateway | `e2eAuth` | Login via gateway, Bearer on `/api/v1/**`, ops scope |

Individual gates: `e2eProduction`, `e2eEnterprise`, `e2eAuth`.

**Full milestone bundle:**

```powershell
.\gradlew.bat milestoneVerification
```

**Live smoke (Compose + boot jars):**

```powershell
.\scripts\smoke-production-e2e.ps1
```

## Compose production stack

```powershell
.\gradlew.bat :auth-server:bootJar :api-gateway:bootJar :fixture-wallet:bootJar :backend:bootJar
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d postgres redis kafka flowguard-auth-server flowguard-api-gateway fixture-wallet flowguard-backend evidence-processor prometheus grafana
```

Set `GATEWAY_INTERNAL_TOKEN` (gateway) and matching `flowguard.gateway.internal-token` on the backend when exposing gateway admin routes. Backend ops calls gateway with header `X-Gateway-Internal-Token`.

Lab stack (vulnerable variant for Day 2 races): omit `docker-compose.prod.yml`.

- Backend readiness: http://localhost:8080/actuator/health/readiness  
- Fixture health: http://localhost:8081/health  
- API gateway: http://localhost:8088 (OAuth2 + routes to backend/auth)
- UI: `cd frontend; npm run dev` (proxies `/api` → **8088**; login via auth-server through gateway)
- Ops UI: http://localhost:5173/ops · Grafana: http://localhost:3000 (see [MONITORING.md](MONITORING.md))

## Production guards

When `FLOWGUARD_PRODUCTION_GUARDS=true` (set in `docker-compose.prod.yml`):

| Service | Validates |
|---------|-----------|
| **api-gateway** | `GATEWAY_INTERNAL_TOKEN` present (16+ chars) |
| **backend** | OAuth and/or `FLOWGUARD_API_KEY`; `GATEWAY_INTERNAL_TOKEN` if gateway URL set; `FIXTURE_INTERNAL_TOKEN` |

Gradle E2E runs with guards **off** so Testcontainers stacks stay open; Compose prod overlay turns guards **on**.

**Local E2E memory:** run `.\gradlew.bat --stop` before `e2eAll` if Docker Desktop and other JVMs are busy; integration E2E uses a single fork (`forkEvery = 0`) and ~1 GiB test heap.

## Smoke

```powershell
.\scripts\smoke-production-e2e.ps1   # e2eAll + Compose prod + login @ 8088
.\scripts\smoke-gateway-live.ps1       # against running gateway only
```

## Operational notes

- Set `MODEL_API_KEY` for real Meta planning in production.
- Use `reference-safe` fixture variant when enforcing Redis/DB idempotency for customer traffic; keep `dup-credit-vulnerable` only in lab/race environments.
- Tune Hikari and Kafka concurrency in `application-prod.yml` for your load.
- Third-party notices: [THIRD_PARTY.md](./THIRD_PARTY.md).
