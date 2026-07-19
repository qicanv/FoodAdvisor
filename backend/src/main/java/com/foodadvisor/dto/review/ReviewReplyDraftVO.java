package com.foodadvisor.dto.review;

import com.foodadvisor.entity.ReviewReplyDraft;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 评价辅助回复草稿的展示 VO（EPIC-02 故事7）
 *
 * 用于前端展示 AI 生成的回复建议草稿，供商家用户查看、编辑和确认发布。
 * 包含生成内容和编辑后的内容两个字段，前端可以对比展示。
 */
@Data
public class ReviewReplyDraftVO {

    /** 草稿 ID */
    private Long id;

    /** 关联的评价 ID */
    private Long reviewId;

    /** 关联的商家 ID */
    private Long merchantId;

    /** AI 生成的原始回复建议内容 */
    private String generatedContent;

    /** 商家编辑后的回复内容（为 null 表示未编辑，前端展示 generatedContent） */
    private String editedContent;

    /**
     * 回复策略：
     * POSITIVE — 好评策略
     * NEGATIVE — 差评策略
     */
    private String strategy;

    /**
     * 草稿状态：
     * DRAFT     — 待处理
     * PUBLISHED — 已发布
     * DISCARDED — 已丢弃
     */
    private String status;

    /** AI 生成时间 */
    private OffsetDateTime generatedAt;

    /** 发布时间（已发布时有值） */
    private OffsetDateTime publishedAt;

    /** 确认发布的人员 ID */
    private Long confirmedBy;

    /** AI 调用追踪 ID */
    private String aiTraceId;

    /** 使用的 AI 模型名称 */
    private String modelName;

    /** 获取当前有效的回复内容：优先返回编辑后的内容，否则返回 AI 原始生成 */
    public String getEffectiveContent() {
        return (editedContent != null && !editedContent.isBlank())
                ? editedContent
                : generatedContent;
    }

    /**
     * 从实体对象构建 VO。
     *
     * @param entity 数据库中的 ReviewReplyDraft 实体
     * @return 前端展示用的 VO 对象，如果 entity 为 null 则返回 null
     */
    public static ReviewReplyDraftVO from(ReviewReplyDraft entity) {
        if (entity == null) {
            return null;
        }

        ReviewReplyDraftVO vo = new ReviewReplyDraftVO();
        vo.setId(entity.getId());
        vo.setReviewId(entity.getReviewId());
        vo.setMerchantId(entity.getMerchantId());
        vo.setGeneratedContent(entity.getGeneratedContent());
        vo.setEditedContent(entity.getEditedContent());
        vo.setStrategy(entity.getStrategy());
        vo.setStatus(entity.getStatus());
        vo.setGeneratedAt(entity.getGeneratedAt());
        vo.setPublishedAt(entity.getPublishedAt());
        vo.setConfirmedBy(entity.getConfirmedBy());
        vo.setAiTraceId(entity.getAiTraceId());
        vo.setModelName(entity.getModelName());
        return vo;
    }
}
