package com.foodadvisor.dto.evaluation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RecommendationEvalRunRequest(

        @Min(value = 1, message = "topK不能小于1")
        @Max(value = 20, message = "topK不能超过20")
        Integer topK
) {
}