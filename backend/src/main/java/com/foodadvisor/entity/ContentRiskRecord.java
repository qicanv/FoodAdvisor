package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.foodadvisor.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 内容风险检测记录实体
 * 对应 content_risk_records 表
 *
 * <p>记录每次违规文本检测的完整结果，
 * 包括风险类型、等级、分值、命中规则和检测状态。</p>
 *
 * @see docs/数据库设计.md 6.19 节
 */
@Data
@TableName(value = "content_risk_records", autoResultMap = true)
public class ContentRiskRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 内容类型：REVIEW / REVIEW_FOLLOW_UP / CHAT_MESSAGE */
    private String contentType;

    /** 内容主键ID（reviewId / messageId） */
    private Long contentId;

    /** 内容版本号 */
    private Integer contentVersion;

    /** 检测规则版本 */
    private String ruleVersion;

    /** 风险类型：AD_SPAM / ABUSE / FALSE_AD / SPAM / OTHER */
    private String riskType;

    /** 风险等级：LOW / MEDIUM / HIGH */
    private String riskLevel;

    /** 风险分值 0-100 */
    private Integer riskScore;

    /**
     * 命中的规则列表（JSONB）
     * JSON数组：[{ruleCode, ruleName, riskType, confidence, evidenceExcerpt}]
     */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String matchedRules;

    /** 脱敏后的违规文本摘要 */
    private String maskedExcerpt;

    /** 检测状态：SUCCESS / FALLBACK / ERROR / TIMEOUT */
    private String detectionStatus;

    /** 使用的AI模型名称 */
    private String modelName;

    /** 业务追踪ID，关联 ai_call_logs */
    private String businessTraceId;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
