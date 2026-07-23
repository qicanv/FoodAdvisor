package com.foodadvisor.dto.fraud;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 刷评案例详情 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCaseDetailVO {

    private Long caseId;
    private Long merchantId;
    private String merchantName;
    private String ruleType;
    private String ruleTypeText;
    private String riskLevel;
    private String status;
    private String statusText;

    /** 规则快照 */
    private Object matchedRuleSnapshot;

    /** 检测摘要 */
    private String summary;

    /** 关联的可疑评价列表 */
    private List<RelatedReview> relatedReviews;

    /** 复核历史 */
    private ReviewHistory reviewHistory;

    private OffsetDateTime detectedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedReview {
        private Long reviewId;
        private Long userId;
        private String userNickname;
        private Double rating;
        private String content;
        private String riskLevel;
        private OffsetDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewHistory {
        private Long reviewedBy;
        private String reviewedByName;
        private OffsetDateTime reviewedAt;
        private String reviewConclusion;
        private String reviewConclusionText;
        private String reviewRemark;
    }
}
