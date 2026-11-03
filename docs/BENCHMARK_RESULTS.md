# Benchmark results (template)

Run locally with Docker:

```powershell
./gradlew day3Verification day5Verification
```

Record results after each run:

| Benchmark | Runs | Violation rate | Notes |
|-----------|------|----------------|-------|
| Baseline idempotency (Day 3) | 5 | 100% expected | `benchmarks/baseline` |
| Idempotency race (Day 2) | 20 | 100% expected | `Day2IdempotencyRaceStabilityTest` |
| Debit race (Day 5) | 20 | 100% expected | `Day5DebitRaceStabilityTest` |
| Evaluation META vs RANDOM | 3 probes | fill after `POST /evaluations` | Day 8 |
