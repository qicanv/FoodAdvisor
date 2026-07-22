package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * OpenSearch 同步任务
 * 对应数据库表 opensearch_sync_tasks
 */
@Data
@TableName("opensearch_sync_tasks")
public class OpenSearchSyncTask {

    /** 同步状态常量 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    /** 操作类型常量 */
    public static final String OP_UPSERT = "UPSERT";
    public static final String OP_DISABLE = "DISABLE";
    public static final String OP_DELETE = "DELETE";
    public static final String OP_REINDEX = "REINDEX";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源类型：MERCHANT / DISH / REVIEW / TOPIC */
    private String sourceType;

    /** 来源主键ID */
    private Long sourceId;

    /** 操作类型：UPSERT / DISABLE / DELETE / REINDEX */
    private String operationType;

    /** 内容版本号 */
    private Integer contentVersion;

    /** 同步状态：PENDING / PROCESSING / SUCCESS / FAILED */
    private String status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 下次重试时间 */
    private OffsetDateTime nextRetryAt;

    /** 错误信息 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
