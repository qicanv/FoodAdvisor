package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.alert.DetectSensitiveRequest;
import com.foodadvisor.dto.alert.SensitiveAlertDTO;
import com.foodadvisor.dto.alert.SensitiveAlertDetailDTO;
import com.foodadvisor.dto.alert.SensitiveAlertReviewDTO;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SensitiveAlertService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveAlertService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SensitiveAlertMapper alertMapper;
    private final SensitiveAlertReviewMapper alertReviewMapper;
    private final ReviewMapper reviewMapper;
    private final MerchantMapper merchantMapper;

    public SensitiveAlertService(
            SensitiveAlertMapper alertMapper,
            SensitiveAlertReviewMapper alertReviewMapper,
            ReviewMapper reviewMapper,
            MerchantMapper merchantMapper) {
        this.alertMapper = alertMapper;
        this.alertReviewMapper = alertReviewMapper;
        this.reviewMapper = reviewMapper;
        this.merchantMapper = merchantMapper;
    }

    // ============================================================
    // 敏感话题关键词词典
    // ============================================================

    /** 食品安全相关关键词 */
    private static final List<String> FOOD_SAFETY_KEYWORDS = List.of(
            "食物中毒", "吃坏肚子", "拉肚子", "腹泻", "呕吐", "过期",
            "变质", "馊", "发霉", "异物", "头发", "虫子", "苍蝇",
            "蟑螂", "不新鲜", "臭", "酸", "食材有问题", "食品质量",
            "吃完不舒服", "上吐下泻", "闹肚子", "食品安全"
    );

    /** 卫生问题相关关键词 */
    private static final List<String> HYGIENE_KEYWORDS = List.of(
            "不卫生", "脏", "蟑螂", "老鼠", "苍蝇", "虫子",
            "厨房脏", "卫生间脏", "环境差", "油腻", "污渍",
            "不干净", "卫生差", "卫生堪忧", "洁癖", "恶心",
            "洗碗", "消毒", "手套", "口罩"
    );

    /** 集中投诉相关关键词 */
    private static final List<String> CONCENTRATED_COMPLAINT_KEYWORDS = List.of(
            "投诉", "举报", "维权", "消协", "工商", "食药监",
            "退款", "赔偿", "道歉", "敷衍", "不理", "态度差",
            "被骗", "上当", "坑", "黑店", "奸商"
    );

    /** 严重服务纠纷相关关键词 */
    private static final List<String> SERVICE_DISPUTE_KEYWORDS = List.of(
            "态度恶劣", "骂人", "打人", "报警", "争执", "争吵",
            "冲突", "歧视", "侮辱", "威胁", "推搡", "动手",
            "服务员骂", "老板骂", "人身攻击", "打架", "纠纷",
            "强制消费", "霸王条款", "多收钱", "乱收费", "宰客"
    );

    /** 话题类型 → 关键词列表映射 */
    private static final Map<String, List<String>> TOPIC_KEYWORD_MAP = Map.of(
            "FOOD_SAFETY", FOOD_SAFETY_KEYWORDS,
            "HYGIENE", HYGIENE_KEYWORDS,
            "CONCENTRATED_COMPLAINT", CONCENTRATED_COMPLAINT_KEYWORDS,
            "SERVICE_DISPUTE", SERVICE_DISPUTE_KEYWORDS
    );

    // ============================================================
    // 敏感话题检测
    // ============================================================

    /**
     * 执行敏感话题检测
     * 分析近期评价内容，当同一商家集中出现敏感内容时自动生成预警记录
     */
    @Transactional
    public List<SensitiveAlertDTO> detectSensitiveTopics(DetectSensitiveRequest request) {
        OffsetDateTime startTime = request.getStartTime();
        OffsetDateTime endTime = request.getEndTime();
        int threshold = request.getThreshold() != null ? request.getThreshold() : 3;

        // 默认分析最近24小时
        OffsetDateTime now = OffsetDateTime.now();
        if (startTime == null) {
            startTime = now.minusHours(24);
        }
        if (endTime == null) {
            endTime = now;
        }

        log.info("开始敏感话题检测: startTime={}, endTime={}, threshold={}, merchantId={}",
                startTime, endTime, threshold, request.getMerchantId());

        // 1. 获取时间范围内的评价
        LambdaQueryWrapper<Review> reviewWrapper = new LambdaQueryWrapper<Review>()
                .ge(Review::getCreatedAt, startTime)
                .le(Review::getCreatedAt, endTime)
                .eq(Review::getStatus, "PUBLISHED")
                .isNotNull(Review::getContent)
                .ne(Review::getContent, "");

        if (request.getMerchantId() != null) {
            reviewWrapper.eq(Review::getMerchantId, request.getMerchantId());
        }

        List<Review> reviews = reviewMapper.selectList(reviewWrapper);
        log.info("获取到 {} 条待分析评价", reviews.size());

        if (reviews.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 对每条评价匹配敏感话题
        // merchantId → topicType → list of (review, matchedKeywords)
        Map<Long, Map<String, List<ReviewMatch>>> matchMap = new HashMap<>();

        for (Review review : reviews) {
            String content = review.getContent();
            if (content == null || content.isBlank()) continue;

            for (Map.Entry<String, List<String>> entry : TOPIC_KEYWORD_MAP.entrySet()) {
                String topicType = entry.getKey();
                List<String> keywords = entry.getValue();

                List<String> matchedKeywords = new ArrayList<>();
                String contentLower = content.toLowerCase();
                for (String keyword : keywords) {
                    if (contentLower.contains(keyword.toLowerCase())) {
                        matchedKeywords.add(keyword);
                    }
                }

                if (!matchedKeywords.isEmpty()) {
                    matchMap
                            .computeIfAbsent(review.getMerchantId(), k -> new HashMap<>())
                            .computeIfAbsent(topicType, k -> new ArrayList<>())
                            .add(new ReviewMatch(review, matchedKeywords));
                }
            }
        }

        // 3. 对于满足阈值的，生成或更新预警
        List<SensitiveAlertDTO> resultAlerts = new ArrayList<>();

        for (Map.Entry<Long, Map<String, List<ReviewMatch>>> merchantEntry : matchMap.entrySet()) {
            Long merchantId = merchantEntry.getKey();

            for (Map.Entry<String, List<ReviewMatch>> topicEntry : merchantEntry.getValue().entrySet()) {
                String topicType = topicEntry.getKey();
                List<ReviewMatch> matches = topicEntry.getValue();

                if (matches.size() < threshold) {
                    log.debug("商家ID={} 话题={} 匹配数={} 低于阈值={}，跳过", merchantId, topicType, matches.size(), threshold);
                    continue;
                }

                // 收集所有关键词（去重）
                Set<String> allKeywords = new LinkedHashSet<>();
                OffsetDateTime firstTime = null;
                OffsetDateTime lastTime = null;

                for (ReviewMatch match : matches) {
                    allKeywords.addAll(match.keywords);
                    OffsetDateTime reviewTime = match.review.getCreatedAt();
                    if (firstTime == null || reviewTime.isBefore(firstTime)) {
                        firstTime = reviewTime;
                    }
                    if (lastTime == null || reviewTime.isAfter(lastTime)) {
                        lastTime = reviewTime;
                    }
                }

                // 4. 检查是否已存在24小时内的同商家+同话题预警
                LambdaQueryWrapper<SensitiveAlert> existingWrapper = new LambdaQueryWrapper<SensitiveAlert>()
                        .eq(SensitiveAlert::getMerchantId, merchantId)
                        .eq(SensitiveAlert::getTopicType, topicType)
                        .ge(SensitiveAlert::getLastOccurredAt, now.minusHours(24))
                        .orderByDesc(SensitiveAlert::getLastOccurredAt)
                        .last("LIMIT 1");

                SensitiveAlert existingAlert = alertMapper.selectOne(existingWrapper);

                if (existingAlert != null) {
                    // 更新已有预警（24小时内合并）
                    updateExistingAlert(existingAlert, matches.size(), allKeywords, lastTime, matches);
                    resultAlerts.add(toDTO(existingAlert, merchantId));
                    log.info("更新已有预警: id={}, 新增评价数={}", existingAlert.getId(), matches.size());
                } else {
                    // 创建新预警
                    SensitiveAlert newAlert = createNewAlert(merchantId, topicType,
                            matches.size(), allKeywords, firstTime, lastTime, matches);
                    resultAlerts.add(toDTO(newAlert, merchantId));
                    log.info("创建新预警: id={}, merchantId={}, topicType={}", newAlert.getId(), merchantId, topicType);
                }
            }
        }

        log.info("敏感话题检测完成: 生成/更新 {} 条预警", resultAlerts.size());
        return resultAlerts;
    }

    /**
     * 创建新预警
     */
    private SensitiveAlert createNewAlert(Long merchantId, String topicType,
                                           int reviewCount, Set<String> keywords,
                                           OffsetDateTime firstTime, OffsetDateTime lastTime,
                                           List<ReviewMatch> matches) {
        OffsetDateTime now = OffsetDateTime.now();

        SensitiveAlert alert = new SensitiveAlert();
        alert.setMerchantId(merchantId);
        alert.setTopicType(topicType);
        alert.setRiskLevel(calculateRiskLevel(topicType, reviewCount));
        alert.setReviewCount(reviewCount);
        alert.setKeywords(toJson(keywords));
        alert.setFirstOccurredAt(firstTime);
        alert.setLastOccurredAt(lastTime != null ? lastTime : now);
        alert.setStatus("PENDING");
        alert.setCreatedAt(now);
        alert.setUpdatedAt(now);

        alertMapper.insert(alert);

        // 保存关联的评价
        saveAlertReviews(alert.getId(), matches);

        return alert;
    }

    /**
     * 更新已有预警（24小时内合并）
     */
    private void updateExistingAlert(SensitiveAlert alert, int newReviewCount,
                                      Set<String> allKeywords, OffsetDateTime lastTime,
                                      List<ReviewMatch> matches) {
        // 合并关键词
        Set<String> existingKeywords = new HashSet<>(parseKeywords(alert.getKeywords()));
        existingKeywords.addAll(allKeywords);

        alert.setReviewCount(alert.getReviewCount() + newReviewCount);
        alert.setKeywords(toJson(existingKeywords));
        if (lastTime != null && lastTime.isAfter(alert.getLastOccurredAt())) {
            alert.setLastOccurredAt(lastTime);
        }
        alert.setRiskLevel(calculateRiskLevel(alert.getTopicType(), alert.getReviewCount()));
        // 如果已被处理过但又有新评价，重新标记为待处理
        if ("RESOLVED".equals(alert.getStatus()) || "DISMISSED".equals(alert.getStatus())) {
            alert.setStatus("PENDING");
        }

        alertMapper.updateById(alert);

        // 保存新增的关联评价
        saveAlertReviews(alert.getId(), matches);
    }

    /**
     * 保存预警关联的评价记录
     */
    private void saveAlertReviews(Long alertId, List<ReviewMatch> matches) {
        OffsetDateTime now = OffsetDateTime.now();

        for (ReviewMatch match : matches) {
            // 检查是否已关联
            LambdaQueryWrapper<SensitiveAlertReview> checkWrapper = new LambdaQueryWrapper<SensitiveAlertReview>()
                    .eq(SensitiveAlertReview::getAlertId, alertId)
                    .eq(SensitiveAlertReview::getReviewId, match.review.getId());

            if (alertReviewMapper.selectCount(checkWrapper) > 0) {
                continue;
            }

            SensitiveAlertReview alertReview = new SensitiveAlertReview();
            alertReview.setAlertId(alertId);
            alertReview.setReviewId(match.review.getId());
            alertReview.setReviewVersion(match.review.getCurrentVersion() != null
                    ? match.review.getCurrentVersion() : 1);
            alertReview.setEvidenceExcerpt(buildEvidenceExcerpt(match));
            alertReview.setCreatedAt(now);

            alertReviewMapper.insert(alertReview);
        }
    }

    /**
     * 构建评价中的证据摘录
     */
    private String buildEvidenceExcerpt(ReviewMatch match) {
        String content = match.review.getContent();
        if (content == null) return "";

        // 找到第一个匹配关键词在原文中的位置，截取周围文本
        for (String keyword : match.keywords) {
            int idx = content.indexOf(keyword);
            if (idx >= 0) {
                int start = Math.max(0, idx - 30);
                int end = Math.min(content.length(), idx + keyword.length() + 30);
                String excerpt = content.substring(start, end);
                if (start > 0) excerpt = "..." + excerpt;
                if (end < content.length()) excerpt = excerpt + "...";
                return excerpt;
            }
        }
        // 截取前100个字符
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }

    /**
     * 计算风险等级
     */
    private String calculateRiskLevel(String topicType, int reviewCount) {
        // 食品安全和严重服务纠纷默认高风险
        if ("FOOD_SAFETY".equals(topicType) || "SERVICE_DISPUTE".equals(topicType)) {
            if (reviewCount >= 5) return "HIGH";
            if (reviewCount >= 3) return "MEDIUM";
            return "LOW";
        }
        // 卫生问题和集中投诉
        if (reviewCount >= 8) return "HIGH";
        if (reviewCount >= 5) return "MEDIUM";
        return "LOW";
    }

    // ============================================================
    // 预警管理 CRUD
    // ============================================================

    /**
     * 分页查询预警列表
     */
    public PageResult<SensitiveAlertDTO> listAlerts(
            String status, String topicType, String riskLevel,
            Long merchantId, OffsetDateTime startTime, OffsetDateTime endTime,
            int pageNum, int pageSize) {

        LambdaQueryWrapper<SensitiveAlert> wrapper = new LambdaQueryWrapper<SensitiveAlert>()
                .orderByDesc(SensitiveAlert::getCreatedAt);

        if (status != null && !status.isBlank()) {
            wrapper.eq(SensitiveAlert::getStatus, status);
        }
        if (topicType != null && !topicType.isBlank()) {
            wrapper.eq(SensitiveAlert::getTopicType, topicType);
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            wrapper.eq(SensitiveAlert::getRiskLevel, riskLevel);
        }
        if (merchantId != null) {
            wrapper.eq(SensitiveAlert::getMerchantId, merchantId);
        }
        if (startTime != null) {
            wrapper.ge(SensitiveAlert::getCreatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(SensitiveAlert::getCreatedAt, endTime);
        }

        Page<SensitiveAlert> page = new Page<>(pageNum, pageSize);
        Page<SensitiveAlert> result = alertMapper.selectPage(page, wrapper);

        // 批量查询商家名称
        Set<Long> merchantIds = result.getRecords().stream()
                .map(SensitiveAlert::getMerchantId)
                .collect(Collectors.toSet());
        Map<Long, String> merchantNameMap = getMerchantNameMap(merchantIds);

        List<SensitiveAlertDTO> dtos = result.getRecords().stream()
                .map(alert -> toDTO(alert, merchantNameMap))
                .collect(Collectors.toList());

        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), dtos);
    }

    /**
     * 获取预警详情（包含关联评价）
     */
    public SensitiveAlertDetailDTO getAlertDetail(Long id) {
        SensitiveAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "预警记录不存在");
        }

        // 查询关联评价
        List<SensitiveAlertReview> alertReviews = alertReviewMapper.selectList(
                new LambdaQueryWrapper<SensitiveAlertReview>()
                        .eq(SensitiveAlertReview::getAlertId, id)
                        .orderByDesc(SensitiveAlertReview::getCreatedAt)
        );

        // 查询评价详情
        List<SensitiveAlertReviewDTO> reviewDTOs = new ArrayList<>();
        if (!alertReviews.isEmpty()) {
            List<Long> reviewIds = alertReviews.stream()
                    .map(SensitiveAlertReview::getReviewId)
                    .collect(Collectors.toList());
            List<Review> reviews = reviewMapper.selectBatchIds(reviewIds);
            Map<Long, Review> reviewMap = reviews.stream()
                    .collect(Collectors.toMap(Review::getId, r -> r));

            for (SensitiveAlertReview ar : alertReviews) {
                Review review = reviewMap.get(ar.getReviewId());
                reviewDTOs.add(SensitiveAlertReviewDTO.builder()
                        .id(ar.getId())
                        .alertId(ar.getAlertId())
                        .reviewId(ar.getReviewId())
                        .reviewVersion(ar.getReviewVersion())
                        .evidenceExcerpt(ar.getEvidenceExcerpt())
                        .reviewContent(review != null ? review.getContent() : null)
                        .reviewRating(review != null && review.getRating() != null ? review.getRating().intValue() : null)
                        .reviewUserId(review != null ? review.getUserId() : null)
                        .reviewCreatedAt(review != null ? review.getCreatedAt() : null)
                        .createdAt(ar.getCreatedAt())
                        .build());
            }
        }

        // 查询商家名称
        Merchant merchant = merchantMapper.selectById(alert.getMerchantId());

        return buildDetailDTO(alert, merchant, reviewDTOs);
    }

    /**
     * 更新预警处理状态
     */
    @Transactional
    public SensitiveAlertDTO updateAlertStatus(Long id, UpdateAlertStatusRequest request,
                                                Long operatorUserId, String operatorUsername) {
        SensitiveAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "预警记录不存在");
        }

        String targetStatus = request.getStatus();
        if (!Set.of("PROCESSING", "RESOLVED", "DISMISSED").contains(targetStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS",
                    "状态必须是 PROCESSING、RESOLVED 或 DISMISSED");
        }

        // 防止重复处理已完结的预警
        if (("RESOLVED".equals(alert.getStatus()) || "DISMISSED".equals(alert.getStatus()))
                && !"PROCESSING".equals(targetStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "STATUS_CONFLICT",
                    "该预警已处理完毕，无法再次修改状态");
        }

        OffsetDateTime now = OffsetDateTime.now();
        alert.setStatus(targetStatus);
        if (request.getRemark() != null) {
            alert.setRemark(request.getRemark());
        }
        if (operatorUserId != null) {
            alert.setHandledBy(operatorUserId);
        }
        if (operatorUsername != null) {
            alert.setHandledUsername(operatorUsername);
        }
        if ("RESOLVED".equals(targetStatus) || "DISMISSED".equals(targetStatus)) {
            alert.setHandledAt(now);
        }
        alert.setUpdatedAt(now);

        alertMapper.updateById(alert);

        Merchant merchant = merchantMapper.selectById(alert.getMerchantId());
        return toDTO(alert, merchant != null ? merchant.getName() : null);
    }

    /**
     * 获取待处理预警数量
     */
    public long countPendingAlerts() {
        return alertMapper.selectCount(
                new LambdaQueryWrapper<SensitiveAlert>()
                        .eq(SensitiveAlert::getStatus, "PENDING")
        );
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private SensitiveAlertDTO toDTO(SensitiveAlert alert, Long merchantId) {
        String merchantName = getMerchantName(merchantId);
        return toDTO(alert, merchantName);
    }

    private SensitiveAlertDTO toDTO(SensitiveAlert alert, String merchantName) {
        return SensitiveAlertDTO.builder()
                .id(alert.getId())
                .merchantId(alert.getMerchantId())
                .merchantName(merchantName)
                .topicType(alert.getTopicType())
                .topicTypeName(getTopicTypeName(alert.getTopicType()))
                .riskLevel(alert.getRiskLevel())
                .riskLevelName(getRiskLevelName(alert.getRiskLevel()))
                .reviewCount(alert.getReviewCount())
                .keywords(parseKeywords(alert.getKeywords()))
                .firstOccurredAt(alert.getFirstOccurredAt())
                .lastOccurredAt(alert.getLastOccurredAt())
                .status(alert.getStatus())
                .statusName(getStatusName(alert.getStatus()))
                .handledUsername(alert.getHandledUsername())
                .handledAt(alert.getHandledAt())
                .remark(alert.getRemark())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .build();
    }

    private SensitiveAlertDTO toDTO(SensitiveAlert alert, Map<Long, String> merchantNameMap) {
        String merchantName = merchantNameMap.getOrDefault(alert.getMerchantId(), "未知商家");
        return toDTO(alert, merchantName);
    }

    private SensitiveAlertDetailDTO buildDetailDTO(SensitiveAlert alert, Merchant merchant,
                                                    List<SensitiveAlertReviewDTO> reviews) {
        return SensitiveAlertDetailDTO.builder()
                .id(alert.getId())
                .merchantId(alert.getMerchantId())
                .merchantName(merchant != null ? merchant.getName() : "未知商家")
                .topicType(alert.getTopicType())
                .topicTypeName(getTopicTypeName(alert.getTopicType()))
                .riskLevel(alert.getRiskLevel())
                .riskLevelName(getRiskLevelName(alert.getRiskLevel()))
                .reviewCount(alert.getReviewCount())
                .keywords(parseKeywords(alert.getKeywords()))
                .firstOccurredAt(alert.getFirstOccurredAt())
                .lastOccurredAt(alert.getLastOccurredAt())
                .status(alert.getStatus())
                .statusName(getStatusName(alert.getStatus()))
                .handledUsername(alert.getHandledUsername())
                .handledAt(alert.getHandledAt())
                .remark(alert.getRemark())
                .createdAt(alert.getCreatedAt())
                .updatedAt(alert.getUpdatedAt())
                .reviews(reviews)
                .build();
    }

    private Map<Long, String> getMerchantNameMap(Set<Long> merchantIds) {
        if (merchantIds.isEmpty()) return Collections.emptyMap();
        List<Merchant> merchants = merchantMapper.selectBatchIds(merchantIds);
        return merchants.stream()
                .collect(Collectors.toMap(Merchant::getId, Merchant::getName));
    }

    private String getMerchantName(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        return merchant != null ? merchant.getName() : null;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON序列化失败", e);
            return "[]";
        }
    }

    private List<String> parseKeywords(String keywordsJson) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(keywordsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("JSON解析关键词失败: {}", keywordsJson, e);
            return Collections.emptyList();
        }
    }

    // ============================================================
    // 枚举中文名映射
    // ============================================================

    public static String getTopicTypeName(String topicType) {
        return switch (topicType) {
            case "FOOD_SAFETY" -> "食品安全";
            case "HYGIENE" -> "卫生问题";
            case "CONCENTRATED_COMPLAINT" -> "集中投诉";
            case "SERVICE_DISPUTE" -> "严重服务纠纷";
            default -> topicType;
        };
    }

    public static String getRiskLevelName(String riskLevel) {
        return switch (riskLevel) {
            case "HIGH" -> "高风险";
            case "MEDIUM" -> "中风险";
            case "LOW" -> "低风险";
            default -> riskLevel;
        };
    }

    public static String getStatusName(String status) {
        return switch (status) {
            case "PENDING" -> "待处理";
            case "PROCESSING" -> "处理中";
            case "RESOLVED" -> "已处理";
            case "DISMISSED" -> "已忽略";
            default -> status;
        };
    }

    /**
     * 内部类：存储评价与匹配关键词的关联
     */
    private static class ReviewMatch {
        final Review review;
        final List<String> keywords;

        ReviewMatch(Review review, List<String> keywords) {
            this.review = review;
            this.keywords = keywords;
        }
    }
}
