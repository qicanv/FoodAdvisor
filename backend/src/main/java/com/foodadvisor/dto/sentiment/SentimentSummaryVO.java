package com.foodadvisor.dto.sentiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 商家评价情感分析汇总 VO。
 *
 * 提供给商家端"评论情感分析"页面的顶部概览 + 图表数据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentSummaryVO {

    /** 时间段内评价总数 */
    private Integer totalReviews;

    /** 已完成情感分析的评价数 */
    private Integer totalAnalyzed;

    /** 好评率 (0-100) */
    private Double positiveRate;

    /** 差评率 (0-100) */
    private Double negativeRate;

    /** 好评率变化趋势（百分点） */
    private Double positiveTrend;

    /** 差评率变化趋势（百分点） */
    private Double negativeTrend;

    /** 主要差评维度中文名，如"上菜速度" */
    private String topComplaintDimension;

    /** 主要差评维度提及次数 */
    private Integer topComplaintCount;

    /** 情感分布：{POSITIVE: {count, percentage}, NEGATIVE: {...}, ...} */
    private Map<String, SentimentCountVO> sentimentDistribution;

    /** 各维度分析数据 */
    private Map<String, SentimentDimensionVO> dimensions;

    /** 好评关键词 Top-N */
    private List<SentimentKeywordVO> positiveKeywords;

    /** 差评问题归类 */
    private List<SentimentIssueVO> complaintIssues;

    /** AI 口碑摘要（复用已有的摘要接口），无数据时 null */
    private Object aiSummary;

    /** 数据更新时间 */
    private String updateTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentCountVO {
        private Integer count;
        private Double percentage;
    }
}
