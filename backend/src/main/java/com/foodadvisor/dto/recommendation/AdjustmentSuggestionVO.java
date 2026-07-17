package com.foodadvisor.dto.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentSuggestionVO {

    private String id;

    private String type;

    private String field;

    private Object currentValue;

    private Object suggestedValue;

    private String displayText;

    private String reason;
}
