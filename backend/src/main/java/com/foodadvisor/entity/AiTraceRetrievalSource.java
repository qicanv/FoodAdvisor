package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("ai_trace_retrieval_sources")
public class AiTraceRetrievalSource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long stageId;
    private String sourceType;
    private String sourceId;
    private String documentId;
    private String chunkId;
    private Long merchantId;
    private String merchantName;
    private String summary;
    private Integer rankNo;
    private BigDecimal relevanceScore;
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
