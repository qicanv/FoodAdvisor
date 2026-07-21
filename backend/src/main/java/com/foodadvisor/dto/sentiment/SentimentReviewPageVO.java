package com.foodadvisor.dto.sentiment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页评论情感分析列表 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentReviewPageVO {

    /** 当前页记录 */
    private List<SentimentReviewItemVO> records;

    /** 总记录数 */
    private Long totalCount;

    /** 总页数 */
    private Integer totalPages;

    /** 当前页码 */
    private Integer page;

    /** 每页大小 */
    private Integer pageSize;
}
