from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app

client = TestClient(app)


def test_internal_endpoint_success() -> None:
    response = client.post(
        "/internal/test",
        headers={
            "X-Internal-Token": settings.internal_api_token,
            "X-Request-Id": "req-test-001",
        },
        json={
            "requestId": "req-test-001",
            "message": "hello",
        },
    )

    assert response.status_code == 200

    body = response.json()

    assert body["requestId"] == "req-test-001"
    assert body["status"] == "SUCCESS"
    assert body["data"]["echo"] == "hello"


def test_internal_endpoint_rejects_wrong_token() -> None:
    response = client.post(
        "/internal/test",
        headers={
            "X-Internal-Token": "wrong-token",
            "X-Request-Id": "req-test-002",
        },
        json={
            "requestId": "req-test-002",
            "message": "hello",
        },
    )

    assert response.status_code == 401

    body = response.json()

    assert body["requestId"] == "req-test-002"
    assert body["status"] == "FAILED"
    assert body["error"]["code"] == "UNAUTHORIZED"


def test_internal_endpoint_rejects_empty_message() -> None:
    response = client.post(
        "/internal/test",
        headers={
            "X-Internal-Token": settings.internal_api_token,
            "X-Request-Id": "req-test-003",
        },
        json={
            "requestId": "req-test-003",
            "message": "",
        },
    )

    assert response.status_code == 422

    body = response.json()

    assert body["requestId"] == "req-test-003"
    assert body["status"] == "FAILED"
    assert body["error"]["code"] == "INVALID_REQUEST"