package com.foodadvisor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodadvisor.dto.regression.RegressionExecutionResult;
import com.foodadvisor.dto.regression.RegressionVersionSnapshot;
import com.foodadvisor.entity.RegressionTestCase;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.trace.AiTraceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReviewSummaryRegressionExecutor
        implements RegressionTestCaseExecutor {

    private static final String TEST_TYPE =
            "REVIEW_SUMMARY";

    private static final int DEFAULT_VERSION = 1;
    private static final int DEFAULT_MINIMUM_REVIEW_COUNT = 5;

    private final AIClientService aiClientService;
    private final RegressionJsonAssertionService assertionService;
    private final ObjectMapper objectMapper;

    public ReviewSummaryRegressionExecutor(
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

        Long merchantId =
                requiredPositiveLong(
                        input,
                        "merchantId"
                );

        int version =
                positiveIntOrDefault(
                        input.get("version"),
                        DEFAULT_VERSION,
                        "version"
                );

        int minimumReviewCount =
                positiveIntOrDefault(
                        input.get("minimumReviewCount"),
                        DEFAULT_MINIMUM_REVIEW_COUNT,
                        "minimumReviewCount"
                );

        List<Map<String, Object>> reviews =
                readReviews(input.get("reviews"));

        /*
         * AIClientService 会自动注入当前启用的评价摘要提示词，
         * 并使用本次任务生成的 traceContext 记录调用链。
         *
         * 调用异常不在这里捕获，由任务编排服务记录为
         * executionStatus=FAILED。
         */
        JsonNode actual =
                aiClientService.generateReviewSummary(
                        merchantId,
                        version,
                        reviews,
                        minimumReviewCount,
                        traceContext
                );

        RegressionJsonAssertionService.AssertionResult assertion =
                assertionService.compare(
                        expected,
                        actual
                );

        ObjectNode metrics =
                assertion.metrics().deepCopy();

        metrics.put(
                "reviewCount",
                reviews.size()
        );
        metrics.put(
                "minimumReviewCount",
                minimumReviewCount
        );
        metrics.put(
                "summaryVersion",
                version
        );

        String modelName =
                textFieldOrDefault(
                        actual,
                        "modelName",
                        versionSnapshot.modelName()
                );

        String modelVersion =
                textFieldOrDefault(
                        actual,
                        "modelVersion",
                        versionSnapshot.modelVersion()
                );

        String promptVersion =
                textFieldOrDefault(
                        actual,
                        "promptVersion",
                        versionSnapshot.promptVersion()
                );

        if (assertion.passed()) {
            return RegressionExecutionResult.passed(
                    actual,
                    metrics,
                    assertion.failureReasons(),
                    modelName,
                    modelVersion,
                    promptVersion,
                    traceContext.traceId()
            );
        }

        return RegressionExecutionResult.failed(
                actual,
                metrics,
                assertion.failureReasons(),
                modelName,
                modelVersion,
                promptVersion,
                traceContext.traceId()
        );
    }

    private List<Map<String, Object>> readReviews(
            JsonNode reviewsNode
    ) {
        if (reviewsNode == null
                || !reviewsNode.isArray()) {
            throw invalidCasePayload(
                    "reviews必须为JSON数组"
            );
        }

        for (JsonNode reviewNode : reviewsNode) {
            if (reviewNode == null
                    || !reviewNode.isObject()) {
                throw invalidCasePayload(
                        "reviews中的每一项都必须为JSON对象"
                );
            }
        }

        try {
            return objectMapper.convertValue(
                    reviewsNode,
                    new TypeReference<
                            List<Map<String, Object>>
                            >() {
                    }
            );
        } catch (IllegalArgumentException exception) {
            throw invalidCasePayload(
                    "reviews无法转换为评价列表"
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

    private String textFieldOrDefault(
            JsonNode source,
            String fieldName,
            String defaultValue
    ) {
        if (source == null
                || !source.isObject()) {
            return defaultValue;
        }

        JsonNode value =
                source.get(fieldName);

        if (value == null
                || !value.isTextual()
                || value.asText().isBlank()) {
            return defaultValue;
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