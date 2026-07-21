package com.foodadvisor.dto.report;

import lombok.Data;

/**
 * 管理员处理举报请求
 */
@Data
public class ResolveReportRequest {

    /** RESOLVED / REJECTED */
    private String status;

    /** 处理结果说明 */
    private String resolution;
}
