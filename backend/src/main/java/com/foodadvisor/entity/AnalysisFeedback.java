package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 商家分析结果反馈实体（EPIC-06 Story 5）
 *
 * 商家用户可以对情感分析、关键词提取、差评归因、竞品对比、
 * 经营建议、评价摘要和商家亮点等分析结果标记为准确或不准确，
 * 并填写具体问题说明。
 *
 * 验收准则对齐：
 * - AC-1: 商家用户能够对属于自己店铺的分析结果提交准确或不准确反馈
 * - AC-2: 反馈保存对应 analysisId 和 merchantId
 * - AC-3: 支持填写并保存具体问题说明
 * - AC-7: 同一商家对同一分析记录重复反馈时更新已有记录
 */
@Data
@TableName("analysis_feedback")
public class AnalysisFeedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商家ID */
    private Long merchantId;

    /**
     * 分析类型：
     * SENTIMENT           — 情感分析
     * KEYWORD             — 关键词提取
     * ISSUE_ATTRIBUTION   — 差评归因
     * COMPETITOR          — 竞品对比
     * BUSINESS_SUGGESTION — 经营建议
     * REVIEW_SUMMARY      — 评价摘要
     * HIGHLIGHT           — 商家亮点
     */
    private String analysisType;

    /** 关联的分析记录ID（可为空，表示对某类分析的整体反馈） */
    private Long analysisId;

    /** 反馈类型：ACCURATE / INACCURATE */
    private String feedbackType;

    /** 具体问题说明（选填） */
    private String content;

    /** 提交反馈的商家用户ID */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;
}
