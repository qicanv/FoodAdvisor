package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;
import java.time.OffsetDateTime;

/**
 * 商家评价摘要（EPIC-01 Story 7）
 */
@Data
@TableName(value = "merchant_review_summaries", autoResultMap = true)
public class MerchantReviewSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    /** 摘要版本号，同商家递增，UNIQUE(merchant_id, version) */
    private Integer version;

    private String summaryText;

    /** JSONB — 优点列表 [{name, mentionCount, reviewIds}] */
    @TableField(jdbcType = JdbcType.OTHER)
    private String advantages;

    /** JSONB — 不足列表 */
    @TableField(jdbcType = JdbcType.OTHER)
    private String disadvantages;

    /** JSONB — 推荐菜列表 */
    @TableField(jdbcType = JdbcType.OTHER)
    private String recommendedDishes;

    /** JSONB — 环境小结 {text, reviewIds} */
    @TableField(jdbcType = JdbcType.OTHER)
    private String environmentSummary;

    /** JSONB — 服务小结 {text, reviewIds} */
    @TableField(jdbcType = JdbcType.OTHER)
    private String serviceSummary;

    /** JSONB — 近期变化 [{text, direction, reviewIds}] */
    @TableField(jdbcType = JdbcType.OTHER)
    private String recentChanges;

    /** 生成时使用的有效评论数 */
    private Integer reviewCount;

    private OffsetDateTime sourceStartTime;
    private OffsetDateTime sourceEndTime;

    /** SUCCESS / INSUFFICIENT_DATA / FAILED */
    private String status;

    private String modelName;
    private String modelVersion;
    private String errorMessage;

    private OffsetDateTime generatedAt;
}
