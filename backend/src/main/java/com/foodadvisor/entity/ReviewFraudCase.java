package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 刷评检测案例实体
 * 一次检测扫描发现的某商家下的一组可疑评价
 */
@Data
@TableName(value = "review_fraud_cases", autoResultMap = true)
public class ReviewFraudCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商家ID */
    private Long merchantId;

    /** 触发规则类型: CONCENTRATION / SIMILARITY / FREQUENCY / RATING_ANOMALY */
    private String ruleType;

    /** 风险等级: LOW / MEDIUM / HIGH */
    private String riskLevel;

    /** 状态: SUSPICIOUS / PENDING_REVIEW / REVIEWED / DISMISSED */
    private String status;

    /** 触发时的规则配置快照（含阈值、时间窗口、实际值等） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String matchedRuleSnapshot;

    /** 关联的可疑评价ID列表，JSON数组 [101, 102, 103] */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String matchedReviewIds;

    /** 检测摘要描述 */
    private String summary;

    /** 检测时间 */
    private OffsetDateTime detectedAt;

    /** 复核人userId */
    private Long reviewedBy;

    /** 复核时间 */
    private OffsetDateTime reviewedAt;

    /** 复核结论: CONFIRMED_FRAUD / DISMISSED / NEED_FURTHER_CHECK */
    private String reviewConclusion;

    /** 复核备注 */
    private String reviewRemark;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
