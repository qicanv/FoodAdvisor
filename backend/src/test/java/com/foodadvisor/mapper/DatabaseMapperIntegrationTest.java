package com.foodadvisor.mapper;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.foodadvisor.entity.ChatMessage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

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

    @Test
    @Transactional
    void sameRequestAllowsOneUserAndOneAssistantMessage() {
        Long sessionId = insertSession();
        String requestId = UUID.randomUUID().toString();

        assertDoesNotThrow(() -> {
            insertMessage(sessionId, requestId, "USER");
            insertMessage(sessionId, requestId, "ASSISTANT");
        });

        List<String> storedMessages = jdbcTemplate.queryForList(
                """
                SELECT role || '/' || message_type
                  FROM chat_messages
                 WHERE session_id = ? AND request_id = ?
                 ORDER BY role DESC
                """,
                String.class,
                sessionId,
                requestId
        );
        assertEquals(
                List.of("USER/TEXT", "ASSISTANT/RECOMMENDATION"),
                storedMessages
        );
    }

    @Test
    @Transactional
    void duplicateUserMessageIsRejected() {
        Long sessionId = insertSession();
        String requestId = UUID.randomUUID().toString();
        insertMessage(sessionId, requestId, "USER");

        assertThrows(
                DuplicateKeyException.class,
                () -> insertMessage(sessionId, requestId, "USER")
        );
    }

    @Test
    @Transactional
    void duplicateAssistantMessageIsRejected() {
        Long sessionId = insertSession();
        String requestId = UUID.randomUUID().toString();
        insertMessage(sessionId, requestId, "ASSISTANT");

        assertThrows(
                DuplicateKeyException.class,
                () -> insertMessage(
                        sessionId,
                        requestId,
                        "ASSISTANT"
                )
        );
    }

    @Test
    @Transactional
    void reservedMessageIdCanBeUsedForAtomicInsert() {
        Long sessionId = insertSession();
        Long messageId = chatMessageMapper.nextId();
        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setSessionId(sessionId);
        message.setRole("USER");
        message.setContent("reserved id test");
        message.setMessageType("TEXT");
        message.setRequestId(UUID.randomUUID().toString());
        message.setMetadata("{}");
        message.setCreatedAt(OffsetDateTime.now());

        assertEquals(
                1,
                chatMessageMapper.insertReserved(message)
        );

        assertEquals(
                messageId,
                jdbcTemplate.queryForObject(
                        "SELECT id FROM chat_messages WHERE id = ?",
                        Long.class,
                        messageId
                )
        );
    }

    private Long insertSession() {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO chat_sessions (
                    user_id, title, status, created_at, updated_at
                )
                SELECT id, 'request role index test', 'ACTIVE',
                       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                  FROM users
                 ORDER BY id
                 LIMIT 1
                RETURNING id
                """,
                Long.class
        );
    }

    private void insertMessage(
            Long sessionId,
            String requestId,
            String role
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO chat_messages (
                    session_id, role, content, message_type,
                    request_id, metadata, created_at
                )
                VALUES (?, ?, 'index test', ?, ?, ?::jsonb,
                        CURRENT_TIMESTAMP)
                """,
                sessionId,
                role,
                "ASSISTANT".equals(role)
                        ? "RECOMMENDATION"
                        : "TEXT",
                requestId,
                "ASSISTANT".equals(role)
                        ? "{\"responseType\":\"NO_MATCH\"}"
                        : "{}"
        );
    }
}
