package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 评价辅助回复草稿实体（EPIC-02 故事7：评价辅助回复）
 *
 * 商家用户可以对单条评价请求 AI 生成回复建议。系统根据评价的情感倾向
 * （好评/差评）采用不同的回复策略生成内容。生成的内容以"草稿"形式保存，
 * 商家必须确认或编辑后才能发布为正式回复。
 *
 * 核心业务规则：
 * - 好评策略：表达感谢 + 回应具体优点
 * - 差评策略：道歉 + 问题说明 + 改进承诺，禁止攻击顾客或推卸责任
 * - 生成内容不得自动发布，必须由商家确认
 * - 草稿支持编辑、发布、丢弃三种操作
 */
@Data
@TableName("review_reply_draft")
public class ReviewReplyDraft {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的评价 ID（来自 reviews 表） */
    private Long reviewId;

    /** 关联的商家 ID（来自 merchants 表），用于权限校验 */
    private Long merchantId;

    /** AI 生成的原始回复建议内容 */
    private String generatedContent;

    /** 商家编辑后的回复内容（发布时以此为准，为 null 表示未编辑） */
    private String editedContent;

    /**
     * AI 使用的回复策略：
     * POSITIVE — 好评回复策略（感谢 + 回应优点）
     * NEGATIVE — 差评回复策略（道歉 + 问题说明 + 改进承诺）
     */
    private String strategy;

    /**
     * 草稿状态：
     * DRAFT     — 草稿状态，商家可以编辑、发布或丢弃
     * PUBLISHED — 已发布为正式回复（写入 review_reply 表后更新）
     * DISCARDED — 已丢弃（商家选择不使用该回复建议）
     */
    private String status;

    /** AI 生成回复的时间 */
    private OffsetDateTime generatedAt;

    /** 商家确认发布为正式回复的时间 */
    private OffsetDateTime publishedAt;

    /** 确认发布的商家用户 ID（来自 merchant_members 表） */
    private Long confirmedBy;

    /** AI 服务调用的追踪 ID，用于排查问题和审计 */
    private String aiTraceId;

    /** 生成该回复所使用的 AI 模型名称 */
    private String modelName;

    /** 记录创建时间（由 MyBatis-Plus 自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 记录最后更新时间（由 MyBatis-Plus 自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
