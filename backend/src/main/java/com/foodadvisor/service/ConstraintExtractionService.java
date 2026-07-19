package com.foodadvisor.service;

import com.foodadvisor.exception.ApiException;
import com.foodadvisor.entity.ChatMessage;
import com.foodadvisor.entity.ChatSession;
import com.foodadvisor.entity.ConstraintExtraction;
import com.foodadvisor.mapper.ConstraintExtractionMapper;
import com.foodadvisor.mapper.ChatMessageMapper;
import com.foodadvisor.mapper.ChatSessionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.foodadvisor.dto.ai.DialogueExtractAiRequest;
import com.foodadvisor.dto.ai.DialogueExtractAiResponse;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.constraint.ConstraintConflictVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.foodadvisor.entity.ChatSessionState;
import com.foodadvisor.mapper.ChatSessionStateMapper;
import com.foodadvisor.dto.constraint.ConstraintExtractResponse;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消费需求提取服务。
 *
 * 当前阶段负责：
 * 1. 校验对话会话；
 * 2. 保存用户本轮原始消息。
 *
 * 后续再逐步加入规则提取、状态合并和历史落库。
 */
@Service
public class ConstraintExtractionService {

    private static final Pattern PARTY_SIZE_PATTERN =
            Pattern.compile(
                    "([0-9一二两三四五六七八九十]+)\\s*(?:个人|人|位)"
            );

    /**
     * 可识别的阿拉伯数字和中文数字。
     */
    private static final String NUMBER_TOKEN =
            "[0-9零一二两三四五六七八九十百]+";

    /**
     * 人均预算：
     * 人均八十、每人100、每个人预算为120。
     */
    private static final Pattern PER_CAPITA_BUDGET_PATTERN =
            Pattern.compile(
                    "(?:人均|每人|每个人)"
                            + "\\s*(?:预算)?"
                            + "\\s*(?:是|为|改成|改为|调整为|换成)?"
                            + "\\s*(" + NUMBER_TOKEN + ")"
                            + "\\s*(?:元|块)?"
            );

    /**
     * 总预算：
     * 总共两百元、一共300、总预算为500、预算400。
     */
    private static final Pattern TOTAL_BUDGET_PATTERN =
            Pattern.compile(
                    "(?:总预算|总的预算|预算总共|总共|一共|预算)"
                            + "\\s*(?:是|为|有|改成|改为|调整为|换成)?"
                            + "\\s*(" + NUMBER_TOKEN + ")"
                            + "\\s*(?:元|块)?"
            );

    /**
     * 当前规则版支持识别的菜系。
     */
    private static final List<String> SUPPORTED_CUISINES =
            List.of(
                    "川菜",
                    "粤菜",
                    "湘菜",
                    "鲁菜",
                    "东北菜",
                    "西餐",
                    "日料",
                    "韩餐"
            );

    /**
     * 当前规则版支持识别的商家类型。
     */
    private static final List<String> SUPPORTED_MERCHANT_TYPES =
            List.of(
                    "火锅",
                    "烧烤",
                    "自助餐",
                    "烤肉",
                    "小吃",
                    "快餐"
            );

    /**
     * 表示排除、不接受的常见表达。
     */
    private static final List<String> NEGATION_PREFIXES =
            List.of(
                    "不要",
                    "不要吃",
                    "不吃",
                    "不想吃",
                    "不能吃",
                    "排除",
                    "不考虑",
                    "别选"
            );

    /**
     * 支持阿拉伯整数、阿拉伯小数和简单中文整数。
     */
    private static final String DECIMAL_OR_CHINESE_NUMBER =
            "(?:[0-9]+(?:\\.[0-9]+)?"
                    + "|[零一二两三四五六七八九十百]+)";

    /**
     * 距离要求：
     * 三公里内、附近2公里、2.5公里以内。
     */
    private static final Pattern DISTANCE_PATTERN =
            Pattern.compile(
                    "(?:附近|距离)?"
                            + "\\s*("
                            + DECIMAL_OR_CHINESE_NUMBER
                            + ")"
                            + "\\s*公里"
                            + "\\s*(?:内|以内|之内)?"
            );

    /**
     * 最低评分的前置表达：
     * 至少4.5分、最低4分、不低于4分。
     */
    private static final Pattern MIN_RATING_PREFIX_PATTERN =
            Pattern.compile(
                    "(?:评分\\s*)?"
                            + "(?:至少|最低|不低于)"
                            + "\\s*([0-5](?:\\.\\d+)?)"
                            + "\\s*分?"
            );

    /**
     * 最低评分的后置表达：
     * 评分4分以上、4.5分及以上。
     */
    private static final Pattern MIN_RATING_SUFFIX_PATTERN =
            Pattern.compile(
                    "(?:评分\\s*)?"
                            + "([0-5](?:\\.\\d+)?)"
                            + "\\s*分"
                            + "\\s*(?:以上|及以上|起)"
            );

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatSessionStateMapper chatSessionStateMapper;
    private final ConstraintExtractionMapper constraintExtractionMapper;
    private final AIClientService aiClientService;
    private final ObjectMapper objectMapper;

    public ConstraintExtractionService(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            ChatSessionStateMapper chatSessionStateMapper,
            ConstraintExtractionMapper constraintExtractionMapper,
            AIClientService aiClientService,
            ObjectMapper objectMapper
    ) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.chatSessionStateMapper = chatSessionStateMapper;
        this.constraintExtractionMapper =
                constraintExtractionMapper;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
    }

    /**
     * 完成一次消费需求提取、合并和持久化。
     *
     * 整个方法处于同一个事务中：
     * 任意一步失败时，本轮消息、状态和历史记录会一起回滚。
     */
    public ConstraintExtractResponse extractAndMerge(
            Long sessionId,
            Long userId,
            String message
    ) {
        return extractAndMerge(
                sessionId,
                userId,
                message,
                null
        );
    }

    public ConstraintExtractResponse extractAndMerge(
            Long sessionId,
            Long userId,
            String message,
            String requestId
    ) {
        /*
         * 1. 确认会话存在、属于当前用户并且仍处于 ACTIVE。
         */
        validateActiveSession(sessionId, userId);

        /*
         * 2. 保存用户本轮原始输入，并取得数据库生成的 messageId。
         */
        ChatMessage savedMessage =
                saveUserMessage(
                        sessionId,
                        message,
                        requestId
                );

        /*
         * 3. 读取该会话之前已经合并好的消费条件。
         */
        ConstraintState oldState =
                loadCurrentState(sessionId);

        /*
         * 4. 从本轮消息中提取结构化消费条件。
         */
        AiExtractionResult aiExtraction =
                tryExtractByAi(
                        sessionId,
                        savedMessage.getId(),
                        message,
                        oldState
                );

        ConstraintState extracted;
        List<String> clearedFields;
        String intent;
        String extractor;
        boolean degraded;

        if (aiExtraction == null) {
            extracted = extractByRules(message);
            clearedFields = List.of();
            intent = "MERCHANT_RECOMMENDATION";
            extractor = "RULE_FALLBACK";
            degraded = true;
        } else {
            extracted = aiExtraction.extracted();
            clearedFields = aiExtraction.clearedFields();
            intent = aiExtraction.intent();
            extractor = aiExtraction.extractor();
            degraded = aiExtraction.degraded();
        }

        /*
         * 5. 检测本轮消息是否存在自相矛盾的条件。
         */
        List<ConstraintConflictVO> conflicts =
                detectConflicts(message);

        ConstraintState merged;
        List<String> changes;

        /*
         * 有冲突时不自动修改当前约束。
         * 仍然保存 extracted 和 conflicts，等待后续追问确认。
         */
        if (conflicts.isEmpty()) {
            merged = merge(
                    oldState,
                    extracted,
                    message
            );
            applyClearedFields(merged, clearedFields);

            changes = detectChanges(
                    oldState,
                    merged
            );
        } else {
            merged = copyState(oldState);
            changes = new ArrayList<>();
        }

        /*
         * 6. 保存当前会话的最新状态。
         */
        saveSessionState(
                sessionId,
                merged,
                conflicts
        );

        /*
         * 7. 保存本轮提取历史，并关联用户消息。
         */
        saveExtraction(
                sessionId,
                savedMessage.getId(),
                extracted,
                merged,
                changes,
                conflicts
        );

        /*
         * 8. 组装接口响应。
         */
        ConstraintExtractResponse response =
                new ConstraintExtractResponse();

        response.setSessionId(sessionId);
        response.setMessageId(savedMessage.getId());
        response.setExtracted(extracted);
        response.setMerged(merged);
        response.setChanges(changes);
        response.setConflicts(conflicts);
        response.setIntent(intent);
        response.setExtractor(extractor);
        response.setDegraded(degraded);

        return response;
    }

    public PreparedExtraction prepareExtraction(
            Long sessionId,
            Long userId,
            String message,
            Long messageId
    ) {
        validateActiveSession(sessionId, userId);
        if (message == null || message.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MESSAGE_REQUIRED",
                    "message不能为空"
            );
        }

        ConstraintState currentState =
                loadCurrentState(sessionId);
        AiExtractionResult aiExtraction =
                tryExtractByAi(
                        sessionId,
                        messageId,
                        message,
                        currentState
                );

        if (aiExtraction == null) {
            return new PreparedExtraction(
                    extractByRules(message),
                    List.of(),
                    "MERCHANT_RECOMMENDATION",
                    "RULE_FALLBACK",
                    true,
                    detectConflicts(message)
            );
        }

        return new PreparedExtraction(
                aiExtraction.extracted(),
                aiExtraction.clearedFields(),
                aiExtraction.intent(),
                aiExtraction.extractor(),
                aiExtraction.degraded(),
                detectConflicts(message)
        );
    }

    public ConstraintExtractResponse extractAndMergePrepared(
            Long sessionId,
            Long userId,
            String message,
            String requestId,
            Long messageId,
            PreparedExtraction prepared
    ) {
        validateActiveSession(sessionId, userId);
        if (prepared == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "EXTRACTION_NOT_PREPARED",
                    "消费需求提取结果尚未准备"
            );
        }

        ChatMessage savedMessage =
                saveUserMessage(
                        sessionId,
                        message,
                        requestId,
                        messageId
                );
        ConstraintState oldState =
                loadCurrentState(sessionId);
        ConstraintState extracted =
                prepared.extracted();
        List<ConstraintConflictVO> conflicts =
                prepared.conflicts();
        ConstraintState merged;
        List<String> changes;

        if (conflicts.isEmpty()) {
            merged = merge(oldState, extracted, message);
            applyClearedFields(
                    merged,
                    prepared.clearedFields()
            );
            changes = detectChanges(oldState, merged);
        } else {
            merged = copyState(oldState);
            changes = new ArrayList<>();
        }

        saveSessionState(sessionId, merged, conflicts);
        saveExtraction(
                sessionId,
                savedMessage.getId(),
                extracted,
                merged,
                changes,
                conflicts
        );

        ConstraintExtractResponse response =
                new ConstraintExtractResponse();
        response.setSessionId(sessionId);
        response.setMessageId(savedMessage.getId());
        response.setExtracted(extracted);
        response.setMerged(merged);
        response.setChanges(changes);
        response.setConflicts(conflicts);
        response.setIntent(prepared.intent());
        response.setExtractor(prepared.extractor());
        response.setDegraded(prepared.degraded());
        return response;
    }

    /**
     * 校验会话是否存在、是否属于当前用户，以及是否仍可继续使用。
     *
     * @param sessionId 当前对话会话 ID
     * @param userId    当前临时登录用户 ID
     * @return 校验通过后的会话实体
     */
    public ChatSession validateActiveSession(
            Long sessionId,
            Long userId
    ) {
        if (sessionId == null) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "SESSION_REQUIRED",
                    "sessionId不能为空"
            );
        }

        if (userId == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "USER_REQUIRED",
                    "缺少用户身份信息"
            );
        }

        ChatSession session =
                chatSessionMapper.selectById(sessionId);

        if (session == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SESSION_NOT_FOUND",
                    "对话会话不存在"
            );
        }

        if (!Objects.equals(session.getUserId(), userId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "SESSION_ACCESS_DENIED",
                    "无权访问该对话会话"
            );
        }

        if (!"ACTIVE".equals(session.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "SESSION_NOT_ACTIVE",
                    "当前对话会话已关闭或归档"
            );
        }

        return session;
    }

    /**
     * 将用户本轮原始输入保存到 chat_messages。
     *
     * @param sessionId 当前会话 ID
     * @param message   用户输入的自然语言消息
     * @return 插入成功且已获得数据库主键的消息实体
     */
    private ChatMessage saveUserMessage(
            Long sessionId,
            String message,
            String requestId
    ) {
        return saveUserMessage(
                sessionId,
                message,
                requestId,
                null
        );
    }

    private ChatMessage saveUserMessage(
            Long sessionId,
            String message,
            String requestId,
            Long messageId
    ) {
        if (message == null || message.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "MESSAGE_REQUIRED",
                    "message不能为空"
            );
        }

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(messageId);
        chatMessage.setSessionId(sessionId);
        chatMessage.setRole("USER");
        chatMessage.setContent(message.trim());
        chatMessage.setMessageType("TEXT");
        chatMessage.setRequestId(requestId);
        chatMessage.setMetadata(
                toJson(java.util.Map.of(
                        "requestId",
                        requestId == null ? "" : requestId,
                        "extractor",
                        "RULE_FALLBACK",
                        "degraded",
                        true
                ))
        );
        chatMessage.setCreatedAt(OffsetDateTime.now());

        int affectedRows =
                messageId == null
                        ? chatMessageMapper.insert(chatMessage)
                        : chatMessageMapper.insertReserved(
                                chatMessage
                        );

        if (affectedRows != 1 || chatMessage.getId() == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MESSAGE_SAVE_FAILED",
                    "用户消息保存失败"
            );
        }

        return chatMessage;
    }

    public record PreparedExtraction(
            ConstraintState extracted,
            List<String> clearedFields,
            String intent,
            String extractor,
            boolean degraded,
            List<ConstraintConflictVO> conflicts
    ) {
    }

    /**
     * 使用固定规则从用户输入中提取消费条件。
     *
     * 当前阶段只提取用餐人数、预算、菜系、口味、环境、距离。
     */
    private AiExtractionResult tryExtractByAi(
            Long sessionId,
            Long messageId,
            String message,
            ConstraintState currentConstraints
    ) {
        try {
            DialogueExtractAiRequest request =
                    new DialogueExtractAiRequest();
            request.setSessionId(sessionId);
            request.setMessageId(messageId);
            request.setContent(message);
            request.setCurrentConstraints(
                    currentConstraints == null
                            ? new ConstraintState()
                            : currentConstraints
            );

            DialogueExtractAiResponse response =
                    aiClientService.extractDialogueConstraints(
                            request
                    );

            return validateAiResponse(response);
        } catch (Exception ignored) {
            return null;
        }
    }

    private AiExtractionResult validateAiResponse(
            DialogueExtractAiResponse response
    ) {
        if (response == null
                || !"AI_MODEL".equals(response.getExtractor())
                || Boolean.TRUE.equals(response.getDegraded())) {
            return null;
        }

        return new AiExtractionResult(
                normalizeIntent(response.getIntent()),
                sanitizeAiConstraints(
                        response.getExtractedConstraints()
                ),
                sanitizeClearedFields(
                        response.getClearedFields()
                ),
                "AI_MODEL",
                false
        );
    }

    private String normalizeIntent(String intent) {
        if ("MERCHANT_RECOMMENDATION".equals(intent)
                || "CONSTRAINT_UPDATE".equals(intent)
                || "GENERAL_CHAT".equals(intent)
                || "UNKNOWN".equals(intent)) {
            return intent;
        }

        return "UNKNOWN";
    }

    private ConstraintState sanitizeAiConstraints(
            ConstraintState source
    ) {
        ConstraintState target = new ConstraintState();

        if (source == null) {
            return target;
        }

        if (source.getPartySize() != null
                && source.getPartySize() > 0
                && source.getPartySize() <= 20) {
            target.setPartySize(source.getPartySize());
        }

        if (isPositiveFinite(source.getTotalBudget())) {
            target.setTotalBudget(source.getTotalBudget());
        }

        if (isPositiveFinite(source.getPerCapitaBudget())) {
            target.setPerCapitaBudget(
                    source.getPerCapitaBudget()
            );
        }

        if (isPositiveFinite(source.getDistanceKm())
                && source.getDistanceKm().compareTo(
                new BigDecimal("100")
        ) <= 0) {
            target.setDistanceKm(source.getDistanceKm());
        }

        if (source.getMinRating() != null
                && source.getMinRating().compareTo(
                BigDecimal.ZERO
        ) >= 0
                && source.getMinRating().compareTo(
                new BigDecimal("5")
        ) <= 0) {
            target.setMinRating(source.getMinRating());
        }

        target.setMerchantTypes(
                sanitizeStringList(source.getMerchantTypes())
        );
        target.setCuisines(
                sanitizeStringList(source.getCuisines())
        );
        target.setTastePreferences(
                sanitizeStringList(source.getTastePreferences())
        );
        target.setTasteRestrictions(
                sanitizeStringList(source.getTasteRestrictions())
        );
        target.setExcludedCuisines(
                sanitizeStringList(source.getExcludedCuisines())
        );
        target.setExcludedMerchantTypes(
                sanitizeStringList(
                        source.getExcludedMerchantTypes()
                )
        );
        target.setScenes(
                sanitizeStringList(source.getScenes())
        );
        target.setEnvironmentRequirements(
                sanitizeStringList(
                        source.getEnvironmentRequirements()
                )
        );

        if ("NOW_OPEN".equals(source.getBusinessTime())
                || "TONIGHT".equals(source.getBusinessTime())
                || "LATE_NIGHT".equals(
                source.getBusinessTime()
        )) {
            target.setBusinessTime(source.getBusinessTime());
        }

        calculatePerCapitaBudget(target);
        return target;
    }

    private boolean isPositiveFinite(BigDecimal value) {
        return value != null
                && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private List<String> sanitizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }

        LinkedHashSet<String> result = new LinkedHashSet<>();

        for (String value : values) {
            if (value == null) {
                continue;
            }

            String trimmed = value.trim();

            if (!trimmed.isEmpty()
                    && trimmed.length() <= 30) {
                result.add(trimmed);
            }
        }

        if (result.size() > 10) {
            return new ArrayList<>(
                    new ArrayList<>(result).subList(0, 10)
            );
        }

        return new ArrayList<>(result);
    }

    private List<String> sanitizeClearedFields(
            List<String> clearedFields
    ) {
        if (clearedFields == null || clearedFields.isEmpty()) {
            return List.of();
        }

        List<String> allowedFields =
                List.of(
                        "partySize",
                        "totalBudget",
                        "perCapitaBudget",
                        "merchantTypes",
                        "cuisines",
                        "tastePreferences",
                        "tasteRestrictions",
                        "excludedCuisines",
                        "excludedMerchantTypes",
                        "distanceKm",
                        "minRating",
                        "scenes",
                        "environmentRequirements",
                        "businessTime"
                );

        LinkedHashSet<String> result = new LinkedHashSet<>();

        for (String field : clearedFields) {
            if (field != null
                    && allowedFields.contains(field)) {
                result.add(field);
            }
        }

        return new ArrayList<>(result);
    }

    private void applyClearedFields(
            ConstraintState state,
            List<String> clearedFields
    ) {
        if (state == null
                || clearedFields == null
                || clearedFields.isEmpty()) {
            return;
        }

        for (String field : clearedFields) {
            switch (field) {
                case "partySize" -> state.setPartySize(null);
                case "totalBudget" -> state.setTotalBudget(null);
                case "perCapitaBudget" ->
                        state.setPerCapitaBudget(null);
                case "merchantTypes" ->
                        state.setMerchantTypes(new ArrayList<>());
                case "cuisines" ->
                        state.setCuisines(new ArrayList<>());
                case "tastePreferences" ->
                        state.setTastePreferences(new ArrayList<>());
                case "tasteRestrictions" ->
                        state.setTasteRestrictions(new ArrayList<>());
                case "excludedCuisines" ->
                        state.setExcludedCuisines(new ArrayList<>());
                case "excludedMerchantTypes" ->
                        state.setExcludedMerchantTypes(
                                new ArrayList<>()
                        );
                case "distanceKm" -> state.setDistanceKm(null);
                case "minRating" -> state.setMinRating(null);
                case "scenes" ->
                        state.setScenes(new ArrayList<>());
                case "environmentRequirements" ->
                        state.setEnvironmentRequirements(
                                new ArrayList<>()
                        );
                case "businessTime" ->
                        state.setBusinessTime(null);
                default -> {
                }
            }
        }
    }

    private record AiExtractionResult(
            String intent,
            ConstraintState extracted,
            List<String> clearedFields,
            String extractor,
            boolean degraded
    ) {
    }

    private ConstraintState extractByRules(String message) {
        ConstraintState state = new ConstraintState();

        extractPartySize(message, state);
        extractBudget(message, state);
        extractCuisineAndMerchantType(message, state);
        extractTaste(message, state);
        extractSceneAndEnvironment(message, state);
        extractDistance(message, state);
        extractMinRating(message, state);
        extractBusinessTime(message, state);
        calculatePerCapitaBudget(state);

        return state;
    }

    /**
     * 从消息中提取用餐人数。
     */
    private void extractPartySize(
            String message,
            ConstraintState state
    ) {
        if (message == null || message.isBlank()) {
            return;
        }

        Matcher matcher =
                PARTY_SIZE_PATTERN.matcher(message);

        if (!matcher.find()) {
            return;
        }

        Integer partySize =
                parseChineseOrArabicNumber(matcher.group(1));

        if (partySize != null && partySize > 0) {
            state.setPartySize(partySize);
        }
    }

    /**
     * 从消息中提取人均预算和总预算。
     */
    private void extractBudget(
            String message,
            ConstraintState state
    ) {
        if (message == null || message.isBlank()) {
            return;
        }

        Matcher perCapitaMatcher =
                PER_CAPITA_BUDGET_PATTERN.matcher(message);

        if (perCapitaMatcher.find()) {
            Integer value = parseChineseOrArabicNumber(
                    perCapitaMatcher.group(1)
            );

            if (value != null && value >= 0) {
                state.setPerCapitaBudget(
                        BigDecimal.valueOf(value)
                );
            }
        }

        Matcher totalMatcher =
                TOTAL_BUDGET_PATTERN.matcher(message);

        if (totalMatcher.find()) {
            Integer value = parseChineseOrArabicNumber(
                    totalMatcher.group(1)
            );

            if (value != null && value >= 0) {
                state.setTotalBudget(
                        BigDecimal.valueOf(value)
                );
            }
        }
    }

    /**
     * 从消息中提取菜系、商家类型及其排除条件。
     */
    private void extractCuisineAndMerchantType(
            String message,
            ConstraintState state
    ) {
        if (message == null || message.isBlank()) {
            return;
        }

        String compactMessage =
                message.replaceAll("\\s+", "");

        for (String cuisine : SUPPORTED_CUISINES) {
            if (!compactMessage.contains(cuisine)) {
                continue;
            }

            if (isNegated(compactMessage, cuisine)) {
                state.getExcludedCuisines().add(cuisine);
            } else {
                state.getCuisines().add(cuisine);
            }
        }

        for (String merchantType
                : SUPPORTED_MERCHANT_TYPES) {

            if (!compactMessage.contains(merchantType)) {
                continue;
            }

            if (isNegated(compactMessage, merchantType)) {
                state.getExcludedMerchantTypes()
                        .add(merchantType);
            } else {
                state.getMerchantTypes()
                        .add(merchantType);
            }
        }
    }

    /**
     * 从消息中提取口味偏好和口味限制。
     */
    private void extractTaste(
            String message,
            ConstraintState state
    ) {
        if (message == null || message.isBlank()) {
            return;
        }

        String compactMessage =
                message.replaceAll("\\s+", "");

        /*
         * 强限制：
         * 用户明确表示不能或不愿意吃辣。
         *
         * 不同表达统一保存成“不吃辣”，
         * 方便后续商家匹配。
         */
        if (compactMessage.contains("不吃辣")
                || compactMessage.contains("不要辣")
                || compactMessage.contains("不能吃辣")
                || compactMessage.contains("吃不了辣")
                || compactMessage.contains("忌辣")
                || compactMessage.contains("不辣")) {
            state.getTasteRestrictions().add("不吃辣");
        }

        /*
         * “不要太辣”通常表示希望辣度较低，
         * 并不等同于完全不能吃辣。
         */
        if (compactMessage.contains("少辣")
                || compactMessage.contains("不要太辣")) {
            state.getTastePreferences().add("少辣");
        }

        if (compactMessage.contains("微辣")) {
            state.getTastePreferences().add("微辣");
        }

        if (compactMessage.contains("清淡")) {
            state.getTastePreferences().add("清淡");
        }
    }

    /**
     * 从消息中提取用餐场景和环境要求。
     */
    private void extractSceneAndEnvironment(
            String message,
            ConstraintState state
    ) {
        if (message == null || message.isBlank()) {
            return;
        }

        String compactMessage =
                message.replaceAll("\\s+", "");

        /*
         * 环境要求
         */
        if (compactMessage.contains("安静")
                || compactMessage.contains("不要太吵")
                || compactMessage.contains("不吵")) {
            state.getEnvironmentRequirements()
                    .add("安静");
        }

        if (compactMessage.contains("适合拍照")
                || compactMessage.contains("方便拍照")
                || compactMessage.contains("拍照好看")
                || compactMessage.contains("出片")) {
            state.getEnvironmentRequirements()
                    .add("适合拍照");
        }

        if (compactMessage.contains("有包间")
                || compactMessage.contains("需要包间")
                || compactMessage.contains("要包间")) {
            state.getEnvironmentRequirements()
                    .add("有包间");
        }

        if (compactMessage.contains("宽敞")
                || compactMessage.contains("座位宽松")
                || compactMessage.contains("不要拥挤")) {
            state.getEnvironmentRequirements()
                    .add("宽敞");
        }

        /*
         * 用餐场景
         */
        if (compactMessage.contains("朋友聚会")
                || compactMessage.contains("朋友聚餐")
                || compactMessage.contains("同学聚会")) {
            state.getScenes().add("朋友聚会");
        }

        if (compactMessage.contains("约会")
                || compactMessage.contains("情侣吃饭")
                || compactMessage.contains("情侣用餐")) {
            state.getScenes().add("约会");
        }

        if (compactMessage.contains("家庭聚餐")
                || compactMessage.contains("家人聚餐")
                || compactMessage.contains("带家人吃饭")) {
            state.getScenes().add("家庭聚餐");
        }

        if (compactMessage.contains("生日聚会")
                || compactMessage.contains("过生日")
                || compactMessage.contains("生日宴")) {
            state.getScenes().add("生日聚会");
        }

        if (compactMessage.contains("商务宴请")
                || compactMessage.contains("请客户")
                || compactMessage.contains("商务聚餐")) {
            state.getScenes().add("商务宴请");
        }
    }

    /**
     * 从消息中提取最大距离要求，单位为公里。
     */
    private void extractDistance(
            String message,
            ConstraintState state
    ) {
        if (message == null || message.isBlank()) {
            return;
        }

        Matcher matcher =
                DISTANCE_PATTERN.matcher(message);

        if (!matcher.find()) {
            return;
        }

        BigDecimal distance =
                parseNumberAsBigDecimal(matcher.group(1));

        if (distance != null
                && distance.compareTo(BigDecimal.ZERO) > 0) {
            state.setDistanceKm(distance);
        }
    }

    /**
     * 从消息中提取最低评分要求。
     */
    private void extractMinRating(
            String message,
            ConstraintState state
    ) {
        if (message == null || message.isBlank()) {
            return;
        }

        Matcher prefixMatcher =
                MIN_RATING_PREFIX_PATTERN.matcher(message);

        BigDecimal rating = null;

        if (prefixMatcher.find()) {
            rating = parseNumberAsBigDecimal(
                    prefixMatcher.group(1)
            );
        } else {
            Matcher suffixMatcher =
                    MIN_RATING_SUFFIX_PATTERN.matcher(message);

            if (suffixMatcher.find()) {
                rating = parseNumberAsBigDecimal(
                        suffixMatcher.group(1)
                );
            }
        }

        if (rating == null) {
            return;
        }

        boolean validRating =
                rating.compareTo(BigDecimal.ZERO) >= 0
                        && rating.compareTo(
                                BigDecimal.valueOf(5)
                        ) <= 0;

        if (validRating) {
            state.setMinRating(rating);
        }
    }

    /**
     * 从消息中提取营业时间要求。
     */
    private void extractBusinessTime(
            String message,
            ConstraintState state
    ) {
        if (message == null || message.isBlank()) {
            return;
        }

        String compactMessage =
                message.replaceAll("\\s+", "");

        /*
         * 深夜、夜宵类表达最具体，优先判断。
         */
        if (compactMessage.contains("深夜营业")
                || compactMessage.contains("深夜还开")
                || compactMessage.contains("凌晨营业")
                || compactMessage.contains("凌晨还开")
                || compactMessage.contains("营业到很晚")
                || compactMessage.contains("夜宵")) {
            state.setBusinessTime("LATE_NIGHT");
            return;
        }

        /*
         * “今晚还开门”表示今晚的营业要求，
         * 不能因为包含“还开门”就误判成现在营业。
         */
        if (compactMessage.contains("今晚营业")
                || compactMessage.contains("今晚还开门")
                || compactMessage.contains("今晚还开")
                || compactMessage.contains("今晚能吃")
                || compactMessage.contains("晚上营业")
                || compactMessage.contains("晚上还开门")) {
            state.setBusinessTime("TONIGHT");
            return;
        }

        /*
         * 当前时刻必须正在营业。
         */
        if (compactMessage.contains("现在营业")
                || compactMessage.contains("现在开门")
                || compactMessage.contains("现在还开门")
                || compactMessage.contains("目前营业")
                || compactMessage.contains("目前还开")
                || compactMessage.contains("正在营业")) {
            state.setBusinessTime("NOW_OPEN");
        }
    }

    /**
     * 判断指定菜系或商家类型是否以排除形式出现。
     *
     * 例如：
     * 不要火锅
     * 不吃川菜
     * 不考虑烧烤
     */
    private boolean isNegated(
            String compactMessage,
            String target
    ) {
        for (String prefix : NEGATION_PREFIXES) {
            if (compactMessage.contains(prefix + target)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 当用户只提供总预算和人数时，自动计算人均预算。
     */
    private void calculatePerCapitaBudget(
            ConstraintState state
    ) {
        if (state.getPerCapitaBudget() != null) {
            return;
        }

        if (state.getTotalBudget() == null
                || state.getPartySize() == null
                || state.getPartySize() <= 0) {
            return;
        }

        BigDecimal partySize =
                BigDecimal.valueOf(state.getPartySize());

        BigDecimal perCapita =
                state.getTotalBudget().divide(
                        partySize,
                        2,
                        RoundingMode.HALF_UP
                );

        state.setPerCapitaBudget(perCapita);
    }

    /**
     * 将阿拉伯数字或简单中文数字转换为 Integer。
     *
     * 支持示例：
     * 4、四、两、十、十二、二十五、
     * 一百、两百、一百二十、二百五十。
     */
    private Integer parseChineseOrArabicNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw
                .trim()
                .replace('两', '二');

        if (normalized.matches("\\d+")) {
            try {
                return Integer.valueOf(normalized);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        int result = 0;
        int currentDigit = -1;

        for (int index = 0;
             index < normalized.length();
             index++) {

            char character = normalized.charAt(index);

            Integer digit = chineseDigit(character);

            if (digit != null) {
                currentDigit = digit;
                continue;
            }

            if (character == '百') {
                int multiplier =
                        currentDigit < 0 ? 1 : currentDigit;

                result += multiplier * 100;
                currentDigit = -1;
                continue;
            }

            if (character == '十') {
                int multiplier =
                        currentDigit < 0 ? 1 : currentDigit;

                result += multiplier * 10;
                currentDigit = -1;
                continue;
            }

            return null;
        }

        if (currentDigit >= 0) {
            result += currentDigit;
        }

        return result;
    }

    /**
     * 将阿拉伯整数、阿拉伯小数或简单中文整数
     * 转换为 BigDecimal。
     */
    private BigDecimal parseNumberAsBigDecimal(
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim();

        /*
         * 阿拉伯整数或小数：
         * 2、4、2.5、4.5
         */
        if (normalized.matches("\\d+(?:\\.\\d+)?")) {
            try {
                return new BigDecimal(normalized);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        /*
         * 中文整数：
         * 三、十二、两百
         */
        Integer integerValue =
                parseChineseOrArabicNumber(normalized);

        if (integerValue == null) {
            return null;
        }

        return BigDecimal.valueOf(integerValue);
    }

    /**
     * 将单个中文数字转换为整数。
     */
    private Integer chineseDigit(char character) {
        return switch (character) {
            case '零' -> 0;
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> null;
        };
    }

    /**
     * 检测用户本轮消息中互相矛盾的消费需求。
     *
     * 当前规则版先处理：
     * 1. 同时想吃火锅和排除火锅；
     * 2. 同时想吃辣和明确不能吃辣。
     */
    private List<ConstraintConflictVO> detectConflicts(
            String message
    ) {
        List<ConstraintConflictVO> conflicts =
                new ArrayList<>();

        if (message == null || message.isBlank()) {
            return conflicts;
        }

        String compactMessage =
                message.replaceAll("\\s+", "");

        /*
         * 火锅冲突
         */
        boolean excludesHotpot =
                isNegated(compactMessage, "火锅");

        boolean wantsHotpot =
                containsPositiveTarget(
                        compactMessage,
                        "火锅"
                );

        if (wantsHotpot && excludesHotpot) {
            conflicts.add(
                    new ConstraintConflictVO(
                            "merchantTypes",
                            "同时出现想吃火锅和不要火锅，需要确认"
                    )
            );
        }

        /*
         * 辣味冲突
         */
        List<String> spicyNegativeExpressions =
                List.of(
                        "不吃辣",
                        "不要辣",
                        "不能吃辣",
                        "吃不了辣",
                        "忌辣",
                        "完全不辣"
                );

        String positiveSpicyMessage =
                compactMessage;

        /*
         * 先移除否定表达，避免：
         * “不要辣”中的“要辣”
         * 被错误识别成正向需求。
         */
        for (String negativeExpression
                : spicyNegativeExpressions) {
            positiveSpicyMessage =
                    positiveSpicyMessage.replace(
                            negativeExpression,
                            ""
                    );
        }

        boolean excludesSpicy =
                containsAny(
                        compactMessage,
                        spicyNegativeExpressions
                );

        boolean wantsSpicy =
                containsAny(
                        positiveSpicyMessage,
                        List.of(
                                "想吃辣",
                                "要吃辣",
                                "想要辣",
                                "要辣",
                                "重辣",
                                "特辣",
                                "麻辣"
                        )
                );

        if (wantsSpicy && excludesSpicy) {
            conflicts.add(
                    new ConstraintConflictVO(
                            "tasteRestrictions",
                            "同时出现想吃辣和不吃辣，需要确认"
                    )
            );
        }

        return conflicts;
    }

    /**
     * 判断目标是否以正向需求形式出现。
     *
     * 会先移除“不要火锅”“不吃火锅”等否定片段，
     * 再判断消息中是否还存在“火锅”。
     */
    private boolean containsPositiveTarget(
            String compactMessage,
            String target
    ) {
        String positiveMessage = compactMessage;

        for (String prefix : NEGATION_PREFIXES) {
            positiveMessage =
                    positiveMessage.replace(
                            prefix + target,
                            ""
                    );
        }

        return positiveMessage.contains(target);
    }

    /**
     * 判断文本是否包含候选表达中的任意一个。
     */
    private boolean containsAny(
            String text,
            List<String> candidates
    ) {
        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 读取指定会话当前已经合并的消费条件。
     *
     * 如果该会话还没有状态记录，返回一个空的 ConstraintState。
     */
    private ConstraintState loadCurrentState(Long sessionId) {
        ChatSessionState sessionState =
                chatSessionStateMapper.selectOne(
                        new LambdaQueryWrapper<ChatSessionState>()
                                .eq(
                                        ChatSessionState::getSessionId,
                                        sessionId
                                )
                );

        /*
         * 第一次进行条件提取时，
         * chat_session_states 中还没有这一会话的记录。
         */
        if (sessionState == null) {
            return new ConstraintState();
        }

        String currentConstraints =
                sessionState.getCurrentConstraints();

        /*
         * 防止旧数据中出现 null 或空字符串。
         */
        if (currentConstraints == null
                || currentConstraints.isBlank()) {
            return new ConstraintState();
        }

        try {
            return objectMapper.readValue(
                    currentConstraints,
                    ConstraintState.class
            );
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CONSTRAINT_STATE_PARSE_FAILED",
                    "会话条件状态解析失败"
            );
        }
    }

    /**
     * 创建 ConstraintState 的独立副本。
     *
     * 不能直接复用旧对象，否则合并时会修改 oldState，
     * 导致后续无法准确检测字段变化。
     */
    private ConstraintState copyState(
            ConstraintState source
    ) {
        ConstraintState copy = new ConstraintState();

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

        copy.setScenes(
                copyList(source.getScenes())
        );
        copy.setEnvironmentRequirements(
                copyList(
                        source.getEnvironmentRequirements()
                )
        );

        copy.setBusinessTime(source.getBusinessTime());

        return copy;
    }

    /**
     * 创建列表副本，并把 null 转换为空列表。
     */
    private List<String> copyList(
            List<String> source
    ) {
        if (source == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(source);
    }

    /**
     * 将本轮提取到的标量条件合并到旧状态。
     *
     * 当前步骤只处理标量字段。
     * 列表字段将在下一步加入。
     */
    private ConstraintState mergeScalarFields(
            ConstraintState oldState,
            ConstraintState extracted
    ) {
        ConstraintState merged =
                copyState(oldState);

        if (extracted == null) {
            return merged;
        }

        /*
         * 标量字段规则：
         * 本轮提取值非 null 时覆盖旧值；
         * 本轮没有提取到时保留旧值。
         */
        if (extracted.getPartySize() != null) {
            merged.setPartySize(
                    extracted.getPartySize()
            );
        }

        if (extracted.getTotalBudget() != null) {
            merged.setTotalBudget(
                    extracted.getTotalBudget()
            );
        }

        if (extracted.getPerCapitaBudget() != null) {
            merged.setPerCapitaBudget(
                    extracted.getPerCapitaBudget()
            );
        }

        if (extracted.getDistanceKm() != null) {
            merged.setDistanceKm(
                    extracted.getDistanceKm()
            );
        }

        if (extracted.getMinRating() != null) {
            merged.setMinRating(
                    extracted.getMinRating()
            );
        }

        if (extracted.getBusinessTime() != null) {
            merged.setBusinessTime(
                    extracted.getBusinessTime()
            );
        }

        recalculateMergedPerCapitaBudget(
                merged,
                extracted
        );

        return merged;
    }

    /**
     * 当人数或总预算发生变化，且用户本轮没有明确给出
     * 人均预算时，根据合并后的完整状态重新计算人均预算。
     */
    private void recalculateMergedPerCapitaBudget(
            ConstraintState merged,
            ConstraintState extracted
    ) {
        /*
         * 用户本轮明确说了人均预算，
         * 应当尊重用户输入，不能重新覆盖。
         */
        if (extracted.getPerCapitaBudget() != null) {
            return;
        }

        boolean calculationBasisChanged =
                extracted.getTotalBudget() != null
                        || extracted.getPartySize() != null;

        if (!calculationBasisChanged) {
            return;
        }

        if (merged.getTotalBudget() == null
                || merged.getPartySize() == null
                || merged.getPartySize() <= 0) {
            return;
        }

        BigDecimal partySize =
                BigDecimal.valueOf(
                        merged.getPartySize()
                );

        BigDecimal perCapita =
                merged.getTotalBudget().divide(
                        partySize,
                        2,
                        RoundingMode.HALF_UP
                );

        merged.setPerCapitaBudget(perCapita);
    }

    /**
     * 将本轮提取结果与旧会话状态合并。
     */
    private ConstraintState merge(
            ConstraintState oldState,
            ConstraintState extracted,
            String message
    ) {
        ConstraintState merged =
                mergeScalarFields(oldState, extracted);

        mergeListFields(
                merged,
                extracted,
                message
        );

        return merged;
    }

    /**
     * 合并列表类型的消费条件。
     *
     * 默认追加去重；
     * 用户明确表示“改成”“换成”时，
     * 替换对应字段的旧列表。
     */
    private void mergeListFields(
            ConstraintState merged,
            ConstraintState extracted,
            String message
    ) {
        if (extracted == null) {
            return;
        }

        merged.setMerchantTypes(
                mergeOrReplaceList(
                        merged.getMerchantTypes(),
                        extracted.getMerchantTypes(),
                        shouldReplaceList(
                                message,
                                extracted.getMerchantTypes(),
                                List.of(
                                        "商家类型",
                                        "餐厅类型",
                                        "店的类型"
                                )
                        )
                )
        );

        merged.setCuisines(
                mergeOrReplaceList(
                        merged.getCuisines(),
                        extracted.getCuisines(),
                        shouldReplaceList(
                                message,
                                extracted.getCuisines(),
                                List.of(
                                        "菜系",
                                        "菜的类型"
                                )
                        )
                )
        );

        merged.setTastePreferences(
                mergeOrReplaceList(
                        merged.getTastePreferences(),
                        extracted.getTastePreferences(),
                        shouldReplaceList(
                                message,
                                extracted.getTastePreferences(),
                                List.of(
                                        "口味",
                                        "辣度",
                                        "口味偏好"
                                )
                        )
                )
        );

        merged.setTasteRestrictions(
                mergeOrReplaceList(
                        merged.getTasteRestrictions(),
                        extracted.getTasteRestrictions(),
                        shouldReplaceList(
                                message,
                                extracted.getTasteRestrictions(),
                                List.of(
                                        "口味限制",
                                        "饮食限制"
                                )
                        )
                )
        );

        merged.setExcludedCuisines(
                mergeOrReplaceList(
                        merged.getExcludedCuisines(),
                        extracted.getExcludedCuisines(),
                        shouldReplaceList(
                                message,
                                extracted.getExcludedCuisines(),
                                List.of(
                                        "排除菜系",
                                        "不要的菜系"
                                )
                        )
                )
        );

        merged.setExcludedMerchantTypes(
                mergeOrReplaceList(
                        merged.getExcludedMerchantTypes(),
                        extracted.getExcludedMerchantTypes(),
                        shouldReplaceList(
                                message,
                                extracted.getExcludedMerchantTypes(),
                                List.of(
                                        "排除类型",
                                        "不要的商家类型"
                                )
                        )
                )
        );

        merged.setScenes(
                mergeOrReplaceList(
                        merged.getScenes(),
                        extracted.getScenes(),
                        shouldReplaceList(
                                message,
                                extracted.getScenes(),
                                List.of(
                                        "场景",
                                        "用餐场景",
                                        "聚餐场景"
                                )
                        )
                )
        );

        merged.setEnvironmentRequirements(
                mergeOrReplaceList(
                        merged.getEnvironmentRequirements(),
                        extracted.getEnvironmentRequirements(),
                        shouldReplaceList(
                                message,
                                extracted.getEnvironmentRequirements(),
                                List.of(
                                        "环境",
                                        "环境要求"
                                )
                        )
                )
        );
    }

    /**
     * 根据 replaceOldValues 决定替换旧值还是追加去重。
     */
    private List<String> mergeOrReplaceList(
            List<String> oldValues,
            List<String> newValues,
            boolean replaceOldValues
    ) {
        if (newValues == null || newValues.isEmpty()) {
            return copyList(oldValues);
        }

        if (replaceOldValues) {
            return distinctList(newValues);
        }

        LinkedHashSet<String> mergedValues =
                new LinkedHashSet<>();

        if (oldValues != null) {
            mergedValues.addAll(oldValues);
        }

        mergedValues.addAll(newValues);

        return new ArrayList<>(mergedValues);
    }

    /**
     * 对列表去重，同时保留原有顺序。
     */
    private List<String> distinctList(
            List<String> values
    ) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }

        return new ArrayList<>(
                new LinkedHashSet<>(values)
        );
    }

    /**
     * 判断用户是否明确要求替换某个列表字段。
     *
     * 支持示例：
     * 改成粤菜
     * 菜系改成粤菜
     * 换成火锅
     * 环境要求改为安静
     */
    private boolean shouldReplaceList(
            String message,
            List<String> newValues,
            List<String> fieldHints
    ) {
        if (message == null
                || message.isBlank()
                || newValues == null
                || newValues.isEmpty()) {
            return false;
        }

        String compactMessage =
                message.replaceAll("\\s+", "");

        List<String> replaceWords =
                List.of(
                        "改成",
                        "改为",
                        "换成",
                        "换为"
                );

        /*
         * 识别“改成粤菜”“换成火锅”。
         */
        for (String replaceWord : replaceWords) {
            for (String value : newValues) {
                if (compactMessage.contains(
                        replaceWord + value
                )) {
                    return true;
                }
            }
        }

        /*
         * 识别“菜系改成粤菜”
         * “环境要求改为安静”。
         */
        for (String fieldHint : fieldHints) {
            for (String replaceWord : replaceWords) {
                if (compactMessage.contains(
                        fieldHint + replaceWord
                )) {
                    return true;
                }
            }

            if (compactMessage.contains(
                    "不要之前的" + fieldHint
            )) {
                return true;
            }
        }

        return false;
    }

    /**
     * 比较合并前后的状态，返回真正发生变化的字段名。
     */
    private List<String> detectChanges(
            ConstraintState oldState,
            ConstraintState merged
    ) {
        ConstraintState safeOld =
                oldState == null
                        ? new ConstraintState()
                        : oldState;

        ConstraintState safeMerged =
                merged == null
                        ? new ConstraintState()
                        : merged;

        List<String> changes = new ArrayList<>();

        addChangeIfDifferent(
                changes,
                "partySize",
                safeOld.getPartySize(),
                safeMerged.getPartySize()
        );

        addDecimalChangeIfDifferent(
                changes,
                "totalBudget",
                safeOld.getTotalBudget(),
                safeMerged.getTotalBudget()
        );

        addDecimalChangeIfDifferent(
                changes,
                "perCapitaBudget",
                safeOld.getPerCapitaBudget(),
                safeMerged.getPerCapitaBudget()
        );

        addListChangeIfDifferent(
                changes,
                "merchantTypes",
                safeOld.getMerchantTypes(),
                safeMerged.getMerchantTypes()
        );

        addListChangeIfDifferent(
                changes,
                "cuisines",
                safeOld.getCuisines(),
                safeMerged.getCuisines()
        );

        addListChangeIfDifferent(
                changes,
                "tastePreferences",
                safeOld.getTastePreferences(),
                safeMerged.getTastePreferences()
        );

        addListChangeIfDifferent(
                changes,
                "tasteRestrictions",
                safeOld.getTasteRestrictions(),
                safeMerged.getTasteRestrictions()
        );

        addListChangeIfDifferent(
                changes,
                "excludedCuisines",
                safeOld.getExcludedCuisines(),
                safeMerged.getExcludedCuisines()
        );

        addListChangeIfDifferent(
                changes,
                "excludedMerchantTypes",
                safeOld.getExcludedMerchantTypes(),
                safeMerged.getExcludedMerchantTypes()
        );

        addDecimalChangeIfDifferent(
                changes,
                "distanceKm",
                safeOld.getDistanceKm(),
                safeMerged.getDistanceKm()
        );

        addDecimalChangeIfDifferent(
                changes,
                "minRating",
                safeOld.getMinRating(),
                safeMerged.getMinRating()
        );

        addListChangeIfDifferent(
                changes,
                "scenes",
                safeOld.getScenes(),
                safeMerged.getScenes()
        );

        addListChangeIfDifferent(
                changes,
                "environmentRequirements",
                safeOld.getEnvironmentRequirements(),
                safeMerged.getEnvironmentRequirements()
        );

        addChangeIfDifferent(
                changes,
                "businessTime",
                safeOld.getBusinessTime(),
                safeMerged.getBusinessTime()
        );

        return changes;
    }

    /**
     * 比较整数、字符串等普通字段。
     */
    private void addChangeIfDifferent(
            List<String> changes,
            String fieldName,
            Object oldValue,
            Object newValue
    ) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(fieldName);
        }
    }

    /**
     * 比较 BigDecimal 字段。
     *
     * 使用 compareTo，避免 50 和 50.00
     * 因小数位数不同而被错误判断为变化。
     */
    private void addDecimalChangeIfDifferent(
            List<String> changes,
            String fieldName,
            BigDecimal oldValue,
            BigDecimal newValue
    ) {
        if (oldValue == null && newValue == null) {
            return;
        }

        if (oldValue == null || newValue == null) {
            changes.add(fieldName);
            return;
        }

        if (oldValue.compareTo(newValue) != 0) {
            changes.add(fieldName);
        }
    }

    /**
     * 比较列表字段。
     *
     * null 和空列表都按照“没有条件”处理。
     */
    private void addListChangeIfDifferent(
            List<String> changes,
            String fieldName,
            List<String> oldValues,
            List<String> newValues
    ) {
        List<String> safeOldValues =
                oldValues == null
                        ? List.of()
                        : oldValues;

        List<String> safeNewValues =
                newValues == null
                        ? List.of()
                        : newValues;

        if (!safeOldValues.equals(safeNewValues)) {
            changes.add(fieldName);
        }
    }

    /**
     * 将 Java 对象转换成 JSON 字符串。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CONSTRAINT_JSON_SERIALIZE_FAILED",
                    "消费条件序列化失败"
            );
        }
    }

    /**
     * 保存当前会话最新的合并条件。
     *
     * 第一次提取时新增记录；
     * 后续提取时更新原记录并增加版本号。
     *
     * 有冲突时进入 CONFIRMING 阶段，并保存待确认内容。
     */
    private void saveSessionState(
            Long sessionId,
            ConstraintState merged,
            List<ConstraintConflictVO> conflicts
    ) {
        ChatSessionState existingState =
                chatSessionStateMapper.selectOne(
                        new LambdaQueryWrapper<ChatSessionState>()
                                .eq(
                                        ChatSessionState::getSessionId,
                                        sessionId
                                )
                );

        OffsetDateTime now =
                OffsetDateTime.now();

        ConstraintState safeMerged =
                merged == null
                        ? new ConstraintState()
                        : merged;

        List<ConstraintConflictVO> safeConflicts =
                conflicts == null
                        ? List.of()
                        : conflicts;

        String mergedJson =
                toJson(safeMerged);

        String pendingConfirmationJson =
                toJson(safeConflicts);

        String conversationStage =
                safeConflicts.isEmpty()
                        ? "COLLECTING"
                        : "CONFIRMING";

        /*
         * 第一次处理该会话，还没有状态记录。
         */
        if (existingState == null) {
            ChatSessionState newState =
                    new ChatSessionState();

            newState.setSessionId(sessionId);
            newState.setCurrentConstraints(mergedJson);
            newState.setMissingFields("[]");
            newState.setRejectedFields("[]");
            newState.setPendingConfirmation(
                    pendingConfirmationJson
            );
            newState.setConversationStage(
                    conversationStage
            );
            newState.setVersion(1);
            newState.setUpdatedAt(now);

            int affectedRows =
                    chatSessionStateMapper.insert(newState);

            if (affectedRows != 1) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "SESSION_STATE_SAVE_FAILED",
                        "会话条件状态保存失败"
                );
            }

            return;
        }

        /*
         * 已存在状态记录，更新最新条件与待确认冲突。
         */
        existingState.setCurrentConstraints(mergedJson);
        existingState.setPendingConfirmation(
                pendingConfirmationJson
        );
        existingState.setConversationStage(
                conversationStage
        );

        int currentVersion =
                existingState.getVersion() == null
                        ? 0
                        : existingState.getVersion();

        existingState.setVersion(currentVersion + 1);
        existingState.setUpdatedAt(now);

        int affectedRows =
                chatSessionStateMapper.updateById(
                        existingState
                );

        if (affectedRows != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SESSION_STATE_UPDATE_FAILED",
                    "会话条件状态更新失败"
            );
        }
    }

    /**
     * 保存本轮条件提取历史。
     *
     * 每一条历史记录都关联本轮保存的用户消息。
     */
    private ConstraintExtraction saveExtraction(
            Long sessionId,
            Long messageId,
            ConstraintState extracted,
            ConstraintState merged,
            List<String> changes,
            List<ConstraintConflictVO> conflicts
    ) {
        if (messageId == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MESSAGE_ID_REQUIRED",
                    "保存条件提取历史时缺少messageId"
            );
        }

        ConstraintState safeExtracted =
                extracted == null
                        ? new ConstraintState()
                        : extracted;

        ConstraintState safeMerged =
                merged == null
                        ? new ConstraintState()
                        : merged;

        List<String> safeChanges =
                changes == null
                        ? List.of()
                        : changes;

        List<ConstraintConflictVO> safeConflicts =
                conflicts == null
                        ? List.of()
                        : conflicts;

        ConstraintExtraction extraction =
                new ConstraintExtraction();

        extraction.setSessionId(sessionId);
        extraction.setMessageId(messageId);

        extraction.setExtractedConstraints(
                toJson(safeExtracted)
        );

        extraction.setMergedConstraints(
                toJson(safeMerged)
        );

        extraction.setChangedFields(
                toJson(safeChanges)
        );

        extraction.setConflictFields(
                toJson(safeConflicts)
        );

        extraction.setModelName("RULE_BASED");
        extraction.setModelVersion("v1");
        extraction.setCreatedAt(OffsetDateTime.now());

        int affectedRows =
                constraintExtractionMapper.insert(
                        extraction
                );

        if (affectedRows != 1
                || extraction.getId() == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CONSTRAINT_EXTRACTION_SAVE_FAILED",
                    "条件提取历史保存失败"
            );
        }

        return extraction;
    }
}
