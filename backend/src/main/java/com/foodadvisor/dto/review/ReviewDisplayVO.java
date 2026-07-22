package com.foodadvisor.dto.review;

import com.foodadvisor.entity.Review;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
public class ReviewDisplayVO {

    private Long id;
    private Long merchantId;
    private Long userId;
    private String username;
    private String nickname;
    private BigDecimal rating;
    private String content;
    private String status;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;

    /**
     * 该评价关联的追评（追加评价）。
     * 每条原评价最多关联一条追评，没有追评时为 null。
     * 前端通过此字段判断是否需要展示"追加评价"入口和追评内容。
     */
    private ReviewFollowUpVO followUp;

    /**
     * 商家对该评价的回复。
     * 如果商家已回复则为 ReviewReplyVO，否则为 null。
     */
    private ReviewReplyVO merchantReply;

    public static ReviewDisplayVO from(Review review) {
        ReviewDisplayVO vo = new ReviewDisplayVO();
        vo.setId(review.getId());
        vo.setMerchantId(review.getMerchantId());
        vo.setUserId(review.getUserId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setStatus(review.getStatus());
        vo.setPublishedAt(review.getPublishedAt());
        vo.setCreatedAt(review.getCreatedAt());
        return vo;
    }
}