package com.foodadvisor.exception;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单个回归案例执行失败。
 *
 * 与普通断言失败不同，该异常表示：
 * - AI 服务明确返回失败状态；
 * - 或执行器已经获得部分结果，但任务未正常完成。
 *
 * 外层任务编排服务捕获后，应保存 actualOutput 和错误原因，
 * 并将 executionStatus 标记为 FAILED。
 */
public class RegressionCaseExecutionException
        extends RuntimeException {

    private final JsonNode actualOutput;

    private final String modelName;

    private final String modelVersion;

    private final String promptVersion;

    private final String traceId;

    public RegressionCaseExecutionException(
            String message,
            JsonNode actualOutput,
            String modelName,
            String modelVersion,
            String promptVersion,
            String traceId
    ) {
        super(message);

        this.actualOutput =
                actualOutput == null
                        ? null
                        : actualOutput.deepCopy();

        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.traceId = traceId;
    }

    public JsonNode getActualOutput() {
        return actualOutput;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public String getTraceId() {
        return traceId;
    }
}