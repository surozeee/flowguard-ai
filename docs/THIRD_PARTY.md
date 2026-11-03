# Third-party software

Flow Guard / LedgerGuard uses the following major dependencies. See each module’s `build.gradle.kts` for full transitive lists.

| Component | License (typical) | Use |
|-----------|-------------------|-----|
| Spring Boot / Spring Cloud / Spring Security | Apache 2.0 | Services, gateway, auth |
| PostgreSQL (container) | PostgreSQL License | Primary datastore |
| Redis (container) | BSD | Idempotency, rate limits |
| Apache Kafka (container) | Apache 2.0 | Saga/transaction events |
| Flyway | Apache 2.0 | Schema migrations |
| Testcontainers | MIT | Integration tests |
| React / Vite | MIT | Frontend |
| Prometheus / Grafana (containers) | Apache 2.0 / AGPL | Monitoring |
| Meta Model API (optional) | Meta terms | Investigation planning when `MODEL_API_KEY` is set |

No production customer financial data is stored in this repository.
