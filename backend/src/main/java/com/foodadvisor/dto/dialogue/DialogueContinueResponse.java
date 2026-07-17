package com.foodadvisor.dto.dialogue;

import com.foodadvisor.dto.constraint.ConstraintConflictVO;
import com.foodadvisor.dto.constraint.ConstraintState;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 多轮对话继续处理后的响应。
 */
@Data
public class DialogueContinueResponse {

    /**
     * 当前会话 ID。
     */
    private Long sessionId;

    private Long userMessageId;

    /**
     * 当前对话阶段。
     *
     * COLLECTING：继续收集需求
     * CONFIRMING：等待用户确认冲突
     * SEARCHING：可以进入商家推荐
     */
    private String stage;

    /**
     * 当前会话合并后的完整消费条件。
     */
    private ConstraintState constraints;

    /**
     * 当前仍然缺少的关键字段。
     */
    private List<String> missingFields =
            new ArrayList<>();

    /**
     * 本轮需要向用户提出的问题。
     *
     * 每轮最多两个。
     */
    private List<FollowUpQuestionVO> questions =
            new ArrayList<>();

    /**
     * 当前条件是否已经足够进入推荐。
     */
    private boolean readyForRecommendation;

    /**
     * 用户是否明确要求停止追问并直接推荐。
     */
    private boolean directRecommendRequested;

    /**
     * 当前检测到的条件冲突。
     */
    private List<ConstraintConflictVO> conflicts =
            new ArrayList<>();
}
