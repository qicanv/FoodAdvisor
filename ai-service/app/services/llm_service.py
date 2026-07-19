"""
大模型调用服务 — 封装 DeepSeek API 调用
"""
import json
import httpx
from app.core.config import settings


class LLMService:
    """通用大模型调用服务"""

    def __init__(self):
        self.api_key = settings.llm_api_key
        self.base_url = (settings.llm_base_url or "https://api.deepseek.com").rstrip("/")
        self.model = settings.llm_model or "deepseek-v4-pro"
        self.provider = settings.llm_provider

    def is_configured(self) -> bool:
        """检查LLM是否已配置"""
        return bool(self.api_key)

    async def chat(self, system_prompt: str, user_message: str,
                   temperature: float = 0.3, max_tokens: int = 2000) -> str:
        """
        发送对话请求，返回模型文本响应
        """
        if not self.is_configured():
            raise RuntimeError("LLM API Key 未配置，请在 .env 中设置 LLM_API_KEY")

        api_root = (
            self.base_url
            if self.base_url.endswith("/v1")
            else f"{self.base_url}/v1"
        )
        url = f"{api_root}/chat/completions"

        payload = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_message}
            ],
            "temperature": temperature,
            "max_tokens": max_tokens
        }

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        # 推理模型（deepseek-v4-pro）带思维链，长输入下 60 秒不够用
        async with httpx.AsyncClient(
            timeout=float(settings.request_timeout_seconds)
        ) as client:
            response = await client.post(url, json=payload, headers=headers)
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"]

    async def chat_json(self, system_prompt: str, user_message: str,
                        temperature: float = 0.1, max_tokens: int = 3000) -> dict:
        """
        发送对话请求，要求模型返回 JSON，自动解析
        """
        json_system_prompt = (
            system_prompt + "\n\n"
            "【重要】你必须只返回有效的 JSON，不要包含任何 markdown 代码块标记、"
            "解释或额外的文字。直接返回原始 JSON。"
        )

        text = await self.chat(json_system_prompt, user_message, temperature, max_tokens)
        text = text.strip()

        # 移除可能的 markdown 代码块标记
        if text.startswith("```"):
            lines = text.split("\n")
            if lines[0].startswith("```"):
                lines = lines[1:]
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            text = "\n".join(lines)

        try:
            return json.loads(text)
        except json.JSONDecodeError:
            # 尝试提取第一个 JSON 对象
            import re
            match = re.search(r'\{.*\}', text, re.DOTALL)
            if match:
                return json.loads(match.group(0))
            raise ValueError(f"模型返回不是有效 JSON: {text[:500]}")


# 单例
llm_service = LLMService()
