"""
conftest.py — Schemathesis hooks and shared fixtures for egov-idgen fuzzer.

Hooks applied here are module-global and run for every generated test case.
"""
from __future__ import annotations

import time
import os
import logging
from typing import Any, Generator

import pytest
import schemathesis
from schemathesis import Case
from schemathesis.hooks import HookContext

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger("idgen-fuzzer")


# ---------------------------------------------------------------------------
# Configuration (override via environment variables for CI / different envs)
# ---------------------------------------------------------------------------
BASE_URL = os.environ.get("IDGEN_BASE_URL", "http://localhost:8088/egov-idgen")
AUTH_TOKEN = os.environ.get("IDGEN_AUTH_TOKEN", "")
TENANT_ID = os.environ.get("IDGEN_TENANT_ID", "pb.amritsar")


# ---------------------------------------------------------------------------
# Global Schemathesis hooks
# ---------------------------------------------------------------------------

@schemathesis.hook
def before_generate_case(context: HookContext, strategy):
    """
    Post-process every Hypothesis-generated Case to:
      1. Ensure RequestInfo required fields are always present and valid.
      2. Inject a realistic timestamp.
      3. Add auth token from environment if available.
    """
    import hypothesis.strategies as st

    def fix_request_info(case: Case) -> Case:
        if case.body is None:
            return case

        body: dict = case.body
        ri = body.setdefault("RequestInfo", {})

        # Required fields — ensure they are non-null and within size limits
        if not ri.get("apiId"):
            ri["apiId"] = "egov-idgen-fuzzer"
        if not ri.get("ver"):
            ri["ver"] = "1.0"
        if not ri.get("ts"):
            ri["ts"] = int(time.time() * 1000)
        if not ri.get("action"):
            ri["action"] = "POST"
        if not ri.get("msgId"):
            ri["msgId"] = f"fuzz-{int(time.time() * 1000)}"

        # Truncate strings that exceed column limits to avoid trivial 400s
        # that are not interesting from a security/logic perspective
        for key, limit in [("apiId", 128), ("ver", 32), ("action", 32), ("msgId", 256)]:
            if isinstance(ri.get(key), str) and len(ri[key]) > limit:
                ri[key] = ri[key][:limit]

        if AUTH_TOKEN:
            ri["authToken"] = AUTH_TOKEN

        # Ensure idRequests list has at least one valid entry
        id_requests = body.get("idRequests") or []
        if not id_requests:
            id_requests = [{"idName": "PT.PropertyId", "tenantId": TENANT_ID}]
            body["idRequests"] = id_requests

        for req in id_requests:
            if isinstance(req, dict):
                if not req.get("idName"):
                    req["idName"] = "PT.PropertyId"
                if not req.get("tenantId"):
                    req["tenantId"] = TENANT_ID
                # Truncate to schema max
                for k, lim in [("idName", 200), ("tenantId", 200), ("format", 200)]:
                    if isinstance(req.get(k), str) and len(req[k]) > lim:
                        req[k] = req[k][:lim]
                # count must be positive
                if isinstance(req.get("count"), int) and req["count"] < 1:
                    req["count"] = 1

        return case

    return strategy.map(fix_request_info)


@schemathesis.hook
def after_call(context: HookContext, response, case: Case) -> None:
    """
    Log every request/response pair.
    Detect unexpected 5xx responses that should be investigated.
    """
    status = response.status_code
    method = case.method.upper()
    path = case.formatted_path

    log_level = logging.INFO if status < 500 else logging.WARNING
    logger.log(log_level, "%s %s → HTTP %d", method, path, status)

    if status >= 500:
        logger.warning(
            "Unexpected 5xx — Body: %s\nRequest body: %s",
            response.text[:500],
            str(case.body)[:500],
        )


# ---------------------------------------------------------------------------
# Shared fixtures
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def base_url() -> str:
    return BASE_URL


@pytest.fixture(scope="session")
def valid_request_info() -> dict:
    """A baseline RequestInfo that always passes validation."""
    return {
        "apiId": "egov-idgen-fuzzer",
        "ver": "1.0",
        "ts": int(time.time() * 1000),
        "action": "POST",
        "msgId": f"fuzz-{int(time.time() * 1000)}",
        "authToken": AUTH_TOKEN or "test-token",
    }


@pytest.fixture(scope="session")
def valid_id_request() -> dict:
    """A single IdRequest entry that is valid against the live service."""
    return {
        "idName": "PT.PropertyId",
        "tenantId": TENANT_ID,
        "count": 1,
    }


@pytest.fixture(scope="session")
def valid_payload(valid_request_info, valid_id_request) -> dict:
    return {
        "RequestInfo": valid_request_info,
        "idRequests": [valid_id_request],
    }
