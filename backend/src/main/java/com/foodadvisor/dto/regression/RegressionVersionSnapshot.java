package com.foodadvisor.dto.regression;

import java.time.OffsetDateTime;

/**
 * 一类回归测试在运行开始时确定的版本快照。
 */
public record RegressionVersionSnapshot(
        String testType,
        String sceneType,

        Long modelConfigId,
        String modelConfigName,
        String modelName,
        String modelVersion,
        OffsetDateTime modelConfigUpdatedAt,

        Long promptDefinitionId,
        Long promptVersionId,
        String promptVersion,

        String algorithmVersion
) {
}