"""
评价辅助回复生成服务（EPIC-02 故事7）

根据评价内容和情感倾向，使用不同的回复策略生成有针对性的回复建议。
好评策略：表达感谢 + 回应具体优点
差评策略：道歉 + 问题说明 + 合理的改进承诺

核心规则：
- 回复必须回应评价中的具体菜品、服务或问题点（验收准则2）
- 不包含辱骂、攻击、完整联系方式或敏感隐私（验收准则5）
- 差评回复不能攻击顾客或推卸责任
"""
import logging
import uuid
from app.core.trace_context import current_trace_id
from app.core.config import settings
from app.models.schemas import (
    GenerateReplyRequest,
    GenerateReplyResponse,
    ReplyStrategyEnum,
)
from app.services.llm_service import LLMService

logger = logging.getLogger(__name__)

# ---- 好评回复的 System Prompt ----
POSITIVE_SYSTEM_PROMPT = """你是一家餐饮商家的客服助手，你的任务是根据顾客的正面评价生成真诚、个性化的回复建议。

## 回复要求
1. **表达感谢**：真诚感谢顾客的好评和支持
2. **回应具体优点**：至少提及评价中的1个具体菜品、服务或环境细节，说明你注意到了顾客的反馈
3. **保持热情**：语气亲切但不夸张，体现商家对顾客体验的重视
4. **适度邀约**：可以温和邀请顾客再次光临

## 严格禁止
- 禁止使用"亲""宝"等过度亲昵的称呼
- 禁止包含完整的手机号、邮箱、地址等联系方式
- 禁止攻击、贬低或嘲讽顾客
- 禁止编造评价中不存在的信息（如"我们的招牌菜XXX"若评价未提及）
- 禁止超过200字，保持简洁有力

## 回复格式
请直接返回纯文本回复内容，不要包含任何前缀说明或quote标记。"""

# ---- 差评回复的 System Prompt ----
NEGATIVE_SYSTEM_PROMPT = """你是一家餐饮商家的客服助手，你的任务是根据顾客的负面评价生成诚恳、有建设性的回复建议。

## 回复要求
1. **诚恳道歉**：首先对顾客不好的体验表示歉意，语气真诚不作假
2. **回应具体问题**：至少提及评价中的1个具体问题点，表明你已经认真阅读了顾客的反馈
3. **合理解释**：对问题给出简要、合理的说明（不是借口），体现商家的重视
4. **改进承诺**：提出具体、可执行的改进措施，让顾客感受到诚意
5. **提供联系方式（可选择）**：可在结尾提供商家客服的一般性联系渠道（如"如有需要可致电门店"），**绝对不要写具体电话号码**

## 严格禁止
- 禁止攻击、指责或嘲讽顾客（如"您太挑剔了""别人都没问题"）
- 禁止推卸责任给顾客、员工或其他第三方
- 禁止包含完整的手机号、邮箱、具体地址等个人联系方式
- 禁止使用威胁性语言或暗示报复
- 禁止编造评价中不存在的事实
- 禁止超过250字

## 回复格式
请直接返回纯文本回复内容，不要包含任何前缀说明或quote标记。"""


class ReplyDraftService:
    """评价辅助回复生成服务"""

    def __init__(self):
        self.llm = LLMService()

    async def generate(self, request: GenerateReplyRequest) -> GenerateReplyResponse:
        """
        根据评价内容和策略生成回复建议。

        Args:
            request: 包含评价内容、评分和策略的请求

        Returns:
            包含生成回复内容的响应
        """
        trace_id = current_trace_id()

        try:
            # 选择对应的 System Prompt
            if request.strategy == ReplyStrategyEnum.POSITIVE:
                system_prompt = POSITIVE_SYSTEM_PROMPT
                prompt_version = "review-reply-positive:v1"
            else:
                system_prompt = NEGATIVE_SYSTEM_PROMPT
                prompt_version = "review-reply-negative:v1"

            # 构建 user message，包含评价信息
            user_message = self._build_user_message(request)

            logger.info(
                "生成回复建议: reviewId=%d, strategy=%s, rating=%d, traceId=%s",
                request.reviewId,
                request.strategy.value,
                request.rating,
                trace_id,
            )

            # 调用 LLM 生成回复
            reply_content = await self.llm.chat(
                system_prompt=system_prompt,
                user_message=user_message,
                temperature=0.7,  # 适度提高温度，让回复更自然
                max_tokens=500,    # 回复不需要太长
            )

            # 清理可能的格式问题
            reply_content = self._clean_reply(reply_content)

            logger.info(
                "回复生成成功: reviewId=%d, length=%d, traceId=%s",
                request.reviewId,
                len(reply_content),
                trace_id,
            )

            return GenerateReplyResponse(
                reviewId=request.reviewId,
                replyContent=reply_content,
                strategy=request.strategy,
                modelName=self.llm.model,
                promptVersion=prompt_version,
                businessTraceId=trace_id,
                status="SUCCESS",
            )

        except Exception as e:
            logger.error(
                "回复生成失败: reviewId=%d, traceId=%s, error=%s",
                request.reviewId,
                trace_id,
                str(e),
            )
            return GenerateReplyResponse(
                reviewId=request.reviewId,
                replyContent="",
                strategy=request.strategy,
                promptVersion=prompt_version,
                businessTraceId=trace_id,
                status="FAILED",
                errorMessage=f"AI 回复生成失败: {str(e)}",
            )

    def _build_user_message(self, request: GenerateReplyRequest) -> str:
        """
        构建发送给 LLM 的用户消息。

        包含评价内容、评分等信息，让模型能针对性地生成回复。
        """
        rating_stars = "⭐" * request.rating
        return f"""请为以下顾客评价生成回复建议：

【顾客评分】{request.rating}/5分 {rating_stars}

【评价内容】
{request.content}

请严格按照系统提示的要求生成回复。"""

    def _clean_reply(self, content: str) -> str:
        """
        清理 LLM 返回的回复内容。

        处理常见的格式问题：
        - 去除首尾空白
        - 去除可能的 markdown 引用标记
        - 去除可能的 "回复：" 前缀
        """
        content = content.strip()

        # 去除常见的 markdown 引用前缀
        if content.startswith("> "):
            content = content[2:]

        # 去除可能的角色前缀
        prefixes_to_strip = [
            "回复：", "回复:", "商家回复：", "商家回复:",
            "店家回复：", "店家回复:", "客服回复：", "客服回复:",
            "回复内容：", "回复内容:",
        ]
        for prefix in prefixes_to_strip:
            if content.startswith(prefix):
                content = content[len(prefix):].strip()
                break

        return content


# 模块级单例
reply_draft_service = ReplyDraftService()
