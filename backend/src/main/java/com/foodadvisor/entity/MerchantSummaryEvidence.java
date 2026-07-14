package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("merchant_summary_evidences")
public class MerchantSummaryEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long summaryId;
    private Long reviewId;
    private String evidenceType;
    private String evidenceExcerpt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
