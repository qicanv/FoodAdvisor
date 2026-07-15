from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_endpoint() -> None:
    response = client.get("/health")

    assert response.status_code == 200

    body = response.json()

    assert body["service"] == "ai-service"
    assert body["status"] in {"UP", "DEGRADED"}
    assert "dependencies" in body