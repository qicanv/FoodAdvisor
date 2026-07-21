package com.foodadvisor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private Map<String, Rule> rules = new LinkedHashMap<>();

    @Data
    public static class Rule {
        private boolean enabled = true;
        private List<String> paths = new ArrayList<>();
        private List<String> methods = new ArrayList<>();
        private RateLimitKeyType keyType = RateLimitKeyType.USER;
        private int maxRequests;
        private long windowSeconds;
    }
}
