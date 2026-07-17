package com.foodadvisor.dto.dialogue;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户拒绝回答的字段记录。
 *
 * 保存到 chat_session_states.rejected_fields JSONB 字段中。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectedFieldRecord {

    /**
     * 用户拒绝回答的约束字段。
     *
     * 例如：
     * partySize
     * perCapitaBudget
     */
    private String field;

    /**
     * 用户拒绝回答时的会话状态版本号。
     *
     * 后续通过版本差判断是否仍处于两轮屏蔽期。
     */
    private Integer version;
}