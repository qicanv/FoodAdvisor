import logging

from fastapi import HTTPException, status
from pydantic import ValidationError

from app.chains.dining_reply_chain import DiningReplyChain
from app.schemas.dialogue import RuntimeModelConfigModel
from app.schemas.dining_reply import DiningReply, DiningReplyRequest
from app.services.langchain_model_factory import create_runtime_chat_model

logger = logging.getLogger(__name__)

class DiningReplyService:
    async def generate(self, request: DiningReplyRequest) -> DiningReply:
        runtime_model = RuntimeModelConfigModel.model_validate(
            request.runtimeModel
        )
        try:
            model = create_runtime_chat_model(runtime_model)
            reply = await DiningReplyChain(
                model,
                system_prompt=request.systemPrompt,
            ).ainvoke(
                request.model_dump(
                    exclude={"runtimeModel", "systemPrompt", "promptVersion"}
                )
            )
            _validate_references(request, reply)
            reply.replyGenerator = "AI_MODEL"
            reply.degraded = False
            reply.modelName = runtime_model.modelName
            reply.promptVersion = (
                request.promptVersion or "dining-reply:v1"
            )
            return reply
        except (ValueError, ValidationError) as exception:
            logger.exception(
                "Invalid dining reply result: %s",
                exception,
            )
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Invalid dining reply result: {exception}",
            ) from exception

        except HTTPException:
            raise

        except Exception as exception:
            logger.exception(
                "Dining reply model call failed: %s",
                exception,
            )
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Dining reply model call failed",
            ) from exception


dining_reply_service = DiningReplyService()


def _validate_references(
    request: DiningReplyRequest,
    reply: DiningReply,
) -> None:
    candidates = {item.merchantId: item for item in request.candidates}
    allowed_fact_ids = {
        fact_id
        for item in request.candidates
        for fact_id in (*item.facts.keys(), *item.riskFacts.keys())
    }
    if request.mode == "NO_MATCH":
        allowed_fact_ids.update(item.factId for item in request.gapFacts)
    allowed_evidence_ids = {
        evidence_id
        for item in request.candidates
        for evidence_id in item.evidenceIds
    }
    for reason in reply.merchantReasons:
        candidate = candidates.get(reason.merchantId)
        if candidate is None:
            raise ValueError("reply referenced an unknown merchantId")
        merchant_facts = set(candidate.facts) | set(candidate.riskFacts)
        if not set(reason.factIds).issubset(merchant_facts):
            raise ValueError("reply referenced a factId from another merchant")
        if not set(reason.riskFactIds).issubset(set(candidate.riskFacts)):
            raise ValueError("reply referenced an invalid riskFactId")
        if not set(reason.evidenceIds).issubset(set(candidate.evidenceIds)):
            raise ValueError("reply referenced an invalid evidenceId")
    if not set(reply.usedFactIds).issubset(allowed_fact_ids):
        raise ValueError("reply referenced an unknown factId")
    if not set(reply.usedEvidenceIds).issubset(allowed_evidence_ids):
        raise ValueError("reply referenced an unknown evidenceId")
