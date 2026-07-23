import json
import logging
from typing import Any

from fastapi import HTTPException, status
from pydantic import ValidationError
from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda

from app.schemas.dialogue import (
    DialogueExtractRequest,
    DialogueExtractResponse,
)
from app.chains.dining_constraint_chain import DiningConstraintChain
from app.schemas.constraint_patch import ConstraintPatch
from app.services.langchain_model_factory import create_runtime_chat_model

logger = logging.getLogger(__name__)

# Compatibility seam for existing tests and legacy embedders. Production keeps
# this as None and always uses the official LangChain provider.
LLMService = None


SYSTEM_PROMPT = """
You are a dining constraint extraction system for FoodAdvisor.
Return strict JSON only. Do not use Markdown, code blocks, or extra text.

The JSON must use this exact top-level structure:
{
  "intent": "MERCHANT_RECOMMENDATION",
  "extractedConstraints": {},
  "clearedFields": [],
  "confidence": 0.0
}

Allowed top-level fields:
intent, extractedConstraints, clearedFields, confidence.

Allowed intents:
MERCHANT_RECOMMENDATION, CONSTRAINT_UPDATE, GENERAL_CHAT, UNKNOWN.

Allowed constraint fields:
partySize, totalBudget, perCapitaBudget, merchantTypes, cuisines,
tastePreferences, tasteRestrictions, dishKeywords, excludedCuisines,
excludedMerchantTypes, distanceKm, minRating, scenes,
environmentRequirements, businessTime, businessTargetTime,
businessTargetNextDay.

Rules:
1. Always use the top-level field extractedConstraints.
2. Never use constraints as a top-level field.
3. intent must be one of the four allowed values.
4. Only extract conditions explicitly expressed by the user.
5. Missing constraint fields must be null, empty arrays, or omitted.
6. Put conditions the user explicitly removes or resets into clearedFields.
7. Only use allowed top-level fields and allowed constraint fields.
8. Do not recommend merchants.
9. Do not output merchantId, merchantName, latitude, or longitude.
10. Put explicit dishes or main ingredients in dishKeywords.
11. Never put negated foods such as "不吃香菜" in dishKeywords.
12. Do not treat cuisines, budgets, distances, or party size as dishes.
13. confidence must be a number between 0 and 1.

Examples:
- "两个人100元吃火锅"
  means partySize=2, totalBudget=100, and a hot-pot dining preference.
- "别太远，5公里以内吧"
  means distanceKm=5.
- "不要火锅"
  means excludedCuisines=["火锅"], or clearedFields=["cuisines"]
  when the user is explicitly removing a previously selected cuisine.
- "四个人，人均八十元，想吃川菜，距离三公里以内"
  means partySize=4, perCapitaBudget=80, cuisines=["川菜"],
  and distanceKm=3.
"""


def normalize_model_result(result: Any) -> dict[str, Any]:
    """
    Normalize known model-output aliases before strict Pydantic validation.

    The official response field is extractedConstraints. Some models may
    return constraints despite the prompt, so it is converted here.
    """
    if not isinstance(result, dict):
        raise ValueError(
            "Dialogue extraction result must be a JSON object, "
            f"but received {type(result).__name__}"
        )

    normalized = dict(result)

    if "constraints" in normalized:
        if (
            "extractedConstraints" not in normalized
            or normalized["extractedConstraints"] is None
        ):
            normalized["extractedConstraints"] = normalized["constraints"]

        # Remove the non-standard alias so strict extra-field validation
        # can continue to reject unrelated unexpected fields.
        normalized.pop("constraints", None)

    return normalized


class DialogueExtractionService:
    async def extract(
        self,
        request: DialogueExtractRequest,
    ) -> DialogueExtractResponse:
        system_prompt = (
            request.systemPrompt
            if request.systemPrompt and request.systemPrompt.strip()
            else None
        )
        prompt_version = (
            request.promptVersion.strip()
            if request.promptVersion and request.promptVersion.strip()
            else "constraint-extraction:v2"
        )
        runtime_model = request.runtimeModel

        payload = {
            "sessionId": request.sessionId,
            "messageId": request.messageId,
            "content": request.content,
            "currentConstraints": request.currentConstraints.model_dump(
                exclude_none=True
            ),
            "recentMessages": request.recentMessages,
            "rejectedFields": request.rejectedFields,
            "pendingConflicts": request.pendingConflicts,
            "timezone": request.timezone,
        }

        try:
            model = _model_for_request(runtime_model, system_prompt)
            patch = await DiningConstraintChain(
                model=model,
                system_prompt=system_prompt,
            ).ainvoke(payload)
            legacy_constraints, cleared_fields = _legacy_projection(patch)
            response = DialogueExtractResponse(
                intent=patch.intent,
                extractedConstraints=legacy_constraints,
                clearedFields=cleared_fields,
                confidence=_overall_confidence(patch),
                patch=patch,
            )

            # These fields are controlled by the service rather than trusted
            # from model-generated output.
            response.extractor = "AI_MODEL"
            response.degraded = False
            response.modelName = runtime_model.modelName
            response.provider = runtime_model.provider
            response.promptVersion = prompt_version

            return response

        except (ValueError, ValidationError) as exception:
            logger.exception(
                "Invalid dialogue extraction result: %s",
                exception,
            )
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=(
                    "Invalid dialogue extraction result: "
                    f"{exception}"
                ),
            ) from exception

        except HTTPException:
            raise

        except Exception as exception:
            logger.exception(
                "Dialogue extraction model call failed: %s",
                exception,
            )
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Dialogue extraction model call failed",
            ) from exception


dialogue_extraction_service = DialogueExtractionService()


def _overall_confidence(patch: ConstraintPatch) -> float:
    if not patch.confidence:
        return 0.0
    return sum(patch.confidence.values()) / len(patch.confidence)


def _legacy_projection(patch: ConstraintPatch) -> tuple[dict[str, Any], list[str]]:
    """Keep old Java clients functional while ConstraintPatch rolls out."""
    operations = patch.operations
    extracted: dict[str, Any] = dict(operations.set_values)
    for field, values in operations.add.items():
        extracted[field] = list(values)
    for field, values in operations.exclude.items():
        target = {
            "cuisines": "excludedCuisines",
            "merchantTypes": "excludedMerchantTypes",
        }.get(field, field)
        extracted[target] = list(values)
    return extracted, list(operations.clear)


def _model_for_request(runtime_model, system_prompt: str | None = None):
    if LLMService is None:
        return create_runtime_chat_model(runtime_model)

    legacy = LLMService(
        api_key=runtime_model.apiKey.get_secret_value(),
        base_url=runtime_model.baseUrl,
        model=runtime_model.modelName,
        provider=runtime_model.provider,
        request_timeout_seconds=runtime_model.timeoutMs / 1000.0,
    )
    if not legacy.is_configured():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Dialogue extraction model is not configured",
        )

    async def invoke(_prompt):
        result = await legacy.chat_json(
            system_prompt=system_prompt,
            temperature=runtime_model.temperature,
            max_tokens=runtime_model.maxOutputTokens,
        )
        return AIMessage(content=json.dumps(
            _legacy_result_to_patch(result), ensure_ascii=False
        ))

    return RunnableLambda(invoke)


def _legacy_result_to_patch(result: Any) -> dict[str, Any]:
    result = normalize_model_result(result)
    constraints = result.get("extractedConstraints", {})
    if not isinstance(constraints, dict):
        raise ValueError("extractedConstraints must be an object")
    operations: dict[str, Any] = {
        "set": {},
        "add": {},
        "remove": {},
        "clear": result.get("clearedFields", []),
        "exclude": {},
        "unexclude": {},
    }
    list_fields = {
        "merchantTypes", "cuisines", "tastePreferences",
        "tasteRestrictions", "dishKeywords", "scenes",
        "environmentRequirements",
    }
    for field, value in constraints.items():
        if field == "excludedCuisines":
            operations["exclude"]["cuisines"] = value
        elif field == "excludedMerchantTypes":
            operations["exclude"]["merchantTypes"] = value
        elif field in list_fields:
            operations["add"][field] = value
        else:
            operations["set"][field] = value
    confidence = result.get("confidence", 0.0)
    return {
        "intent": result.get("intent", "UNKNOWN"),
        "directRecommend": False,
        "operations": operations,
        "conflicts": [],
        "followUpHints": [],
        "confidence": (
            {next(iter(constraints)): confidence}
            if constraints and isinstance(confidence, (int, float))
            else confidence if isinstance(confidence, dict) else {}
        ),
    }
