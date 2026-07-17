package com.foodadvisor.dto.summary;

import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 摘要依据 VO — 供"查看依据"溯源使用 */
@Data
public class SummaryEvidenceVO {
    private Long reviewId;
    private String evidenceType;
    private String evidenceExcerpt;

    /** 原评价是否仍然可见（被删除/隐藏时为 false，不返回正文） */
    private Boolean reviewAvailable;
    private BigDecimal rating;
    private String reviewContent;
    private OffsetDateTime publishedAt;
}