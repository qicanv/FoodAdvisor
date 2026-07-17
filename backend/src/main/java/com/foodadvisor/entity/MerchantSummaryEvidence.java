package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 评价摘要依据 — 摘要结论与原始评价的关联（验收准则 3）
 */
@Data
@TableName("merchant_summary_evidences")
public class MerchantSummaryEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long summaryId;
    private Long reviewId;

    /** ADVANTAGE / DISADVANTAGE / DISH / ENVIRONMENT / SERVICE / RECENT_CHANGE */
    private String evidenceType;

    private String evidenceExcerpt;

    private OffsetDateTime createdAt;
}
