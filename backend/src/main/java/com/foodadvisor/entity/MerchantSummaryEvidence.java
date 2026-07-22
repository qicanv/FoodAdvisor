package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("merchant_summary_evidences")
public class MerchantSummaryEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long summaryId;
    private Long reviewId;

    @TableField(exist = false)
    private String sourceType;

    @TableField(exist = false)
    private Long sourceMerchantId;

    @TableField(exist = false)
    private Integer reviewVersion;

    private String evidenceType;
    private String evidenceExcerpt;
    private OffsetDateTime createdAt;
}
