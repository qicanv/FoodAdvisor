from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable, RunnableLambda, RunnableSequence

from app.schemas.constraint_patch import ConstraintPatch


_DEFAULT_PROMPT = (
    Path(__file__).parents[1] / "prompts" / "dining" / "constraint_system.txt"
).read_text(encoding="utf-8")


def _message_text(value: Any) -> str:
    """Extract plain text content from the model response."""

    content = getattr(value, "content", value)

    if isinstance(content, str):
        return content

    return json.dumps(content, ensure_ascii=False)


class DiningConstraintChain:
    """A deterministic prompt → model → Pydantic parser chain with one repair."""

    def __init__(
        self,
        model: Runnable,
        system_prompt: str | None = None,
    ):
        self.parser = PydanticOutputParser(
            pydantic_object=ConstraintPatch,
        )

        # The database prompt may contain JSON examples with many `{}` characters.
        # Pass it as a template variable so LangChain does not parse those braces
        # as f-string replacement fields.
        effective_system_prompt = system_prompt or _DEFAULT_PROMPT

        prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    "{system_prompt}\n\n{format_instructions}",
                ),
                (
                    "human",
                    "Current trusted state:\n{current_state}\n"
                    "Recent messages:\n{recent_messages}\n"
                    "Rejected fields:\n{rejected_fields}\n"
                    "Pending conflicts:\n{pending_conflicts}\n"
                    "Timezone: {timezone}\n"
                    "Current user message:\n{content}",
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

        repair_prompt = ChatPromptTemplate.from_messages(
            [
                (
                    "system",
                    "{system_prompt}\n\n"
                    "Repair the invalid output once. "
                    "Return only valid JSON matching the required schema.\n\n"
                    "{format_instructions}",
                ),
                (
                    "human",
                    "Original input:\n{original_input}\n"
                    "Invalid output:\n{invalid_output}\n"
                    "Validation error:\n{validation_error}",
                ),
            ]
        ).partial(
            system_prompt=effective_system_prompt,
            format_instructions=self.parser.get_format_instructions(),
        )

        self.repair_sequence: RunnableSequence = (
            repair_prompt
            | model
            | RunnableLambda(_message_text)
        )

    async def ainvoke(
        self,
        payload: dict[str, Any],
    ) -> ConstraintPatch:
        variables = {
            "current_state": json.dumps(
                payload.get("currentConstraints", {}),
                ensure_ascii=False,
            ),
            "recent_messages": json.dumps(
                payload.get("recentMessages", []),
                ensure_ascii=False,
            ),
            "rejected_fields": json.dumps(
                payload.get("rejectedFields", []),
                ensure_ascii=False,
            ),
            "pending_conflicts": json.dumps(
                payload.get("pendingConflicts", []),
                ensure_ascii=False,
            ),
            "timezone": payload.get(
                "timezone",
                "Asia/Shanghai",
            ),
            "content": payload.get(
                "content",
                "",
            ),
        }

        raw = await self.sequence.ainvoke(variables)

        try:
            return self.parser.parse(raw)
        except Exception as first_error:
            repaired = await self.repair_sequence.ainvoke(
                {
                    "original_input": json.dumps(
                        variables,
                        ensure_ascii=False,
                    ),
                    "invalid_output": raw,
                    "validation_error": str(first_error),
                }
            )

            return self.parser.parse(repaired)