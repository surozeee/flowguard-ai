# 10-day milestone map (production E2E)

| Day | Gate | Gradle / script |
|-----|------|-----------------|
| 1 | Postgres + stack smoke | `./gradlew day1Integration`, `scripts/smoke-day1.ps1` |
| 2 | Oracle I4 + 20/20 idempotency race | `./gradlew day2Verification`, `scripts/smoke-day2.ps1` |
| 3 | Frozen baseline benchmark | `./gradlew day3Verification` |
| 4 | G2 hypothesis before experiment + SSE events | `./gradlew :integration:test --tests "ai.flowguard.day4.*"` |
| 5 | CONCURRENT_DEBIT 20/20 | `./gradlew day5Verification` |
| 6 | Patch propose + verify API | Manual/API: `POST /api/v1/investigations/{id}/patches` |
| 7 | React workbench + SSE timeline | `frontend` dev server + investigation detail |
| 8 | Evaluations META vs RANDOM | `POST /api/v1/evaluations`, `/evaluations` UI |
| 9 | Demo script | `scripts/run-demo.ps1` |
| 10 | Full milestone bundle | `./gradlew milestoneVerification`, `scripts/smoke-all.ps1` |

All Docker-backed gates require a running Docker engine.
