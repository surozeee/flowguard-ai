# Contributing

Flow Guard follows the same layout and API envelope as HRM Suite ERP (`GlobalResponse`, controller → service → repository).

## Style

- Match the module you touch: naming, package depth, and response shape in neighboring classes.
- Prefer small, obvious methods over framework ceremony.
- Comments only for non-obvious invariants (idempotency, saga ordering, outbox semantics).
- No drive-by refactors outside the task.

## AI-assisted development

This repo may use AI tools during development. Before submission or release:

1. Every change is reviewed and run locally (`.\gradlew.bat e2eAll` with Docker, `npm run build` in `frontend/`).
2. Record model usage in investigation `model_calls` when Meta API is used in demos.

## Verification

```powershell
.\gradlew.bat milestoneVerification
cd frontend; npm run build
```
