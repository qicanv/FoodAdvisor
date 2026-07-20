"""
商家亮点挖掘服务（EPIC-02 Story 5）

从正面评价中提取顾客经常认可的招牌菜、环境特色、服务特点、
价格优势和品牌特色，按提及次数和好评比例排序。

核心原则：
1. 所有亮点必须来源于真实评价，禁止编造评价中不存在的内容
2. 每条亮点至少关联一条真实正面评论
3. 证据不足时明确返回 INSUFFICIENT_DATA，不强行生成
"""
import logging
import uuid
from app.core.trace_context import current_trace_id
from typing import List, Optional

from app.models.schemas import (
    HighlightGenerateRequest, HighlightGenerateResponse,
    HighlightItem, HighlightEvidence, HighlightReviewItem
)
from app.services.llm_service import llm_service

logger = logging.getLogger(__name__)

# 评价原文片段最长截取长度（作为兜底依据时）
EVIDENCE_EXCERPT_MAX_LENGTH = 100

HIGHLIGHT_PROMPT = """你是餐饮点评平台的口碑分析专家。下面会给你一家商家的正面用户评价列表（仅含好评/中评），请从中挖掘商家的核心亮点。

## 规则（非常重要，违反任何一条都是严重错误）
1. 所有亮点必须来源于给出的评价原文，禁止编造评价中不存在的招牌菜、环境描述、服务细节、价格信息或品牌故事
2. 每个亮点必须给出支撑它的 reviewIds（必须是输入中真实存在的 reviewId），没有依据的亮点不要输出
3. mentionCount 表示提到该亮点的不同评价数量，必须等于 reviewIds 的数量
4. 没有足够证据支撑的维度直接返回空数组，不要强行总结
5. highlightType 根据内容归类：
   - SIGNATURE_DISH：顾客反复提及的具体菜品（必须有明确菜名）
   - ENVIRONMENT：装修风格、氛围、景观、座位舒适度、噪音水平等
   - SERVICE：服务员态度、响应速度、专业程度、等位服务等
   - PRICE：性价比、实惠、分量足、优惠活动等
   - BRAND_FEATURE：排队热度、老店口碑、独特卖点等不属于以上四类的特色
6. positiveRatio 计算公式：该亮点关联评价中正面（POSITIVE）评价的数量 / 关联评价总数
7. evidenceExcerpt 必须是对应评价原文中的片段（可以截取，不能改写）

## 输出格式（严格 JSON）
{
  "highlights": [
    {
      "highlightType": "SIGNATURE_DISH",
      "title": "招牌拿铁广受好评",
      "description": "超过80%的顾客在评价中提到了招牌拿铁，一致认为口感醇厚、拉花精致",
      "mentionCount": 8,
      "positiveRatio": 0.95,
      "reviewIds": [101, 102, 103, 104, 105, 106, 107, 108]
    },
    {
      "highlightType": "ENVIRONMENT",
      "title": "安静舒适的ins风装修",
      "description": "多位顾客提到店面装修风格清新、环境安静，适合办公和约会",
      "mentionCount": 6,
      "positiveRatio": 0.90,
      "reviewIds": [102, 103, 105, 107, 109, 110]
    }
  ],
  "evidences": [
    {"reviewId": 101, "highlightType": "SIGNATURE_DISH", "evidenceExcerpt": "招牌拿铁真的绝了，拉花超级精致"},
    {"reviewId": 102, "highlightType": "ENVIRONMENT", "evidenceExcerpt": "环境很安静，适合带着电脑来办公"}
  ]
}

注意：
- 最多输出 10 条亮点，按 mentionCount × positiveRatio 综合排序取前 10
- 同类型亮点可以有多个（如多个招牌菜）
- 同一评价可以支撑多条亮点
"""


class HighlightService:
    """商家亮点挖掘服务"""

    def _generate_trace_id(self) -> str:
        return current_trace_id()

    async def generate(self, request: HighlightGenerateRequest) -> HighlightGenerateResponse:
        """
        从正面评价中挖掘商家亮点。

        评论不足时返回 INSUFFICIENT_DATA，不调用大模型（验收准则 5）。
        """
        trace_id = self._generate_trace_id()

        # 过滤空内容评论
        reviews = [r for r in request.reviews if r.content and r.content.strip()]

        # 正面评论不足 — 不调用模型（验收准则 5）
        if len(reviews) < request.minimumPositiveCount:
            return HighlightGenerateResponse(
                merchantId=request.merchantId,
                version=request.version,
                highlightStatus="INSUFFICIENT_DATA",
                reviewCount=len(reviews),
                minimumPositiveCount=request.minimumPositiveCount,
                businessTraceId=trace_id,
            )

        # 构造用户消息：带 reviewId 和已有分析结果的编号评价列表
        lines = []
        for r in reviews:
            time_part = f"，时间：{r.reviewTime}" if r.reviewTime else ""
            kw_part = f"，关键词：{'、'.join(r.keywords)}" if r.keywords else ""
            sentiment_part = f"，情感：{r.sentiment}" if r.sentiment else ""
            lines.append(
                f"[reviewId={r.reviewId}] 评分：{r.rating}星{sentiment_part}{kw_part}{time_part}\n{r.content}"
            )
        user_message = (
            f"以下是该商家的 {len(reviews)} 条正面/中性用户评价，请从中挖掘商家亮点：\n\n"
            + "\n\n".join(lines)
        )

        try:
            result = await llm_service.chat_json(
                system_prompt=HIGHLIGHT_PROMPT,
                user_message=user_message,
                temperature=0.3,
                max_tokens=6000,
            )
        except Exception as e:
            logger.error(f"亮点挖掘失败 merchantId={request.merchantId}: {repr(e)}")
            return HighlightGenerateResponse(
                merchantId=request.merchantId,
                version=request.version,
                highlightStatus="FAILED",
                reviewCount=len(reviews),
                minimumPositiveCount=request.minimumPositiveCount,
                modelName=f"fallback:{llm_service.model}",
                businessTraceId=trace_id,
                errorMessage=repr(e)[:500],
            )

        # ==== 结果校验：过滤编造的 reviewId，丢弃没有依据的亮点 ====
        valid_ids = {r.reviewId for r in reviews}
        id_to_content = {r.reviewId: r.content for r in reviews}

        highlights = self._validate_highlights(
            result.get("highlights"), valid_ids
        )
        evidences = self._validate_evidences(
            result.get("evidences"), valid_ids, id_to_content
        )

        # 模型没给依据时，从亮点自动补充依据（保证验收准则 2 可追溯）
        evidences = self._ensure_evidences(evidences, highlights, id_to_content)

        return HighlightGenerateResponse(
            merchantId=request.merchantId,
            version=request.version,
            highlightStatus="SUCCESS",
            highlights=highlights,
            reviewCount=len(reviews),
            minimumPositiveCount=request.minimumPositiveCount,
            evidences=evidences,
            modelName=llm_service.model,
            businessTraceId=trace_id,
        )

    def _validate_highlights(self, raw, valid_ids: set) -> List[HighlightItem]:
        """校验亮点列表：过滤无效 reviewId，无依据的亮点直接丢弃"""
        highlights = []
        seen_titles = set()  # 去重：同标题只保留一条
        for item in raw or []:
            if not isinstance(item, dict):
                continue

            title = str(item.get("title", "")).strip()
            if not title or title in seen_titles:
                continue

            highlight_type = str(item.get("highlightType", "")).strip().upper()
            if highlight_type not in {
                "SIGNATURE_DISH", "ENVIRONMENT", "SERVICE", "PRICE", "BRAND_FEATURE"
            }:
                continue

            desc = str(item.get("description", "")).strip()
            ids = self._filter_ids(item.get("reviewIds"), valid_ids)
            if not ids:
                continue  # 没有真实评价支撑 → 丢弃（验收准则 2）

            mention_count = item.get("mentionCount", len(ids))
            if not isinstance(mention_count, (int, float)) or mention_count < 1:
                mention_count = len(ids)

            positive_ratio = item.get("positiveRatio", 1.0)
            if not isinstance(positive_ratio, (int, float)) or positive_ratio < 0 or positive_ratio > 1:
                positive_ratio = 1.0

            seen_titles.add(title)
            highlights.append(HighlightItem(
                highlightType=highlight_type,
                title=title,
                description=desc or title,
                mentionCount=min(int(mention_count), len(ids)),
                positiveRatio=float(positive_ratio),
                reviewIds=ids,
            ))

        # 按 mentionCount × positiveRatio 降序排列
        highlights.sort(
            key=lambda h: h.mentionCount * h.positiveRatio,
            reverse=True,
        )
        # 最多取前 10 条
        return highlights[:10]

    def _validate_evidences(
        self, raw, valid_ids: set, id_to_content: dict
    ) -> List[HighlightEvidence]:
        """校验依据：reviewId 必须真实存在；片段不在原文中时用原文开头兜底"""
        evidences = []
        for item in raw or []:
            if not isinstance(item, dict):
                continue
            review_id = item.get("reviewId")
            if review_id not in valid_ids:
                continue
            excerpt = str(item.get("evidenceExcerpt", "")).strip()
            content = id_to_content.get(review_id, "")
            # 片段必须真实出现在原评价中，否则回退为原文截取
            if not excerpt or excerpt not in content:
                excerpt = content[:EVIDENCE_EXCERPT_MAX_LENGTH]
            evidences.append(HighlightEvidence(
                reviewId=review_id,
                highlightType=str(item.get("highlightType", "SIGNATURE_DISH")).upper(),
                evidenceExcerpt=excerpt,
            ))
        return evidences

    def _ensure_evidences(
        self,
        evidences: List[HighlightEvidence],
        highlights: List[HighlightItem],
        id_to_content: dict,
    ) -> List[HighlightEvidence]:
        """每条亮点至少有一条依据；缺失时用亮点第一条评价的原文补齐"""
        existing = {(e.reviewId, e.highlightType) for e in evidences}
        for h in highlights:
            if not h.reviewIds:
                continue
            first_id = h.reviewIds[0]
            if (first_id, h.highlightType) in existing:
                continue
            evidences.append(HighlightEvidence(
                reviewId=first_id,
                highlightType=h.highlightType,
                evidenceExcerpt=id_to_content.get(first_id, "")[:EVIDENCE_EXCERPT_MAX_LENGTH],
            ))
            existing.add((first_id, h.highlightType))
        return evidences

    def _filter_ids(self, raw_ids, valid_ids: set) -> List[int]:
        """只保留输入集合中真实存在的 reviewId，去重保序"""
        seen = []
        for i in raw_ids or []:
            if isinstance(i, int) and i in valid_ids and i not in seen:
                seen.append(i)
        return seen


# 单例
highlight_service = HighlightService()
