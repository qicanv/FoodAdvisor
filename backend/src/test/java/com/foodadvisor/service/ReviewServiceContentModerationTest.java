package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.foodadvisor.dto.review.ReviewSubmitRequest;
import com.foodadvisor.dto.review.ReviewSubmitResponse;
import com.foodadvisor.dto.violation.ViolationTextResult;
import com.foodadvisor.entity.AuditLog;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewVersion;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.AuditLogMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.ReviewAnalysisMapper;
import com.foodadvisor.mapper.ReviewImageMapper;
import com.foodadvisor.mapper.ReviewIssueCategoryMapper;
import com.foodadvisor.mapper.ReviewIssueRelationMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.ReviewTagMapper;
import com.foodadvisor.mapper.ReviewTagRelationMapper;
import com.foodadvisor.mapper.ReviewVersionMapper;
import com.foodadvisor.mapper.ReviewReplyMapper;
import com.foodadvisor.storage.ReviewImageStorageService;
import com.foodadvisor.util.SensitiveLogSanitizer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReviewServiceContentModerationTest {

    private static final Long USER_ID = 7L;
    private static final Long MERCHANT_ID = 11L;
    private static final Long REVIEW_ID = 101L;
    private static final String SENSITIVE_WORD = "\u66b4\u6050";

    @Mock
    private ReviewMapper reviewMapper;
    @Mock
    private ReviewAnalysisMapper analysisMapper;
    @Mock
    private ReviewTagRelationMapper tagRelationMapper;
    @Mock
    private ReviewTagMapper tagMapper;
    @Mock
    private ReviewImageMapper imageMapper;
    @Mock
    private ReviewVersionMapper versionMapper;
    @Mock
    private MerchantMapper merchantMapper;
    @Mock
    private ReviewImageStorageService imageStorageService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private com.foodadvisor.mapper.UserMapper userMapper;
    @Mock
    private ReviewIssueRelationMapper issueRelationMapper;
    @Mock
    private ReviewIssueCategoryMapper issueCategoryMapper;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ReviewReplyMapper replyMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ViolationTextService violationTextService;

    private ReviewService reviewService;

    @BeforeAll
    static void initMyBatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Merchant.class
        );
    }

    @BeforeEach
    void setUp() {
        reviewService = createService(auditLogService);
        stubCommonPersistence();
    }

    @Test
    void submitReviewWritesInfoContentModerationLog() {
        ReviewSubmitResponse response = reviewService.submitOriginalReview(
                USER_ID,
                MERCHANT_ID,
                request("Fresh noodles and soup tasted excellent"),
                List.of()
        );

        AuditLog auditLog = capturedAuditLog();

        assertAll(
                () -> assertEquals(REVIEW_ID, response.getId()),
                () -> assertEquals("PUBLISHED", response.getStatus()),
                () -> assertEquals("APPROVED", response.getModerationStatus()),
                () -> assertEquals("LOW", response.getRiskLevel()),
                () -> assertEquals("CONTENT_MODERATION", auditLog.getOperationType()),
                () -> assertEquals("REVIEW_MODERATION", auditLog.getModule()),
                () -> assertEquals("INFO", auditLog.getLevel()),
                () -> assertEquals("SUCCESS", auditLog.getResult()),
                () -> assertEquals("REVIEW", auditLog.getObjectType()),
                () -> assertEquals(String.valueOf(REVIEW_ID), auditLog.getObjectId()),
                () -> assertEquals(USER_ID, auditLog.getOperatorUserId()),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"operation\":\"AUTO_MODERATE_REVIEW_CREATE\"")),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"merchantId\":" + MERCHANT_ID)),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"executor\":\"VIOLATION_TEXT_SERVICE\""))
        );
        verify(auditLogService, times(1)).recordSafely(any(AuditLog.class));
    }

    @Test
    void submitReviewWithRuleHitWritesWarnWithoutSensitiveContent() {
        String content = "Risk review text " + SENSITIVE_WORD + " still long";

        ReviewSubmitResponse response = reviewService.submitOriginalReview(
                USER_ID,
                MERCHANT_ID,
                request(content),
                List.of()
        );

        AuditLog auditLog = capturedAuditLog();

        assertAll(
                () -> assertEquals("PENDING", response.getStatus()),
                () -> assertEquals("PENDING", response.getModerationStatus()),
                () -> assertEquals("HIGH", response.getRiskLevel()),
                () -> assertEquals("WARN", auditLog.getLevel()),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"violation\":true")),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"riskScore\":80")),
                () -> assertFalse(auditLog.getMetadata().contains(content)),
                () -> assertFalse(auditLog.getMetadata().contains(SENSITIVE_WORD))
        );
        verify(auditLogService, times(1)).recordSafely(any(AuditLog.class));
    }

    @Test
    void editReviewWritesBeforeAndAfterModerationState() {
        lenient().when(reviewMapper.selectById(REVIEW_ID))
                .thenReturn(existingReview());

        ReviewSubmitResponse response = reviewService.editReview(
                USER_ID,
                REVIEW_ID,
                request("Edited review text " + SENSITIVE_WORD + " long"),
                List.of()
        );

        AuditLog auditLog = capturedAuditLog();

        assertAll(
                () -> assertEquals(REVIEW_ID, response.getId()),
                () -> assertEquals("PENDING", response.getStatus()),
                () -> assertEquals("WARN", auditLog.getLevel()),
                () -> assertEquals(String.valueOf(REVIEW_ID), auditLog.getObjectId()),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"operation\":\"AUTO_MODERATE_REVIEW_UPDATE\"")),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"beforeStatus\":\"PUBLISHED\"")),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"afterStatus\":\"PENDING\"")),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"beforeModerationStatus\":\"APPROVED\"")),
                () -> assertTrue(auditLog.getMetadata()
                        .contains("\"riskLevel\":\"HIGH\""))
        );
        verify(auditLogService, times(1)).recordSafely(any(AuditLog.class));
    }

    @Test
    void auditWriteFailureDoesNotAffectSubmit() {
        reviewService = createService(failingAuditLogService());
        stubCommonPersistence();

        ReviewSubmitResponse response = reviewService.submitOriginalReview(
                USER_ID,
                MERCHANT_ID,
                request("Fresh rice bowl with balanced flavor"),
                List.of()
        );

        assertAll(
                () -> assertEquals(REVIEW_ID, response.getId()),
                () -> assertEquals("PUBLISHED", response.getStatus()),
                () -> assertEquals("APPROVED", response.getModerationStatus())
        );
    }

    @Test
    void auditWriteFailureDoesNotAffectEdit() {
        reviewService = createService(failingAuditLogService());
        stubCommonPersistence();
        lenient().when(reviewMapper.selectById(REVIEW_ID))
                .thenReturn(existingReview());

        ReviewSubmitResponse response = reviewService.editReview(
                USER_ID,
                REVIEW_ID,
                request("Edited rice bowl with balanced flavor"),
                List.of()
        );

        assertAll(
                () -> assertEquals(REVIEW_ID, response.getId()),
                () -> assertEquals("PUBLISHED", response.getStatus()),
                () -> assertEquals("APPROVED", response.getModerationStatus())
        );
    }

    @Test
    void updateByIdFalseDoesNotWriteContentModerationSuccessLog() {
        lenient().when(reviewMapper.selectById(REVIEW_ID))
                .thenReturn(existingReview());
        lenient().when(reviewMapper.updateById(any(Review.class)))
                .thenReturn(0);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> reviewService.editReview(
                        USER_ID,
                        REVIEW_ID,
                        request("Edited rice bowl with balanced flavor"),
                        List.of()
                )
        );

        assertEquals("REVIEW_UPDATE_FAILED", exception.getCode());
        verify(auditLogService, never()).recordSafely(any(AuditLog.class));
    }

    @Test
    void imageProcessingFailureDoesNotWriteContentModerationSuccessLog() {
        ReviewSubmitRequest request =
                request("Fresh noodles and soup tasted excellent");
        request.setKeepImageIds(List.of(999L));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> reviewService.submitOriginalReview(
                        USER_ID,
                        MERCHANT_ID,
                        request,
                        List.of()
                )
        );

        assertEquals("REVIEW_IMAGE_KEEP_INVALID", exception.getCode());
        verify(auditLogService, never()).recordSafely(any(AuditLog.class));
    }

    private ReviewService createService(AuditLogService auditLogService) {
        ReviewService service = new ReviewService(
                analysisMapper,
                tagRelationMapper,
                tagMapper,
                imageMapper,
                versionMapper,
                merchantMapper,
                imageStorageService,
                jdbcTemplate,
                userMapper,
                issueRelationMapper,
                issueCategoryMapper,
                replyMapper,
                notificationService,
                auditLogService,
                violationTextService
        );
        ReflectionTestUtils.setField(service, "baseMapper", reviewMapper);
        return service;
    }

    private void stubCommonPersistence() {
        lenient().when(merchantMapper.selectById(MERCHANT_ID))
                .thenReturn(merchant());
        lenient().when(reviewMapper.selectList(any()))
                .thenReturn(List.of());
        lenient().when(reviewMapper.insert(any(Review.class)))
                .thenAnswer(invocation -> {
                    Review review = invocation.getArgument(0);
                    review.setId(REVIEW_ID);
                    return 1;
                });
        lenient().when(reviewMapper.updateById(any(Review.class)))
                .thenReturn(1);
        lenient().when(imageMapper.selectList(any()))
                .thenReturn(List.of());
        lenient().when(versionMapper.insert(any(ReviewVersion.class)))
                .thenReturn(1);
        lenient().when(analysisMapper.selectOne(any()))
                .thenReturn(null);
        lenient().when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(1);
        lenient().when(merchantMapper.update(any(), any()))
                .thenReturn(1);

        // ViolationTextService stubs:
        // detectViolation responds based on content keywords
        lenient().when(violationTextService.detectViolation(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String content = invocation.getArgument(0);
                    if (!isBlank(content) && content.contains(SENSITIVE_WORD)) {
                        return ViolationTextResult.builder()
                                .violation(true)
                                .riskLevel("HIGH")
                                .riskScore(80)
                                .riskType(null)
                                .matchedRules(List.of())
                                .detectionStatus("SUCCESS")
                                .modelName("mock-model")
                                .build();
                    }
                    return ViolationTextResult.builder()
                            .violation(false)
                            .riskLevel("LOW")
                            .riskScore(5)
                            .riskType(null)
                            .matchedRules(List.of())
                            .detectionStatus("SUCCESS")
                            .modelName("mock-model")
                            .build();
                });

        // saveRecord is lenient (no-op for most tests)
        lenient().doNothing().when(violationTextService)
                .saveRecord(anyString(), anyString(), any(), any(), anyString(), any());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private AuditLog capturedAuditLog() {
        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogService).recordSafely(captor.capture());
        AuditLog auditLog = captor.getValue();
        assertNotNull(auditLog.getMetadata());
        return auditLog;
    }

    private AuditLogService failingAuditLogService() {
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        doThrow(new RuntimeException("password=plain-secret token=secret-token"))
                .when(auditLogMapper)
                .insert(any(AuditLog.class));
        return new AuditLogService(
                auditLogMapper,
                new SensitiveLogSanitizer()
        );
    }

    private ReviewSubmitRequest request(String content) {
        ReviewSubmitRequest request = new ReviewSubmitRequest();
        request.setContent(content);
        request.setRating(5);
        request.setTasteRating(5);
        request.setEnvironmentRating(4);
        request.setServiceRating(5);
        request.setAverageSpend(BigDecimal.valueOf(66));
        request.setKeepImageIds(List.of());
        return request;
    }

    private Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId(MERCHANT_ID);
        merchant.setName("Test Merchant");
        merchant.setPlatformStatus("ACTIVE");
        merchant.setOperationStatus("OPERATING");
        return merchant;
    }

    private Review existingReview() {
        Review review = new Review();
        review.setId(REVIEW_ID);
        review.setUserId(USER_ID);
        review.setMerchantId(MERCHANT_ID);
        review.setReviewType("ORIGINAL");
        review.setSource("SYSTEM");
        review.setContent("Existing published review");
        review.setRating(BigDecimal.valueOf(4));
        review.setTasteRating(BigDecimal.valueOf(4));
        review.setEnvironmentRating(BigDecimal.valueOf(4));
        review.setServiceRating(BigDecimal.valueOf(4));
        review.setCurrentVersion(1);
        review.setStatus("PUBLISHED");
        review.setModerationStatus("APPROVED");
        review.setRiskLevel("LOW");
        review.setPublishedAt(OffsetDateTime.now());
        return review;
    }
}
