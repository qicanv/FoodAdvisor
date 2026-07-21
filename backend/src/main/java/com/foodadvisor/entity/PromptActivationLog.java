package com.foodadvisor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

@TableName("prompt_activation_logs")
public class PromptActivationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long promptDefinitionId;

    private Long fromVersionId;

    private Long toVersionId;

    private String operationType;

    private String operationNote;

    private Long operatedBy;

    private OffsetDateTime operatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPromptDefinitionId() {
        return promptDefinitionId;
    }

    public void setPromptDefinitionId(Long promptDefinitionId) {
        this.promptDefinitionId = promptDefinitionId;
    }

    public Long getFromVersionId() {
        return fromVersionId;
    }

    public void setFromVersionId(Long fromVersionId) {
        this.fromVersionId = fromVersionId;
    }

    public Long getToVersionId() {
        return toVersionId;
    }

    public void setToVersionId(Long toVersionId) {
        this.toVersionId = toVersionId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getOperationNote() {
        return operationNote;
    }

    public void setOperationNote(String operationNote) {
        this.operationNote = operationNote;
    }

    public Long getOperatedBy() {
        return operatedBy;
    }

    public void setOperatedBy(Long operatedBy) {
        this.operatedBy = operatedBy;
    }

    public OffsetDateTime getOperatedAt() {
        return operatedAt;
    }

    public void setOperatedAt(OffsetDateTime operatedAt) {
        this.operatedAt = operatedAt;
    }
}