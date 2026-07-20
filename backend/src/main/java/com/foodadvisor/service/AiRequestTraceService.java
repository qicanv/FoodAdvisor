package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.trace.AiTraceDetailVO;
import com.foodadvisor.dto.trace.AiTraceQueryRequest;
import com.foodadvisor.entity.AiRequestTrace;
import com.foodadvisor.entity.AiRequestTraceStage;
import com.foodadvisor.entity.AiTraceRetrievalSource;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.AiRequestTraceMapper;
import com.foodadvisor.mapper.AiRequestTraceStageMapper;
import com.foodadvisor.mapper.AiTraceRetrievalSourceMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.trace.AiTraceContext;
import com.foodadvisor.util.AiTraceSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class AiRequestTraceService {
    private static final Logger log = LoggerFactory.getLogger(AiRequestTraceService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AiRequestTraceMapper traceMapper;
    private final AiRequestTraceStageMapper stageMapper;
    private final AiTraceRetrievalSourceMapper sourceMapper;
    private final MerchantMapper merchantMapper;
    private final AiTraceSanitizer sanitizer;
    private final ConcurrentHashMap<String, Long> traceStarts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> stageStarts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> stageSequences =
            new ConcurrentHashMap<>();

    public AiRequestTraceService(
            AiRequestTraceMapper traceMapper,
            AiRequestTraceStageMapper stageMapper,
            AiTraceRetrievalSourceMapper sourceMapper,
            MerchantMapper merchantMapper,
            AiTraceSanitizer sanitizer
    ) {
        this.traceMapper = traceMapper;
        this.stageMapper = stageMapper;
        this.sourceMapper = sourceMapper;
        this.merchantMapper = merchantMapper;
        this.sanitizer = sanitizer;
    }

    public AiTraceContext startTrace(
            String requestId, Long sessionId, Long userId, String scene
    ) {
        AiTraceContext context = AiTraceContext.create(
                requestId, sessionId, userId, requireText(scene, "scene")
        );
        AiRequestTrace trace = new AiRequestTrace();
        trace.setTraceId(context.traceId());
        trace.setRequestId(trim(requestId));
        trace.setSessionId(sessionId);
        trace.setUserId(userId);
        trace.setScene(context.scene());
        trace.setStatus("RUNNING");
        trace.setStructuredConditions("{}");
        trace.setFinalOutputSummary("{}");
        trace.setStartedAt(OffsetDateTime.now());
        trace.setCreatedAt(trace.getStartedAt());
        try {
            traceMapper.insert(trace);
            traceStarts.put(context.traceId(), System.nanoTime());
            stageSequences.put(context.traceId(), new AtomicInteger());
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        }
        return context;
    }

    public void updateIntent(AiTraceContext context, String intent) {
        updateRootSafely(context, trace -> trace.setIntent(sanitizer.sanitizeText(intent)));
    }

    public void updateStructuredConditions(AiTraceContext context, Object conditions) {
        updateRootSafely(context,
                trace -> trace.setStructuredConditions(sanitizer.sanitizeJson(conditions)));
    }

    public AiRequestTraceStage startStage(
            AiTraceContext context, String stageName, int sequenceNo,
            int attemptNo, Object inputSummary
    ) {
        AiRequestTraceStage stage = new AiRequestTraceStage();
        stage.setTraceId(requireContext(context).traceId());
        stage.setStageName(requireText(stageName, "stageName"));
        stage.setSequenceNo(sequenceNo);
        stage.setAttemptNo(attemptNo <= 0 ? 1 : attemptNo);
        stage.setStatus("RUNNING");
        stage.setInputSummary(sanitizer.sanitizeJson(inputSummary));
        stage.setOutputSummary("{}");
        stage.setStartedAt(OffsetDateTime.now());
        stage.setCreatedAt(stage.getStartedAt());
        stageMapper.insert(stage);
        if (stage.getId() != null) stageStarts.put(stage.getId(), System.nanoTime());
        return stage;
    }

    public AiRequestTraceStage startStage(
            AiTraceContext context, String stageName, Object inputSummary
    ) {
        int sequence = stageSequences
                .computeIfAbsent(requireContext(context).traceId(),
                        key -> new AtomicInteger())
                .incrementAndGet();
        return startStage(context, stageName, sequence, 1, inputSummary);
    }

    public void completeStage(
            AiRequestTraceStage stage, Object outputSummary,
            String provider, String modelName, String modelVersion,
            String promptVersion
    ) {
        finishStage(stage, "SUCCESS", outputSummary, provider, modelName,
                modelVersion, promptVersion, null, null);
    }

    public void fallbackStage(AiRequestTraceStage stage, Object outputSummary,
                              String errorCode, String errorMessage) {
        finishStage(stage, "FALLBACK", outputSummary, null, "RULE_ENGINE",
                null, "NOT_APPLICABLE", errorCode, errorMessage);
    }

    public void failStage(AiRequestTraceStage stage, String errorCode, String errorMessage) {
        finishStage(stage, "FAILED", null, null, null, null, null,
                errorCode, errorMessage);
    }

    public void addRetrievalSources(
            AiTraceContext context, Long stageId, List<AiTraceRetrievalSource> sources
    ) {
        if (sources == null) return;
        List<AiTraceRetrievalSource> limitedSources = sources.stream().limit(50).toList();
        enrichMissingMerchantNames(limitedSources);
        for (AiTraceRetrievalSource source : limitedSources) {
            source.setId(null);
            source.setTraceId(requireContext(context).traceId());
            source.setStageId(stageId);
            source.setMerchantName(sanitizer.sanitizeText(source.getMerchantName()));
            source.setSummary(sanitizer.sanitizeText(source.getSummary()));
            source.setCreatedAt(OffsetDateTime.now());
            sourceMapper.insert(source);
        }
    }

    public void completeTrace(
            AiTraceContext context, String status, Object finalSummary,
            String provider, String modelName, String modelVersion, String promptVersion
    ) {
        String finalStatus = "FALLBACK".equals(status) ? "FALLBACK" : "SUCCESS";
        finishTrace(context, finalStatus, finalSummary, provider, modelName,
                modelVersion, promptVersion, null, null);
    }

    public void failTrace(AiTraceContext context, String errorCode, String errorMessage) {
        failTrace(context, null, errorCode, errorMessage);
    }

    public void failTrace(
            AiTraceContext context, Object finalSummary,
            String errorCode, String errorMessage
    ) {
        failRunningStagesSafely(context, errorCode, errorMessage);
        finishTrace(context, "FAILED", finalSummary, null, null, null, null,
                errorCode, errorMessage);
    }

    public void failTraceSafely(AiTraceContext context, String errorCode, String errorMessage) {
        failTraceSafely(context, null, errorCode, errorMessage);
    }

    public void failTraceSafely(
            AiTraceContext context, Object finalSummary,
            String errorCode, String errorMessage
    ) {
        try {
            failTrace(context, finalSummary, errorCode, errorMessage);
        } catch (Exception exception) {
            log.warn("AI trace failure write failed. traceId={}, error={}",
                    context == null ? null : context.traceId(),
                    sanitizer.sanitizeText(exception.getMessage()));
        }
    }

    public void failRunningStagesSafely(
            AiTraceContext context, String errorCode, String errorMessage
    ) {
        if (context == null) return;
        try {
            List<AiRequestTraceStage> running = stageMapper.selectList(
                    new QueryWrapper<AiRequestTraceStage>()
                            .eq("trace_id", context.traceId())
                            .eq("status", "RUNNING")
            );
            for (AiRequestTraceStage stage : running) {
                failStage(stage, errorCode, errorMessage);
            }
        } catch (Exception exception) {
            log.warn("AI running trace stages could not be closed. traceId={}, error={}",
                    context.traceId(), sanitizer.sanitizeText(exception.getMessage()));
        }
    }

    public Page<AiRequestTrace> query(AiTraceQueryRequest request) {
        int pageNum = request.getPageNum() <= 0 ? 1 : request.getPageNum();
        int pageSize = request.getPageSize() <= 0 ? 20
                : Math.min(request.getPageSize(), MAX_PAGE_SIZE);
        QueryWrapper<AiRequestTrace> wrapper = new QueryWrapper<>();
        wrapper.eq(hasText(request.getTraceId()), "trace_id", trim(request.getTraceId()))
                .eq(hasText(request.getRequestId()), "request_id", trim(request.getRequestId()))
                .eq(request.getSessionId() != null, "session_id", request.getSessionId())
                .eq(request.getUserId() != null, "user_id", request.getUserId())
                .eq(hasText(request.getScene()), "scene", trim(request.getScene()))
                .eq(hasText(request.getStatus()), "status", upper(request.getStatus()))
                .eq(hasText(request.getModelName()), "model_name", trim(request.getModelName()))
                .ge(request.getStartTime() != null, "started_at", request.getStartTime())
                .le(request.getEndTime() != null, "started_at", request.getEndTime());
        if (Boolean.TRUE.equals(request.getFallback())) {
            wrapper.and(value -> value.eq("status", "FALLBACK")
                    .or().exists("SELECT 1 FROM ai_request_trace_stages s "
                            + "WHERE s.trace_id = ai_request_traces.trace_id "
                            + "AND s.status = 'FALLBACK'"));
        } else if (Boolean.FALSE.equals(request.getFallback())) {
            wrapper.notExists("SELECT 1 FROM ai_request_trace_stages s "
                    + "WHERE s.trace_id = ai_request_traces.trace_id "
                    + "AND s.status = 'FALLBACK'");
        }
        wrapper.orderByDesc("started_at").orderByDesc("id");
        return traceMapper.selectPage(Page.of(pageNum, pageSize), wrapper);
    }

    public AiTraceDetailVO detail(String traceId) {
        AiRequestTrace trace = traceMapper.selectOne(
                new QueryWrapper<AiRequestTrace>().eq("trace_id", trim(traceId))
        );
        if (trace == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TRACE_NOT_FOUND", "AI trace was not found");
        }
        AiTraceDetailVO detail = new AiTraceDetailVO();
        detail.setTrace(trace);
        detail.setStages(stageMapper.selectList(
                new QueryWrapper<AiRequestTraceStage>().eq("trace_id", traceId)
                        .orderByAsc("sequence_no", "attempt_no")
        ));
        detail.setRetrievalSources(sourceMapper.selectList(
                new QueryWrapper<AiTraceRetrievalSource>().eq("trace_id", traceId)
                        .orderByAsc("rank_no", "id")
        ));
        enrichMissingMerchantNames(detail.getRetrievalSources());
        sanitizeDetail(detail);
        return detail;
    }

    private void finishStage(
            AiRequestTraceStage stage, String status, Object outputSummary,
            String provider, String modelName, String modelVersion,
            String promptVersion, String errorCode, String errorMessage
    ) {
        if (stage == null || stage.getId() == null) return;
        AiRequestTraceStage current = stageMapper.selectById(stage.getId());
        if (current == null) return;
        String resolvedStatus = resolveTerminalStatus(current.getStatus(), status);
        if (isTerminal(current.getStatus()) && current.getStatus().equals(resolvedStatus)) return;
        OffsetDateTime completedAt = OffsetDateTime.now();
        current.setStatus(resolvedStatus);
        current.setOutputSummary(sanitizer.sanitizeJson(outputSummary));
        current.setProvider(trim(provider));
        current.setModelName(trim(modelName));
        current.setModelVersion(trim(modelVersion));
        current.setPromptVersion(trim(promptVersion));
        current.setErrorCode(trim(errorCode));
        current.setErrorMessage(sanitizer.sanitizeText(errorMessage));
        current.setCompletedAt(completedAt);
        current.setDurationMs(elapsedNanos(
                stageStarts.remove(current.getId()), current.getStartedAt(), completedAt));
        stageMapper.updateById(current);
    }

    private void finishTrace(
            AiTraceContext context, String status, Object finalSummary,
            String provider, String modelName, String modelVersion,
            String promptVersion, String errorCode, String errorMessage
    ) {
        AiRequestTrace trace = findRequired(requireContext(context).traceId());
        String resolvedStatus = resolveTerminalStatus(trace.getStatus(), status);
        if (isTerminal(trace.getStatus()) && trace.getStatus().equals(resolvedStatus)) return;
        OffsetDateTime completedAt = OffsetDateTime.now();
        trace.setStatus(resolvedStatus);
        trace.setFinalOutputSummary(sanitizer.sanitizeJson(finalSummary));
        trace.setProvider(trim(provider));
        trace.setModelName(trim(modelName));
        trace.setModelVersion(trim(modelVersion));
        trace.setPromptVersion(trim(promptVersion));
        trace.setErrorCode(trim(errorCode));
        trace.setErrorMessage(sanitizer.sanitizeText(errorMessage));
        trace.setCompletedAt(completedAt);
        trace.setTotalDurationMs(elapsedNanos(
                traceStarts.remove(trace.getTraceId()), trace.getStartedAt(), completedAt));
        traceMapper.updateById(trace);
        stageSequences.remove(trace.getTraceId());
    }

    private void sanitizeDetail(AiTraceDetailVO detail) {
        if (detail == null) return;
        AiRequestTrace trace = detail.getTrace();
        if (trace != null) {
            trace.setStructuredConditions(sanitizer.sanitizeJson(trace.getStructuredConditions()));
            trace.setFinalOutputSummary(sanitizer.sanitizeJson(trace.getFinalOutputSummary()));
            trace.setErrorMessage(sanitizer.sanitizeText(trace.getErrorMessage()));
        }
        if (detail.getStages() != null) {
            for (AiRequestTraceStage stage : detail.getStages()) {
                stage.setInputSummary(sanitizer.sanitizeJson(stage.getInputSummary()));
                stage.setOutputSummary(sanitizer.sanitizeJson(stage.getOutputSummary()));
                stage.setErrorMessage(sanitizer.sanitizeText(stage.getErrorMessage()));
            }
        }
        if (detail.getRetrievalSources() != null) {
            for (AiTraceRetrievalSource source : detail.getRetrievalSources()) {
                source.setSummary(sanitizer.sanitizeText(source.getSummary()));
            }
        }
    }

    /** Resolves only missing merchant names in one batch for new and historic trace sources. */
    private void enrichMissingMerchantNames(List<AiTraceRetrievalSource> sources) {
        if (sources == null || sources.isEmpty() || merchantMapper == null) return;
        Set<Long> merchantIds = sources.stream()
                .filter(source -> !hasText(source.getMerchantName()))
                .map(AiTraceRetrievalSource::getMerchantId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (merchantIds.isEmpty()) return;
        try {
            Map<Long, String> namesById = merchantMapper.selectList(
                            new QueryWrapper<Merchant>().in("id", merchantIds))
                    .stream()
                    .filter(merchant -> merchant.getId() != null && hasText(merchant.getName()))
                    .collect(Collectors.toMap(Merchant::getId, Merchant::getName,
                            (first, ignored) -> first));
            for (AiTraceRetrievalSource source : sources) {
                if (!hasText(source.getMerchantName()) && source.getMerchantId() != null) {
                    source.setMerchantName(namesById.get(source.getMerchantId()));
                }
            }
        } catch (Exception exception) {
            log.warn("AI trace merchant name enrichment failed. merchantIds={}, error={}",
                    merchantIds.size(), sanitizer.sanitizeText(exception.getMessage()));
        }
    }

    private String resolveTerminalStatus(String currentStatus, String requestedStatus) {
        if (!isTerminal(currentStatus)) return requestedStatus;
        if (!isTerminal(requestedStatus)) return currentStatus;
        return terminalPriority(requestedStatus) > terminalPriority(currentStatus)
                ? requestedStatus : currentStatus;
    }

    private boolean isTerminal(String status) {
        return "SUCCESS".equals(status) || "FALLBACK".equals(status) || "FAILED".equals(status);
    }

    private int terminalPriority(String status) {
        return switch (status) {
            case "FAILED" -> 3;
            case "FALLBACK" -> 2;
            case "SUCCESS" -> 1;
            default -> 0;
        };
    }

    private void updateRootSafely(AiTraceContext context,
                                  java.util.function.Consumer<AiRequestTrace> update) {
        try {
            AiRequestTrace trace = findRequired(requireContext(context).traceId());
            update.accept(trace);
            traceMapper.updateById(trace);
        } catch (DataIntegrityViolationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("AI trace update failed. traceId={}, error={}",
                    context == null ? null : context.traceId(),
                    sanitizer.sanitizeText(exception.getMessage()));
        }
    }

    private AiRequestTrace findRequired(String traceId) {
        AiRequestTrace trace = traceMapper.selectOne(
                new QueryWrapper<AiRequestTrace>().eq("trace_id", traceId)
        );
        if (trace == null) throw new IllegalStateException("AI trace root does not exist");
        return trace;
    }

    private long elapsed(OffsetDateTime start, OffsetDateTime end) {
        return start == null ? 0 : Math.max(0, Duration.between(start, end).toMillis());
    }
    private long elapsedNanos(Long startNanos, OffsetDateTime start, OffsetDateTime end) {
        if (startNanos == null) return elapsed(start, end);
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }
    private AiTraceContext requireContext(AiTraceContext value) {
        if (value == null || !hasText(value.traceId())) {
            throw new IllegalArgumentException("trace context is required");
        }
        return value;
    }
    private String requireText(String value, String name) {
        if (!hasText(value)) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String trim(String value) { return value == null ? null : value.trim(); }
    private String upper(String value) { return value == null ? null : value.trim().toUpperCase(); }
}
