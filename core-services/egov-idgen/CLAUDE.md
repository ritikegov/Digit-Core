# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build
mvn clean install

# Build (skip tests)
mvn clean install -DskipTests

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=IdGenerationServiceTest

# Run a single test method
mvn test -Dtest=IdGenerationServiceTest#testGenerateIdResponse

# Run the service locally
mvn spring-boot:run
```

No checkstyle plugin is configured in `pom.xml`.

## Local Setup

The service runs on `http://localhost:8088/egov-idgen`. It requires:

1. **PostgreSQL** at `localhost:5432`, database `rainmaker_new` (credentials: `postgres`/`postgres`). Flyway runs migrations automatically on startup.

2. **MDMS service** for ID format lookups. Port-forward it locally and update `application.properties`:
   ```properties
   mdms.service.host=http://127.0.0.1:8088/
   ```
   Alternatively, set `idformat.from.mdms=false` and seed the `id_generator` table directly.

3. **Lombok** — requires IDE plugin (IntelliJ: install from marketplace; Eclipse: add `-javaagent:/path/to/lombok.jar` to `eclipse.ini`) and annotation processing enabled.

## Architecture

### Request Flow

```
POST /egov-idgen/id/_generate
  → IdGenerationController
  → IdGenerationService.generateIdResponse()
      → for each IdRequest:
          1. getIdFormatFinal()   ← fetches format from MDMS or id_generator table
          2. getFormattedId()     ← resolves format placeholders into a concrete ID
```

### ID Format Resolution

`IdGenerationService` resolves format strings that contain zero or more placeholders:

| Placeholder | Resolved to |
|-------------|-------------|
| `[seq:SEQ_NAME]` | Next value from a PostgreSQL sequence (`SELECT NEXTVAL(...)`) |
| `[fy:yyyy-yy]` | Financial year string (Apr–Mar boundaries, IST timezone) |
| `[cy:yyyy]` | Calendar year string |
| `[city:<jsonpath>]` | City code fetched from MDMS `tenant` module |
| `[d{n}]` / `[a{n}]` / `[an{n}]` | n random digits / alpha / alphanumeric chars |

When `idformat.from.mdms=true` (default), the format string is fetched from MDMS module `common-masters`, master `IdFormat`, filtered by `idName` and `tenantId`. When false, it is read from the `id_generator` PostgreSQL table.

If `autocreate.new.seq=true`, missing PostgreSQL sequences are created automatically via `CREATE SEQUENCE`. Default is `false`, so a missing sequence throws `IDSeqNotFoundException`.

### Key Configuration Flags (`application.properties`)

```properties
idformat.from.mdms=true       # true = MDMS, false = local id_generator table
autocreate.new.seq=false      # auto-create DB sequences on first use
id.timezone=IST               # timezone for [fy:*] and [cy:*] placeholders
```

### Exception Handling

`GlobalExceptionHandler` (a `@RestControllerAdvice`) maps all exceptions to `ErrorRes` JSON — never returns raw stack traces. Custom exceptions (`InvalidIDFormatException`, `IDSeqNotFoundException`, `IDSeqOverflowException`) carry the originating `RequestInfo` so error responses can echo back `apiId`, `ver`, and `msgId`.

### Database Schema

Single table:
```sql
CREATE TABLE id_generator (
    idname       VARCHAR(200) NOT NULL,
    tenantid     VARCHAR(200) NOT NULL,
    format       VARCHAR(200) NOT NULL,
    sequencenumber INTEGER   NOT NULL
);
```
Flyway migrations live in `src/main/resources/db/migration/main/` (35 versioned SQL files). Seed/dev data is in `db/migration/dev/` and `db/migration/seed/`.

### MDMS Integration (`MdmsService`)

Uses `MdmsClientService` from the `egov-mdms-service` dependency. Calls `POST /egov-mdms-service/v1/_search` with a `MdmsCriteriaReq`. JSONPath is used to extract values from the response — `$.MdmsRes.common-masters.IdFormat[?(@.idName=='{idName}')].format` for formats and `$.MdmsRes.tenant.tenants[?(@.code=='{tenantCode}')].city.code` for city codes.

### Fuzzing

A Schemathesis-based fuzzer lives in `fuzzing/`. See `fuzzing/README.md` for usage. The OpenAPI spec (`fuzzing/openapi.yaml`) was hand-authored from the source models and serves as the ground truth for fuzz testing.
