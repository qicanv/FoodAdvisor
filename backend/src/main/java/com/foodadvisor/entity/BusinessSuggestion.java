package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 经营改进建议实体（EPIC-02 Story 8）
 *
 * 系统结合近期情感趋势、差评归因、商家亮点和竞品对比结果，
 * 为商家生成阶段性的经营改进建议。每项建议关联具体数据依据，
 * 区分短期/长期措施，并支持数据不足时的低置信度标记。
 *
 * 验收准则对齐：
 * - AC-1: 每项建议至少关联一个口碑趋势、差评类别、商家亮点或竞品对比数据
 * - AC-3: 每项建议包含问题对象、改进措施和适用时间范围
 * - AC-4: 建议标记为短期(SHORT_TERM)或长期(LONG_TERM)
 * - AC-5: 数据量低于配置阈值时 confidence=LOW
 */
@Data
@TableName("business_suggestions")
public class BusinessSuggestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商家ID */
    private Long merchantId;

    /** 建议版本号，同商家递增 */
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

    /** 数据依据摘要，如"近30天上菜速度相关差评占比上升12%" */
    private String dataBasisSummary;

    /** 相关指标名称，如"上菜速度差评占比" */
    private String metricName;

    /** 指标数值，如"从15%升至27%" */
    private String metricValue;

    /** 置信度：HIGH / MEDIUM / LOW */
    private String confidence;

    /** 状态：ACTIVE / OUTDATED / DISABLED */
    private String status;

    /** 建议生成时间 */
    private OffsetDateTime generatedAt;
}
