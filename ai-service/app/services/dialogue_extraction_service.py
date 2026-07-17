import json

from fastapi import HTTPException, status
from pydantic import ValidationError

from app.schemas.dialogue import (
    DialogueExtractRequest,
    DialogueExtractResponse,
)
from app.services.llm_service import llm_service


SYSTEM_PROMPT = """
You extract dining dialogue intent and consumer constraints for FoodAdvisor.
Return strict JSON only.

Allowed intents:
MERCHANT_RECOMMENDATION, CONSTRAINT_UPDATE, GENERAL_CHAT, UNKNOWN.

Allowed constraint fields:
partySize, totalBudget, perCapitaBudget, merchantTypes, cuisines,
tastePreferences, tasteRestrictions, excludedCuisines,
excludedMerchantTypes, distanceKm, minRating, scenes,
environmentRequirements, businessTime.

Rules:
- Do not recommend merchants.
- Do not output merchantId or merchant names.
- Do not invent constraints the user did not express.
- Missing fields must be null or omitted.
- Put explicitly removed conditions into clearedFields.
- Only use allowed fields.
- Do not output latitude or longitude.
"""


class DialogueExtractionService:
    async def extract(
        self,
        request: DialogueExtractRequest,
    ) -> DialogueExtractResponse:
        if not llm_service.is_configured():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="LLM model is not configured",
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
            result = await llm_service.chat_json(
                SYSTEM_PROMPT,
                json.dumps(payload, ensure_ascii=False),
                temperature=0.1,
                max_tokens=1200,
            )
            return DialogueExtractResponse.model_validate(result)
        except (ValueError, ValidationError) as exception:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Invalid dialogue extraction result: {exception}",
            ) from exception
        except HTTPException:
            raise
        except Exception as exception:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Dialogue extraction model call failed",
            ) from exception


dialogue_extraction_service = DialogueExtractionService()
