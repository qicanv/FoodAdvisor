package com.foodadvisor.dto.constraint;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 消费需求提取接口的响应数据。
 */
@Data
public class ConstraintExtractResponse {

    /**
     * 当前对话会话 ID。
     */
    private Long sessionId;

    /**
     * 仅从本轮用户消息中提取出的条件。
     */
    private ConstraintState extracted;

    /**
     * 本轮条件与历史条件合并后的完整状态。
     */
    private ConstraintState merged;

    /**
     * 本轮实际发生变化的字段名称。
     */
    private List<String> changes = new ArrayList<>();

    /**
     * 本轮检测出的冲突。
     */
    private List<ConstraintConflictVO> conflicts =
            new ArrayList<>();
}