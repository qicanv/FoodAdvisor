package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.dto.dialogue.DialogueContinueResponse;
import com.foodadvisor.dto.dialogue.DialogueHistoryResponse;
import com.foodadvisor.dto.dialogue.DialogueMessageRequest;
import com.foodadvisor.dto.dialogue.DialogueMessageResponse;
import com.foodadvisor.dto.dialogue.DialogueMessageVO;
import com.foodadvisor.dto.dialogue.FollowUpQuestionVO;
import com.foodadvisor.dto.recommendation.RecommendationItemVO;
import com.foodadvisor.dto.recommendation.AdjustmentSuggestionVO;
import com.foodadvisor.dto.recommendation.LimitingConditionVO;
import com.foodadvisor.dto.recommendation.RecommendationAdjustRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankRequest;
import com.foodadvisor.dto.recommendation.RecommendationRankResponse;
import com.foodadvisor.entity.ChatMessage;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Recommendation;
import com.foodadvisor.entity.RecommendationItem;
import com.foodadvisor.mapper.ChatMessageMapper;
import com.foodadvisor.mapper.ChatSessionMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.RecommendationItemMapper;
import com.foodadvisor.mapper.RecommendationMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DiningDialogueMessageService {

    private static final String ROLE_USER = "USER";

    private static final String ROLE_ASSISTANT = "ASSISTANT";

    private static final String TYPE_CLARIFICATION =
            "CLARIFICATION";

    private static final String TYPE_RECOMMENDATION =
            "RECOMMENDATION";

    private static final String TYPE_NO_MATCH = "NO_MATCH";

    private static final String MESSAGE_TYPE_TEXT = "TEXT";

    private static final String MESSAGE_TYPE_QUESTION = "QUESTION";

    private static final String MESSAGE_TYPE_ERROR = "ERROR";

    private static final String MESSAGE_TYPE_SYSTEM_NOTICE =
            "SYSTEM_NOTICE";

    private static final Duration IDEMPOTENCY_TTL =
            Duration.ofSeconds(60);

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RecommendationMapper recommendationMapper;
    private final RecommendationItemMapper recommendationItemMapper;
    private final MerchantMapper merchantMapper;
    private final DialogueService dialogueService;
    private final RecommendationRankingService recommendationRankingService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public DiningDialogueMessageService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            RecommendationMapper recommendationMapper,
            RecommendationItemMapper recommendationItemMapper,
            MerchantMapper merchantMapper,
            DialogueService dialogueService,
            RecommendationRankingService recommendationRankingService,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.recommendationMapper = recommendationMapper;
        this.recommendationItemMapper = recommendationItemMapper;
        this.merchantMapper = merchantMapper;
        this.dialogueService = dialogueService;
        this.recommendationRankingService =
                recommendationRankingService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public DialogueMessageResponse sendMessage(
            Long sessionId,
            DialogueMessageRequest request
    ) {
        validateRequest(sessionId, request);
        validateSessionOwner(sessionId, request.getUserId());

        DialogueMessageResponse existing =
                findCompletedResponse(
                        sessionId,
                        request.getRequestId()
                );

        if (existing != null) {
            return existing;
        }

        if (selectMessage(
                sessionId,
                request.getRequestId(),
                ROLE_USER
        ) != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REQUEST_PROCESSING",
                    "请求正在处理中，请稍后重试"
            );
        }

        String lockKey =
                "dialogue:idempotency:"
                        + request.getUserId()
                        + ":"
                        + sessionId
                        + ":"
                        + request.getRequestId();

        String lockToken = acquireLock(lockKey);

        if (lockToken == null) {
            existing = findCompletedResponse(
                    sessionId,
                    request.getRequestId()
            );

            if (existing != null) {
                return existing;
            }

            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "REQUEST_PROCESSING",
                    "请求正在处理中，请稍后重试"
            );
        }

        try {
            return processMessage(sessionId, request);
        } finally {
            releaseLock(lockKey, lockToken);
        }
    }

    @Transactional(readOnly = true)
    public DialogueHistoryResponse listMessages(
            Long sessionId,
            Long userId
    ) {
        validateUser(userId);
        validateSessionOwner(sessionId, userId);

        List<ChatMessage> messages =
                chatMessageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(
                                        ChatMessage::getSessionId,
                                        sessionId
                                )
                                .orderByAsc(
                                        ChatMessage::getCreatedAt
                                )
                                .orderByAsc(ChatMessage::getId)
                );

        DialogueHistoryResponse response =
                new DialogueHistoryResponse();
        response.setSessionId(sessionId);

        if (messages == null) {
            return response;
        }

        for (ChatMessage message : messages) {
            response.getMessages().add(
                    toMessageVO(message)
            );
        }

        return response;
    }

    @Transactional
    public RecommendationRankResponse adjustRecommendation(
            Long sessionId,
            RecommendationAdjustRequest request
    ) {
        if (request == null
                || request.getSourceMessageId() == null
                || request.getSourceMessageId() <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SOURCE_MESSAGE_REQUIRED",
                    "sourceMessageId不能为空"
            );
        }

        validateSessionOwner(sessionId, request.getUserId());

        ChatMessage assistantMessage =
                chatMessageMapper.selectOne(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(
                                        ChatMessage::getId,
                                        request.getSourceMessageId()
                                )
                                .eq(
                                        ChatMessage::getSessionId,
                                        sessionId
                                )
                                .last("FOR UPDATE")
                );

        if (assistantMessage == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SOURCE_MESSAGE_NOT_FOUND",
                    "待调整的助手消息不存在"
            );
        }
        if (!ROLE_ASSISTANT.equals(
                assistantMessage.getRole()
        )) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SOURCE_MESSAGE_NOT_ASSISTANT",
                    "只能调整助手推荐消息"
            );
        }
        if (!TYPE_RECOMMENDATION.equals(
                assistantMessage.getMessageType()
        )) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SOURCE_MESSAGE_NOT_RECOMMENDATION",
                    "源消息不是推荐类型"
            );
        }

        RecommendationRankResponse recommendation =
                recommendationRankingService.adjustAndRank(
                        sessionId,
                        request
                );

        updateAdjustedAssistantMessage(
                assistantMessage,
                recommendation
        );
        return recommendation;
    }

    protected DialogueMessageResponse processMessage(
            Long sessionId,
            DialogueMessageRequest request
    ) {
        DialogueContinueResponse dialogue =
                dialogueService.continueDialogue(
                        sessionId,
                        request.getUserId(),
                        request.getContent(),
                        request.getRequestId()
                );

        if (!dialogue.isReadyForRecommendation()) {
            String assistantText =
                    buildClarificationText(dialogue);
            ChatMessage assistant =
                    saveAssistantMessage(
                            sessionId,
                            request.getRequestId(),
                            assistantText,
                            TYPE_CLARIFICATION,
                            "NEED_MORE_INFORMATION",
                            null,
                            dialogue.getExtractor(),
                            dialogue.getDegraded()
                    );

            DialogueMessageResponse response =
                    buildResponse(
                    sessionId,
                    dialogue.getUserMessageId(),
                    assistant.getId(),
                    request.getRequestId(),
                    TYPE_CLARIFICATION,
                    assistantText,
                    dialogue,
                    null
            );

            saveResponseSnapshot(assistant, response);
            return response;
        }

        RecommendationRankRequest rankRequest =
                new RecommendationRankRequest();
        rankRequest.setUserId(request.getUserId());
        rankRequest.setUserLatitude(
                request.getUserLatitude()
        );
        rankRequest.setUserLongitude(
                request.getUserLongitude()
        );

        RecommendationRankResponse recommendation =
                recommendationRankingService.rank(
                        sessionId,
                        rankRequest
                );

        String responseType =
                Boolean.TRUE.equals(
                        recommendation.getMatched()
                )
                        ? TYPE_RECOMMENDATION
                        : TYPE_NO_MATCH;

        String assistantText =
                buildRecommendationText(recommendation);

        ChatMessage assistant =
                saveAssistantMessage(
                        sessionId,
                        request.getRequestId(),
                        assistantText,
                        responseType,
                        recommendation.getStatus(),
                        recommendation.getRecommendationId(),
                        dialogue.getExtractor(),
                        dialogue.getDegraded()
                );

        attachRecommendationMessages(
                recommendation.getRecommendationId(),
                dialogue.getUserMessageId(),
                assistant.getId(),
                assistantText
        );

        DialogueMessageResponse response =
                buildResponse(
                sessionId,
                dialogue.getUserMessageId(),
                assistant.getId(),
                request.getRequestId(),
                responseType,
                assistantText,
                dialogue,
                recommendation
        );

        saveResponseSnapshot(assistant, response);
        return response;
    }

    private void validateRequest(
            Long sessionId,
            DialogueMessageRequest request
    ) {
        if (sessionId == null || sessionId <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SESSION_REQUIRED",
                    "sessionId不能为空"
            );
        }

        if (request == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REQUEST_REQUIRED",
                    "请求不能为空"
            );
        }

        validateUser(request.getUserId());

        if (request.getContent() == null
                || request.getContent().trim().isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "EMPTY_MESSAGE",
                    "消息内容不能为空"
            );
        }

        if (request.getContent().trim().length() > 1000) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MESSAGE_TOO_LONG",
                    "消息内容不能超过1000个字符"
            );
        }

        if (request.getRequestId() == null
                || request.getRequestId().isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "REQUEST_ID_REQUIRED",
                    "requestId不能为空"
            );
        }

        if (!request.isLocationPairValid()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "LOCATION_PAIR_INVALID",
                    "userLatitude和userLongitude必须同时提供或同时不提供"
            );
        }
    }

    private void validateUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "缺少有效用户身份"
            );
        }
    }

    private ChatSession validateSessionOwner(
            Long sessionId,
            Long userId
    ) {
        ChatSession session =
                chatSessionMapper.selectById(sessionId);

        if (session == null
                || !"ACTIVE".equals(session.getStatus())) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SESSION_NOT_FOUND",
                    "对话会话不存在或不可访问"
            );
        }

        if (!Objects.equals(session.getUserId(), userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "SESSION_ACCESS_DENIED",
                    "无权访问该对话会话"
            );
        }

        return session;
    }

    private String acquireLock(String lockKey) {
        String token =
                "lock-" + UUID.randomUUID();

        try {
            Boolean result =
                    redisTemplate.opsForValue()
                            .setIfAbsent(
                                    lockKey,
                                    token,
                                    IDEMPOTENCY_TTL
                            );
            return Boolean.TRUE.equals(result)
                    ? token
                    : null;
        } catch (RedisConnectionFailureException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IDEMPOTENCY_SERVICE_ERROR",
                    "请求幂等服务暂时不可用，请稍后重试"
            );
        }
    }

    private void releaseLock(
            String lockKey,
            String lockToken
    ) {
        try {
            Object currentValue =
                    redisTemplate.opsForValue()
                            .get(lockKey);

            if (lockToken.equals(currentValue)) {
                redisTemplate.delete(lockKey);
            }
        } catch (RedisConnectionFailureException ignored) {
            // The key has a TTL, so a release failure will not leave a permanent lock.
        }
    }

    private DialogueMessageResponse findCompletedResponse(
            Long sessionId,
            String requestId
    ) {
        ChatMessage userMessage =
                selectMessage(
                        sessionId,
                        requestId,
                        ROLE_USER
                );

        ChatMessage assistantMessage =
                selectMessage(
                        sessionId,
                        requestId,
                        ROLE_ASSISTANT
                );

        if (userMessage == null || assistantMessage == null) {
            return null;
        }

        Map<String, Object> metadata =
                parseMetadata(assistantMessage);

        Object snapshot =
                metadata.get("responseSnapshot");

        if (snapshot != null) {
            DialogueMessageResponse response =
                    objectMapper.convertValue(
                            snapshot,
                            DialogueMessageResponse.class
                    );
            response.setSessionId(sessionId);
            response.setUserMessageId(userMessage.getId());
            response.setAssistantMessageId(
                    assistantMessage.getId()
            );
            response.setRequestId(requestId);
            return response;
        }

        DialogueMessageResponse response =
                new DialogueMessageResponse();
        response.setSessionId(sessionId);
        response.setUserMessageId(userMessage.getId());
        response.setAssistantMessageId(
                assistantMessage.getId()
        );
        response.setRequestId(requestId);
        response.setResponseType(
                asString(metadata.get("responseType"))
        );
        response.setAssistantText(
                assistantMessage.getContent()
        );
        response.setExtractor(
                asString(metadata.get("extractor"))
        );
        response.setDegraded(
                asBoolean(metadata.get("degraded"))
        );

        Long recommendationId =
                asLong(metadata.get("recommendationId"));

        if (recommendationId != null) {
            response.setRecommendation(
                    loadRecommendationResponse(
                            recommendationId
                    )
            );
        }

        return response;
    }

    private ChatMessage selectMessage(
            Long sessionId,
            String requestId,
            String role
    ) {
        return chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getRequestId, requestId)
                        .eq(ChatMessage::getRole, role)
                        .last("LIMIT 1")
        );
    }

    private ChatMessage saveAssistantMessage(
            Long sessionId,
            String requestId,
            String content,
            String responseType,
            String status,
            Long recommendationId,
            String extractor,
            Boolean degraded
    ) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(ROLE_ASSISTANT);
        message.setContent(content);
        message.setRequestId(requestId);
        message.setMessageType(
                mapResponseTypeToMessageType(responseType)
        );
        message.setMetadata(
                toJson(metadata(
                        requestId,
                        responseType,
                        status,
                        recommendationId,
                        extractor,
                        degraded
                ))
        );
        message.setCreatedAt(OffsetDateTime.now());

        int rows = chatMessageMapper.insert(message);

        if (rows != 1 || message.getId() == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MESSAGE_SAVE_FAILED",
                    "助手消息保存失败"
            );
        }

        return message;
    }

    static String mapResponseTypeToMessageType(
            String responseType
    ) {
        if (responseType == null) {
            return MESSAGE_TYPE_TEXT;
        }

        return switch (responseType) {
            case TYPE_NO_MATCH, TYPE_RECOMMENDATION ->
                    TYPE_RECOMMENDATION;
            case TYPE_CLARIFICATION, MESSAGE_TYPE_QUESTION ->
                    MESSAGE_TYPE_QUESTION;
            case MESSAGE_TYPE_ERROR -> MESSAGE_TYPE_ERROR;
            case MESSAGE_TYPE_SYSTEM_NOTICE ->
                    MESSAGE_TYPE_SYSTEM_NOTICE;
            case MESSAGE_TYPE_TEXT -> MESSAGE_TYPE_TEXT;
            default -> MESSAGE_TYPE_TEXT;
        };
    }

    private void saveResponseSnapshot(
            ChatMessage assistantMessage,
            DialogueMessageResponse response
    ) {
        Map<String, Object> metadata =
                parseMetadata(assistantMessage);
        Map<String, Object> updated =
                new LinkedHashMap<>(metadata);
        updated.put("responseSnapshot", response);
        if (response.getRecommendation() != null) {
            updated.put(
                    "adjustmentSuggestions",
                    response.getRecommendation()
                            .getAdjustmentSuggestions()
            );
        }

        assistantMessage.setMetadata(toJson(updated));

        int rows =
                chatMessageMapper.updateById(
                        assistantMessage
                );

        if (rows != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MESSAGE_UPDATE_FAILED",
                    "助手消息响应快照保存失败"
            );
        }
    }

    private void updateAdjustedAssistantMessage(
            ChatMessage assistantMessage,
            RecommendationRankResponse recommendation
    ) {
        String responseType =
                "SUCCESS".equals(recommendation.getStatus())
                        ? TYPE_RECOMMENDATION
                        : TYPE_NO_MATCH;
        String assistantText =
                buildRecommendationText(recommendation);

        Map<String, Object> existing =
                parseMetadata(assistantMessage);
        Map<String, Object> updated =
                new LinkedHashMap<>(existing);
        Object previousRecommendationId =
                existing.get("recommendationId");
        DialogueMessageResponse snapshot =
                readResponseSnapshot(existing);
        String previousRequestId =
                snapshot.getRecommendation() == null
                        ? null
                        : snapshot.getRecommendation()
                                .getRequestId();

        updated.put(
                "sourceMessageId",
                assistantMessage.getId()
        );
        updated.put(
                "previousRecommendationId",
                previousRecommendationId
        );
        if (previousRequestId != null) {
            updated.put(
                    "previousRequestId",
                    previousRequestId
            );
        }
        updated.put("responseType", responseType);
        updated.put("status", recommendation.getStatus());
        updated.put(
                "recommendationId",
                recommendation.getRecommendationId()
        );
        updated.put(
                "currentConstraints",
                recommendation.getCurrentConstraints()
        );
        updated.put(
                "limitingConditions",
                recommendation.getLimitingConditions()
        );
        updated.put(
                "adjustmentSuggestions",
                recommendation.getAdjustmentSuggestions()
        );

        snapshot.setSessionId(assistantMessage.getSessionId());
        snapshot.setAssistantMessageId(
                assistantMessage.getId()
        );
        snapshot.setRequestId(
                assistantMessage.getRequestId()
        );
        snapshot.setResponseType(responseType);
        snapshot.setAssistantText(assistantText);
        snapshot.setCurrentConstraints(
                recommendation.getCurrentConstraints()
        );
        snapshot.setRecommendation(recommendation);

        if (snapshot.getUserMessageId() == null
                && assistantMessage.getRequestId() != null) {
            ChatMessage userMessage =
                    selectMessage(
                            assistantMessage.getSessionId(),
                            assistantMessage.getRequestId(),
                            ROLE_USER
                    );
            if (userMessage != null) {
                snapshot.setUserMessageId(userMessage.getId());
            }
        }
        updated.put("responseSnapshot", snapshot);

        assistantMessage.setContent(assistantText);
        assistantMessage.setMessageType(TYPE_RECOMMENDATION);
        assistantMessage.setMetadata(toJson(updated));

        int rows =
                chatMessageMapper.updateById(
                        assistantMessage
                );
        if (rows != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MESSAGE_UPDATE_FAILED",
                    "调整后的助手消息保存失败"
            );
        }

        attachRecommendationMessages(
                recommendation.getRecommendationId(),
                snapshot.getUserMessageId(),
                assistantMessage.getId(),
                assistantText
        );
    }

    private DialogueMessageResponse readResponseSnapshot(
            Map<String, Object> metadata
    ) {
        Object snapshot = metadata.get("responseSnapshot");
        if (snapshot == null) {
            return new DialogueMessageResponse();
        }
        return objectMapper.convertValue(
                snapshot,
                DialogueMessageResponse.class
        );
    }

    private Map<String, Object> metadata(
            String requestId,
            String responseType,
            String status,
            Long recommendationId,
            String extractor,
            Boolean degraded
    ) {
        Map<String, Object> metadata =
                new LinkedHashMap<>();
        metadata.put("requestId", requestId);
        metadata.put("responseType", responseType);
        metadata.put("status", status);
        metadata.put(
                "extractor",
                extractor == null
                        ? "RULE_FALLBACK"
                        : extractor
        );
        metadata.put(
                "degraded",
                degraded == null || degraded
        );

        if (recommendationId != null) {
            metadata.put("recommendationId", recommendationId);
        }

        return metadata;
    }

    private void attachRecommendationMessages(
            Long recommendationId,
            Long userMessageId,
            Long assistantMessageId,
            String replyText
    ) {
        if (recommendationId == null) {
            return;
        }

        Recommendation recommendation =
                recommendationMapper.selectById(
                        recommendationId
                );

        if (recommendation == null) {
            return;
        }

        recommendation.setUserMessageId(userMessageId);
        recommendation.setAssistantMessageId(
                assistantMessageId
        );
        recommendation.setReplyText(replyText);

        int rows =
                recommendationMapper.updateById(
                        recommendation
                );

        if (rows != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "RECOMMENDATION_UPDATE_FAILED",
                    "推荐记录关联消息失败"
            );
        }
    }

    private DialogueMessageResponse buildResponse(
            Long sessionId,
            Long userMessageId,
            Long assistantMessageId,
            String requestId,
            String responseType,
            String assistantText,
            DialogueContinueResponse dialogue,
            RecommendationRankResponse recommendation
    ) {
        DialogueMessageResponse response =
                new DialogueMessageResponse();
        response.setSessionId(sessionId);
        response.setUserMessageId(userMessageId);
        response.setAssistantMessageId(
                assistantMessageId
        );
        response.setRequestId(requestId);
        response.setResponseType(responseType);
        response.setAssistantText(assistantText);
        response.setConversationStage(
                dialogue.getStage()
        );
        response.setCurrentConstraints(
                dialogue.getConstraints()
        );
        response.setMissingFields(
                dialogue.getMissingFields()
        );
        response.setRecommendation(recommendation);
        response.setExtractor(dialogue.getExtractor());
        response.setDegraded(dialogue.getDegraded());
        return response;
    }

    private String buildClarificationText(
            DialogueContinueResponse dialogue
    ) {
        if ("GENERAL_CHAT".equals(dialogue.getIntent())) {
            return "我可以根据人数、预算、菜系、距离和用餐场景为您推荐商家，请告诉我您的用餐需求。";
        }

        if ("UNKNOWN".equals(dialogue.getIntent())) {
            return "请补充您的探店需求，例如人数、预算、菜系、距离或用餐场景。";
        }

        if (dialogue.getQuestions() == null
                || dialogue.getQuestions().isEmpty()) {
            return "还需要补充一些用餐需求后，我再为您推荐商家。";
        }

        List<String> questions =
                new ArrayList<>();

        for (FollowUpQuestionVO question
                : dialogue.getQuestions()) {
            if (question != null
                    && question.getQuestion() != null
                    && !question.getQuestion().isBlank()) {
                questions.add(question.getQuestion());
            }
        }

        if (questions.isEmpty()) {
            return "还需要补充一些用餐需求后，我再为您推荐商家。";
        }

        return String.join(" ", questions);
    }

    private String buildRecommendationText(
            RecommendationRankResponse recommendation
    ) {
        if (recommendation == null) {
            return "推荐结果生成失败，请稍后重试。";
        }

        if (!Boolean.TRUE.equals(
                recommendation.getMatched()
        )) {
            return recommendation.getMessage();
        }

        return "为您找到 "
                + recommendation.getResultCount()
                + " 家符合条件的商家。";
    }

    private DialogueMessageVO toMessageVO(
            ChatMessage message
    ) {
        DialogueMessageVO vo =
                new DialogueMessageVO();
        vo.setId(message.getId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setRequestId(message.getRequestId());
        vo.setCreatedAt(message.getCreatedAt());

        Map<String, Object> metadata =
                parseMetadata(message);
        vo.setResponseType(
                asString(metadata.get("responseType"))
        );
        vo.setStatus(asString(metadata.get("status")));

        Long recommendationId =
                asLong(metadata.get("recommendationId"));
        vo.setRecommendationId(recommendationId);

        if (recommendationId != null) {
            vo.setRecommendations(
                    loadRecommendationItems(
                            recommendationId
                    )
            );
        }
        vo.setAdjustmentSuggestions(
                readAdjustmentSuggestions(metadata)
        );
        vo.setLimitingConditions(
                readMetadataList(
                        metadata,
                        "limitingConditions",
                        new TypeReference<
                                List<LimitingConditionVO>>() {
                        }
                )
        );
        Object currentConstraints =
                metadata.get("currentConstraints");
        if (currentConstraints != null) {
            vo.setCurrentConstraints(
                    objectMapper.convertValue(
                            currentConstraints,
                            com.foodadvisor.dto.constraint
                                    .ConstraintState.class
                    )
            );
        }

        return vo;
    }

    private <T> T readMetadataList(
            Map<String, Object> metadata,
            String key,
            TypeReference<T> type
    ) {
        Object value = metadata.get(key);
        if (value == null) {
            return objectMapper.convertValue(
                    List.of(),
                    type
            );
        }
        return objectMapper.convertValue(value, type);
    }

    private List<AdjustmentSuggestionVO>
            readAdjustmentSuggestions(
            Map<String, Object> metadata
    ) {
        Object directSuggestions =
                metadata.get("adjustmentSuggestions");
        if (directSuggestions != null) {
            return objectMapper.convertValue(
                    directSuggestions,
                    new TypeReference<
                            List<AdjustmentSuggestionVO>>() {
                    }
            );
        }

        Object snapshot = metadata.get("responseSnapshot");
        if (!(snapshot instanceof Map<?, ?> snapshotMap)) {
            return new ArrayList<>();
        }

        Object recommendation =
                snapshotMap.get("recommendation");
        if (!(recommendation
                instanceof Map<?, ?> recommendationMap)) {
            return new ArrayList<>();
        }

        Object suggestions =
                recommendationMap.get(
                        "adjustmentSuggestions"
                );
        if (!(suggestions instanceof List<?> list)) {
            return new ArrayList<>();
        }

        return objectMapper.convertValue(
                list,
                new TypeReference<
                        List<AdjustmentSuggestionVO>>() {
                }
        );
    }

    private List<RecommendationItemVO> loadRecommendationItems(
            Long recommendationId
    ) {
        List<RecommendationItem> items =
                recommendationItemMapper.selectList(
                        new LambdaQueryWrapper
                                <RecommendationItem>()
                                .eq(
                                        RecommendationItem
                                                ::getRecommendationId,
                                        recommendationId
                                )
                                .orderByAsc(
                                        RecommendationItem::getRankNo
                                )
                );

        List<RecommendationItemVO> results =
                new ArrayList<>();

        if (items == null) {
            return results;
        }

        for (RecommendationItem item : items) {
            Merchant merchant =
                    merchantMapper.selectById(
                            item.getMerchantId()
                    );

            if (merchant == null) {
                continue;
            }

            RecommendationItemVO vo =
                    new RecommendationItemVO();
            vo.setRankNo(item.getRankNo());
            vo.setMerchantId(merchant.getId());
            vo.setMerchantName(merchant.getName());
            vo.setCategory(merchant.getCategory());
            vo.setCuisine(merchant.getCuisine());
            vo.setMerchantRating(merchant.getRating());
            vo.setAveragePrice(
                    merchant.getAveragePrice()
            );
            vo.setReviewCount(merchant.getReviewCount());
            vo.setOperationStatus(
                    merchant.getOperationStatus()
            );
            vo.setFinalScore(
                    item.getScore() == null
                            ? null
                            : item.getScore().multiply(
                            new BigDecimal("100")
                    )
            );
            vo.setReason(item.getReason());
            results.add(vo);
        }

        return results;
    }

    private RecommendationRankResponse loadRecommendationResponse(
            Long recommendationId
    ) {
        Recommendation recommendation =
                recommendationMapper.selectById(
                        recommendationId
                );

        if (recommendation == null) {
            return null;
        }

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
        response.setStatus(recommendation.getStatus());
        response.setMessage(
                recommendation.getReplyText()
        );
        response.setResultCount(
                recommendation.getResultCount()
        );
        response.setMatched(
                "SUCCESS".equals(
                        recommendation.getStatus()
                )
        );
        response.setResults(
                loadRecommendationItems(
                        recommendationId
                )
        );
        return response;
    }

    private Map<String, Object> parseMetadata(
            ChatMessage message
    ) {
        if (message == null
                || message.getMetadata() == null
                || message.getMetadata().isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(
                    message.getMetadata(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "METADATA_SERIALIZE_FAILED",
                    "消息元数据序列化失败"
            );
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        if (value == null) {
            return null;
        }

        return Boolean.valueOf(value.toString());
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value == null) {
            return null;
        }

        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
