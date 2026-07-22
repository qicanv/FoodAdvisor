package com.foodadvisor.dto.ai;

import java.math.BigDecimal;

/**
 * 由 Spring Boot 从数据库模型配置中解析出的单次 AI 调用配置。
 *
 * apiKey 仅允许存在于进程内存和发往内部 AI 服务的请求中，
 * 不得写入业务日志、调用日志或响应。
 */
public record RuntimeModelConfig(
        String provider,
        String modelName,
        String baseUrl,
        String apiKey,
        Integer timeoutMs,
        BigDecimal temperature,
        Integer maxOutputTokens
) {
}