package com.foodadvisor.service;

import com.foodadvisor.dto.trace.AiTraceDetailVO;
import com.foodadvisor.entity.AiRequestTrace;
import com.foodadvisor.entity.AiRequestTraceStage;
import com.foodadvisor.entity.AiTraceRetrievalSource;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.mapper.AiRequestTraceMapper;
import com.foodadvisor.mapper.AiRequestTraceStageMapper;
import com.foodadvisor.mapper.AiTraceRetrievalSourceMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.trace.AiTraceContext;
import com.foodadvisor.util.AiTraceSanitizer;
import com.foodadvisor.util.SensitiveLogSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiRequestTraceServiceTest {
    private AiRequestTraceMapper traceMapper;
    private AiRequestTraceStageMapper stageMapper;
    private AiTraceRetrievalSourceMapper sourceMapper;
    private MerchantMapper merchantMapper;
    private AiRequestTraceService service;
    private AiTraceContext context;

    @BeforeEach
    void setUp() {
        traceMapper = mock(AiRequestTraceMapper.class);
        stageMapper = mock(AiRequestTraceStageMapper.class);
        sourceMapper = mock(AiTraceRetrievalSourceMapper.class);
        merchantMapper = mock(MerchantMapper.class);
        service = new AiRequestTraceService(traceMapper, stageMapper, sourceMapper, merchantMapper,
                new AiTraceSanitizer(new ObjectMapper(), new SensitiveLogSanitizer()));
        context = new AiTraceContext("trc-test", "req-test", null, 1L, "REVIEW_ANALYSIS");
    }

    @Test
    void completeTraceDoesNotOverwriteFailedRoot() {
        AiRequestTrace trace = root("FAILED");
        when(traceMapper.selectOne(any())).thenReturn(trace);

        service.completeTrace(context, "SUCCESS", Map.of("status", "SUCCESS"),
                "FASTAPI", null, null, null);

        assertThat(trace.getStatus()).isEqualTo("FAILED");
        verify(traceMapper, never()).updateById(any(AiRequestTrace.class));
    }

    @Test
    void completeTraceDoesNotOverwriteFallbackRoot() {
        AiRequestTrace trace = root("FALLBACK");
        when(traceMapper.selectOne(any())).thenReturn(trace);

        service.completeTrace(context, "SUCCESS", Map.of("status", "SUCCESS"),
                "FASTAPI", null, null, null);

        assertThat(trace.getStatus()).isEqualTo("FALLBACK");
        verify(traceMapper, never()).updateById(any(AiRequestTrace.class));
    }

    @Test
    void repeatedSuccessCompletionIsIdempotent() {
        AiRequestTrace trace = root("RUNNING");
        when(traceMapper.selectOne(any())).thenReturn(trace);

        assertDoesNotThrow(() -> service.completeTrace(context, "SUCCESS", Map.of(),
                "FASTAPI", null, null, null));
        assertDoesNotThrow(() -> service.completeTrace(context, "SUCCESS", Map.of(),
                "FASTAPI", null, null, null));

        assertThat(trace.getStatus()).isEqualTo("SUCCESS");
        verify(traceMapper, times(1)).updateById(any(AiRequestTrace.class));
    }

    @Test
    void repeatedStageCloseCannotOverwriteFailureAndFailureClosesRunningStages() {
        AiRequestTraceStage stage = stage("RUNNING");
        when(stageMapper.selectById(8L)).thenReturn(stage);
        service.failStage(stage, "MODEL_FAILED", "password=123456");

        service.completeStage(stage, Map.of("status", "SUCCESS"), "FASTAPI", null, null, null);

        assertThat(stage.getStatus()).isEqualTo("FAILED");
        verify(stageMapper, times(1)).updateById(any(AiRequestTraceStage.class));

        AiRequestTrace root = root("RUNNING");
        AiRequestTraceStage running = stage("RUNNING");
        running.setId(9L);
        when(traceMapper.selectOne(any())).thenReturn(root);
        when(stageMapper.selectList(any())).thenReturn(List.of(running));
        when(stageMapper.selectById(9L)).thenReturn(running);

        service.failTrace(context, "REQUEST_FAILED", "Authorization: Bearer abcdef");

        assertThat(running.getStatus()).isEqualTo("FAILED");
        assertThat(root.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void detailAppliesDefensiveSecondSanitization() {
        AiRequestTrace trace = root("SUCCESS");
        trace.setStructuredConditions("{\"tastePreferences\":[\"微辣\"],\"password\":\"123456\"}");
        trace.setFinalOutputSummary("{\"status\":\"SUCCESS\",\"X-Internal-Token\":\"internal-secret-token\"}");
        trace.setErrorMessage("Authorization: Bearer abcdef test@example.com latitude=31.230416");
        AiRequestTraceStage stage = stage("SUCCESS");
        stage.setInputSummary("{\"reviewId\":1,\"private_key\":\"sk-complete-secret\"}");
        stage.setOutputSummary("{\"status\":\"SUCCESS\",\"phone\":\"18146258399\"}");
        stage.setErrorMessage("password=123456");
        AiTraceRetrievalSource source = new AiTraceRetrievalSource();
        source.setSummary("test@example.com longitude=121.473701");
        when(traceMapper.selectOne(any())).thenReturn(trace);
        when(stageMapper.selectList(any())).thenReturn(List.of(stage));
        when(sourceMapper.selectList(any())).thenReturn(List.of(source));

        AiTraceDetailVO detail = service.detail(context.traceId());

        String rendered = detail.getTrace().getStructuredConditions()
                + detail.getTrace().getFinalOutputSummary() + detail.getTrace().getErrorMessage()
                + detail.getStages().get(0).getInputSummary() + detail.getStages().get(0).getOutputSummary()
                + detail.getStages().get(0).getErrorMessage() + detail.getRetrievalSources().get(0).getSummary();
        assertThat(rendered).doesNotContain("internal-secret-token", "Bearer abcdef", "123456",
                "sk-complete-secret", "18146258399", "test@example.com", "31.230416", "121.473701");
        assertThat(detail.getTrace().getFinalOutputSummary()).contains("\"status\":\"SUCCESS\"");
    }

    @Test
    void retrievalSourceUsesResponseMerchantNameWithoutLookup() {
        AiTraceRetrievalSource source = retrievalSource(10L, "Response merchant");

        service.addRetrievalSources(context, null, List.of(source));

        assertThat(source.getMerchantName()).isEqualTo("Response merchant");
        verify(merchantMapper, never()).selectList(any());
        verify(sourceMapper).insert(source);
    }

    @Test
    void retrievalSourcesBatchFillMissingMerchantNamesAndAllowNullMerchantId() {
        Merchant merchant = new Merchant();
        merchant.setId(10L);
        merchant.setName("Batch merchant");
        when(merchantMapper.selectList(any())).thenReturn(List.of(merchant));
        AiTraceRetrievalSource first = retrievalSource(10L, null);
        AiTraceRetrievalSource second = retrievalSource(10L, "");
        AiTraceRetrievalSource withoutMerchant = retrievalSource(null, null);

        assertDoesNotThrow(() -> service.addRetrievalSources(context, null,
                List.of(first, second, withoutMerchant)));

        assertThat(first.getMerchantName()).isEqualTo("Batch merchant");
        assertThat(second.getMerchantName()).isEqualTo("Batch merchant");
        assertThat(withoutMerchant.getMerchantName()).isNull();
        verify(merchantMapper, times(1)).selectList(any());
        verify(sourceMapper, times(3)).insert(any(AiTraceRetrievalSource.class));
    }

    @Test
    void detailBackfillsHistoricRetrievalSourceMerchantName() {
        AiRequestTrace trace = root("SUCCESS");
        AiTraceRetrievalSource historic = retrievalSource(10L, null);
        Merchant merchant = new Merchant();
        merchant.setId(10L);
        merchant.setName("Historic merchant");
        when(traceMapper.selectOne(any())).thenReturn(trace);
        when(stageMapper.selectList(any())).thenReturn(List.of());
        when(sourceMapper.selectList(any())).thenReturn(List.of(historic));
        when(merchantMapper.selectList(any())).thenReturn(List.of(merchant));

        AiTraceDetailVO detail = service.detail(context.traceId());

        assertThat(detail.getRetrievalSources().get(0).getMerchantName())
                .isEqualTo("Historic merchant");
        verify(merchantMapper, times(1)).selectList(any());
    }

    private AiTraceRetrievalSource retrievalSource(Long merchantId, String merchantName) {
        AiTraceRetrievalSource source = new AiTraceRetrievalSource();
        source.setMerchantId(merchantId);
        source.setMerchantName(merchantName);
        source.setSummary("safe summary");
        return source;
    }

    private AiRequestTrace root(String status) {
        AiRequestTrace trace = new AiRequestTrace();
        trace.setId(1L);
        trace.setTraceId(context.traceId());
        trace.setStatus(status);
        trace.setStartedAt(OffsetDateTime.now().minusSeconds(1));
        return trace;
    }

    private AiRequestTraceStage stage(String status) {
        AiRequestTraceStage stage = new AiRequestTraceStage();
        stage.setId(8L);
        stage.setTraceId(context.traceId());
        stage.setStatus(status);
        stage.setStartedAt(OffsetDateTime.now().minusSeconds(1));
        return stage;
    }
}
