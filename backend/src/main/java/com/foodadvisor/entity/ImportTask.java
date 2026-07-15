package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("import_tasks")
public class ImportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MERCHANT / DISH / REVIEW */
    private String taskType;

    private String originalFilename;
    private String fileHash;

    /** PENDING / PROCESSING / PARTIAL_SUCCESS / SUCCESS / FAILED */
    private String status;

    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private Long createdBy;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
