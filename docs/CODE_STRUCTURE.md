# Code structure (aligned with HRM Suite / ERP Web patterns)

## Backend (`backend`, `fixture-wallet`)

Mirrors **hrmsuite-erp** service layout: `core` + domain modules with **controller → service/impl → repository → payload**.

```
ai.flowguard
├── LedgerGuardServiceApplication.java
├── core/
│   ├── config/
│   ├── enums/ApiStatusEnum
│   ├── exception/GlobalExceptionHandler, BusinessException
│   ├── payload/response/GlobalResponse
│   └── web/CustomLedgerRestController
├── project/
│   ├── controller/
│   ├── service/ + service/impl/
│   ├── repository/
│   └── payload/response/
├── investigation/
│   ├── controller/
│   ├── service/ + service/impl/
│   ├── repository/
│   └── payload/request|response/
├── experiment/
│   ├── controller/
│   └── repository/
├── health/
│   ├── controller/
│   └── service/
├── meta/, messaging/, config/   # cross-cutting integrations
```

**API envelope:** all LedgerGuard REST responses use `GlobalResponse<T>` (`status`, `message`, `data`) like ERP `GlobalResponse`.

**Fixture wallet** uses the same idea under `ai.flowguard.fixture`:

- `core/web/CustomFixtureRestController`
- `payment/controller`, `payment/payload/request`
- `ops/controller` (reset, race hooks)
- `health/controller`

## Frontend (`frontend`)

Mirrors **erp-web** (Vite instead of Next, same mental model):

```
src/
├── app/              # App shell + route table
├── pages/            # route screens (home, investigate, detail)
├── components/
│   ├── common/       # AppShell, PageCard
│   └── investigation/
├── shared/
│   ├── services/     # *.service.ts → apiFetch
│   └── types/        # *.types.ts
├── lib/api-fetch.ts  # fetch + GlobalResponse unwrap
├── hooks/
└── styles/global.css
```

Import alias: `@/*` → `src/*` (same as erp-web).
