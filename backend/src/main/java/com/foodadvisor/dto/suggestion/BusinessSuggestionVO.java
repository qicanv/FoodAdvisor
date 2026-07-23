package com.foodadvisor.dto.suggestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 经营改进建议展示对象（EPIC-02 Story 8）
 *
 * 返回给前端的建议数据，包含问题对象、改进措施、时间范围、
 * 数据依据、置信度等完整信息。
 *
 * 验收准则对齐：
 * - AC-2: 展示对应指标、数量、占比或原评论依据
 * - AC-3: 至少包含问题对象、改进措施和适用时间范围
 * - AC-4: 建议标记为短期或长期
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessSuggestionVO {

    /** 建议ID */
    private Long suggestionId;

    /** 商家ID */
    private Long merchantId;

    /** 建议版本号 */
    private Integer version;

    /** 建议标题，如"优化周末高峰期出餐速度" */
    private String title;

    /** 建议详细描述，含问题分析和具体改进措施 */
    private String description;

    /**
     * 建议类别：
     * REPUTATION_TREND  — 基于口碑趋势
     * NEGATIVE_ISSUE    — 基于差评归因
     * HIGHLIGHT_GAP     — 基于亮点差距
     * COMPETITOR_GAP    — 基于竞品差距
     */
    private String category;

    /** 优先级：HIGH / MEDIUM / LOW */
    private String priority;

    /** 适用时间范围：SHORT_TERM / LONG_TERM */
    private String timeframe;

    /** 预期改进效果描述 */
    private String expectedEffect;

    /** 数据依据类型 */
    private String dataBasisType;

    /** 数据依据摘要 */
    private String dataBasisSummary;

    /** 相关指标名称 */
    private String metricName;

    /** 指标数值 */
    private String metricValue;

    /** 置信度：HIGH / MEDIUM / LOW */
    private String confidence;

    /** 状态：ACTIVE / OUTDATED / DISABLED */
    private String status;

    /** 状态说明消息 */
    private String statusMessage;

    /** 生成时间 */
    private OffsetDateTime generatedAt;

    /** AI 调用追踪 ID */
    private String traceId;

    // ---- 批量生成时的汇总信息（仅列表场景使用） ----

    /** 本次生成的总建议数 */
    private Integer totalSuggestions;

    /** 本次生成的总体状态：SUCCESS / PARTIAL / INSUFFICIENT_DATA / NONE */
    private String generationStatus;

    /** 总体状态说明 */
    private String generationMessage;

    /** 各数据源的可用性汇总 */
    private List<DataSourceStatus> dataSources;

    /**
     * 数据源可用性状态
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSourceStatus {
        /** 数据源类型 */
        private String sourceType;
        /** 是否可用（数据充足） */
        private Boolean available;
        /** 可用数据条数 */
        private Integer dataCount;
        /** 最低要求条数 */
        private Integer minimumRequired;
        /** 说明 */
        private String message;
    }
}
