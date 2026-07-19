package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("recommendation_evidences")
public class RecommendationEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long recommendationItemId;

    /** MERCHANT / REVIEW / DISH / KNOWLEDGE_CHUNK */
    private String sourceType;

    private Long sourceMerchantId;
    private Long reviewId;
    private Long dishId;
    private String knowledgeDocumentId;
    private String evidenceExcerpt;
    private String sourceTextSnapshot;
    private String conditionKey;
    private BigDecimal relevanceScore;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
