package com.foodadvisor.dto.regression;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单个回归案例执行器返回的统一结果。
 *
 * executionStatus 由外层任务服务负责维护；
 * 执行器只需要返回断言状态、输出、指标和失败原因。
 */
public record RegressionExecutionResult(
        JsonNode actualOutput,
        JsonNode metrics,
        JsonNode failureReasons,

        String assertionStatus,

        String modelName,
        String modelVersion,
        String promptVersion,

        String traceId
) {

    public static RegressionExecutionResult passed(
            JsonNode actualOutput,
            JsonNode metrics,
            JsonNode failureReasons,
            String modelName,
            String modelVersion,
            String promptVersion,
            String traceId
    ) {
        return new RegressionExecutionResult(
                actualOutput,
                metrics,
                failureReasons,
                "PASSED",
                modelName,
                modelVersion,
                promptVersion,
                traceId
        );
    }

    public static RegressionExecutionResult failed(
            JsonNode actualOutput,
            JsonNode metrics,
            JsonNode failureReasons,
            String modelName,
            String modelVersion,
            String promptVersion,
            String traceId
    ) {
        return new RegressionExecutionResult(
                actualOutput,
                metrics,
                failureReasons,
                "FAILED",
                modelName,
                modelVersion,
                promptVersion,
                traceId
        );
    }

    public static RegressionExecutionResult notEvaluated(
            JsonNode actualOutput,
            JsonNode metrics,
            JsonNode failureReasons,
            String modelName,
            String modelVersion,
            String promptVersion,
            String traceId
    ) {
        return new RegressionExecutionResult(
                actualOutput,
                metrics,
                failureReasons,
                "NOT_EVALUATED",
                modelName,
                modelVersion,
                promptVersion,
                traceId
        );
    }
}