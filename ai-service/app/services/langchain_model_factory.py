from __future__ import annotations

from typing import Any

from langchain_openai import ChatOpenAI

from app.schemas.dialogue import RuntimeModelConfigModel


def create_runtime_chat_model(
    runtime_model: RuntimeModelConfigModel,
) -> ChatOpenAI:
    """Create one request-scoped OpenAI-compatible LangChain chat model."""

    model_options: dict[str, Any] = {
        "api_key": runtime_model.apiKey.get_secret_value(),
        "base_url": runtime_model.baseUrl,
        "model": runtime_model.modelName,
        "timeout": runtime_model.timeoutMs / 1000.0,
        "temperature": runtime_model.temperature,
        "max_tokens": runtime_model.maxOutputTokens,
        "max_retries": 0,
    }

    normalized_base_url = runtime_model.baseUrl.lower()
    normalized_model_name = runtime_model.modelName.lower()

    # Dining constraint extraction and reply generation require concise,
    # structured JSON. Disable Qwen thinking mode to prevent reasoning tokens
    # from consuming the complete output-token budget.
    if (
        "dashscope.aliyuncs.com" in normalized_base_url
        and normalized_model_name.startswith("qwen")
    ):
        model_options["extra_body"] = {
            "enable_thinking": False,
        }

    return ChatOpenAI(**model_options)