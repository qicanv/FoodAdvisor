import pytest
from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app
from app.services import dialogue_extraction_service as service_module

@pytest.fixture(autouse=True)
def configure_internal_token(monkeypatch):
    """为本测试文件临时配置内部 token，测试结束后自动恢复。"""
    monkeypatch.setattr(
        settings,
        "internal_api_token",
        "test-token",
    )

client = TestClient(app)


def auth_headers(token: str = "test-token") -> dict[str, str]:
    return {
        "X-Internal-Token": token,
        "X-Request-Id": "req-dialogue-test",
    }


def runtime_model() -> dict:
    return {
        "provider": "OPENAI_COMPATIBLE",
        "modelName": "mock-model",
        "baseUrl": "https://model.example.com/v1",
        "apiKey": "plain-runtime-secret",
        "timeoutMs": 12345,
        "temperature": 0.25,
        "maxOutputTokens": 1500,
    }


def request_body(
    content: object = (
        "四个人，人均八十，想吃川菜，"
        "三公里以内，环境安静"
    ),
) -> dict:
    return {
        "sessionId": 10,
        "messageId": 100,
        "content": content,
        "currentConstraints": {
            "partySize": 2,
        },
        "runtimeModel": runtime_model(),
    }


def mock_model(
    monkeypatch,
    result: dict,
    configured: bool = True,
) -> dict:
    captured: dict = {
        "init": [],
        "chat": [],
    }

    class FakeLLMService:
        def __init__(self, **kwargs):
            captured["init"].append(kwargs)
            self.model = kwargs.get("model")
            self.provider = kwargs.get("provider")

        def is_configured(self) -> bool:
            return configured

        async def chat_json(self, *args, **kwargs):
            captured["chat"].append(kwargs)
            return result

    monkeypatch.setattr(
        service_module,
        "LLMService",
        FakeLLMService,
    )

    return captured


def success_result(**overrides) -> dict:
    result = {
        "intent": "MERCHANT_RECOMMENDATION",
        "extractedConstraints": {
            "partySize": 4,
            "perCapitaBudget": 80,
            "cuisines": ["川菜"],
            "distanceKm": 3,
            "environmentRequirements": ["安静"],
        },
        "clearedFields": [],
        "confidence": 0.92,
        "extractor": "AI_MODEL",
        "degraded": False,
    }
    result.update(overrides)
    return result


def post_extract(body: dict, token: str = "test-token"):
    return client.post(
        "/internal/dialogue/extract",
        headers=auth_headers(token),
        json=body,
    )


def test_dialogue_extract_accepts_correct_internal_token(monkeypatch):
    mock_model(monkeypatch, success_result())

    response = post_extract(request_body())

    assert response.status_code == 200
    assert response.json()["extractor"] == "AI_MODEL"


def test_dialogue_extract_rejects_wrong_internal_token(monkeypatch):
    mock_model(monkeypatch, success_result())

    response = post_extract(request_body(), token="wrong-token")

    assert response.status_code == 401
    assert response.json()["error"]["code"] == "UNAUTHORIZED"


def test_dialogue_extract_rejects_null_content():
    response = post_extract(request_body(None))

    assert response.status_code == 422
    assert response.headers["content-type"].startswith("application/json")
    assert response.json()["error"]["code"] == "INVALID_REQUEST"


@pytest.mark.parametrize(
    "content",
    ["", "   "],
)
def test_dialogue_extract_rejects_blank_content(
    monkeypatch,
    content,
):
    called = {
        "value": False,
    }

    class FailIfCreated:
        def __init__(self, **kwargs):
            called["value"] = True

    monkeypatch.setattr(
        service_module,
        "LLMService",
        FailIfCreated,
    )

    response = post_extract(
        request_body(content)
    )

    assert response.status_code == 422

    body = response.json()

    assert body["error"]["code"] == "INVALID_REQUEST"
    assert "content" in str(
        body["error"]["details"]
    )
    assert "content must not be blank" in str(
        body["error"]["details"]
    )
    assert "ValueError(" not in response.text
    assert called["value"] is False


def test_dialogue_extracts_party_size(monkeypatch):
    mock_model(monkeypatch, success_result())

    body = post_extract(request_body()).json()

    assert body["extractedConstraints"]["partySize"] == 4


def test_dialogue_extracts_budget(monkeypatch):
    mock_model(monkeypatch, success_result())

    body = post_extract(request_body()).json()

    assert body["extractedConstraints"]["perCapitaBudget"] == 80


def test_dialogue_extracts_cuisine(monkeypatch):
    mock_model(monkeypatch, success_result())

    body = post_extract(request_body()).json()

    assert body["extractedConstraints"]["cuisines"] == ["川菜"]


def test_dialogue_extracts_distance(monkeypatch):
    mock_model(monkeypatch, success_result())

    body = post_extract(request_body()).json()

    assert body["extractedConstraints"]["distanceKm"] == 3


def test_dialogue_extracts_environment(monkeypatch):
    mock_model(monkeypatch, success_result())

    body = post_extract(request_body()).json()

    assert body["extractedConstraints"]["environmentRequirements"] == ["安静"]


def test_dialogue_accepts_dish_keywords_and_budget(monkeypatch):
    mock_model(
        monkeypatch,
        success_result(
            extractedConstraints={
                "dishKeywords": ["水煮鱼"],
                "perCapitaBudget": 80,
            }
        ),
    )

    body = post_extract(request_body("想吃水煮鱼，人均80元")).json()

    assert body["extractedConstraints"]["dishKeywords"] == ["水煮鱼"]
    assert body["extractedConstraints"]["perCapitaBudget"] == 80


def test_dialogue_accepts_business_target_time_and_model_metadata(monkeypatch):
    mock_model(
        monkeypatch,
        success_result(
            extractedConstraints={
                "businessTargetTime": "22:00",
                "businessTargetNextDay": False,
            }
        ),
    )

    body = post_extract(request_body("晚上十点后还营业")).json()

    assert body["extractedConstraints"]["businessTargetTime"] == "22:00"
    assert body["extractedConstraints"]["businessTargetNextDay"] is False
    assert body["extractor"] == "AI_MODEL"
    assert body["degraded"] is False
    assert body["modelName"] == "mock-model"
    assert body["provider"] == "OPENAI_COMPATIBLE"


def test_dialogue_rejects_invalid_business_target_time(monkeypatch):
    mock_model(
        monkeypatch,
        success_result(
            extractedConstraints={"businessTargetTime": "25:99"}
        ),
    )

    assert post_extract(request_body("深夜营业")).status_code == 502


def test_dialogue_rejects_invalid_dish_keywords(monkeypatch):
    mock_model(
        monkeypatch,
        success_result(extractedConstraints={"dishKeywords": "水煮鱼"}),
    )
    assert post_extract(request_body("想吃水煮鱼")).status_code == 502

    mock_model(
        monkeypatch,
        success_result(
            extractedConstraints={"dishKeywords": ["超" * 31]}
        ),
    )
    assert post_extract(request_body("想吃菜")).status_code == 502


def test_dialogue_detects_constraint_update(monkeypatch):
    mock_model(
        monkeypatch,
        success_result(
            intent="CONSTRAINT_UPDATE",
            extractedConstraints={"perCapitaBudget": 100},
        ),
    )

    body = post_extract(request_body("预算改成每人一百")).json()

    assert body["intent"] == "CONSTRAINT_UPDATE"
    assert body["extractedConstraints"]["perCapitaBudget"] == 100


def test_dialogue_detects_cleared_constraints(monkeypatch):
    mock_model(
        monkeypatch,
        success_result(
            intent="CONSTRAINT_UPDATE",
            extractedConstraints={},
            clearedFields=["environmentRequirements"],
        ),
    )

    body = post_extract(request_body("环境不用安静")).json()

    assert body["clearedFields"] == ["environmentRequirements"]


def test_dialogue_uses_request_runtime_model(
    monkeypatch,
):
    captured = mock_model(
        monkeypatch,
        success_result(),
    )

    response = post_extract(
        request_body()
    )

    assert response.status_code == 200

    assert captured["init"] == [
        {
            "api_key": "plain-runtime-secret",
            "base_url": (
                "https://model.example.com/v1"
            ),
            "model": "mock-model",
            "provider": "OPENAI_COMPATIBLE",
            "request_timeout_seconds": 12.345,
        }
    ]

    assert captured["chat"][0]["temperature"] == 0.25
    assert captured["chat"][0]["max_tokens"] == 1500

    assert (
        "plain-runtime-secret"
        not in response.text
    )


def test_dialogue_requires_runtime_model():
    body = request_body()
    body.pop("runtimeModel")

    response = post_extract(body)

    assert response.status_code == 422
    assert (
        response.json()["error"]["code"]
        == "INVALID_REQUEST"
    )

def test_dialogue_does_not_invent_missing_fields(monkeypatch):
    mock_model(
        monkeypatch,
        success_result(extractedConstraints={"partySize": 4}),
    )

    body = post_extract(request_body("四个人")).json()

    assert "cuisines" in body["extractedConstraints"]
    assert body["extractedConstraints"]["cuisines"] == []


def test_dialogue_rejects_invalid_model_json(
    monkeypatch,
):
    class InvalidJsonLLMService:
        def __init__(self, **kwargs):
            self.model = kwargs.get("model")
            self.provider = kwargs.get("provider")

        def is_configured(self) -> bool:
            return True

        async def chat_json(self, *args, **kwargs):
            raise ValueError("not json")

    monkeypatch.setattr(
        service_module,
        "LLMService",
        InvalidJsonLLMService,
    )

    response = post_extract(
        request_body()
    )

    assert response.status_code == 502


def test_dialogue_rejects_unknown_model_fields(monkeypatch):
    mock_model(
        monkeypatch,
        success_result(extractedConstraints={"budget": 80}),
    )

    response = post_extract(request_body())

    assert response.status_code == 502


def test_dialogue_model_not_configured_returns_error(monkeypatch):
    mock_model(monkeypatch, success_result(), configured=False)

    response = post_extract(request_body())

    assert response.status_code == 503


def test_dialogue_response_does_not_include_merchant_id(monkeypatch):
    mock_model(monkeypatch, success_result())

    body_text = post_extract(request_body()).text

    assert "merchantId" not in body_text


def test_dialogue_response_does_not_include_merchant_name(monkeypatch):
    mock_model(monkeypatch, success_result())

    body_text = post_extract(request_body()).text

    assert "merchantName" not in body_text


def test_normalize_model_result_converts_constraints_alias() -> None:
    from app.services.dialogue_extraction_service import normalize_model_result

    result = normalize_model_result(
        {
            "intent": "MERCHANT_RECOMMENDATION",
            "constraints": {
                "partySize": 4,
                "perCapitaBudget": 80,
                "cuisines": ["川菜"],
                "distanceKm": 3,
            },
            "clearedFields": [],
            "confidence": 0.95,
        }
    )

    assert "constraints" not in result
    assert result["extractedConstraints"] == {
        "partySize": 4,
        "perCapitaBudget": 80,
        "cuisines": ["川菜"],
        "distanceKm": 3,
    }
