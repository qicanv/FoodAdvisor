package com.foodadvisor.dto.regression;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegressionTestCaseRequest(

        @NotBlank(message = "案例编号不能为空")
        @Size(max = 100, message = "案例编号不能超过100个字符")
        String caseCode,

        @Size(max = 200, message = "案例名称不能超过200个字符")
        String caseName,

        String description,

        @NotNull(message = "案例输入不能为空")
        JsonNode inputPayload,

        @NotNull(message = "期望输出不能为空")
        JsonNode expectedOutput,

        JsonNode assertionConfig,

        JsonNode tags,

        @Min(value = 0, message = "排序号不能小于0")
        Integer sequenceNo,

        Boolean enabled
) {
}