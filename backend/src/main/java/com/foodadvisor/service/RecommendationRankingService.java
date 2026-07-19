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
import com.foodadvisor.dto.recommendation.RecommendationRankRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.dto.recommendation.RecommendationWeights;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.ChatSessionState;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.MerchantBusinessHours;
import com.foodadvisor.entity.Recommendation;
import com.foodadvisor.entity.RecommendationItem;
import com.foodadvisor.mapper.ChatSessionMapper;
import com.foodadvisor.mapper.ChatSessionStateMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.RecommendationItemMapper;
import com.foodadvisor.mapper.RecommendationMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

    private static final String ALGORITHM_VERSION =
            "RULE_V1";

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

    private final ChatSessionMapper chatSessionMapper;
    private final ChatSessionStateMapper chatSessionStateMapper;
    private final MerchantMapper merchantMapper;
    private final RecommendationMapper recommendationMapper;
    private final RecommendationItemMapper recommendationItemMapper;
    private final MatchScoreCalculator matchScoreCalculator;
    private final MerchantBusinessHoursService businessHoursService;
    private final ObjectMapper objectMapper;

    public RecommendationRankingService(
            ChatSessionMapper chatSessionMapper,
            ChatSessionStateMapper chatSessionStateMapper,
            MerchantMapper merchantMapper,
            RecommendationMapper recommendationMapper,
            RecommendationItemMapper recommendationItemMapper,
            MatchScoreCalculator matchScoreCalculator,
            MerchantBusinessHoursService businessHoursService,
            ObjectMapper objectMapper
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatSessionStateMapper = chatSessionStateMapper;
        this.merchantMapper = merchantMapper;
        this.recommendationMapper = recommendationMapper;
        this.recommendationItemMapper = recommendationItemMapper;
        this.matchScoreCalculator = matchScoreCalculator;
        this.businessHoursService = businessHoursService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecommendationRankResponse rank(
            Long sessionId,
            RecommendationRankRequest request
    ) {
        try {
            return rankInternal(sessionId, request);
        } catch (DataAccessException exception) {
            throw dataServiceException(exception);
        }
    }

    @Transactional
    public RecommendationRankResponse adjustAndRank(
            Long sessionId,
            RecommendationAdjustRequest request
    ) {
        try {
            return adjustAndRankInternal(sessionId, request);
        } catch (DataAccessException exception) {
            throw dataServiceException(exception);
        }
    }

    private RecommendationRankResponse adjustAndRankInternal(
            Long sessionId,
            RecommendationAdjustRequest request
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

        return rankInternal(sessionId, rankRequest);
    }

    private RecommendationRankResponse rankInternal(
            Long sessionId,
            RecommendationRankRequest request
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

        Recommendation recommendation =
                createPendingRecommendation(
                        session,
                        constraints,
                        weights
                );

        List<Merchant> candidates =
                loadCandidateMerchants();

        Map<Long, List<MerchantBusinessHours>> businessHours =
                loadBusinessHours(candidates, constraints);

        List<RecommendationItemVO> results =
                calculateResults(
                        candidates,
                        constraints,
                        weights,
                        request.getUserLatitude(),
                        request.getUserLongitude(),
                        businessHours
                );

        results.sort(this::compareResults);
        assignRanks(results);

        saveRecommendationItems(
                recommendation.getId(),
                results
        );

        completeRecommendation(
                recommendation,
                results
        );

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

        return buildResponse(
                recommendation,
                constraints,
                weights,
                results,
                noMatchAnalysis
        );
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
            RecommendationWeights weights
    ) {
        Recommendation recommendation =
                new Recommendation();

        recommendation.setUserId(session.getUserId());
        recommendation.setSessionId(session.getId());
        recommendation.setRequestId(
                "rank-" + UUID.randomUUID()
        );
        recommendation.setTraceId(
                "trace-" + UUID.randomUUID()
        );
        recommendation.setQueryText(
                "基于会话结构化需求执行商家规则排序"
        );
        recommendation.setParsedConstraints(
                serializeToJson(
                        constraints,
                        "CONSTRAINTS_SERIALIZE_FAILED",
                        "推荐条件快照序列化失败"
                )
        );
        recommendation.setAlgorithmVersion(
                ALGORITHM_VERSION
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

    private List<RecommendationItemVO> calculateResults(
            List<Merchant> candidates,
            ConstraintState constraints,
            RecommendationWeights weights,
            BigDecimal userLatitude,
            BigDecimal userLongitude,
            Map<Long, List<MerchantBusinessHours>> businessHours
    ) {
        List<RecommendationItemVO> results =
                new ArrayList<>();

        for (Merchant merchant : candidates) {
            MerchantBusinessHoursService.BusinessHoursMatch
                    businessHoursMatch = businessHoursService.match(
                    constraints,
                    businessHours.get(merchant.getId())
            );
            if (!businessHoursMatch.matched()) {
                continue;
            }
            matchScoreCalculator.calculate(
                    merchant,
                    constraints,
                    weights,
                    userLatitude,
                    userLongitude
            ).ifPresent(result -> {
                matchScoreCalculator.addBusinessHoursEvidence(
                        result,
                        businessHoursMatch.evidence()
                );
                results.add(result);
            });
        }

        return results;
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
            List<RecommendationItemVO> results
    ) {
        boolean hasResults =
                results != null
                        && !results.isEmpty();

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
                        ? "规则排序完成，共返回"
                        + results.size()
                        + "家匹配商家"
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
            NoMatchAnalysis noMatchAnalysis
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
