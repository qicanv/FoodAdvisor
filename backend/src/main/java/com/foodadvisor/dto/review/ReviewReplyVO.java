package com.foodadvisor.dto.review;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ReviewReplyVO {

    private Long id;
    private Long reviewId;
    private Long merchantId;
    private String merchantName;
    private String replyContent;
    private OffsetDateTime replyTime;
    private String status;
}