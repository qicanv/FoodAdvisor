package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("import_task_items")
public class ImportTaskItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Integer rowNo;
    private String externalKey;

    /** JSONB — 原始行数据 */
    private String rawData;

    /** SUCCESS / FAILED / DUPLICATE */
    private String status;

    private Long targetId;
    private String errorCode;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
