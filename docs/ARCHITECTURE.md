# LedgerGuard architecture

## Runtime components

- **Orchestrator** (`backend/`, port 8080): investigations, experiments, evaluations, patch workflow, SSE event stream, Flyway metadata schema.
- **Fixture wallet** (`fixture-wallet/`, port 8081): vulnerable/safe payment variants, race checkpoints, reset API.
- **Immutable oracles** (`trusted-tests/`): JDBC `FinancialOracle` v1.0.0 — not patchable at runtime.
- **Evidence processor** (`python-services/evidence-processor/`): Kafka consumer, Redis hash chain for evidence events.
- **Frontend** (`frontend/`, Vite): investigation console, live SSE timeline, evaluations page.

## Investigation flow

1. Client `POST /api/v1/investigations` → async pipeline.
2. Meta (or stub) proposes plan → **hypothesis row persisted before experiment run** (G2).
3. `ExperimentExecutor` drives fixture (credit duplicate or concurrent debit).
4. Oracle verdict + hashed evidence chain stored in `run_evidence`.
5. Events published to Kafka, Redis cache, DB `investigation_events`, and SSE `/api/v1/investigations/{id}/events`.

## Data stores

- PostgreSQL schemas: `fixture` (ledger), `metadata` (investigations, model_calls, patch_attempts, evaluations).
- Redis: investigation state cache.
- Kafka: investigation / experiment / evidence topics.
