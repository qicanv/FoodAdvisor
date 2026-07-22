package com.foodadvisor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodadvisor.dto.regression.RegressionExecutionResult;
import com.foodadvisor.dto.regression.RegressionVersionSnapshot;
import com.foodadvisor.entity.RegressionTestCase;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.exception.RegressionCaseExecutionException;
import com.foodadvisor.trace.AiTraceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SentimentAnalysisRegressionExecutor
        implements RegressionTestCaseExecutor {

    private static final String TEST_TYPE =
            "SENTIMENT_ANALYSIS";

    private static final int DEFAULT_REVIEW_VERSION = 1;

    private final AIClientService aiClientService;

    private final RegressionJsonAssertionService assertionService;

    private final ObjectMapper objectMapper;

    public SentimentAnalysisRegressionExecutor(
            AIClientService aiClientService,
            RegressionJsonAssertionService assertionService,
            ObjectMapper objectMapper
    ) {
        this.aiClientService = aiClientService;
        this.assertionService = assertionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportedTestType() {
        return TEST_TYPE;
    }

    @Override
    public RegressionExecutionResult execute(
            RegressionTestCase testCase,
            RegressionVersionSnapshot versionSnapshot,
            JsonNode executionOptions,
            AiTraceContext traceContext
    ) {
        JsonNode input =
                readStoredObject(
                        testCase.getInputPayload(),
                        "inputPayload"
                );

        JsonNode expected =
                readStoredObject(
                        testCase.getExpectedOutput(),
                        "expectedOutput"
                );

        Long reviewId =
                requiredPositiveLong(
                        input,
                        "reviewId"
                );

        Long merchantId =
                requiredPositiveLong(
                        input,
                        "merchantId"
                );

        int reviewVersion =
                positiveIntOrDefault(
                        input.get("reviewVersion"),
                        DEFAULT_REVIEW_VERSION,
                        "reviewVersion"
                );

        String content =
                requiredText(
                        input,
                        "content"
                );

        JsonNode actual;

        /*
         * 编排器正常情况下会传入 traceContext。
         * 保留无追踪上下文的调用方式，便于独立测试执行器。
         */
        if (traceContext == null) {
            actual =
                    aiClientService.analyzeReview(
                            reviewId,
                            merchantId,
                            content,
                            reviewVersion
                    );
        } else {
            actual =
                    aiClientService.analyzeReview(
                            reviewId,
                            merchantId,
                            content,
                            reviewVersion,
                            traceContext
                    );
        }

        validateResponseObject(actual);

        String modelName =
                runtimeTextOrSnapshot(
                        actual,
                        "modelName",
                        versionSnapshot.modelName()
                );

        String modelVersion =
                runtimeTextOrSnapshot(
                        actual,
                        "modelVersion",
                        versionSnapshot.modelVersion()
                );

        /*
         * AI 服务 local 模式会明确返回 promptVersion=null，
         * 表示没有实际使用提示词。
         *
         * 字段明确存在但为 null 时必须保留 null，
         * 不能错误回填运行开始时的提示词快照。
         */
        String promptVersion =
                runtimeTextOrSnapshot(
                        actual,
                        "promptVersion",
                        versionSnapshot.promptVersion()
                );

        String traceId =
                resolveTraceId(
                        actual,
                        traceContext
                );

        String responseStatus =
                textFieldOrDefault(
                        actual,
                        "status",
                        "SUCCESS"
                );

        /*
         * AI 服务内部异常可能仍返回 HTTP 200，
         * 但 status=FAILED。
         *
         * 这种情况属于执行失败，而不是断言失败。
         */
        if (!"SUCCESS".equalsIgnoreCase(responseStatus)) {
            String errorMessage =
                    textFieldOrDefault(
                            actual,
                            "errorMessage",
                            "AI情感分析未成功完成，status="
                                    + responseStatus
                    );

            throw new RegressionCaseExecutionException(
                    errorMessage,
                    actual,
                    modelName,
                    modelVersion,
                    promptVersion,
                    traceId
            );
        }

        RegressionJsonAssertionService.AssertionResult assertion =
                assertionService.compare(
                        expected,
                        actual
                );

        ObjectNode metrics =
                assertion.metrics().deepCopy();

        metrics.put(
                "reviewVersion",
                actual.path("reviewVersion")
                        .asInt(reviewVersion)
        );

        metrics.put(
                "analysisVersion",
                actual.path("analysisVersion")
                        .asInt(1)
        );

        if (actual.has("confidence")
                && actual.get("confidence").isNumber()) {
            metrics.put(
                    "confidence",
                    actual.get("confidence")
                            .decimalValue()
            );
        }

        metrics.put(
                "lowConfidence",
                actual.path("lowConfidence")
                        .asBoolean(false)
        );

        metrics.put(
                "keywordCount",
                arraySize(actual, "keywords")
        );

        metrics.put(
                "aspectCount",
                arraySize(actual, "aspects")
        );

        metrics.put(
                "tagCount",
                arraySize(actual, "tags")
        );

        metrics.put(
                "issueCategoryCount",
                arraySize(
                        actual,
                        "issueCategories"
                )
        );

        if (assertion.passed()) {
            return RegressionExecutionResult.passed(
                    actual,
                    metrics,
                    assertion.failureReasons(),
                    modelName,
                    modelVersion,
                    promptVersion,
                    traceId
            );
        }

        return RegressionExecutionResult.failed(
                actual,
                metrics,
                assertion.failureReasons(),
                modelName,
                modelVersion,
                promptVersion,
                traceId
        );
    }

    private void validateResponseObject(
            JsonNode actual
    ) {
        if (actual == null
                || actual.isNull()
                || !actual.isObject()) {
            throw new RegressionCaseExecutionException(
                    "AI情感分析响应必须为JSON对象",
                    actual,
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    private JsonNode readStoredObject(
            String rawJson,
            String fieldName
    ) {
        if (rawJson == null || rawJson.isBlank()) {
            throw invalidCasePayload(
                    fieldName + "不能为空"
            );
        }

        try {
            JsonNode node =
                    objectMapper.readTree(rawJson);

            if (!node.isObject()) {
                throw invalidCasePayload(
                        fieldName + "必须为JSON对象"
                );
            }

            return node;
        } catch (JsonProcessingException exception) {
            throw invalidCasePayload(
                    fieldName + "不是合法JSON"
            );
        }
    }

    private Long requiredPositiveLong(
            JsonNode input,
            String fieldName
    ) {
        JsonNode value =
                input.get(fieldName);

        if (value == null
                || !value.canConvertToLong()
                || value.asLong() <= 0) {
            throw invalidCasePayload(
                    fieldName + "必须为正整数"
            );
        }

        return value.asLong();
    }

    private int positiveIntOrDefault(
            JsonNode value,
            int defaultValue,
            String fieldName
    ) {
        if (value == null || value.isNull()) {
            return defaultValue;
        }

        if (!value.canConvertToInt()
                || value.asInt() <= 0) {
            throw invalidCasePayload(
                    fieldName + "必须为正整数"
            );
        }

        return value.asInt();
    }

    private String requiredText(
            JsonNode input,
            String fieldName
    ) {
        JsonNode value =
                input.get(fieldName);

        if (value == null
                || !value.isTextual()
                || value.asText().isBlank()) {
            throw invalidCasePayload(
                    fieldName + "不能为空"
            );
        }

        return value.asText().trim();
    }

    /**
     * 字段不存在时使用任务启动时的版本快照；
     * 字段明确存在但为 null 时保留 null。
     */
    private String runtimeTextOrSnapshot(
            JsonNode actual,
            String fieldName,
            String snapshotValue
    ) {
        if (!actual.has(fieldName)) {
            return snapshotValue;
        }

        JsonNode value =
                actual.get(fieldName);

        if (value == null
                || value.isNull()
                || !value.isTextual()
                || value.asText().isBlank()) {
            return null;
        }

        return value.asText().trim();
    }

    private String textFieldOrDefault(
            JsonNode source,
            String fieldName,
            String defaultValue
    ) {
        JsonNode value =
                source.get(fieldName);

        if (value == null
                || value.isNull()
                || !value.isTextual()
                || value.asText().isBlank()) {
            return defaultValue;
        }

        return value.asText().trim();
    }

    private String resolveTraceId(
            JsonNode actual,
            AiTraceContext traceContext
    ) {
        if (traceContext != null
                && traceContext.traceId() != null
                && !traceContext.traceId().isBlank()) {
            return traceContext.traceId();
        }

        return runtimeTextOrSnapshot(
                actual,
                "businessTraceId",
                null
        );
    }

    private int arraySize(
            JsonNode source,
            String fieldName
    ) {
        JsonNode value =
                source.get(fieldName);

        return value != null && value.isArray()
                ? value.size()
                : 0;
    }

    private ApiException invalidCasePayload(
            String message
    ) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REGRESSION_CASE_PAYLOAD",
                message
        );
    }
}