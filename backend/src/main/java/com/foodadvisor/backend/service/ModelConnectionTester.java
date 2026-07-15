package com.foodadvisor.backend.service;

import com.foodadvisor.backend.dto.modelconfig.ConnectionTestResponse;
import com.foodadvisor.backend.entity.ModelConfig;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class ModelConnectionTester {

    private final ApiKeyCryptoService apiKeyCryptoService;

    public ModelConnectionTester(ApiKeyCryptoService apiKeyCryptoService) {
        this.apiKeyCryptoService = apiKeyCryptoService;
    }

    public ConnectionTestResponse test(ModelConfig config) {
        String endpoint = normalizeBaseUrl(config.getBaseUrl()) + "/models";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .header("Authorization", "Bearer "
                        + apiKeyCryptoService.decrypt(config.getEncryptedApiKey()))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            int statusCode = response.statusCode();
            if (statusCode >= 200 && statusCode < 300) {
                return new ConnectionTestResponse(
                        true,
                        "Connection test succeeded",
                        statusCode
                );
            }

            return new ConnectionTestResponse(
                    false,
                    "Model service returned HTTP " + statusCode,
                    statusCode
            );
        } catch (IllegalArgumentException exception) {
            return new ConnectionTestResponse(
                    false,
                    "Invalid model service address",
                    null
            );
        } catch (IOException exception) {
            return new ConnectionTestResponse(
                    false,
                    "Unable to connect to model service: " + exception.getMessage(),
                    null
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ConnectionTestResponse(
                    false,
                    "Connection test was interrupted",
                    null
            );
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
