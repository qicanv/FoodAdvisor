from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable, RunnableLambda, RunnableSequence

from app.schemas.dining_reply import DiningReply


logger = logging.getLogger(__name__)


_DEFAULT_PROMPT = (
    Path(__file__).parents[1] / "prompts" / "dining" / "reply_system.txt"
).read_text(encoding="utf-8")


def _message_text(value: Any) -> str:
    """Extract text and log metadata when the model returns no usable content."""

    content = getattr(value, "content", value)

    if isinstance(content, str):
        if not content.strip():
            logger.warning(
                "Dining reply model returned empty content. "
                "message_type=%s, "
                "additional_kwargs=%r, "
                "response_metadata=%r, "
                "usage_metadata=%r",
                type(value).__name__,
                getattr(value, "additional_kwargs", None),
                getattr(value, "response_metadata", None),
                getattr(value, "usage_metadata", None),
            )

        return content

    logger.warning(
        "Dining reply model returned non-string content. "
        "message_type=%s, "
        "content=%r, "
        "additional_kwargs=%r, "
        "response_metadata=%r, "
        "usage_metadata=%r",
        type(value).__name__,
        content,
        getattr(value, "additional_kwargs", None),
        getattr(value, "response_metadata", None),
        getattr(value, "usage_metadata", None),
    )

    return json.dumps(content, ensure_ascii=False)


class DiningReplyChain:
    """Generate language from trusted facts without allowing tool autonomy."""

    def __init__(
        self,
        model: Runnable,
        system_prompt: str | None = None,
    ):
        self.parser = PydanticOutputParser(
            pydantic_object=DiningReply,
        )

        # Pass the dynamic prompt as a variable so JSON braces contained in
        # database prompts are not interpreted as LangChain template fields.
        effective_system_prompt = system_prompt or _DEFAULT_PROMPT

        prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    "{system_prompt}\n\n{format_instructions}",
                ),
                (
                    "human",
                    "Trusted reply input:\n{reply_input}",
                ),
            ]
        ).partial(
            system_prompt=effective_system_prompt,
            format_instructions=self.parser.get_format_instructions(),
        )

        self.sequence: RunnableSequence = (
            prompt
            | model
            | RunnableLambda(_message_text)
        )

    async def ainvoke(
        self,
        payload: dict[str, Any],
    ) -> DiningReply:
        raw = await self.sequence.ainvoke(
            {
                "reply_input": json.dumps(
                    payload,
                    ensure_ascii=False,
                )
            }
        )

        return self.parser.parse(raw)