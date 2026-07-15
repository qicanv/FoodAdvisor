package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_session_states")
public class ChatSessionState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** JSONB — 当前合并后的消费条件 */
    private String currentConstraints;

    /** JSONB — 缺失字段列表 */
    private String missingFields;

    /** JSONB — 用户拒绝回答的字段 */
    private String rejectedFields;

    /** JSONB — 待确认的冲突条件 */
    private String pendingConfirmation;

    /** COLLECTING / CONFIRMING / SEARCHING / RECOMMENDED / COMPLETED */
    private String conversationStage;

    private Integer version;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
