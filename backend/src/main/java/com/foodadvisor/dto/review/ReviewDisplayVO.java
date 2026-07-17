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