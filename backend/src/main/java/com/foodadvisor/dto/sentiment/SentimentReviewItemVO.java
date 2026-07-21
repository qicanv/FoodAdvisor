package com.foodadvisor.dto.sentiment;

import com.foodadvisor.dto.ReviewAnalysisResultVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评论情感分析列表项 VO — 评论明细表中的单行数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentReviewItemVO {

    private Long reviewId;

    private Long merchantId;

    /** 综合评分 1-5 */
    private Integer rating;

    /** 评价内容 */
    private String content;

    /** 评价时间 */
    private String reviewTime;

    /** 整体情感：POSITIVE / NEGATIVE / NEUTRAL / MIXED */
    private String sentiment;

    /** 置信度 0-1 */
    private Double confidence;

    /** 关键词列表 */
    private List<String> keywords;

    /** 方面级情感 */
    private List<ReviewAnalysisResultVO.AspectVO> aspects;

    /** 差评归因 */
    private List<ReviewAnalysisResultVO.IssueCategoryVO> issueCategories;

    /** AI 追踪 ID */
    private String businessTraceId;
}
