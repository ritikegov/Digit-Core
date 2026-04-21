# egov-idgen Schemathesis Fuzzer

Property-based and adversarial HTTP fuzzer for the **egov-idgen** service,
built on [Schemathesis](https://schemathesis.readthedocs.io/) (Hypothesis-driven
OpenAPI testing).

---

## Prerequisites

| Tool | Version |
|------|---------|
| Python | ≥ 3.10 |
| pip | ≥ 23 |
| egov-idgen service | running & reachable |

---

## Quick start

```bash
# 1. Create and activate a virtual environment
python3 -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate

# 2. Install dependencies
pip install -r requirements.txt

# 3. Point the fuzzer at your running service
export IDGEN_BASE_URL="http://localhost:8088/egov-idgen"   # default
export IDGEN_AUTH_TOKEN=""          # leave blank if auth is disabled locally
export IDGEN_TENANT_ID="pb.amritsar"

# 4. Run all tests
pytest test_idgen_fuzzer.py -v
```

---

## Running modes

### Schemathesis CLI (standalone — no pytest required)

```bash
# Basic run against the local spec
schemathesis run openapi.yaml \
  --base-url http://localhost:8088/egov-idgen \
  --checks all \
  --max-examples 200

# Target a specific endpoint
schemathesis run openapi.yaml \
  --base-url http://localhost:8088/egov-idgen \
  --endpoint "/id/_generate" \
  --checks not_a_server_error,status_code_conformance,response_schema_conformance

# Run against a live URL (pulls spec from the service if it exposes /v3/api-docs)
schemathesis run http://localhost:8088/egov-idgen/v3/api-docs \
  --checks all \
  --max-examples 200
```

### pytest — all tests

```bash
pytest test_idgen_fuzzer.py -v
```

### pytest — only property-based (Hypothesis) tests

```bash
pytest test_idgen_fuzzer.py -k "test_generate_ids_schema" -v
```

### pytest — only hand-crafted edge cases (faster, no Hypothesis)

```bash
pytest test_idgen_fuzzer.py -k "not test_generate_ids_schema" -v
```

### Parallel execution

```bash
# Requires pytest-xdist
pytest test_idgen_fuzzer.py -n auto -v
```

### HTML report

```bash
pytest test_idgen_fuzzer.py -v --html=report.html --self-contained-html
```

---

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `IDGEN_BASE_URL` | `http://localhost:8088/egov-idgen` | Base URL of the running service |
| `IDGEN_AUTH_TOKEN` | *(empty)* | Bearer / auth token for secured deployments |
| `IDGEN_TENANT_ID` | `pb.amritsar` | Tenant used in test requests |

---

## Test coverage

| Test class | What it covers |
|------------|---------------|
| `test_generate_ids_schema` | Hypothesis-generated cases — schema conformance, no 5xx |
| `TestHappyPath` | Positive smoke tests — single, bulk, multi-tenant IDs |
| `TestBoundaryValues` | count=0/1/1000/99999, field max lengths, oversized fields |
| `TestMissingRequiredFields` | Each required field omitted individually |
| `TestTypeConfusion` | Wrong types for count, ts, non-JSON body |
| `TestAdversarialInputs` | SQL injection, format-placeholder injection, SSTI probes, unicode |
| `TestInformationDisclosure` | No stack traces, no DB URLs in error responses |
| `TestConcurrency` | 20 concurrent requests — no duplicate IDs |
| `TestHttpMethods` | GET/PUT/PATCH/DELETE on POST-only endpoint |

---

## File layout

```
fuzzing/
├── openapi.yaml          # OpenAPI 3.0 spec (hand-authored from source code)
├── conftest.py           # Schemathesis global hooks + shared pytest fixtures
├── test_idgen_fuzzer.py  # All test cases
├── requirements.txt      # Python dependencies
└── README.md             # This file
```

---

## Known limitations

* **MDMS dependency** — many tests that use a real `idName` (e.g. `PT.PropertyId`)
  will fail with 400/500 unless the service can reach an MDMS instance with that
  format configured.  Set `idformat.from.mdms=false` in `application.properties`
  and seed the `id_generator` table locally to run fully offline.

* **Sequence not found** — `IDSeqNotFoundException` is expected when a sequence
  referenced in a format string doesn't exist in PostgreSQL.  This returns a
  structured error (not a 500) and is counted as a pass.

* **Auth** — If the service is behind an API gateway requiring a valid Keycloak
  token, set `IDGEN_AUTH_TOKEN` to a valid token; otherwise all requests will
  receive 401 and most tests will be skipped implicitly (they assert `!= 500`).
