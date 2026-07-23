package com.foodadvisor.dto.fraud;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 刷评案例列表 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCaseListVO {

    private Long caseId;
    private Long merchantId;
    private String merchantName;
    private String ruleType;
    private String ruleTypeText;
    private String riskLevel;
    private String status;
    private String statusText;

    /** 规则快照（含阈值、时间窗口、实际触发值等） */
    private Object matchedRuleSnapshot;

    /** 关联的可疑评价数量 */
    private int relatedReviewCount;

    private String summary;
    private OffsetDateTime detectedAt;
    private String reviewedByName;
    private OffsetDateTime reviewedAt;
    private String reviewConclusion;
    private String reviewConclusionText;
}
