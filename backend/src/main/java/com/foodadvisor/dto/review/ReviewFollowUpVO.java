package com.foodadvisor.dto.review;

import com.foodadvisor.entity.Review;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 追评（追加评价）展示 VO。
 *
 * 前端在商家详情页用这个结构在原评价下方渲染追评，
 * 通过 isFollowUp 字段区分追评，视觉上与原评价区分开。
 *
 * 对应 Jira 用户故事 EPIC-08 故事2：评价追加（追评）。
 */
@Data
public class ReviewFollowUpVO {

    /** 追评记录 ID */
    private Long id;

    /** 关联的原评价 ID（parentReviewId） */
    private Long parentReviewId;

    /** 追评综合评分 1-5（选填，未填时为空） */
    private BigDecimal rating;

    /** 追评正文 */
    private String content;

    /** 本次消费的人均金额（选填） */
    private BigDecimal averageSpend;

    /** 本次消费日期 */
    private LocalDate consumptionDate;

    /** 当前版本号 */
    private Integer currentVersion;

    /** 状态：PENDING(待处理) / PUBLISHED / HIDDEN / DELETED */
    private String status;

    /** 审核状态：PENDING / APPROVED / REJECTED */
    private String moderationStatus;

    /** 追评发布时间 */
    private OffsetDateTime publishedAt;

    /** 追评创建时间 */
    private OffsetDateTime createdAt;

    /** 追评最后更新时间 */
    private OffsetDateTime updatedAt;

    /**
     * 固定标识，前端用此字段判断是否为追评。
     * 始终返回 true，方便前端使用 v-if 控制样式。
     */
    private boolean isFollowUp = true;

    // ========== 工厂方法 ==========

    /**
     * 从 Review 实体构建追评 VO。
     *
     * @param review Review 实体（reviewType 必须为 FOLLOW_UP）
     * @return 追评展示 VO，如果 review 为 null 则返回 null
     */
    public static ReviewFollowUpVO from(Review review) {
        if (review == null) {
            return null;
        }

        ReviewFollowUpVO vo = new ReviewFollowUpVO();
        vo.setId(review.getId());
        vo.setParentReviewId(review.getParentReviewId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setAverageSpend(review.getAverageSpend());
        vo.setConsumptionDate(review.getConsumptionDate());
        vo.setCurrentVersion(review.getCurrentVersion());
        vo.setStatus(review.getStatus());
        vo.setModerationStatus(review.getModerationStatus());
        vo.setPublishedAt(review.getPublishedAt() != null ? review.getPublishedAt() : review.getCreatedAt());
        vo.setCreatedAt(review.getCreatedAt());
        vo.setUpdatedAt(review.getUpdatedAt());
        return vo;
    }
}
