package com.foodadvisor.dto.dialogue;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个追问问题。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowUpQuestionVO {

    /**
     * 该问题对应的约束字段。
     *
     * 例如：
     * partySize
     * perCapitaBudget
     * cuisines
     */
    private String field;

    /**
     * 展示给用户的问题文案。
     */
    private String question;
}