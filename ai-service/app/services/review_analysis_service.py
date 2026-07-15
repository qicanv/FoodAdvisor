"""
评价分析服务 — 情感分析、关键词提取、方面识别、差评归因
"""
import logging
from typing import List, Optional
from app.models.schemas import (
    AnalyzeRequest, AnalyzeResponse, AspectResult,
    SentimentEnum, AspectCategoryEnum
)
from app.services.llm_service import llm_service
from app.core.config import settings
from app.models.schemas import TagResult

SENTIMENT_LOW_CONFIDENCE_THRESHOLD = settings.sentiment_low_confidence_threshold

logger = logging.getLogger(__name__)

# ============================================
# 情感分析提示词
# ============================================
SENTIMENT_ANALYSIS_PROMPT = """你是一个餐饮评论分析专家。请分析以下用户评论，并返回一个 JSON 对象。

## 分析任务
1. **整体情感**：判断评论的整体情感倾向 (POSITIVE/NEGATIVE/NEUTRAL/MIXED)
   - POSITIVE: 整体正面，主要表达满意
   - NEGATIVE: 整体负面，主要表达不满
   - NEUTRAL: 客观描述，无明显情感
   - MIXED: 同时包含明显的正面和负面内容
2. **置信度**：给出 0~1 之间的置信度分数
3. **关键词提取**：提取评论中提到的关键特征词（菜品名、口味、环境、服务、价格等，2-8个词）
4. **方面级分析**：按以下维度分别分析情感（如果有提到的话）
   - TASTE: 口味/菜品味道
   - ENVIRONMENT: 用餐环境/装修/氛围
   - SERVICE: 服务态度/服务质量
   - PRICE: 价格/性价比
   - QUEUE_TIME: 排队等待时间
   - HYGIENE: 卫生情况
   - PORTION: 菜品分量
   - SPEED: 上菜速度
   - PARKING: 停车便利性
5. **差评归因**：如果整体情感为 NEGATIVE 或包含负面内容，分析主要原因类别
   (HYGIENE/SERVICE/SPEED/TASTE/PRICE/PORTION/QUEUE_TIME/ENVIRONMENT/OTHER)

## 输出格式（严格 JSON）
{
  "sentiment": "POSITIVE",
  "confidence": 0.95,
  "keywords": ["麻婆豆腐", "麻辣鲜香", "服务热情"],
  "aspects": [
    {"category": "TASTE", "sentiment": "POSITIVE", "text": "麻婆豆腐特别好吃"},
    {"category": "SERVICE", "sentiment": "POSITIVE", "text": "服务态度也很好"}
  ],
  "negativeReason": null
}

注意：
- keywords 只提取评论中实际提到的特征词，不要编造
- aspects 只分析评论中实际提到的维度
- 每个 aspect 的 text 应尽可能引用原评论中的表述
- negativeReason 只在有负面内容时填写，正面评论填 null
"""


class ReviewAnalysisService:
    """评价分析服务"""

    async def analyze(self, request: AnalyzeRequest) -> AnalyzeResponse:
        """
        分析单条评价 — 调用大模型完成情感、关键词、方面、归因
        """
        try:
            result = await llm_service.chat_json(
                system_prompt=SENTIMENT_ANALYSIS_PROMPT,
                user_message=f"请分析以下用户评论：\n\n{request.content}",
                temperature=0.1,
                max_tokens=3000
            )

            confidence = self._clamp_confidence(result.get("confidence", 0.5))

            return AnalyzeResponse(
                reviewId=request.reviewId,
                merchantId=request.merchantId,
                sentiment=self._validate_sentiment(result.get("sentiment", "NEUTRAL")),
                confidence=confidence,
                lowConfidence=confidence < SENTIMENT_LOW_CONFIDENCE_THRESHOLD,
                keywords=result.get("keywords", [])[:10],
                aspects=self._parse_aspects(result.get("aspects", [])),
                tags=self._parse_tags(result.get("tags", [])),
                negativeReason=result.get("negativeReason"),
                modelName=llm_service.model,
                modelVersion=request.modelVersion,
                status="SUCCESS"
            )

        except Exception as e:
            logger.error(f"评论分析失败 reviewId={request.reviewId}: {e}")
            # 返回降级结果
            return AnalyzeResponse(
                reviewId=request.reviewId,
                merchantId=request.merchantId,
                sentiment=SentimentEnum.NEUTRAL,
                confidence=0.0,
                lowConfidence=True,
                keywords=[],
                aspects=[],
                tags=[],
                negativeReason=None,
                modelName=f"fallback:{llm_service.model}",
                modelVersion=request.modelVersion,
                status="FAILED"
            )

    async def batch_analyze(self, requests: List[AnalyzeRequest]) -> tuple:
        """
        批量分析，返回 (成功结果列表, 错误列表)
        """
        results = []
        errors = []
        for req in requests:
            try:
                result = await self.analyze(req)
                results.append(result)
            except Exception as e:
                errors.append({
                    "reviewId": req.reviewId,
                    "error": str(e)
                })
        return results, errors

    def _parse_tags(self, tags: list) -> List[TagResult]:
        parsed = []
        for t in tags:
            if not isinstance(t, dict):
                continue
            parsed.append(TagResult(
                tagCode=t.get("tagCode", ""),
                tagName=t.get("tagName", ""),
                category=t.get("category", "OTHER"),
                sentiment=t.get("sentiment", "NEUTRAL"),
                confidence=self._clamp_confidence(t.get("confidence", 0.5)),
                evidenceText=t.get("evidenceText")
            ))
        return parsed

    def _validate_sentiment(self, sentiment: str) -> str:
        valid = {"POSITIVE", "NEUTRAL", "NEGATIVE", "MIXED"}
        s = str(sentiment).upper().strip()
        return s if s in valid else "NEUTRAL"

    def _clamp_confidence(self, value: float) -> float:
        try:
            return max(0.0, min(1.0, float(value)))
        except (TypeError, ValueError):
            return 0.5

    def _parse_aspects(self, aspects: list) -> List[AspectResult]:
        parsed = []
        for a in aspects:
            if not isinstance(a, dict):
                continue
            parsed.append(AspectResult(
                category=a.get("category", "OTHER"),
                sentiment=a.get("sentiment", "NEUTRAL"),
                text=a.get("text", "")
            ))
        return parsed


# 单例
review_analysis_service = ReviewAnalysisService()
