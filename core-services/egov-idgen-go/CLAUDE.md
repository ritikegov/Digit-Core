# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this service does

`egov-idgen-go` is a Go port of the DIGIT platform's ID generation service. It generates formatted unique IDs (e.g. `PB-PT-2024-25-000001`) by combining format templates with PostgreSQL sequences, financial/calendar year tokens, city codes from MDMS, and random text. It exposes a single endpoint: `POST /egov-idgen/id/_generate`.

The original Java service lives at `../egov-idgen`. This Go port preserves the exact API contract.

## Commands

```bash
# Run locally (source env first)
export $(cat .env | xargs) && go run ./cmd/server/main.go

# Build binary
go build -o egov-idgen ./cmd/server/main.go

# Run tests
go test ./...

# Run a single test
go test ./internal/service/... -run TestGenerateFinancialYear -v

# Build Docker image
docker build --no-cache -t talele08/egov-idgen-go:1.x .
docker push talele08/egov-idgen-go:1.x

# Deploy to cluster
kubectl set image deployment/egov-idgen egov-idgen=talele08/egov-idgen-go:1.x
kubectl rollout status deployment/egov-idgen
kubectl logs -f deployment/egov-idgen
```

## Architecture

Request flow:
```
POST /egov-idgen/id/_generate
  → handler.IdGenHandler.generate()
  → service.IdGenService.GenerateIdResponse()
      → service.MdmsService.GetIdFormatAndCity()   [HTTP call to MDMS]
      → repository.IdGenRepository.GenerateSequenceNumbers()  [PostgreSQL]
```

**`internal/service/idgen.go`** — core logic. Format templates use `[token]` syntax:
- `[seq_name]` → PostgreSQL sequence via `NEXTVAL`, zero-padded to 6 digits
- `[fy:yyyy-yy]` → financial year (April–March cycle)
- `[cy:yyyy]` → current year (Java `SimpleDateFormat` pattern, converted to Go layout)
- `[city]` → city code fetched from MDMS `tenant/tenants`
- `[tenantid]` / `[tenant_id]` / `[TENANT_ID]` → literal substitution before token parsing
- anything else → random numeric string (length from `{n}` suffix, default 2)

Sequences are pre-fetched in bulk (`GENERATE_SERIES`) for the full `count` before iterating, so one DB round-trip handles all IDs in a batch request. If a sequence doesn't exist and `AUTOCREATE_NEW_SEQ=true` and the format came from MDMS (`autoCreate` flag), it is created automatically.

**`internal/service/mdms.go`** — makes a single POST to MDMS that fetches both the ID format (`common-masters/IdFormat`) and city code (`tenant/tenants`) in one call, unlike the Java service which made separate calls.

**`internal/config/config.go`** — reads `SPRING_DATASOURCE_URL` (JDBC URL) and parses it into host/port/dbname so the same DIGIT Helm chart env vars work for both Java and Go deployments.

## Environment variables

Uses the same env var names as the Java service's Helm chart:

| Env var | Purpose | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL (parsed for host/port/db) | — |
| `SPRING_DATASOURCE_USERNAME` | DB user | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `postgres` |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | Max open DB connections | `10` |
| `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE` | Max idle DB connections | `2` |
| `DB_SSL_MODE` | PostgreSQL SSL mode | `require` |
| `MDMS_SERVICE_HOST` | MDMS base URL | `http://localhost:8280/` |
| `MDMS_SERVICE_SEARCH_URI` | MDMS search path | `egov-mdms-service/v1/_search` |
| `IDFORMAT_FROM_MDMS` | Use MDMS for format lookup (vs DB) | `true` |
| `AUTOCREATE_NEW_SEQ` | Auto-create missing PG sequences | `true` |
| `ID_TIMEZONE` | Timezone for `cy:` tokens (`IST` → `Asia/Kolkata`) | `IST` |
| `SERVER_PORT` | HTTP listen port | `8080` |
| `SERVER_CONTEXT_PATH` | URL prefix | `/egov-idgen` |

## Key constraints

- **Sequence name validation**: sequence names extracted from format templates are validated as `[a-zA-Z0-9_]` only before interpolation into SQL (`isValidIdentifier` in `repository/idgen.go`). Do not relax this — it prevents SQL injection since sequence names cannot be parameterised in PostgreSQL.
- **Docker EXPOSE is 8080**: always set `SERVER_PORT=8080` when running in Docker/Kubernetes to match the exposed port.
- **RDS requires SSL**: `DB_SSL_MODE` defaults to `require`. Set to `disable` only for local Postgres.
- **`IST` timezone**: mapped manually to `Asia/Kolkata` in `locationName()` since Go's `time.LoadLocation` does not recognise the abbreviation on Alpine Linux.
