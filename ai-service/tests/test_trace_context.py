from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_normal_response_echoes_supplied_trace(monkeypatch):
    response = client.get(
        "/health",
        headers={
            "X-Trace-Id": "trc-12345678-1234-1234-1234-123456789012",
            "X-Request-Id": "request-1",
            "X-AI-Stage": "MODEL_CALL",
        },
    )
    assert response.status_code == 200
    assert response.json()["traceId"] == "trc-12345678-1234-1234-1234-123456789012"
    assert response.json()["requestId"] == "request-1"


def test_missing_trace_generates_compatible_trace():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["traceId"].startswith("trc-")
    assert response.json()["requestId"].startswith("req-")


def test_illegal_and_too_long_trace_are_rejected():
    illegal = client.get("/health", headers={"X-Trace-Id": "bad trace"})
    too_long = client.get("/health", headers={"X-Trace-Id": "t" * 101})
    assert illegal.status_code == 400
    assert too_long.status_code == 400
    assert illegal.json()["error"]["code"] == "INVALID_TRACE_CONTEXT"
