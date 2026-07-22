import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app

TEST_INTERNAL_TOKEN = "test-internal-token"

client = TestClient(app)


@pytest.fixture(autouse=True)
def configure_internal_token(monkeypatch: pytest.MonkeyPatch) -> None:
    """
    每个测试显式配置测试 Token，不依赖开发者本机的 .env。
    """
    monkeypatch.setattr(
        settings,
        "internal_api_token",
        TEST_INTERNAL_TOKEN,
    )


def build_headers(request_id: str) -> dict[str, str]:
    return {
        "X-Internal-Token": TEST_INTERNAL_TOKEN,
        "X-Request-Id": request_id,
    }


def test_internal_endpoint_success() -> None:
    response = client.post(
        "/internal/test",
        headers=build_headers("req-test-001"),
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


def test_internal_endpoint_rejects_missing_token() -> None:
    response = client.post(
        "/internal/test",
        headers={
            "X-Request-Id": "req-test-missing-token",
        },
        json={
            "requestId": "req-test-missing-token",
            "message": "hello",
        },
    )

    assert response.status_code == 401


def test_internal_endpoint_rejects_when_server_token_not_configured(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setattr(settings, "internal_api_token", None)

    response = client.post(
        "/internal/test",
        headers={
            "X-Internal-Token": TEST_INTERNAL_TOKEN,
            "X-Request-Id": "req-test-server-token-missing",
        },
        json={
            "requestId": "req-test-server-token-missing",
            "message": "hello",
        },
    )

    assert response.status_code == 503


def test_internal_endpoint_rejects_empty_message() -> None:
    response = client.post(
        "/internal/test",
        headers=build_headers("req-test-003"),
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