import uuid
from datetime import datetime, timezone
from typing import Any

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse


def get_request_id(request: Request) -> str:
    request_id = request.headers.get("X-Request-Id")

    if request_id:
        return request_id

    return f"req-{uuid.uuid4().hex}"


def make_json_safe(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            str(key): make_json_safe(item)
            for key, item in value.items()
        }

    if isinstance(value, (list, tuple)):
        return [make_json_safe(item) for item in value]

    if isinstance(value, (str, int, float, bool)) or value is None:
        return value

    return str(value)


def clean_validation_errors(errors: list[dict[str, Any]]) -> list[dict[str, Any]]:
    cleaned: list[dict[str, Any]] = []

    for error in errors:
        item: dict[str, Any] = {
            "loc": make_json_safe(error.get("loc")),
            "type": make_json_safe(error.get("type")),
            "msg": make_json_safe(error.get("msg")),
        }

        if "ctx" in error:
            item["ctx"] = make_json_safe(error.get("ctx"))

        cleaned.append(item)

    return cleaned


def register_exception_handlers(application: FastAPI) -> None:
    @application.exception_handler(HTTPException)
    async def http_exception_handler(
        request: Request,
        exception: HTTPException,
    ) -> JSONResponse:
        error_code = (
            "UNAUTHORIZED"
            if exception.status_code == 401
            else "HTTP_ERROR"
        )

        return JSONResponse(
            status_code=exception.status_code,
            content={
                "requestId": get_request_id(request),
                "status": "FAILED",
                "error": {
                    "code": error_code,
                    "message": str(exception.detail),
                    "retryable": False,
                },
                "timestamp": datetime.now(timezone.utc).isoformat(),
            },
        )

    @application.exception_handler(RequestValidationError)
    async def validation_exception_handler(
        request: Request,
        exception: RequestValidationError,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content={
                "requestId": get_request_id(request),
                "status": "FAILED",
                "error": {
                    "code": "INVALID_REQUEST",
                    "message": "Request validation failed",
                    "retryable": False,
                    "details": clean_validation_errors(
                        exception.errors()
                    ),
                },
                "timestamp": datetime.now(timezone.utc).isoformat(),
            },
        )

    @application.exception_handler(Exception)
    async def unknown_exception_handler(
        request: Request,
        exception: Exception,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=500,
            content={
                "requestId": get_request_id(request),
                "status": "FAILED",
                "error": {
                    "code": "INTERNAL_ERROR",
                    "message": "Internal server error",
                    "retryable": False,
                },
                "timestamp": datetime.now(timezone.utc).isoformat(),
            },
        )
