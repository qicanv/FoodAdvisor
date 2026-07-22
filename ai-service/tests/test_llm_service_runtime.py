from app.services.llm_service import LLMService


def test_runtime_configuration_overrides_settings():
    service = LLMService(
        api_key="runtime-key",
        base_url="https://example.com/v1/",
        model="runtime-model",
        provider="OPENAI_COMPATIBLE",
        request_timeout_seconds=12.5,
    )

    assert service.api_key == "runtime-key"
    assert service.base_url == "https://example.com/v1"
    assert service.model == "runtime-model"
    assert service.provider == "OPENAI_COMPATIBLE"
    assert service.request_timeout_seconds == 12.5
    assert (
        service._build_chat_completions_url()
        == "https://example.com/v1/chat/completions"
    )


def test_runtime_clients_do_not_share_configuration():
    first = LLMService(
        api_key="first-key",
        base_url="https://first.example.com",
        model="first-model",
        provider="FIRST",
        request_timeout_seconds=10,
    )

    second = LLMService(
        api_key="second-key",
        base_url="https://second.example.com",
        model="second-model",
        provider="SECOND",
        request_timeout_seconds=20,
    )

    assert first.api_key == "first-key"
    assert second.api_key == "second-key"
    assert first.model == "first-model"
    assert second.model == "second-model"
    assert first.base_url != second.base_url
    assert (
        first.request_timeout_seconds
        != second.request_timeout_seconds
    )