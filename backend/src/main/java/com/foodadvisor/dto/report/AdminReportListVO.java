package com.foodadvisor.dto.report;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 管理员举报列表项 VO（比用户视图多了举报人信息）
 */
@Data
public class AdminReportListVO {

    private Long id;
    private Long reporterUserId;
    private String reporterUsername;
    private Long reportedReviewId;
    private Long merchantId;
    private String merchantName;

    /** 评价完整内容 */
    private String reviewContent;

    /** 评价评分 */
    private Integer reviewRating;

    /** 评价状态（可能已被删除） */
    private String reviewStatus;

    private String reason;
    private String reasonText;
    private String description;
    private String status;
    private String statusText;
    private String resolution;
    private OffsetDateTime createdAt;
    private OffsetDateTime handledAt;
}
