package com.foodadvisor.mapper;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.foodadvisor.entity.ChatMessage;
import com.foodadvisor.entity.MerchantBusinessHours;
import com.foodadvisor.entity.Dish;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Autowired
    private BusinessHoursMapper businessHoursMapper;

    @Autowired
    private DishMapper dishMapper;

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

    @Test
    @Transactional
    void businessHoursBatchQueryReturnsOnlyRequestedMerchantsInSqlOrder() {
        Long merchantA = insertMerchant("BUSINESS_HOURS_A");
        Long merchantB = insertMerchant("BUSINESS_HOURS_B");
        Long excludedMerchant = insertMerchant(
                "BUSINESS_HOURS_EXCLUDED"
        );

        insertBusinessHours(
                merchantB, 4, null, null, true, false
        );
        insertBusinessHours(
                merchantA, 1, "17:00", "22:00", false, false
        );
        insertBusinessHours(
                excludedMerchant, 1, "08:00", "23:00", false, false
        );
        insertBusinessHours(
                merchantB, 2, "09:00", "20:00", false, false
        );
        insertBusinessHours(
                merchantA, 5, "18:00", "02:00", false, true
        );
        insertBusinessHours(
                merchantA, 1, "10:00", "14:00", false, false
        );

        List<MerchantBusinessHours> rows =
                businessHoursMapper.selectByMerchantIds(
                        List.of(merchantB, merchantA)
                );

        assertEquals(5, rows.size());
        assertEquals(
                Set.of(merchantA, merchantB),
                rows.stream()
                        .map(MerchantBusinessHours::getMerchantId)
                        .collect(Collectors.toSet())
        );
        assertEquals(
                5L,
                rows.stream()
                        .map(MerchantBusinessHours::getId)
                        .distinct()
                        .count()
        );

        assertBusinessHours(
                rows.get(0),
                merchantA,
                1,
                LocalTime.of(10, 0),
                LocalTime.of(14, 0),
                false,
                false
        );
        assertBusinessHours(
                rows.get(1),
                merchantA,
                1,
                LocalTime.of(17, 0),
                LocalTime.of(22, 0),
                false,
                false
        );
        assertBusinessHours(
                rows.get(2),
                merchantA,
                5,
                LocalTime.of(18, 0),
                LocalTime.of(2, 0),
                false,
                true
        );
        assertBusinessHours(
                rows.get(3),
                merchantB,
                2,
                LocalTime.of(9, 0),
                LocalTime.of(20, 0),
                false,
                false
        );
        assertBusinessHours(
                rows.get(4),
                merchantB,
                4,
                null,
                null,
                true,
                false
        );
    }

    @Test
    @Transactional
    void dishQueriesReturnOnlyActiveNonDeletedRequestedMerchants() {
        Long merchantA = insertMerchant("DISH_MAPPER_A");
        Long merchantB = insertMerchant("DISH_MAPPER_B");
        Long excluded = insertMerchant("DISH_MAPPER_EXCLUDED");

        insertDish(merchantA, "下架烤鱼", "OFF_SHELF", false, "30", "[]");
        insertDish(merchantB, "牛肉菜", "ACTIVE", false, "48", "[\"香辣\"]");
        insertDish(merchantA, "已归档菜", "ARCHIVED", false, "20", "[]");
        insertDish(excluded, "其他商家水煮鱼", "ACTIVE", false, "66", "[]");
        insertDish(merchantA, "已删除水煮鱼", "ACTIVE", true, "50", "[]");
        insertDish(merchantA, "水煮鱼", "ACTIVE", false, "68", "[\"麻辣\"]");
        insertDish(merchantA, "价格未知菜", "ACTIVE", false, null, "[]");

        List<Dish> single =
                dishMapper.selectByMerchantId(merchantA);
        List<Dish> batch =
                dishMapper.selectActiveByMerchantIds(
                        List.of(merchantB, merchantA)
                );

        assertEquals(
                Set.of("水煮鱼", "价格未知菜"),
                single.stream().map(Dish::getName)
                        .collect(Collectors.toSet())
        );
        assertEquals(3, batch.size());
        assertEquals(
                Set.of(merchantA, merchantB),
                batch.stream().map(Dish::getMerchantId)
                        .collect(Collectors.toSet())
        );
        assertTrue(batch.stream().allMatch(dish ->
                "ACTIVE".equals(dish.getStatus())
                        && dish.getDeletedAt() == null));
        Dish beef = batch.stream()
                .filter(dish -> "牛肉菜".equals(dish.getName()))
                .findFirst()
                .orElseThrow();
        assertEquals(new BigDecimal("48.00"), beef.getPrice());
        assertEquals("[\"香辣\"]", beef.getTasteTags());
        assertEquals(merchantB, beef.getMerchantId());
        assertTrue(batch.stream().anyMatch(dish ->
                "价格未知菜".equals(dish.getName())
                        && dish.getPrice() == null));
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

    private Long insertMerchant(String marker) {
        String uniqueCode =
                marker + "_"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8);
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO merchants (
                    merchant_code, name, category, address,
                    platform_status, operation_status,
                    created_at, updated_at
                )
                VALUES (?, ?, 'MAPPER_TEST', 'mapper test address',
                        'ACTIVE', 'OPERATING',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class,
                uniqueCode,
                marker
        );
    }

    private void insertBusinessHours(
            Long merchantId,
            int dayOfWeek,
            String openTime,
            String closeTime,
            boolean isClosed,
            boolean crossesMidnight
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO merchant_business_hours (
                    merchant_id, day_of_week,
                    open_time, close_time,
                    is_closed, crosses_midnight,
                    created_at, updated_at
                )
                VALUES (?, ?, ?::time, ?::time, ?, ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                merchantId,
                dayOfWeek,
                openTime,
                closeTime,
                isClosed,
                crossesMidnight
        );
    }

    private void insertDish(
            Long merchantId,
            String name,
            String status,
            boolean deleted,
            String price,
            String tasteTags
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO dishes (
                    merchant_id, name, price, category, description,
                    taste_tags, recommended, status,
                    created_at, updated_at, deleted_at
                )
                VALUES (?, ?, ?::numeric, 'TEST', 'test description',
                        ?::jsonb, false, ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                """,
                merchantId,
                name,
                price,
                tasteTags,
                status,
                deleted
        );
    }

    private void assertBusinessHours(
            MerchantBusinessHours row,
            Long merchantId,
            int dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime,
            boolean isClosed,
            boolean crossesMidnight
    ) {
        assertEquals(merchantId, row.getMerchantId());
        assertEquals(dayOfWeek, row.getDayOfWeek());
        assertEquals(openTime, row.getOpenTime());
        assertEquals(closeTime, row.getCloseTime());
        assertEquals(isClosed, row.getIsClosed());
        assertEquals(crossesMidnight, row.getCrossesMidnight());
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
