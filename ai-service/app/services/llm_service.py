"""
大模型调用服务——封装 OpenAI 兼容模型 API 调用。
"""

import json
import re

import httpx

from app.core.config import settings


class LLMService:
    """通用大模型调用服务。"""

    def __init__(self) -> None:
        self.api_key = settings.llm_api_key
        self.base_url = (
            settings.llm_base_url or "https://api.deepseek.com"
        ).rstrip("/")
        self.model = settings.llm_model or "deepseek-v4-pro"
        self.provider = settings.llm_provider

    def is_configured(self) -> bool:
        """检查大模型必要配置是否完整。"""
        return all(
            isinstance(value, str) and bool(value.strip())
            for value in (
                self.api_key,
                self.base_url,
                self.model,
            )
        )

    def _build_chat_completions_url(self) -> str:
        """
        根据配置生成 OpenAI 兼容的 chat completions 地址。

        支持以下配置形式：
        - https://example.com
        - https://example.com/v1
        - https://example.com/v1/chat/completions
        """
        normalized_url = self.base_url.rstrip("/")

        if normalized_url.endswith("/chat/completions"):
            return normalized_url

        if normalized_url.endswith("/v1"):
            return f"{normalized_url}/chat/completions"

        return f"{normalized_url}/v1/chat/completions"

    async def chat(
        self,
        system_prompt: str,
        user_message: str,
        temperature: float = 0.3,
        max_tokens: int = 2000,
    ) -> str:
        """发送对话请求，返回模型的文本响应。"""
        if not self.is_configured():
            raise RuntimeError(
                "LLM 配置不完整，请检查 LLM_API_KEY、"
                "LLM_BASE_URL 和 LLM_MODEL"
            )

        url = self._build_chat_completions_url()

        payload = {
            "model": self.model,
            "messages": [
                {
                    "role": "system",
                    "content": system_prompt,
                },
                {
                    "role": "user",
                    "content": user_message,
                },
            ],
            "temperature": temperature,
            "max_tokens": max_tokens,
        }

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        async with httpx.AsyncClient(
            timeout=float(settings.request_timeout_seconds)
        ) as client:
            response = await client.post(
                url,
                json=payload,
                headers=headers,
            )
            response.raise_for_status()

            data = response.json()

            try:
                return data["choices"][0]["message"]["content"]
            except (KeyError, IndexError, TypeError) as exception:
                raise ValueError(
                    "模型响应缺少 choices[0].message.content"
                ) from exception

    async def chat_json(
        self,
        system_prompt: str,
        user_message: str,
        temperature: float = 0.1,
        max_tokens: int = 3000,
    ) -> dict:
        """发送对话请求，并将模型响应解析为 JSON 对象。"""
        json_system_prompt = (
            system_prompt
            + "\n\n"
            + "【重要】你必须只返回有效的 JSON，"
            + "不要包含任何 markdown 代码块标记、解释或额外文字。"
            + "直接返回原始 JSON。"
        )

        text = await self.chat(
            json_system_prompt,
            user_message,
            temperature,
            max_tokens,
        )
        text = text.strip()

        # 移除模型可能返回的 Markdown 代码块标记。
        if text.startswith("```"):
            lines = text.splitlines()

            if lines and lines[0].strip().startswith("```"):
                lines = lines[1:]

            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]

            text = "\n".join(lines).strip()

        try:
            result = json.loads(text)
        except json.JSONDecodeError as original_exception:
            # 兼容模型在 JSON 前后附带少量非 JSON 文本的情况。
            match = re.search(r"\{.*\}", text, re.DOTALL)

            if match is None:
                raise ValueError(
                    f"模型返回不是有效 JSON: {text[:500]}"
                ) from original_exception

            try:
                result = json.loads(match.group(0))
            except json.JSONDecodeError as extracted_exception:
                raise ValueError(
                    f"模型返回不是有效 JSON: {text[:500]}"
                ) from extracted_exception

        if not isinstance(result, dict):
            raise ValueError(
                "模型返回的 JSON 顶层结构必须是对象"
            )

        return result


llm_service = LLMService()