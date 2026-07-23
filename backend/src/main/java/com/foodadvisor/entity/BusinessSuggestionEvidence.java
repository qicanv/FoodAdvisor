package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 经营改进建议依据实体（EPIC-02 Story 8）
 *
 * 每条建议至少关联一个数据来源（口碑趋势、差评归因、
 * 商家亮点或竞品对比），用户可点击查看对应统计或原始评论。
 *
 * 验收准则对齐：
 * - AC-2: 每项建议展示对应指标、数量、占比或原评论依据
 * - AC-7: 点击建议依据能够查看对应统计或原始评论
 */
@Data
@TableName("business_suggestion_evidences")
public class BusinessSuggestionEvidence {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联的建议ID */
    private Long suggestionId;

    /**
     * 来源类型：
     * REPUTATION_TREND — 口碑趋势
     * NEGATIVE_ISSUE   — 差评归因
     * HIGHLIGHT        — 商家亮点
     * COMPETITOR       — 竞品对比
     * REVIEW           — 原始评价
     */
    private String sourceType;

    /** 来源数据ID（如 reputation_statistics.id / issue_category.id 等） */
    private Long sourceId;

    /** 关联的评价ID（sourceType=REVIEW 时使用） */
    private Long reviewId;

    /**
     * 指标快照（JSONB），存储生成建议时的指标数据。
     * 示例：
     * {
     *   "metricName": "上菜速度差评占比",
     *   "currentValue": "27%",
     *   "previousValue": "15%",
     *   "changeDirection": "UP",
     *   "periodType": "MONTH",
     *   "periodStart": "2026-06-01",
     *   "periodEnd": "2026-06-30"
     * }
     */
    private String metricSnapshot;

    /** 依据摘要文本 */
    private String evidenceExcerpt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
