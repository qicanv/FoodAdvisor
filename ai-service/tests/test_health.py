from fastapi.testclient import TestClient

import app.api.health as health_module
from app.core.config import settings
from app.main import app

client = TestClient(app)


def test_health_endpoint_model_configured(monkeypatch) -> None:
    # 避免测试依赖真实 OpenSearch。
    monkeypatch.setattr(
        health_module,
        "check_opensearch_connection",
        lambda: True,
    )

    # 使用测试数据，不依赖本地 .env 中的真实密钥。
    monkeypatch.setattr(settings, "llm_api_key", "test-api-key")
    monkeypatch.setattr(
        settings,
        "llm_base_url",
        "https://example.com/v1",
    )
    monkeypatch.setattr(settings, "llm_model", "test-model")

    response = client.get("/health")

    assert response.status_code == 200

    body = response.json()

    assert body["service"] == "ai-service"
    assert body["status"] == "UP"
    assert body["dependencies"]["openSearch"] == "UP"
    assert body["dependencies"]["modelApi"] == "CONFIGURED"
    assert "timestamp" in body


def test_health_endpoint_model_not_configured(monkeypatch) -> None:
    monkeypatch.setattr(
        health_module,
        "check_opensearch_connection",
        lambda: True,
    )

    monkeypatch.setattr(settings, "llm_api_key", None)
    monkeypatch.setattr(
        settings,
        "llm_base_url",
        "https://example.com/v1",
    )
    monkeypatch.setattr(settings, "llm_model", "test-model")

    response = client.get("/health")

    assert response.status_code == 200

    body = response.json()

    assert body["service"] == "ai-service"
    assert body["status"] == "UP"
    assert body["dependencies"]["openSearch"] == "UP"
    assert body["dependencies"]["modelApi"] == "NOT_CONFIGURED"


def test_health_endpoint_when_opensearch_is_down(monkeypatch) -> None:
    monkeypatch.setattr(
        health_module,
        "check_opensearch_connection",
        lambda: False,
    )

    monkeypatch.setattr(settings, "llm_api_key", "test-api-key")
    monkeypatch.setattr(
        settings,
        "llm_base_url",
        "https://example.com/v1",
    )
    monkeypatch.setattr(settings, "llm_model", "test-model")

    response = client.get("/health")

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "DEGRADED"
    assert body["dependencies"]["openSearch"] == "DOWN"
    assert body["dependencies"]["modelApi"] == "CONFIGURED"
