package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.backend.exception.ApiException;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.RecommendationRankRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.dto.recommendation.RecommendationWeights;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.ChatSessionState;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Recommendation;
import com.foodadvisor.entity.RecommendationItem;
import com.foodadvisor.mapper.ChatSessionMapper;
import com.foodadvisor.mapper.ChatSessionStateMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.RecommendationItemMapper;
import com.foodadvisor.mapper.RecommendationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 商家推荐排序服务。
 *
 * 负责：
 * 1. 校验会话和用户归属；
 * 2. 从会话状态读取结构化需求；
 * 3. 查询有效候选商家；
 * 4. 调用纯规则评分器；
 * 5. 执行确定性的稳定排序；
 * 6. 保存推荐记录和每个商家的评分明细；
 * 7. 返回完整排序结果。
 *
 * 本服务不调用大模型。
 * 推荐分数、价格、评分和距离均来自数据库或确定性算法。
 */
@Service
public class RecommendationRankingService {

    /**
     * 当前排序算法版本。
     *
     * 修改计算规则时应同步修改版本号，
     * 便于追踪历史推荐结果。
     */
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

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    private static final BigDecimal DATABASE_SCORE_MIN =
            BigDecimal.ZERO;

    private static final BigDecimal DATABASE_SCORE_MAX =
            BigDecimal.ONE;

    private final ChatSessionMapper chatSessionMapper;

    private final ChatSessionStateMapper
            chatSessionStateMapper;

    private final MerchantMapper merchantMapper;

    private final RecommendationMapper
            recommendationMapper;

    private final RecommendationItemMapper
            recommendationItemMapper;

    private final MatchScoreCalculator
            matchScoreCalculator;

    private final ObjectMapper objectMapper;

    public RecommendationRankingService(
            ChatSessionMapper chatSessionMapper,
            ChatSessionStateMapper chatSessionStateMapper,
            MerchantMapper merchantMapper,
            RecommendationMapper recommendationMapper,
            RecommendationItemMapper recommendationItemMapper,
            MatchScoreCalculator matchScoreCalculator,
            ObjectMapper objectMapper
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatSessionStateMapper =
                chatSessionStateMapper;
        this.merchantMapper = merchantMapper;
        this.recommendationMapper =
                recommendationMapper;
        this.recommendationItemMapper =
                recommendationItemMapper;
        this.matchScoreCalculator =
                matchScoreCalculator;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据指定会话中的消费需求执行推荐排序。
     */
    @Transactional
    public RecommendationRankResponse rank(
            Long sessionId,
            RecommendationRankRequest request
    ) {
        validateRequest(sessionId, request);

        Long userId = request.getUserId();

        ChatSession session =
                loadAndValidateSession(
                        sessionId,
                        userId
                );

        ChatSessionState sessionState =
                loadSessionState(sessionId);

        ensureNoPendingConflict(sessionState);

        ConstraintState constraints =
                parseConstraints(sessionState);

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

        List<RecommendationItemVO> results =
                calculateResults(
                        candidates,
                        constraints,
                        weights,
                        request.getUserLatitude(),
                        request.getUserLongitude()
                );

        /*
         * 必须采用确定性的多级排序：
         * 1. 最终得分降序；
         * 2. 商家评分降序；
         * 3. 评论数量降序；
         * 4. 商家ID升序。
         *
         * 最后一个 merchantId ASC 能保证同分情况下
         * 连续执行多次仍然保持同样顺序。
         */
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

        return buildResponse(
                recommendation,
                constraints,
                weights,
                results
        );
    }

    /**
     * 校验服务入口参数。
     *
     * Controller 后续会使用 Jakarta Validation，
     * 但 Service 层仍保留校验，避免其他代码绕过 Controller 调用。
     */
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
                    "推荐权重不能为空，且权重总和必须等于100"
            );
        }
    }

    /**
     * 校验会话是否存在、是否属于当前用户并且仍然有效。
     */
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

    /**
     * 加载当前会话的结构化需求状态。
     */
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

    /**
     * 存在未解决冲突时，不允许执行推荐排序。
     */
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

    /**
     * 将 current_constraints JSONB 反序列化为 ConstraintState。
     */
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

    /**
     * 请求未传权重时使用默认权重。
     */
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

    /**
     * 创建推荐主记录。
     *
     * requestId 和 traceId 使用随机UUID，
     * 满足数据库非空唯一索引的可追踪要求。
     */
    private Recommendation createPendingRecommendation(
            ChatSession session,
            ConstraintState constraints,
            RecommendationWeights weights
    ) {
        Recommendation recommendation =
                new Recommendation();

        recommendation.setUserId(
                session.getUserId()
        );

        recommendation.setSessionId(
                session.getId()
        );

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

        /*
         * 当前阶段没有调用大模型。
         * modelName 和 modelVersion 保持为空，
         * 避免误导为大模型生成了排序分数。
         */
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

    /**
     * 查询基础候选商家。
     *
     * 数据库查询先完成平台状态、经营状态和逻辑删除过滤；
     * MatchScoreCalculator 中还会再次校验，
     * 形成双重防护。
     */
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

    /**
     * 对每个候选商家执行硬过滤和规则评分。
     */
    private List<RecommendationItemVO>
    calculateResults(
            List<Merchant> candidates,
            ConstraintState constraints,
            RecommendationWeights weights,
            BigDecimal userLatitude,
            BigDecimal userLongitude
    ) {
        List<RecommendationItemVO> results =
                new ArrayList<>();

        for (Merchant merchant : candidates) {
            Optional<RecommendationItemVO>
                    calculatedResult =
                    matchScoreCalculator.calculate(
                            merchant,
                            constraints,
                            weights,
                            userLatitude,
                            userLongitude
                    );

            calculatedResult.ifPresent(
                    results::add
            );
        }

        return results;
    }

    /**
     * 稳定排序比较器。
     */
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

    /**
     * 空值排在最后的降序比较。
     */
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

    /**
     * 空值排在最后的升序比较。
     */
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

    /**
     * 排序完成后按顺序设置从1开始的排名。
     */
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

    /**
     * 保存每一个商家的排名和评分明细。
     */
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

            item.setRankNo(
                    result.getRankNo()
            );

            /*
             * 接口显示0～100分，
             * 数据库score字段保存0～1。
             */
            item.setScore(
                    normalizeDatabaseScore(
                            result.getFinalScore()
                    )
            );

            item.setScoreDetails(
                    serializeToJson(
                            result.getScoreItems(),
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

            /*
             * riskNotes 保存到数据库已有的
             * unmatched_conditions 字段。
             */
            item.setUnmatchedConditions(
                    serializeToJson(
                            result.getRiskNotes(),
                            "RISK_NOTES_SERIALIZE_FAILED",
                            "推荐风险提示序列化失败"
                    )
            );

            item.setReason(
                    result.getReason()
            );

            item.setCreatedAt(
                    OffsetDateTime.now()
            );

            int insertedRows =
                    recommendationItemMapper.insert(
                            item
                    );

            if (insertedRows != 1) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "RECOMMENDATION_ITEM_CREATE_FAILED",
                        "推荐结果明细保存失败"
                );
            }
        }
    }

    /**
     * 将接口0～100分转换为数据库0～1分。
     */
    private BigDecimal normalizeDatabaseScore(
            BigDecimal displayScore
    ) {
        if (displayScore == null) {
            return DATABASE_SCORE_MIN
                    .setScale(
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

    /**
     * 更新推荐主记录的最终状态。
     */
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

        if (hasResults) {
            recommendation.setReplyText(
                    "规则排序完成，共返回"
                            + results.size()
                            + "家匹配商家"
            );
        } else {
            recommendation.setReplyText(
                    "没有找到同时满足硬性条件的商家"
            );
        }

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

    /**
     * 构建接口响应。
     */
    private RecommendationRankResponse buildResponse(
            Recommendation recommendation,
            ConstraintState constraints,
            RecommendationWeights weights,
            List<RecommendationItemVO> results
    ) {
        RecommendationRankResponse response =
                new RecommendationRankResponse();

        response.setRecommendationId(
                recommendation.getId()
        );

        response.setSessionId(
                recommendation.getSessionId()
        );

        response.setAlgorithmVersion(
                recommendation.getAlgorithmVersion()
        );

        response.setConstraints(constraints);
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

        return response;
    }

    /**
     * 将对象序列化为合法JSON字符串。
     */
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
}