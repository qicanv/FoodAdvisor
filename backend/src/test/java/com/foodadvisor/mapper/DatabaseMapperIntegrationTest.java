package com.foodadvisor.mapper;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "foodadvisor.hot-words.scheduled.enabled=false")
class DatabaseMapperIntegrationTest {

    static {
        Dotenv.configure().directory("./").ignoreIfMissing().systemProperties().load();
        Dotenv.configure().directory("../").ignoreIfMissing().systemProperties().load();
    }

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private RecommendationFeedbackMapper recommendationFeedbackMapper;

    @Test
    void auditLogCountQueryUsesCurrentDatabaseColumns() {
        Long count = auditLogMapper.countByOperatorRoleAndOperationType(
                "__MAPPER_TEST_ROLE__",
                "__MAPPER_TEST_OPERATION__",
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now()
        );

        assertNotNull(count);
        assertTrue(count >= 0);
    }

    @Test
    void recommendationFeedbackCountQueryUsesCurrentTableName() {
        Long count = recommendationFeedbackMapper.countByTimeRange(
                OffsetDateTime.parse("1900-01-01T00:00:00Z"),
                OffsetDateTime.parse("1900-01-02T00:00:00Z")
        );

        assertNotNull(count);
        assertTrue(count >= 0);
    }
}
