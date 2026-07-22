package com.foodadvisor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodadvisor.dto.regression.RegressionExecutionResult;
import com.foodadvisor.dto.regression.RegressionVersionSnapshot;
import com.foodadvisor.entity.RegressionTestCase;
import com.foodadvisor.trace.AiTraceContext;

/**
 * 四类回归测试执行器的统一协议。
 */
public interface RegressionTestCaseExecutor {

    /**
     * 当前执行器负责的测试类型。
     */
    String supportedTestType();

    /**
     * 执行单个测试案例。
     *
     * 执行失败时直接抛出异常，由外层任务服务记录
     * executionStatus=FAILED 和错误原因。
     */
    RegressionExecutionResult execute(
            RegressionTestCase testCase,
            RegressionVersionSnapshot versionSnapshot,
            JsonNode executionOptions,
            AiTraceContext traceContext
    );
}