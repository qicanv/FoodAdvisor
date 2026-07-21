package com.foodadvisor.dto.report;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * "我的举报"列表项 VO
 */
@Data
public class MyReportListVO {

    /** 举报记录ID */
    private Long id;

    /** 被举报评价ID */
    private Long reportedReviewId;

    /** 商家ID */
    private Long merchantId;

    /** 商家名称 */
    private String merchantName;

    /** 评价摘要（前80字） */
    private String reviewSummary;

    /** 举报原因 */
    private String reason;

    /** 举报原因中文 */
    private String reasonText;

    /** 补充说明 */
    private String description;

    /** 处理状态：PENDING / RESOLVED / REJECTED */
    private String status;

    /** 处理状态中文 */
    private String statusText;

    /** 处理结果说明 */
    private String resolution;

    /** 举报时间 */
    private OffsetDateTime createdAt;

    /** 处理时间 */
    private OffsetDateTime handledAt;
}
