package com.foodadvisor.dto.sentiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个维度的情感分析统计。
 * 例如：服务维度 — 正面 72%，中性 10%，负面 18%
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentDimensionVO {

    /** 维度编码：SERVICE / TASTE / PRICE / ENVIRONMENT */
    private String key;

    /** 维度中文名 */
    private String label;

    /** 正面占比 (0-100) */
    private Double positivePct;

    /** 中性占比 (0-100) */
    private Double neutralPct;

    /** 负面占比 (0-100) */
    private Double negativePct;

    /** 正面评价数 */
    private Integer positiveCount;

    /** 负面评价数 */
    private Integer negativeCount;

    /** 该维度被提及的评价占比 (0-100) */
    private Double coverage;
}
