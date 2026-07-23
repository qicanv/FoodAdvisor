package com.foodadvisor.dto.recommendation;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class LimitingConditionVO {

    private String field;

    private String type;

    private Object currentValue;

    private Integer recoveredMerchantCount;

    private String description;

    private List<Long> candidateMerchantIds;

    public LimitingConditionVO(
            String field,
            String type,
            Object currentValue,
            Integer recoveredMerchantCount,
            String description
    ) {
        this(field, type, currentValue, recoveredMerchantCount, description,
                List.of());
    }

    public LimitingConditionVO(
            String field,
            String type,
            Object currentValue,
            Integer recoveredMerchantCount,
            String description,
            List<Long> candidateMerchantIds
    ) {
        this.field = field;
        this.type = type;
        this.currentValue = currentValue;
        this.recoveredMerchantCount = recoveredMerchantCount;
        this.description = description;
        this.candidateMerchantIds = candidateMerchantIds == null
                ? List.of() : List.copyOf(candidateMerchantIds);
    }
}
