# Auth server and API gateway

Flow Guard adds ERP-style edge security: **opaque OAuth2 tokens**, **token introspection** at the gateway, and **scope-based Ops RBAC**.

## Services

| Service | Default port | Role |
|---------|--------------|------|
| `flowguard-auth-server` | 9000 | Spring Authorization Server (reference access tokens), `/api/v1/auth/login` |
| `flowguard-api-gateway` | 8088 | Spring Cloud Gateway, introspection, Redis rate limits |
| `flowguard-backend` | 8080 | API (direct for integration tests; UI should use gateway in prod overlay) |

## Dev users

| Username | Password | Scopes |
|----------|----------|--------|
| `investigator` | `investigator123` | `flowguard:read` |
| `ops` | `ops123` | `flowguard:read`, `flowguard:ops` |

## Frontend

1. Run stack with `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` (OAuth enabled on backend).
2. Vite proxies `/api` and `/oauth2` to `http://localhost:8088`.
3. Sign in at `/login` or set `VITE_FLOWGUARD_API_KEY` for legacy direct-backend mode.

## Ops endpoints

`/api/v1/ops/**` requires `SCOPE_flowguard:ops` at the gateway and on the backend.

## Fixture internal API

When `FIXTURE_INTERNAL_TOKEN` is set, `/internal/**` on fixture-wallet requires header `X-Fixture-Internal-Token`. The gateway does not expose fixture routes publicly.

## Environment

See root `.env.example` for `AUTH_*`, `FLOWGUARD_OAUTH_ENABLED`, and `FIXTURE_INTERNAL_TOKEN`.
