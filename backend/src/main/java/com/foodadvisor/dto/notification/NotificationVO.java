package com.foodadvisor.dto.notification;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class NotificationVO {

    private Long id;

    private Long reviewId;

    private Long merchantId;

    private String type;

    private String title;

    private String reviewSummary;

    private String replySummary;

    private String merchantName;

    private String status;

    private Boolean notified;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}