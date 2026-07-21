package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.evaluation.RecommendationEvalCaseResultResponse;
import com.foodadvisor.dto.evaluation.RecommendationEvalRunRequest;
import com.foodadvisor.dto.evaluation.RecommendationEvalRunResponse;
import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.RecommendationWeights;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantBusinessHours;
import com.foodadvisor.entity.RecommendationEvalCase;
import com.foodadvisor.entity.RecommendationEvalCaseResult;
import com.foodadvisor.entity.RecommendationEvalDataset;
import com.foodadvisor.entity.RecommendationEvalRun;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.RecommendationEvalCaseMapper;
import com.foodadvisor.mapper.RecommendationEvalCaseResultMapper;
import com.foodadvisor.mapper.RecommendationEvalDatasetMapper;
import com.foodadvisor.mapper.RecommendationEvalRunMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class RecommendationEvaluationRunService {

    private static final int DEFAULT_TOP_K = 10;

    private static final String MODEL_NAME = "RULE_FALLBACK";
    private static final String MODEL_VERSION = "RULE_BASELINE_V1";
    private static final String PROMPT_VERSION = "NOT_APPLICABLE";
    private static final String ALGORITHM_VERSION = "MATCH_SCORE_V1";

    private final RecommendationEvalDatasetMapper datasetMapper;
    private final RecommendationEvalCaseMapper caseMapper;
    private final RecommendationEvalRunMapper runMapper;
    private final RecommendationEvalCaseResultMapper caseResultMapper;
    private final MerchantMapper merchantMapper;
    private final ConstraintExtractionService constraintExtractionService;
    private final MerchantBusinessHoursService businessHoursService;
    private final MatchScoreCalculator matchScoreCalculator;
    private final ObjectMapper objectMapper;

    public RecommendationEvaluationRunService(
            RecommendationEvalDatasetMapper datasetMapper,
            RecommendationEvalCaseMapper caseMapper,
            RecommendationEvalRunMapper runMapper,
            RecommendationEvalCaseResultMapper caseResultMapper,
            MerchantMapper merchantMapper,
            ConstraintExtractionService constraintExtractionService,
            MerchantBusinessHoursService businessHoursService,
            MatchScoreCalculator matchScoreCalculator,
            ObjectMapper objectMapper
    ) {
        this.datasetMapper = datasetMapper;
        this.caseMapper = caseMapper;
        this.runMapper = runMapper;
        this.caseResultMapper = caseResultMapper;
        this.merchantMapper = merchantMapper;
        this.constraintExtractionService = constraintExtractionService;
        this.businessHoursService = businessHoursService;
        this.matchScoreCalculator = matchScoreCalculator;
        this.objectMapper = objectMapper;
    }

    /**
     * 同步执行一次标准测试集评测。
     */
    @Transactional
    public RecommendationEvalRunResponse executeRun(
            Long datasetId,
            RecommendationEvalRunRequest request,
            Long createdBy
    ) {
        RecommendationEvalDataset dataset =
                getDatasetOrThrow(datasetId);

        if ("ARCHIVED".equalsIgnoreCase(dataset.getStatus())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EVAL_DATASET_ARCHIVED",
                    "Archived evaluation datasets cannot be executed"
            );
        }

        int topK = request == null || request.topK() == null
                ? DEFAULT_TOP_K
                : request.topK();

        validateTopK(topK);

        List<RecommendationEvalCase> cases =
                loadEnabledCases(datasetId);

        if (cases.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EVAL_DATASET_EMPTY",
                    "Evaluation dataset has no enabled cases"
            );
        }

        RecommendationEvalRun run =
                createRun(dataset, cases.size(), createdBy);

        List<Merchant> candidates = loadCandidateMerchants();

        Map<Long, List<MerchantBusinessHours>> businessHours =
                businessHoursService.loadGrouped(
                        candidates.stream()
                                .map(Merchant::getId)
                                .toList()
                );

        int successCount = 0;
        int failedCount = 0;
        int totalExpectedFields = 0;
        int totalMatchedFields = 0;
        int exactConstraintCaseCount = 0;
        int noResultCaseCount = 0;
        int totalReturnedRecommendations = 0;

        Set<Long> uniqueMerchantIds = new LinkedHashSet<>();

        for (RecommendationEvalCase evalCase : cases) {
            long startedNanos = System.nanoTime();

            RecommendationEvalCaseResult caseResult =
                    createBaseCaseResult(run.getId(), evalCase);

            try {
                CaseExecutionSummary summary =
                        evaluateCase(
                                evalCase,
                                caseResult,
                                candidates,
                                businessHours,
                                topK
                        );

                caseResult.setStatus("SUCCESS");
                caseResult.setDurationMs(
                        elapsedMilliseconds(startedNanos)
                );
                caseResult.setUpdatedAt(OffsetDateTime.now());

                insertCaseResult(caseResult);

                successCount++;
                totalExpectedFields += summary.expectedFieldCount();
                totalMatchedFields += summary.matchedFieldCount();
                totalReturnedRecommendations += summary.returnedCount();

                if (summary.exactConstraintMatch()) {
                    exactConstraintCaseCount++;
                }

                if (summary.noResult()) {
                    noResultCaseCount++;
                }

                uniqueMerchantIds.addAll(summary.merchantIds());
            } catch (Exception exception) {
                caseResult.setStatus("FAILED");
                caseResult.setResultCount(0);
                caseResult.setDurationMs(
                        elapsedMilliseconds(startedNanos)
                );
                caseResult.setErrorMessage(errorMessage(exception));
                caseResult.setFailureReasons(
                        safeWriteJson(
                                List.of(
                                        Map.of(
                                                "type",
                                                "EXECUTION_ERROR",
                                                "message",
                                                errorMessage(exception)
                                        )
                                ),
                                "[]"
                        )
                );
                caseResult.setUpdatedAt(OffsetDateTime.now());

                insertCaseResult(caseResult);
                failedCount++;
            }
        }

        completeRun(
                run,
                topK,
                cases.size(),
                successCount,
                failedCount,
                totalExpectedFields,
                totalMatchedFields,
                exactConstraintCaseCount,
                noResultCaseCount,
                totalReturnedRecommendations,
                uniqueMerchantIds
        );

        return toRunResponse(runMapper.selectById(run.getId()));
    }

    /**
     * 查询单次评测运行。
     */
    public RecommendationEvalRunResponse getRun(Long runId) {
        return toRunResponse(getRunOrThrow(runId));
    }

    /**
     * 查询一次运行下的全部案例结果。
     */
    public List<RecommendationEvalCaseResultResponse> listResults(
            Long runId
    ) {
        getRunOrThrow(runId);

        return caseResultMapper.selectList(
                        new LambdaQueryWrapper<RecommendationEvalCaseResult>()
                                .eq(
                                        RecommendationEvalCaseResult::getRunId,
                                        runId
                                )
                                .orderByAsc(
                                        RecommendationEvalCaseResult::getId
                                )
                )
                .stream()
                .map(this::toCaseResultResponse)
                .toList();
    }

    private CaseExecutionSummary evaluateCase(
            RecommendationEvalCase evalCase,
            RecommendationEvalCaseResult caseResult,
            List<Merchant> candidates,
            Map<Long, List<MerchantBusinessHours>> businessHours,
            int topK
    ) {
        ConstraintExtractionService.PreparedExtraction prepared =
                constraintExtractionService
                        .prepareEvaluationExtraction(
                                evalCase.getInputText()
                        );

        ConstraintState extracted = prepared.extracted() == null
                ? new ConstraintState()
                : prepared.extracted();

        JsonNode expectedNode =
                readJsonObject(
                        evalCase.getExpectedConstraints(),
                        "expectedConstraints"
                );

        JsonNode extractedNode =
                objectMapper.valueToTree(extracted);

        JsonNode locationNode =
                readJsonObject(
                        evalCase.getLocationSnapshot(),
                        "locationSnapshot"
                );

        BigDecimal userLatitude =
                decimalValue(locationNode, "latitude");

        BigDecimal userLongitude =
                decimalValue(locationNode, "longitude");

        ConstraintComparison comparison =
                compareConstraints(
                        expectedNode,
                        extractedNode
                );

        List<Map<String, Object>> failureReasons =
                new ArrayList<>(comparison.failureReasons());

        if (extracted.getDistanceKm() != null
                && (userLatitude == null || userLongitude == null)) {
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("type", "LOCATION_MISSING");
            reason.put(
                    "message",
                    "Distance constraint requires latitude and longitude"
            );
            failureReasons.add(reason);
        }

        EvaluationMatches matches =
                calculateRecommendations(
                        candidates,
                        extracted,
                        userLatitude,
                        userLongitude,
                        businessHours
                );

        List<RecommendationItemVO> ranked =
                rankAndLimit(matches.items(), topK);

        if (ranked.isEmpty()) {
            Map<String, Object> reason = new LinkedHashMap<>();
            reason.put("type", "NO_RECOMMENDATION");
            reason.put(
                    "message",
                    "No merchant satisfied the extracted hard conditions"
            );
            failureReasons.add(reason);
        }

        Map<String, Object> hardMetrics = new LinkedHashMap<>();
        hardMetrics.put(
                "expectedConstraintFieldCount",
                comparison.expectedFieldCount()
        );
        hardMetrics.put(
                "matchedExpectedConstraintFieldCount",
                comparison.matchedFieldCount()
        );
        hardMetrics.put(
                "constraintAccuracy",
                comparison.accuracy()
        );
        hardMetrics.put(
                "allExpectedConstraintsMatched",
                comparison.exactMatch()
        );
        hardMetrics.put(
                "candidateMerchantCount",
                candidates.size()
        );
        hardMetrics.put(
                "businessHoursRejectedCount",
                matches.businessHoursRejectedCount()
        );
        hardMetrics.put(
                "hardFilterRejectedCount",
                matches.hardFilterRejectedCount()
        );
        hardMetrics.put(
                "matchedMerchantCount",
                matches.items().size()
        );
        hardMetrics.put(
                "returnedMerchantCount",
                ranked.size()
        );
        hardMetrics.put("topK", topK);
        hardMetrics.put("extractor", prepared.extractor());
        hardMetrics.put("degraded", prepared.degraded());
        hardMetrics.put(
                "conflictCount",
                prepared.conflicts() == null
                        ? 0
                        : prepared.conflicts().size()
        );

        caseResult.setExpectedConstraints(
                expectedNode.toString()
        );
        caseResult.setExtractedConstraints(
                extractedNode.toString()
        );

        /*
         * 当前评测为无状态单轮评测，
         * 因此 mergedConstraints 与 extractedConstraints 相同。
         */
        caseResult.setMergedConstraints(
                extractedNode.toString()
        );

        caseResult.setRecommendationSnapshot(
                writeJson(ranked)
        );
        caseResult.setHardConditionMetrics(
                writeJson(hardMetrics)
        );
        caseResult.setFailureReasons(
                writeJson(failureReasons)
        );
        caseResult.setResultCount(ranked.size());
        caseResult.setErrorMessage(null);

        return new CaseExecutionSummary(
                comparison.expectedFieldCount(),
                comparison.matchedFieldCount(),
                comparison.exactMatch(),
                ranked.isEmpty(),
                ranked.size(),
                ranked.stream()
                        .map(RecommendationItemVO::getMerchantId)
                        .filter(java.util.Objects::nonNull)
                        .collect(
                                java.util.stream.Collectors.toCollection(
                                        LinkedHashSet::new
                                )
                        )
        );
    }

    private EvaluationMatches calculateRecommendations(
            List<Merchant> candidates,
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            Map<Long, List<MerchantBusinessHours>> businessHours
    ) {
        List<RecommendationItemVO> results = new ArrayList<>();
        RecommendationWeights weights = new RecommendationWeights();

        int businessHoursRejectedCount = 0;
        int hardFilterRejectedCount = 0;

        for (Merchant merchant : candidates) {
            MerchantBusinessHoursService.BusinessHoursMatch hoursMatch =
                    businessHoursService.match(
                            constraints,
                            businessHours.get(merchant.getId())
                    );

            if (!hoursMatch.matched()) {
                businessHoursRejectedCount++;
                continue;
            }

            Optional<RecommendationItemVO> result =
                    matchScoreCalculator.calculate(
                            merchant,
                            constraints,
                            weights,
                            userLatitude,
                            userLongitude,
                            Map.of()
                    );

            if (result.isEmpty()) {
                hardFilterRejectedCount++;
                continue;
            }

            RecommendationItemVO item = result.get();

            matchScoreCalculator.addBusinessHoursEvidence(
                    item,
                    hoursMatch.evidence()
            );

            results.add(item);
        }

        return new EvaluationMatches(
                results,
                businessHoursRejectedCount,
                hardFilterRejectedCount
        );
    }

    private List<RecommendationItemVO> rankAndLimit(
            List<RecommendationItemVO> source,
            int topK
    ) {
        List<RecommendationItemVO> ranked =
                new ArrayList<>(source);

        ranked.sort(
                Comparator
                        .comparing(
                                RecommendationItemVO::getFinalScore,
                                Comparator.nullsFirst(
                                        BigDecimal::compareTo
                                )
                        )
                        .reversed()
                        .thenComparing(
                                RecommendationItemVO::getMerchantId,
                                Comparator.nullsLast(
                                        Long::compareTo
                                )
                        )
        );

        int limit = Math.min(topK, ranked.size());

        List<RecommendationItemVO> limited =
                new ArrayList<>(ranked.subList(0, limit));

        for (int index = 0; index < limited.size(); index++) {
            limited.get(index).setRankNo(index + 1);
        }

        return limited;
    }

    private ConstraintComparison compareConstraints(
            JsonNode expected,
            JsonNode actual
    ) {
        int expectedFieldCount = 0;
        int matchedFieldCount = 0;

        List<Map<String, Object>> failures =
                new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> fields =
                expected.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry =
                    fields.next();

            expectedFieldCount++;

            String fieldName = entry.getKey();
            JsonNode expectedValue = entry.getValue();
            JsonNode actualValue = actual.get(fieldName);

            if (jsonValuesEqual(expectedValue, actualValue)) {
                matchedFieldCount++;
                continue;
            }

            Map<String, Object> reason =
                    new LinkedHashMap<>();

            reason.put("type", "CONSTRAINT_MISMATCH");
            reason.put("field", fieldName);
            reason.put(
                    "expected",
                    objectMapper.convertValue(
                            expectedValue,
                            Object.class
                    )
            );
            reason.put(
                    "actual",
                    actualValue == null
                            ? null
                            : objectMapper.convertValue(
                                    actualValue,
                                    Object.class
                            )
            );

            failures.add(reason);
        }

        BigDecimal accuracy =
                expectedFieldCount == 0
                        ? BigDecimal.ONE
                        : BigDecimal.valueOf(matchedFieldCount)
                        .divide(
                                BigDecimal.valueOf(
                                        expectedFieldCount
                                ),
                                4,
                                RoundingMode.HALF_UP
                        );

        return new ConstraintComparison(
                expectedFieldCount,
                matchedFieldCount,
                expectedFieldCount == matchedFieldCount,
                accuracy,
                failures
        );
    }

    private boolean jsonValuesEqual(
            JsonNode expected,
            JsonNode actual
    ) {
        if (expected == null || expected.isNull()) {
            return actual == null || actual.isNull();
        }

        if (actual == null || actual.isNull()) {
            return false;
        }

        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(
                    actual.decimalValue()
            ) == 0;
        }

        if (expected.isTextual() && actual.isTextual()) {
            return expected.asText().trim().equalsIgnoreCase(
                    actual.asText().trim()
            );
        }

        if (expected.isArray() && actual.isArray()
                && containsOnlyText(expected)
                && containsOnlyText(actual)) {
            return textSet(expected).equals(textSet(actual));
        }

        return expected.equals(actual);
    }

    private boolean containsOnlyText(JsonNode arrayNode) {
        for (JsonNode item : arrayNode) {
            if (!item.isTextual()) {
                return false;
            }
        }
        return true;
    }

    private Set<String> textSet(JsonNode arrayNode) {
        Set<String> values = new LinkedHashSet<>();

        for (JsonNode item : arrayNode) {
            values.add(
                    item.asText()
                            .trim()
                            .toLowerCase(Locale.ROOT)
            );
        }

        return values;
    }

    private RecommendationEvalRun createRun(
            RecommendationEvalDataset dataset,
            int requestedCount,
            Long createdBy
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        RecommendationEvalRun run =
                new RecommendationEvalRun();

        run.setDatasetId(dataset.getId());
        run.setStatus("RUNNING");
        run.setModelName(MODEL_NAME);
        run.setModelVersion(MODEL_VERSION);
        run.setPromptVersion(PROMPT_VERSION);
        run.setAlgorithmVersion(ALGORITHM_VERSION);
        run.setDataVersion(dataset.getDataVersion());
        run.setRequestedCount(requestedCount);
        run.setSuccessCount(0);
        run.setFailedCount(0);
        run.setUniqueMerchantCount(0);
        run.setMetrics("{}");
        run.setErrorMessage(null);
        run.setCreatedBy(createdBy);
        run.setStartedAt(now);
        run.setCompletedAt(null);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);

        if (runMapper.insert(run) != 1 || run.getId() == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EVAL_RUN_CREATE_FAILED",
                    "Failed to create recommendation evaluation run"
            );
        }

        return run;
    }

    private RecommendationEvalCaseResult createBaseCaseResult(
            Long runId,
            RecommendationEvalCase evalCase
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        RecommendationEvalCaseResult result =
                new RecommendationEvalCaseResult();

        result.setRunId(runId);
        result.setCaseId(evalCase.getId());
        result.setStatus("FAILED");
        result.setTraceId(null);
        result.setInputSnapshot(evalCase.getInputText());
        result.setExpectedConstraints(
                defaultJsonObject(
                        evalCase.getExpectedConstraints()
                )
        );
        result.setExtractedConstraints("{}");
        result.setMergedConstraints("{}");
        result.setRecommendationSnapshot("[]");
        result.setHardConditionMetrics("{}");
        result.setFailureReasons("[]");
        result.setResultCount(0);
        result.setCreatedAt(now);
        result.setUpdatedAt(now);

        return result;
    }

    private void insertCaseResult(
            RecommendationEvalCaseResult result
    ) {
        if (caseResultMapper.insert(result) != 1
                || result.getId() == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EVAL_CASE_RESULT_SAVE_FAILED",
                    "Failed to save recommendation evaluation case result"
            );
        }
    }

    private void completeRun(
            RecommendationEvalRun run,
            int topK,
            int requestedCount,
            int successCount,
            int failedCount,
            int totalExpectedFields,
            int totalMatchedFields,
            int exactConstraintCaseCount,
            int noResultCaseCount,
            int totalReturnedRecommendations,
            Set<Long> uniqueMerchantIds
    ) {
        BigDecimal overallAccuracy =
                totalExpectedFields == 0
                        ? BigDecimal.ONE
                        : BigDecimal.valueOf(
                                totalMatchedFields
                        ).divide(
                                BigDecimal.valueOf(
                                        totalExpectedFields
                                ),
                                4,
                                RoundingMode.HALF_UP
                        );

        Map<String, Object> metrics =
                new LinkedHashMap<>();

        metrics.put("topK", topK);
        metrics.put("requestedCaseCount", requestedCount);
        metrics.put("successfulCaseCount", successCount);
        metrics.put("failedCaseCount", failedCount);
        metrics.put(
                "expectedConstraintFieldCount",
                totalExpectedFields
        );
        metrics.put(
                "matchedExpectedConstraintFieldCount",
                totalMatchedFields
        );
        metrics.put(
                "overallConstraintAccuracy",
                overallAccuracy
        );
        metrics.put(
                "exactConstraintMatchCaseCount",
                exactConstraintCaseCount
        );
        metrics.put(
                "noResultCaseCount",
                noResultCaseCount
        );
        metrics.put(
                "totalReturnedRecommendations",
                totalReturnedRecommendations
        );
        metrics.put(
                "uniqueMerchantCount",
                uniqueMerchantIds.size()
        );

        String status;

        if (failedCount == 0) {
            status = "COMPLETED";
        } else if (successCount > 0) {
            status = "PARTIAL";
        } else {
            status = "FAILED";
        }

        run.setStatus(status);
        run.setSuccessCount(successCount);
        run.setFailedCount(failedCount);
        run.setUniqueMerchantCount(uniqueMerchantIds.size());
        run.setMetrics(writeJson(metrics));
        run.setErrorMessage(
                "FAILED".equals(status)
                        ? "All evaluation cases failed"
                        : null
        );
        run.setCompletedAt(OffsetDateTime.now());
        run.setUpdatedAt(OffsetDateTime.now());

        if (runMapper.updateById(run) != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EVAL_RUN_UPDATE_FAILED",
                    "Failed to complete recommendation evaluation run"
            );
        }
    }

    private List<RecommendationEvalCase> loadEnabledCases(
            Long datasetId
    ) {
        return caseMapper.selectList(
                new LambdaQueryWrapper<RecommendationEvalCase>()
                        .eq(
                                RecommendationEvalCase::getDatasetId,
                                datasetId
                        )
                        .eq(
                                RecommendationEvalCase::getEnabled,
                                Boolean.TRUE
                        )
                        .orderByAsc(
                                RecommendationEvalCase::getSequenceNo
                        )
                        .orderByAsc(
                                RecommendationEvalCase::getId
                        )
        );
    }

    private List<Merchant> loadCandidateMerchants() {
        List<Merchant> merchants =
                merchantMapper.selectList(
                        new LambdaQueryWrapper<Merchant>()
                                .eq(
                                        Merchant::getPlatformStatus,
                                        "ACTIVE"
                                )
                                .eq(
                                        Merchant::getOperationStatus,
                                        "OPERATING"
                                )
                                .isNull(
                                        Merchant::getDeletedAt
                                )
                                .orderByAsc(Merchant::getId)
                );

        return merchants == null
                ? List.of()
                : merchants;
    }

    private RecommendationEvalDataset getDatasetOrThrow(
            Long datasetId
    ) {
        if (datasetId == null || datasetId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EVAL_DATASET_ID",
                    "Evaluation dataset id must be positive"
            );
        }

        RecommendationEvalDataset dataset =
                datasetMapper.selectById(datasetId);

        if (dataset == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "EVAL_DATASET_NOT_FOUND",
                    "Recommendation evaluation dataset not found"
            );
        }

        return dataset;
    }

    private RecommendationEvalRun getRunOrThrow(Long runId) {
        if (runId == null || runId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EVAL_RUN_ID",
                    "Evaluation run id must be positive"
            );
        }

        RecommendationEvalRun run =
                runMapper.selectById(runId);

        if (run == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "EVAL_RUN_NOT_FOUND",
                    "Recommendation evaluation run not found"
            );
        }

        return run;
    }

    private JsonNode readJsonObject(
            String rawValue,
            String fieldName
    ) {
        String source = rawValue == null || rawValue.isBlank()
                ? "{}"
                : rawValue;

        try {
            JsonNode node = objectMapper.readTree(source);

            if (!node.isObject()) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_EVAL_JSON",
                        fieldName + " must be a JSON object"
                );
            }

            return node;
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EVAL_JSON",
                    fieldName + " contains invalid JSON"
            );
        }
    }

    private BigDecimal decimalValue(
            JsonNode objectNode,
            String fieldName
    ) {
        JsonNode value = objectNode.get(fieldName);

        if (value == null || value.isNull()) {
            return null;
        }

        if (value.isNumber()) {
            return value.decimalValue();
        }

        if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize evaluation snapshot",
                    exception
            );
        }
    }

    private String safeWriteJson(
            Object value,
            String fallback
    ) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    private String defaultJsonObject(String value) {
        return value == null || value.isBlank()
                ? "{}"
                : value;
    }

    private long elapsedMilliseconds(long startedNanos) {
        return Math.max(
                0L,
                (System.nanoTime() - startedNanos) / 1_000_000L
        );
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        String result =
                exception.getClass().getSimpleName()
                        + ": "
                        + message;

        return result.length() <= 2000
                ? result
                : result.substring(0, 2000);
    }

    private void validateTopK(int topK) {
        if (topK < 1 || topK > 20) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_EVAL_TOP_K",
                    "topK must be between 1 and 20"
            );
        }
    }

    private RecommendationEvalRunResponse toRunResponse(
            RecommendationEvalRun run
    ) {
        return new RecommendationEvalRunResponse(
                run.getId(),
                run.getDatasetId(),
                run.getStatus(),
                run.getModelName(),
                run.getModelVersion(),
                run.getPromptVersion(),
                run.getAlgorithmVersion(),
                run.getDataVersion(),
                run.getRequestedCount(),
                run.getSuccessCount(),
                run.getFailedCount(),
                run.getUniqueMerchantCount(),
                run.getMetrics(),
                run.getErrorMessage(),
                run.getCreatedBy(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getCreatedAt(),
                run.getUpdatedAt()
        );
    }

    private RecommendationEvalCaseResultResponse toCaseResultResponse(
            RecommendationEvalCaseResult result
    ) {
        return new RecommendationEvalCaseResultResponse(
                result.getId(),
                result.getRunId(),
                result.getCaseId(),
                result.getStatus(),
                result.getTraceId(),
                result.getInputSnapshot(),
                result.getExpectedConstraints(),
                result.getExtractedConstraints(),
                result.getMergedConstraints(),
                result.getRecommendationSnapshot(),
                result.getHardConditionMetrics(),
                result.getFailureReasons(),
                result.getResultCount(),
                result.getDurationMs(),
                result.getErrorMessage(),
                result.getRelevanceLabel(),
                result.getAnnotationNote(),
                result.getAnnotatedBy(),
                result.getAnnotatedAt(),
                result.getCreatedAt(),
                result.getUpdatedAt()
        );
    }

    private record EvaluationMatches(
            List<RecommendationItemVO> items,
            int businessHoursRejectedCount,
            int hardFilterRejectedCount
    ) {
    }

    private record ConstraintComparison(
            int expectedFieldCount,
            int matchedFieldCount,
            boolean exactMatch,
            BigDecimal accuracy,
            List<Map<String, Object>> failureReasons
    ) {
    }

    private record CaseExecutionSummary(
            int expectedFieldCount,
            int matchedFieldCount,
            boolean exactConstraintMatch,
            boolean noResult,
            int returnedCount,
            Set<Long> merchantIds
    ) {
    }
}