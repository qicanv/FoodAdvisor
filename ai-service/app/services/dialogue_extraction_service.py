import json
from typing import Any

from fastapi import HTTPException, status
from pydantic import ValidationError

from app.schemas.dialogue import (
    DialogueExtractRequest,
    DialogueExtractResponse,
)
from app.services.llm_service import LLMService


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
            else SYSTEM_PROMPT
        )
        prompt_version = (
            request.promptVersion.strip()
            if request.promptVersion and request.promptVersion.strip()
            else "dialogue-extraction:v1"
        )
        runtime_model = request.runtimeModel

        model_client = LLMService(
            api_key=runtime_model.apiKey.get_secret_value(),
            base_url=runtime_model.baseUrl,
            model=runtime_model.modelName,
            provider=runtime_model.provider,
            request_timeout_seconds=(
                runtime_model.timeoutMs / 1000.0
            ),
        )

        if not model_client.is_configured():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Runtime LLM model is not configured",
            )

        payload = {
            "sessionId": request.sessionId,
            "messageId": request.messageId,
            "content": request.content,
            "currentConstraints": request.currentConstraints.model_dump(
                exclude_none=True
            ),
        }

        try:
            result = await model_client.chat_json(
                system_prompt=system_prompt,
                user_message=json.dumps(
                    payload,
                    ensure_ascii=False,
                ),
                temperature=runtime_model.temperature,
                max_tokens=runtime_model.maxOutputTokens,
            )

            normalized_result = normalize_model_result(result)

            response = DialogueExtractResponse.model_validate(
                normalized_result
            )

            # These fields are controlled by the service rather than trusted
            # from model-generated output.
            response.extractor = "AI_MODEL"
            response.degraded = False
            response.modelName = model_client.model
            response.provider = model_client.provider
            response.promptVersion = prompt_version

            return response

        except (ValueError, ValidationError) as exception:
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
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Dialogue extraction model call failed",
            ) from exception


dialogue_extraction_service = DialogueExtractionService()