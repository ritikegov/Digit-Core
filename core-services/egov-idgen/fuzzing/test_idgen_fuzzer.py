"""
test_idgen_fuzzer.py
====================
Schemathesis-based fuzzer for the egov-idgen service.

Run modes
---------
1. Schemathesis auto-generated property tests (Hypothesis-driven):
       pytest test_idgen_fuzzer.py -k "test_generate_ids_schema"

2. Targeted / hand-crafted edge-case tests:
       pytest test_idgen_fuzzer.py -k "not test_generate_ids_schema"

3. Full run (both):
       pytest test_idgen_fuzzer.py -v

Environment variables
---------------------
  IDGEN_BASE_URL     Base URL of the service (default: http://localhost:8088/egov-idgen)
  IDGEN_AUTH_TOKEN   Bearer token for auth-protected deployments
  IDGEN_TENANT_ID    Tenant to use in requests (default: pb.amritsar)
"""
from __future__ import annotations

import os
import time
from pathlib import Path
from typing import Any

import pytest
import requests
import schemathesis
from hypothesis import HealthCheck, settings, Phase
from schemathesis.checks import (
    not_a_server_error,
    response_schema_conformance,
    status_code_conformance,
)

# ---------------------------------------------------------------------------
# Schema loading
# ---------------------------------------------------------------------------

_SPEC_PATH = Path(__file__).parent / "openapi.yaml"
_BASE_URL = os.environ.get("IDGEN_BASE_URL", "http://localhost:8088/egov-idgen")

schema = schemathesis.from_file(str(_SPEC_PATH), base_url=_BASE_URL)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _ri(extra: dict | None = None) -> dict:
    """Build a minimal valid RequestInfo."""
    ri = {
        "apiId": "egov-idgen-fuzzer",
        "ver": "1.0",
        "ts": int(time.time() * 1000),
        "action": "POST",
        "msgId": f"fuzz-{int(time.time() * 1000)}",
    }
    if extra:
        ri.update(extra)
    return ri


def _post(base_url: str, payload: dict) -> requests.Response:
    return requests.post(
        f"{base_url}/id/_generate",
        json=payload,
        timeout=10,
        headers={"Content-Type": "application/json"},
    )


# ---------------------------------------------------------------------------
# 1. Schemathesis property-based (Hypothesis-driven) tests
# ---------------------------------------------------------------------------

@schema.parametrize(endpoint="/id/_generate")
@settings(
    max_examples=150,
    suppress_health_check=[HealthCheck.too_slow, HealthCheck.filter_too_much],
    phases=[Phase.explicit, Phase.reuse, Phase.generate, Phase.shrink],
    deadline=10_000,   # 10 s per example — generous for a DB-backed service
)
def test_generate_ids_schema(case):
    """
    Auto-generate requests from the OpenAPI spec and assert that:
      - The service never returns an unhandled 5xx.
      - Every 2xx response body conforms to the response schema.
      - Every 4xx has a valid error body (not a raw exception stack trace).
    """
    response = case.call()
    case.validate_response(
        response,
        checks=[
            not_a_server_error,
            status_code_conformance,
            response_schema_conformance,
        ],
    )


# ---------------------------------------------------------------------------
# 2. Happy-path smoke tests (require a running + seeded service)
# ---------------------------------------------------------------------------

class TestHappyPath:
    """Basic positive tests — confirm the service works end-to-end."""

    def test_single_id_generation(self, base_url, valid_payload):
        r = _post(base_url, valid_payload)
        assert r.status_code == 200, r.text
        body = r.json()
        assert "idResponses" in body
        assert len(body["idResponses"]) == 1
        assert body["idResponses"][0].get("id")

    def test_bulk_id_generation(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "count": 5}],
        }
        r = _post(base_url, payload)
        assert r.status_code == 200, r.text
        body = r.json()
        ids = [resp["id"] for resp in body["idResponses"]]
        assert len(ids) == 5
        # IDs must be unique
        assert len(set(ids)) == 5, "Duplicate IDs returned for bulk request"

    def test_multiple_id_requests(self, base_url, valid_request_info):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [
                {"idName": "PT.PropertyId", "tenantId": "pb.amritsar"},
                {"idName": "PT.PropertyId", "tenantId": "pb.ludhiana"},
            ],
        }
        r = _post(base_url, payload)
        assert r.status_code in (200, 400), r.text  # 400 if tenant not configured

    def test_response_info_mirrors_request_info(self, base_url, valid_payload):
        r = _post(base_url, valid_payload)
        assert r.status_code == 200, r.text
        body = r.json()
        ri_in = valid_payload["RequestInfo"]
        ri_out = body.get("responseInfo", {})
        assert ri_out.get("apiId") == ri_in["apiId"]
        assert ri_out.get("ver") == ri_in["ver"]
        assert ri_out.get("status") == "SUCCESSFUL"


# ---------------------------------------------------------------------------
# 3. Boundary & edge-case tests
# ---------------------------------------------------------------------------

class TestBoundaryValues:
    """Test values at the edges of defined constraints."""

    def test_count_equals_1(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "count": 1}],
        }
        r = _post(base_url, payload)
        assert r.status_code in (200, 400), r.text

    def test_count_equals_1000(self, base_url, valid_request_info, valid_id_request):
        """Maximum allowed count — service must handle without crashing."""
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "count": 1000}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Server error on count=1000: {r.text}"

    def test_count_zero(self, base_url, valid_request_info, valid_id_request):
        """count=0 is invalid — must not cause 500."""
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "count": 0}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Unhandled server error on count=0: {r.text}"

    def test_count_negative(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "count": -1}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Unhandled server error on count=-1: {r.text}"

    def test_count_very_large(self, base_url, valid_request_info, valid_id_request):
        """Beyond spec max — should be rejected cleanly, not crash."""
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "count": 99999}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Unhandled server error on count=99999: {r.text}"

    def test_idname_max_length(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "idName": "X" * 200}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, r.text

    def test_idname_over_max_length(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "idName": "X" * 201}],
        }
        r = _post(base_url, payload)
        assert r.status_code in (400, 422), (
            f"Expected validation error for idName > 200 chars, got {r.status_code}: {r.text}"
        )

    def test_tenant_id_max_length(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "tenantId": "t" * 200}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, r.text

    def test_format_max_length(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "format": "F" * 200}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, r.text

    def test_empty_id_requests_list(self, base_url, valid_request_info):
        """Empty array should be rejected with a 4xx."""
        payload = {"RequestInfo": valid_request_info, "idRequests": []}
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Unhandled error on empty idRequests: {r.text}"

    def test_100_id_request_entries(self, base_url, valid_request_info, valid_id_request):
        """Large list of id requests — service must not crash."""
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [valid_id_request] * 100,
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Server error on 100 idRequests: {r.text}"


# ---------------------------------------------------------------------------
# 4. Null / missing field tests
# ---------------------------------------------------------------------------

class TestMissingRequiredFields:
    """Omitting required fields must yield 400, not 500."""

    @pytest.mark.parametrize("missing_field", ["idName", "tenantId"])
    def test_missing_id_request_field(self, base_url, valid_request_info, valid_id_request, missing_field):
        req = {k: v for k, v in valid_id_request.items() if k != missing_field}
        payload = {"RequestInfo": valid_request_info, "idRequests": [req]}
        r = _post(base_url, payload)
        assert r.status_code in (400, 422), (
            f"Expected 4xx when {missing_field!r} is missing, got {r.status_code}: {r.text}"
        )

    @pytest.mark.parametrize("missing_field", ["apiId", "ver", "ts", "action", "msgId"])
    def test_missing_request_info_field(self, base_url, valid_request_info, valid_id_request, missing_field):
        ri = {k: v for k, v in valid_request_info.items() if k != missing_field}
        payload = {"RequestInfo": ri, "idRequests": [valid_id_request]}
        r = _post(base_url, payload)
        assert r.status_code in (400, 422), (
            f"Expected 4xx when RequestInfo.{missing_field!r} is missing, got {r.status_code}: {r.text}"
        )

    def test_null_idname(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "idName": None}],
        }
        r = _post(base_url, payload)
        assert r.status_code in (400, 422), f"Expected 4xx for null idName, got {r.status_code}: {r.text}"

    def test_null_tenant_id(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "tenantId": None}],
        }
        r = _post(base_url, payload)
        assert r.status_code in (400, 422), f"Expected 4xx for null tenantId, got {r.status_code}: {r.text}"

    def test_missing_request_info_entirely(self, base_url, valid_id_request):
        payload = {"idRequests": [valid_id_request]}
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Unhandled 500 when RequestInfo is absent: {r.text}"

    def test_missing_id_requests_entirely(self, base_url, valid_request_info):
        payload = {"RequestInfo": valid_request_info}
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Unhandled 500 when idRequests is absent: {r.text}"


# ---------------------------------------------------------------------------
# 5. Malformed / type-confusion tests
# ---------------------------------------------------------------------------

class TestTypeConfusion:
    """Send values with incorrect types — must never result in an unhandled 500."""

    @pytest.mark.parametrize("bad_count", ["one", 1.5, True, [], {}])
    def test_count_wrong_type(self, base_url, valid_request_info, valid_id_request, bad_count):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "count": bad_count}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, (
            f"Unhandled 500 for count={bad_count!r}: {r.text}"
        )

    @pytest.mark.parametrize("bad_ts", ["not-a-number", None, "2024-01-01", 1.5])
    def test_ts_wrong_type(self, base_url, valid_request_info, valid_id_request, bad_ts):
        ri = {**valid_request_info, "ts": bad_ts}
        payload = {"RequestInfo": ri, "idRequests": [valid_id_request]}
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Unhandled 500 for ts={bad_ts!r}: {r.text}"

    def test_id_requests_not_array(self, base_url, valid_request_info, valid_id_request):
        payload = {"RequestInfo": valid_request_info, "idRequests": valid_id_request}
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Unhandled 500 when idRequests is object: {r.text}"

    def test_request_body_is_array(self, base_url):
        """Sending a top-level JSON array instead of an object."""
        r = requests.post(
            f"{base_url}/id/_generate",
            json=[{"foo": "bar"}],
            timeout=10,
        )
        assert r.status_code != 500, f"Unhandled 500 for array body: {r.text}"

    def test_request_body_is_string(self, base_url):
        r = requests.post(
            f"{base_url}/id/_generate",
            data="this is not json",
            headers={"Content-Type": "application/json"},
            timeout=10,
        )
        assert r.status_code != 500, f"Unhandled 500 for non-JSON body: {r.text}"

    def test_empty_body(self, base_url):
        r = requests.post(
            f"{base_url}/id/_generate",
            data="",
            headers={"Content-Type": "application/json"},
            timeout=10,
        )
        assert r.status_code != 500, f"Unhandled 500 for empty body: {r.text}"

    def test_wrong_content_type(self, base_url, valid_payload):
        import json
        r = requests.post(
            f"{base_url}/id/_generate",
            data=json.dumps(valid_payload),
            headers={"Content-Type": "text/plain"},
            timeout=10,
        )
        assert r.status_code != 500, f"Unhandled 500 for wrong content-type: {r.text}"


# ---------------------------------------------------------------------------
# 6. Injection / adversarial input tests
# ---------------------------------------------------------------------------

class TestAdversarialInputs:
    """
    Probe for injection vulnerabilities and abnormal processing.
    These tests verify that the service returns a safe error response,
    not an unhandled exception or information leak.
    """

    SQL_PAYLOADS = [
        "'; DROP TABLE id_generator; --",
        "' OR '1'='1",
        "1; SELECT * FROM id_generator",
        "' UNION SELECT NULL, NULL, NULL --",
        "PT.PropertyId' AND SLEEP(5) --",
    ]

    @pytest.mark.parametrize("sql", SQL_PAYLOADS)
    def test_sql_injection_in_idname(self, base_url, valid_request_info, valid_id_request, sql):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "idName": sql}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Possible SQL injection surface in idName — got 500: {r.text}"
        # Response must not echo back raw SQL error messages
        assert "syntax error" not in r.text.lower()
        assert "org.postgresql" not in r.text

    @pytest.mark.parametrize("sql", SQL_PAYLOADS)
    def test_sql_injection_in_tenant_id(self, base_url, valid_request_info, valid_id_request, sql):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "tenantId": sql}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Possible SQL injection surface in tenantId — got 500: {r.text}"
        assert "syntax error" not in r.text.lower()
        assert "org.postgresql" not in r.text

    @pytest.mark.parametrize("sql", SQL_PAYLOADS)
    def test_sql_injection_in_format(self, base_url, valid_request_info, valid_id_request, sql):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "format": sql}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Possible SQL injection in format — got 500: {r.text}"
        assert "syntax error" not in r.text.lower()

    FORMAT_INJECTION_PAYLOADS = [
        # Format placeholder abuse
        "[seq:" + "A" * 500 + "]",
        "[fy:../../etc/passwd]",
        "[city:../../../etc/passwd]",
        "[seq:${7*7}]",             # SSTI probe
        "{{7*7}}",                  # Jinja/Pebble SSTI
        "${7*7}",                   # Spring SpEL
        "#{7*7}",                   # Spring EL
        "%{7*7}",                   # OGNL
        "\\u0000",                  # Null byte
        "\r\n\r\nHTTP/1.1 200 OK",  # Header injection
    ]

    @pytest.mark.parametrize("fmt", FORMAT_INJECTION_PAYLOADS)
    def test_format_injection(self, base_url, valid_request_info, valid_id_request, fmt):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "format": fmt}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Server error on format injection payload {fmt!r}: {r.text}"
        # Must not leak stack traces
        assert "java.lang." not in r.text
        assert "at org.egov" not in r.text

    UNICODE_PAYLOADS = [
        "💥🔥🚀",           # Emoji
        "\u0000\u0001",      # Control characters
        "𝒯𝑒𝓈𝓉",             # Mathematical script
        "Ａ" * 50,           # Full-width ASCII
        "\u202e reversed",   # Right-to-left override
        "中文租户",           # CJK characters
    ]

    @pytest.mark.parametrize("value", UNICODE_PAYLOADS)
    def test_unicode_in_idname(self, base_url, valid_request_info, valid_id_request, value):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "idName": value}],
        }
        r = _post(base_url, payload)
        assert r.status_code != 500, f"Server error on unicode idName {value!r}: {r.text}"


# ---------------------------------------------------------------------------
# 7. Information disclosure tests
# ---------------------------------------------------------------------------

class TestInformationDisclosure:
    """Verify error responses do not leak internal implementation details."""

    def test_no_stack_trace_on_invalid_format(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "format": "[INVALID_PLACEHOLDER_XYZ]"}],
        }
        r = _post(base_url, payload)
        assert "at org.egov" not in r.text, "Stack trace leaked in response body"
        assert "at java." not in r.text, "Stack trace leaked in response body"

    def test_no_stack_trace_on_missing_sequence(self, base_url, valid_request_info, valid_id_request):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "idName": "NONEXISTENT.Format"}],
        }
        r = _post(base_url, payload)
        assert "at org.egov" not in r.text, "Stack trace leaked in error response"

    def test_no_db_url_in_response(self, base_url, valid_request_info):
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{"idName": None, "tenantId": None}],
        }
        r = _post(base_url, payload)
        assert "jdbc:postgresql" not in r.text, "DB connection string exposed in error response"
        assert "postgres" not in r.text.lower() or r.status_code == 200, (
            "PostgreSQL info may be leaked in error body"
        )

    def test_error_response_structure(self, base_url, valid_request_info, valid_id_request):
        """Error responses should conform to the ErrorRes schema."""
        payload = {
            "RequestInfo": valid_request_info,
            "idRequests": [{**valid_id_request, "idName": None}],
        }
        r = _post(base_url, payload)
        if r.status_code in (400, 422):
            body = r.json()
            # Should have ResponseInfo with FAILED status
            ri_out = body.get("ResponseInfo") or body.get("responseInfo") or {}
            assert ri_out.get("status") in ("FAILED", None), (
                f"Expected FAILED status in error response, got: {ri_out}"
            )


# ---------------------------------------------------------------------------
# 8. Concurrency / idempotency tests
# ---------------------------------------------------------------------------

class TestConcurrency:
    """Ensure concurrent requests do not produce duplicate IDs."""

    def test_no_duplicate_ids_under_load(self, base_url, valid_request_info, valid_id_request):
        import concurrent.futures

        def generate_one():
            payload = {
                "RequestInfo": {**valid_request_info, "ts": int(time.time() * 1000)},
                "idRequests": [valid_id_request],
            }
            r = _post(base_url, payload)
            if r.status_code == 200:
                return r.json()["idResponses"][0]["id"]
            return None

        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
            futures = [pool.submit(generate_one) for _ in range(20)]
            ids = [f.result() for f in concurrent.futures.as_completed(futures)]

        generated = [i for i in ids if i is not None]
        assert len(generated) == len(set(generated)), (
            f"Duplicate IDs generated under concurrent load! IDs: {generated}"
        )


# ---------------------------------------------------------------------------
# 9. HTTP method / endpoint tests
# ---------------------------------------------------------------------------

class TestHttpMethods:
    """Only POST should be accepted on /id/_generate."""

    @pytest.mark.parametrize("method", ["GET", "PUT", "PATCH", "DELETE"])
    def test_unsupported_http_methods(self, base_url, method):
        r = requests.request(method, f"{base_url}/id/_generate", timeout=10)
        assert r.status_code in (405, 404, 400), (
            f"Expected 4xx for {method} /id/_generate, got {r.status_code}"
        )

    def test_unknown_endpoint(self, base_url):
        r = requests.get(f"{base_url}/id/_nonexistent", timeout=10)
        assert r.status_code in (404, 405), f"Unexpected status for unknown endpoint: {r.status_code}"
