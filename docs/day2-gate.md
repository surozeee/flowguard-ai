# Day 2 verification gate (G1)

## Objective

Immutable financial oracle coverage plus **20/20** fresh-state reproduction of the seeded idempotency race violating **I4 `PAYMENT_EFFECT_ONCE`**.

## Automated suite

| Test | Module | Purpose |
|------|--------|---------|
| `FinancialOraclePositiveTest` | `trusted-tests` | Balanced ledger passes all invariants |
| `FinancialOracleNegativeTest` | `trusted-tests` | I1/I2/I4 negative fixtures |
| `Day2IdempotencyRaceStabilityTest` | `fixture-wallet` | **20 runs** → duplicate credit + oracle fail |
| `Day2ReferenceSafeControlTest` | `fixture-wallet` | Safe variant: sequential retry credits once |

Evidence report (after stability test):

`fixture-wallet/build/reports/day2/idempotency-replay.json`

## Commands

```powershell
# Full Day 2 gate (Docker required)
.\scripts\smoke-day2.ps1

# Or Gradle
.\gradlew.bat day2Verification
```

## Pass criteria

1. All oracle unit tests pass.  
2. `Day2IdempotencyRaceStabilityTest` reports **20/20** reproduced violations.  
3. `Day2ReferenceSafeControlTest` passes (proves oracle is not always red).

## Enterprise layout

- Oracle tests: `trusted-tests/src/test/java/ai/flowguard/oracles/`  
- Race harness: `fixture-wallet/.../verification/ConcurrentCreditRaceRunner`  
- Immutable oracle production code remains in `trusted-tests/src/main` (not patchable by AI fixture diffs).
