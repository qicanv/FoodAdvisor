package com.foodadvisor.enums;

import java.util.Arrays;

public enum PromptScene {

    DINING_RECOMMENDATION("探店推荐"),
    CONSTRAINT_EXTRACTION("需求提取"),
    REVIEW_SUMMARY("评价摘要"),
    SENTIMENT_ANALYSIS("情感分析"),
    REVIEW_REPLY("评价回复"),
    BUSINESS_ADVICE("经营建议");

    private final String sceneName;

    PromptScene(String sceneName) {
        this.sceneName = sceneName;
    }

    public String getCode() {
        return name();
    }

    public String getSceneName() {
        return sceneName;
    }

    public static PromptScene fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "Prompt scene code must not be blank"
            );
        }

        return Arrays.stream(values())
                .filter(scene -> scene.name().equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported prompt scene: " + code
                ));
    }
}