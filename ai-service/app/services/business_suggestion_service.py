"""
经营改进建议生成服务（EPIC-02 Story 8）

结合近期情感趋势、差评归因、商家亮点和竞品对比结果，
为商家生成阶段性的经营改进建议。

核心原则：
1. 所有建议必须基于传入的真实数据，禁止编造不存在的问题
2. 每项建议至少关联一个数据来源
3. 数据不足时明确返回 INSUFFICIENT_DATA，降低结论确定性
4. 建议区分短期(SHORT_TERM)和长期(LONG_TERM)措施
5. 预置数据中不存在的问题不会被作为主要改进建议
"""
import logging
import uuid
from typing import List, Optional

from app.models.schemas import (
    BusinessSuggestionRequest, BusinessSuggestionResponse,
    BusinessSuggestionItem,
)
from app.services.llm_service import llm_service

logger = logging.getLogger(__name__)

BUSINESS_SUGGESTION_PROMPT = """你是餐饮行业的经营顾问专家。下面会给你一家餐饮商家的多维度经营数据，请基于这些数据生成具体、可执行的经营改进建议。

## 输入数据说明
你会收到以下四类数据（可能部分缺失）：
1. **口碑趋势(reputationTrends)**：按月的平均评分、正面/负面评价占比变化
2. **差评归因(issueStats)**：差评的类别分布（卫生、服务、上菜速度、口味、价格、分量、排队、环境等），含数量和占比
3. **商家亮点(highlights)**：已挖掘的商家核心优势（招牌菜、环境、服务、价格、品牌特色）
4. **竞品数据(competitors)**：周边同类商家的基础数据（名称、评分、人均、评价数）

## 规则（非常重要，违反任何一条都是严重错误）
1. **所有建议必须基于给出的数据**，禁止编造数据中不存在的问题或现象
2. 每项建议的 **category** 根据主要数据来源归类：
   - NEGATIVE_ISSUE：主要依据差评归因数据
   - REPUTATION_TREND：主要依据口碑趋势变化
   - HIGHLIGHT_GAP：主要依据亮点数据中的不足（如某方面提及少）
   - COMPETITOR_GAP：主要依据竞品对比显示差距
3. **timeframe** 判断标准：
   - SHORT_TERM：1-4周内可实施的改进（如调整排班、优化某道菜品、改善某环节服务）
   - LONG_TERM：需要1个月以上系统性地改进（如重新装修、员工培训体系、供应链优化）
4. **priority** 判断标准：
   - HIGH：差评集中、对评分影响大、竞品明显领先的方面
   - MEDIUM：有一定数量反馈但非最紧急的问题
   - LOW：偶发性问题或锦上添花的改进
5. **confidence** 判断标准：
   - HIGH：数据充足（相关差评>=10条或趋势变化明显），结论确定性强
   - MEDIUM：有一定数据支撑但样本量适中
   - LOW：数据量低于配置阈值或趋势不明显，依据有限
6. **dataBasisSummary** 必须包含具体的指标、数量或占比，例如"近30天上菜速度相关差评占比从15%升至27%（涉及12条评价）"
7. **title** 应简洁明确，描述问题对象和改进方向
8. **description** 应包含：问题现状 → 数据依据 → 具体改进措施 → 预期效果
9. **expectedEffect** 描述改进后的预期效果，尽可能量化
10. 预置数据中不存在的问题类别不要强行生成建议
11. 数据充足的维度可以给多条建议（如差评集中在服务和上菜速度两个方向）
12. **最多输出 10 条建议**，按 priority（HIGH先）+ confidence（HIGH先）排序

## 输出格式（严格 JSON）
```json
{
  "status": "SUCCESS",
  "summaryText": "总体经营状况概述，2~3句话概括当前主要优势和待改进方向",
  "dataSufficiency": "SUFFICIENT",
  "suggestions": [
    {
      "title": "优化周末高峰期出餐速度",
      "description": "近30天上菜速度相关差评占比从15%上升至27%（涉及12条评价），主要集中在周五晚和周末午餐时段。建议：（1）周末高峰时段增加1名后厨帮工或安排预切配；（2）优化热门菜品备料流程，提前预制半成品；（3）在菜单上标注预计等待时间，管理顾客预期。",
      "category": "NEGATIVE_ISSUE",
      "priority": "HIGH",
      "timeframe": "SHORT_TERM",
      "expectedEffect": "预计可将上菜速度相关差评占比降至15%以下，平均等餐时间缩短5-8分钟",
      "dataBasisType": "NEGATIVE_ISSUE",
      "dataBasisSummary": "近30天上菜速度相关差评占比从15%升至27%，涉及12条评价，为该商家差评第一大类",
      "metricName": "上菜速度差评占比",
      "metricValue": "27%（较上期+12个百分点）",
      "confidence": "HIGH",
      "evidences": [
        {
          "sourceType": "NEGATIVE_ISSUE",
          "evidenceExcerpt": "上菜速度差评占比27%（12条），较上期上升12个百分点",
          "metricSnapshot": {
            "metricName": "上菜速度差评占比",
            "currentValue": "27%",
            "previousValue": "15%",
            "changeDirection": "UP",
            "periodType": "MONTH"
          }
        }
      ]
    },
    {
      "title": "强化招牌菜宣传，缩小与竞品评分差距",
      "description": "本店综合评分4.2，低于周边竞品'XX餐厅'（4.7）和'YY小馆'（4.5）。本店亮点中'招牌菜口味好'提及35次为最大优势。建议：（1）在店铺首页和外卖平台突出展示招牌菜及好评截图；（2）推出招牌菜试吃小份或优惠套餐吸引新客尝试；（3）邀请熟客在评价中多分享招牌菜体验。",
      "category": "COMPETITOR_GAP",
      "priority": "MEDIUM",
      "timeframe": "SHORT_TERM",
      "expectedEffect": "预计2-3个月内综合评分可提升0.2-0.3分",
      "dataBasisType": "COMPETITOR",
      "dataBasisSummary": "本店评分4.2，低于竞品'XX餐厅'4.7和'YY小馆'4.5；招牌菜亮点提及35次",
      "metricName": "综合评分竞品差距",
      "metricValue": "低于竞品均值0.4分",
      "confidence": "MEDIUM",
      "evidences": [
        {
          "sourceType": "HIGHLIGHT",
          "evidenceExcerpt": "超过80%的顾客在评价中提到了招牌菜，一致认为口味出众"
        }
      ]
    }
  ]
}
```

## 状态判断
- 如果数据严重不足（所有数据源都不可用或无法得出可靠结论），返回 status="INSUFFICIENT_DATA"，suggestions=[]
- 如果数据部分可用但不足以支撑某些维度的建议，只在有数据支撑的维度生成建议，confidence 设为 LOW
- 如果至少能生成一条基于数据的建议，返回 status="SUCCESS"
"""


class BusinessSuggestionService:
    """经营改进建议生成服务"""

    def _generate_trace_id(self) -> str:
        return f"bs-{uuid.uuid4().hex[:12]}"

    async def generate(self, request: BusinessSuggestionRequest) -> BusinessSuggestionResponse:
        """
        生成经营改进建议。

        流程：
        1. 检查数据充足性
        2. 构建 prompt 上下文
        3. 调用 LLM 生成建议
        4. 校验和返回结果
        """
        # ---- 1. 数据充足性预检 ----
        reputation_list = request.reputationTrends or []
        issues_list = request.issueStats or []
        highlights_list = request.highlights or []
        competitors_list = request.competitors or []

        has_reputation = len(reputation_list) > 0
        has_issues = len(issues_list) > 0
        has_highlights = len(highlights_list) > 0
        has_competitors = len(competitors_list) > 0

        available_sources = 0
        if has_reputation:
            available_sources += 1
        if has_issues:
            available_sources += 1
        if has_highlights:
            available_sources += 1
        if has_competitors:
            available_sources += 1

        # 评价太少且无分析数据
        if request.reviewCount < request.minimumReviewCount and available_sources == 0:
            logger.info(
                f"商家 {request.merchantId} 数据不足: "
                f"reviewCount={request.reviewCount}, availableSources={available_sources}"
            )
            return BusinessSuggestionResponse(
                merchantId=request.merchantId,
                version=request.version,
                status="INSUFFICIENT_DATA",
                suggestions=[],
                dataSufficiency="INSUFFICIENT",
                summaryText="当前评价数量和相关分析数据不足，无法生成可靠的经营改进建议。"
                            f"（现有评价 {request.reviewCount} 条，至少需要 {request.minimumReviewCount} 条）",
                businessTraceId=self._generate_trace_id(),
            )

        # ---- 2. 构建 Prompt 上下文 ----
        context_parts = [
            f"## 商家 #{request.merchantId} 经营数据分析",
            f"有效评价总数：{request.reviewCount} 条",
            f"最少需要评价数：{request.minimumReviewCount} 条",
        ]

        # 口碑趋势
        if has_reputation:
            context_parts.append("\n### 口碑趋势数据（月度）")
            for point in request.reputationTrends:
                context_parts.append(
                    f"- {point.get('periodStart', '?')} ~ {point.get('periodEnd', '?')}: "
                    f"平均评分 {point.get('averageRating', 'N/A')}, "
                    f"正面占比 {_format_ratio(point.get('positiveRatio'))}, "
                    f"负面占比 {_format_ratio(point.get('negativeRatio'))}, "
                    f"评价数 {point.get('totalReviewCount', 0)}"
                )

        # 差评归因
        if has_issues:
            context_parts.append("\n### 差评归因分布")
            for issue in request.issueStats:
                context_parts.append(
                    f"- {issue.get('category_name', issue.get('categoryCode', '?'))}: "
                    f"涉及 {issue.get('review_count', 0)} 条评价 "
                    f"（占比 {issue.get('percentage', 0)}%）"
                )

        # 商家亮点
        if has_highlights:
            context_parts.append("\n### 商家亮点")
            for hl in request.highlights:
                context_parts.append(
                    f"- [{hl.get('highlightType', '?')}] {hl.get('title', '')}: "
                    f"{hl.get('description', '')} "
                    f"（提及 {hl.get('mentionCount', 0)} 次，好评率 "
                    f"{_format_ratio(hl.get('positiveRatio'))}）"
                )

        # 竞品数据
        if has_competitors:
            context_parts.append("\n### 周边竞品概况")
            for comp in request.competitors:
                context_parts.append(
                    f"- {comp.get('name', '?')}: "
                    f"评分 {comp.get('rating', 'N/A')}, "
                    f"人均 {comp.get('average_price', 'N/A')} 元, "
                    f"评价数 {comp.get('review_count', 0)}"
                )

        # 数据可用性汇总
        context_parts.append(f"\n### 数据充足性评估")
        context_parts.append(f"可用数据源数量：{available_sources}/4")
        if request.reviewCount < request.minimumReviewCount:
            context_parts.append(
                f"⚠️ 评价数量({request.reviewCount})低于阈值({request.minimumReviewCount})，"
                f"部分建议的置信度应为 LOW"
            )
        context_parts.append(
            f"数据充足的数据源：{'口碑趋势 ' if has_reputation else ''}"
            f"{'差评归因 ' if has_issues else ''}"
            f"{'商家亮点 ' if has_highlights else ''}"
            f"{'竞品数据' if has_competitors else ''}"
        )

        user_message = "\n".join(context_parts)

        # ---- 3. 调用 LLM ----
        logger.info(
            f"生成经营建议 merchantId={request.merchantId}, "
            f"reviewCount={request.reviewCount}, availableSources={available_sources}"
        )

        try:
            result = await llm_service.chat_json(
                system_prompt=BUSINESS_SUGGESTION_PROMPT,
                user_message=user_message,
                temperature=0.3,
                max_tokens=3000,
                request_timeout_seconds=150,
            )
            logger.info(
                f"LLM raw result type={type(result).__name__}, "
                f"keys={list(result.keys()) if isinstance(result, dict) else 'N/A'}"
            )
        except Exception as e:
            import traceback
            logger.error(
                f"经营建议生成LLM调用失败 merchantId={request.merchantId}: "
                f"{type(e).__name__}: {e}\n{traceback.format_exc()}"
            )
            return BusinessSuggestionResponse(
                merchantId=request.merchantId,
                version=request.version,
                status="FAILED",
                suggestions=[],
                errorMessage=f"LLM调用失败: {str(e)}",
                businessTraceId=self._generate_trace_id(),
            )

        # ---- 4. 解析和校验结果 ----
        if not isinstance(result, dict):
            return BusinessSuggestionResponse(
                merchantId=request.merchantId,
                version=request.version,
                status="FAILED",
                suggestions=[],
                errorMessage="LLM返回格式异常",
                businessTraceId=self._generate_trace_id(),
            )

        status = result.get("status", "SUCCESS")

        suggestions_raw = result.get("suggestions", [])
        if not isinstance(suggestions_raw, list):
            suggestions_raw = []

        # 限制最多10条
        if len(suggestions_raw) > 10:
            suggestions_raw = suggestions_raw[:10]

        # 构建响应
        suggestions = []
        for item in suggestions_raw:
            if not isinstance(item, dict):
                continue

            # 跳过没有标题或描述的不完整建议
            if not item.get("title") or not item.get("description"):
                continue

            # 校验 category 合法值
            category = item.get("category", "NEGATIVE_ISSUE")
            valid_categories = [
                "NEGATIVE_ISSUE", "REPUTATION_TREND",
                "HIGHLIGHT_GAP", "COMPETITOR_GAP"
            ]
            if category not in valid_categories:
                category = "NEGATIVE_ISSUE"

            suggestions.append(BusinessSuggestionItem(
                title=str(item.get("title", "")),
                description=str(item.get("description", "")),
                category=category,
                priority=item.get("priority", "MEDIUM"),
                timeframe=item.get("timeframe", "SHORT_TERM"),
                expectedEffect=item.get("expectedEffect"),
                dataBasisType=item.get("dataBasisType"),
                dataBasisSummary=item.get("dataBasisSummary"),
                metricName=item.get("metricName"),
                metricValue=item.get("metricValue"),
                confidence=item.get("confidence", "MEDIUM"),
                evidences=item.get("evidences"),
            ))

        # 低数据量时降低置信度
        if request.reviewCount < request.minimumReviewCount:
            for s in suggestions:
                s.confidence = "LOW"

        logger.info(
            f"经营建议生成完成 merchantId={request.merchantId}, "
            f"suggestionCount={len(suggestions)}, status={status}"
        )

        return BusinessSuggestionResponse(
            merchantId=request.merchantId,
            version=request.version,
            status=status if suggestions else "INSUFFICIENT_DATA",
            suggestions=suggestions,
            summaryText=result.get("summaryText"),
            dataSufficiency=result.get("dataSufficiency", "SUFFICIENT"),
            modelName=result.get("modelName"),
            modelVersion=result.get("modelVersion"),
            promptVersion=result.get("promptVersion", "business-suggestion:v1"),
            businessTraceId=self._generate_trace_id(),
        )


def _format_ratio(value) -> str:
    """将0~1的比例格式化为百分比字符串"""
    if value is None:
        return "N/A"
    try:
        pct = float(value) * 100
        return f"{pct:.1f}%"
    except (ValueError, TypeError):
        return str(value)


# 单例
business_suggestion_service = BusinessSuggestionService()
