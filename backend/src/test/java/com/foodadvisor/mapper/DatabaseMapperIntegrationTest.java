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
import com.foodadvisor.entity.MerchantHighlight;
import com.foodadvisor.entity.MerchantHighlightEvidence;

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
    @Autowired
    private MerchantHighlightMapper merchantHighlightMapper;
    @Autowired
    private MerchantHighlightEvidenceMapper merchantHighlightEvidenceMapper;

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

    @Test
    @Transactional
    void highlightMappersBatchQueryRealPostgresWithStableIsolation() {
        Long merchantA = insertMerchant("HIGHLIGHT_MAPPER_A");
        Long merchantB = insertMerchant("HIGHLIGHT_MAPPER_B");
        Long excludedMerchant = insertMerchant("HIGHLIGHT_MAPPER_EXCLUDED");
        Long reviewA1 = insertReview(merchantA, "A公开评价一，真实测试内容");
        Long reviewA2 = insertReview(merchantA, "A公开评价二，真实测试内容");
        Long reviewB = insertReview(merchantB, "B公开评价，真实测试内容");

        Long aLower = insertHighlight(merchantA, "ACTIVE",
                "环境舒适", 2, "0.80");
        Long excludedStatus = insertHighlight(merchantA, "OUTDATED",
                "过期亮点", 99, "1.00");
        Long bActive = insertHighlight(merchantB, "ACTIVE",
                "服务快捷", 3, "0.90");
        Long excludedMerchantHighlight = insertHighlight(
                excludedMerchant, "ACTIVE", "其他商家亮点", 20, "0.99");
        Long aHigher = insertHighlight(merchantA, "ACTIVE",
                "环境安静", 5, "0.95");

        insertHighlightEvidence(aLower, reviewA1, "A证据一");
        insertHighlightEvidence(excludedStatus, reviewA2, "过期证据");
        insertHighlightEvidence(bActive, reviewB, "B证据");
        insertHighlightEvidence(excludedMerchantHighlight, reviewB, "其他证据");
        insertHighlightEvidence(aHigher, reviewA2, "A证据二");

        List<MerchantHighlight> highlights =
                merchantHighlightMapper.selectActiveByMerchantIds(
                        List.of(merchantB, merchantA));

        assertEquals(List.of(aHigher, aLower, bActive),
                highlights.stream().map(MerchantHighlight::getId).toList());
        assertEquals(Set.of(merchantA, merchantB),
                highlights.stream().map(MerchantHighlight::getMerchantId)
                        .collect(Collectors.toSet()));
        assertTrue(highlights.stream().allMatch(value ->
                "ACTIVE".equals(value.getStatus())));

        List<MerchantHighlightEvidence> evidences =
                merchantHighlightEvidenceMapper.selectByHighlightIds(
                        List.of(aHigher, bActive));

        assertEquals(2, evidences.size());
        assertEquals(Set.of(aHigher, bActive),
                evidences.stream()
                        .map(MerchantHighlightEvidence::getHighlightId)
                        .collect(Collectors.toSet()));
        assertEquals(Set.of(reviewA2, reviewB),
                evidences.stream()
                        .map(MerchantHighlightEvidence::getReviewId)
                        .collect(Collectors.toSet()));
        assertTrue(evidences.stream().noneMatch(value ->
                value.getHighlightId().equals(aLower)
                        || value.getHighlightId().equals(excludedStatus)
                        || value.getHighlightId().equals(excludedMerchantHighlight)));
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

    private Long insertReview(Long merchantId, String content) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO reviews (
                    merchant_id, review_type, rating, content, source,
                    current_version, status, moderation_status, risk_level,
                    published_at, created_at, updated_at
                )
                VALUES (?, 'ORIGINAL', 5, ?, 'SYSTEM',
                        1, 'PUBLISHED', 'APPROVED', 'LOW',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class, merchantId, content);
    }

    private Long insertHighlight(
            Long merchantId, String status, String title,
            int mentionCount, String positiveRatio) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO merchant_highlights (
                    merchant_id, highlight_type, title, description,
                    mention_count, positive_ratio, version, status, generated_at
                )
                VALUES (?, 'ENVIRONMENT', ?, ?, ?, ?::numeric, 1, ?,
                        CURRENT_TIMESTAMP)
                RETURNING id
                """,
                Long.class, merchantId, title, title + "描述",
                mentionCount, positiveRatio, status);
    }

    private void insertHighlightEvidence(
            Long highlightId, Long reviewId, String excerpt) {
        jdbcTemplate.update(
                """
                INSERT INTO merchant_highlight_evidences (
                    highlight_id, review_id, review_version,
                    evidence_excerpt, created_at
                )
                VALUES (?, ?, 1, ?, CURRENT_TIMESTAMP)
                """,
                highlightId, reviewId, excerpt);
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
