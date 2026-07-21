package com.foodadvisor.dto.report;

import lombok.Data;

/**
 * 提交举报请求
 */
@Data
public class ReviewReportRequest {

    /** 被举报的评价ID */
    private Long reportedReviewId;

    /** 商家ID */
    private Long merchantId;

    /**
     * 举报原因：ADVERTISING / FALSE_REVIEW / MALICIOUS_ATTACK /
     *           SEXUAL_OR_VULGAR / PRIVACY_LEAK / OTHER
     */
    private String reason;

    /** 补充说明（选填，最多500字） */
    private String description;
}
