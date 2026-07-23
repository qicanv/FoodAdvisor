package com.foodadvisor.dto.suggestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 经营改进建议依据展示对象（EPIC-02 Story 8）
 *
 * 用户点击建议依据时，返回对应的统计数据或原始评论内容。
 *
 * 验收准则对齐：
 * - AC-2: 展示对应指标、数量、占比或原评论依据
 * - AC-7: 点击建议依据能够查看对应统计或原始评论
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessSuggestionEvidenceVO {

    /** 依据ID */
    private Long evidenceId;

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

    /** 来源数据ID */
    private Long sourceId;

    /** 关联的评价ID */
    private Long reviewId;

    /** 依据摘要文本 */
    private String evidenceExcerpt;

    // ---- 指标快照（来自 metric_snapshot JSONB） ----

    /** 指标名称 */
    private String metricName;

    /** 当前值 */
    private String currentValue;

    /** 上一周期值 */
    private String previousValue;

    /** 变化方向：UP / DOWN / STABLE */
    private String changeDirection;

    /** 统计周期类型：DAY / WEEK / MONTH */
    private String periodType;

    /** 统计周期起始日期 */
    private String periodStart;

    /** 统计周期结束日期 */
    private String periodEnd;

    // ---- 原始评论信息（sourceType=REVIEW 时填充） ----

    /** 评价是否可用（未被删除/隐藏） */
    private Boolean reviewAvailable;

    /** 评价评分 */
    private Integer rating;

    /** 评价内容（可用时返回） */
    private String reviewContent;

    /** 评价发布时间 */
    private OffsetDateTime publishedAt;

    /** 不可用原因（reviewAvailable=false 时） */
    private String unavailableReason;
}
