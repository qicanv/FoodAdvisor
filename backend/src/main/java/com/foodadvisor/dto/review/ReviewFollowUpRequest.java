package com.foodadvisor.dto.review;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 追评（追加评价）请求 DTO。
 *
 * 与首次评价不同，追评只要求正文 + 消费日期，综合评分选填，
 * 不包含分项评分、图片等字段。
 *
 * 对应 Jira 用户故事 EPIC-08 故事2：评价追加（追评）。
 */
@Data
public class ReviewFollowUpRequest {

    /**
     * 追评正文，必填，要求 10-2000 字符。
     */
    private String content;

    /**
     * 本次消费的综合评分，选填，范围 1-5。
     */
    private Integer rating;

    /**
     * 本次消费日期，必填。
     */
    private LocalDate consumptionDate;

    /**
     * 本次消费的人均金额，选填。
     */
    private BigDecimal averageSpend;
}
