package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.ai.DialogueExtractAiRequest;
import com.foodadvisor.dto.ai.DialogueExtractAiResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AI 服务调用客户端 — 通过 HTTP 调用 FastAPI
 */
@Service
public class AIClientService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai-service.base-url}")
    private String aiServiceBaseUrl;

    @Value("${ai-service.internal-token:}")
    private String internalToken;

    @Value("${ai-service.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${ai-service.read-timeout:60000}")
    private int readTimeout;

    public AIClientService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
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
    public JsonNode analyzeReview(Long reviewId, Long merchantId, String content, Integer reviewVersion) {
        String url = aiServiceBaseUrl + "/internal/reviews/analyze";

        Map<String, Object> request = Map.of(
                "reviewId", reviewId,
                "merchantId", merchantId,
                "reviewVersion", reviewVersion != null ? reviewVersion : 1,
                "content", content
        );

        return post(url, request);
    }

    /**
     * 调用评论分析接口（兼容旧调用）
     */
    public JsonNode analyzeReview(Long reviewId, Long merchantId, String content) {
        return analyzeReview(reviewId, merchantId, content, 1);
    }

    /**
     * 批量分析评论
     */
    public JsonNode batchAnalyzeReviews(java.util.List<Map<String, Object>> reviews) {
        String url = aiServiceBaseUrl + "/internal/reviews/batch-analyze";
        Map<String, Object> request = Map.of("reviews", reviews);
        return post(url, request);
    }

    public DialogueExtractAiResponse extractDialogueConstraints(
            DialogueExtractAiRequest request
    ) {
        String url =
                aiServiceBaseUrl + "/internal/dialogue/extract";

        try {
            JsonNode response = post(url, request);
            return objectMapper.treeToValue(
                    response,
                    DialogueExtractAiResponse.class
            );
        } catch (Exception exception) {
            throw new RuntimeException(
                    "AI dialogue extraction failed: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * 健康检查
     */
    public boolean isHealthy() {
        try {
            String url = aiServiceBaseUrl + "/health";
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode post(String url, Object body) {
        try {
            requireInternalToken();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Token", internalToken);
            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(body), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException(
                    "AI service HTTP "
                            + e.getStatusCode().value(),
                    e
            );
        } catch (ResourceAccessException e) {
            throw new RuntimeException(
                    "AI service connection failed",
                    e
            );
        } catch (Exception e) {
            throw new RuntimeException("AI service call failed: " + e.getMessage(), e);
        }
    }

    private void requireInternalToken() {
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException(
                    "INTERNAL_API_TOKEN is required for AI service calls"
            );
        }
    }
}
