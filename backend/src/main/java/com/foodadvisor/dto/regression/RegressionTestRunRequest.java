package com.foodadvisor.dto.regression;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RegressionTestRunRequest(

        @Size(max = 200, message = "运行名称不能超过200个字符")
        String runName,

        @NotEmpty(message = "至少选择一个测试集")
        List<@NotNull(message = "测试集ID不能为空") Long> testSetIds,

        Long baselineRunId,

        JsonNode executionOptions
) {
}