package com.foodadvisor.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 某问题类别下的关联评价 VO — 点击类别后查看原始评价
 */
@Data
public class IssueReviewVO {

    private Long reviewId;

    /** 综合评分 1-5 */
    private Integer rating;

    /** 评价正文（前端按需截断） */
    private String content;

    /** 评价发布时间 */
    private OffsetDateTime publishedAt;

    /** 归因置信度 0-1 */
    private BigDecimal confidence;

    /** 原文依据片段（AI 提取的该问题对应原文） */
    private String evidenceText;
}
