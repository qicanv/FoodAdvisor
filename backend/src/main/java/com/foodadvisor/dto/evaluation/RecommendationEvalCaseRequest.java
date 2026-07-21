package com.foodadvisor.dto.evaluation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecommendationEvalCaseRequest(

        @NotBlank(message = "案例编号不能为空")
        @Size(max = 100, message = "案例编号不能超过100个字符")
        String caseCode,

        @Size(max = 200, message = "案例名称不能超过200个字符")
        String caseName,

        @NotBlank(message = "测试输入不能为空")
        @Size(max = 2000, message = "测试输入不能超过2000个字符")
        String inputText,

        @NotBlank(message = "期望条件不能为空")
        @Size(max = 20000, message = "期望条件内容过长")
        String expectedConstraints,

        @Size(max = 10000, message = "位置快照内容过长")
        String locationSnapshot,

        @Size(max = 10000, message = "案例标签内容过长")
        String tags,

        @Min(value = 0, message = "排序序号不能小于0")
        Integer sequenceNo,

        Boolean enabled
) {
}