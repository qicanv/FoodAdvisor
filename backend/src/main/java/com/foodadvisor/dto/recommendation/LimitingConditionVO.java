package com.foodadvisor.dto.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LimitingConditionVO {

    private String field;

    private String type;

    private Object currentValue;

    private Integer recoveredMerchantCount;

    private String description;
}
