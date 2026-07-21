"""
周边竞品对比服务（EPIC-02 Story 6）

根据 Spring Boot 传入的本店与竞品统计数据，调用大模型生成：
- 每家商家的优势/短板分析（相对于竞品群体）
- 横向对比总结（本店在竞品中的定位）
- 基于差距的改进建议

核心原则：
1. 所有分析必须基于传入的真实统计数据，禁止编造不存在的事实
2. 差距不明显时明确说明"无明显差异"，不做强行解读
3. 改进建议必须具体、可执行，针对统计中暴露的具体短板
"""
import logging
import uuid
from typing import List, Optional

from app.models.schemas import (
    CompetitorComparisonRequest,
    CompetitorComparisonResponse,
    CompetitorMerchantData,
    CompetitorSingleComparisonResult,
)
from app.services.llm_service import llm_service

logger = logging.getLogger(__name__)

# ============================================
# 竞品对比分析提示词
# ============================================
COMPETITOR_COMPARISON_PROMPT = """你是一位餐饮行业的数据分析顾问，专精于为商家提供竞品对比分析和经营建议。

## 输入数据说明
你会收到一家"本店"和1~3家"竞品"的结构化统计数据。字段含义：
- averagePrice: 人均消费（元）
- rating: 综合评分（0~5分）
- reviewCount: 评价总数
- positiveRate: 好评率（0~1，即正面评价占比）
- tasteRating: 口味评分均值（0~5分）
- environmentRating: 环境评分均值（0~5分）
- serviceRating: 服务评分均值（0~5分）
- topPositiveTags: 顾客高频提及的正面标签
- topNegativeIssues: 顾客主要投诉的问题

## 分析任务
请从以下角度对本店和竞品进行对比分析，返回严格 JSON：

### 1. 每家商家的分析（merchantAnalyses）
对每一家商家（包括本店），分析其中相对于其他几家的：
- **strengths（优势）**：该商家在哪些维度明显领先于其他商家（至少领先10%才算优势）
- **weaknesses（短板）**：该商家在哪些维度明显落后于其他商家（至少落后10%才算短板）
- **overallAssessment（综合评价）**：1~2 句话总结该商家在竞品中的定位

### 2. 横向对比总结（summaryText）
2~3 句话概括本店在这组竞品中的整体表现和定位。
如果本店在多数维度领先 → 给出领先结论
如果本店在多数维度落后 → 给出追赶建议
如果各有优劣     → 点明关键优势项和关键短板项
如果差距不明显   → 明确说明"各商家在核心指标上差异不大"

### 3. 改进建议（improvementSuggestions）
只针对**本店**给出改进建议，必须：
- 每条建议对应一个具体的数据短板（如"服务评分3.2低于竞品平均4.1"）
- 建议具体可执行（如"建议在周末高峰期增加1-2名服务人员"而非"提升服务"）
- 最多3条建议，排序从最关键到次要
- 如果本店各项指标均领先于竞品 → 建议侧重如何保持优势和拓展新优势
- 如果差距不明显 → 可提出差异化竞争方向

## 规则（违反任何一条都是错误）
1. **禁止编造**：所有分析必须严格基于输入数据中的数字和标签，不得编造输入中没有的内容
2. **差异不明显的处理**：当某维度差距小于10%时，不要强行判定为"优势"或"短板"，可以说"与竞品持平"
3. **正向表达**：即使是指出短板，也要用建设性表述，不用贬低性语言
4. **具体引用**：提到任何结论时，尽量引用具体数值（如"本店评分4.3 > 竞品均值3.8"）
5. **不做横向拉踩**：不贬低任何商家，焦点放在客观数据和建设性建议上

## 输出格式（严格 JSON）
{
  "merchantAnalyses": [
    {
      "merchantId": 1,
      "merchantName": "本店名称",
      "strengths": ["优势1：具体数据对比", "优势2：具体数据对比"],
      "weaknesses": ["短板1：具体数据对比"],
      "overallAssessment": "综合评价文字"
    }
  ],
  "summaryText": "横向对比总结，2~3句话",
  "improvementSuggestions": [
    "建议1：针对具体问题 + 可执行措施",
    "建议2：针对具体问题 + 可执行措施"
  ]
}

## 注意事项
- strengths 和 weaknesses 可以为空数组（当没有明显优势/短板时）
- 不要为了填充数组而编造不存在的优劣
- 语言风格：专业、简洁、建设性（面向商家用户）
"""


class CompetitorComparisonService:
    """周边竞品对比服务 — 用大模型分析数据 + 生成对比见解"""

    def _generate_trace_id(self) -> str:
        """生成 AI 调用追踪ID"""
        return f"trace-{uuid.uuid4().hex[:16]}"

    async def compare(self, request: CompetitorComparisonRequest) -> CompetitorComparisonResponse:
        """
        对传入的商家统计数据进行竞品对比分析。

        调用大模型分析统计数据并生成：
        - 每家商家的优势/短板
        - 横向对比总结
        - 本店改进建议

        统计数据和对比维度都由 Spring Boot 负责计算和提供，
        AI 只负责生成自然语言分析，不修改任何数值。
        """
        trace_id = self._generate_trace_id()

        system_prompt = (
            request.systemPrompt
            if request.systemPrompt and request.systemPrompt.strip()
            else COMPETITOR_COMPARISON_PROMPT
        )
        prompt_version = (
            request.promptVersion.strip()
            if request.promptVersion and request.promptVersion.strip()
            else "competitor-comparison:v1"
        )

        # ---- 构造给 LLM 的用户消息 ----
        user_message = self._build_user_message(request)

        try:
            result = await llm_service.chat_json(
                system_prompt=system_prompt,
                user_message=user_message,
                temperature=0.3,  # 适中温度，允许一定表达多样性但要忠实数据
                max_tokens=6000,
            )
        except Exception as e:
            logger.error(
                f"竞品对比分析失败 merchantId={request.merchantId}: {repr(e)}"
            )
            return CompetitorComparisonResponse(
                merchantId=request.merchantId,
                comparisonStatus="FAILED",
                modelName=f"fallback:{llm_service.model}",
                promptVersion=prompt_version,
                businessTraceId=trace_id,
                errorMessage=repr(e)[:500],
            )

        # ---- 解析并校验 LLM 返回结果 ----
        merchant_analyses = self._parse_merchant_analyses(
            result.get("merchantAnalyses", []), request.competitors
        )
        summary_text = str(result.get("summaryText", "")).strip() or None
        suggestions = self._parse_suggestions(result.get("improvementSuggestions", []))

        return CompetitorComparisonResponse(
            merchantId=request.merchantId,
            comparisonStatus="SUCCESS",
            merchantAnalyses=merchant_analyses,
            summaryText=summary_text,
            improvementSuggestions=suggestions,
            modelName=llm_service.model,
            promptVersion=prompt_version,
            businessTraceId=trace_id,
        )

    # ============================================
    # 构造 LLM 输入
    # ============================================

    def _build_user_message(self, request: CompetitorComparisonRequest) -> str:
        """
        将 CompetitorComparisonRequest 中的商家数据格式化为
        大模型可读的自然语言表格。
        """
        lines = [
            f"以下是一家名为\"本店\"的商家和其周边同类型竞品的统计数据。",
            f"本店是商家ID={request.merchantId}。",
            f"请对本店及竞品进行对比分析。\n",
        ]

        for i, c in enumerate(request.competitors):
            is_self = c.merchantId == request.merchantId
            label = "【本店】" if is_self else f"【竞品{i + 1}】"

            lines.append(f"{label} {c.merchantName}")
            lines.append(f"  - 商家ID: {c.merchantId}")
            lines.append(f"  - 类别: {c.category}")
            if c.cuisine:
                lines.append(f"  - 菜系: {c.cuisine}")
            if c.address:
                lines.append(f"  - 地址: {c.address}")
            if c.averagePrice is not None:
                lines.append(f"  - 人均消费: ¥{c.averagePrice}")
            if c.rating is not None:
                lines.append(f"  - 综合评分: {c.rating}/5")
            lines.append(f"  - 评价总数: {c.reviewCount}")
            if c.positiveRate is not None:
                lines.append(f"  - 好评率: {c.positiveRate:.0%}")
            if c.tasteRating is not None:
                lines.append(f"  - 口味评分: {c.tasteRating}/5")
            if c.environmentRating is not None:
                lines.append(f"  - 环境评分: {c.environmentRating}/5")
            if c.serviceRating is not None:
                lines.append(f"  - 服务评分: {c.serviceRating}/5")
            if c.topPositiveTags:
                lines.append(f"  - 高频正面标签: {'、'.join(c.topPositiveTags[:5])}")
            if c.topNegativeIssues:
                lines.append(f"  - 主要差评问题: {'、'.join(c.topNegativeIssues[:5])}")
            lines.append("")

        return "\n".join(lines)

    # ============================================
    # 解析 & 校验 LLM 输出
    # ============================================

    def _parse_merchant_analyses(
        self,
        raw: list,
        competitors: List[CompetitorMerchantData],
    ) -> List[CompetitorSingleComparisonResult]:
        """
        解析 LLM 返回的商家分析列表，校验 merchantId 的合法性，
        过滤掉不存在或编造的商家。
        """
        valid_ids = {c.merchantId for c in competitors}
        valid_names = {c.merchantId: c.merchantName for c in competitors}
        parsed = []
        seen_ids = set()

        for item in raw or []:
            if not isinstance(item, dict):
                continue

            mid = item.get("merchantId")
            if mid not in valid_ids or mid in seen_ids:
                continue  # 跳过非法或重复的商家ID

            seen_ids.add(mid)
            parsed.append(CompetitorSingleComparisonResult(
                merchantId=mid,
                merchantName=valid_names.get(mid, str(mid)),
                strengths=self._clean_string_list(item.get("strengths", [])),
                weaknesses=self._clean_string_list(item.get("weaknesses", [])),
                overallAssessment=str(item.get("overallAssessment", "")).strip(),
            ))

        # 确保所有传入的商家都有分析（LLM漏掉时补一条空记录）
        for c in competitors:
            if c.merchantId not in seen_ids:
                parsed.append(CompetitorSingleComparisonResult(
                    merchantId=c.merchantId,
                    merchantName=c.merchantName,
                    overallAssessment="暂无分析",
                ))

        return parsed

    def _parse_suggestions(self, raw: list) -> List[str]:
        """解析改进建议列表，过滤空字符串，最多保留3条"""
        suggestions = []
        for item in raw or []:
            if isinstance(item, str) and item.strip():
                suggestions.append(item.strip())
        return suggestions[:3]

    def _clean_string_list(self, raw: list) -> List[str]:
        """清洗字符串列表：去重、去空、保留顺序"""
        seen = set()
        result = []
        for item in raw or []:
            if isinstance(item, str) and item.strip():
                s = item.strip()
                if s not in seen:
                    seen.add(s)
                    result.append(s)
        return result


# 单例
competitor_comparison_service = CompetitorComparisonService()
