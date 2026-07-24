package com.foodadvisor.dto.alert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveAlertReviewDTO {

    private Long id;

    private Long alertId;

    private Long reviewId;

    private Integer reviewVersion;

    /** 评价中与敏感话题相关的摘录 */
    private String evidenceExcerpt;

    /** 评价完整内容 */
    private String reviewContent;

    /** 评价评分 */
    private Integer reviewRating;

    /** 评价用户ID */
    private Long reviewUserId;

    /** 评价用户名 */
    private String reviewUsername;

    /** 评价发布时间 */
    private OffsetDateTime reviewCreatedAt;

    private OffsetDateTime createdAt;
}
