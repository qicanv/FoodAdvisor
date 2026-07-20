"""
评价智能总结服务 — 商家口碑摘要生成（EPIC-01 Story 7）

核心原则：所有结论必须能追溯到输入评论。
模型返回的每个要点都带 reviewIds，这里会过滤掉
不在输入集合中的 ID；没有真实依据的要点直接丢弃。
"""
import logging
import uuid
from app.core.trace_context import current_trace_id
from typing import List, Optional

from app.models.schemas import (
    ReviewSummaryRequest, ReviewSummaryResponse,
    SummaryPoint, SummaryEvidence, SummaryReviewItem
)
from app.services.llm_service import llm_service

logger = logging.getLogger(__name__)

# 评价原文片段最长截取长度（作为兜底依据时）
EVIDENCE_EXCERPT_MAX_LENGTH = 100
EVIDENCE_TYPES = {
    "ADVANTAGE", "DISADVANTAGE", "DISH",
    "ENVIRONMENT", "SERVICE", "RECENT_CHANGE",
}

REVIEW_SUMMARY_PROMPT = """你是餐饮点评平台的口碑分析专家。下面会给你一家商家的用户评价列表，请生成一份口碑摘要。

## 规则（非常重要，违反任何一条都是严重错误）
1. 所有结论必须来源于给出的评价原文，禁止编造评价中不存在的菜品、品牌历史、服务事实或任何描述
2. 每个要点必须给出支撑它的 reviewIds（必须是输入中真实存在的 reviewId），没有依据的要点不要输出
3. mentionCount 表示提到该要点的不同评价数量，必须等于 reviewIds 的数量
4. 某个维度（如环境、服务、推荐菜）在评价中没有足够信息时，返回空数组或空对象，不要强行总结
5. 区分长期口碑与近期动态：把时间最近的约 30% 评价视为"近期"，如果近期评价与整体口碑有明显差异（如某类问题集中出现、口味明显下滑或改善），写入 recentChanges；没有明显变化就返回空数组
6. 排队、卫生、分量、价格等问题作为 advantages 或 disadvantages 的条目呈现
7. evidenceExcerpt 必须是对应评价原文中的片段（可以截取，不能改写）

## 输出格式（严格 JSON）
{
  "summaryText": "一段 50~120 字的总体口碑概述，概括主要优点和主要问题",
  "advantages": [
    {"name": "菜品口味好", "mentionCount": 3, "reviewIds": [101, 102, 103]}
  ],
  "disadvantages": [
    {"name": "周末排队久", "mentionCount": 2, "reviewIds": [104, 105]}
  ],
  "recommendedDishes": [
    {"name": "招牌拿铁", "mentionCount": 2, "reviewIds": [101, 102]}
  ],
  "environmentSummary": {"text": "环境安静，适合谈事情", "reviewIds": [103]},
  "serviceSummary": {"text": "服务响应快，高峰期人手略显不足", "reviewIds": [102, 104]},
  "recentChanges": [
    {"text": "近期上菜速度相关差评增多", "direction": "DECLINING", "reviewIds": [104, 105]}
  ],
  "evidences": [
    {"reviewId": 104, "evidenceType": "DISADVANTAGE", "evidenceExcerpt": "周末去排了四十分钟"}
  ]
}

evidenceType 可选值：ADVANTAGE / DISADVANTAGE / DISH / ENVIRONMENT / SERVICE / RECENT_CHANGE
direction 可选值：IMPROVING / DECLINING / STABLE
"""


class ReviewSummaryService:
    """商家评价摘要生成服务"""

    def _generate_trace_id(self) -> str:
        return current_trace_id()

    async def summarize(self, request: ReviewSummaryRequest) -> ReviewSummaryResponse:
        trace_id = self._generate_trace_id()

        # 过滤空内容评论
        reviews = [r for r in request.reviews if r.content and r.content.strip()]

        # 评论不足 — 不调用模型，直接返回（验收准则 5）
        if len(reviews) < request.minimumReviewCount:
            return ReviewSummaryResponse(
                merchantId=request.merchantId,
                version=request.version,
                summaryStatus="INSUFFICIENT_DATA",
                reviewCount=len(reviews),
                minimumReviewCount=request.minimumReviewCount,
                businessTraceId=trace_id,
            )

        # 构造用户消息：带 reviewId 的编号评价列表
        lines = []
        for r in reviews:
            time_part = f"，时间：{r.reviewTime}" if r.reviewTime else ""
            lines.append(f"[reviewId={r.reviewId}] 评分：{r.rating}星{time_part}\n{r.content}")
        user_message = (
            f"以下是该商家的 {len(reviews)} 条用户评价，请生成口碑摘要：\n\n"
            + "\n\n".join(lines)
        )

        try:
            result = await llm_service.chat_json(
                system_prompt=REVIEW_SUMMARY_PROMPT,
                user_message=user_message,
                temperature=0.2,
                max_tokens=8000,  # 推理模型的思维链也计入 max_tokens，给足余量防止 JSON 被截断
            )
        except Exception as e:
            # repr(e)：超时类异常 str() 为空，repr 能保留异常类型名
            logger.error(f"摘要生成失败 merchantId={request.merchantId}: {repr(e)}")
            return ReviewSummaryResponse(
                merchantId=request.merchantId,
                version=request.version,
                summaryStatus="FAILED",
                reviewCount=len(reviews),
                minimumReviewCount=request.minimumReviewCount,
                modelName=f"fallback:{llm_service.model}",
                businessTraceId=trace_id,
                errorMessage=repr(e)[:500],
            )

        # ==== 结果校验：过滤编造的 reviewId，丢弃没有依据的要点 ====
        valid_ids = {r.reviewId for r in reviews}
        id_to_content = {r.reviewId: r.content for r in reviews}

        advantages = self._validate_points(result.get("advantages"), valid_ids)
        disadvantages = self._validate_points(result.get("disadvantages"), valid_ids)
        dishes = self._validate_points(result.get("recommendedDishes"), valid_ids)
        evidences = self._validate_evidences(
            result.get("evidences"), valid_ids, id_to_content
        )

        # 模型没给依据时，从要点自动补充依据（保证验收准则 3 可追溯）
        evidences = self._ensure_evidences(
            evidences, advantages, "ADVANTAGE", id_to_content
        )
        evidences = self._ensure_evidences(
            evidences, disadvantages, "DISADVANTAGE", id_to_content
        )
        evidences = self._ensure_evidences(
            evidences, dishes, "DISH", id_to_content
        )

        return ReviewSummaryResponse(
            merchantId=request.merchantId,
            version=request.version,
            summaryStatus="SUCCESS",
            summaryText=str(result.get("summaryText") or "").strip() or None,
            advantages=advantages,
            disadvantages=disadvantages,
            recommendedDishes=dishes,
            environmentSummary=self._validate_aspect(
                result.get("environmentSummary"), valid_ids
            ),
            serviceSummary=self._validate_aspect(
                result.get("serviceSummary"), valid_ids
            ),
            recentChanges=self._validate_changes(
                result.get("recentChanges"), valid_ids
            ),
            reviewCount=len(reviews),
            minimumReviewCount=request.minimumReviewCount,
            evidences=evidences,
            modelName=llm_service.model,
            businessTraceId=trace_id,
        )

    def _validate_points(self, raw, valid_ids: set) -> List[SummaryPoint]:
        """校验要点列表：过滤无效 reviewId，无依据的要点直接丢弃"""
        points = []
        for item in raw or []:
            if not isinstance(item, dict):
                continue
            name = str(item.get("name", "")).strip()
            if not name:
                continue
            ids = self._filter_ids(item.get("reviewIds"), valid_ids)
            if not ids:
                continue  # 没有真实评价支撑 → 丢弃（验收准则 6）
            points.append(SummaryPoint(
                name=name,
                mentionCount=len(ids),  # 以实际依据数为准，不信模型的计数
                reviewIds=ids,
            ))
        return points

    def _validate_aspect(self, raw, valid_ids: set) -> dict:
        """校验环境/服务小结：无有效依据时返回空对象（前端显示"暂无足够信息"）"""
        if not isinstance(raw, dict):
            return {}
        text = str(raw.get("text", "")).strip()
        ids = self._filter_ids(raw.get("reviewIds"), valid_ids)
        if not text or not ids:
            return {}
        return {"text": text, "reviewIds": ids}

    def _validate_changes(self, raw, valid_ids: set) -> List[dict]:
        """校验近期变化列表"""
        changes = []
        for item in raw or []:
            if not isinstance(item, dict):
                continue
            text = str(item.get("text", "")).strip()
            ids = self._filter_ids(item.get("reviewIds"), valid_ids)
            if not text or not ids:
                continue
            direction = str(item.get("direction", "STABLE")).upper()
            if direction not in {"IMPROVING", "DECLINING", "STABLE"}:
                direction = "STABLE"
            changes.append({"text": text, "direction": direction, "reviewIds": ids})
        return changes

    def _validate_evidences(
        self, raw, valid_ids: set, id_to_content: dict
    ) -> List[SummaryEvidence]:
        """校验依据：reviewId 必须真实存在；片段不在原文中时用原文开头兜底"""
        evidences = []
        for item in raw or []:
            if not isinstance(item, dict):
                continue
            review_id = item.get("reviewId")
            if review_id not in valid_ids:
                continue
            evidence_type = str(
                item.get("evidenceType", "ADVANTAGE")
            ).upper()
            if evidence_type not in EVIDENCE_TYPES:
                continue
            excerpt = str(item.get("evidenceExcerpt", "")).strip()
            content = id_to_content.get(review_id, "")
            # 片段必须真实出现在原评价中，否则回退为原文截取
            if not excerpt or excerpt not in content:
                excerpt = content[:EVIDENCE_EXCERPT_MAX_LENGTH]
            evidences.append(SummaryEvidence(
                reviewId=review_id,
                evidenceType=evidence_type,
                evidenceExcerpt=excerpt,
            ))
        return evidences

    def _ensure_evidences(
        self,
        evidences: List[SummaryEvidence],
        points: List[SummaryPoint],
        evidence_type: str,
        id_to_content: dict,
    ) -> List[SummaryEvidence]:
        """每类要点至少有一条依据；缺失时用要点第一条评价的原文补齐"""
        existing = {(e.reviewId, e.evidenceType) for e in evidences}
        for point in points:
            first_id = point.reviewIds[0]
            if (first_id, evidence_type) in existing:
                continue
            evidences.append(SummaryEvidence(
                reviewId=first_id,
                evidenceType=evidence_type,
                evidenceExcerpt=id_to_content.get(first_id, "")[:EVIDENCE_EXCERPT_MAX_LENGTH],
            ))
            existing.add((first_id, evidence_type))
        return evidences

    def _filter_ids(self, raw_ids, valid_ids: set) -> List[int]:
        """只保留输入集合中真实存在的 reviewId，去重保序"""
        seen = []
        for i in raw_ids or []:
            if isinstance(i, int) and i in valid_ids and i not in seen:
                seen.append(i)
        return seen


# 单例
review_summary_service = ReviewSummaryService()
