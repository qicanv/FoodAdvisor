package com.foodadvisor.dto.constraint;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示消费需求中需要用户进一步确认的冲突。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintConflictVO {

    /**
     * 发生冲突的字段。
     * 例如：merchantTypes、tasteRestrictions。
     */
    private String field;

    /**
     * 面向调用方的冲突说明。
     */
    private String message;
}