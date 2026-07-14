package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("merchant_review_summaries")
public class MerchantReviewSummary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;
    private Integer version;
    private String summaryText;

    /** JSONB — 优点列表 */
    private String advantages;

    /** JSONB — 缺点列表 */
    private String disadvantages;

    /** JSONB — 推荐菜品列表 */
    private String recommendedDishes;

    /** JSONB — 环境摘要 */
    private String environmentSummary;

    /** JSONB — 服务摘要 */
    private String serviceSummary;

    /** JSONB — 近期变化 */
    private String recentChanges;

    private Integer reviewCount;
    private LocalDateTime sourceStartTime;
    private LocalDateTime sourceEndTime;

    /** SUCCESS / INSUFFICIENT_DATA / FAILED */
    private String status;

    private String modelName;
    private String modelVersion;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime generatedAt;
}
