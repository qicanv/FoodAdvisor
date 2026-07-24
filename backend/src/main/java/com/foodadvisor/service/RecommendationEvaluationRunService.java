package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.evaluation.RecommendationEvalRunComparisonResponse;
import com.foodadvisor.dto.evaluation.RecommendationEvalCaseResultResponse;
import com.foodadvisor.dto.evaluation.RecommendationEvalRunRequest;
import com.foodadvisor.dto.evaluation.RecommendationEvalRunResponse;
import com.foodadvisor.dto.evaluation.RecommendationEvalAnnotationRequest;
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
     * 执行一个不落库的推荐评测案例。
     *
     * 该入口供通用回归测试模块复用：
     * - 使用正式的规则条件提取；
     * - 使用正式的营业时间过滤；
     * - 使用正式的硬条件过滤和评分排序；
     * - 不创建 recommendation_eval_runs；
     * - 不写入 recommendation_eval_case_results。
     */
    @Transactional(readOnly = true)
    public AdHocRecommendationEvaluation evaluateAdHoc(
            String inputText,
            String expectedConstraints,
            String locationSnapshot,
            Integer requestedTopK
    ) {
        if (inputText == null || inputText.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REGRESSION_RECOMMENDATION_INPUT_REQUIRED",
                    "推荐回归案例的message不能为空"
            );
        }

        int topK =
                requestedTopK == null
                        ? DEFAULT_TOP_K
                        : requestedTopK;

        validateTopK(topK);

        RecommendationEvalCase evalCase =
                new RecommendationEvalCase();

        evalCase.setInputText(inputText.trim());
        evalCase.setExpectedConstraints(
                defaultJsonObject(expectedConstraints)
        );
        evalCase.setLocationSnapshot(
                defaultJsonObject(locationSnapshot)
        );

        /*
         * 仅借助原有结果实体承接 evaluateCase 的输出，
         * 该对象不会写入数据库。
         */
        RecommendationEvalCaseResult caseResult =
                createBaseCaseResult(
                        null,
                        evalCase
                );

        List<Merchant> candidates =
                loadCandidateMerchants();

        Map<Long, List<MerchantBusinessHours>> businessHours =
                businessHoursService.loadGrouped(
                        candidates.stream()
                                .map(Merchant::getId)
                                .toList()
                );

        long startedNanos =
                System.nanoTime();

        CaseExecutionSummary summary =
                evaluateCase(
                        evalCase,
                        caseResult,
                        candidates,
                        businessHours,
                        topK
                );

        long durationMs =
                elapsedMilliseconds(startedNanos);

        caseResult.setStatus("SUCCESS");
        caseResult.setDurationMs(durationMs);
        caseResult.setUpdatedAt(OffsetDateTime.now());

        return new AdHocRecommendationEvaluation(
                caseResult.getExtractedConstraints(),
                caseResult.getMergedConstraints(),
                caseResult.getRecommendationSnapshot(),
                caseResult.getHardConditionMetrics(),
                caseResult.getFailureReasons(),

                caseResult.getResultCount() == null
                        ? 0
                        : caseResult.getResultCount(),

                summary.exactConstraintMatch(),
                summary.noResult(),
                new LinkedHashSet<>(
                        summary.merchantIds()
                ),
                durationMs
        );
    }


    /**
     * 查询指定测试集的历史评测运行。
     */
    public List<RecommendationEvalRunResponse> listRuns(
            Long datasetId,
            String status
    ) {
        getDatasetOrThrow(datasetId);

        LambdaQueryWrapper<RecommendationEvalRun> wrapper =
                new LambdaQueryWrapper<RecommendationEvalRun>()
                        .eq(
                                RecommendationEvalRun::getDatasetId,
                                datasetId
                        )
                        .orderByDesc(
                                RecommendationEvalRun::getCreatedAt
                        )
                        .orderByDesc(
                                RecommendationEvalRun::getId
                        );

        if (status != null && !status.isBlank()) {
            wrapper.eq(
                    RecommendationEvalRun::getStatus,
                    status.trim().toUpperCase(Locale.ROOT)
            );
        }

        return runMapper.selectList(wrapper)
                .stream()
                .map(this::toRunResponse)
                .toList();
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

    /**
     * 比较同一测试集的两次评测运行。
     */
    public RecommendationEvalRunComparisonResponse compareRuns(
            Long baselineRunId,
            Long candidateRunId
    ) {
        if (baselineRunId.equals(candidateRunId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EVAL_RUN_COMPARE_SAME_RUN",
                    "基准运行和候选运行不能是同一次运行"
            );
        }

        RecommendationEvalRun baselineRun =
                getRunOrThrow(baselineRunId);

        RecommendationEvalRun candidateRun =
                getRunOrThrow(candidateRunId);

        ensureComparableRun(baselineRun);
        ensureComparableRun(candidateRun);

        if (!baselineRun.getDatasetId().equals(
                candidateRun.getDatasetId()
        )) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EVAL_RUN_DATASET_MISMATCH",
                    "只能比较同一测试集产生的评测运行"
            );
        }

        JsonNode baselineMetrics =
                parseStoredJsonObject(
                        baselineRun.getMetrics(),
                        "baseline metrics"
                );

        JsonNode candidateMetrics =
                parseStoredJsonObject(
                        candidateRun.getMetrics(),
                        "candidate metrics"
                );

        BigDecimal baselineAccuracy =
                metricDecimal(
                        baselineMetrics,
                        "overallConstraintAccuracy"
                );

        BigDecimal candidateAccuracy =
                metricDecimal(
                        candidateMetrics,
                        "overallConstraintAccuracy"
                );

        int baselineExactMatchCount =
                metricInteger(
                        baselineMetrics,
                        "exactConstraintMatchCaseCount"
                );

        int candidateExactMatchCount =
                metricInteger(
                        candidateMetrics,
                        "exactConstraintMatchCaseCount"
                );

        int baselineFailedCount =
                metricInteger(
                        baselineMetrics,
                        "failedCaseCount"
                );

        int candidateFailedCount =
                metricInteger(
                        candidateMetrics,
                        "failedCaseCount"
                );

        int baselineNoResultCount =
                metricInteger(
                        baselineMetrics,
                        "noResultCaseCount"
                );

        int candidateNoResultCount =
                metricInteger(
                        candidateMetrics,
                        "noResultCaseCount"
                );

        int baselineReturnedCount =
                metricInteger(
                        baselineMetrics,
                        "totalReturnedRecommendations"
                );

        int candidateReturnedCount =
                metricInteger(
                        candidateMetrics,
                        "totalReturnedRecommendations"
                );

        int baselineUniqueMerchantCount =
                metricInteger(
                        baselineMetrics,
                        "uniqueMerchantCount"
                );

        int candidateUniqueMerchantCount =
                metricInteger(
                        candidateMetrics,
                        "uniqueMerchantCount"
                );

        RecommendationEvalRunComparisonResponse.MetricComparison
                metricComparison =
                new RecommendationEvalRunComparisonResponse
                        .MetricComparison(
                        baselineAccuracy,
                        candidateAccuracy,
                        decimalChange(
                                baselineAccuracy,
                                candidateAccuracy
                        ),

                        baselineExactMatchCount,
                        candidateExactMatchCount,
                        candidateExactMatchCount
                                - baselineExactMatchCount,

                        baselineFailedCount,
                        candidateFailedCount,
                        candidateFailedCount
                                - baselineFailedCount,

                        baselineNoResultCount,
                        candidateNoResultCount,
                        candidateNoResultCount
                                - baselineNoResultCount,

                        baselineReturnedCount,
                        candidateReturnedCount,
                        candidateReturnedCount
                                - baselineReturnedCount,

                        baselineUniqueMerchantCount,
                        candidateUniqueMerchantCount,
                        candidateUniqueMerchantCount
                                - baselineUniqueMerchantCount
                );

        Map<Long, RecommendationEvalCaseResult>
                baselineResults =
                loadCaseResultMap(baselineRunId);

        Map<Long, RecommendationEvalCaseResult>
                candidateResults =
                loadCaseResultMap(candidateRunId);

        Set<Long> allCaseIds = new LinkedHashSet<>();
        allCaseIds.addAll(baselineResults.keySet());
        allCaseIds.addAll(candidateResults.keySet());

        List<RecommendationEvalRunComparisonResponse.CaseComparison>
                caseComparisons = new ArrayList<>();

        List<Long> improvedCaseIds = new ArrayList<>();
        List<Long> regressedCaseIds = new ArrayList<>();
        List<Long> unchangedCaseIds = new ArrayList<>();
        List<Long> addedCaseIds = new ArrayList<>();
        List<Long> removedCaseIds = new ArrayList<>();

        for (Long caseId : allCaseIds) {
            RecommendationEvalCaseResult baselineResult =
                    baselineResults.get(caseId);

            RecommendationEvalCaseResult candidateResult =
                    candidateResults.get(caseId);

            BigDecimal baselineCaseAccuracy =
                    baselineResult == null
                            ? null
                            : caseConstraintAccuracy(
                                    baselineResult
                            );

            BigDecimal candidateCaseAccuracy =
                    candidateResult == null
                            ? null
                            : caseConstraintAccuracy(
                                    candidateResult
                            );

            String changeType = resolveCaseChangeType(
                    baselineResult,
                    candidateResult,
                    baselineCaseAccuracy,
                    candidateCaseAccuracy
            );

            switch (changeType) {
                case "IMPROVED" -> improvedCaseIds.add(caseId);
                case "REGRESSED" -> regressedCaseIds.add(caseId);
                case "UNCHANGED" -> unchangedCaseIds.add(caseId);
                case "ADDED" -> addedCaseIds.add(caseId);
                case "REMOVED" -> removedCaseIds.add(caseId);
                default -> {
                    // 不会进入此分支
                }
            }

            Integer baselineResultCount =
                    baselineResult == null
                            ? null
                            : zeroIfNull(
                                    baselineResult.getResultCount()
                            );

            Integer candidateResultCount =
                    candidateResult == null
                            ? null
                            : zeroIfNull(
                                    candidateResult.getResultCount()
                            );

            Integer resultCountChange =
                    baselineResultCount == null
                            || candidateResultCount == null
                            ? null
                            : candidateResultCount
                            - baselineResultCount;

            BigDecimal accuracyChange =
                    baselineCaseAccuracy == null
                            || candidateCaseAccuracy == null
                            ? null
                            : decimalChange(
                                    baselineCaseAccuracy,
                                    candidateCaseAccuracy
                            );

            caseComparisons.add(
                    new RecommendationEvalRunComparisonResponse
                            .CaseComparison(
                            caseId,

                            baselineResult == null
                                    ? null
                                    : baselineResult.getId(),
                            candidateResult == null
                                    ? null
                                    : candidateResult.getId(),

                            baselineResult == null
                                    ? null
                                    : baselineResult.getStatus(),
                            candidateResult == null
                                    ? null
                                    : candidateResult.getStatus(),

                            baselineCaseAccuracy,
                            candidateCaseAccuracy,
                            accuracyChange,

                            baselineResultCount,
                            candidateResultCount,
                            resultCountChange,

                            changeType
                    )
            );
        }

        return new RecommendationEvalRunComparisonResponse(
                baselineRunId,
                candidateRunId,
                baselineRun.getDatasetId(),

                toComparisonRunSnapshot(baselineRun),
                toComparisonRunSnapshot(candidateRun),

                metricComparison,
                caseComparisons,

                improvedCaseIds,
                regressedCaseIds,
                unchangedCaseIds,
                addedCaseIds,
                removedCaseIds
        );
    }

    /**
     * 人工标注单个评测案例结果。
     */
    @Transactional
    public RecommendationEvalCaseResultResponse annotateResult(
            Long runId,
            Long resultId,
            RecommendationEvalAnnotationRequest request,
            Long annotatedBy
    ) {
        /*
         * 复用现有查询方法校验运行记录存在。
         */
        getRun(runId);

        RecommendationEvalCaseResult result =
                caseResultMapper.selectById(resultId);

        /*
         * 不区分“结果不存在”和“结果不属于该运行”，
         * 避免通过接口探测其他运行中的结果。
         */
        if (result == null
                || !runId.equals(result.getRunId())) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "EVAL_RESULT_NOT_FOUND",
                    "评测案例结果不存在"
            );
        }

        String annotationNote = request.annotationNote();

        result.setRelevanceLabel(
                request.relevanceLabel().trim()
        );
        result.setAnnotationNote(
                annotationNote == null
                        || annotationNote.isBlank()
                        ? null
                        : annotationNote.trim()
        );
        result.setAnnotatedBy(annotatedBy);

        OffsetDateTime now = OffsetDateTime.now();

        result.setAnnotatedAt(now);
        result.setUpdatedAt(now);

        if (caseResultMapper.updateById(result) != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EVAL_RESULT_ANNOTATION_FAILED",
                    "评测结果标注失败，请稍后重试"
            );
        }

        return toCaseResultResponse(result);
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
                            null  // 评估模式不使用语义检索
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

    /**
     * 查询一次运行下的案例结果，并按caseId组织。
     */
    private Map<Long, RecommendationEvalCaseResult>
    loadCaseResultMap(Long runId) {
        List<RecommendationEvalCaseResult> results =
                caseResultMapper.selectList(
                        new LambdaQueryWrapper
                                <RecommendationEvalCaseResult>()
                                .eq(
                                        RecommendationEvalCaseResult::getRunId,
                                        runId
                                )
                                .orderByAsc(
                                        RecommendationEvalCaseResult::getCaseId
                                )
                );

        Map<Long, RecommendationEvalCaseResult> resultMap =
                new LinkedHashMap<>();

        for (RecommendationEvalCaseResult result
                : results == null
                ? List.<RecommendationEvalCaseResult>of()
                : results) {
            resultMap.put(
                    result.getCaseId(),
                    result
            );
        }

        return resultMap;
    }

    /**
     * 只有已经结束的运行可以参与比较。
     */
    private void ensureComparableRun(
            RecommendationEvalRun run
    ) {
        String status = run.getStatus();

        if ("PENDING".equalsIgnoreCase(status)
                || "RUNNING".equalsIgnoreCase(status)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EVAL_RUN_NOT_COMPLETED",
                    "评测运行尚未结束，暂时不能比较"
            );
        }
    }

    /**
     * 将运行转换为对比接口中的版本快照。
     */
    private RecommendationEvalRunComparisonResponse.RunSnapshot
    toComparisonRunSnapshot(
            RecommendationEvalRun run
    ) {
        return new RecommendationEvalRunComparisonResponse
                .RunSnapshot(
                run.getStatus(),
                run.getModelName(),
                run.getModelVersion(),
                run.getPromptVersion(),
                run.getAlgorithmVersion(),
                run.getDataVersion()
        );
    }

    /**
     * 解析数据库中保存的JSON对象。
     */
    private JsonNode parseStoredJsonObject(
            String rawJson,
            String fieldName
    ) {
        if (rawJson == null || rawJson.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            JsonNode node = objectMapper.readTree(rawJson);

            if (node != null && node.isObject()) {
                return node;
            }
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EVAL_STORED_JSON_INVALID",
                    fieldName + "格式错误"
            );
        }

        throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "EVAL_STORED_JSON_INVALID",
                fieldName + "不是JSON对象"
        );
    }

    /**
     * 读取运行指标中的小数。
     */
    private BigDecimal metricDecimal(
            JsonNode metrics,
            String fieldName
    ) {
        JsonNode value = metrics.path(fieldName);

        if (value.isNumber()) {
            return value.decimalValue()
                    .setScale(
                            4,
                            RoundingMode.HALF_UP
                    );
        }

        if (value.isTextual()) {
            try {
                return new BigDecimal(
                        value.asText().trim()
                ).setScale(
                        4,
                        RoundingMode.HALF_UP
                );
            } catch (NumberFormatException ignored) {
                return BigDecimal.ZERO.setScale(4);
            }
        }

        return BigDecimal.ZERO.setScale(4);
    }

    /**
     * 读取运行指标中的整数。
     */
    private int metricInteger(
            JsonNode metrics,
            String fieldName
    ) {
        JsonNode value = metrics.path(fieldName);

        if (value.isIntegralNumber()) {
            return value.asInt();
        }

        if (value.isTextual()) {
            try {
                return Integer.parseInt(
                        value.asText().trim()
                );
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        return 0;
    }

    /**
     * 读取单案例的条件准确率。
     */
    private BigDecimal caseConstraintAccuracy(
            RecommendationEvalCaseResult result
    ) {
        JsonNode hardMetrics =
                parseStoredJsonObject(
                        result.getHardConditionMetrics(),
                        "hardConditionMetrics"
                );

        return metricDecimal(
                hardMetrics,
                "constraintAccuracy"
        );
    }

    /**
     * 判断单个测试案例是提升、退步还是不变。
     */
    private String resolveCaseChangeType(
            RecommendationEvalCaseResult baseline,
            RecommendationEvalCaseResult candidate,
            BigDecimal baselineAccuracy,
            BigDecimal candidateAccuracy
    ) {
        if (baseline == null) {
            return "ADDED";
        }

        if (candidate == null) {
            return "REMOVED";
        }

        int baselineStatusRank =
                caseStatusRank(baseline.getStatus());

        int candidateStatusRank =
                caseStatusRank(candidate.getStatus());

        if (candidateStatusRank > baselineStatusRank) {
            return "IMPROVED";
        }

        if (candidateStatusRank < baselineStatusRank) {
            return "REGRESSED";
        }

        int accuracyComparison =
                candidateAccuracy.compareTo(
                        baselineAccuracy
                );

        if (accuracyComparison > 0) {
            return "IMPROVED";
        }

        if (accuracyComparison < 0) {
            return "REGRESSED";
        }

        return "UNCHANGED";
    }

    /**
     * SUCCESS优于SKIPPED，SKIPPED优于FAILED。
     */
    private int caseStatusRank(String status) {
        if (status == null) {
            return 0;
        }

        return switch (status.toUpperCase(Locale.ROOT)) {
            case "SUCCESS" -> 2;
            case "SKIPPED" -> 1;
            default -> 0;
        };
    }

    /**
     * 计算候选值相对于基准值的变化。
     */
    private BigDecimal decimalChange(
            BigDecimal baseline,
            BigDecimal candidate
    ) {
        return candidate.subtract(baseline)
                .setScale(
                        4,
                        RoundingMode.HALF_UP
                );
    }

    private Integer zeroIfNull(Integer value) {
        return value == null ? 0 : value;
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

    /**
     * 通用回归测试调用推荐评测后获得的内存结果。
     */
    public record AdHocRecommendationEvaluation(
            String extractedConstraints,
            String mergedConstraints,
            String recommendationSnapshot,
            String hardConditionMetrics,
            String failureReasons,
            int returnedCount,
            boolean exactConstraintMatch,
            boolean noResult,
            Set<Long> merchantIds,
            long durationMs
    ) {
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
