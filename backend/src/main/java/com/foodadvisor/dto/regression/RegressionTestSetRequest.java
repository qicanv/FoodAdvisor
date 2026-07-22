package com.foodadvisor.dto.regression;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegressionTestSetRequest(

        @NotBlank(message = "测试集名称不能为空")
        @Size(max = 200, message = "测试集名称不能超过200个字符")
        String name,

        String description,

        @NotBlank(message = "测试类型不能为空")
        String testType,

        @NotBlank(message = "数据版本不能为空")
        @Size(max = 100, message = "数据版本不能超过100个字符")
        String dataVersion,

        @NotBlank(message = "测试集状态不能为空")
        String status,

        JsonNode metadata
) {
}