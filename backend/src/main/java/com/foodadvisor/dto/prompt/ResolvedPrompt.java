package com.foodadvisor.dto.prompt;

/**
 * 实际 AI 请求所使用的提示词版本。
 *
 * 该对象只用于运行时传递，不直接作为管理端写入请求。
 */
public record ResolvedPrompt(
        Long definitionId,
        Long versionId,
        String sceneCode,
        Integer versionNo,
        String versionTag,
        String content
) {
}