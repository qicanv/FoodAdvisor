"""
评价分析服务 — 情感分析、关键词提取、方面识别、差评归因（V0.4）

支持三种分析模式（通过 SENTIMENT_ANALYSIS_MODE 环境变量配置）:
- local:  仅用本地微调模型（MultiHeadSentimentClassifier），快速、离线、维度精准
- llm:    仅用 DeepSeek LLM（向后兼容，支持关键词/标签/归因）
- hybrid: 本地模型做维度情感，LLM 补充关键词/标签/归因
"""
import logging
import uuid
from app.core.trace_context import current_trace_id
from datetime import datetime, timezone
from typing import List, Optional
from app.models.schemas import (
    AnalyzeRequest, AnalyzeResponse, AspectResult,
    SentimentEnum, AspectCategoryEnum, IssueCategoryResult
)
from app.services.llm_service import llm_service
from app.core.config import settings
from app.models.schemas import TagResult

SENTIMENT_LOW_CONFIDENCE_THRESHOLD = settings.sentiment_low_confidence_threshold

# 差评归因类别映射
ISSUE_CATEGORY_NAMES = {
    "HYGIENE": "卫生问题",
    "SERVICE_ATTITUDE": "服务态度",
    "SERVING_SPEED": "上菜速度",
    "TASTE": "菜品口味",
    "PRICE": "价格问题",
    "PORTION": "分量问题",
    "QUEUE": "排队时间",
    "ENVIRONMENT": "环境问题",
    "OTHER": "其他问题",
}

logger = logging.getLogger(__name__)

# ============================================
# 情感分析提示词（V0.3 — 增加差评归因结构化输出）
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
5. **差评归因**：如果整体情感为 NEGATIVE 或包含负面内容，列出具体问题类别
   - 可选值：HYGIENE / SERVICE_ATTITUDE / SERVING_SPEED / TASTE / PRICE / PORTION / QUEUE / ENVIRONMENT / OTHER
   - 每个类别需要包含置信度和原文依据
6. **标签提取**：从评论中识别是否提到以下预定义标签，每条评论可以关联多个标签
   - 你只能从以下标签列表中选择，不能自己编造标签编码：

   【口味类 TASTE】
   TASTE_GOOD（口味好）、TASTE_BAD（口味差）

   【环境类 ENVIRONMENT】
   ENVIRONMENT_GOOD（环境好）、ENVIRONMENT_BAD（环境差）

   【服务类 SERVICE】
   SERVICE_GOOD（服务好）、SERVICE_BAD（服务差）

   【价格类 PRICE】
   PRICE_LOW（价格实惠）、PRICE_HIGH（价格偏高）

   【排队类 QUEUE_TIME】
   QUEUE_LONG（排队久）、QUEUE_SHORT（排队快）

   【分量类 PORTION】
   PORTION_LARGE（分量足）、PORTION_SMALL（分量少）

   【卫生类 HYGIENE】
   HYGIENE_GOOD（卫生好）、HYGIENE_BAD（卫生差）

   【速度类 SPEED】
   SPEED_FAST（上菜快）、SPEED_SLOW（上菜慢）

   【停车类 PARKING】
   PARKING_GOOD（停车方便）、PARKING_BAD（停车难）

   - 每个标签需要给出情感倾向（POSITIVE/NEGATIVE/NEUTRAL）、置信度和原文依据
   - 只提取评论中明确提到的，不要猜测或编造

## 输出格式（严格 JSON）
{
  "sentiment": "NEGATIVE",
  "confidence": 0.92,
  "keywords": ["上菜慢", "服务差", "口味一般"],
  "aspects": [
    {"category": "SPEED", "sentiment": "NEGATIVE", "text": "等了半个多小时才上菜"},
    {"category": "SERVICE", "sentiment": "NEGATIVE", "text": "叫了好几次都没人理"}
  ],
  "negativeReason": "SERVING_SPEED",
  "issueCategories": [
    {"category": "SERVING_SPEED", "confidence": 0.95, "evidenceText": "等了半个多小时才上来第一个菜"},
    {"category": "SERVICE_ATTITUDE", "confidence": 0.88, "evidenceText": "服务员态度冷漠，叫了好几次都没人理"}
  ],
  "tags": [
    {"tagCode": "SPEED_SLOW", "tagName": "上菜慢", "category": "SPEED", "sentiment": "NEGATIVE", "confidence": 0.95, "evidenceText": "等了半个多小时才上菜"},
    {"tagCode": "SERVICE_BAD", "tagName": "服务差", "category": "SERVICE", "sentiment": "NEGATIVE", "confidence": 0.88, "evidenceText": "服务员态度冷漠"}
  ]
}

注意：
- keywords 只提取评论中实际提到的特征词，不要编造
- aspects 只分析评论中实际提到的维度
- 每个 aspect 的 text 应尽可能引用原评论中的表述
- negativeReason 只在有负面内容时填写，正面评论填 null
- issueCategories 只在有负面内容时填写，正面评论填空数组 []
- tags 只从上述预定义标签列表中选择 tagCode，不能自己编造
- 评论中没有提到的维度不要强行贴标签，空数组 [] 是合法的
"""


class ReviewAnalysisService:
    """评价分析服务（V0.3）"""

    def _generate_trace_id(self) -> str:
        """生成 AI 调用追踪ID"""
        return current_trace_id()

    async def analyze(self, request: AnalyzeRequest, analysis_version: int = 1) -> AnalyzeResponse:
        """
        分析单条评价 — 根据 SENTIMENT_ANALYSIS_MODE 选择分析策略
        """
        mode = settings.sentiment_analysis_mode
        if mode == "local":
            return self._analyze_with_local_model(request, analysis_version)
        elif mode == "hybrid":
            return await self._analyze_hybrid(request, analysis_version)
        else:
            return await self._analyze_with_llm(request, analysis_version)

    # ============================================
    # 本地模型分析
    # ============================================

    def _analyze_with_local_model(self, request: AnalyzeRequest, analysis_version: int = 1) -> AnalyzeResponse:
        """使用本地微调模型进行情感分析"""
        from app.services.ml_sentiment_service import ml_sentiment_service

        try:
            ml_result = ml_sentiment_service.predict(request.content)
            return ml_sentiment_service.map_to_analyze_response(request, ml_result, analysis_version)
        except Exception as e:
            logger.error(f"本地模型分析失败 reviewId={request.reviewId}: {e}")
            from app.services.ml_sentiment_service import ml_sentiment_service
            return ml_sentiment_service.degrade_response(request, str(e))

    # ============================================
    # 混合分析：本地模型 + LLM
    # ============================================

    async def _analyze_hybrid(self, request: AnalyzeRequest, analysis_version: int = 1) -> AnalyzeResponse:
        """本地模型做维度情感，LLM 补充关键词/标签/归因"""
        from app.services.ml_sentiment_service import ml_sentiment_service

        trace_id = self._generate_trace_id()

        # Step 1: 本地模型做维度情感分类
        try:
            ml_result = ml_sentiment_service.predict(request.content)
            base_response = ml_sentiment_service.map_to_analyze_response(request, ml_result, analysis_version)
        except Exception as e:
            logger.warning(f"混合模式：本地模型失败，降级到 LLM。原因: {e}")
            return await self._analyze_with_llm(request, analysis_version)

        # Step 2: LLM 补充关键词/标签/归因
        try:
            llm_result = await llm_service.chat_json(
                system_prompt=SENTIMENT_ANALYSIS_PROMPT,
                user_message=f"请分析以下用户评论：\n\n{request.content}",
                temperature=0.1,
                max_tokens=3000,
            )
            # 合并 LLM 的 keywords/tags/issueCategories 到本地模型的结果中
            base_response.keywords = llm_result.get("keywords", [])[:10]
            base_response.tags = self._parse_tags(llm_result.get("tags", []))
            base_response.issueCategories = self._parse_issue_categories(
                llm_result.get("issueCategories", [])
            )
            base_response.negativeReason = llm_result.get("negativeReason")
            base_response.modelName = f"hybrid:{ml_sentiment_service.model_name}+{llm_service.model}"
        except Exception as e:
            logger.warning(f"混合模式：LLM 补充分析失败，仅返回本地模型结果。原因: {e}")
            # LLM 失败时，本地模型结果仍然有效
            base_response.modelName = f"hybrid-fallback:{ml_sentiment_service.model_name}"

        base_response.businessTraceId = trace_id
        return base_response

    # ============================================
    # LLM 分析（原有逻辑）
    # ============================================

    async def _analyze_with_llm(self, request: AnalyzeRequest, analysis_version: int = 1) -> AnalyzeResponse:
        """使用 DeepSeek LLM 进行情感分析（现有实现）"""
        trace_id = self._generate_trace_id()
        started_at = datetime.now(timezone.utc)

        try:
            result = await llm_service.chat_json(
                system_prompt=SENTIMENT_ANALYSIS_PROMPT,
                user_message=f"请分析以下用户评论：\n\n{request.content}",
                temperature=0.1,
                max_tokens=3000
            )

            confidence = self._clamp_confidence(result.get("confidence", 0.5))
            completed_at = datetime.now(timezone.utc)

            return AnalyzeResponse(
                reviewId=request.reviewId,
                merchantId=request.merchantId,
                reviewVersion=request.reviewVersion,
                analysisVersion=analysis_version,
                sentiment=self._validate_sentiment(result.get("sentiment", "NEUTRAL")),
                confidence=confidence,
                lowConfidence=confidence < SENTIMENT_LOW_CONFIDENCE_THRESHOLD,
                keywords=result.get("keywords", [])[:10],
                aspects=self._parse_aspects(result.get("aspects", [])),
                tags=self._parse_tags(result.get("tags", [])),
                issueCategories=self._parse_issue_categories(result.get("issueCategories", [])),
                negativeReason=result.get("negativeReason"),
                modelName=llm_service.model,
                modelVersion=request.modelVersion,
                businessTraceId=trace_id,
                status="SUCCESS",
                errorMessage=None
            )

        except Exception as e:
            logger.error(f"评论分析失败 reviewId={request.reviewId}: {e}")
            completed_at = datetime.now(timezone.utc)
            # 返回降级结果
            return AnalyzeResponse(
                reviewId=request.reviewId,
                merchantId=request.merchantId,
                reviewVersion=request.reviewVersion,
                analysisVersion=analysis_version,
                sentiment=SentimentEnum.NEUTRAL,
                confidence=0.0,
                lowConfidence=True,
                keywords=[],
                aspects=[],
                tags=[],
                issueCategories=[],
                negativeReason=None,
                modelName=f"fallback:{llm_service.model}",
                modelVersion=request.modelVersion,
                businessTraceId=trace_id,
                status="FAILED",
                errorMessage=str(e)[:500]
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

    def _parse_issue_categories(self, issues: list) -> List[IssueCategoryResult]:
        """解析差评归因类别（V0.3 新增）"""
        parsed = []
        for item in issues:
            if not isinstance(item, dict):
                continue
            cat_code = item.get("category", "OTHER")
            parsed.append(IssueCategoryResult(
                category=cat_code,
                categoryName=ISSUE_CATEGORY_NAMES.get(cat_code, cat_code),
                confidence=self._clamp_confidence(item.get("confidence", 0.5)),
                evidenceText=item.get("evidenceText")
            ))
        return parsed

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
