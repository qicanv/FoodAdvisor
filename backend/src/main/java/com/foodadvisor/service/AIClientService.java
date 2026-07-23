package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodadvisor.dto.ai.DialogueExtractAiRequest;
import com.foodadvisor.dto.ai.DialogueExtractAiResponse;
import com.foodadvisor.dto.ai.RuntimeModelConfig;
import com.foodadvisor.dto.prompt.ResolvedPrompt;
import com.foodadvisor.entity.AiCallLog;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.entity.AiRequestTraceStage;
import com.foodadvisor.entity.AiTraceRetrievalSource;
import com.foodadvisor.enums.PromptScene;
import com.foodadvisor.trace.AiTraceContext;
import com.foodadvisor.util.SensitiveLogSanitizer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AIClientService {

    private static final Logger log =
            LoggerFactory.getLogger(AIClientService.class);

    private static final int MAX_ERROR_LENGTH = 300;
    private static final AtomicInteger STAGE_SEQUENCE = new AtomicInteger();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AiCallLogService aiCallLogService;
    private final AuditLogService auditLogService;
    private final SensitiveLogSanitizer sanitizer;
    private final AiRequestTraceService traceService;
    private final PromptManagementService promptManagementService;
    private final RuntimeModelConfigResolver runtimeModelConfigResolver;

    @Value("${ai-service.base-url}")
    private String aiServiceBaseUrl;

    @Value("${ai-service.internal-token:}")
    private String internalToken;

    @Value("${ai-service.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${ai-service.read-timeout:60000}")
    private int readTimeout;

    /**
     * 保留给现有单元测试和不需要追踪能力的调用方。
     */
    public AIClientService(
            ObjectMapper objectMapper,
            AiCallLogService aiCallLogService,
            AuditLogService auditLogService,
            SensitiveLogSanitizer sanitizer
    ) {
        this(
                objectMapper,
                aiCallLogService,
                auditLogService,
                sanitizer,
                null,
                null,
                null
        );
    }

    /**
     * 保留原有五参数构造方法，避免已有测试或代码失效。
     */
    public AIClientService(
            ObjectMapper objectMapper,
            AiCallLogService aiCallLogService,
            AuditLogService auditLogService,
            SensitiveLogSanitizer sanitizer,
            AiRequestTraceService traceService
    ) {
        this(
                objectMapper,
                aiCallLogService,
                auditLogService,
                sanitizer,
                traceService,
                null,
                null
        );
    }

    /**
     * 保留原有六参数构造方法，兼容现有单元测试。
     */
    public AIClientService(
            ObjectMapper objectMapper,
            AiCallLogService aiCallLogService,
            AuditLogService auditLogService,
            SensitiveLogSanitizer sanitizer,
            AiRequestTraceService traceService,
            PromptManagementService promptManagementService
    ) {
        this(
                objectMapper,
                aiCallLogService,
                auditLogService,
                sanitizer,
                traceService,
                promptManagementService,
                null
        );
    }

    /**
     * Spring Boot 实际运行时使用的完整构造方法。
     */
    @Autowired
    public AIClientService(
            ObjectMapper objectMapper,
            AiCallLogService aiCallLogService,
            AuditLogService auditLogService,
            SensitiveLogSanitizer sanitizer,
            AiRequestTraceService traceService,
            PromptManagementService promptManagementService,
            RuntimeModelConfigResolver runtimeModelConfigResolver
    ) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.aiCallLogService = aiCallLogService;
        this.auditLogService = auditLogService;
        this.sanitizer = sanitizer;
        this.traceService = traceService;
        this.promptManagementService = promptManagementService;
        this.runtimeModelConfigResolver = runtimeModelConfigResolver;
    }

    @PostConstruct
    void configureTimeouts() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        restTemplate.setRequestFactory(requestFactory);
    }

    /**
     * 调用评论分析接口（V0.3 — 支持 reviewVersion）
     */
    public JsonNode analyzeReview(
            Long reviewId,
            Long merchantId,
            String content,
            Integer reviewVersion
    ) {
        String url = aiServiceBaseUrl + "/internal/reviews/analyze";

        Map<String, Object> request = Map.of(
                "reviewId", reviewId,
                "merchantId", merchantId,
                "reviewVersion",
                reviewVersion != null ? reviewVersion : 1,
                "content", content
        );

        return post(url, request, "REVIEW_ANALYSIS");
    }

    /**
     * 调用评论分析接口（兼容旧调用）
     */
    public JsonNode analyzeReview(
            Long reviewId,
            Long merchantId,
            String content
    ) {
        return analyzeReview(reviewId, merchantId, content, 1);
    }

    /**
     * 批量分析评论
     */
    public JsonNode batchAnalyzeReviews(
            List<Map<String, Object>> reviews,
            String analysisMode
    ) {
        String url =
                aiServiceBaseUrl + "/internal/reviews/batch-analyze";

        Map<String, Object> request;
        if (analysisMode != null && !analysisMode.isEmpty()) {
            request = Map.of(
                "reviews", reviews,
                "analysisMode", analysisMode
            );
        } else {
            request = Map.of("reviews", reviews);
        }

        return post(
                url,
                request,
                "BATCH_REVIEW_ANALYSIS"
        );
    }

    /**
     * 调用对话条件提取接口
     */
    public DialogueExtractAiResponse extractDialogueConstraints(
            DialogueExtractAiRequest request
    ) {
        String url =
                aiServiceBaseUrl + "/internal/dialogue/extract";

        try {
            JsonNode response = post(
                    url,
                    request,
                    "DIALOGUE_CONSTRAINT_EXTRACTION"
            );

            return objectMapper.treeToValue(
                    response,
                    DialogueExtractAiResponse.class
            );
        } catch (Exception exception) {
            throw new RuntimeException(
                    "AI 对话条件提取失败："
                            + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * 调用评价摘要生成接口（EPIC-01 Story 7）
     */
    public JsonNode generateReviewSummary(
            Long merchantId,
            Integer version,
            List<Map<String, Object>> reviews,
            int minimumReviewCount
    ) {
        String url =
                aiServiceBaseUrl
                        + "/internal/merchants/review-summary";

        Map<String, Object> request = Map.of(
                "requestId",
                "summary-" + merchantId + "-v" + version,
                "merchantId",
                merchantId,
                "version",
                version,
                "reviews",
                reviews,
                "minimumReviewCount",
                minimumReviewCount
        );

        return post(
                url,
                request,
                "REVIEW_SUMMARY_GENERATION"
        );
    }

    /**
     * 调用商家亮点挖掘接口（EPIC-02 Story 5）
     *
     * @param merchantId          商家ID
     * @param version             亮点版本号
     * @param reviews             正面评价列表 [{reviewId, rating, content, reviewTime, keywords, sentiment}]
     * @param minimumPositiveCount 最少正面评价数阈值
     * @return AI 服务返回的亮点 JSON
     */
    public JsonNode generateMerchantHighlights(
            Long merchantId,
            Integer version,
            List<Map<String, Object>> reviews,
            int minimumPositiveCount
    ) {
        String url =
                aiServiceBaseUrl
                        + "/internal/merchants/highlights";

        Map<String, Object> request = Map.of(
                "requestId",
                "highlights-" + merchantId + "-v" + version,
                "merchantId",
                merchantId,
                "version",
                version,
                "reviews",
                reviews,
                "minimumPositiveCount",
                minimumPositiveCount
        );

        return post(
                url,
                request,
                "MERCHANT_HIGHLIGHT_GENERATION"
        );
    }

    /**
     * 语义检索 — 将用户查询转为向量，从 OpenSearch 检索相关文档。
     *
     * @param query       用户原始查询文本
     * @param merchantIds 限定候选商家 ID，null 表示不限定
     * @param sourceTypes 限定来源类型，null 表示全部
     * @return 检索结果 JSON（含 searchMode、results 列表）
     */
    public JsonNode semanticSearch(
            String query,
            List<Long> merchantIds,
            List<String> sourceTypes
    ) {
        String url =
                aiServiceBaseUrl + "/internal/search/semantic";

        Map<String, Object> filters =
                new java.util.LinkedHashMap<>();

        if (merchantIds != null && !merchantIds.isEmpty()) {
            filters.put("merchantIds", merchantIds);
        }

        if (sourceTypes != null && !sourceTypes.isEmpty()) {
            filters.put("sourceTypes", sourceTypes);
        }

        Map<String, Object> request =
                new java.util.LinkedHashMap<>();

        request.put("query", query);
        request.put("topK", 20);
        request.put("filters", filters);

        AiTraceContext context = traceService == null
                ? AiTraceContext.create(null, null, null, "SEMANTIC_SEARCH")
                : traceService.startTrace(null, null, null, "SEMANTIC_SEARCH");
        try {
            JsonNode result = semanticSearch(query, merchantIds, sourceTypes, context);
            if (traceService != null) {
                String mode = result.path("data").path("searchMode").asText("VECTOR");
                runTraceWrite(() -> traceService.completeTrace(context,
                        "KEYWORD_FALLBACK".equals(mode) ? "FALLBACK" : "SUCCESS",
                        Map.of("responseType", "SEMANTIC_SEARCH",
                                "resultCount", result.path("data").path("results").size(),
                                "degraded", "KEYWORD_FALLBACK".equals(mode)),
                        "OPENSEARCH", "EMBEDDING_SEARCH", null, "NOT_APPLICABLE"));
            }
            return result;
        } catch (RuntimeException exception) {
            if (traceService != null) {
                traceService.failTraceSafely(context, "SEMANTIC_SEARCH_FAILED",
                        exception.getMessage());
            }
            throw exception;
        }
    }

    public JsonNode semanticSearch(
            String query,
            List<Long> merchantIds,
            List<String> sourceTypes,
            AiTraceContext context
    ) {
        return semanticSearch(query, merchantIds, sourceTypes, 20, context);
    }

    /**
     * 语义检索 — 支持指定 topK。
     *
     * @param query       用户原始查询文本
     * @param merchantIds 限定候选商家 ID，null 表示不限定
     * @param sourceTypes 限定来源类型，null 表示全部
     * @param topK        返回结果数量
     * @param context     追踪上下文
     * @return 检索结果 JSON
     */
    public JsonNode semanticSearch(
            String query,
            List<Long> merchantIds,
            List<String> sourceTypes,
            int topK,
            AiTraceContext context
    ) {
        String url = aiServiceBaseUrl + "/internal/search/semantic";
        Map<String, Object> filters = new java.util.LinkedHashMap<>();
        if (merchantIds != null && !merchantIds.isEmpty()) filters.put("merchantIds", merchantIds);
        if (sourceTypes != null && !sourceTypes.isEmpty()) filters.put("sourceTypes", sourceTypes);
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("query", query);
        request.put("topK", topK);
        request.put("filters", filters);
        request.put("requestId", context.requestId());
        JsonNode result = post(url, request, "SEMANTIC_SEARCH", context, "KNOWLEDGE_RETRIEVAL");
        if (traceService != null) {
            traceService.addRetrievalSources(context, null, retrievalSources(result, merchantIds));
        }
        return result;
    }

    /**
     * 调用 AI 生成评价回复建议（EPIC-02 故事7：评价辅助回复）
     *
     * 系统根据评价的情感倾向（好评/差评）采用不同的回复策略，
     * 调用 AI 服务生成有针对性的回复建议。
     *
     * @param reviewId   评价 ID
     * @param merchantId 商家 ID
     * @param content    评价正文内容
     * @param strategy   回复策略：POSITIVE（好评策略）或 NEGATIVE（差评策略）
     * @param rating     评价评分（1-5），可为 null
     * @return AI 服务返回的 JSON，包含 replyContent、strategy、modelName、businessTraceId
     */
    public JsonNode generateReplyDraft(
            Long reviewId,
            Long merchantId,
            String content,
            String strategy,
            Integer rating
    ) {
        String url =
                aiServiceBaseUrl
                        + "/internal/reviews/generate-reply";

        Map<String, Object> request = Map.of(
                "reviewId", reviewId,
                "merchantId", merchantId,
                "content", content,
                "strategy", strategy,
                "rating", rating != null ? rating : 3
        );

        return post(
                url,
                request,
                "REVIEW_REPLY_GENERATION"
        );
    }

    /**
     * 调用周边竞品对比分析接口（EPIC-02 Story 6）
     *
     * 将本店和竞品的统计数据发送给 AI 服务，
     * 由大模型生成对比分析文字（优势/短板/总结/建议）。
     *
     * 统计数据的查询和组装由 CompetitorComparisonService 负责，
     * AI 服务只负责基于数据生成自然语言分析，不修改数值。
     *
     * @param merchantId  发起对比的本店 ID
     * @param competitors 商家统计数据列表（第一个为本店，其余为竞品）
     * @return AI 服务返回的 JSON，包含 merchantAnalyses, summaryText, improvementSuggestions
     */
    public JsonNode generateCompetitorComparison(
            Long merchantId,
            List<Map<String, Object>> competitors
    ) {
        String url =
                aiServiceBaseUrl
                        + "/internal/merchants/competitor-comparison";

        Map<String, Object> request = Map.of(
                "requestId",
                "competitor-" + merchantId + "-"
                        + System.currentTimeMillis(),
                "merchantId",
                merchantId,
                "competitors",
                competitors
        );

        return post(
                url,
                request,
                "COMPETITOR_ANALYSIS"
        );
    }

    /**
     * 调用周边竞品对比分析接口（带追踪上下文）。
     *
     * @see #generateCompetitorComparison(Long, List)
     */
    public JsonNode generateCompetitorComparison(
            Long merchantId,
            List<Map<String, Object>> competitors,
            AiTraceContext context
    ) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("requestId", context.requestId() == null
                ? "competitor-" + merchantId + "-" + System.currentTimeMillis()
                : context.requestId());
        request.put("merchantId", merchantId);
        request.put("competitors", competitors);

        return post(
                aiServiceBaseUrl + "/internal/merchants/competitor-comparison",
                request,
                "COMPETITOR_ANALYSIS",
                context,
                "MODEL_CALL"
        );
    }

    /**
     * 健康检查
     */
    public JsonNode analyzeReview(
            Long reviewId, Long merchantId, String content,
            Integer reviewVersion, AiTraceContext context
    ) {
        return post(aiServiceBaseUrl + "/internal/reviews/analyze",
                Map.of("reviewId", reviewId, "merchantId", merchantId,
                        "reviewVersion", reviewVersion == null ? 1 : reviewVersion,
                        "content", content),
                "REVIEW_ANALYSIS", context, "MODEL_CALL");
    }

    public DialogueExtractAiResponse extractDialogueConstraints(
            DialogueExtractAiRequest request, AiTraceContext context
    ) {
        try {
            return objectMapper.treeToValue(post(
                    aiServiceBaseUrl + "/internal/dialogue/extract", request,
                    "DIALOGUE_CONSTRAINT_EXTRACTION", context,
                    "CONSTRAINT_EXTRACTION"), DialogueExtractAiResponse.class);
        } catch (Exception exception) {
            throw new RuntimeException("AI dialogue constraint extraction failed", exception);
        }
    }

    public JsonNode generateReviewSummary(
            Long merchantId, Integer version, List<Map<String, Object>> reviews,
            int minimumReviewCount, AiTraceContext context
    ) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("requestId", context.requestId() == null
                ? "summary-" + merchantId + "-v" + version : context.requestId());
        request.put("merchantId", merchantId);
        request.put("version", version);
        request.put("reviews", reviews);
        request.put("minimumReviewCount", minimumReviewCount);
        return post(aiServiceBaseUrl + "/internal/merchants/review-summary",
                request, "REVIEW_SUMMARY_GENERATION", context, "MODEL_CALL");
    }

    public JsonNode generateMerchantHighlights(
            Long merchantId, Integer version, List<Map<String, Object>> reviews,
            int minimumPositiveCount, AiTraceContext context
    ) {
        Map<String, Object> request = new java.util.LinkedHashMap<>();
        request.put("requestId", context.requestId() == null
                ? "highlights-" + merchantId + "-v" + version : context.requestId());
        request.put("merchantId", merchantId);
        request.put("version", version);
        request.put("reviews", reviews);
        request.put("minimumPositiveCount", minimumPositiveCount);
        return post(aiServiceBaseUrl + "/internal/merchants/highlights",
                request, "MERCHANT_HIGHLIGHT_GENERATION", context, "MODEL_CALL");
    }

    public JsonNode generateReplyDraft(
            Long reviewId, Long merchantId, String content, String strategy,
            Integer rating, AiTraceContext context
    ) {
        return post(aiServiceBaseUrl + "/internal/reviews/generate-reply",
                Map.of("reviewId", reviewId, "merchantId", merchantId,
                        "content", content, "strategy", strategy,
                        "rating", rating == null ? 3 : rating),
                "REVIEW_REPLY_GENERATION", context, "MODEL_CALL");
    }

    /**
     * 停用 OpenSearch 知识文档（内容状态变更时调用）。
     *
     * @param sourceType 来源类型：MERCHANT / MERCHANT_INTRO / MENU / REVIEW
     * @param sourceIds 来源ID列表
     * @return AI 服务返回的 JSON，包含 deactivatedCount
     */
    public JsonNode deactivateKnowledge(
            String sourceType,
            List<Long> sourceIds
    ) {
        String url =
                aiServiceBaseUrl + "/internal/knowledge/deactivate";

        Map<String, Object> request = Map.of(
                "sourceType", sourceType,
                "sourceIds", sourceIds
        );

        return post(url, request, "KNOWLEDGE_DEACTIVATE");
    }

    /**
     * 调用内容清洗与切分接口。
     *
     * @param items 待处理内容列表，每项包含 merchantId/sourceType/sourceId/content
     * @return AI 服务返回的 JSON，包含 chunks 列表
     */
    public JsonNode processContent(List<Map<String, Object>> items) {
        String url = aiServiceBaseUrl + "/internal/content/process";
        Map<String, Object> request = Map.of("items", items);
        return post(url, request, "CONTENT_PROCESS");
    }

    /**
     * 调用知识向量化与存储接口。
     *
     * @param documents 文档列表，每项包含 chunkId/merchantId/sourceType/text 等
     * @return AI 服务返回的 JSON，包含 successCount/skipCount/failCount
     */
    public JsonNode upsertKnowledge(List<Map<String, Object>> documents) {
        String url = aiServiceBaseUrl + "/internal/knowledge/upsert";
        Map<String, Object> request = Map.of("documents", documents);
        return post(url, request, "KNOWLEDGE_UPSERT");
    }

    /**
     * 调用评论摘要忠实性测试接口（EPIC-06 Story 3）。
     *
     * 将已生成的商家口碑摘要和原始评价原文送入 AI 服务，
     * 以 LLM-as-Judge 模式对摘要中的每个声明做忠实性验证。
     *
     * @param requestBody 包含 merchantId、summary（ReviewSummaryResponse 结构）、
     *                    reviews（FaithfulnessReviewItem 列表）的 Map
     * @return AI 服务返回的 JSON，包含 overallScore、claimResults、各类计数等
     */
    public JsonNode testSummaryFaithfulness(Map<String, Object> requestBody) {
        String url = aiServiceBaseUrl + "/internal/reviews/summary-faithfulness-test";
        return post(url, requestBody, "FAITHFULNESS_TEST");
    }

    public boolean isHealthy() {
        try {
            String url = aiServiceBaseUrl + "/health";

            ResponseEntity<String> response =
                    restTemplate.getForEntity(
                            url,
                            String.class
                    );

            return response
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception exception) {
            return false;
        }
    }

    private JsonNode post(
            String url,
            Object body,
            String functionType
    ) {
        AiTraceContext context = traceService == null
                ? AiTraceContext.create(null, null, null, functionType)
                : traceService.startTrace(null, null, null, functionType);
        try {
            JsonNode result = post(url, body, functionType, context, stageFor(functionType));
            if (traceService != null) {
                runTraceWrite(() -> traceService.completeTrace(context, "SUCCESS",
                        Map.of("responseType", functionType, "degraded", false),
                        "FASTAPI", modelName(result), modelVersion(result), promptVersion(result)));
            }
            return result;
        } catch (RuntimeException exception) {
            if (traceService != null) {
                traceService.failTraceSafely(context, "AI_CALL_FAILED", exception.getMessage());
            }
            throw exception;
        }
    }

    private JsonNode post(
            String url,
            Object body,
            String functionType,
            AiTraceContext context,
            String stageName
    ) {
        String callId =
                "ai-" + UUID.randomUUID();
        AiRequestTraceStage traceStage = startStageSafely(
                context, stageName, functionType);

        long startNanos =
                System.nanoTime();

        OffsetDateTime startedAt =
                OffsetDateTime.now();

        /*
         * 请求摘要继续基于原始业务参数生成，避免提示词正文进入日志。
         * inputLength 则基于真正发往 AI 服务的请求体计算。
         */
        String requestSummary =
                requestSummary(body, functionType);

        Integer inputLength = null;

        try {
            requireInternalToken();

            Object requestBody =
                    attachRuntimeModel(
                            attachRuntimePrompt(
                                    body,
                                    functionType
                            ),
                            functionType
                    );

            inputLength =
                    payloadLength(requestBody);

            HttpHeaders headers =
                    new HttpHeaders();

            headers.setContentType(
                    MediaType.APPLICATION_JSON
            );

            headers.set(
                    "X-Internal-Token",
                    internalToken
            );
            headers.set("X-Trace-Id", context.traceId());
            if (context.requestId() != null && !context.requestId().isBlank()) {
                headers.set("X-Request-Id", context.requestId());
            }
            headers.set("X-AI-Stage", stageName);

            HttpEntity<String> entity =
                    new HttpEntity<>(
                            objectMapper
                                    .writeValueAsString(requestBody),
                            headers
                    );

            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            url,
                            entity,
                            String.class
                    );

            int latencyMs =
                    latencyMs(startNanos);

            String responseBody =
                    response.getBody();

            JsonNode result =
                    objectMapper.readTree(responseBody);

            AiCallLog aiCallLog =
                    buildAiCallLog(
                            callId,
                            context,
                            stageName,
                            functionType,
                            "SUCCESS",
                            latencyMs,
                            inputLength,
                            responseLength(responseBody),
                            null,
                            null,
                            requestSummary,
                            responseSummary(response, result),
                            startedAt
                    );

            recordLogsSafely(
                    aiCallLog,
                    callId,
                    functionType,
                    "INFO",
                    "SUCCESS",
                    latencyMs,
                    inputLength,
                    responseLength(responseBody),
                    response.getStatusCode().value(),
                    null
            );
            if (traceService != null) {
                boolean retrievalFallback = "SEMANTIC_SEARCH".equals(functionType)
                        && "KEYWORD_FALLBACK".equals(
                        result.path("data").path("searchMode").asText());
                if (retrievalFallback) {
                    runTraceWrite(() -> traceService.fallbackStage(traceStage,
                            Map.of("responseType", functionType,
                                    "retrievalMode", "KEYWORD_FALLBACK"),
                            "OPENSEARCH_UNAVAILABLE",
                            "Vector retrieval was unavailable; keyword fallback was used"));
                } else {
                    runTraceWrite(() -> traceService.completeStage(traceStage,
                            Map.of("responseType", functionType),
                            "FASTAPI", modelName(result), modelVersion(result),
                            promptVersion(result)));
                }
            }

            return result;
        } catch (HttpStatusCodeException exception) {
            RuntimeException wrapped =
                    new RuntimeException(
                            "AI 服务请求失败，HTTP 状态码："
                                    + exception
                                            .getStatusCode()
                                            .value(),
                            exception
                    );

            recordFailure(
                    callId,
                    context,
                    stageName,
                    functionType,
                    startNanos,
                    inputLength,
                    requestSummary,
                    startedAt,
                    classifyHttpError(exception),
                    wrapped,
                    exception.getStatusCode().value()
            );
            runTraceWrite(() -> traceService.failStage(
                    traceStage, classifyHttpError(exception), wrapped.getMessage()));

            throw wrapped;
        } catch (ResourceAccessException exception) {
            RuntimeException wrapped =
                    new RuntimeException(
                            "AI 服务连接失败",
                            exception
                    );

            recordFailure(
                    callId,
                    context,
                    stageName,
                    functionType,
                    startNanos,
                    inputLength,
                    requestSummary,
                    startedAt,
                    "CONNECTION_FAILED",
                    wrapped,
                    null
            );
            runTraceWrite(() -> traceService.failStage(
                    traceStage, "CONNECTION_FAILED", wrapped.getMessage()));

            throw wrapped;
        } catch (Exception exception) {
            RuntimeException wrapped =
                    new RuntimeException(
                            "AI 服务调用失败："
                                    + exception.getMessage(),
                            exception
                    );

            recordFailure(
                    callId,
                    context,
                    stageName,
                    functionType,
                    startNanos,
                    inputLength,
                    requestSummary,
                    startedAt,
                    classifyUnexpected(exception),
                    wrapped,
                    null
            );
            runTraceWrite(() -> traceService.failStage(
                    traceStage, classifyUnexpected(exception), wrapped.getMessage()));

            throw wrapped;
        }
    }

    /**
     * 为探店条件提取请求附加数据库中绑定的运行时模型配置。
     *
     * 请求摘要在本方法执行前已经生成，因此明文 API Key 不会进入
     * ai_call_logs 或审计日志。
     */
    private Object attachRuntimeModel(
            Object body,
            String functionType
    ) {
        if (!"DIALOGUE_CONSTRAINT_EXTRACTION".equals(
                functionType
        )) {
            return body;
        }

        if (runtimeModelConfigResolver == null) {
            throw new IllegalStateException(
                    "Runtime model configuration resolver is unavailable"
            );
        }

        RuntimeModelConfig runtimeModel =
                runtimeModelConfigResolver
                        .resolveStoreRecommendation();

        JsonNode bodyTree =
                objectMapper.valueToTree(body);

        if (!(bodyTree instanceof ObjectNode objectBody)) {
            throw new IllegalStateException(
                    "AI request body must be a JSON object"
            );
        }

        ObjectNode enrichedBody =
                objectBody.deepCopy();

        enrichedBody.set(
                "runtimeModel",
                objectMapper.valueToTree(runtimeModel)
        );

        return enrichedBody;
    }

    /**
     * 根据 AI 功能类型，将当前启用的提示词附加到请求体。
     *
     * 原请求对象不会被修改。数据库没有启用版本、场景不受管理，
     * 或提示词查询发生异常时，直接返回原请求体，由 Python 使用
     * 代码中的默认提示词。
     */
    private Object attachRuntimePrompt(
            Object body,
            String functionType
    ) {
        PromptScene scene =
                promptSceneFor(functionType);

        if (body == null
                || scene == null
                || promptManagementService == null) {
            return body;
        }

        try {
            ResolvedPrompt prompt =
                    promptManagementService
                            .resolveActivePrompt(scene)
                            .orElse(null);

            if (prompt == null) {
                return body;
            }

            JsonNode bodyTree =
                    objectMapper.valueToTree(body);

            if (!(bodyTree instanceof ObjectNode objectBody)) {
                return body;
            }

            ObjectNode enrichedBody =
                    objectBody.deepCopy();

            enrichedBody.put(
                    "systemPrompt",
                    prompt.content()
            );

            enrichedBody.put(
                    "promptVersion",
                    prompt.versionTag()
            );

            return enrichedBody;
        } catch (Exception exception) {
            log.warn(
                    "Runtime prompt resolution failed. scene={}, error={}",
                    scene.getCode(),
                    sanitizer.sanitize(
                            exception.getMessage()
                    )
            );

            return body;
        }
    }

    /**
     * 将现有 AIClientService 功能类型映射到提示词管理场景。
     *
     * 商家亮点和语义检索目前不属于本故事定义的六个场景，
     * 因此不注入动态提示词。
     */
    private PromptScene promptSceneFor(
            String functionType
    ) {
        if (functionType == null
                || functionType.isBlank()) {
            return null;
        }

        return switch (functionType) {
            case "REVIEW_ANALYSIS",
                 "BATCH_REVIEW_ANALYSIS" ->
                    PromptScene.SENTIMENT_ANALYSIS;

            case "DIALOGUE_CONSTRAINT_EXTRACTION" ->
                    PromptScene.CONSTRAINT_EXTRACTION;

            case "REVIEW_SUMMARY_GENERATION" ->
                    PromptScene.REVIEW_SUMMARY;

            case "REVIEW_REPLY_GENERATION" ->
                    PromptScene.REVIEW_REPLY;

            case "COMPETITOR_ANALYSIS" ->
                    PromptScene.BUSINESS_ADVICE;

            default -> null;
        };
    }

    private void requireInternalToken() {
        if (internalToken == null
                || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "INTERNAL_API_TOKEN is not configured"
            );
        }
    }

    private void recordFailure(
            String traceId,
            AiTraceContext context,
            String stageName,
            String functionType,
            long startNanos,
            Integer inputLength,
            String requestSummary,
            OffsetDateTime startedAt,
            String errorType,
            RuntimeException exception,
            Integer httpStatus
    ) {
        int latencyMs =
                latencyMs(startNanos);

        String status =
                "TIMEOUT".equals(errorType)
                        || "CONNECTION_FAILED".equals(errorType)
                        ? "TIMEOUT"
                        : "FAILED";

        AiCallLog aiCallLog =
                buildAiCallLog(
                        traceId,
                        context,
                        stageName,
                        functionType,
                        status,
                        latencyMs,
                        inputLength,
                        null,
                        errorType,
                        errorSummary(exception),
                        requestSummary,
                        "{\"errorType\":\""
                                + jsonEscape(errorType)
                                + "\"}",
                        startedAt
                );

        recordLogsSafely(
                aiCallLog,
                traceId,
                functionType,
                auditLevel(errorType),
                "FAILURE",
                latencyMs,
                inputLength,
                null,
                httpStatus,
                errorType
        );
    }

    private void recordLogsSafely(
            AiCallLog aiCallLog,
            String traceId,
            String functionType,
            String level,
            String result,
            Integer latencyMs,
            Integer inputLength,
            Integer outputLength,
            Integer httpStatus,
            String errorType
    ) {
        try {
            aiCallLogService.recordSafely(aiCallLog);
        } catch (Exception exception) {
            log.warn(
                    "AI 调用日志写入失败。traceId={}，错误={}",
                    sanitizer.sanitize(traceId),
                    sanitizer.sanitize(
                            exception.getMessage()
                    )
            );
        }

        AuditLog auditLog =
                new AuditLog();

        auditLog.setOperationType("AI_CALL");
        auditLog.setModule("AI");
        auditLog.setLevel(level);
        auditLog.setResult(result);
        auditLog.setObjectType("AI_CALL");

        auditLog.setObjectId(
                aiCallLog.getId() == null
                        ? traceId
                        : String.valueOf(
                                aiCallLog.getId()
                        )
        );

        auditLog.setBusinessTraceId(traceId);
        auditLog.setErrorCode(errorType);
        auditLog.setErrorMessage(
                aiCallLog.getErrorMessage()
        );

        auditLog.setMetadata(
                auditMetadata(
                        functionType,
                        latencyMs,
                        inputLength,
                        outputLength,
                        httpStatus,
                        aiCallLog.getId()
                )
        );

        try {
            auditLogService.recordSafely(auditLog);
        } catch (Exception exception) {
            log.warn(
                    "AI 审计日志写入失败。traceId={}，错误={}",
                    sanitizer.sanitize(traceId),
                    sanitizer.sanitize(
                            exception.getMessage()
                    )
            );
        }
    }

    private AiCallLog buildAiCallLog(
            String traceId,
            AiTraceContext context,
            String stageName,
            String functionType,
            String status,
            Integer latencyMs,
            Integer inputLength,
            Integer outputLength,
            String errorType,
            String errorMessage,
            String requestSummary,
            String responseSummary,
            OffsetDateTime createdAt
    ) {
        AiCallLog aiCallLog =
                new AiCallLog();

        aiCallLog.setTraceId(traceId);
        aiCallLog.setRootTraceId(context.traceId());
        aiCallLog.setStageName(stageName);
        aiCallLog.setUserId(context.userId());
        aiCallLog.setSessionId(context.sessionId());
        aiCallLog.setFunctionType(functionType);
        aiCallLog.setProvider("FASTAPI");
        aiCallLog.setModelName(
                "foodadvisor-ai-service"
        );
        aiCallLog.setStatus(status);
        aiCallLog.setLatencyMs(latencyMs);
        aiCallLog.setInputTokens(inputLength);
        aiCallLog.setOutputTokens(outputLength);
        aiCallLog.setTotalTokens(
                sum(inputLength, outputLength)
        );
        aiCallLog.setErrorType(errorType);
        aiCallLog.setErrorMessage(errorMessage);
        aiCallLog.setRequestSummary(requestSummary);
        aiCallLog.setResponseSummary(responseSummary);
        aiCallLog.setCreatedAt(createdAt);

        return aiCallLog;
    }

    private int nextSequence() {
        return STAGE_SEQUENCE.updateAndGet(value ->
                value == Integer.MAX_VALUE ? 1 : value + 1);
    }

    private AiRequestTraceStage startStageSafely(
            AiTraceContext context, String stageName, String functionType
    ) {
        if (traceService == null) return null;
        try {
            return traceService.startStage(
                    context, stageName, Map.of("responseType", functionType));
        } catch (Exception exception) {
            log.warn("AI trace stage start failed. traceId={}, stage={}, error={}",
                    sanitizer.sanitize(context.traceId()), stageName,
                    sanitizer.sanitize(exception.getMessage()));
            return null;
        }
    }

    private void runTraceWrite(Runnable action) {
        if (traceService == null || action == null) return;
        try {
            action.run();
        } catch (Exception exception) {
            log.warn("AI trace stage update failed: {}",
                    sanitizer.sanitize(exception.getMessage()));
        }
    }

    private String stageFor(String functionType) {
        return switch (functionType) {
            case "SEMANTIC_SEARCH" -> "KNOWLEDGE_RETRIEVAL";
            case "DIALOGUE_CONSTRAINT_EXTRACTION" -> "CONSTRAINT_EXTRACTION";
            default -> "MODEL_CALL";
        };
    }

    private String modelName(JsonNode value) {
        return text(value, "modelName");
    }

    private String modelVersion(JsonNode value) {
        return text(value, "modelVersion");
    }

    private String promptVersion(JsonNode value) {
        return text(value, "promptVersion");
    }

    private String text(JsonNode value, String field) {
        return value != null && value.hasNonNull(field) ? value.get(field).asText() : null;
    }

    private List<AiTraceRetrievalSource> retrievalSources(
            JsonNode response, List<Long> allowedMerchantIds
    ) {
        List<AiTraceRetrievalSource> sources = new ArrayList<>();
        JsonNode results = response == null ? null : response.path("data").path("results");
        if (results == null || !results.isArray()) return sources;
        int rank = 1;
        for (JsonNode item : results) {
            AiTraceRetrievalSource source = new AiTraceRetrievalSource();
            source.setSourceType(text(item, "sourceType"));
            source.setSourceId(text(item, "sourceId"));
            source.setDocumentId(text(item, "documentId"));
            source.setChunkId(text(item, "chunkId"));
            if (item.hasNonNull("merchantId") && item.get("merchantId").canConvertToLong()) {
                source.setMerchantId(item.get("merchantId").asLong());
            }
            if (allowedMerchantIds != null && !allowedMerchantIds.isEmpty()
                    && (source.getMerchantId() == null
                    || !allowedMerchantIds.contains(source.getMerchantId()))) {
                continue;
            }
            source.setMerchantName(text(item, "merchantName"));
            source.setSummary(text(item, "text"));
            source.setRankNo(rank++);
            if (item.hasNonNull("score") && item.get("score").isNumber()) {
                source.setRelevanceScore(BigDecimal.valueOf(item.get("score").asDouble()));
            }
            sources.add(source);
        }
        return sources;
    }

    private String requestSummary(
            Object body,
            String functionType
    ) {
        try {
            JsonNode node =
                    objectMapper.valueToTree(body);

            StringBuilder summary =
                    new StringBuilder();

            summary.append("{\"scene\":\"")
                    .append(jsonEscape(functionType))
                    .append("\",\"inputLength\":")
                    .append(payloadLength(body));

            appendLongField(
                    summary,
                    node,
                    "reviewId"
            );

            appendLongField(
                    summary,
                    node,
                    "merchantId"
            );

            appendLongField(
                    summary,
                    node,
                    "sessionId"
            );

            appendLongField(
                    summary,
                    node,
                    "messageId"
            );

            if (node.has("reviews")
                    && node.get("reviews").isArray()) {
                summary.append(",\"batchSize\":")
                        .append(
                                node.get("reviews").size()
                        );
            }

            summary.append("}");

            return sanitizer.sanitize(
                    summary.toString()
            );
        } catch (Exception exception) {
            return "{\"scene\":\""
                    + jsonEscape(functionType)
                    + "\"}";
        }
    }

    private void appendLongField(
            StringBuilder summary,
            JsonNode node,
            String field
    ) {
        if (node.has(field)
                && node.get(field)
                        .canConvertToLong()) {
            summary.append(",\"")
                    .append(field)
                    .append("\":")
                    .append(
                            node.get(field).asLong()
                    );
        }
    }

    private String responseSummary(
            ResponseEntity<String> response,
            JsonNode body
    ) {
        String modelName =
                body != null
                        && body.has("modelName")
                        && !body.get("modelName")
                                .isNull()
                        ? body.get("modelName")
                                .asText()
                        : null;

        return "{\"httpStatus\":"
                + response
                        .getStatusCode()
                        .value()
                + ",\"outputLength\":"
                + responseLength(
                        response.getBody()
                )
                + (
                        modelName == null
                                ? ""
                                : ",\"modelName\":\""
                                + jsonEscape(modelName)
                                + "\""
                )
                + "}";
    }

    private String auditMetadata(
            String functionType,
            Integer latencyMs,
            Integer inputLength,
            Integer outputLength,
            Integer httpStatus,
            Long aiCallLogId
    ) {
        return "{\"scene\":\""
                + jsonEscape(functionType)
                + "\","
                + "\"modelName\":"
                + "\"foodadvisor-ai-service\","
                + "\"latencyMs\":"
                + latencyMs
                + ","
                + "\"inputLength\":"
                + nullNumber(inputLength)
                + ","
                + "\"outputLength\":"
                + nullNumber(outputLength)
                + ","
                + "\"httpStatus\":"
                + nullNumber(httpStatus)
                + ","
                + "\"aiCallLogId\":"
                + nullNumber(aiCallLogId)
                + "}";
    }

    private String classifyHttpError(
            HttpStatusCodeException exception
    ) {
        int status =
                exception
                        .getStatusCode()
                        .value();

        if (status == 408) {
            return "TIMEOUT";
        }

        if (status == 429) {
            return "RATE_LIMITED";
        }

        if (status >= 500) {
            return "AI_SERVICE_UNAVAILABLE";
        }

        return "AI_SERVICE_HTTP_ERROR";
    }

    private String classifyUnexpected(
            Exception exception
    ) {
        if (exception
                instanceof IllegalStateException) {
            return "CONFIGURATION_ERROR";
        }

        return "PROTOCOL_ERROR";
    }

    private String auditLevel(
            String errorType
    ) {
        if ("TIMEOUT".equals(errorType)
                || "RATE_LIMITED".equals(
                        errorType
                )) {
            return "WARN";
        }

        return "ERROR";
    }

    private String errorSummary(
            Exception exception
    ) {
        String message =
                exception.getMessage();

        String summary =
                exception
                        .getClass()
                        .getSimpleName()
                        + (
                                message == null
                                        ? ""
                                        : ": " + message
                );

        summary =
                sanitizer.sanitize(summary);

        return summary.length()
                > MAX_ERROR_LENGTH
                ? summary.substring(
                        0,
                        MAX_ERROR_LENGTH
                )
                : summary;
    }

    private int latencyMs(
            long startNanos
    ) {
        long elapsed =
                System.nanoTime() - startNanos;

        return (int) Math.max(
                0L,
                elapsed / 1_000_000L
        );
    }

    private Integer payloadLength(
            Object value
    ) {
        try {
            return objectMapper
                    .writeValueAsString(value)
                    .length();
        } catch (Exception exception) {
            return null;
        }
    }

    private Integer responseLength(
            String value
    ) {
        return value == null
                ? 0
                : value.length();
    }

    private Integer sum(
            Integer first,
            Integer second
    ) {
        if (first == null
                && second == null) {
            return null;
        }

        return (
                first == null
                        ? 0
                        : first
        ) + (
                second == null
                        ? 0
                        : second
        );
    }

    private String nullNumber(
            Number value
    ) {
        return value == null
                ? "null"
                : value.toString();
    }

    private String jsonEscape(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
