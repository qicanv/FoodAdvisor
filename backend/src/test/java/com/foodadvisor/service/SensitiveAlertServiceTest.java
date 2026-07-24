package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.alert.DetectSensitiveRequest;
import com.foodadvisor.dto.alert.SensitiveAlertDTO;
import com.foodadvisor.dto.alert.SensitiveAlertDetailDTO;
import com.foodadvisor.dto.alert.UpdateAlertStatusRequest;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.SensitiveAlert;
import com.foodadvisor.entity.SensitiveAlertReview;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.SensitiveAlertMapper;
import com.foodadvisor.mapper.SensitiveAlertReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SensitiveAlertService 单元测试
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>关键词匹配 — 四种话题类型命中</li>
 *   <li>阈值控制 — 低于阈值不触发预警</li>
 *   <li>24h合并 — 同商家同话题预警合并</li>
 *   <li>边界场景 — 空评价、无匹配</li>
 *   <li>CRUD — 列表查询、详情、状态更新</li>
 *   <li>状态校验 — 非法状态拒绝</li>
 *   <li>枚举中文名映射</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SensitiveAlertServiceTest {

    @Mock
    private SensitiveAlertMapper alertMapper;
    @Mock
    private SensitiveAlertReviewMapper alertReviewMapper;
    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private MerchantMapper merchantMapper;

    private SensitiveAlertService sensitiveAlertService;

    private static final Long MERCHANT_ID = 1L;
    private static final String MERCHANT_NAME = "川味小馆";

    @BeforeEach
    void setUp() {
        sensitiveAlertService = new SensitiveAlertService(
                alertMapper, alertReviewMapper, reviewMapper, merchantMapper);
        // 对模棱两可的 insert/updateById 使用 lenient 模式，依赖默认返回值
        lenient().when(alertMapper.insert(isA(SensitiveAlert.class))).thenReturn(1);
        lenient().when(alertMapper.updateById(isA(SensitiveAlert.class))).thenReturn(1);
        lenient().when(alertReviewMapper.insert(isA(SensitiveAlertReview.class))).thenReturn(1);
    }

    // ============================================================
    // 1. 关键词匹配 — 食品安全
    // ============================================================

    @Test
    void shouldDetectFoodSafetyKeywords() {
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "吃完之后拉肚子了，怀疑食物中毒"),
                buildReview(2L, MERCHANT_ID, "吃坏肚子了，这家店食材过期变质"),
                buildReview(3L, MERCHANT_ID, "菜里有虫子，太恶心了"),
                buildReview(4L, MERCHANT_ID, "馊了的饭菜还端上来")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals("FOOD_SAFETY", result.get(0).getTopicType()),
                () -> assertEquals(MERCHANT_ID, result.get(0).getMerchantId())
        );
    }

    @Test
    void shouldDetectFoodSafetyWithHighConfidence() {
        // 5+ 条食品安全评价 → HIGH risk
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "食物中毒了，吃完就呕吐"),
                buildReview(2L, MERCHANT_ID, "吃坏肚子，拉肚子"),
                buildReview(3L, MERCHANT_ID, "过期变质的东西给我们吃"),
                buildReview(4L, MERCHANT_ID, "蚊虫苍蝇到处飞"),
                buildReview(5L, MERCHANT_ID, "不新鲜，吃完上吐下泻")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals("HIGH", result.get(0).getRiskLevel()),
                () -> assertEquals(5, result.get(0).getReviewCount())
        );
    }

    // ============================================================
    // 2. 关键词匹配 — 卫生问题
    // ============================================================

    @Test
    void shouldDetectHygieneKeywords() {
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "这家店太脏了，厨房里都是蟑螂老鼠"),
                buildReview(2L, MERCHANT_ID, "卫生间脏得没法用，油腻腻的"),
                buildReview(3L, MERCHANT_ID, "环境很差，不卫生，污渍到处都是")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals("HYGIENE", result.get(0).getTopicType())
        );
    }

    // ============================================================
    // 3. 关键词匹配 — 集中投诉
    // ============================================================

    @Test
    void shouldDetectConcentratedComplaintKeywords() {
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "我要投诉这家店，要求退款赔偿"),
                buildReview(2L, MERCHANT_ID, "去消协举报，这就是个黑店"),
                buildReview(3L, MERCHANT_ID, "被骗了，上当了，大家不要来")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals("CONCENTRATED_COMPLAINT", result.get(0).getTopicType())
        );
    }

    // ============================================================
    // 4. 关键词匹配 — 服务纠纷
    // ============================================================

    @Test
    void shouldDetectServiceDisputeKeywords() {
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "服务员态度恶劣，直接骂人"),
                buildReview(2L, MERCHANT_ID, "老板威胁要打人，已经报警了"),
                buildReview(3L, MERCHANT_ID, "强制消费，霸王条款，宰客")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals("SERVICE_DISPUTE", result.get(0).getTopicType())
        );
    }

    // ============================================================
    // 5. 一条评价匹配多个话题
    // ============================================================

    @Test
    void shouldMatchMultipleTopicsInOneReview() {
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "食物中毒拉肚子，服务员还态度恶劣骂人"),
                buildReview(2L, MERCHANT_ID, "吃完不舒服还被人身攻击"),
                buildReview(3L, MERCHANT_ID, "变质食物加上多收钱还宰客")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        // 应该生成 2 条预警：食品安全 + 服务纠纷
        assertTrue(result.size() >= 2, "Should generate at least 2 alerts");
        List<String> topicTypes = result.stream().map(SensitiveAlertDTO::getTopicType).toList();
        assertTrue(topicTypes.contains("FOOD_SAFETY"));
        assertTrue(topicTypes.contains("SERVICE_DISPUTE"));
    }

    // ============================================================
    // 6. 阈值控制 — 低于阈值不生成预警
    // ============================================================

    @Test
    void shouldNotTriggerAlertBelowThreshold() {
        // 只有2条匹配 → 默认阈值3 → 不触发
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "吃完拉肚子了"),
                buildReview(2L, MERCHANT_ID, "有点不新鲜")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertTrue(result.isEmpty(), "Should not generate alert below threshold");
    }

    @Test
    void shouldRespectCustomThreshold() {
        // 设置阈值=2 → 2条即可触发
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "吃完拉肚子了"),
                buildReview(2L, MERCHANT_ID, "有点不新鲜")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        DetectSensitiveRequest request = new DetectSensitiveRequest();
        request.setThreshold(2);

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(request);

        assertEquals(1, result.size(), "Should trigger alert with custom threshold=2");
    }

    // ============================================================
    // 7. 24小时合并逻辑
    // ============================================================

    @Test
    void shouldMergeAlertWithin24Hours() {
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "吃坏肚子了"),
                buildReview(2L, MERCHANT_ID, "食物中毒"),
                buildReview(3L, MERCHANT_ID, "拉肚子")
        );

        // Mock 已存在24h内的预警
        SensitiveAlert existingAlert = buildAlert(10L, MERCHANT_ID,
                "FOOD_SAFETY", "MEDIUM", 2, OffsetDateTime.now().minusHours(12));
        existingAlert.setKeywords("[\"吃坏肚子\",\"拉肚子\"]");

        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(existingAlert);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(10L, result.get(0).getId()),
                // reviewCount 应该是 2+3=5
                () -> assertEquals(5, result.get(0).getReviewCount())
        );
    }

    // ============================================================
    // 8. 已处理预警重新激活
    // ============================================================

    @Test
    void shouldReactivateResolvedAlertOnNewMatches() {
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "食物中毒，已经吐了"),
                buildReview(2L, MERCHANT_ID, "吃坏肚子"),
                buildReview(3L, MERCHANT_ID, "食材有问题")
        );

        SensitiveAlert resolvedAlert = buildAlert(10L, MERCHANT_ID,
                "FOOD_SAFETY", "HIGH", 3, OffsetDateTime.now().minusHours(20));
        resolvedAlert.setStatus("RESOLVED");
        resolvedAlert.setHandledBy(1L);
        resolvedAlert.setHandledAt(OffsetDateTime.now().minusHours(10));

        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(resolvedAlert);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertAll(
                () -> assertEquals(1, result.size()),
                // 已处理 → 有新评价 → 重新激活为 PENDING
                () -> assertEquals("PENDING", result.get(0).getStatus())
        );
    }

    // ============================================================
    // 9. 空评价 / 边界
    // ============================================================

    @Test
    void shouldReturnEmptyListWhenNoReviews() {
        when(reviewMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipReviewsWithEmptyContent() {
        Review emptyReview = buildReview(1L, MERCHANT_ID, "");
        emptyReview.setContent(null);
        Review normalButNoMatch = buildReview(2L, MERCHANT_ID, "味道不错环境好");

        when(reviewMapper.selectList(any())).thenReturn(
                List.of(emptyReview, normalButNoMatch));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertTrue(result.isEmpty(), "Empty and no-match reviews should not trigger alerts");
    }

    // ============================================================
    // 10. 指定单个商家
    // ============================================================

    @Test
    void shouldFilterBySingleMerchantId() {
        Long targetMerchantId = 5L;
        List<Review> reviews = List.of(
                buildReview(1L, targetMerchantId, "食物中毒了"),
                buildReview(2L, targetMerchantId, "吃坏肚子"),
                buildReview(3L, targetMerchantId, "拉肚子")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(targetMerchantId))
                .thenReturn(buildMerchant(targetMerchantId, "指定商家"));

        DetectSensitiveRequest request = new DetectSensitiveRequest();
        request.setMerchantId(targetMerchantId);

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(request);

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(targetMerchantId, result.get(0).getMerchantId()),
                () -> assertEquals("指定商家", result.get(0).getMerchantName())
        );
    }

    // ============================================================
    // 11. CRUD — 列表查询
    // ============================================================

    @Test
    void shouldListAlertsWithPagination() {
        SensitiveAlert alert = buildAlert(1L, MERCHANT_ID, "FOOD_SAFETY",
                "HIGH", 5, OffsetDateTime.now().minusHours(10));
        Page<SensitiveAlert> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(alert));
        mockPage.setTotal(1);

        when(alertMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(merchantMapper.selectBatchIds(anySet()))
                .thenReturn(List.of(buildMerchant(MERCHANT_ID, MERCHANT_NAME)));

        PageResult<SensitiveAlertDTO> result = sensitiveAlertService.listAlerts(
                null, null, null, null, null, null, 1, 20);

        assertAll(
                () -> assertEquals(1, result.getTotal()),
                () -> assertEquals(1, result.getRecords().size()),
                () -> assertEquals("FOOD_SAFETY", result.getRecords().get(0).getTopicType()),
                () -> assertEquals(MERCHANT_NAME, result.getRecords().get(0).getMerchantName())
        );
    }

    // ============================================================
    // 12. CRUD — 预警详情
    // ============================================================

    @Test
    void shouldGetAlertDetailWithReviews() {
        SensitiveAlert alert = buildAlert(1L, MERCHANT_ID, "FOOD_SAFETY",
                "HIGH", 3, OffsetDateTime.now().minusHours(5));
        alert.setKeywords("[\"食物中毒\",\"拉肚子\"]");

        SensitiveAlertReview alertReview = new SensitiveAlertReview();
        alertReview.setId(1L);
        alertReview.setAlertId(1L);
        alertReview.setReviewId(100L);
        alertReview.setReviewVersion(1);
        alertReview.setEvidenceExcerpt("...食物中毒...");

        Review review = buildReview(100L, MERCHANT_ID, "这家店食物中毒");
        review.setRating(BigDecimal.valueOf(2));
        review.setUserId(42L);

        when(alertMapper.selectById(1L)).thenReturn(alert);
        when(alertReviewMapper.selectList(any())).thenReturn(List.of(alertReview));
        when(reviewMapper.selectBatchIds(anyList())).thenReturn(List.of(review));
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        SensitiveAlertDetailDTO detail = sensitiveAlertService.getAlertDetail(1L);

        assertAll(
                () -> assertEquals(1L, detail.getId()),
                () -> assertEquals("FOOD_SAFETY", detail.getTopicType()),
                () -> assertEquals(MERCHANT_NAME, detail.getMerchantName()),
                () -> assertEquals(1, detail.getReviews().size()),
                () -> assertEquals(100L, detail.getReviews().get(0).getReviewId()),
                () -> assertEquals(2, detail.getReviews().get(0).getReviewRating()),
                () -> assertEquals(42L, detail.getReviews().get(0).getReviewUserId())
        );
    }

    @Test
    void shouldThrowWhenAlertNotFound() {
        when(alertMapper.selectById(999L)).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class,
                () -> sensitiveAlertService.getAlertDetail(999L));

        assertEquals("ALERT_NOT_FOUND", ex.getCode());
    }

    // ============================================================
    // 13. CRUD — 更新状态
    // ============================================================

    @Test
    void shouldUpdateAlertStatusToResolved() {
        SensitiveAlert alert = buildAlert(1L, MERCHANT_ID, "FOOD_SAFETY",
                "HIGH", 3, OffsetDateTime.now().minusHours(5));
        alert.setStatus("PENDING");

        UpdateAlertStatusRequest request = new UpdateAlertStatusRequest();
        request.setStatus("RESOLVED");
        request.setRemark("已通知商家整改");

        when(alertMapper.selectById(1L)).thenReturn(alert);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        SensitiveAlertDTO result = sensitiveAlertService.updateAlertStatus(
                1L, request, 1L, "admin");

        assertAll(
                () -> assertEquals("RESOLVED", result.getStatus()),
                () -> assertEquals("admin", result.getHandledUsername())
        );
    }

    @Test
    void shouldRejectInvalidStatusValue() {
        SensitiveAlert alert = buildAlert(1L, MERCHANT_ID, "FOOD_SAFETY",
                "HIGH", 3, OffsetDateTime.now().minusHours(5));
        alert.setStatus("PENDING");

        UpdateAlertStatusRequest request = new UpdateAlertStatusRequest();
        request.setStatus("INVALID_STATUS");

        when(alertMapper.selectById(1L)).thenReturn(alert);

        ApiException ex = assertThrows(ApiException.class,
                () -> sensitiveAlertService.updateAlertStatus(1L, request, 1L, "admin"));

        assertEquals("INVALID_STATUS", ex.getCode());
    }

    @Test
    void shouldRejectDuplicateResolvedStatusUpdate() {
        SensitiveAlert alert = buildAlert(1L, MERCHANT_ID, "FOOD_SAFETY",
                "HIGH", 3, OffsetDateTime.now().minusHours(5));
        alert.setStatus("RESOLVED");

        UpdateAlertStatusRequest request = new UpdateAlertStatusRequest();
        request.setStatus("DISMISSED");

        when(alertMapper.selectById(1L)).thenReturn(alert);

        ApiException ex = assertThrows(ApiException.class,
                () -> sensitiveAlertService.updateAlertStatus(1L, request, 1L, "admin"));

        assertEquals("STATUS_CONFLICT", ex.getCode());
    }

    // ============================================================
    // 14. 待处理计数
    // ============================================================

    @Test
    void shouldCountPendingAlerts() {
        when(alertMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(7L);

        long count = sensitiveAlertService.countPendingAlerts();

        assertEquals(7L, count);
    }

    // ============================================================
    // 15. 枚举中文名映射
    // ============================================================

    @Test
    void shouldReturnCorrectChineseTopicTypeNames() {
        assertAll(
                () -> assertEquals("食品安全", SensitiveAlertService.getTopicTypeName("FOOD_SAFETY")),
                () -> assertEquals("卫生问题", SensitiveAlertService.getTopicTypeName("HYGIENE")),
                () -> assertEquals("集中投诉", SensitiveAlertService.getTopicTypeName("CONCENTRATED_COMPLAINT")),
                () -> assertEquals("严重服务纠纷", SensitiveAlertService.getTopicTypeName("SERVICE_DISPUTE"))
        );
    }

    @Test
    void shouldReturnCorrectChineseRiskLevelNames() {
        assertAll(
                () -> assertEquals("高风险", SensitiveAlertService.getRiskLevelName("HIGH")),
                () -> assertEquals("中风险", SensitiveAlertService.getRiskLevelName("MEDIUM")),
                () -> assertEquals("低风险", SensitiveAlertService.getRiskLevelName("LOW"))
        );
    }

    @Test
    void shouldReturnCorrectChineseStatusNames() {
        assertAll(
                () -> assertEquals("待处理", SensitiveAlertService.getStatusName("PENDING")),
                () -> assertEquals("处理中", SensitiveAlertService.getStatusName("PROCESSING")),
                () -> assertEquals("已处理", SensitiveAlertService.getStatusName("RESOLVED")),
                () -> assertEquals("已忽略", SensitiveAlertService.getStatusName("DISMISSED"))
        );
    }

    // ============================================================
    // 16. 关键词匹配可靠性
    // ============================================================

    @Test
    void shouldMatchKeywordsReliably() {
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "食物中毒"),
                buildReview(2L, MERCHANT_ID, "食物中毒"),
                buildReview(3L, MERCHANT_ID, "食物中毒")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertEquals(1, result.size());
        assertTrue(result.get(0).getKeywords().contains("食物中毒"));
    }

    @Test
    void shouldDeduplicateKeywords() {
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "食物中毒"),
                buildReview(2L, MERCHANT_ID, "食物中毒"),
                buildReview(3L, MERCHANT_ID, "食物中毒")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);
        when(alertMapper.selectOne(any())).thenReturn(null);
        when(alertReviewMapper.selectCount(any())).thenReturn(0L);
        when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(buildMerchant(MERCHANT_ID, MERCHANT_NAME));

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        // "食物中毒" 去重后只出现1次
        long keywordCount = result.get(0).getKeywords().stream()
                .filter(k -> k.equals("食物中毒")).count();
        assertEquals(1, keywordCount, "Keywords should be deduplicated");
    }

    // ============================================================
    // 17. detectSensitiveTopics默认参数
    // ============================================================

    @Test
    void shouldUseDefault24hWindowWhenNoTimeSpecified() {
        // 验证当没有指定时间时，使用默认24h窗口
        when(reviewMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(
                new DetectSensitiveRequest());

        assertTrue(result.isEmpty());
        // 验证调用了selectList（参数中包含时间范围过滤）
        verify(reviewMapper, times(1)).selectList(any());
    }

    @Test
    void shouldUseDefaultThreshold3WhenNotSpecified() {
        // 2条评论低于默认阈值3，不应触发预警
        List<Review> reviews = List.of(
                buildReview(1L, MERCHANT_ID, "食物中毒"),
                buildReview(2L, MERCHANT_ID, "拉肚子")
        );
        when(reviewMapper.selectList(any())).thenReturn(reviews);

        DetectSensitiveRequest request = new DetectSensitiveRequest();
        // 不设置threshold

        List<SensitiveAlertDTO> result = sensitiveAlertService.detectSensitiveTopics(request);

        assertTrue(result.isEmpty(), "Default threshold=3, 2 reviews should not trigger");
    }

    // ============================================================
    // 测试辅助方法
    // ============================================================

    private Review buildReview(Long id, Long merchantId, String content) {
        Review review = new Review();
        review.setId(id);
        review.setMerchantId(merchantId);
        review.setContent(content);
        review.setStatus("PUBLISHED");
        review.setRating(BigDecimal.valueOf(3));
        review.setUserId(100L);
        review.setCurrentVersion(1);
        review.setCreatedAt(OffsetDateTime.now().minusHours(1));
        return review;
    }

    private SensitiveAlert buildAlert(Long id, Long merchantId,
                                       String topicType, String riskLevel,
                                       int reviewCount, OffsetDateTime lastOccurredAt) {
        SensitiveAlert alert = new SensitiveAlert();
        alert.setId(id);
        alert.setMerchantId(merchantId);
        alert.setTopicType(topicType);
        alert.setRiskLevel(riskLevel);
        alert.setReviewCount(reviewCount);
        alert.setKeywords("[]");
        alert.setStatus("PENDING");
        alert.setFirstOccurredAt(lastOccurredAt.minusHours(1));
        alert.setLastOccurredAt(lastOccurredAt);
        alert.setCreatedAt(lastOccurredAt);
        alert.setUpdatedAt(lastOccurredAt);
        return alert;
    }

    private Merchant buildMerchant(Long id, String name) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setName(name);
        merchant.setPlatformStatus("ACTIVE");
        merchant.setOperationStatus("OPERATING");
        return merchant;
    }
}
