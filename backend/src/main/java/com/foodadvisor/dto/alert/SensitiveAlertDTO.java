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
public class SensitiveAlertDTO {

    private Long id;

    private Long merchantId;

    private String merchantName;

    private String topicType;

    /** 话题类型中文名 */
    private String topicTypeName;

    private String riskLevel;

    /** 风险等级中文名 */
    private String riskLevelName;

    private Integer reviewCount;

    private List<String> keywords;

    private OffsetDateTime firstOccurredAt;

    private OffsetDateTime lastOccurredAt;

    private String status;

    /** 状态中文名 */
    private String statusName;

    private String handledUsername;

    private OffsetDateTime handledAt;

    private String remark;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
