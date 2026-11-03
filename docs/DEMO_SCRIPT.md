# Demo script (Day 9)

1. Start infra: `docker compose up -d`
2. Run demo launcher: `scripts/run-demo.ps1` (builds jars, starts fixture + backend + optional UI).
3. Open `http://localhost:5173/investigate` — start investigation with objective *duplicate credit*.
4. Open investigation detail — confirm **SSE timeline** shows hypothesis → experiment → verdict.
5. Optional: `POST /api/v1/evaluations` with `arm: META` vs `RANDOM` on `/evaluations` page.
6. Show oracle evidence hash in API experiment/evidence response.
