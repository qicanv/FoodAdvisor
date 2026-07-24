package com.foodadvisor.dto.alert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveAlertDetailDTO {

    private Long id;

    private Long merchantId;

    private String merchantName;

    private String topicType;

    private String topicTypeName;

    private String riskLevel;

    private String riskLevelName;

    private Integer reviewCount;

    private List<String> keywords;

    private OffsetDateTime firstOccurredAt;

    private OffsetDateTime lastOccurredAt;

    private String status;

    private String statusName;

    private String handledUsername;

    private OffsetDateTime handledAt;

    private String remark;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 关联的原始评价列表 */
    private List<SensitiveAlertReviewDTO> reviews;
}
