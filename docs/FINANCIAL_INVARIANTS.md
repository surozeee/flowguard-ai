# Financial invariants (LedgerGuard MVP)

All checks are implemented in `trusted-tests` → `FinancialOracle` (version stamped in code). The model **selects** probes; it never defines pass/fail.

| ID | Name | Intent |
|----|------|--------|
| I1 | `JOURNAL_BALANCED` | Posted journal lines net to zero per currency |
| I2 | `ACCOUNT_RECONCILES` | Stored balance equals opening + sum of posted movements |
| I3 | `NONNEGATIVE` | Non-overdraft accounts stay ≥ 0 |
| I4 | `PAYMENT_EFFECT_ONCE` | Same idempotency key → at most one financial effect |
| I5 | `VALID_STATE_TRANSITION` | Payment state machine (stub in MVP oracle) |

Day 2 gate targets **I4** on fixture variant `dup-credit-vulnerable` with explicit race checkpoint `AFTER_IDEMPOTENCY_LOOKUP`.
