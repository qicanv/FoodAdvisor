package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.backend.exception.ApiException;
import com.foodadvisor.dto.constraint.ConstraintConflictVO;
import com.foodadvisor.dto.constraint.ConstraintExtractResponse;
import com.foodadvisor.dto.constraint.ConstraintState;
import com.foodadvisor.dto.dialogue.DialogueContinueResponse;
import com.foodadvisor.dto.dialogue.FollowUpQuestionVO;
import com.foodadvisor.dto.dialogue.RejectedFieldRecord;
import com.foodadvisor.entity.ChatSessionState;
import com.foodadvisor.mapper.ChatSessionStateMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 多轮对话与需求追问服务。
 *
 * 负责：
 * 1. 复用消费需求提取结果；
 * 2. 判断条件是否足够推荐；
 * 3. 生成缺失字段和追问问题；
 * 4. 处理用户拒绝回答和冲突确认；
 * 5. 更新当前会话的对话阶段。
 */
@Service
public class DialogueService {

    /**
     * 仍在收集用户需求。
     */
    private static final String STAGE_COLLECTING =
            "COLLECTING";

    /**
     * 正在等待用户确认冲突。
     */
    private static final String STAGE_CONFIRMING =
            "CONFIRMING";

    /**
     * 条件已经足够，可以进入商家搜索。
     */
    private static final String STAGE_SEARCHING =
            "SEARCHING";

    /**
     * 可追问的约束字段名称。
     */
    private static final String FIELD_PARTY_SIZE =
            "partySize";

    private static final String FIELD_PER_CAPITA_BUDGET =
            "perCapitaBudget";

    private static final String FIELD_CUISINES =
            "cuisines";

    private static final String FIELD_DISTANCE_KM =
            "distanceKm";

    private static final String FIELD_SCENES =
            "scenes";

    /**
     * 用户明确要求停止追问并直接推荐的表达。
     */
    private static final List<String>
            DIRECT_RECOMMEND_PHRASES = List.of(
                    "直接推荐",
                    "先推荐",
                    "随便推荐",
                    "不用问了",
                    "你看着推荐"
            );

    /**
     * 用户拒绝继续回答条件问题的表达。
     */
    private static final List<String>
            REFUSAL_PHRASES = List.of(
                    "不想回答",
                    "随便",
                    "都行",
                    "无所谓",
                    "你决定"
            );

    private final ConstraintExtractionService
            constraintExtractionService;

    private final ChatSessionStateMapper
            chatSessionStateMapper;

    private final ObjectMapper objectMapper;

    public DialogueService(
            ConstraintExtractionService
                    constraintExtractionService,
            ChatSessionStateMapper
                    chatSessionStateMapper,
            ObjectMapper objectMapper
    ) {
        this.constraintExtractionService =
                constraintExtractionService;

        this.chatSessionStateMapper =
                chatSessionStateMapper;

        this.objectMapper = objectMapper;
    }

    /**
     * 继续处理指定会话的一轮对话。
     *
     * 处理顺序：
     * 1. 读取上一轮状态；
     * 2. 调用需求提取与合并服务；
     * 3. 处理用户拒绝回答；
     * 4. 合并本轮冲突和上一轮待确认冲突；
     * 5. 优先处理条件冲突；
     * 6. 判断是否能够进入推荐；
     * 7. 生成最多两个追问问题；
     * 8. 保存本轮对话状态；
     * 9. 返回本轮对话结果。
     */
    @Transactional
    public DialogueContinueResponse continueDialogue(
            Long sessionId,
            Long userId,
            String message
    ) {
        return continueDialogue(
                sessionId,
                userId,
                message,
                null
        );
    }

    @Transactional
    public DialogueContinueResponse continueDialogue(
            Long sessionId,
            Long userId,
            String message,
            String requestId
    ) {
        /*
         * 必须在需求提取之前读取上一轮状态。
         *
         * 用户本轮说“不想回答”时，
         * 需要知道上一轮到底询问了哪些字段。
         *
         * 本轮只补充人数或预算时，
         * 还需要保留上一轮未解决的冲突。
         */
        ChatSessionState previousState =
                loadSessionState(sessionId);

        List<String> previousMissingFields =
                parseMissingFields(previousState);

        List<RejectedFieldRecord> rejectedRecords =
                parseRejectedFields(previousState);

        /*
         * 读取上一轮尚未解决的冲突。
         *
         * ConstraintExtractionService 只返回本轮新产生的冲突，
         * 因此必须在调用它之前保存上一轮冲突。
         */
        List<ConstraintConflictVO> previousPendingConflicts =
                parsePendingConfirmations(previousState);

        /*
         * “直接推荐”和“拒绝回答”需要分别识别。
         *
         * isRefusalResponse 内部已经保证：
         * “随便推荐”不会被误判成普通拒绝。
         */
        boolean directRecommendRequested =
                isDirectRecommendRequested(message);

        boolean refusalResponse =
                isRefusalResponse(message);

        /*
         * 复用故事 9 的需求提取与多轮合并能力。
         *
         * 参数顺序：
         * sessionId、userId、message。
         */
        ConstraintExtractResponse extractionResponse =
                constraintExtractionService.extractAndMerge(
                        sessionId,
                        userId,
                        message,
                        requestId
                );

        /*
         * extractAndMerge 执行后，
         * chat_session_states.version 已增加一次。
         *
         * 这里重新查询，取得真实的当前版本。
         */
        ChatSessionState currentState =
                loadSessionState(sessionId);

        int currentVersion =
                getStateVersion(currentState);

        /*
         * 正常情况下 currentState 一定存在。
         * 这里保留兜底逻辑，避免异常情况下版本为 0。
         */
        if (currentVersion == 0) {
            currentVersion =
                    getStateVersion(previousState) + 1;
        }

        /*
         * 用户拒绝回答时，
         * 把上一轮实际询问的字段记录为已拒绝。
         */
        if (refusalResponse) {
            rejectedRecords =
                    updateRejectedFieldRecords(
                            rejectedRecords,
                            previousMissingFields,
                            currentVersion
                    );
        }

        ConstraintState constraints =
                extractionResponse.getMerged();

        if (constraints == null) {
            constraints = new ConstraintState();
        }

        /*
         * 取得本轮新产生的冲突。
         */
        List<ConstraintConflictVO> currentConflicts;

        if (extractionResponse.getConflicts() == null) {
            currentConflicts = new ArrayList<>();
        } else {
            currentConflicts =
                    extractionResponse.getConflicts();
        }

        /*
         * 确定本轮最终需要保留的冲突。
         *
         * 可能出现三种情况：
         * 1. 本轮产生了新冲突：使用本轮冲突；
         * 2. 本轮明确解决上一轮冲突：清空冲突；
         * 3. 本轮只补充人数等无关信息：保留上一轮冲突。
         */
        List<ConstraintConflictVO> conflicts =
                determineEffectiveConflicts(
                        previousPendingConflicts,
                        currentConflicts,
                        message
                );

        String stage;
        boolean readyForRecommendation;

        List<String> responseMissingFields =
                new ArrayList<>();

        List<FollowUpQuestionVO> questions;

        /*
         * 冲突的优先级最高。
         *
         * 只要仍然存在冲突：
         * 1. 不能直接推荐；
         * 2. 阶段进入 CONFIRMING；
         * 3. 只返回冲突确认问题；
         * 4. 不返回普通缺失字段问题。
         */
        if (hasConflicts(conflicts)) {
            stage = STAGE_CONFIRMING;
            readyForRecommendation = false;

            questions =
                    generateConflictQuestions(conflicts);
        } else {
            /*
             * 没有冲突时，
             * 再判断是否达到最低推荐条件。
             */
            readyForRecommendation =
                    isReadyForRecommendation(
                            constraints,
                            directRecommendRequested
                    );

            if (readyForRecommendation) {
                stage = STAGE_SEARCHING;
            } else {
                stage = STAGE_COLLECTING;
            }

            /*
             * 先计算客观缺少的字段。
             */
            List<String> allMissingFields =
                    calculateMissingFields(
                            constraints,
                            readyForRecommendation,
                            directRecommendRequested
                    );

            /*
             * 再过滤仍处于两轮屏蔽期的字段。
             */
            List<String> askableMissingFields =
                    filterRejectedFields(
                            allMissingFields,
                            rejectedRecords,
                            currentVersion
                    );

            /*
             * 每轮最多生成两个问题。
             */
            questions =
                    generateQuestions(
                            askableMissingFields
                    );

            /*
             * missingFields 只返回本轮真正询问的字段。
             *
             * 这样用户下一轮说“不想回答”时，
             * 可以准确知道他拒绝的是哪些问题。
             */
            for (FollowUpQuestionVO question
                    : questions) {
                if (question.getField() != null
                        && !question.getField().isBlank()) {
                    responseMissingFields.add(
                            question.getField()
                    );
                }
            }
        }

        /*
         * 将本轮对话状态补充写回数据库。
         *
         * 注意：
         * currentConstraints 和 version 已经由
         * ConstraintExtractionService 更新，
         * 这里不能再次增加 version。
         */
        saveDialogueState(
                currentState,
                responseMissingFields,
                rejectedRecords,
                conflicts,
                stage
        );

        DialogueContinueResponse response =
                new DialogueContinueResponse();

        response.setSessionId(sessionId);
        response.setUserMessageId(
                extractionResponse.getMessageId()
        );
        response.setStage(stage);
        response.setConstraints(constraints);
        response.setMissingFields(
                responseMissingFields
        );
        response.setQuestions(questions);
        response.setReadyForRecommendation(
                readyForRecommendation
        );
        response.setDirectRecommendRequested(
                directRecommendRequested
        );
        response.setConflicts(conflicts);

        return response;
    }

    /**
     * 将多轮对话补充状态写回数据库。
     *
     * 此方法只更新：
     * 1. missingFields；
     * 2. rejectedFields；
     * 3. pendingConfirmation；
     * 4. conversationStage；
     * 5. updatedAt。
     *
     * 不修改 version，避免一轮消息增加两次版本号。
     */
    private void saveDialogueState(
            ChatSessionState sessionState,
            List<String> missingFields,
            List<RejectedFieldRecord> rejectedRecords,
            List<ConstraintConflictVO> conflicts,
            String stage
    ) {
        if (sessionState == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SESSION_STATE_NOT_FOUND",
                    "需求提取完成后未找到会话状态"
            );
        }

        List<String> safeMissingFields =
                missingFields == null
                        ? new ArrayList<>()
                        : missingFields;

        List<RejectedFieldRecord> safeRejectedRecords =
                rejectedRecords == null
                        ? new ArrayList<>()
                        : rejectedRecords;

        List<ConstraintConflictVO> safeConflicts =
                conflicts == null
                        ? new ArrayList<>()
                        : conflicts;

        sessionState.setMissingFields(
                serializeToJson(
                        safeMissingFields,
                        "MISSING_FIELDS_SERIALIZE_FAILED",
                        "会话缺失字段序列化失败"
                )
        );

        sessionState.setRejectedFields(
                serializeToJson(
                        safeRejectedRecords,
                        "REJECTED_FIELDS_SERIALIZE_FAILED",
                        "用户拒绝字段记录序列化失败"
                )
        );

        sessionState.setPendingConfirmation(
                serializeToJson(
                        safeConflicts,
                        "PENDING_CONFIRMATION_SERIALIZE_FAILED",
                        "待确认冲突信息序列化失败"
                )
        );

        sessionState.setConversationStage(stage);
        sessionState.setUpdatedAt(OffsetDateTime.now());

        int updatedRows =
                chatSessionStateMapper.updateById(
                        sessionState
                );

        if (updatedRows != 1) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SESSION_STATE_UPDATE_FAILED",
                    "会话状态更新失败"
            );
        }
    }

    /**
     * 将对象序列化为 JSON 字符串。
     */
    private String serializeToJson(
            Object value,
            String errorCode,
            String errorMessage
    ) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    errorCode,
                    errorMessage
            );
        }
    }

    /**
     * 判断用户是否明确要求直接推荐。
     */
    private boolean isDirectRecommendRequested(
            String message
    ) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String compactMessage =
                message.replaceAll("\\s+", "");

        for (String phrase
                : DIRECT_RECOMMEND_PHRASES) {
            if (compactMessage.contains(phrase)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断用户是否拒绝回答上一轮的问题。
     */
    private boolean isRefusalResponse(
            String message
    ) {
        if (message == null || message.isBlank()) {
            return false;
        }

        /*
         * “随便推荐”属于直接推荐，
         * 不能同时被识别成拒绝回答。
         */
        if (isDirectRecommendRequested(message)) {
            return false;
        }

        String compactMessage =
                message.replaceAll("\\s+", "");

        for (String phrase : REFUSAL_PHRASES) {
            if (compactMessage.contains(phrase)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 判断当前条件是否已经足够进入推荐。
     *
     * 满足以下任意一种情况即可：
     * 1. 用户明确要求直接推荐；
     * 2. 满足普通推荐最低条件；
     * 3. 满足场景型推荐最低条件。
     */
    private boolean isReadyForRecommendation(
            ConstraintState constraints,
            boolean directRecommendRequested
    ) {
        /*
         * 用户明确要求直接推荐时，
         * 即使当前条件不完整，也停止追问。
         */
        if (directRecommendRequested) {
            return true;
        }

        if (constraints == null) {
            return false;
        }

        boolean hasPartySize =
                constraints.getPartySize() != null;

        boolean hasBudget =
                constraints.getPerCapitaBudget() != null
                        || constraints.getTotalBudget() != null;

        boolean hasPreference =
                hasValues(constraints.getCuisines())
                        || hasValues(
                                constraints.getMerchantTypes()
                        )
                        || hasValues(constraints.getScenes())
                        || hasValues(
                                constraints
                                        .getEnvironmentRequirements()
                        );

        /*
         * 普通推荐最低条件：
         * 人数 + 预算 + 至少一个偏好。
         */
        boolean normalRecommendationReady =
                hasPartySize
                        && hasBudget
                        && hasPreference;

        /*
         * 场景型推荐最低条件：
         * 有用餐场景，并且有预算或人数。
         */
        boolean sceneRecommendationReady =
                hasValues(constraints.getScenes())
                        && (hasBudget || hasPartySize);

        return normalRecommendationReady
                || sceneRecommendationReady;
    }

    /**
     * 判断列表中是否至少存在一个有效值。
     */
    private boolean hasValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return false;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return true;
            }
        }

        return false;
    }

    /**
     * 计算当前仍然缺少的关键条件。
     *
     * 返回顺序就是后续追问优先级：
     * 1. partySize
     * 2. perCapitaBudget
     * 3. cuisines
     * 4. distanceKm
     * 5. scenes
     */
    private List<String> calculateMissingFields(
            ConstraintState constraints,
            boolean readyForRecommendation,
            boolean directRecommendRequested
    ) {
        List<String> missingFields =
                new ArrayList<>();

        /*
         * 已经可以推荐，或者用户明确要求直接推荐时，
         * 不再把任何字段标记为需要继续追问。
         */
        if (readyForRecommendation
                || directRecommendRequested) {
            return missingFields;
        }

        ConstraintState safeConstraints =
                constraints == null
                        ? new ConstraintState()
                        : constraints;

        /*
         * 1. 缺少用餐人数。
         */
        if (safeConstraints.getPartySize() == null) {
            missingFields.add(FIELD_PARTY_SIZE);
        }

        /*
         * 2. 人均预算和总预算都没有时，
         * 才认为预算信息缺失。
         */
        boolean hasBudget =
                safeConstraints.getPerCapitaBudget() != null
                        || safeConstraints.getTotalBudget() != null;

        if (!hasBudget) {
            missingFields.add(
                    FIELD_PER_CAPITA_BUDGET
            );
        }

        /*
         * 3. 没有任何偏好条件时，
         * 使用 cuisines 作为偏好类追问入口。
         *
         * 用户已有商家类型、场景或环境要求时，
         * 不需要强制继续追问菜系。
         */
        boolean hasPreference =
                hasValues(safeConstraints.getCuisines())
                        || hasValues(
                                safeConstraints
                                        .getMerchantTypes()
                        )
                        || hasValues(
                                safeConstraints.getScenes()
                        )
                        || hasValues(
                                safeConstraints
                                        .getEnvironmentRequirements()
                        );

        if (!hasPreference) {
            missingFields.add(FIELD_CUISINES);
        }

        /*
         * 4. 距离是补充条件。
         */
        if (safeConstraints.getDistanceKm() == null) {
            missingFields.add(FIELD_DISTANCE_KM);
        }

        /*
         * 5. 场景也是补充条件。
         */
        if (!hasValues(safeConstraints.getScenes())) {
            missingFields.add(FIELD_SCENES);
        }

        return missingFields;
    }

    /**
     * 根据缺失字段生成追问问题。
     *
     * missingFields 本身已经按照优先级排列，
     * 因此这里只需要依次取前两个可识别字段。
     */
    private List<FollowUpQuestionVO> generateQuestions(
            List<String> missingFields
    ) {
        List<FollowUpQuestionVO> questions =
                new ArrayList<>();

        if (missingFields == null
                || missingFields.isEmpty()) {
            return questions;
        }

        for (String field : missingFields) {
            FollowUpQuestionVO question =
                    createQuestion(field);

            /*
             * 遇到未知字段时不加入问题列表。
             */
            if (question != null) {
                questions.add(question);
            }

            /*
             * 验收要求：每轮最多追问两个问题。
             */
            if (questions.size() >= 2) {
                break;
            }
        }

        return questions;
    }

    /**
     * 根据约束字段创建对应的追问问题。
     */
    private FollowUpQuestionVO createQuestion(
            String field
    ) {
        if (field == null || field.isBlank()) {
            return null;
        }

        return switch (field) {
            case FIELD_PARTY_SIZE ->
                    new FollowUpQuestionVO(
                            FIELD_PARTY_SIZE,
                            "几个人用餐？"
                    );

            case FIELD_PER_CAPITA_BUDGET ->
                    new FollowUpQuestionVO(
                            FIELD_PER_CAPITA_BUDGET,
                            "希望人均预算大概是多少？"
                    );

            case FIELD_CUISINES ->
                    new FollowUpQuestionVO(
                            FIELD_CUISINES,
                            "有偏好的菜系或不想吃的类型吗？"
                    );

            case FIELD_DISTANCE_KM ->
                    new FollowUpQuestionVO(
                            FIELD_DISTANCE_KM,
                            "希望距离控制在几公里内？"
                    );

            case FIELD_SCENES ->
                    new FollowUpQuestionVO(
                            FIELD_SCENES,
                            "这次用餐是什么场景，比如朋友聚会、约会或家庭聚餐？"
                    );

            default -> null;
        };
    }

    /**
     * 查询指定会话当前的对话状态。
     *
     * 新会话第一次请求时可能还没有状态记录，
     * 此时返回 null。
     */
    private ChatSessionState loadSessionState(
            Long sessionId
    ) {
        if (sessionId == null) {
            return null;
        }

        return chatSessionStateMapper.selectOne(
                new LambdaQueryWrapper<ChatSessionState>()
                        .eq(
                                ChatSessionState::getSessionId,
                                sessionId
                        )
        );
    }

    /**
     * 读取上一轮保存的 missingFields。
     */
    private List<String> parseMissingFields(
            ChatSessionState sessionState
    ) {
        if (sessionState == null
                || sessionState.getMissingFields() == null
                || sessionState.getMissingFields().isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    sessionState.getMissingFields(),
                    new TypeReference<List<String>>() {
                    }
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "MISSING_FIELDS_PARSE_FAILED",
                    "会话缺失字段解析失败"
            );
        }
    }

    /**
     * 读取用户之前拒绝回答的字段记录。
     */
    private List<RejectedFieldRecord> parseRejectedFields(
            ChatSessionState sessionState
    ) {
        if (sessionState == null
                || sessionState.getRejectedFields() == null
                || sessionState.getRejectedFields().isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(
                    sessionState.getRejectedFields(),
                    new TypeReference<
                            List<RejectedFieldRecord>
                            >() {
                    }
            );
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "REJECTED_FIELDS_PARSE_FAILED",
                    "用户拒绝字段记录解析失败"
            );
        }
    }

    /**
     * 读取上一轮尚未解决的冲突信息。
     */
    private List<ConstraintConflictVO>
            parsePendingConfirmations(
            ChatSessionState sessionState
    ) {
        if (sessionState == null
                || sessionState.getPendingConfirmation() == null
                || sessionState
                        .getPendingConfirmation()
                        .isBlank()) {
            return new ArrayList<>();
        }

        try {
            List<ConstraintConflictVO> conflicts =
                    objectMapper.readValue(
                            sessionState
                                    .getPendingConfirmation(),
                            new TypeReference<
                                    List<ConstraintConflictVO>
                                    >() {
                            }
                    );

            return conflicts == null
                    ? new ArrayList<>()
                    : conflicts;
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PENDING_CONFIRMATION_PARSE_FAILED",
                    "待确认冲突信息解析失败"
            );
        }
    }

    /**
     * 根据上一轮 missingFields，
     * 找出上一轮真正向用户提出的字段。
     *
     * 因为每轮最多生成两个问题，
     * 所以这里最多返回两个字段。
     */
    private List<String> getPreviouslyAskedFields(
            List<String> previousMissingFields
    ) {
        List<String> askedFields =
                new ArrayList<>();

        List<FollowUpQuestionVO> previousQuestions =
                generateQuestions(previousMissingFields);

        for (FollowUpQuestionVO question
                : previousQuestions) {
            if (question.getField() != null
                    && !question.getField().isBlank()) {
                askedFields.add(question.getField());
            }
        }

        return askedFields;
    }

    /**
     * 将上一轮实际追问的字段记录为用户拒绝回答。
     *
     * 同一个字段如果以前已经拒绝过，
     * 则更新为本次最新拒绝版本，不重复添加。
     */
    private List<RejectedFieldRecord>
            updateRejectedFieldRecords(
            List<RejectedFieldRecord> existingRecords,
            List<String> previousMissingFields,
            int rejectedVersion
    ) {
        List<RejectedFieldRecord> updatedRecords =
                new ArrayList<>();

        /*
         * 复制旧记录，避免直接修改原列表。
         */
        if (existingRecords != null) {
            for (RejectedFieldRecord record
                    : existingRecords) {
                if (record == null
                        || record.getField() == null
                        || record.getField().isBlank()) {
                    continue;
                }

                updatedRecords.add(
                        new RejectedFieldRecord(
                                record.getField(),
                                record.getVersion()
                        )
                );
            }
        }

        List<String> askedFields =
                getPreviouslyAskedFields(
                        previousMissingFields
                );

        for (String askedField : askedFields) {
            boolean existingRecordUpdated = false;

            for (RejectedFieldRecord record
                    : updatedRecords) {
                if (askedField.equals(
                        record.getField()
                )) {
                    record.setVersion(rejectedVersion);
                    existingRecordUpdated = true;
                    break;
                }
            }

            if (!existingRecordUpdated) {
                updatedRecords.add(
                        new RejectedFieldRecord(
                                askedField,
                                rejectedVersion
                        )
                );
            }
        }

        return updatedRecords;
    }

    /**
     * 过滤仍处于拒绝屏蔽期内的字段。
     *
     * 用户拒绝某字段后的当前轮以及后续两轮，
     * 都不再重复追问该字段。
     */
    private List<String> filterRejectedFields(
            List<String> missingFields,
            List<RejectedFieldRecord> rejectedRecords,
            int currentVersion
    ) {
        List<String> filteredFields =
                new ArrayList<>();

        if (missingFields == null
                || missingFields.isEmpty()) {
            return filteredFields;
        }

        for (String field : missingFields) {
            if (field == null || field.isBlank()) {
                continue;
            }

            boolean temporarilyRejected =
                    isFieldTemporarilyRejected(
                            field,
                            rejectedRecords,
                            currentVersion
                    );

            if (!temporarilyRejected) {
                filteredFields.add(field);
            }
        }

        return filteredFields;
    }

    /**
     * 判断指定字段是否仍处于两轮拒绝屏蔽期。
     */
    private boolean isFieldTemporarilyRejected(
            String field,
            List<RejectedFieldRecord> rejectedRecords,
            int currentVersion
    ) {
        if (field == null
                || field.isBlank()
                || rejectedRecords == null
                || rejectedRecords.isEmpty()) {
            return false;
        }

        for (RejectedFieldRecord record
                : rejectedRecords) {
            if (record == null
                    || record.getField() == null
                    || record.getVersion() == null) {
                continue;
            }

            if (!field.equals(record.getField())) {
                continue;
            }

            int roundsSinceRejection =
                    currentVersion
                            - record.getVersion();

            /*
             * 防止异常数据中的未来版本号。
             */
            if (roundsSinceRejection < 0) {
                return true;
            }

            return roundsSinceRejection <= 2;
        }

        return false;
    }

    /**
     * 确定本轮最终仍需确认的冲突。
     */
    private List<ConstraintConflictVO>
            determineEffectiveConflicts(
            List<ConstraintConflictVO> previousConflicts,
            List<ConstraintConflictVO> currentConflicts,
            String message
    ) {
        /*
         * 本轮重新产生冲突时，
         * 直接使用本轮冲突。
         */
        if (hasConflicts(currentConflicts)) {
            return new ArrayList<>(currentConflicts);
        }

        /*
         * 上一轮也没有待确认冲突。
         */
        if (!hasConflicts(previousConflicts)) {
            return new ArrayList<>();
        }

        /*
         * 用户已经明确解决上一轮所有冲突。
         */
        if (arePreviousConflictsResolved(
                previousConflicts,
                message
        )) {
            return new ArrayList<>();
        }

        /*
         * 用户只是补充了人数、预算等无关信息，
         * 继续保留上一轮冲突。
         */
        return new ArrayList<>(previousConflicts);
    }

    /**
     * 判断上一轮所有冲突是否都已被明确解决。
     */
    private boolean arePreviousConflictsResolved(
            List<ConstraintConflictVO> previousConflicts,
            String message
    ) {
        if (!hasConflicts(previousConflicts)
                || message == null
                || message.isBlank()) {
            return false;
        }

        for (ConstraintConflictVO conflict
                : previousConflicts) {
            if (!isSingleConflictResolved(
                    conflict,
                    message
            )) {
                return false;
            }
        }

        return true;
    }

    /**
     * 判断单个冲突是否被本轮消息明确解决。
     *
     * 当前需求提取服务主要处理：
     * 1. 火锅类型冲突；
     * 2. 辣味偏好冲突。
     */
    private boolean isSingleConflictResolved(
            ConstraintConflictVO conflict,
            String message
    ) {
        if (conflict == null
                || message == null
                || message.isBlank()) {
            return false;
        }

        String compactMessage =
                message.replaceAll("\\s+", "");

        /*
         * 含有犹豫表达时，不视为解决冲突。
         */
        List<String> uncertainPhrases = List.of(
                "还没决定",
                "没想好",
                "不知道",
                "不确定",
                "再想想",
                "要不要"
        );

        for (String phrase : uncertainPhrases) {
            if (compactMessage.contains(phrase)) {
                return false;
            }
        }

        String conflictMessage =
                conflict.getMessage() == null
                        ? ""
                        : conflict.getMessage();

        String conflictField =
                conflict.getField() == null
                        ? ""
                        : conflict.getField();

        /*
         * 火锅类型冲突：
         * 用户必须再次提到“火锅”，并给出明确选择。
         */
        if (conflictMessage.contains("火锅")
                || "merchantTypes".equals(
                        conflictField
                )) {
            if (!compactMessage.contains("火锅")) {
                return false;
            }

            return compactMessage.contains("最终")
                    || compactMessage.contains("确定")
                    || compactMessage.contains("就吃火锅")
                    || compactMessage.contains("想吃火锅")
                    || compactMessage.contains("要吃火锅")
                    || compactMessage.contains("不要火锅")
                    || compactMessage.contains("不吃火锅")
                    || compactMessage.contains("排除火锅");
        }

        /*
         * 辣味冲突：
         * 用户必须再次提到“辣”，并给出明确选择。
         */
        if (conflictMessage.contains("辣")
                || "tastePreferences".equals(
                        conflictField
                )
                || "tasteRestrictions".equals(
                        conflictField
                )) {
            if (!compactMessage.contains("辣")) {
                return false;
            }

            return compactMessage.contains("最终")
                    || compactMessage.contains("确定")
                    || compactMessage.contains("要辣")
                    || compactMessage.contains("吃辣")
                    || compactMessage.contains("不要辣")
                    || compactMessage.contains("不吃辣")
                    || compactMessage.contains("不能吃辣")
                    || compactMessage.contains("不辣");
        }

        /*
         * 未识别的冲突类型默认继续保留，
         * 避免系统在不确定时误清除冲突。
         */
        return false;
    }

    /**
     * 判断当前提取结果中是否存在条件冲突。
     */
    private boolean hasConflicts(
            List<ConstraintConflictVO> conflicts
    ) {
        return conflicts != null
                && !conflicts.isEmpty();
    }

    /**
     * 根据冲突信息生成确认问题。
     *
     * 存在冲突时，只返回冲突确认问题，
     * 不再返回普通的缺失字段追问。
     */
    private List<FollowUpQuestionVO>
            generateConflictQuestions(
            List<ConstraintConflictVO> conflicts
    ) {
        List<FollowUpQuestionVO> questions =
                new ArrayList<>();

        if (!hasConflicts(conflicts)) {
            return questions;
        }

        for (ConstraintConflictVO conflict
                : conflicts) {
            if (conflict == null) {
                continue;
            }

            String field = conflict.getField();

            if (field == null || field.isBlank()) {
                field = "conflict";
            }

            String question =
                    buildConflictQuestion(conflict);

            questions.add(
                    new FollowUpQuestionVO(
                            field,
                            question
                    )
            );

            /*
             * 每轮最多返回两个冲突确认问题。
             */
            if (questions.size() >= 2) {
                break;
            }
        }

        return questions;
    }

    /**
     * 创建单个冲突确认问题。
     */
    private String buildConflictQuestion(
            ConstraintConflictVO conflict
    ) {
        String message = conflict.getMessage();

        /*
         * 优先使用需求提取服务已经生成的冲突说明。
         */
        if (message != null && !message.isBlank()) {
            return message;
        }

        String field = conflict.getField();

        if ("merchantTypes".equals(field)) {
            return "检测到餐厅类型要求存在冲突，请确认最终想吃还是不想吃该类型？";
        }

        if ("cuisines".equals(field)) {
            return "检测到菜系要求存在冲突，请确认最终偏好的菜系？";
        }

        if ("tastePreferences".equals(field)
                || "tasteRestrictions".equals(field)) {
            return "检测到口味要求存在冲突，请确认最终的口味偏好？";
        }

        return "检测到用餐条件存在冲突，请确认最终需求？";
    }

    /**
     * 安全取得当前会话状态版本号。
     *
     * 尚未创建状态记录的新会话按 version = 0 处理。
     */
    private int getStateVersion(
            ChatSessionState sessionState
    ) {
        if (sessionState == null
                || sessionState.getVersion() == null) {
            return 0;
        }

        return sessionState.getVersion();
    }
}
