"""
评论摘要忠实性测试服务 — LLM-as-Judge 验证摘要是否忠实于原始评价（EPIC-01 Story 8）

核心原理：
  摘要中的每个要点（优点/不足/推荐菜/环境/服务/近期变化/总结文本）
  都声称来源于某些评价（通过 reviewIds 引用）。忠实性测试就是要验证：
  给定这些评价的原文内容，该要点是否确实能从评价中推断出来？

  我们采用 LLM-as-Judge 模式：
  - 将「声明文本」+「支撑评价原文」一起送入评判模型
  - 评判模型给出三分类判定：FAITHFUL / UNFAITHFUL / UNCERTAIN
  - 每个判定附带置信度 + 详细理由（引用原文语句）

设计要点：
  1. 从 ReviewSummaryResponse 中提取所有声明（claims）
  2. 通过 reviewIds 匹配原始评价原文作为证据
  3. 将所有声明 + 证据批量送入 LLM 一次性评判（减少 API 调用次数）
  4. 汇总统计整体忠实性得分（FAITHFUL 占比）
  5. 未匹配到原文的声明标记为 UNCERTAIN（信息不足无法判断）
"""
import logging
from typing import List, Optional, Dict

from app.core.trace_context import current_trace_id
from app.models.schemas import (
    FaithfulnessTestRequest,
    FaithfulnessTestResponse,
    FaithfulnessClaimResult,
    FaithfulnessVerdictEnum,
    ReviewSummaryResponse,
    SummaryPoint,
    FaithfulnessReviewItem,
)
from app.services.llm_service import llm_service

logger = logging.getLogger(__name__)

# ============================================
# 忠实性评判提示词（LLM-as-Judge）
# ============================================
FAITHFULNESS_JUDGE_PROMPT = """你是一个严格的事实核查专家，专门验证餐饮评论摘要的忠实性。

## 你的任务
下面会给你一份商家口碑摘要中的多个「声明」（声明是摘要中的一句话或一个要点），以及每个声明所引用的「原始评价原文」。
你需要逐一判断每个声明是否**忠实于**其引用的原始评价。

## 什么是"忠实"（FAITHFUL）？
一个声明是忠实的，当且仅当：
- 声明的核心内容可以直接从引用的评价原文中找到依据
- 声明没有编造评价中不存在的信息
- 声明没有歪曲、夸大或误读评价的意思
- 声明中的数值（如 "3人提到"）与评价原文中实际出现的情况一致

## 什么是"不忠实"（UNFAITHFUL）？
一个声明是不忠实的，当：
- 声明的内容在引用评价中完全找不到依据（幻觉/编造）
- 声明的内容与引用评价的表述明显矛盾
- 声明的核心事实性错误（如把好评总结成差评、张冠李戴）

## 什么是"不确定"（UNCERTAIN）？
在以下情况下标记为不确定：
- 引用评价数量过少（仅1条），不足以确认趋势性声明
- 评价原文表述模糊，有多种解读可能
- 声明部分有依据但关键细节无法确认

## 评判要求（非常重要）
1. 逐条评判，不要遗漏任何声明
2. 每个判定的 reasoning 必须引用评价原文中的具体语句（用引号括起来），不能只写"评价中有提到"
3. 如果引用的评价原文为空或无法匹配，判定为 UNCERTAIN，reasoning 写明"未找到对应评价原文"
4. 一次评价可以支撑多个声明，不同声明的评判相互独立
5. 只基于给出的评价原文做判断，不要引入外部知识

## 输出格式（严格 JSON）
{
  "claimResults": [
    {
      "claimIndex": 0,
      "verdict": "FAITHFUL",
      "confidence": 0.92,
      "reasoning": "声明'菜品口味好'在评价[reviewId=101]中有直接依据：'他家的红烧肉特别入味'，评价[reviewId=102]中也提到'口味确实不错'。两条评价均正面评价口味，声明忠实。"
    },
    {
      "claimIndex": 1,
      "verdict": "UNFAITHFUL",
      "confidence": 0.95,
      "reasoning": "声明'周末排队久'声称引用评价[reviewId=104,105]，但评价[reviewId=104]原文为'工作日中午去的，不用排队'，评价[reviewId=105]原文未提及排队相关字眼。两条评价均不支持'周末排队久'这一声明。"
    }
  ]
}

注意：
- claimIndex 必须与输入中的声明序号一致
- verdict 只能是 FAITHFUL、UNFAITHFUL、UNCERTAIN 之一
- confidence 取 0~1 之间的值
- reasoning 必须具体，引用原文片段
- 如果某个声明的 citedReviews 为空，直接判 UNCERTAIN
"""

# ============================================
# 单条评价原文最大截取长度（避免上下文溢出）
# ============================================
MAX_REVIEW_CONTENT_LENGTH = 500


class FaithfulnessTestService:
    """
    评论摘要忠实性测试服务

    采用 LLM-as-Judge 模式，对摘要中的每个声明做忠实性验证。
    整体流程：
    1. 从摘要中提取所有声明
    2. 为每个声明匹配引用评价的原文
    3. 构造评判 prompt，批量送入 LLM
    4. 解析评判结果，计算整体得分

    使用方式（单例）：
        from app.services.faithfulness_test_service import faithfulness_test_service
        result = await faithfulness_test_service.test(request)
    """

    # --------------------------------------------------
    # 公共入口
    # --------------------------------------------------

    async def test(self, request: FaithfulnessTestRequest) -> FaithfulnessTestResponse:
        """
        对商家口碑摘要执行忠实性测试

        参数:
            request: 包含已生成的摘要 + 原始评价原文列表

        返回:
            FaithfulnessTestResponse: 每个声明的忠实性判定 + 整体得分
        """
        trace_id = current_trace_id()

        # ---- Step 1: 构建 reviewId → 原文 的索引 ----
        id_to_review: Dict[int, FaithfulnessReviewItem] = {}
        for r in request.reviews:
            id_to_review[r.reviewId] = r

        # ---- Step 2: 从摘要中提取所有声明 ----
        all_claims = self._extract_claims(request.summary, id_to_review)

        # 无声明可测（摘要内容为空）
        if not all_claims:
            return FaithfulnessTestResponse(
                merchantId=request.merchantId,
                testStatus="SUCCESS",
                overallScore=1.0,
                totalClaims=0,
                faithfulCount=0,
                unfaithfulCount=0,
                uncertainCount=0,
                claimResults=[],
                summaryText=request.summary.summaryText,
                modelName=llm_service.model,
                businessTraceId=trace_id,
            )

        # ---- Step 3: 构建批量评判的用户消息 ----
        user_message = self._build_judge_message(all_claims, id_to_review)

        # ---- Step 4: 调用 LLM 评判 ----
        try:
            judge_result = await llm_service.chat_json(
                system_prompt=FAITHFULNESS_JUDGE_PROMPT,
                user_message=user_message,
                temperature=0.1,   # 低温度保证判定一致性
                max_tokens=8000,   # 给足 token 空间容纳多个声明的详细 reasoning
            )
        except Exception as e:
            logger.error(
                f"忠实性测试 LLM 调用失败 merchantId={request.merchantId}: {repr(e)}"
            )
            return FaithfulnessTestResponse(
                merchantId=request.merchantId,
                testStatus="FAILED",
                overallScore=0.0,
                totalClaims=len(all_claims),
                faithfulCount=0,
                unfaithfulCount=0,
                uncertainCount=len(all_claims),
                claimResults=[
                    FaithfulnessClaimResult(
                        claimType=c["claimType"],
                        claimText=c["claimText"],
                        verdict=FaithfulnessVerdictEnum.UNCERTAIN,
                        confidence=0.0,
                        reasoning=f"LLM 调用失败，无法完成评判: {repr(e)[:300]}",
                        citedReviewIds=c["reviewIds"],
                        actualMatchingCount=c["actualMatchingCount"],
                    )
                    for c in all_claims
                ],
                summaryText=request.summary.summaryText,
                modelName=f"fallback:{llm_service.model}",
                businessTraceId=trace_id,
                errorMessage=repr(e)[:500],
            )

        # ---- Step 5: 将 LLM 返回的评判结果与原始声明对齐 ----
        raw_results = judge_result.get("claimResults", [])
        claim_results = self._align_results(all_claims, raw_results)

        # ---- Step 6: 统计汇总 ----
        faithful_count = sum(1 for c in claim_results if c.verdict == FaithfulnessVerdictEnum.FAITHFUL)
        unfaithful_count = sum(1 for c in claim_results if c.verdict == FaithfulnessVerdictEnum.UNFAITHFUL)
        uncertain_count = sum(1 for c in claim_results if c.verdict == FaithfulnessVerdictEnum.UNCERTAIN)
        total = len(claim_results)
        overall_score = faithful_count / total if total > 0 else 1.0

        # 测试状态：有 UNFAITHFUL 判定时标记 PARTIAL（部分不忠实）
        if unfaithful_count > 0:
            test_status = "PARTIAL"
        else:
            test_status = "SUCCESS"

        logger.info(
            f"忠实性测试完成 merchantId={request.merchantId}: "
            f"total={total}, faithful={faithful_count}, "
            f"unfaithful={unfaithful_count}, uncertain={uncertain_count}, "
            f"score={overall_score:.2f}"
        )

        return FaithfulnessTestResponse(
            merchantId=request.merchantId,
            testStatus=test_status,
            overallScore=overall_score,
            totalClaims=total,
            faithfulCount=faithful_count,
            unfaithfulCount=unfaithful_count,
            uncertainCount=uncertain_count,
            claimResults=claim_results,
            summaryText=request.summary.summaryText,
            modelName=llm_service.model,
            businessTraceId=trace_id,
        )

    # --------------------------------------------------
    # 声明提取：将摘要结构化字段展开为统一的声明列表
    # --------------------------------------------------

    def _extract_claims(
        self,
        summary: ReviewSummaryResponse,
        id_to_review: Dict[int, FaithfulnessReviewItem],
    ) -> List[dict]:
        """
        从 ReviewSummaryResponse 中提取所有需要验证的声明

        每个声明统一为 dict 格式：
        {
            "claimType": str,        # 声明类型标识
            "claimText": str,        # 声明文本内容
            "reviewIds": List[int],  # 声明引用的评价ID
            "actualMatchingCount": int,  # 实际可匹配到原文的评价数
        }

        提取规则：
        - 跳过文本为空的声明
        - 跳过 reviewIds 全为空的声明（没有依据的声明不予测试）
        """
        claims = []

        # 1) 优点（advantages）
        for point in (summary.advantages or []):
            claims.append(self._point_to_claim(
                point, "advantage", id_to_review
            ))

        # 2) 不足（disadvantages）
        for point in (summary.disadvantages or []):
            claims.append(self._point_to_claim(
                point, "disadvantage", id_to_review
            ))

        # 3) 推荐菜（recommendedDishes）
        for point in (summary.recommendedDishes or []):
            claims.append(self._point_to_claim(
                point, "recommendedDish", id_to_review
            ))

        # 4) 环境总结（environmentSummary）
        if summary.environmentSummary and isinstance(summary.environmentSummary, dict):
            text = str(summary.environmentSummary.get("text", "")).strip()
            ids = summary.environmentSummary.get("reviewIds", []) or []
            if text:
                claims.append(self._make_claim(
                    "environmentSummary", text, ids, id_to_review
                ))

        # 5) 服务总结（serviceSummary）
        if summary.serviceSummary and isinstance(summary.serviceSummary, dict):
            text = str(summary.serviceSummary.get("text", "")).strip()
            ids = summary.serviceSummary.get("reviewIds", []) or []
            if text:
                claims.append(self._make_claim(
                    "serviceSummary", text, ids, id_to_review
                ))

        # 6) 近期变化（recentChanges）
        for change in (summary.recentChanges or []):
            if not isinstance(change, dict):
                continue
            text = str(change.get("text", "")).strip()
            direction = change.get("direction", "")
            ids = change.get("reviewIds", []) or []
            if text:
                # 将方向信息拼入声明文本，便于评判模型理解上下文
                full_text = f"{text}（趋势：{direction}）" if direction else text
                claims.append(self._make_claim(
                    "recentChange", full_text, ids, id_to_review
                ))

        # 7) 总体摘要文本（summaryText）
        if summary.summaryText and summary.summaryText.strip():
            # summaryText 引用全部有效评价
            all_ids = list(id_to_review.keys())
            if all_ids:
                claims.append(self._make_claim(
                    "summaryText",
                    summary.summaryText.strip(),
                    all_ids,
                    id_to_review,
                ))

        # 过滤掉没有任何有效评价支撑的声明
        claims = [c for c in claims if c["actualMatchingCount"] > 0]

        return claims

    def _point_to_claim(
        self,
        point: SummaryPoint,
        claim_type: str,
        id_to_review: Dict[int, FaithfulnessReviewItem],
    ) -> dict:
        """将 SummaryPoint 转为统一的 claim dict"""
        name = (point.name or "").strip()
        ids = point.reviewIds or []
        return self._make_claim(claim_type, name, ids, id_to_review)

    def _make_claim(
        self,
        claim_type: str,
        text: str,
        review_ids: List[int],
        id_to_review: Dict[int, FaithfulnessReviewItem],
    ) -> dict:
        """构造统一的 claim dict，统计实际可匹配的评价数"""
        matching_count = sum(
            1 for rid in review_ids if rid in id_to_review
        )
        return {
            "claimType": claim_type,
            "claimText": text,
            "reviewIds": list(review_ids),
            "actualMatchingCount": matching_count,
        }

    # --------------------------------------------------
    # 评判消息构造
    # --------------------------------------------------

    def _build_judge_message(
        self,
        claims: List[dict],
        id_to_review: Dict[int, FaithfulnessReviewItem],
    ) -> str:
        """
        构造送给评判 LLM 的用户消息

        格式：
        ## 需要验证的声明列表
        声明 0 (类型: advantage)
        文本: xxx
        引用评价:
        [reviewId=101] 评分：4星
        评价原文内容...
        ---

        声明 1 (类型: disadvantage)
        ...

        这样评判模型可以一次性看到所有声明 + 对应证据，
        不需要多次 API 调用
        """
        # 先收集所有被引用的 reviewId，构造一个共享的"评价证物池"
        # 避免同一段评价在多个声明中重复出现
        cited_ids: set = set()
        for c in claims:
            for rid in c["reviewIds"]:
                if rid in id_to_review:
                    cited_ids.add(rid)

        # 构造证据池
        evidence_lines = ["## 原始评价证物池（供参考）", ""]
        for rid in sorted(cited_ids):
            review = id_to_review[rid]
            rating_str = f"，评分：{review.rating}星" if review.rating else ""
            # 截取过长评价，避免上下文溢出
            content = (review.content or "").strip()
            if len(content) > MAX_REVIEW_CONTENT_LENGTH:
                content = content[:MAX_REVIEW_CONTENT_LENGTH] + "...（原文过长已截断）"
            evidence_lines.append(
                f"[reviewId={rid}]{rating_str}\n{content}"
            )
            evidence_lines.append("---")
            evidence_lines.append("")

        # 构造声明列表
        claim_lines = ["## 需要验证的声明列表", ""]
        for i, c in enumerate(claims):
            claim_lines.append(
                f"### 声明 {i}（类型: {c['claimType']}）"
            )
            claim_lines.append(f"声明文本: {c['claimText']}")
            claim_lines.append(
                f"引用的评价ID: {c['reviewIds']} "
                f"（实际匹配到原文: {c['actualMatchingCount']}条）"
            )
            claim_lines.append("")

        user_message = (
            f"请对以下 {len(claims)} 个摘要声明进行忠实性评判。\n\n"
            + "\n".join(evidence_lines)
            + "\n"
            + "\n".join(claim_lines)
        )

        return user_message

    # --------------------------------------------------
    # 结果对齐与解析
    # --------------------------------------------------

    def _align_results(
        self,
        claims: List[dict],
        raw_results: list,
    ) -> List[FaithfulnessClaimResult]:
        """
        将 LLM 返回的评判结果与原始声明列表对齐

        容错处理：
        - LLM 可能遗漏某些声明的评判 → 标记为 UNCERTAIN
        - LLM 可能返回额外的评判 → 忽略
        - LLM 返回的 claimIndex 可能错位 → 按 claimIndex 对齐
        """
        # 先按 claimIndex 建立索引
        judge_map: Dict[int, dict] = {}
        for item in (raw_results or []):
            if not isinstance(item, dict):
                continue
            idx = item.get("claimIndex")
            if isinstance(idx, int) and 0 <= idx < len(claims):
                judge_map[idx] = item

        results = []
        for i, claim in enumerate(claims):
            judge = judge_map.get(i)

            if judge is None:
                # LLM 遗漏了这个声明的评判
                results.append(FaithfulnessClaimResult(
                    claimType=claim["claimType"],
                    claimText=claim["claimText"],
                    verdict=FaithfulnessVerdictEnum.UNCERTAIN,
                    confidence=0.0,
                    reasoning="评判模型未返回该声明的判定结果",
                    citedReviewIds=claim["reviewIds"],
                    actualMatchingCount=claim["actualMatchingCount"],
                ))
                continue

            # 解析并规范化 verdict
            raw_verdict = str(judge.get("verdict", "UNCERTAIN")).upper().strip()
            try:
                verdict = FaithfulnessVerdictEnum(raw_verdict)
            except ValueError:
                verdict = FaithfulnessVerdictEnum.UNCERTAIN

            # 解析并钳制 confidence
            raw_conf = judge.get("confidence", 0.5)
            try:
                confidence = max(0.0, min(1.0, float(raw_conf)))
            except (TypeError, ValueError):
                confidence = 0.5

            # 解析 reasoning（确保不为空）
            reasoning = str(judge.get("reasoning", "")).strip()
            if not reasoning:
                reasoning = "评判模型未提供理由"

            results.append(FaithfulnessClaimResult(
                claimType=claim["claimType"],
                claimText=claim["claimText"],
                verdict=verdict,
                confidence=confidence,
                reasoning=reasoning,
                citedReviewIds=claim["reviewIds"],
                actualMatchingCount=claim["actualMatchingCount"],
            ))

        return results


# ============================================
# 单例（遵循项目一致的模式）
# ============================================
faithfulness_test_service = FaithfulnessTestService()
