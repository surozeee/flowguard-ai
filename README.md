# FlowGuard AI — LedgerGuard

A synthetic wallet you can break on purpose. Java and SQL decide whether the books still balance. The model only suggests the next probe. No real money.

Run it with Docker Compose. Kubernetes is not required.

## Setup

Java 21, Node 20+, and Docker Desktop.

```powershell
copy .env.example .env
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Sign in as `investigator` / `investigator123` or `ops` / `ops123`.

Without `MODEL_API_KEY` the planner stays on a stub. Set the key only when you want live Meta calls.

## Layout

```text
Browser (Vite :5173)
    └── API gateway :8088
            └── Backend orchestrator :8080
                    ├── Postgres, Redis, Kafka
                    └── Fixture wallet :8081
Kafka evidence ──► Python worker :8090 ──► hash chain back to Spring
```

| Module | Role |
|--------|------|
| `backend` | Investigations, oracles, transactions |
| `fixture-wallet` | Fake payments, races, saga outbox |
| `api-gateway` / `auth-server` | Login and the edge |
| `trusted-tests` | Financial oracles |
| `frontend` | Operator console |
| `python-services/evidence-processor` | Evidence hashing |

## What you can do

- See whether Postgres, Redis, and Kafka are up
- Post a credit and replay the same idempotency key
- Start a transfer and watch the saga
- Investigate duplicate-credit risk
- Replay inbox, outbox, and dead letters as the ops user

Longer notes live under `docs/`.
