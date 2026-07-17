package com.foodadvisor.mapper;

/**
 * 内部使用：标签统计 SQL 的返回行映射。
 * 不作为对外 DTO，只在 Service 层被聚合成 ReviewTagStatVO。
 */
public class TagSentimentCount {
    private String tagCode;
    private String tagName;
    private String category;
    private String sentiment;   // POSITIVE / NEUTRAL / NEGATIVE
    private Long cnt;

    // getter / setter

    public String getTagCode() { return tagCode; }
    public void setTagCode(String tagCode) { this.tagCode = tagCode; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public Long getCnt() { return cnt; }
    public void setCnt(Long cnt) { this.cnt = cnt; }
}
