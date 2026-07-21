package com.foodadvisor.dto.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 人工标注单个评测案例结果。
 */
public record RecommendationEvalAnnotationRequest(

        @NotBlank(message = "relevanceLabel不能为空")
        @Pattern(
                regexp = "RELEVANT|PARTIALLY_RELEVANT|IRRELEVANT",
                message = "relevanceLabel只能是RELEVANT、PARTIALLY_RELEVANT或IRRELEVANT"
        )
        String relevanceLabel,

        @Size(
                max = 1000,
                message = "annotationNote长度不能超过1000个字符"
        )
        String annotationNote
) {
}