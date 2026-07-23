"""
违规文本检测服务

职责:
1. 调用 LLM 对文本内容进行违规分类
2. 识别广告引流、恶意谩骂、虚假宣传、无关灌水等违规类型
3. 返回结构化检测结果（风险类型、等级、分值、命中规则）
4. 确定性输出：相同 content + ruleVersion → 一致结果（temperature=0）

设计原则:
- 使用 LLM 进行语义级别检测，而非简单关键词匹配
- 返回命中的规则列表及原文证据
- 支持 ruleVersion 追踪规则变更
- LLM 调用失败时抛出异常，由调用方处理降级
"""
import logging
import uuid
from datetime import datetime, timezone
from typing import Optional

from app.core.trace_context import current_trace_id
from app.schemas.violation_check import (
    ViolationCheckRequest,
    ViolationCheckResponse,
    MatchedRule,
    RiskTypeEnum,
    RiskLevelEnum,
    DetectionStatusEnum,
)
from app.services.llm_service import llm_service

logger = logging.getLogger(__name__)

# ============================================
# 违规文本检测 System Prompt
# ============================================
VIOLATION_DETECTION_PROMPT = """你是一个内容安全审核专家。你的任务是分析用户提交的餐饮评价文本，判断是否包含违规内容。

## 违规类型定义

### 1. AD_SPAM（广告引流）
- 包含微信号、手机号、QQ号、群号等联系方式
- 引导用户添加好友、加入群聊、私聊
- 推广其他平台、APP、网站链接
- 明显的商品推销、代购、刷单、刷评信息
- 示例："加微信xxx了解更多"、"扫码领优惠"、"加群送福利"

### 2. ABUSE（恶意谩骂）
- 人身攻击、辱骂、诅咒
- 使用侮辱性词汇针对商家、店员或其他用户
- 带有强烈恶意的负面表达（超出正常差评范围）
- 地域歧视、种族歧视、性别歧视等
- 示例："老板是个傻X"、"这家店全是垃圾人"

### 3. FALSE_AD（虚假宣传）
- 明显夸大功效的虚假描述
- 虚构不存在的菜品或服务
- 捏造事实误导其他用户
- 与实际情况严重不符的陈述
- 示例："吃了这家店的菜癌症都好了"、"全宇宙最好吃的火锅"

### 4. SPAM（无关灌水）
- 重复的无意义内容（如"啊啊啊啊"、"111111"）
- 与餐饮评价完全无关的内容
- 纯表情符号或乱码
- 测试内容、占位符
- 明显由机器生成的模板化内容
- 示例："test test test"、"占位占位"、"1111111111"

### 5. OTHER（其他违规）
- 涉及色情、低俗内容
- 涉及暴恐、违法信息
- 泄露他人隐私（电话号码、身份证号、住址等）
- 其他违反平台规则的内容

## 正常评价判断
- 如果是正常的餐饮评价（包括真实的差评），即使语气不好或不满意，也不应判定为违规
- 真实的消费体验描述、合理批评、建设性意见都属于正常评价
- 只有明显超出正常评价范围的恶意内容才需要标记

## 输出格式

你必须返回如下 JSON 对象：

{
  "riskType": null,
  "riskLevel": "LOW",
  "riskScore": 10,
  "matchedRules": [
    {
      "ruleCode": "AD_SPAM_001",
      "ruleName": "包含联系方式推广",
      "riskType": "AD_SPAM",
      "confidence": 0.95,
      "evidenceExcerpt": "加微信xxx了解更多优惠"
    }
  ]
}

### 字段说明
- riskType: 风险类型，取值 AD_SPAM / ABUSE / FALSE_AD / SPAM / OTHER。如果没有任何违规，设为 null
- riskLevel: 风险等级
  - HIGH: 明显违规，需要阻止发布（score >= 70）
  - MEDIUM: 疑似违规，需要人工审核（score 40-69）
  - LOW: 几乎无违规风险（score < 40）
- riskScore: 风险分值 0-100 的整数
  - 0-19: 完全正常
  - 20-39: 基本正常，有轻微疑点
  - 40-69: 中等风险，建议人工复核
  - 70-84: 较高风险，明显违规
  - 85-100: 极高风险，严重违规
- matchedRules: 命中的规则列表。如果没有任何违规，为空数组 []
  - ruleCode: 规则编码，格式为 {RISK_TYPE}_{序号}，如 AD_SPAM_001
  - ruleName: 规则的中文描述
  - riskType: 风险类型（同顶层 riskType）
  - confidence: 该规则的置信度 0~1
  - evidenceExcerpt: 触发该规则的原文片段（不超过80字）

## 注意事项
1. 必须只返回 JSON，不要包含 markdown 代码块、解释或额外文字
2. 如实判断，不要为了"安全"而将所有内容标记为违规
3. 正常的差评（即使是激烈的批评）不应判定为违规
4. 多个规则命中时，在 matchedRules 中全部列出
5. riskScore 应准确反映违规程度：轻微广告可打 45 分，明显谩骂应打 85+ 分
6. evidenceExcerpt 应直接引用原文，不要改写
"""

DEFAULT_RULE_VERSION = "violation-detection:v1"


class ViolationDetectionService:
    """违规文本检测服务"""

    def _generate_trace_id(self) -> str:
        """生成 AI 调用追踪ID"""
        trace_id = current_trace_id()
        if trace_id:
            return trace_id
        return f"violation-{uuid.uuid4().hex[:12]}"

    async def check(
        self,
        request: ViolationCheckRequest,
    ) -> ViolationCheckResponse:
        """
        检测文本违规内容。

        参数:
            request: 检测请求，包含 content 和可选的 ruleVersion

        返回:
            ViolationCheckResponse: 包含风险类型、等级、分值和命中规则

        异常:
            ValueError: content 为空或无效
            RuntimeError: LLM 调用失败
        """
        if not request.content or not request.content.strip():
            raise ValueError("content must not be blank")

        trace_id = self._generate_trace_id()
        rule_version = request.ruleVersion or DEFAULT_RULE_VERSION

        logger.info(
            "违规文本检测开始: content_length=%d, rule_version=%s, trace_id=%s",
            len(request.content),
            rule_version,
            trace_id,
        )

        try:
            result = await llm_service.chat_json(
                system_prompt=VIOLATION_DETECTION_PROMPT,
                user_message=(
                    f"【检测规则版本：{rule_version}】\n\n"
                    f"请检测以下评价文本是否违规：\n\n"
                    f"{request.content}"
                ),
                temperature=0.0,  # 确定性输出
                max_tokens=2000,
            )

            # 解析和验证返回结果
            risk_score = self._clamp_score(result.get("riskScore", 0))
            risk_level = self._resolve_risk_level(
                result.get("riskLevel"), risk_score
            )
            risk_type = self._validate_risk_type(result.get("riskType"))
            matched_rules = self._parse_matched_rules(
                result.get("matchedRules", [])
            )

            logger.info(
                "违规文本检测完成: risk_level=%s, risk_score=%d, "
                "risk_type=%s, matched_rules_count=%d, trace_id=%s",
                risk_level.value,
                risk_score,
                risk_type.value if risk_type else "NONE",
                len(matched_rules),
                trace_id,
            )

            return ViolationCheckResponse(
                riskType=risk_type,
                riskLevel=risk_level,
                riskScore=risk_score,
                matchedRules=matched_rules,
                modelName=llm_service.model,
                businessTraceId=trace_id,
                detectionStatus=DetectionStatusEnum.SUCCESS,
                errorMessage=None,
            )

        except Exception as e:
            logger.error(
                "违规文本检测失败: content_length=%d, error=%s, trace_id=%s",
                len(request.content),
                str(e)[:300],
                trace_id,
                exc_info=True,
            )
            return ViolationCheckResponse(
                riskType=None,
                riskLevel=RiskLevelEnum.LOW,
                riskScore=0,
                matchedRules=[],
                modelName=None,
                businessTraceId=trace_id,
                detectionStatus=DetectionStatusEnum.ERROR,
                errorMessage=str(e)[:500],
            )

    # ============================================
    # 结果解析与验证
    # ============================================

    @staticmethod
    def _clamp_score(raw: object) -> int:
        """将风险分值 clamp 到 0-100 范围"""
        try:
            score = int(float(str(raw)))
            return max(0, min(100, score))
        except (ValueError, TypeError):
            return 0

    @staticmethod
    def _resolve_risk_level(raw_level: object, score: int) -> RiskLevelEnum:
        """
        解析风险等级。
        优先使用 LLM 返回的等级，缺失时根据分值推断。
        """
        if isinstance(raw_level, str):
            upper = raw_level.upper().strip()
            if upper in ("HIGH", "MEDIUM", "LOW"):
                return RiskLevelEnum(upper)

        # 分值推断
        if score >= 70:
            return RiskLevelEnum.HIGH
        elif score >= 40:
            return RiskLevelEnum.MEDIUM
        else:
            return RiskLevelEnum.LOW

    @staticmethod
    def _validate_risk_type(raw: object) -> Optional[RiskTypeEnum]:
        """验证风险类型是否有效"""
        if raw is None:
            return None
        if isinstance(raw, str):
            upper = raw.upper().strip()
            if upper in ("AD_SPAM", "ABUSE", "FALSE_AD", "SPAM", "OTHER"):
                return RiskTypeEnum(upper)
        return None

    @staticmethod
    def _parse_matched_rules(raw: object) -> list[MatchedRule]:
        """解析匹配的规则列表"""
        if not isinstance(raw, list):
            return []

        rules: list[MatchedRule] = []
        for item in raw:
            if not isinstance(item, dict):
                continue
            try:
                rule = MatchedRule(
                    ruleCode=str(item.get("ruleCode", "")),
                    ruleName=str(item.get("ruleName", "")),
                    riskType=RiskTypeEnum(
                        str(item.get("riskType", "OTHER")).upper()
                    ),
                    confidence=min(1.0, max(0.0, float(
                        item.get("confidence", 0.5)
                    ))),
                    evidenceExcerpt=(
                        str(item["evidenceExcerpt"])[:200]
                        if item.get("evidenceExcerpt")
                        else None
                    ),
                )
                rules.append(rule)
            except (ValueError, TypeError, KeyError) as e:
                logger.warning("跳过无效规则条目: %s", e)
                continue

        return rules


# 模块级单例
violation_detection_service = ViolationDetectionService()
