package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("recommendations")
public class Recommendation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long sessionId;
    private Long userMessageId;
    private Long assistantMessageId;
    private String requestId;
    private String traceId;
    private String queryText;

    /** JSONB — 解析后的结构化约束 */
    private String parsedConstraints;

    private String replyText;
    private String algorithmVersion;

    /** JSONB — 排序权重快照 */
    private String weightSnapshot;

    private String modelName;
    private String modelVersion;

    /** PENDING / SUCCESS / NO_MATCH / FAILED */
    private String status;

    private Integer resultCount;
    private String errorCode;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private OffsetDateTime completedAt;
}
