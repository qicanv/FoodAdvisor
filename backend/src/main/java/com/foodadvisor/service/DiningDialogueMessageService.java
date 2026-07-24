package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.foodadvisor.dto.recommendation.MatchedDishVO;
import com.foodadvisor.dto.recommendation.RecommendationBasisVO;
import com.foodadvisor.entity.ChatMessage;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Recommendation;
import com.foodadvisor.entity.RecommendationItem;
import com.foodadvisor.entity.RecommendationEvidence;
import com.foodadvisor.mapper.ChatMessageMapper;
import com.foodadvisor.mapper.ChatSessionMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.RecommendationItemMapper;
import com.foodadvisor.mapper.RecommendationMapper;
import com.foodadvisor.mapper.RecommendationEvidenceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import com.foodadvisor.trace.AiTraceContext;
import com.foodadvisor.entity.AiRequestTraceStage;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class DiningDialogueMessageService {
    private static final java.util.concurrent.atomic.AtomicInteger TRACE_SEQUENCE =
            new java.util.concurrent.atomic.AtomicInteger(3000);

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
            Duration.ofMinutes(3);

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final RecommendationMapper recommendationMapper;
    private final RecommendationItemMapper recommendationItemMapper;
    private final RecommendationEvidenceMapper
            recommendationEvidenceMapper;
    private final MerchantMapper merchantMapper;
    private final DialogueService dialogueService;
    private final ConstraintExtractionService
            constraintExtractionService;
    private final RecommendationRankingService recommendationRankingService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    @Autowired(required = false)
    private AiRequestTraceService traceService;
    @Autowired(required = false)
    private AIClientService aiClientService;

    public DiningDialogueMessageService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            RecommendationMapper recommendationMapper,
            RecommendationItemMapper recommendationItemMapper,
            RecommendationEvidenceMapper recommendationEvidenceMapper,
            MerchantMapper merchantMapper,
            DialogueService dialogueService,
            ConstraintExtractionService constraintExtractionService,
            RecommendationRankingService recommendationRankingService,
            RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.recommendationMapper = recommendationMapper;
        this.recommendationItemMapper = recommendationItemMapper;
        this.recommendationEvidenceMapper =
                recommendationEvidenceMapper;
        this.merchantMapper = merchantMapper;
        this.dialogueService = dialogueService;
        this.constraintExtractionService =
                constraintExtractionService;
        this.recommendationRankingService =
                recommendationRankingService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate =
                new TransactionTemplate(transactionManager);
    }

    public DialogueMessageResponse sendMessage(
            Long sessionId,
            DialogueMessageRequest request
    ) {
        validateRequest(sessionId, request);
        ChatSession session =
                validateSessionOwner(sessionId, request.getUserId());

        updateSessionTitleIfFirstMessage(
                sessionId,
                session.getTitle(),
                request.getContent());

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

        AiTraceContext context = null;
        try {
            context = startTrace(request.getRequestId(), sessionId,
                    request.getUserId(), "DINING_DIALOGUE_MESSAGE");
            recordRequestReceived(context, "DIALOGUE_MESSAGE");
            Long messageId = chatMessageMapper.nextId();
            if (messageId == null || messageId <= 0) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "MESSAGE_ID_RESERVE_FAILED",
                        "用户消息编号预留失败"
                );
            }

            ConstraintExtractionService.PreparedExtraction
                    preparedExtraction =
                    constraintExtractionService
                            .prepareExtraction(
                                    sessionId,
                                    request.getUserId(),
                                    request.getContent(),
                                    messageId,
                                    context
                            );

            AiTraceContext activeContext = context;
            DialogueMessageResponse response =
                    transactionTemplate.execute(status ->
                            processMessage(
                                    sessionId,
                                    request,
                                    messageId,
                                    preparedExtraction,
                                    activeContext
                            )
                    );
            if (response == null) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "MESSAGE_PROCESSING_FAILED",
                        "消息处理失败"
                );
            }
            completeTrace(context, response);
            return response;
        } catch (RuntimeException exception) {
            if (context != null && traceService != null) {
                traceService.failTraceSafely(
                        context, "DINING_DIALOGUE_FAILED", exception.getMessage());
            }
            throw exception;
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
        AiTraceContext context = startTrace(null, sessionId,
                request == null ? null : request.getUserId(),
                "DINING_RECOMMENDATION_ADJUST");
        recordRequestReceived(context, "RECOMMENDATION_ADJUST");
        try {
            RecommendationRankResponse response =
                    adjustRecommendation(sessionId, request, context);
            completeRecommendationTrace(context, response, "RECOMMENDATION_ADJUST");
            return response;
        } catch (RuntimeException exception) {
            if (traceService != null) {
                traceService.failTraceSafely(
                        context, "RECOMMENDATION_ADJUST_FAILED", exception.getMessage());
            }
            throw exception;
        }
    }

    public RecommendationRankResponse adjustRecommendation(
            Long sessionId,
            RecommendationAdjustRequest request,
            AiTraceContext context
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

        request.setQuery(
                restoreOriginalQuery(
                        sessionId,
                        request.getUserId(),
                        assistantMessage
                )
        );

        RecommendationRankResponse recommendation =
                traceService == null
                        ? recommendationRankingService.adjustAndRank(sessionId, request)
                        : recommendationRankingService.adjustAndRank(
                                sessionId, request, context);

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
        AiTraceContext context = startTrace(request.getRequestId(), sessionId,
                request.getUserId(), "DINING_DIALOGUE_MESSAGE");
        recordRequestReceived(context, "DIALOGUE_MESSAGE");
        try {
            DialogueMessageResponse response = processMessage(
                    sessionId,
                    request,
                    null,
                    null,
                    context
            );
            completeTrace(context, response);
            return response;
        } catch (RuntimeException exception) {
            if (traceService != null) {
                traceService.failTraceSafely(
                        context, "DIALOGUE_MESSAGE_FAILED", exception.getMessage());
            }
            throw exception;
        }
    }

    private DialogueMessageResponse processMessage(
            Long sessionId,
            DialogueMessageRequest request,
            Long messageId,
            ConstraintExtractionService.PreparedExtraction
                    preparedExtraction,
            AiTraceContext context
    ) {
        DialogueContinueResponse dialogue =
                preparedExtraction == null
                        ? (traceService == null
                        ? dialogueService.continueDialogue(
                                sessionId,
                                request.getUserId(),
                                request.getContent(),
                                request.getRequestId(),
                                null,
                                null
                        )
                        : dialogueService.continueDialogue(
                                sessionId,
                                request.getUserId(),
                                request.getContent(),
                                request.getRequestId(),
                                null,
                                null,
                                context
                        ))
                        : (traceService == null
                        ? dialogueService.continueDialogue(
                                sessionId,
                                request.getUserId(),
                                request.getContent(),
                                request.getRequestId(),
                                messageId,
                                preparedExtraction
                        )
                        : dialogueService.continueDialogue(
                                sessionId,
                                request.getUserId(),
                                request.getContent(),
                                request.getRequestId(),
                                messageId,
                                preparedExtraction,
                                context
                        ));

        if (!dialogue.isReadyForRecommendation()) {
            GeneratedReply generatedReply =
                    generateClarificationReply(dialogue);
            String assistantText = generatedReply.text();
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
            response.setReplyGenerator(generatedReply.generator());
            response.setReplyPromptVersion(generatedReply.promptVersion());
            response.setReplyDegraded(generatedReply.degraded());

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
        rankRequest.setQuery(request.getContent());

        RecommendationRankResponse recommendation =
                traceService == null
                        ? recommendationRankingService.rank(sessionId, rankRequest)
                        : recommendationRankingService.rank(
                                sessionId, rankRequest, context);

        String responseType =
                Boolean.TRUE.equals(
                        recommendation.getMatched()
                )
                        ? TYPE_RECOMMENDATION
                        : TYPE_NO_MATCH;

        GeneratedReply generatedReply =
                generateRecommendationReply(dialogue, recommendation);
        String assistantText = generatedReply.text();

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
        response.setReplyGenerator(generatedReply.generator());
        response.setReplyPromptVersion(generatedReply.promptVersion());
        response.setReplyDegraded(generatedReply.degraded());

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
            if (response.getTraceId() == null) {
                response.setTraceId(asString(metadata.get("traceId")));
            }
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
        response.setTraceId(asString(metadata.get("traceId")));
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
        updated.put("traceId", response.getTraceId());
        updated.put("replyGenerator", response.getReplyGenerator());
        updated.put("replyPromptVersion", response.getReplyPromptVersion());
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
        response.setTraceId(dialogue.getTraceId());
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
        response.setModelName(dialogue.getModelName());
        response.setPromptVersion(dialogue.getPromptVersion());
        return response;
    }

    private AiTraceContext startTrace(
            String requestId, Long sessionId, Long userId, String scene
    ) {
        return traceService == null
                ? AiTraceContext.create(requestId, sessionId, userId, scene)
                : traceService.startTrace(requestId, sessionId, userId, scene);
    }

    private void recordRequestReceived(AiTraceContext context, String responseType) {
        if (traceService == null) return;
        try {
            AiRequestTraceStage stage = traceService.startStage(
                    context, "REQUEST_RECEIVED",
                    Map.of("responseType", responseType));
            traceService.completeStage(stage, Map.of("responseType", responseType),
                    null, null, null, null);
        } catch (Exception ignored) {
            // Trace writes must not break the request.
        }
    }

    private void completeTrace(
            AiTraceContext context, DialogueMessageResponse response
    ) {
        if (traceService == null || response == null) return;
        try {
            traceService.updateStructuredConditions(
                    context, response.getCurrentConstraints());
            List<Long> merchantIds = response.getRecommendation() == null
                    || response.getRecommendation().getResults() == null
                    ? List.of()
                    : response.getRecommendation().getResults().stream()
                    .map(RecommendationItemVO::getMerchantId)
                    .filter(Objects::nonNull)
                    .toList();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("recommendationId", response.getRecommendation() == null
                    ? null : response.getRecommendation().getRecommendationId());
            summary.put("merchantIds", merchantIds);
            summary.put("resultCount", response.getRecommendation() == null
                    ? 0 : response.getRecommendation().getResultCount());
            summary.put("degraded", Boolean.TRUE.equals(response.getDegraded()));
            summary.put("responseType", response.getResponseType());
            summary.put("extractor", response.getExtractor());
            traceService.completeTrace(context,
                    Boolean.TRUE.equals(response.getDegraded()) ? "FALLBACK" : "SUCCESS",
                    summary,
                    Boolean.TRUE.equals(response.getDegraded()) ? null : "FASTAPI",
                    response.getModelName(),
                    null,
                    response.getPromptVersion());
        } catch (Exception ignored) {
            // Trace writes must not change a successful dialogue response.
        }
    }

    private void completeRecommendationTrace(
            AiTraceContext context, RecommendationRankResponse response,
            String responseType
    ) {
        if (traceService == null || response == null) return;
        try {
            List<Long> merchantIds = response.getResults() == null
                    ? List.of()
                    : response.getResults().stream()
                    .map(RecommendationItemVO::getMerchantId)
                    .filter(Objects::nonNull)
                    .toList();
            traceService.updateStructuredConditions(
                    context, response.getCurrentConstraints());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("recommendationId", response.getRecommendationId());
            summary.put("merchantIds", merchantIds);
            summary.put("resultCount", response.getResultCount());
            summary.put("degraded", false);
            summary.put("responseType", responseType);
            traceService.completeTrace(context, "SUCCESS", summary,
                    null, "RULE_ENGINE", null, "NOT_APPLICABLE");
        } catch (Exception ignored) {
            // Trace writes must not change a successful adjustment.
        }
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

    private GeneratedReply generateClarificationReply(
            DialogueContinueResponse dialogue
    ) {
        String fallback = buildClarificationText(dialogue);
        if (aiClientService == null) {
            return GeneratedReply.fallback(fallback);
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mode", "CLARIFICATION");
            payload.put("currentConstraints", dialogue.getConstraints());
            payload.put("changedFields", List.of());
            payload.put("missingFields", dialogue.getMissingFields());
            payload.put("conflicts", dialogue.getConflicts());
            payload.put("candidates", List.of());
            payload.put("gapFacts", List.of());
            payload.put("recentMessages", List.of());
            payload.put("maximumQuestions", 2);
            JsonNode result = aiClientService.generateDiningReply(payload);
            JsonNode questions = result.path("followUpQuestions");
            if (!questions.isArray() || questions.size() > 2) {
                return GeneratedReply.fallback(fallback);
            }
            return validatedText(result, fallback);
        } catch (Exception exception) {
            log.warn("Dining clarification reply degraded: {}",
                    exception.getClass().getSimpleName());
            return GeneratedReply.fallback(fallback);
        }
    }

    private GeneratedReply generateRecommendationReply(
            DialogueContinueResponse dialogue,
            RecommendationRankResponse recommendation
    ) {
        String fallback = buildRecommendationText(recommendation);
        if (aiClientService == null || recommendation == null) {
            return GeneratedReply.fallback(fallback);
        }
        try {
            Map<Long, java.util.Set<String>> allowedFacts =
                    new LinkedHashMap<>();
            Map<Long, java.util.Set<Long>> allowedEvidence =
                    new LinkedHashMap<>();
            List<Map<String, Object>> candidates = new ArrayList<>();
            for (RecommendationItemVO item : recommendation.getResults()) {
                Map<String, String> facts = new LinkedHashMap<>();
                int index = 1;
                for (String fact : item.getMatchedConditions()) {
                    if (fact != null && !fact.isBlank()) {
                        facts.put("m" + item.getMerchantId() + ".fact." + index++,
                                fact);
                    }
                }
                Map<String, String> risks = new LinkedHashMap<>();
                index = 1;
                for (String risk : item.getRiskNotes()) {
                    if (risk != null && !risk.isBlank()) {
                        risks.put("m" + item.getMerchantId() + ".risk." + index++,
                                risk);
                    }
                }
                List<Long> evidenceIds = item.getRecommendationBases().stream()
                        .map(RecommendationBasisVO::getEvidenceId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
                allowedFacts.put(item.getMerchantId(), facts.keySet());
                allowedEvidence.put(item.getMerchantId(),
                        new java.util.LinkedHashSet<>(evidenceIds));
                Map<String, Object> candidate = new LinkedHashMap<>();
                candidate.put("merchantId", item.getMerchantId());
                candidate.put("name", item.getMerchantName());
                candidate.put("facts", facts);
                candidate.put("riskFacts", risks);
                candidate.put("evidenceIds", evidenceIds);
                candidates.add(candidate);
            }

            List<Map<String, Object>> gapFacts = new ArrayList<>();
            int gapIndex = 1;
            Map<String, AdjustmentSuggestionVO> suggestionByField =
                    new LinkedHashMap<>();
            for (AdjustmentSuggestionVO suggestion :
                    recommendation.getAdjustmentSuggestions()) {
                if (suggestion.getField() != null) {
                    suggestionByField.putIfAbsent(
                            suggestion.getField(), suggestion);
                }
            }
            java.util.Set<String> representedGapFields =
                    new java.util.LinkedHashSet<>();
            for (LimitingConditionVO limit :
                    recommendation.getLimitingConditions()) {
                AdjustmentSuggestionVO suggestion =
                        suggestionByField.get(limit.getField());
                Map<String, Object> gap = new LinkedHashMap<>();
                gap.put("factId", "gap-" + gapIndex++);
                gap.put("field", limit.getField());
                gap.put("description", limit.getDescription());
                gap.put("currentValue", limit.getCurrentValue());
                gap.put("nearestCandidateValue", suggestion == null
                        ? null : suggestion.getSuggestedValue());
                gap.put("difference", suggestion == null
                        ? null : numericDifference(
                        suggestion.getCurrentValue(),
                        suggestion.getSuggestedValue()));
                gap.put("recoveredMerchantCount",
                        limit.getRecoveredMerchantCount() == null
                                ? 0 : limit.getRecoveredMerchantCount());
                gap.put("candidateMerchantIds",
                        limit.getCandidateMerchantIds() == null
                                ? List.of() : limit.getCandidateMerchantIds());
                gapFacts.add(gap);
                representedGapFields.add(limit.getField());
            }
            for (AdjustmentSuggestionVO suggestion :
                    recommendation.getAdjustmentSuggestions()) {
                if (representedGapFields.contains(suggestion.getField())) {
                    continue;
                }
                Map<String, Object> gap = new LinkedHashMap<>();
                gap.put("factId", suggestion.getId() == null
                        ? "gap-" + gapIndex++ : suggestion.getId());
                gap.put("field", suggestion.getField());
                gap.put("description", suggestion.getDisplayText() == null
                        ? suggestion.getReason() : suggestion.getDisplayText());
                gap.put("currentValue", suggestion.getCurrentValue());
                gap.put("nearestCandidateValue",
                        suggestion.getSuggestedValue());
                gap.put("difference", numericDifference(
                        suggestion.getCurrentValue(),
                        suggestion.getSuggestedValue()));
                gap.put("recoveredMerchantCount", 0);
                gap.put("candidateMerchantIds", List.of());
                gapFacts.add(gap);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mode", Boolean.TRUE.equals(recommendation.getMatched())
                    ? "RECOMMENDATION" : "NO_MATCH");
            payload.put("currentConstraints", dialogue.getConstraints());
            payload.put("changedFields", List.of());
            payload.put("missingFields", dialogue.getMissingFields());
            payload.put("conflicts", dialogue.getConflicts());
            payload.put("candidates", candidates);
            payload.put("gapFacts", gapFacts);
            payload.put("recentMessages", List.of());
            payload.put("maximumQuestions", 0);
            JsonNode result = aiClientService.generateDiningReply(payload);
            if (!validateReplyReferences(
                    result, allowedFacts, allowedEvidence,
                    Boolean.TRUE.equals(recommendation.getMatched()))) {
                return GeneratedReply.fallback(fallback);
            }
            for (JsonNode reason : result.path("merchantReasons")) {
                Long merchantId = reason.path("merchantId").asLong();
                recommendation.getResults().stream()
                        .filter(item -> Objects.equals(
                                item.getMerchantId(), merchantId))
                        .findFirst()
                        .ifPresent(item -> item.setReason(
                                reason.path("reason").asText(item.getReason())));
            }
            return validatedText(result, fallback);
        } catch (Exception exception) {
            log.warn("Dining recommendation reply degraded: {}",
                    exception.getClass().getSimpleName());
            return GeneratedReply.fallback(fallback);
        }
    }

    private Object numericDifference(Object current, Object suggested) {
        if (!(current instanceof Number left)
                || !(suggested instanceof Number right)) return null;
        return new BigDecimal(right.toString())
                .subtract(new BigDecimal(left.toString()));
    }

    private GeneratedReply validatedText(JsonNode result, String fallback) {
        if (result == null
                || result.path("assistantText").asText("").isBlank()) {
            return GeneratedReply.fallback(fallback);
        }
        return new GeneratedReply(
                result.path("assistantText").asText(),
                "AI_MODEL",
                false,
                result.path("promptVersion").asText("dining-reply:v1")
        );
    }

    private boolean validateReplyReferences(
            JsonNode result,
            Map<Long, java.util.Set<String>> allowedFacts,
            Map<Long, java.util.Set<Long>> allowedEvidence,
            boolean recommendationMode
    ) {
        if (result == null || !result.path("merchantReasons").isArray()) {
            return false;
        }
        for (JsonNode reason : result.path("merchantReasons")) {
            Long merchantId = reason.path("merchantId").asLong(-1);
            if (!allowedFacts.containsKey(merchantId)) return false;
            int basisCount = 0;
            for (JsonNode fact : reason.path("factIds")) {
                if (!allowedFacts.get(merchantId).contains(fact.asText())) {
                    return false;
                }
                basisCount++;
            }
            for (JsonNode evidence : reason.path("evidenceIds")) {
                if (!allowedEvidence.getOrDefault(merchantId, java.util.Set.of())
                        .contains(evidence.asLong())) {
                    return false;
                }
                basisCount++;
            }
            if (recommendationMode && basisCount < 2) return false;
        }
        return true;
    }

    private record GeneratedReply(
            String text,
            String generator,
            boolean degraded,
            String promptVersion
    ) {
        private static GeneratedReply fallback(String text) {
            return new GeneratedReply(
                    text, "TEMPLATE_FALLBACK", true, "NOT_APPLICABLE");
        }
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
        vo.setCurrentConstraints(
                readCurrentConstraints(metadata)
        );

        return vo;
    }

    private com.foodadvisor.dto.constraint.ConstraintState
            readCurrentConstraints(
            Map<String, Object> metadata
    ) {
        Object value = metadata.get("currentConstraints");
        if (value == null) {
            Object snapshot = metadata.get("responseSnapshot");
            if (snapshot instanceof Map<?, ?> snapshotMap) {
                value = snapshotMap.get("currentConstraints");
            }
        }
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(
                    value,
                    com.foodadvisor.dto.constraint
                            .ConstraintState.class
            );
        } catch (IllegalArgumentException ignored) {
            // 兼容缺字段或结构异常的历史消息，不阻断整个历史列表。
            return null;
        }
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

        Map<Long, List<MatchedDishVO>> matchedDishes =
                loadDishEvidence(items);
        Map<Long, List<RecommendationBasisVO>> recommendationBases =
                loadRecommendationBases(items);

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
            vo.setLongitude(merchant.getLongitude());
            vo.setLatitude(merchant.getLatitude());
            vo.setFinalScore(
                    item.getScore() == null
                            ? null
                            : item.getScore().multiply(
                            new BigDecimal("100")
                    )
            );
            vo.setDistanceKm(
                    readDistanceKm(item.getScoreDetails())
            );
            vo.setReason(item.getReason());
            vo.setMatchedDishes(
                    matchedDishes.getOrDefault(
                            item.getMerchantId(),
                            new ArrayList<>()
                    )
            );
            vo.setRecommendationBases(
                    recommendationBases.getOrDefault(
                            item.getMerchantId(), new ArrayList<>()));
            results.add(vo);
        }

        return results;
    }

    private Map<Long, List<RecommendationBasisVO>> loadRecommendationBases(
            List<RecommendationItem> items
    ) {
        Map<Long, List<RecommendationBasisVO>> result = new LinkedHashMap<>();
        List<Long> itemIds = items.stream().map(RecommendationItem::getId)
                .filter(Objects::nonNull).toList();
        if (itemIds.isEmpty()) return result;
        Map<Long, Long> merchants = items.stream().collect(
                java.util.stream.Collectors.toMap(
                        RecommendationItem::getId,
                        RecommendationItem::getMerchantId));
        List<RecommendationEvidence> evidences =
                recommendationEvidenceMapper.selectList(
                        new LambdaQueryWrapper<RecommendationEvidence>()
                                .in(RecommendationEvidence::getRecommendationItemId, itemIds)
                                .in(RecommendationEvidence::getSourceType,
                                        List.of("REVIEW", "MERCHANT"))
                                .orderByAsc(RecommendationEvidence::getId));
        for (RecommendationEvidence evidence :
                evidences == null ? List.<RecommendationEvidence>of() : evidences) {
            try {
                Long merchantId = merchants.get(evidence.getRecommendationItemId());
                if (merchantId == null
                        || !merchantId.equals(evidence.getSourceMerchantId())) continue;
                RecommendationBasisVO basis = objectMapper.readValue(
                        evidence.getSourceTextSnapshot(),
                        RecommendationBasisVO.class);
                if (!merchantId.equals(basis.getMerchantId())) continue;
                if (evidence.getConditionKey() != null
                        && !evidence.getConditionKey().isBlank()) {
                    basis.setMatchedCondition(evidence.getConditionKey());
                }
                if (basis.getMatchedCondition() == null
                        || basis.getMatchedCondition().isBlank()) continue;
                basis.setEvidenceId(evidence.getId());
                List<RecommendationBasisVO> list =
                        result.computeIfAbsent(merchantId, ignored -> new ArrayList<>());
                if (list.size() < 3) list.add(basis);
            } catch (Exception exception) {
                log.warn("Ignoring invalid recommendation evidence id={}",
                        evidence.getId());
            }
        }
        return result;
    }

    private Map<Long, List<MatchedDishVO>> loadDishEvidence(
            List<RecommendationItem> items
    ) {
        Map<Long, List<MatchedDishVO>> result =
                new LinkedHashMap<>();
        List<Long> itemIds = items.stream()
                .map(RecommendationItem::getId)
                .filter(Objects::nonNull)
                .toList();
        if (itemIds.isEmpty()) {
            return result;
        }
        Map<Long, Long> itemMerchants = new LinkedHashMap<>();
        for (RecommendationItem item : items) {
            itemMerchants.put(item.getId(), item.getMerchantId());
        }
        List<RecommendationEvidence> evidences =
                recommendationEvidenceMapper.selectList(
                        new LambdaQueryWrapper
                                <RecommendationEvidence>()
                                .in(
                                        RecommendationEvidence
                                                ::getRecommendationItemId,
                                        itemIds
                                )
                                .eq(
                                        RecommendationEvidence
                                                ::getSourceType,
                                        "DISH"
                                )
                                .orderByAsc(
                                        RecommendationEvidence::getId
                                )
                );
        for (RecommendationEvidence evidence :
                evidences == null
                        ? List.<RecommendationEvidence>of()
                        : evidences) {
            try {
                MatchedDishVO dish = objectMapper.readValue(
                        evidence.getSourceTextSnapshot(),
                        MatchedDishVO.class
                );
                Long expectedMerchant =
                        itemMerchants.get(
                                evidence.getRecommendationItemId()
                        );
                if (expectedMerchant == null
                        || !expectedMerchant.equals(
                        evidence.getSourceMerchantId()
                )
                        || !expectedMerchant.equals(
                        dish.getMerchantId()
                )) {
                    log.warn(
                            "Ignoring cross-merchant dish evidence id={}",
                            evidence.getId()
                    );
                    continue;
                }
                List<MatchedDishVO> merchantDishes =
                        result.computeIfAbsent(
                                expectedMerchant,
                                ignored -> new ArrayList<>()
                        );
                if (merchantDishes.size() < 3) {
                    merchantDishes.add(dish);
                }
            } catch (Exception exception) {
                log.warn(
                        "Ignoring invalid dish evidence snapshot id={}",
                        evidence.getId()
                );
            }
        }
        return result;
    }

    private BigDecimal readDistanceKm(String scoreDetails) {
        if (scoreDetails == null || scoreDetails.isBlank()) {
            return null;
        }

        try {
            Object value = objectMapper.readTree(scoreDetails)
                    .get("distanceKm");
            if (!(value instanceof
                    com.fasterxml.jackson.databind.JsonNode node)
                    || node.isNull()) {
                return null;
            }

            String text = node.isNumber()
                    ? node.asText()
                    : node.isTextual()
                    ? node.textValue()
                    : null;
            if (text == null || text.isBlank()) {
                return null;
            }

            BigDecimal distance = new BigDecimal(text);
            return distance.compareTo(BigDecimal.ZERO) < 0
                    ? null
                    : distance;
        } catch (Exception ignored) {
            return null;
        }
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
        response.setTraceId(recommendation.getTraceId());
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

    private String restoreOriginalQuery(
            Long sessionId,
            Long userId,
            ChatMessage assistantMessage
    ) {
        Map<String, Object> metadata =
                parseMetadata(assistantMessage);

        Long recommendationId =
                asLong(metadata.get("recommendationId"));

        if (recommendationId == null) {
            return null;
        }

        Recommendation recommendation =
                recommendationMapper.selectOne(
                        new LambdaQueryWrapper<Recommendation>()
                                .eq(
                                        Recommendation::getId,
                                        recommendationId
                                )
                                .eq(
                                        Recommendation::getSessionId,
                                        sessionId
                                )
                                .eq(
                                        Recommendation::getUserId,
                                        userId
                                )
                );

        if (recommendation == null
                || recommendation.getUserMessageId() == null) {
            return null;
        }

        ChatMessage userMessage =
                chatMessageMapper.selectOne(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(
                                        ChatMessage::getId,
                                        recommendation.getUserMessageId()
                                )
                                .eq(
                                        ChatMessage::getSessionId,
                                        sessionId
                                )
                                .eq(
                                        ChatMessage::getRole,
                                        ROLE_USER
                                )
                );

        if (userMessage == null
                || userMessage.getContent() == null
                || userMessage.getContent().isBlank()) {
            return null;
        }

        return userMessage.getContent();
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

    /**
     * 当会话标题仍为默认值时，用首条用户消息截断后更新标题。
     */
    private void updateSessionTitleIfFirstMessage(
            Long sessionId,
            String currentTitle,
            String userContent
    ) {
        if (!ChatSessionService.DEFAULT_TITLE.equals(currentTitle)) {
            return;
        }

        String newTitle = truncateTitle(userContent);
        if (newTitle.equals(currentTitle)) {
            return;
        }

        chatSessionMapper.update(
                null,
                new LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .set(ChatSession::getTitle, newTitle)
                        .set(ChatSession::getUpdatedAt,
                                OffsetDateTime.now())
        );
    }

    /**
     * 截断文本至最多 20 个字符作为会话标题。
     */
    private String truncateTitle(String text) {
        if (text == null || text.isBlank()) {
            return ChatSessionService.DEFAULT_TITLE;
        }

        String trimmed = text.trim();
        int maxLen = 20;

        if (trimmed.length() <= maxLen) {
            return trimmed;
        }

        return trimmed.substring(0, maxLen);
    }
}
