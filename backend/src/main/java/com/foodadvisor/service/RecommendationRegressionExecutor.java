package com.foodadvisor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodadvisor.dto.regression.RegressionExecutionResult;
import com.foodadvisor.dto.regression.RegressionVersionSnapshot;
import com.foodadvisor.entity.RegressionTestCase;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.trace.AiTraceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RecommendationRegressionExecutor
        implements RegressionTestCaseExecutor {

    private static final String TEST_TYPE =
            "RECOMMENDATION";

    private static final int DEFAULT_TOP_K = 10;

    private final RecommendationEvaluationRunService evaluationRunService;

    private final RegressionJsonAssertionService assertionService;

    private final ObjectMapper objectMapper;

    public RecommendationRegressionExecutor(
            RecommendationEvaluationRunService evaluationRunService,
            RegressionJsonAssertionService assertionService,
            ObjectMapper objectMapper
    ) {
        this.evaluationRunService = evaluationRunService;
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

        String message =
                requiredText(
                        input,
                        "message"
                );

        int topK =
                topKValue(
                        input,
                        executionOptions
                );

        JsonNode locationSnapshot =
                optionalObject(
                        input.get("locationSnapshot"),
                        "locationSnapshot"
                );

        /*
         * 原有推荐评测会使用期望条件计算条件提取准确率。
         * 通用回归案例未声明 extractedConstraints 时，
         * 传入空对象，只执行推荐逻辑。
         */
        JsonNode expectedConstraints =
                expected.get("extractedConstraints");

        if (expectedConstraints == null
                || expectedConstraints.isNull()) {
            expectedConstraints =
                    objectMapper.createObjectNode();
        } else if (!expectedConstraints.isObject()) {
            throw invalidCasePayload(
                    "expectedOutput.extractedConstraints必须为JSON对象"
            );
        }

        RecommendationEvaluationRunService
                .AdHocRecommendationEvaluation result =
                evaluationRunService.evaluateAdHoc(
                        message,
                        expectedConstraints.toString(),
                        locationSnapshot.toString(),
                        topK
                );

        ObjectNode actual =
                buildActualOutput(result);

        RegressionJsonAssertionService.AssertionResult assertion =
                assertionService.compare(
                        expected,
                        actual
                );

        ObjectNode metrics =
                readJsonObject(
                        result.hardConditionMetrics(),
                        "hardConditionMetrics"
                );

        metrics.put(
                "durationMs",
                result.durationMs()
        );

        metrics.put(
                "returnedCount",
                result.returnedCount()
        );

        metrics.put(
                "exactConstraintMatch",
                result.exactConstraintMatch()
        );

        metrics.put(
                "noResult",
                result.noResult()
        );

        ArrayNode failureReasons =
                mergeFailureReasons(
                        result.failureReasons(),
                        assertion.failureReasons()
                );

        /*
         * 旧推荐评测产生的失败原因也属于回归失败，例如：
         * - 条件提取不匹配；
         * - 缺少位置；
         * - 没有符合硬条件的商家。
         */
        boolean passed =
                assertion.passed()
                        && failureReasons.isEmpty();

        String traceId =
                traceContext == null
                        ? null
                        : traceContext.traceId();

        if (passed) {
            return RegressionExecutionResult.passed(
                    actual,
                    metrics,
                    failureReasons,
                    versionSnapshot.modelName(),
                    versionSnapshot.modelVersion(),
                    versionSnapshot.promptVersion(),
                    traceId
            );
        }

        return RegressionExecutionResult.failed(
                actual,
                metrics,
                failureReasons,
                versionSnapshot.modelName(),
                versionSnapshot.modelVersion(),
                versionSnapshot.promptVersion(),
                traceId
        );
    }

    private ObjectNode buildActualOutput(
            RecommendationEvaluationRunService
                    .AdHocRecommendationEvaluation result
    ) {
        ObjectNode actual =
                objectMapper.createObjectNode();

        actual.set(
                "extractedConstraints",
                readJsonObject(
                        result.extractedConstraints(),
                        "extractedConstraints"
                )
        );

        actual.set(
                "mergedConstraints",
                readJsonObject(
                        result.mergedConstraints(),
                        "mergedConstraints"
                )
        );

        actual.set(
                "recommendations",
                readJsonArray(
                        result.recommendationSnapshot(),
                        "recommendationSnapshot"
                )
        );

        actual.set(
                "hardConditionMetrics",
                readJsonObject(
                        result.hardConditionMetrics(),
                        "hardConditionMetrics"
                )
        );

        actual.put(
                "resultCount",
                result.returnedCount()
        );

        actual.put(
                "exactConstraintMatch",
                result.exactConstraintMatch()
        );

        actual.put(
                "noResult",
                result.noResult()
        );

        ArrayNode merchantIds =
                actual.putArray("merchantIds");

        for (Long merchantId : result.merchantIds()) {
            if (merchantId != null) {
                merchantIds.add(merchantId);
            }
        }

        return actual;
    }

    private ArrayNode mergeFailureReasons(
            String evaluationFailureJson,
            JsonNode assertionFailureReasons
    ) {
        ArrayNode merged =
                objectMapper.createArrayNode();

        ArrayNode evaluationFailures =
                readJsonArray(
                        evaluationFailureJson,
                        "failureReasons"
                );

        for (JsonNode failure : evaluationFailures) {
            merged.add(failure.deepCopy());
        }

        if (assertionFailureReasons != null
                && assertionFailureReasons.isArray()) {
            for (JsonNode failure :
                    assertionFailureReasons) {
                merged.add(failure.deepCopy());
            }
        }

        return merged;
    }

    private int topKValue(
            JsonNode input,
            JsonNode executionOptions
    ) {
        int defaultValue =
                DEFAULT_TOP_K;

        if (executionOptions != null
                && executionOptions.isObject()
                && executionOptions.has("topK")) {
            defaultValue =
                    validTopK(
                            executionOptions.get("topK"),
                            "executionOptions.topK"
                    );
        }

        if (!input.has("topK")
                || input.get("topK").isNull()) {
            return defaultValue;
        }

        return validTopK(
                input.get("topK"),
                "inputPayload.topK"
        );
    }

    private int validTopK(
            JsonNode value,
            String fieldName
    ) {
        if (value == null
                || !value.canConvertToInt()) {
            throw invalidCasePayload(
                    fieldName + "必须为整数"
            );
        }

        int topK =
                value.asInt();

        if (topK < 1 || topK > 20) {
            throw invalidCasePayload(
                    fieldName + "必须在1到20之间"
            );
        }

        return topK;
    }

    private JsonNode optionalObject(
            JsonNode value,
            String fieldName
    ) {
        if (value == null || value.isNull()) {
            return objectMapper.createObjectNode();
        }

        if (!value.isObject()) {
            throw invalidCasePayload(
                    fieldName + "必须为JSON对象"
            );
        }

        return value;
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

    private ObjectNode readJsonObject(
            String rawJson,
            String fieldName
    ) {
        JsonNode value =
                readJson(
                        rawJson,
                        fieldName
                );

        if (!value.isObject()) {
            throw new IllegalStateException(
                    fieldName + "不是JSON对象"
            );
        }

        return (ObjectNode) value;
    }

    private ArrayNode readJsonArray(
            String rawJson,
            String fieldName
    ) {
        JsonNode value =
                readJson(
                        rawJson,
                        fieldName
                );

        if (!value.isArray()) {
            throw new IllegalStateException(
                    fieldName + "不是JSON数组"
            );
        }

        return (ArrayNode) value;
    }

    private JsonNode readJson(
            String rawJson,
            String fieldName
    ) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException(
                    fieldName + "不能为空"
            );
        }

        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    fieldName + "不是合法JSON",
                    exception
            );
        }
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