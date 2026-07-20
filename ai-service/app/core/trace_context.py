import json
import re
import uuid
from contextvars import ContextVar

from fastapi import Request
from fastapi.responses import JSONResponse, Response
from starlette.middleware.base import BaseHTTPMiddleware

_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$")
_STAGE_PATTERN = re.compile(r"^[A-Z][A-Z0-9_]{0,99}$")
trace_id_var: ContextVar[str | None] = ContextVar("trace_id", default=None)
request_id_var: ContextVar[str | None] = ContextVar("request_id", default=None)
stage_var: ContextVar[str | None] = ContextVar("ai_stage", default=None)


def current_trace_id() -> str:
    return trace_id_var.get() or f"trc-{uuid.uuid4()}"


def current_request_id() -> str:
    return request_id_var.get() or f"req-{uuid.uuid4().hex}"


class TraceContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        raw_trace_id = request.headers.get("X-Trace-Id")
        raw_request_id = request.headers.get("X-Request-Id")
        raw_stage = request.headers.get("X-AI-Stage")
        if raw_trace_id and not _ID_PATTERN.fullmatch(raw_trace_id):
            return _invalid_identifier("X-Trace-Id")
        if raw_request_id and not _ID_PATTERN.fullmatch(raw_request_id):
            return _invalid_identifier("X-Request-Id")
        if raw_stage and not _STAGE_PATTERN.fullmatch(raw_stage):
            return _invalid_identifier("X-AI-Stage")

        trace_id = raw_trace_id or f"trc-{uuid.uuid4()}"
        request_id = raw_request_id or f"req-{uuid.uuid4().hex}"
        tokens = (
            trace_id_var.set(trace_id),
            request_id_var.set(request_id),
            stage_var.set(raw_stage),
        )
        request.state.trace_id = trace_id
        request.state.request_id = request_id
        request.state.ai_stage = raw_stage
        try:
            response = await call_next(request)
            response.headers["X-Trace-Id"] = trace_id
            response.headers["X-Request-Id"] = request_id
            if "application/json" not in response.headers.get("content-type", ""):
                return response
            body = b"".join([chunk async for chunk in response.body_iterator])
            try:
                payload = json.loads(body)
            except (json.JSONDecodeError, UnicodeDecodeError):
                return Response(content=body, status_code=response.status_code,
                                headers=dict(response.headers))
            if isinstance(payload, dict):
                payload["traceId"] = trace_id
                payload.setdefault("requestId", request_id)
            headers = dict(response.headers)
            headers.pop("content-length", None)
            return JSONResponse(payload, status_code=response.status_code, headers=headers)
        finally:
            trace_id_var.reset(tokens[0])
            request_id_var.reset(tokens[1])
            stage_var.reset(tokens[2])


def _invalid_identifier(header: str) -> JSONResponse:
    return JSONResponse(status_code=400, content={
        "requestId": f"req-{uuid.uuid4().hex}",
        "traceId": f"trc-{uuid.uuid4()}",
        "status": "FAILED",
        "error": {"code": "INVALID_TRACE_CONTEXT",
                  "message": f"{header} is invalid", "retryable": False},
    })
