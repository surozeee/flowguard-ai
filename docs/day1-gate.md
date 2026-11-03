# Day 1 integration gate

## Objective

Prove **G0**: clean build, real PostgreSQL, Flyway migrations, runnable fixture, and orchestrator wiring (Redis + Kafka) without manual DB edits.

## Automated tests

| Test | Module | Scope |
|------|--------|--------|
| `Day1PostgresIntegrationTest` | `fixture-wallet` | Testcontainers Postgres, Flyway, reset, credit |
| `Day1FullStackIntegrationTest` | `integration` | Postgres + Redis + Kafka; both Spring Boot apps; investigation → `VIOLATION_REPRODUCED` |

## Commands

```powershell
# Full Day 1 smoke (preflight + compile + module IT + stack IT or Compose fallback)
.\scripts\smoke-day1.ps1

# Live stack against docker-compose (Postgres + Redis + Kafka + boot jars)
.\scripts\day1-live-integration.ps1

# Testcontainers stack only
.\gradlew.bat day1Integration
```

## Manual smoke (optional)

```powershell
docker compose up -d postgres redis kafka
$env:FIXTURE_VARIANT="dup-credit-vulnerable"
.\gradlew.bat :fixture-wallet:bootRun
# separate terminal
.\gradlew.bat :backend:bootRun
```

- Fixture: `GET http://localhost:8081/health`
- Backend: `GET http://localhost:8080/api/v1/health`
- Reset: `POST http://localhost:8081/internal/reset`

## Pass criteria

1. `./gradlew clean compileJava compileTestJava` succeeds  
2. `Day1PostgresIntegrationTest` passes  
3. `day1Integration` passes (all five ordered steps in full stack test)

Failure in step 3 with `Investigation failed` usually indicates fixture URL misconfiguration or race timeout — re-run once; persistent failure is a Day 2 barrier issue.
