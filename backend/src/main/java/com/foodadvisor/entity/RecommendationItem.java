package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("recommendation_items")
public class RecommendationItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recommendationId;
    private Long merchantId;
    private Integer rankNo;
    private BigDecimal score;

    /** JSONB — 评分明细 */
    private String scoreDetails;

    /** JSONB — 匹配的条件 */
    private String matchedConditions;

    /** JSONB — 不匹配的条件 */
    private String unmatchedConditions;

    private String reason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
