package com.foodadvisor.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodadvisor.dto.ai.DialogueExtractAiRequest;
import com.foodadvisor.dto.ai.DialogueExtractAiResponse;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.regression.RegressionExecutionResult;
import com.foodadvisor.dto.regression.RegressionVersionSnapshot;
import com.foodadvisor.entity.RegressionTestCase;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.trace.AiTraceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ConstraintExtractionRegressionExecutor
        implements RegressionTestCaseExecutor {

    private static final String TEST_TYPE =
            "CONSTRAINT_EXTRACTION";

    private final AIClientService aiClientService;
    private final RegressionJsonAssertionService assertionService;
    private final ObjectMapper objectMapper;

    public ConstraintExtractionRegressionExecutor(
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

        DialogueExtractAiRequest request =
                buildRequest(testCase, input);

        /*
         * 不捕获调用异常。
         * 外层任务编排服务负责将调用异常记录为 executionStatus=FAILED。
         */
        DialogueExtractAiResponse response =
                aiClientService.extractDialogueConstraints(
                        request,
                        traceContext
                );

        JsonNode actual =
                objectMapper.valueToTree(response);

        RegressionJsonAssertionService.AssertionResult assertion =
                assertionService.compare(
                        expected,
                        actual
                );

        ObjectNode metrics =
                assertion.metrics().deepCopy();

        metrics.put(
                "extractor",
                textOrDefault(
                        response.getExtractor(),
                        "UNKNOWN"
                )
        );

        metrics.put(
                "degraded",
                Boolean.TRUE.equals(
                        response.getDegraded()
                )
        );

        if (response.getConfidence() != null) {
            metrics.put(
                    "confidence",
                    response.getConfidence()
            );
        }

        String modelName =
                textOrDefault(
                        response.getModelName(),
                        versionSnapshot.modelName()
                );

        String modelVersion =
                textOrDefault(
                        response.getModelVersion(),
                        versionSnapshot.modelVersion()
                );

        String promptVersion =
                textOrDefault(
                        response.getPromptVersion(),
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

    private DialogueExtractAiRequest buildRequest(
            RegressionTestCase testCase,
            JsonNode input
    ) {
        String content =
                requiredText(input, "content");

        DialogueExtractAiRequest request =
                new DialogueExtractAiRequest();

        /*
         * 回归测试不访问真实会话。
         * 缺省时使用稳定的虚拟正整数，避免 AI Schema 拒绝 null。
         */
        request.setSessionId(
                positiveLongOrDefault(
                        input.get("sessionId"),
                        1L
                )
        );

        request.setMessageId(
                positiveLongOrDefault(
                        input.get("messageId"),
                        testCase.getId() == null
                                ? 1L
                                : testCase.getId()
                )
        );

        request.setContent(content);

        JsonNode currentConstraints =
                input.get("currentConstraints");

        if (currentConstraints == null
                || currentConstraints.isNull()) {
            request.setCurrentConstraints(
                    new ConstraintState()
            );
        } else {
            if (!currentConstraints.isObject()) {
                throw invalidCasePayload(
                        "currentConstraints必须为JSON对象"
                );
            }

            try {
                request.setCurrentConstraints(
                        objectMapper.treeToValue(
                                currentConstraints,
                                ConstraintState.class
                        )
                );
            } catch (JsonProcessingException exception) {
                throw invalidCasePayload(
                        "currentConstraints无法转换为条件对象"
                );
            }
        }

        return request;
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

    private Long positiveLongOrDefault(
            JsonNode value,
            Long defaultValue
    ) {
        if (value == null || value.isNull()) {
            return defaultValue;
        }

        if (!value.canConvertToLong()
                || value.asLong() <= 0) {
            throw invalidCasePayload(
                    "sessionId和messageId必须为正整数"
            );
        }

        return value.asLong();
    }

    private String textOrDefault(
            String value,
            String defaultValue
    ) {
        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
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