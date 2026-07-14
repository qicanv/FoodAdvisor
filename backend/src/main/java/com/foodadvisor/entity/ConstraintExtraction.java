package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("constraint_extractions")
public class ConstraintExtraction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;
    private Long messageId;

    /** JSONB — 本轮提取的条件 */
    private String extractedConstraints;

    /** JSONB — 合并后的条件 */
    private String mergedConstraints;

    /** JSONB — 变化的字段 */
    private String changedFields;

    /** JSONB — 冲突字段 */
    private String conflictFields;

    private String modelName;
    private String modelVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
