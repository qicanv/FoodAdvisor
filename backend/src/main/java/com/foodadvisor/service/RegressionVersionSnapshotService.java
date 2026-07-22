package com.foodadvisor.service;

import com.foodadvisor.dto.modelconfig.SceneBindingResponse;
import com.foodadvisor.dto.prompt.ResolvedPrompt;
import com.foodadvisor.dto.regression.RegressionVersionSnapshot;
import com.foodadvisor.enums.PromptScene;
import com.foodadvisor.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RegressionVersionSnapshotService {

    private static final Set<String> TEST_TYPES = Set.of(
            "CONSTRAINT_EXTRACTION",
            "RECOMMENDATION",
            "REVIEW_SUMMARY",
            "SENTIMENT_ANALYSIS"
    );

    private static final String DEFAULT_PROMPT_VERSION =
            "DEFAULT_CODE_PROMPT";

    private final ModelConfigService modelConfigService;
    private final PromptManagementService promptManagementService;

    public RegressionVersionSnapshotService(
            ModelConfigService modelConfigService,
            PromptManagementService promptManagementService
    ) {
        this.modelConfigService = modelConfigService;
        this.promptManagementService = promptManagementService;
    }

    /**
     * 解析一种测试类型当前实际使用的版本信息。
     */
    public RegressionVersionSnapshot resolve(
            String rawTestType
    ) {
        String testType = normalizeTestType(rawTestType);

        /*
         * 推荐评测目前使用确定性的规则提取和匹配评分器，
         * 不通过模型和提示词完成排序。
         */
        if ("RECOMMENDATION".equals(testType)) {
            return new RegressionVersionSnapshot(
                    testType,
                    PromptScene.DINING_RECOMMENDATION.name(),

                    null,
                    null,
                    "RULE_FALLBACK",
                    "RULE_BASELINE_V1",
                    null,

                    null,
                    null,
                    "NOT_APPLICABLE",

                    "MATCH_SCORE_V1"
            );
        }

        PromptScene promptScene =
                promptSceneFor(testType);

        SceneBindingResponse binding =
                findActiveBinding(promptScene);

        ResolvedPrompt prompt =
                promptManagementService
                        .resolveActivePrompt(promptScene)
                        .orElse(null);

        String modelName =
                binding == null
                        ? "UNBOUND"
                        : textOrDefault(
                                binding.modelName(),
                                "UNBOUND"
                        );

        /*
         * 当前数据库没有独立的 modelVersion 字段。
         * 实际调用的 modelName 通常就是供应商模型版本标识，
         * 例如带日期或版本号的模型名称。
         */
        String modelVersion = modelName;

        return new RegressionVersionSnapshot(
                testType,
                promptScene.name(),

                binding == null
                        ? null
                        : binding.modelConfigId(),

                binding == null
                        ? null
                        : binding.modelConfigName(),

                modelName,
                modelVersion,

                binding == null
                        ? null
                        : binding.updatedAt(),

                prompt == null
                        ? null
                        : prompt.definitionId(),

                prompt == null
                        ? null
                        : prompt.versionId(),

                prompt == null
                        ? DEFAULT_PROMPT_VERSION
                        : textOrDefault(
                                prompt.versionTag(),
                                DEFAULT_PROMPT_VERSION
                        ),

                algorithmVersionFor(testType)
        );
    }

    /**
     * 一次解析多个测试类型。
     *
     * LinkedHashMap 保持调用方传入的测试类型顺序。
     */
    public Map<String, RegressionVersionSnapshot> resolveAll(
            Collection<String> testTypes
    ) {
        Map<String, RegressionVersionSnapshot> snapshots =
                new LinkedHashMap<>();

        if (testTypes == null) {
            return snapshots;
        }

        for (String rawTestType : testTypes) {
            RegressionVersionSnapshot snapshot =
                    resolve(rawTestType);

            snapshots.put(
                    snapshot.testType(),
                    snapshot
            );
        }

        return snapshots;
    }

    private SceneBindingResponse findActiveBinding(
            PromptScene promptScene
    ) {
        List<SceneBindingResponse> bindings =
                modelConfigService.listSceneBindings();

        if (bindings == null || bindings.isEmpty()) {
            return null;
        }

        return bindings.stream()
                .filter(item -> item != null)
                .filter(item ->
                        promptScene.name().equalsIgnoreCase(
                                item.sceneType()
                        )
                )
                .filter(item ->
                        "ACTIVE".equalsIgnoreCase(
                                item.status()
                        )
                )
                .findFirst()
                .orElse(null);
    }

    private PromptScene promptSceneFor(
            String testType
    ) {
        return switch (testType) {
            case "CONSTRAINT_EXTRACTION" ->
                    PromptScene.CONSTRAINT_EXTRACTION;

            case "REVIEW_SUMMARY" ->
                    PromptScene.REVIEW_SUMMARY;

            case "SENTIMENT_ANALYSIS" ->
                    PromptScene.SENTIMENT_ANALYSIS;

            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REGRESSION_TEST_SCENE_NOT_SUPPORTED",
                    "当前测试类型没有对应的提示词场景"
            );
        };
    }

    private String algorithmVersionFor(
            String testType
    ) {
        return switch (testType) {
            case "CONSTRAINT_EXTRACTION" ->
                    "AI_CONSTRAINT_EXTRACTION_V1";

            case "REVIEW_SUMMARY" ->
                    "REVIEW_SUMMARY_ASSERTION_V1";

            case "SENTIMENT_ANALYSIS" ->
                    "SENTIMENT_ASSERTION_V1";

            case "RECOMMENDATION" ->
                    "MATCH_SCORE_V1";

            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REGRESSION_ALGORITHM_VERSION_UNKNOWN",
                    "无法确定当前测试类型的算法版本"
            );
        };
    }

    private String normalizeTestType(
            String rawTestType
    ) {
        if (rawTestType == null
                || rawTestType.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REGRESSION_TEST_TYPE_REQUIRED",
                    "回归测试类型不能为空"
            );
        }

        String testType =
                rawTestType.trim()
                        .toUpperCase(Locale.ROOT);

        if (!TEST_TYPES.contains(testType)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REGRESSION_TEST_TYPE",
                    "不支持的回归测试类型"
            );
        }

        return testType;
    }

    private String textOrDefault(
            String value,
            String defaultValue
    ) {
        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }
}