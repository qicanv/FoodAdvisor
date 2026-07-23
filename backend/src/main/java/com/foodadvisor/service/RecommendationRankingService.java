package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.AdjustmentSuggestionVO;
import com.foodadvisor.dto.recommendation.LimitingConditionVO;
import com.foodadvisor.dto.recommendation.RecommendationAdjustRequest;
import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.MatchedDishVO;
import com.foodadvisor.dto.recommendation.RecommendationRankRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.dto.recommendation.RecommendationBasisVO;
import com.foodadvisor.dto.recommendation.RecommendationWeights;
import com.foodadvisor.dto.recommendation.SemanticMatchResult;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.ChatSessionState;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantBusinessHours;
import com.foodadvisor.entity.Dish;
import com.foodadvisor.entity.Recommendation;
import com.foodadvisor.entity.RecommendationEvidence;
import com.foodadvisor.entity.RecommendationItem;
import com.foodadvisor.entity.MerchantHighlight;
import com.foodadvisor.entity.MerchantHighlightEvidence;
import com.foodadvisor.entity.Review;
import com.foodadvisor.mapper.ChatSessionMapper;
import com.foodadvisor.mapper.ChatSessionStateMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.DishMapper;
import com.foodadvisor.mapper.RecommendationEvidenceMapper;
import com.foodadvisor.mapper.RecommendationItemMapper;
import com.foodadvisor.mapper.RecommendationMapper;
import com.foodadvisor.mapper.MerchantHighlightMapper;
import com.foodadvisor.mapper.MerchantHighlightEvidenceMapper;
import com.foodadvisor.mapper.ReviewMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.foodadvisor.trace.AiTraceContext;
import com.foodadvisor.entity.AiRequestTraceStage;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RecommendationRankingService {
    private static final java.util.concurrent.atomic.AtomicInteger TRACE_SEQUENCE =
            new java.util.concurrent.atomic.AtomicInteger(2000);

    private static final String ALGORITHM_RULE_V1 =
            "RULE_V1";

    private static final String ALGORITHM_HYBRID_V1 =
            "HYBRID_V1";

    private static final String ALGORITHM_HYBRID_PARTIAL_V1 =
            "HYBRID_PARTIAL_V1";

    private static final String ALGORITHM_RULE_FALLBACK_V1 =
            "RULE_FALLBACK_V1";

    private static final String SESSION_STATUS_ACTIVE =
            "ACTIVE";

    private static final String MERCHANT_PLATFORM_ACTIVE =
            "ACTIVE";

    private static final String MERCHANT_OPERATION_OPERATING =
            "OPERATING";

    private static final String RECOMMENDATION_STATUS_PENDING =
            "PENDING";

    private static final String RECOMMENDATION_STATUS_SUCCESS =
            "SUCCESS";

    private static final String RECOMMENDATION_STATUS_NO_MATCH =
            "NO_MATCH";

    private static final String NO_MATCH_MESSAGE =
            "当前没有完全匹配的结果";

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal MAX_DISTANCE_KM =
            new BigDecimal("100");

    private static final int MAX_ADJUSTMENT_LIST_SIZE = 10;

    private static final int MAX_ADJUSTMENT_LIST_ITEM_LENGTH = 30;

    private static final BigDecimal DATABASE_SCORE_MIN =
            BigDecimal.ZERO;

    private static final BigDecimal DATABASE_SCORE_MAX =
            BigDecimal.ONE;

    /**
     * Java 端期望 AI Service 最终返回的单路最大文档数量。
     *
     * AI Service 启用 reranker 后，还会扩大 OpenSearch 初始召回，
     * 因此这里不直接使用接口允许的最大值 100。
     */
    private static final int MAX_SEMANTIC_TOP_K = 60;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatSessionStateMapper chatSessionStateMapper;
    private final MerchantMapper merchantMapper;
    private final RecommendationMapper recommendationMapper;
    private final RecommendationItemMapper recommendationItemMapper;
    private final DishMapper dishMapper;
    private final RecommendationEvidenceMapper recommendationEvidenceMapper;
    private final DishMatchingService dishMatchingService;
    private final MerchantHighlightMapper merchantHighlightMapper;
    private final MerchantHighlightEvidenceMapper merchantHighlightEvidenceMapper;
    private final ReviewMapper reviewMapper;
    private final MerchantHighlightMatchingService merchantHighlightMatchingService;
    private final MatchScoreCalculator matchScoreCalculator;
    private final AIClientService aiClientService;
    private final MerchantBusinessHoursService businessHoursService;
    private final ObjectMapper objectMapper;
    @Autowired(required = false)
    private AiRequestTraceService traceService;

    private enum SemanticSearchStatus {
        /**
         * 三个来源均正常完成检索。
         */
        FULL,

        /**
         * 至少一个来源正常，至少一个来源失败。
         */
        PARTIAL,

        /**
         * 有语义查询，但三个来源都不可用。
         */
        UNAVAILABLE,

        /**
         * 没有自然语言查询，或硬过滤后没有候选商家。
         */
        SKIPPED
    }

    private record SemanticSearchOutcome(
            Map<Long, SemanticMatchResult> matches,
            SemanticSearchStatus status
    ) {
    }

    private record RankingCalculation(
            List<RecommendationItemVO> results,
            SemanticSearchStatus semanticStatus
    ) {
    }

    public RecommendationRankingService(
            ChatSessionMapper chatSessionMapper,
            ChatSessionStateMapper chatSessionStateMapper,
            MerchantMapper merchantMapper,
            RecommendationMapper recommendationMapper,
            RecommendationItemMapper recommendationItemMapper,
            DishMapper dishMapper,
            RecommendationEvidenceMapper recommendationEvidenceMapper,
            DishMatchingService dishMatchingService,
            MerchantHighlightMapper merchantHighlightMapper,
            MerchantHighlightEvidenceMapper merchantHighlightEvidenceMapper,
            ReviewMapper reviewMapper,
            MerchantHighlightMatchingService merchantHighlightMatchingService,
            MatchScoreCalculator matchScoreCalculator,
            AIClientService aiClientService,
            MerchantBusinessHoursService businessHoursService,
            ObjectMapper objectMapper
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatSessionStateMapper = chatSessionStateMapper;
        this.merchantMapper = merchantMapper;
        this.recommendationMapper = recommendationMapper;
        this.recommendationItemMapper = recommendationItemMapper;
        this.dishMapper = dishMapper;
        this.recommendationEvidenceMapper =
                recommendationEvidenceMapper;
        this.dishMatchingService = dishMatchingService;
        this.merchantHighlightMapper = merchantHighlightMapper;
        this.merchantHighlightEvidenceMapper = merchantHighlightEvidenceMapper;
        this.reviewMapper = reviewMapper;
        this.merchantHighlightMatchingService = merchantHighlightMatchingService;
        this.matchScoreCalculator = matchScoreCalculator;
        this.aiClientService = aiClientService;
        this.businessHoursService = businessHoursService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecommendationRankResponse rank(
            Long sessionId,
            RecommendationRankRequest request
    ) {
        AiTraceContext context = standaloneContext(
                "rank-" + UUID.randomUUID(), sessionId,
                request == null ? null : request.getUserId(),
                "DINING_RECOMMENDATION_RANK");
        recordRequestReceived(context, "RECOMMENDATION");
        try {
            RecommendationRankResponse response = rank(sessionId, request, context);
            completeStandalone(context, response, "RECOMMENDATION");
            return response;
        } catch (DataAccessException exception) {
            failTrace(context, exception);
            throw dataServiceException(exception);
        } catch (RuntimeException exception) {
            failTrace(context, exception);
            throw exception;
        }
    }

    @Transactional
    public RecommendationRankResponse rank(
            Long sessionId,
            RecommendationRankRequest request,
            AiTraceContext context
    ) {
        try {
            return rankInternal(sessionId, request, context);
        } catch (DataAccessException exception) {
            closeRunningStages(context, exception);
            throw dataServiceException(exception);
        } catch (RuntimeException exception) {
            closeRunningStages(context, exception);
            throw exception;
        }
    }

    @Transactional
    public RecommendationRankResponse adjustAndRank(
            Long sessionId,
            RecommendationAdjustRequest request
    ) {
        AiTraceContext context = standaloneContext(
                "rank-" + UUID.randomUUID(), sessionId,
                request == null ? null : request.getUserId(),
                "DINING_RECOMMENDATION_ADJUST");
        recordRequestReceived(context, "RECOMMENDATION_ADJUST");
        try {
            RecommendationRankResponse response =
                    adjustAndRank(sessionId, request, context);
            completeStandalone(context, response, "RECOMMENDATION_ADJUST");
            return response;
        } catch (DataAccessException exception) {
            failTrace(context, exception);
            throw dataServiceException(exception);
        } catch (RuntimeException exception) {
            failTrace(context, exception);
            throw exception;
        }
    }

    @Transactional
    public RecommendationRankResponse adjustAndRank(
            Long sessionId,
            RecommendationAdjustRequest request,
            AiTraceContext context
    ) {
        try {
            return adjustAndRankInternal(sessionId, request, context);
        } catch (RuntimeException exception) {
            closeRunningStages(context, exception);
            throw exception;
        }
    }

    private RecommendationRankResponse adjustAndRankInternal(
            Long sessionId,
            RecommendationAdjustRequest request,
            AiTraceContext context
    ) {
        validateAdjustRequest(sessionId, request);

        ChatSession session =
                loadAndValidateSession(
                        sessionId,
                        request.getUserId()
                );

        ChatSessionState sessionState =
                loadSessionState(sessionId);

        ConstraintState constraints =
                parseConstraints(sessionState);

        applyAdjustment(
                constraints,
                request.getField(),
                request.getValue()
        );

        saveAdjustedSessionState(
                sessionState,
                constraints
        );

        RecommendationRankRequest rankRequest =
                new RecommendationRankRequest();
        rankRequest.setUserId(session.getUserId());
        rankRequest.setUserLatitude(
                request.getUserLatitude()
        );
        rankRequest.setUserLongitude(
                request.getUserLongitude()
        );
        rankRequest.setWeights(
                request.getWeights() == null
                        ? new RecommendationWeights()
                        : request.getWeights()
        );

        return rankInternal(sessionId, rankRequest, context);
    }

    private RecommendationRankResponse rankInternal(
            Long sessionId,
            RecommendationRankRequest request,
            AiTraceContext context
    ) {
        validateRequest(sessionId, request);

        ChatSession session =
                loadAndValidateSession(
                        sessionId,
                        request.getUserId()
                );

        ChatSessionState sessionState =
                loadSessionState(sessionId);

        ensureNoPendingConflict(sessionState);

        ConstraintState constraints =
                parseConstraints(sessionState);

        validateLocationForDistance(
                constraints,
                request.getUserLatitude(),
                request.getUserLongitude()
        );

        RecommendationWeights weights =
                resolveWeights(request);

        String semanticQuery = request.getQuery() != null
                && !request.getQuery().isBlank()
                ? request.getQuery().trim()
                : null;

        Recommendation recommendation =
                createPendingRecommendation(
                        session,
                        constraints,
                        weights,
                        context,
                        semanticQuery
                );

        AiRequestTraceStage rankingStage = startStage(
                context,
                "RECOMMENDATION_RANKING",
                java.util.Map.of(
                        "responseType",
                        "RECOMMENDATION",
                        "semanticEnabled",
                        semanticQuery != null
                )
        );

        List<Merchant> candidates =
                loadCandidateMerchants();

        Map<Long, List<MerchantBusinessHours>> businessHours =
                loadBusinessHours(candidates, constraints);

        RankingCalculation calculation =
                calculateResults(
                        candidates,
                        constraints,
                        weights,
                        request.getUserLatitude(),
                        request.getUserLongitude(),
                        businessHours,
                        context,
                        semanticQuery
                );

        List<RecommendationItemVO> results =
                calculation.results();

        SemanticSearchStatus semanticStatus =
                calculation.semanticStatus();

        String algorithmVersion =
                algorithmVersionFor(semanticStatus);

        boolean degraded =
                isSemanticDegraded(semanticStatus);

        applyDishKeywordFilter(
                candidates,
                constraints,
                results
        );
        applyHighlightEvidence(candidates, constraints, results);

        results.sort(this::compareResults);
        assignRanks(results);
        completeStage(
                rankingStage,
                java.util.Map.of(
                        "resultCount",
                        results.size(),
                        "semanticStatus",
                        semanticStatus.name(),
                        "algorithmVersion",
                        algorithmVersion,
                        "degraded",
                        degraded
                ),
                rankingEngineFor(semanticStatus),
                "NOT_APPLICABLE"
        );

        AiRequestTraceStage evidenceStage = startStage(
                context, "EVIDENCE_SELECTION",
                java.util.Map.of("resultCount", results.size()));
        saveRecommendationItems(
                recommendation.getId(),
                results
        );
        completeStage(evidenceStage,
                java.util.Map.of("resultCount", results.size()),
                "RULE_ENGINE", "NOT_APPLICABLE");

        AiRequestTraceStage persistStage = startStage(
                context, "RESULT_PERSIST",
                java.util.Map.of("recommendationId", recommendation.getId()));
        completeRecommendation(
                recommendation,
                results,
                semanticStatus
        );
        completeStage(persistStage,
                java.util.Map.of("recommendationId", recommendation.getId(),
                        "resultCount", results.size()),
                "RULE_ENGINE", "NOT_APPLICABLE");

        NoMatchAnalysis noMatchAnalysis =
                results.isEmpty()
                        ? analyzeNoMatch(
                        candidates,
                        constraints,
                        request.getUserLatitude(),
                        request.getUserLongitude()
                )
                        : new NoMatchAnalysis(
                        List.of(),
                        List.of()
                );

        RecommendationRankResponse response = buildResponse(
                recommendation,
                constraints,
                weights,
                results,
                noMatchAnalysis,
                semanticStatus
        );
        response.setTraceId(context.traceId());
        return response;
    }

    private void validateRequest(
            Long sessionId,
            RecommendationRankRequest request
    ) {
        if (sessionId == null || sessionId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SESSION_REQUIRED",
                    "sessionId不能为空且必须大于0"
            );
        }

        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REQUEST_REQUIRED",
                    "推荐排序请求不能为空"
            );
        }

        if (request.getUserId() == null
                || request.getUserId() <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "USER_REQUIRED",
                    "userId不能为空且必须大于0"
            );
        }

        if (!request.isLocationPairValid()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "LOCATION_PAIR_INVALID",
                    "userLatitude和userLongitude必须同时提供或同时不提供"
            );
        }

        RecommendationWeights weights =
                request.getWeights();

        if (weights != null
                && !weights.isTotalWeightValid()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "RECOMMENDATION_WEIGHTS_INVALID",
                    "推荐权重总和必须等于100"
            );
        }
    }

    private void validateAdjustRequest(
            Long sessionId,
            RecommendationAdjustRequest request
    ) {
        if (sessionId == null || sessionId <= 0
                || request == null
                || request.getUserId() == null
                || request.getUserId() <= 0) {
            throw invalidAdjustment(
                    "调整请求缺少有效的sessionId或userId"
            );
        }

        if (request.getUserLatitude() == null
                ^ request.getUserLongitude() == null) {
            throw invalidAdjustment(
                    "userLatitude和userLongitude必须同时提供或同时不提供"
            );
        }

        if (request.getWeights() != null
                && !request.getWeights()
                .isTotalWeightValid()) {
            throw invalidAdjustment(
                    "推荐权重总和必须等于100"
            );
        }
    }

    private ChatSession loadAndValidateSession(
            Long sessionId,
            Long userId
    ) {
        ChatSession session =
                chatSessionMapper.selectById(sessionId);

        if (session == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SESSION_NOT_FOUND",
                    "未找到指定的对话会话"
            );
        }

        if (session.getUserId() == null
                || !session.getUserId().equals(userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "SESSION_ACCESS_DENIED",
                    "无权访问该对话会话"
            );
        }

        if (!SESSION_STATUS_ACTIVE.equalsIgnoreCase(
                session.getStatus()
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SESSION_NOT_ACTIVE",
                    "当前对话会话不是可用状态"
            );
        }

        return session;
    }

    private ChatSessionState loadSessionState(
            Long sessionId
    ) {
        ChatSessionState state =
                chatSessionStateMapper.selectOne(
                        new LambdaQueryWrapper
                                <ChatSessionState>()
                                .eq(
                                        ChatSessionState::getSessionId,
                                        sessionId
                                )
                                .last("LIMIT 1")
                );

        if (state == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SESSION_STATE_NOT_FOUND",
                    "当前会话尚未生成消费需求状态"
            );
        }

        return state;
    }

    private void ensureNoPendingConflict(
            ChatSessionState state
    ) {
        if ("CONFIRMING".equalsIgnoreCase(
                state.getConversationStage()
        )) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONSTRAINT_CONFIRMATION_REQUIRED",
                    "当前会话仍有待确认的冲突条件"
            );
        }

        String pendingJson =
                state.getPendingConfirmation();

        if (pendingJson == null
                || pendingJson.isBlank()) {
            return;
        }

        try {
            JsonNode pendingNode =
                    objectMapper.readTree(pendingJson);

            boolean noPendingConflict =
                    pendingNode == null
                            || pendingNode.isNull()
                            || (pendingNode.isArray()
                            && pendingNode.isEmpty());

            if (!noPendingConflict) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "CONSTRAINT_CONFIRMATION_REQUIRED",
                        "当前会话仍有待确认的冲突条件"
                );
            }
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PENDING_CONFIRMATION_PARSE_FAILED",
                    "待确认冲突数据解析失败"
            );
        }
    }

    private ConstraintState parseConstraints(
            ChatSessionState state
    ) {
        String constraintsJson =
                state.getCurrentConstraints();

        if (constraintsJson == null
                || constraintsJson.isBlank()) {
            return new ConstraintState();
        }

        try {
            ConstraintState constraints =
                    objectMapper.readValue(
                            constraintsJson,
                            ConstraintState.class
                    );

            return constraints == null
                    ? new ConstraintState()
                    : constraints;
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CONSTRAINTS_PARSE_FAILED",
                    "会话消费需求数据解析失败"
            );
        }
    }

    private void validateLocationForDistance(
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude
    ) {
        if (constraints == null
                || constraints.getDistanceKm() == null
                || constraints.getDistanceKm()
                .compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        if (userLatitude == null
                || userLongitude == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "USER_LOCATION_REQUIRED",
                    "已设置距离要求，但缺少当前位置"
            );
        }
    }

    private RecommendationWeights resolveWeights(
            RecommendationRankRequest request
    ) {
        RecommendationWeights weights =
                request.getWeights();

        if (weights == null) {
            weights = new RecommendationWeights();
        }

        if (!weights.isTotalWeightValid()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "RECOMMENDATION_WEIGHTS_INVALID",
                    "推荐权重总和必须等于100"
            );
        }

        return weights;
    }

    private Recommendation createPendingRecommendation(
            ChatSession session,
            ConstraintState constraints,
            RecommendationWeights weights,
            AiTraceContext context,
            String semanticQuery
    ) {
        Recommendation recommendation =
                new Recommendation();

        recommendation.setUserId(session.getUserId());
        recommendation.setSessionId(session.getId());
        recommendation.setRequestId(
                context.requestId()
        );
        recommendation.setTraceId(
                context.traceId()
        );
        recommendation.setQueryText(
                semanticQuery == null
                        ? "基于会话结构化需求执行商家规则排序"
                        : "基于会话结构化需求与自然语言语义执行商家混合排序"
        );
        recommendation.setParsedConstraints(
                serializeToJson(
                        constraints,
                        "CONSTRAINTS_SERIALIZE_FAILED",
                        "推荐条件快照序列化失败"
                )
        );
        recommendation.setAlgorithmVersion(
                ALGORITHM_RULE_V1
        );
        recommendation.setWeightSnapshot(
                serializeToJson(
                        weights,
                        "WEIGHTS_SERIALIZE_FAILED",
                        "推荐权重快照序列化失败"
                )
        );
        recommendation.setModelName(null);
        recommendation.setModelVersion(null);
        recommendation.setStatus(
                RECOMMENDATION_STATUS_PENDING
        );
        recommendation.setResultCount(0);
        recommendation.setCreatedAt(
                OffsetDateTime.now()
        );

        int insertedRows =
                recommendationMapper.insert(
                        recommendation
                );

        if (insertedRows != 1
                || recommendation.getId() == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "RECOMMENDATION_CREATE_FAILED",
                    "推荐记录创建失败"
            );
        }

        return recommendation;
    }

    private List<Merchant> loadCandidateMerchants() {
        List<Merchant> merchants =
                merchantMapper.selectList(
                        new LambdaQueryWrapper<Merchant>()
                                .eq(
                                        Merchant::getPlatformStatus,
                                        MERCHANT_PLATFORM_ACTIVE
                                )
                                .eq(
                                        Merchant::getOperationStatus,
                                        MERCHANT_OPERATION_OPERATING
                                )
                                .isNull(
                                        Merchant::getDeletedAt
                                )
                                .orderByAsc(
                                        Merchant::getId
                                )
                );

        return merchants == null
                ? new ArrayList<>()
                : merchants;
    }

    private RankingCalculation calculateResults(
            List<Merchant> candidates,
            ConstraintState constraints,
            RecommendationWeights weights,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            Map<Long, List<MerchantBusinessHours>> businessHours,
            AiTraceContext context,
            String originalQuery
    ) {
        /*
         * 第一阶段：先执行确定性的硬条件过滤。
         *
         * 只有满足预算、菜系、商家类型、评分、距离和营业时间的商家，
         * 才允许进入后续语义召回，避免不符合条件的商家占用向量 topK。
         */
        List<Merchant> eligibleCandidates = new ArrayList<>();

        Map<Long, MerchantBusinessHoursService.BusinessHoursMatch>
                eligibleBusinessHours =
                new java.util.LinkedHashMap<>();

        for (Merchant merchant : candidates) {
            boolean passedHardFilters =
                    matchScoreCalculator.passesHardFilters(
                            merchant,
                            constraints,
                            userLatitude,
                            userLongitude
                    );

            if (!passedHardFilters) {
                continue;
            }

            MerchantBusinessHoursService.BusinessHoursMatch
                    businessHoursMatch =
                    businessHoursService.match(
                            constraints,
                            businessHours.get(merchant.getId())
                    );

            if (!businessHoursMatch.matched()) {
                continue;
            }

            eligibleCandidates.add(merchant);
            eligibleBusinessHours.put(
                    merchant.getId(),
                    businessHoursMatch
            );
        }

        /*
         * 第二阶段：只在满足硬条件的商家范围内做语义检索和 rerank。
         */
        SemanticSearchOutcome semanticOutcome =
                performSemanticSearch(
                        eligibleCandidates,
                        originalQuery,
                        context
                );

        Map<Long, SemanticMatchResult> semanticMatches =
                semanticOutcome.matches();

        /*
         * 第三阶段：计算规则分、语义分和最终综合分。
         */
        List<RecommendationItemVO> results =
                new ArrayList<>();

        for (Merchant merchant : eligibleCandidates) {
            MerchantBusinessHoursService.BusinessHoursMatch
                    businessHoursMatch =
                    eligibleBusinessHours.get(merchant.getId());

            matchScoreCalculator.calculate(
                    merchant,
                    constraints,
                    weights,
                    userLatitude,
                    userLongitude,
                    semanticMatches
            ).ifPresent(result -> {
                matchScoreCalculator.addBusinessHoursEvidence(
                        result,
                        businessHoursMatch.evidence()
                );

                results.add(result);
            });
        }

        return new RankingCalculation(
                results,
                semanticOutcome.status()
        );
    }

    /**
     * 三路分源语义检索 + 加权聚合。
     *
     * 将用户的原始自然语言查询在 MERCHANT_INTRO、MENU、REVIEW
     * 三个来源上分别做向量检索，然后按 merchantId 聚合，
     * 得到每个商家的加权语义分和可信度。
     *
     * @param candidates    候选商家列表
     * @param originalQuery 用户原始自然语言查询，为 null 时语义检索不启用
     * @param context       追踪上下文
     * @return 语义匹配结果及检索状态
     */
    private SemanticSearchOutcome performSemanticSearch(
            List<Merchant> candidates,
            String originalQuery,
            AiTraceContext context
    ) {
        if (originalQuery == null
                || originalQuery.isBlank()
                || candidates == null
                || candidates.isEmpty()) {
            return new SemanticSearchOutcome(
                    Map.of(),
                    SemanticSearchStatus.SKIPPED
            );
        }

        List<Long> merchantIds = candidates.stream()
                .map(Merchant::getId)
                .toList();

        // ---- 根据硬过滤后的候选商家数量动态确定召回规模 ----
        int candidateCount = merchantIds.size();

        int introTopK = calculateSemanticTopK(
                candidateCount,
                5,
                1
        );

        int menuTopK = calculateSemanticTopK(
                candidateCount,
                10,
                2
        );

        int reviewTopK = calculateSemanticTopK(
                candidateCount,
                15,
                3
        );

        // ---- 三路检索 ----
        JsonNode introHits = safeSearch(
                originalQuery,
                merchantIds,
                List.of("MERCHANT_INTRO"),
                introTopK,
                context
        );

        JsonNode menuHits = safeSearch(
                originalQuery,
                merchantIds,
                List.of("MENU"),
                menuTopK,
                context
        );

        JsonNode reviewHits = safeSearch(
                originalQuery,
                merchantIds,
                List.of("REVIEW"),
                reviewTopK,
                context
        );

        // 统计各路的可用状态
        boolean introAvailable =
                isSearchAvailable(introHits);

        boolean menuAvailable =
                isSearchAvailable(menuHits);

        boolean reviewAvailable =
                isSearchAvailable(reviewHits);

        int availableCount = (introAvailable ? 1 : 0)
                + (menuAvailable ? 1 : 0)
                + (reviewAvailable ? 1 : 0);

        if (availableCount == 0) {
            return new SemanticSearchOutcome(
                    Map.of(),
                    SemanticSearchStatus.UNAVAILABLE
            );
        }

        SemanticSearchStatus semanticStatus =
                availableCount == 3
                        ? SemanticSearchStatus.FULL
                        : SemanticSearchStatus.PARTIAL;

        // 如果有路失败，重新分配权重
        BigDecimal[] weights = redistributeWeights(
                introAvailable, menuAvailable, reviewAvailable);

        // ---- 聚合 ----
        Map<Long, SemanticMatchResult> results =
                new java.util.LinkedHashMap<>();

        // 从各路的 hits 中提取最高分
        Map<Long, BigDecimal> introMax =
                extractMaxScores(introHits);
        Map<Long, BigDecimal> menuMax =
                extractMaxScores(menuHits);
        Map<Long, BigDecimal> reviewMax =
                extractMaxScores(reviewHits);

        // 从各路的 hits 中提取最佳 evidence
        Map<Long, List<SemanticMatchResult.SemanticEvidenceItem>> introEvidence =
                extractTopEvidence(introHits, 2);
        Map<Long, List<SemanticMatchResult.SemanticEvidenceItem>> menuEvidence =
                extractTopEvidence(menuHits, 2);
        Map<Long, List<SemanticMatchResult.SemanticEvidenceItem>> reviewEvidence =
                extractTopEvidence(reviewHits, 3);

        // 遍历所有候选商家，聚合分路分数
        for (Merchant merchant : candidates) {
            Long merchantId = merchant.getId();
            SemanticMatchResult result =
                    new SemanticMatchResult();

            result.setMerchantId(merchantId);
            result.setIntroScore(
                    introMax.get(merchantId)
            );
            result.setMenuScore(
                    menuMax.get(merchantId)
            );
            result.setReviewScore(
                    reviewMax.get(merchantId)
            );

            // 汇总 evidence
            List<SemanticMatchResult.SemanticEvidenceItem> evidence =
                    new ArrayList<>();

            if (introEvidence.containsKey(merchantId)) {
                evidence.addAll(
                        introEvidence.get(merchantId)
                );
            }

            if (menuEvidence.containsKey(merchantId)) {
                evidence.addAll(
                        menuEvidence.get(merchantId)
                );
            }

            if (reviewEvidence.containsKey(merchantId)) {
                evidence.addAll(
                        reviewEvidence.get(merchantId)
                );
            }

            result.setEvidenceItems(evidence);

            // 至少有一路命中才保留
            if (result.getIntroScore() == null
                    && result.getMenuScore() == null
                    && result.getReviewScore() == null) {
                continue;
            }

            if (availableCount < 3) {
                result.recomputeWithWeights(
                        weights[0],
                        weights[1],
                        weights[2]
                );
            } else {
                result.compute();
            }

            results.put(merchantId, result);
        }

        return new SemanticSearchOutcome(
                results,
                semanticStatus
        );
    }

    private String algorithmVersionFor(
            SemanticSearchStatus status
    ) {
        return switch (status) {
            case FULL ->
                    ALGORITHM_HYBRID_V1;
            case PARTIAL ->
                    ALGORITHM_HYBRID_PARTIAL_V1;
            case UNAVAILABLE ->
                    ALGORITHM_RULE_FALLBACK_V1;
            case SKIPPED ->
                    ALGORITHM_RULE_V1;
        };
    }

    private boolean isSemanticDegraded(
            SemanticSearchStatus status
    ) {
        return status == SemanticSearchStatus.PARTIAL
                || status == SemanticSearchStatus.UNAVAILABLE;
    }

    private String rankingEngineFor(
            SemanticSearchStatus status
    ) {
        return switch (status) {
            case FULL, PARTIAL ->
                    "HYBRID_ENGINE";
            case UNAVAILABLE, SKIPPED ->
                    "RULE_ENGINE";
        };
    }

    private String recommendationQueryTextFor(
            SemanticSearchStatus status
    ) {
        return switch (status) {
            case FULL ->
                    "基于结构化条件、向量检索和重排序执行商家混合排序";
            case PARTIAL ->
                    "部分语义来源不可用，基于可用语义结果和结构化条件执行混合排序";
            case UNAVAILABLE ->
                    "语义检索不可用，已降级为结构化条件规则排序";
            case SKIPPED ->
                    "基于会话结构化需求执行商家规则排序";
        };
    }

    private String recommendationReplyTextFor(
            SemanticSearchStatus status,
            int resultCount
    ) {
        return switch (status) {
            case FULL ->
                    "混合排序完成，共返回"
                            + resultCount
                            + "家匹配商家";
            case PARTIAL ->
                    "部分语义来源降级，混合排序完成，共返回"
                            + resultCount
                            + "家匹配商家";
            case UNAVAILABLE ->
                    "语义检索不可用，已使用规则排序，共返回"
                            + resultCount
                            + "家匹配商家";
            case SKIPPED ->
                    "规则排序完成，共返回"
                            + resultCount
                            + "家匹配商家";
        };
    }

    /**
     * 根据硬过滤后的候选商家数量计算单路语义召回数量。
     *
     * @param candidateCount      合格候选商家数量
     * @param minimumTopK         该来源的最小召回数量
     * @param documentsPerMerchant 每家商家预计需要保留的文档数量
     */
    private int calculateSemanticTopK(
            int candidateCount,
            int minimumTopK,
            int documentsPerMerchant
    ) {
        long desiredTopK = Math.max(
                minimumTopK,
                (long) candidateCount * documentsPerMerchant
        );

        return (int) Math.min(
                desiredTopK,
                MAX_SEMANTIC_TOP_K
        );
    }

    /**
     * 安全调用语义检索，失败返回空 JSON。
     */
    private JsonNode safeSearch(
            String query,
            List<Long> merchantIds,
            List<String> sourceTypes,
            int topK,
            AiTraceContext context
    ) {
        try {
            return aiClientService.semanticSearch(
                    query, merchantIds, sourceTypes, topK, context);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 判断语义检索服务是否正常完成。
     *
     * SUCCESS 且结果为空表示检索正常，只是当前来源没有相关命中；
     * 只有响应为空或 status != SUCCESS 才表示该检索分路不可用。
     */
    private boolean isSearchAvailable(JsonNode response) {
        if (response == null) {
            return false;
        }

        String status = response.path("status").asText("");

        return "SUCCESS".equalsIgnoreCase(status);
    }

    /**
     * 当部分来源不可用时，重新分配权重。
     *
     * @return [introWeight, menuWeight, reviewWeight]
     */
    private BigDecimal[] redistributeWeights(
            boolean introAvailable,
            boolean menuAvailable,
            boolean reviewAvailable
    ) {
        BigDecimal total = BigDecimal.ZERO;
        if (introAvailable)  total = total.add(SemanticMatchResult.INTRO_WEIGHT);
        if (menuAvailable)   total = total.add(SemanticMatchResult.MENU_WEIGHT);
        if (reviewAvailable) total = total.add(SemanticMatchResult.REVIEW_WEIGHT);

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
        }

        return new BigDecimal[] {
                introAvailable
                        ? SemanticMatchResult.INTRO_WEIGHT.divide(total, 6, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO,
                menuAvailable
                        ? SemanticMatchResult.MENU_WEIGHT.divide(total, 6, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO,
                reviewAvailable
                        ? SemanticMatchResult.REVIEW_WEIGHT.divide(total, 6, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO,
        };
    }

    /**
     * 从检索结果中提取每个 merchant 的最高相似度分数。
     */
    private Map<Long, BigDecimal> extractMaxScores(JsonNode response) {
        Map<Long, BigDecimal> scores = new java.util.HashMap<>();
        if (response == null) return scores;

        JsonNode results = response.path("data").path("results");
        if (!results.isArray()) return scores;

        for (JsonNode hit : results) {
            long merchantId = hit.path("merchantId").asLong();
            BigDecimal score = new BigDecimal(hit.path("score").asDouble());
            scores.merge(merchantId, score,
                    (existing, incoming) ->
                            existing.compareTo(incoming) >= 0 ? existing : incoming);
        }
        return scores;
    }

    /**
     * 从检索结果中提取每个 merchant 的前 N 条 evidence。
     */
    private Map<Long, List<SemanticMatchResult.SemanticEvidenceItem>>
            extractTopEvidence(JsonNode response, int maxPerMerchant) {
        Map<Long, List<SemanticMatchResult.SemanticEvidenceItem>> evidence =
                new java.util.LinkedHashMap<>();
        if (response == null) return evidence;

        JsonNode results = response.path("data").path("results");
        if (!results.isArray()) return evidence;

        for (JsonNode hit : results) {
            long merchantId = hit.path("merchantId").asLong();
            List<SemanticMatchResult.SemanticEvidenceItem> items =
                    evidence.computeIfAbsent(merchantId, k -> new ArrayList<>());
            if (items.size() >= maxPerMerchant) continue;

            SemanticMatchResult.SemanticEvidenceItem item =
                    new SemanticMatchResult.SemanticEvidenceItem();
            item.setSourceType(hit.path("sourceType").asText(""));
            item.setSourceId(hit.path("sourceId").asLong(0));
            String text = hit.path("text").asText("");
            item.setText(text.length() > 150 ? text.substring(0, 150) : text);
            item.setScore(new BigDecimal(hit.path("score").asDouble(0)));
            items.add(item);
        }
        return evidence;
    }

private void applyDishKeywordFilter(
            List<Merchant> candidates,
            ConstraintState constraints,
            List<RecommendationItemVO> results
    ) {
        boolean hasDishKeywords =
                constraints.getDishKeywords() != null
                        && !constraints.getDishKeywords().isEmpty();
        boolean hasTastePreferences =
                constraints.getTastePreferences() != null
                        && !constraints.getTastePreferences().isEmpty();
        if (!hasDishKeywords && !hasTastePreferences) {
            return;
        }
        List<Long> merchantIds = candidates.stream()
                .map(Merchant::getId)
                .toList();
        if (merchantIds.isEmpty()) {
            results.clear();
            return;
        }
        List<Dish> dishes =
                dishMapper.selectActiveByMerchantIds(merchantIds);
        Map<Long, List<Dish>> grouped = new java.util.LinkedHashMap<>();
        for (Dish dish : dishes == null ? List.<Dish>of() : dishes) {
            grouped.computeIfAbsent(
                    dish.getMerchantId(),
                    ignored -> new ArrayList<>()
            ).add(dish);
        }
        Map<Long, List<MatchedDishVO>> matches =
                dishMatchingService.match(constraints, grouped);
        if (hasDishKeywords) {
            results.removeIf(result ->
                    !matches.containsKey(result.getMerchantId()));
        }
        for (RecommendationItemVO result : results) {
            if (!matches.containsKey(result.getMerchantId())) {
                continue;
            }
            result.setMatchedDishes(
                    new ArrayList<>(
                            matches.get(result.getMerchantId())
                    )
            );
            matchScoreCalculator.addDishEvidence(
                    result,
                    matches.get(result.getMerchantId())
            );
        }
    }

    private void applyHighlightEvidence(
            List<Merchant> candidates,
            ConstraintState constraints,
            List<RecommendationItemVO> results
    ) {
        if (!merchantHighlightMatchingService.isRelevant(constraints)
                || results.isEmpty()) {
            return;
        }
        List<Long> merchantIds = results.stream()
                .map(RecommendationItemVO::getMerchantId).distinct().toList();
        List<MerchantHighlight> highlights =
                merchantHighlightMapper.selectActiveByMerchantIds(merchantIds);
        highlights = highlights == null ? List.of() : highlights;
        List<Long> highlightIds = highlights.stream()
                .map(MerchantHighlight::getId).toList();
        List<MerchantHighlightEvidence> links =
                highlightIds.isEmpty() ? List.of()
                        : merchantHighlightEvidenceMapper
                        .selectByHighlightIds(highlightIds);
        List<Long> reviewIds = (links == null ? List.<MerchantHighlightEvidence>of() : links)
                .stream().map(MerchantHighlightEvidence::getReviewId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Review> reviews = reviewIds.isEmpty()
                ? Map.of()
                : reviewMapper.selectByIds(reviewIds).stream()
                .collect(java.util.stream.Collectors.toMap(Review::getId, review -> review));
        Map<Long, List<RecommendationBasisVO>> matches =
                merchantHighlightMatchingService.match(
                        constraints, highlights, links, reviews);
        Map<Long, Merchant> merchantMap = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(
                        Merchant::getId, merchant -> merchant));
        for (RecommendationItemVO result : results) {
            List<RecommendationBasisVO> bases = new ArrayList<>(
                    matches.getOrDefault(result.getMerchantId(), List.of()));
            addMerchantBasis(constraints, merchantMap.get(result.getMerchantId()), bases);
            if (bases.isEmpty()) continue;
            result.setRecommendationBases(new ArrayList<>(bases));
            long reviewBasisCount = bases.stream()
                    .filter(basis -> "REVIEW".equals(basis.getSourceType()))
                    .count();
            BigDecimal boost = BigDecimal.valueOf(
                    Math.min(3, reviewBasisCount));
            result.setFinalScore(result.getFinalScore().add(boost)
                    .min(ONE_HUNDRED));
            result.getMatchedConditions().add(0,
                    "评价亮点：" + bases.get(0).getTitle());
            result.setReason(result.getMerchantName() + "满足"
                    + result.getMatchedConditions().stream().limit(2)
                    .collect(java.util.stream.Collectors.joining("、"))
                    + "，综合匹配分为"
                    + result.getFinalScore().stripTrailingZeros().toPlainString()
                    + "分。");
        }
    }

    private void addMerchantBasis(
            ConstraintState constraints,
            Merchant merchant,
            List<RecommendationBasisVO> bases
    ) {
        if (merchant == null || bases.size() >= 3) return;
        String condition = null;
        String summary = null;
        if (constraints.getEnvironmentRequirements() != null
                && merchant.getEnvironmentTags() != null
                && constraints.getEnvironmentRequirements().stream()
                .anyMatch(merchant.getEnvironmentTags()::contains)) {
            condition = "environmentRequirements";
            summary = "商家资料中的环境标签：" + merchant.getEnvironmentTags();
        }
        if (summary == null) return;
        RecommendationBasisVO basis = new RecommendationBasisVO();
        basis.setSourceType("MERCHANT");
        basis.setSourceId(merchant.getId());
        basis.setMerchantId(merchant.getId());
        basis.setTitle("商家资料");
        basis.setSummary(summary);
        basis.setMatchedCondition(condition);
        basis.setRelevanceScore(new BigDecimal("1.0"));
        bases.add(basis);
    }

    private Map<Long, List<MerchantBusinessHours>> loadBusinessHours(
            List<Merchant> candidates,
            ConstraintState constraints
    ) {
        if (!businessHoursService.hasBusinessTimeConstraint(
                constraints
        )) {
            return Map.of();
        }
        return businessHoursService.loadGrouped(
                candidates.stream()
                        .map(Merchant::getId)
                        .toList()
        );
    }

    private NoMatchAnalysis analyzeNoMatch(
            List<Merchant> candidates,
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude
    ) {
        List<LimitingConditionVO> limitingConditions =
                new ArrayList<>();
        List<AdjustmentSuggestionVO> suggestions =
                new ArrayList<>();

        addBudgetAnalysis(
                candidates,
                constraints,
                userLatitude,
                userLongitude,
                limitingConditions,
                suggestions
        );

        addDistanceAnalysis(
                candidates,
                constraints,
                userLatitude,
                userLongitude,
                limitingConditions,
                suggestions
        );

        addListRemovalAnalysis(
                candidates,
                constraints,
                userLatitude,
                userLongitude,
                "cuisines",
                "RELAX_CUISINE",
                constraints.getCuisines(),
                limitingConditions,
                suggestions
        );

        addListRemovalAnalysis(
                candidates,
                constraints,
                userLatitude,
                userLongitude,
                "scenes",
                "REMOVE_SCENE",
                constraints.getScenes(),
                limitingConditions,
                suggestions
        );

        addEnvironmentAnalysis(
                candidates,
                constraints,
                userLatitude,
                userLongitude,
                limitingConditions,
                suggestions
        );

        addMinRatingAnalysis(
                candidates,
                constraints,
                userLatitude,
                userLongitude,
                limitingConditions,
                suggestions
        );

        if (constraints.getDishKeywords() != null
                && !constraints.getDishKeywords().isEmpty()) {
            limitingConditions.add(
                    new LimitingConditionVO(
                            "dishKeywords",
                            "DISH",
                            constraints.getDishKeywords(),
                            0,
                            "当前有效菜单中没有命中指定菜品"
                    )
            );
            suggestions.add(
                    new AdjustmentSuggestionVO(
                            "remove-dish-keywords",
                            "RELAX_DISH",
                            "dishKeywords",
                            constraints.getDishKeywords(),
                            List.of(),
                            "暂时取消菜品限制并重新搜索",
                            "放宽菜品要求后可继续按其他条件推荐"
                    )
            );
        }

        limitingConditions.sort(
                Comparator.comparing(
                                LimitingConditionVO
                                        ::getRecoveredMerchantCount,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                        .thenComparing(
                                LimitingConditionVO::getField
                        )
        );

        if (limitingConditions.size() > 3) {
            limitingConditions =
                    new ArrayList<>(
                            limitingConditions.subList(0, 3)
                    );
        }

        suggestions.sort(
                Comparator.comparing(
                        AdjustmentSuggestionVO::getId
                )
        );

        if (suggestions.isEmpty()) {
            suggestions.add(
                    fallbackSuggestion(constraints)
            );
        }

        if (suggestions.size() > 3) {
            suggestions =
                    new ArrayList<>(
                            suggestions.subList(0, 3)
                    );
        }

        return new NoMatchAnalysis(
                limitingConditions,
                suggestions
        );
    }

    private void addBudgetAnalysis(
            List<Merchant> candidates,
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            List<LimitingConditionVO> limitingConditions,
            List<AdjustmentSuggestionVO> suggestions
    ) {
        BigDecimal currentBudget =
                matchScoreCalculator
                        .resolvePerCapitaBudget(
                                constraints
                        );

        if (currentBudget == null) {
            return;
        }

        ConstraintState relaxed =
                copyConstraints(constraints);
        relaxed.setPerCapitaBudget(null);
        relaxed.setTotalBudget(null);

        List<Merchant> recovered =
                strictMatches(
                        candidates,
                        relaxed,
                        userLatitude,
                        userLongitude
                );

        if (recovered.isEmpty()) {
            return;
        }

        BigDecimal suggestedBudget =
                recovered.stream()
                        .map(Merchant::getAveragePrice)
                        .filter(value -> value != null)
                        .min(BigDecimal::compareTo)
                        .orElse(
                                currentBudget.multiply(
                                        new BigDecimal("1.2")
                                )
                        )
                        .setScale(0, RoundingMode.CEILING);

        limitingConditions.add(
                new LimitingConditionVO(
                        "perCapitaBudget",
                        "BUDGET",
                        currentBudget,
                        recovered.size(),
                        "当前人均预算限制排除了部分可匹配商家"
                )
        );

        suggestions.add(
                new AdjustmentSuggestionVO(
                        "increase-budget-"
                                + formatIdValue(
                                suggestedBudget
                        ),
                        "INCREASE_BUDGET",
                        "perCapitaBudget",
                        currentBudget,
                        suggestedBudget,
                        "将人均预算提高到 "
                                + formatNumber(
                                suggestedBudget
                        )
                                + " 元",
                        "提高到该预算后可以找到符合其他条件的商家"
                )
        );
    }

    private void addDistanceAnalysis(
            List<Merchant> candidates,
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            List<LimitingConditionVO> limitingConditions,
            List<AdjustmentSuggestionVO> suggestions
    ) {
        BigDecimal currentDistance =
                constraints.getDistanceKm();

        if (currentDistance == null
                || currentDistance
                .compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        ConstraintState relaxed =
                copyConstraints(constraints);
        relaxed.setDistanceKm(null);

        List<Merchant> recovered =
                strictMatches(
                        candidates,
                        relaxed,
                        userLatitude,
                        userLongitude
                );

        if (recovered.isEmpty()) {
            return;
        }

        List<Merchant> coordinateCandidates =
                recovered.stream()
                        .filter(merchant ->
                                userLatitude != null
                                        && userLongitude != null
                                        && merchant.getLatitude() != null
                                        && merchant
                                        .getLongitude() != null
                        )
                        .toList();

        if (coordinateCandidates.isEmpty()) {
            return;
        }

        BigDecimal nearestDistance =
                coordinateCandidates.stream()
                        .map(merchant ->
                                matchScoreCalculator
                                        .calculateDistanceKm(
                                                userLatitude,
                                                userLongitude,
                                                merchant.getLatitude(),
                                                merchant.getLongitude()
                                        )
                        )
                        .min(BigDecimal::compareTo)
                        .orElse(null);

        if (nearestDistance == null) {
            return;
        }

        BigDecimal suggestedDistance =
                nearestDistance.setScale(
                        0,
                        RoundingMode.CEILING
                );

        if (suggestedDistance.compareTo(
                currentDistance
        ) <= 0) {
            suggestedDistance =
                    currentDistance.add(BigDecimal.ONE);
        }

        ConstraintState verified =
                copyConstraints(constraints);
        verified.setDistanceKm(suggestedDistance);

        List<Merchant> verifiedRecovered =
                strictMatches(
                        candidates,
                        verified,
                        userLatitude,
                        userLongitude
                );

        if (verifiedRecovered.isEmpty()) {
            return;
        }

        limitingConditions.add(
                new LimitingConditionVO(
                        "distanceKm",
                        "DISTANCE",
                        currentDistance,
                        verifiedRecovered.size(),
                        "当前距离范围内没有完全匹配的商家"
                )
        );

        suggestions.add(
                new AdjustmentSuggestionVO(
                        "expand-distance-"
                                + formatIdValue(
                                suggestedDistance
                        ),
                        "EXPAND_DISTANCE",
                        "distanceKm",
                        currentDistance,
                        suggestedDistance,
                        "将距离范围扩大到 "
                                + formatNumber(
                                suggestedDistance
                        )
                                + " 公里",
                        "扩大距离后可以覆盖符合其他条件的商家"
                )
        );
    }

    private void addListRemovalAnalysis(
            List<Merchant> candidates,
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            String field,
            String type,
            List<String> currentValues,
            List<LimitingConditionVO> limitingConditions,
            List<AdjustmentSuggestionVO> suggestions
    ) {
        if (!hasValues(currentValues)) {
            return;
        }

        ConstraintState relaxed =
                copyConstraints(constraints);

        if ("cuisines".equals(field)) {
            relaxed.setCuisines(new ArrayList<>());
        } else if ("scenes".equals(field)) {
            relaxed.setScenes(new ArrayList<>());
        }

        List<Merchant> recovered =
                strictMatches(
                        candidates,
                        relaxed,
                        userLatitude,
                        userLongitude
                );

        if (recovered.isEmpty()) {
            return;
        }

        String displayText =
                "cuisines".equals(field)
                        ? "暂时取消菜系限制并重新搜索"
                        : "暂时取消用餐场景限制并重新搜索";

        limitingConditions.add(
                new LimitingConditionVO(
                        field,
                        type,
                        currentValues,
                        recovered.size(),
                        displayText
                )
        );

        suggestions.add(
                new AdjustmentSuggestionVO(
                        ("cuisines".equals(field)
                                ? "relax-cuisine"
                                : "remove-scene")
                                + "-"
                                + recovered.size(),
                        type,
                        field,
                        currentValues,
                        List.of(),
                        displayText,
                        "放宽该条件后可以找到符合其他条件的商家"
                )
        );
    }

    private void addEnvironmentAnalysis(
            List<Merchant> candidates,
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            List<LimitingConditionVO> limitingConditions,
            List<AdjustmentSuggestionVO> suggestions
    ) {
        if (!hasValues(
                constraints.getEnvironmentRequirements()
        )) {
            return;
        }

        for (String requirement
                : constraints.getEnvironmentRequirements()) {
            if (requirement == null
                    || requirement.isBlank()) {
                continue;
            }

            ConstraintState relaxed =
                    copyConstraints(constraints);
            List<String> remaining =
                    new ArrayList<>(
                            constraints
                                    .getEnvironmentRequirements()
                    );
            remaining.remove(requirement);
            relaxed.setEnvironmentRequirements(
                    remaining
            );

            List<Merchant> recovered =
                    strictMatches(
                            candidates,
                            relaxed,
                            userLatitude,
                            userLongitude
                    );

            if (recovered.isEmpty()) {
                continue;
            }

            limitingConditions.add(
                    new LimitingConditionVO(
                            "environmentRequirements",
                            "ENVIRONMENT",
                            requirement,
                            recovered.size(),
                            "环境要求“"
                                    + requirement
                                    + "”限制了可匹配商家"
                    )
            );

            suggestions.add(
                    new AdjustmentSuggestionVO(
                            "remove-environment-"
                                    + stableToken(requirement),
                            "REMOVE_ENVIRONMENT",
                            "environmentRequirements",
                            constraints
                                    .getEnvironmentRequirements(),
                            remaining,
                            "取消环境要求："
                                    + requirement,
                            "一次只放宽一个环境要求，避免过度改变用户需求"
                    )
            );
        }
    }

    private void addMinRatingAnalysis(
            List<Merchant> candidates,
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            List<LimitingConditionVO> limitingConditions,
            List<AdjustmentSuggestionVO> suggestions
    ) {
        BigDecimal currentRating =
                constraints.getMinRating();

        if (currentRating == null) {
            return;
        }

        ConstraintState relaxed =
                copyConstraints(constraints);
        relaxed.setMinRating(null);

        List<Merchant> recovered =
                strictMatches(
                        candidates,
                        relaxed,
                        userLatitude,
                        userLongitude
                );

        if (recovered.isEmpty()) {
            return;
        }

        BigDecimal suggestedRating =
                currentRating.subtract(
                        new BigDecimal("0.5")
                );

        if (suggestedRating.compareTo(
                BigDecimal.ZERO
        ) <= 0) {
            suggestedRating = BigDecimal.ZERO;
        }

        limitingConditions.add(
                new LimitingConditionVO(
                        "minRating",
                        "RATING",
                        currentRating,
                        recovered.size(),
                        "当前最低评分要求限制了可匹配商家"
                )
        );

        suggestions.add(
                new AdjustmentSuggestionVO(
                        "lower-min-rating-"
                                + formatIdValue(
                                suggestedRating
                        ),
                        "LOWER_MIN_RATING",
                        "minRating",
                        currentRating,
                        suggestedRating,
                        "将最低评分降低到 "
                                + formatNumber(
                                suggestedRating
                        ),
                        "降低评分门槛后可以找到符合其他条件的商家"
                )
        );
    }

    private List<Merchant> strictMatches(
            List<Merchant> candidates,
            ConstraintState constraints,
            BigDecimal userLatitude,
            BigDecimal userLongitude
    ) {
        List<Merchant> matched =
                new ArrayList<>();

        for (Merchant candidate : candidates) {
            if (matchScoreCalculator
                    .passesHardFilters(
                            candidate,
                            constraints,
                            userLatitude,
                            userLongitude
                    )) {
                matched.add(candidate);
            }
        }

        return matched;
    }

    private AdjustmentSuggestionVO fallbackSuggestion(
            ConstraintState constraints
    ) {
        if (hasValues(constraints.getCuisines())) {
            return new AdjustmentSuggestionVO(
                    "relax-cuisine-general",
                    "RELAX_CUISINE",
                    "cuisines",
                    constraints.getCuisines(),
                    List.of(),
                    "暂时取消菜系限制并重新搜索",
                    "当前没有完全匹配结果，可以先放宽菜系要求"
            );
        }

        if (hasValues(
                constraints.getEnvironmentRequirements()
        )) {
            String requirement =
                    constraints
                            .getEnvironmentRequirements()
                            .get(0);
            List<String> remaining =
                    new ArrayList<>(
                            constraints
                                    .getEnvironmentRequirements()
                    );
            remaining.remove(requirement);

            return new AdjustmentSuggestionVO(
                    "remove-environment-general",
                    "REMOVE_ENVIRONMENT",
                    "environmentRequirements",
                    constraints.getEnvironmentRequirements(),
                    remaining,
                    "取消一个环境要求后重新搜索",
                    "当前暂无满足基础营业状态的商家，可尝试取消部分查询条件后重新搜索"
            );
        }

        if (hasValues(constraints.getScenes())) {
            return new AdjustmentSuggestionVO(
                    "remove-scene-general",
                    "REMOVE_SCENE",
                    "scenes",
                    constraints.getScenes(),
                    List.of(),
                    "暂时取消用餐场景限制并重新搜索",
                    "当前暂无满足基础营业状态的商家，可尝试取消部分查询条件后重新搜索"
            );
        }

        if (constraints.getMinRating() != null) {
            return new AdjustmentSuggestionVO(
                    "lower-min-rating-general",
                    "LOWER_MIN_RATING",
                    "minRating",
                    constraints.getMinRating(),
                    BigDecimal.ZERO,
                    "取消最低评分限制并重新搜索",
                    "当前暂无满足基础营业状态的商家，可尝试取消部分查询条件后重新搜索"
            );
        }

        BigDecimal currentDistance =
                constraints.getDistanceKm();

        if (currentDistance != null
                && currentDistance.compareTo(
                BigDecimal.ZERO
        ) > 0) {
            return new AdjustmentSuggestionVO(
                "expand-distance-general",
                "EXPAND_DISTANCE",
                "distanceKm",
                currentDistance,
                currentDistance == null
                        ? new BigDecimal("5")
                        : currentDistance.add(
                        BigDecimal.ONE
                ),
                "扩大距离范围并重新搜索",
                "当前没有完全匹配结果，可以先扩大距离范围"
            );
        }

        BigDecimal currentBudget =
                matchScoreCalculator.resolvePerCapitaBudget(
                        constraints
                );

        if (currentBudget != null
                && currentBudget.compareTo(
                BigDecimal.ZERO
        ) > 0) {
            BigDecimal suggestedBudget =
                    currentBudget.multiply(
                            new BigDecimal("1.2")
                    ).setScale(0, RoundingMode.CEILING);

            return new AdjustmentSuggestionVO(
                    "increase-budget-general",
                    "INCREASE_BUDGET",
                    "perCapitaBudget",
                    currentBudget,
                    suggestedBudget,
                    "提高人均预算后重新搜索",
                    "当前暂无满足基础营业状态的商家，可尝试取消部分查询条件后重新搜索"
            );
        }

        return new AdjustmentSuggestionVO(
                "relax-query-general",
                "RELAX_CUISINE",
                "cuisines",
                List.of(),
                List.of(),
                "取消部分查询条件后重新搜索",
                "当前暂无满足基础营业状态的商家，可尝试取消部分查询条件后重新搜索"
        );
    }

    private int compareResults(
            RecommendationItemVO left,
            RecommendationItemVO right
    ) {
        int scoreCompare =
                compareNullableDescending(
                        left.getFinalScore(),
                        right.getFinalScore()
                );

        if (scoreCompare != 0) {
            return scoreCompare;
        }

        int ratingCompare =
                compareNullableDescending(
                        left.getMerchantRating(),
                        right.getMerchantRating()
                );

        if (ratingCompare != 0) {
            return ratingCompare;
        }

        int reviewCountCompare =
                compareNullableDescending(
                        left.getReviewCount(),
                        right.getReviewCount()
                );

        if (reviewCountCompare != 0) {
            return reviewCountCompare;
        }

        return compareNullableAscending(
                left.getMerchantId(),
                right.getMerchantId()
        );
    }

    private <T extends Comparable<? super T>>
    int compareNullableDescending(
            T left,
            T right
    ) {
        if (left == null && right == null) {
            return 0;
        }

        if (left == null) {
            return 1;
        }

        if (right == null) {
            return -1;
        }

        return right.compareTo(left);
    }

    private <T extends Comparable<? super T>>
    int compareNullableAscending(
            T left,
            T right
    ) {
        if (left == null && right == null) {
            return 0;
        }

        if (left == null) {
            return 1;
        }

        if (right == null) {
            return -1;
        }

        return left.compareTo(right);
    }

    private void assignRanks(
            List<RecommendationItemVO> results
    ) {
        for (int index = 0;
             index < results.size();
             index++) {
            results.get(index).setRankNo(
                    index + 1
            );
        }
    }

    private void saveRecommendationItems(
            Long recommendationId,
            List<RecommendationItemVO> results
    ) {
        for (RecommendationItemVO result
                : results) {
            RecommendationItem item =
                    new RecommendationItem();

            item.setRecommendationId(
                    recommendationId
            );
            item.setMerchantId(
                    result.getMerchantId()
            );
            item.setRankNo(result.getRankNo());
            item.setScore(
                    normalizeDatabaseScore(
                            result.getFinalScore()
                    )
            );
            item.setScoreDetails(
                    serializeToJson(
                            buildScoreDetails(result),
                            "SCORE_DETAILS_SERIALIZE_FAILED",
                            "推荐评分明细序列化失败"
                    )
            );
            item.setMatchedConditions(
                    serializeToJson(
                            result.getMatchedConditions(),
                            "MATCHED_CONDITIONS_SERIALIZE_FAILED",
                            "推荐匹配条件序列化失败"
                    )
            );
            item.setUnmatchedConditions(
                    serializeToJson(
                            result.getRiskNotes(),
                            "RISK_NOTES_SERIALIZE_FAILED",
                            "推荐风险提示序列化失败"
                    )
            );
            item.setReason(result.getReason());
            item.setCreatedAt(OffsetDateTime.now());

            int insertedRows =
                    recommendationItemMapper.insert(item);

            if (insertedRows != 1) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "RECOMMENDATION_ITEM_CREATE_FAILED",
                        "推荐结果明细保存失败"
                );
            }
            saveDishEvidence(item, result);
            saveRecommendationBases(item, result);
        }
    }

    private void saveRecommendationBases(
            RecommendationItem item,
            RecommendationItemVO result
    ) {
        for (RecommendationBasisVO basis :
                result.getRecommendationBases() == null
                        ? List.<RecommendationBasisVO>of()
                        : result.getRecommendationBases()) {
            if (basis.getMatchedCondition() == null
                    || basis.getMatchedCondition().isBlank()) {
                continue;
            }
            if (!result.getMerchantId().equals(basis.getMerchantId())
                    || (!"REVIEW".equals(basis.getSourceType())
                    && !"MERCHANT".equals(basis.getSourceType()))) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "RECOMMENDATION_EVIDENCE_MERCHANT_MISMATCH",
                        "推荐依据与推荐商家不一致");
            }
            RecommendationEvidence evidence = new RecommendationEvidence();
            evidence.setRecommendationItemId(item.getId());
            evidence.setSourceType(basis.getSourceType());
            evidence.setSourceMerchantId(result.getMerchantId());
            if ("REVIEW".equals(basis.getSourceType())) {
                evidence.setReviewId(basis.getSourceId());
            }
            evidence.setEvidenceExcerpt(basis.getSummary());
            evidence.setConditionKey(basis.getMatchedCondition());
            evidence.setSourceTextSnapshot(serializeToJson(
                    basis, "EVIDENCE_SNAPSHOT_SERIALIZE_FAILED",
                    "推荐依据快照序列化失败"));
            evidence.setRelevanceScore(basis.getRelevanceScore());
            evidence.setCreatedAt(OffsetDateTime.now());
            if (recommendationEvidenceMapper.insert(evidence) != 1) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "RECOMMENDATION_EVIDENCE_CREATE_FAILED",
                        "推荐依据保存失败");
            }
            basis.setEvidenceId(evidence.getId());
        }
    }

    private void saveDishEvidence(
            RecommendationItem item,
            RecommendationItemVO result
    ) {
        if (result.getMatchedDishes() == null) {
            return;
        }
        for (MatchedDishVO dish : result.getMatchedDishes()) {
            if (!result.getMerchantId().equals(dish.getMerchantId())) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "DISH_EVIDENCE_MERCHANT_MISMATCH",
                        "菜品证据与推荐商家不一致"
                );
            }
            RecommendationEvidence evidence =
                    new RecommendationEvidence();
            evidence.setRecommendationItemId(item.getId());
            evidence.setSourceType("DISH");
            evidence.setSourceMerchantId(result.getMerchantId());
            evidence.setDishId(dish.getDishId());
            evidence.setEvidenceExcerpt(dish.getMatchReason());
            evidence.setSourceTextSnapshot(
                    serializeToJson(
                            dish,
                            "DISH_SNAPSHOT_SERIALIZE_FAILED",
                            "菜品证据快照序列化失败"
                    )
            );
            evidence.setRelevanceScore(dish.getMatchScore());
            evidence.setCreatedAt(OffsetDateTime.now());
            if (recommendationEvidenceMapper.insert(evidence) != 1) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "DISH_EVIDENCE_CREATE_FAILED",
                        "菜品推荐证据保存失败"
                );
            }
        }
    }

    private java.util.Map<String, Object> buildScoreDetails(
            RecommendationItemVO result
    ) {
        java.util.Map<String, Object> details =
                new java.util.LinkedHashMap<>();
        if (result.getScoreItems() != null) {
            details.putAll(result.getScoreItems());
        }
        details.put("distanceKm", result.getDistanceKm());
        return details;
    }

    private BigDecimal normalizeDatabaseScore(
            BigDecimal displayScore
    ) {
        if (displayScore == null) {
            return DATABASE_SCORE_MIN.setScale(
                    6,
                    RoundingMode.HALF_UP
            );
        }

        BigDecimal normalized =
                displayScore.divide(
                        ONE_HUNDRED,
                        6,
                        RoundingMode.HALF_UP
                );

        if (normalized.compareTo(
                DATABASE_SCORE_MIN
        ) < 0) {
            normalized = DATABASE_SCORE_MIN;
        }

        if (normalized.compareTo(
                DATABASE_SCORE_MAX
        ) > 0) {
            normalized = DATABASE_SCORE_MAX;
        }

        return normalized.setScale(
                6,
                RoundingMode.HALF_UP
        );
    }

    private void completeRecommendation(
            Recommendation recommendation,
            List<RecommendationItemVO> results,
            SemanticSearchStatus semanticStatus
    ) {
        boolean hasResults =
                results != null
                        && !results.isEmpty();

        recommendation.setAlgorithmVersion(
                algorithmVersionFor(semanticStatus)
        );
        recommendation.setQueryText(
                recommendationQueryTextFor(semanticStatus)
        );
        recommendation.setStatus(
                hasResults
                        ? RECOMMENDATION_STATUS_SUCCESS
                        : RECOMMENDATION_STATUS_NO_MATCH
        );
        recommendation.setResultCount(
                hasResults ? results.size() : 0
        );
        recommendation.setReplyText(
                hasResults
                        ? recommendationReplyTextFor(
                        semanticStatus,
                        results.size()
                )
                        : NO_MATCH_MESSAGE
        );
        recommendation.setCompletedAt(
                OffsetDateTime.now()
        );

        int updatedRows =
                recommendationMapper.updateById(
                        recommendation
                );

        if (updatedRows != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "RECOMMENDATION_COMPLETE_FAILED",
                    "推荐记录状态更新失败"
            );
        }
    }

    private RecommendationRankResponse buildResponse(
            Recommendation recommendation,
            ConstraintState constraints,
            RecommendationWeights weights,
            List<RecommendationItemVO> results,
            NoMatchAnalysis noMatchAnalysis,
            SemanticSearchStatus semanticStatus
    ) {
        boolean matched =
                results != null && !results.isEmpty();

        RecommendationRankResponse response =
                new RecommendationRankResponse();

        response.setRecommendationId(
                recommendation.getId()
        );
        response.setSessionId(
                recommendation.getSessionId()
        );
        response.setRequestId(
                recommendation.getRequestId()
        );
        response.setAlgorithmVersion(
                recommendation.getAlgorithmVersion()
        );
        response.setSemanticStatus(
                semanticStatus.name()
        );
        response.setDegraded(
                isSemanticDegraded(semanticStatus)
        );
        response.setMatched(matched);
        response.setStatus(
                recommendation.getStatus()
        );
        response.setMessage(
                matched
                        ? "推荐完成"
                        : NO_MATCH_MESSAGE
        );
        response.setConstraints(constraints);
        response.setCurrentConstraints(
                constraints
        );
        response.setWeights(weights);
        response.setResultCount(
                results == null
                        ? 0
                        : results.size()
        );
        response.setResults(
                results == null
                        ? new ArrayList<>()
                        : results
        );
        response.setLimitingConditions(
                noMatchAnalysis.limitingConditions()
        );
        response.setAdjustmentSuggestions(
                noMatchAnalysis.suggestions()
        );

        return response;
    }

    private AiTraceContext standaloneContext(
            String requestId, Long sessionId, Long userId, String scene
    ) {
        return traceService == null
                ? AiTraceContext.create(requestId, sessionId, userId, scene)
                : traceService.startTrace(requestId, sessionId, userId, scene);
    }

    private AiRequestTraceStage startStage(
            AiTraceContext context, String name, Object input
    ) {
        if (traceService == null || context == null) return null;
        try {
            return traceService.startStage(context, name, input);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void recordRequestReceived(
            AiTraceContext context, String responseType
    ) {
        AiRequestTraceStage stage = startStage(
                context, "REQUEST_RECEIVED",
                java.util.Map.of("responseType", responseType));
        completeStage(stage,
                java.util.Map.of("responseType", responseType),
                null, null);
    }

    private void completeStage(
            AiRequestTraceStage stage, Object output,
            String modelName, String promptVersion
    ) {
        if (traceService == null || stage == null) return;
        try {
            traceService.completeStage(stage, output, null, modelName,
                    null, promptVersion);
        } catch (Exception ignored) {
            // Trace writes must not break ranking.
        }
    }

    private void completeStandalone(
            AiTraceContext context,
            RecommendationRankResponse response,
            String responseType
    ) {
        if (traceService == null || response == null) {
            return;
        }

        try {
            traceService.updateStructuredConditions(
                    context,
                    response.getCurrentConstraints()
            );

            List<Long> merchantIds =
                    response.getResults() == null
                            ? List.of()
                            : response.getResults().stream()
                            .map(RecommendationItemVO::getMerchantId)
                            .filter(java.util.Objects::nonNull)
                            .toList();

            String traceEngine =
                    response.getAlgorithmVersion() != null
                            && response.getAlgorithmVersion()
                            .startsWith("HYBRID")
                            ? "HYBRID_ENGINE"
                            : "RULE_ENGINE";

            traceService.completeTrace(
                    context,
                    "SUCCESS",
                    java.util.Map.of(
                            "recommendationId",
                            response.getRecommendationId(),
                            "merchantIds",
                            merchantIds,
                            "resultCount",
                            response.getResultCount(),
                            "degraded",
                            Boolean.TRUE.equals(
                                    response.getDegraded()
                            ),
                            "semanticStatus",
                            response.getSemanticStatus(),
                            "algorithmVersion",
                            response.getAlgorithmVersion(),
                            "responseType",
                            responseType
                    ),
                    null,
                    traceEngine,
                    null,
                    "NOT_APPLICABLE"
            );
        } catch (Exception ignored) {
            // Trace writes must not change a successful ranking response.
        }
    }

    private void failTrace(AiTraceContext context, Exception exception) {
        if (traceService != null && context != null) {
            traceService.failTraceSafely(
                    context, "RECOMMENDATION_FAILED", exception.getMessage());
        }
    }

    private void closeRunningStages(AiTraceContext context, Exception exception) {
        if (traceService != null) {
            traceService.failRunningStagesSafely(
                    context, "RECOMMENDATION_FAILED", exception.getMessage());
        }
    }

    private void applyAdjustment(
            ConstraintState constraints,
            String field,
            Object value
    ) {
        switch (field) {
            case "perCapitaBudget" ->
                    constraints.setPerCapitaBudget(
                            readPositiveDecimal(
                                    value,
                                    "perCapitaBudget"
                            )
                    );
            case "totalBudget" ->
                    constraints.setTotalBudget(
                            readPositiveDecimal(
                                    value,
                                    "totalBudget"
                            )
                    );
            case "distanceKm" ->
                    constraints.setDistanceKm(
                            readDistanceKm(value)
                    );
            case "cuisines" ->
                    constraints.setCuisines(
                            readStringList(value)
                    );
            case "dishKeywords" ->
                    constraints.setDishKeywords(
                            readStringList(value)
                    );
            case "scenes" ->
                    constraints.setScenes(
                            readStringList(value)
                    );
            case "environmentRequirements" ->
                    constraints.setEnvironmentRequirements(
                            readStringList(value)
                    );
            case "minRating" ->
                    constraints.setMinRating(
                            readRating(value)
                    );
            default -> throw invalidAdjustment(
                    "不支持调整字段: " + field
            );
        }
    }

    private void saveAdjustedSessionState(
            ChatSessionState sessionState,
            ConstraintState constraints
    ) {
        sessionState.setCurrentConstraints(
                serializeToJson(
                        constraints,
                        "CONSTRAINTS_SERIALIZE_FAILED",
                        "会话条件序列化失败"
                )
        );
        sessionState.setUpdatedAt(OffsetDateTime.now());
        sessionState.setConversationStage("SEARCHING");

        int currentVersion =
                sessionState.getVersion() == null
                        ? 0
                        : sessionState.getVersion();
        sessionState.setVersion(currentVersion + 1);

        int updatedRows =
                chatSessionStateMapper.updateById(
                        sessionState
                );

        if (updatedRows != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SESSION_STATE_UPDATE_FAILED",
                    "会话条件状态更新失败"
            );
        }
    }

    private BigDecimal readPositiveDecimal(
            Object value,
            String field
    ) {
        validateDecimalInput(value, field);

        BigDecimal decimal;

        try {
            decimal = objectMapper.convertValue(
                    value,
                    BigDecimal.class
            );
        } catch (IllegalArgumentException exception) {
            throw invalidAdjustment(
                    "调整值必须是非负数字"
            );
        }

        if (decimal == null
                || decimal.compareTo(
                BigDecimal.ZERO
        ) <= 0) {
            throw invalidAdjustment(
                    "调整值必须是非负数字"
            );
        }

        return decimal;
    }

    private BigDecimal readDistanceKm(Object value) {
        BigDecimal distance =
                readPositiveDecimal(
                        value,
                        "distanceKm"
                );

        if (distance.compareTo(MAX_DISTANCE_KM) > 0) {
            throw invalidAdjustment(
                    "distanceKm must be less than or equal to "
                            + MAX_DISTANCE_KM
            );
        }

        return distance;
    }

    private BigDecimal readRatingDecimal(Object value) {
        validateDecimalInput(value, "minRating");

        try {
            return objectMapper.convertValue(
                    value,
                    BigDecimal.class
            );
        } catch (IllegalArgumentException exception) {
            throw invalidAdjustment(
                    "minRating must be a finite number"
            );
        }
    }

    private void validateDecimalInput(
            Object value,
            String field
    ) {
        if (value == null) {
            throw invalidAdjustment(
                    field + " must be a finite number"
            );
        }

        if (value instanceof Double doubleValue
                && !Double.isFinite(doubleValue)) {
            throw invalidAdjustment(
                    field + " must be a finite number"
            );
        }

        if (value instanceof Float floatValue
                && !Float.isFinite(floatValue)) {
            throw invalidAdjustment(
                    field + " must be a finite number"
            );
        }

        if (!(value instanceof Number
                || value instanceof String)) {
            throw invalidAdjustment(
                    field + " must be a finite number"
            );
        }
    }

    private BigDecimal readRating(Object value) {
        BigDecimal rating =
                readRatingDecimal(value);

        if (rating.compareTo(BigDecimal.ZERO) < 0
                || rating.compareTo(
                new BigDecimal("5")
        ) > 0) {
            throw invalidAdjustment(
                    "最低评分不能大于5"
            );
        }

        return rating;
    }

    private List<String> readStringList(
            Object value
    ) {
        if (!(value instanceof Collection<?>)) {
            throw invalidAdjustment(
                    "adjustment value must be a string array"
            );
        }

        List<?> rawValues;

        try {
            rawValues = objectMapper.convertValue(
                    value,
                    List.class
            );
        } catch (IllegalArgumentException exception) {
            throw invalidAdjustment(
                    "调整值必须是字符串数组"
            );
        }

        List<String> values =
                new ArrayList<>();

        if (rawValues == null) {
            return values;
        }

        if (rawValues.isEmpty()) {
            return values;
        }

        Set<String> distinct =
                new LinkedHashSet<>();

        for (Object rawValue : rawValues) {
            if (!(rawValue instanceof String textValue)) {
                throw invalidAdjustment(
                        "adjustment value must be a string array"
                );
            }

            String text =
                    textValue.trim();

            if (text.isBlank()) {
                continue;
            }

            if (text.length()
                    > MAX_ADJUSTMENT_LIST_ITEM_LENGTH) {
                throw invalidAdjustment(
                        "adjustment list item is too long"
                );
            }

            distinct.add(text);

            if (distinct.size()
                    > MAX_ADJUSTMENT_LIST_SIZE) {
                throw invalidAdjustment(
                        "adjustment list cannot exceed "
                                + MAX_ADJUSTMENT_LIST_SIZE
                                + " items"
                );
            }
        }

        if (distinct.isEmpty()) {
            throw invalidAdjustment(
                    "adjustment list cannot contain only blank values"
            );
        }

        values.addAll(distinct);
        return values;
    }

    private ConstraintState copyConstraints(
            ConstraintState source
    ) {
        ConstraintState copy =
                new ConstraintState();

        if (source == null) {
            return copy;
        }

        copy.setPartySize(source.getPartySize());
        copy.setTotalBudget(source.getTotalBudget());
        copy.setPerCapitaBudget(
                source.getPerCapitaBudget()
        );
        copy.setMerchantTypes(
                copyList(source.getMerchantTypes())
        );
        copy.setCuisines(
                copyList(source.getCuisines())
        );
        copy.setTastePreferences(
                copyList(source.getTastePreferences())
        );
        copy.setTasteRestrictions(
                copyList(source.getTasteRestrictions())
        );
        copy.setDishKeywords(
                copyList(source.getDishKeywords())
        );
        copy.setExcludedCuisines(
                copyList(source.getExcludedCuisines())
        );
        copy.setExcludedMerchantTypes(
                copyList(
                        source.getExcludedMerchantTypes()
                )
        );
        copy.setDistanceKm(source.getDistanceKm());
        copy.setMinRating(source.getMinRating());
        copy.setScenes(copyList(source.getScenes()));
        copy.setEnvironmentRequirements(
                copyList(
                        source.getEnvironmentRequirements()
                )
        );
        copy.setBusinessTime(source.getBusinessTime());

        return copy;
    }

    private List<String> copyList(
            List<String> values
    ) {
        return values == null
                ? new ArrayList<>()
                : new ArrayList<>(values);
    }

    private ApiException invalidAdjustment(
            String message
    ) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_RECOMMENDATION_ADJUSTMENT",
                message
        );
    }

    private ApiException dataServiceException(
            DataAccessException exception
    ) {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "RECOMMENDATION_DATA_SERVICE_ERROR",
                "推荐数据服务暂时不可用，请稍后重试"
        );
    }

    private String serializeToJson(
            Object value,
            String errorCode,
            String errorMessage
    ) {
        try {
            return objectMapper.writeValueAsString(
                    value
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    errorCode,
                    errorMessage
            );
        }
    }

    private boolean hasValues(List<String> values) {
        return values != null
                && values.stream().anyMatch(value ->
                value != null && !value.isBlank()
        );
    }

    private String formatNumber(BigDecimal value) {
        if (value == null) {
            return "未知";
        }

        return value.stripTrailingZeros()
                .toPlainString();
    }

    private String formatIdValue(BigDecimal value) {
        return formatNumber(value)
                .replace(".", "-");
    }

    private String stableToken(String value) {
        return value == null
                ? "unknown"
                : Integer.toHexString(
                value.hashCode()
        );
    }

    private record NoMatchAnalysis(
            List<LimitingConditionVO> limitingConditions,
            List<AdjustmentSuggestionVO> suggestions
    ) {
    }
}
