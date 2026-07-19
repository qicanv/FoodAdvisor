package com.foodadvisor.service;

import com.foodadvisor.dto.dialogue.DialogueMessageRequest;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "foodadvisor.hot-words.scheduled.enabled=false",
        "ai-service.internal-token="
})
class DiningDialogueTransactionIntegrationTest {

    static {
        Dotenv.configure().directory("./")
                .ignoreIfMissing().systemProperties().load();
        Dotenv.configure().directory("../")
                .ignoreIfMissing().systemProperties().load();
    }

    private static final String REQUEST_PREFIX =
            "tx-rollback-assistant-";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DiningDialogueMessageService service;

    @MockitoSpyBean
    private RedisTemplate<String, Object> redisTemplate;

    private Long sessionId;
    private Long userId;
    private String requestId;
    private Long evidenceHighlightId;
    private Long evidenceReviewId;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, Object> values =
                mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(any(), any(), any()))
                .thenReturn(true);
        when(values.get(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == null
                        ? null
                        : null
        );

        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users ORDER BY id LIMIT 1",
                Long.class
        );
        sessionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO chat_sessions (
                    user_id, title, status, created_at, updated_at
                )
                VALUES (?, 'transaction rollback test', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                userId
        );
        jdbcTemplate.update(
                """
                INSERT INTO chat_session_states (
                    session_id, current_constraints, missing_fields,
                    rejected_fields, pending_confirmation,
                    conversation_stage, version, updated_at
                )
                VALUES (?, '{}'::jsonb, '[]'::jsonb, '[]'::jsonb,
                        '[]'::jsonb, 'COLLECTING', 1,
                        CURRENT_TIMESTAMP)
                """,
                sessionId
        );

        requestId = REQUEST_PREFIX + UUID.randomUUID();
        jdbcTemplate.execute(
                """
                CREATE OR REPLACE FUNCTION fail_ai_dining_assistant_test()
                RETURNS trigger AS $$
                BEGIN
                    IF NEW.role = 'ASSISTANT'
                       AND NEW.request_id LIKE 'tx-rollback-assistant-%'
                    THEN
                        RAISE EXCEPTION 'forced assistant insert failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """
        );
        jdbcTemplate.execute(
                """
                DROP TRIGGER IF EXISTS
                    trg_fail_ai_dining_assistant_test
                    ON chat_messages
                """
        );
        jdbcTemplate.execute(
                """
                CREATE TRIGGER trg_fail_ai_dining_assistant_test
                BEFORE INSERT ON chat_messages
                FOR EACH ROW
                EXECUTE FUNCTION fail_ai_dining_assistant_test()
                """
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute(
                """
                DROP TRIGGER IF EXISTS
                    trg_fail_ai_dining_assistant_test
                    ON chat_messages
                """
        );
        jdbcTemplate.execute(
                """
                DROP FUNCTION IF EXISTS
                    fail_ai_dining_assistant_test()
                """
        );
        jdbcTemplate.execute(
                "DROP TRIGGER IF EXISTS trg_fail_recommendation_evidence_test "
                        + "ON recommendation_evidences");
        jdbcTemplate.execute(
                "DROP FUNCTION IF EXISTS fail_recommendation_evidence_test()");
        if (sessionId != null) {
            jdbcTemplate.update(
                    "DELETE FROM chat_sessions WHERE id = ?",
                    sessionId
            );
        }
        if (evidenceHighlightId != null) {
            jdbcTemplate.update(
                    "DELETE FROM merchant_highlights WHERE id = ?",
                    evidenceHighlightId);
        }
        if (evidenceReviewId != null) {
            jdbcTemplate.update(
                    "DELETE FROM reviews WHERE id = ?",
                    evidenceReviewId);
        }
    }

    @Test
    void assistantInsertFailureRollsBackWholeInitialTurn() {
        DialogueMessageRequest request =
                new DialogueMessageRequest();
        request.setUserId(userId);
        request.setRequestId(requestId);
        request.setContent(
                "四个人，人均八十，想吃川菜，直接推荐"
        );
        request.setUserLatitude(new BigDecimal("30.5728"));
        request.setUserLongitude(new BigDecimal("104.0668"));

        assertThrows(
                RuntimeException.class,
                () -> service.sendMessage(sessionId, request)
        );

        assertEquals(
                0,
                count(
                        "SELECT count(*) FROM chat_messages "
                                + "WHERE session_id = ?",
                        sessionId
                )
        );
        assertEquals(
                0,
                count(
                        "SELECT count(*) FROM constraint_extractions "
                                + "WHERE session_id = ?",
                        sessionId
                )
        );
        assertEquals(
                0,
                count(
                        "SELECT count(*) FROM recommendations "
                                + "WHERE session_id = ?",
                        sessionId
                )
        );
        assertEquals(
                0,
                count(
                        """
                        SELECT count(*)
                          FROM recommendation_items ri
                          JOIN recommendations r
                            ON r.id = ri.recommendation_id
                         WHERE r.session_id = ?
                        """,
                        sessionId
                )
        );
        assertEquals(
                1,
                count(
                        """
                        SELECT version
                          FROM chat_session_states
                         WHERE session_id = ?
                           AND current_constraints = '{}'::jsonb
                           AND conversation_stage = 'COLLECTING'
                        """,
                        sessionId
                )
        );
    }

    @Test
    void recommendationEvidenceInsertFailureRollsBackRecommendationGraph() {
        jdbcTemplate.execute(
                "DROP TRIGGER IF EXISTS trg_fail_ai_dining_assistant_test "
                        + "ON chat_messages");
        Long merchantId = jdbcTemplate.queryForObject(
                """
                SELECT id FROM merchants
                 WHERE platform_status = 'ACTIVE'
                   AND operation_status = 'OPERATING'
                   AND deleted_at IS NULL
                 ORDER BY id LIMIT 1
                """,
                Long.class);
        evidenceReviewId = jdbcTemplate.queryForObject(
                """
                INSERT INTO reviews (
                    merchant_id, review_type, rating, content, source,
                    current_version, status, moderation_status, risk_level,
                    published_at, created_at, updated_at
                )
                VALUES (?, 'ORIGINAL', 5,
                        '环境安静，适合聊天，真实事务测试评价内容',
                        'SYSTEM', 1, 'PUBLISHED', 'APPROVED', 'LOW',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class, merchantId);
        evidenceHighlightId = jdbcTemplate.queryForObject(
                """
                INSERT INTO merchant_highlights (
                    merchant_id, highlight_type, title, description,
                    mention_count, positive_ratio, version, status, generated_at
                )
                VALUES (?, 'ENVIRONMENT', '环境安静',
                        '有评价提到环境安静', 1, 1, 9999,
                        'ACTIVE', CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class, merchantId);
        jdbcTemplate.update(
                """
                INSERT INTO merchant_highlight_evidences (
                    highlight_id, review_id, review_version,
                    evidence_excerpt, created_at
                )
                VALUES (?, ?, 1, '环境安静', CURRENT_TIMESTAMP)
                """,
                evidenceHighlightId, evidenceReviewId);
        jdbcTemplate.execute(
                """
                CREATE OR REPLACE FUNCTION fail_recommendation_evidence_test()
                RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced evidence insert failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute(
                """
                CREATE TRIGGER trg_fail_recommendation_evidence_test
                BEFORE INSERT ON recommendation_evidences
                FOR EACH ROW EXECUTE FUNCTION
                    fail_recommendation_evidence_test()
                """);

        DialogueMessageRequest request = new DialogueMessageRequest();
        request.setUserId(userId);
        request.setRequestId("tx-evidence-" + UUID.randomUUID());
        request.setContent(
                "四个人，人均八十，环境安静，适合聊天，直接推荐");
        request.setUserLatitude(new BigDecimal("30.5728"));
        request.setUserLongitude(new BigDecimal("104.0668"));

        assertThrows(RuntimeException.class,
                () -> service.sendMessage(sessionId, request));

        assertEquals(0, count(
                "SELECT count(*) FROM recommendations WHERE session_id = ?",
                sessionId));
        assertEquals(0, count(
                """
                SELECT count(*) FROM recommendation_items ri
                JOIN recommendations r ON r.id = ri.recommendation_id
                WHERE r.session_id = ?
                """, sessionId));
        assertEquals(0, count(
                """
                SELECT count(*) FROM recommendation_evidences re
                JOIN recommendation_items ri
                  ON ri.id = re.recommendation_item_id
                JOIN recommendations r ON r.id = ri.recommendation_id
                WHERE r.session_id = ?
                """, sessionId));
    }

    private int count(String sql, Long id) {
        Integer value = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                id
        );
        return value == null ? 0 : value;
    }
}
