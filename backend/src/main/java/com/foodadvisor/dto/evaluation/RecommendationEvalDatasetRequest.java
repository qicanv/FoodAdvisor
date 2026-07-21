package com.foodadvisor.dto.evaluation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecommendationEvalDatasetRequest(

        @NotBlank(message = "测试集名称不能为空")
        @Size(max = 200, message = "测试集名称不能超过200个字符")
        String name,

        @Size(max = 2000, message = "测试集描述不能超过2000个字符")
        String description,

        @Size(max = 100, message = "数据版本不能超过100个字符")
        String dataVersion,

        @Size(max = 30, message = "测试集状态不能超过30个字符")
        String status
) {
}