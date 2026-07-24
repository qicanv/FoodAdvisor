package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodadvisor.dto.PageResult;
import com.foodadvisor.dto.feedback.AnalysisFeedbackStatisticsVO;
import com.foodadvisor.dto.feedback.AnalysisFeedbackSubmitRequest;
import com.foodadvisor.dto.feedback.AnalysisFeedbackVO;
import com.foodadvisor.entity.AnalysisFeedback;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.User;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.AnalysisFeedbackMapper;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 商家分析结果反馈服务（EPIC-06 Story 5）
 *
 * 商家用户可以对情感分析、关键词提取、差评归因、竞品对比、
 * 经营建议、评价摘要和商家亮点等分析结果标记为准确或不准确，
 * 并填写具体问题说明。
 *
 * 验收准则对齐：
 * - AC-1: 商家用户能够对属于自己店铺的分析结果提交准确或不准确反馈
 * - AC-2: 反馈保存对应 analysisId 和 merchantId
 * - AC-3: 支持填写并保存具体问题说明
 * - AC-4: 管理员能够按 AI 功能和反馈类型汇总数量
 * - AC-5: 商家用户查询其他店铺反馈时返回 403（由 Controller 校验）
 * - AC-6: 管理员能够从反馈列表查看对应分析记录和问题说明
 * - AC-7: 同一商家对同一分析记录重复反馈时更新已有记录
 */
@Service
public class AnalysisFeedbackService {

    private static final Logger log =
            LoggerFactory.getLogger(AnalysisFeedbackService.class);

    /** 允许的反馈类型 */
    private static final Set<String> VALID_FEEDBACK_TYPES =
            Set.of("ACCURATE", "INACCURATE");

    /** 允许的分析类型 */
    private static final Set<String> VALID_ANALYSIS_TYPES =
            Set.of("SENTIMENT", "KEYWORD", "ISSUE_ATTRIBUTION", "COMPETITOR",
                    "BUSINESS_SUGGESTION", "REVIEW_SUMMARY", "HIGHLIGHT");

    /** 分析类型中文名映射 */
    private static final Map<String, String> ANALYSIS_TYPE_TEXT = Map.of(
            "SENTIMENT", "情感分析",
            "KEYWORD", "关键词提取",
            "ISSUE_ATTRIBUTION", "差评归因",
            "COMPETITOR", "竞品对比",
            "BUSINESS_SUGGESTION", "经营建议",
            "REVIEW_SUMMARY", "评价摘要",
            "HIGHLIGHT", "商家亮点"
    );

    /** 问题说明最大长度 */
    private static final int MAX_CONTENT_LENGTH = 2000;

    private final AnalysisFeedbackMapper feedbackMapper;
    private final MerchantMapper merchantMapper;
    private final UserMapper userMapper;

    public AnalysisFeedbackService(
            AnalysisFeedbackMapper feedbackMapper,
            MerchantMapper merchantMapper,
            UserMapper userMapper
    ) {
        this.feedbackMapper = feedbackMapper;
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
    }

    // ============================================
    // 商家端：提交/更新反馈
    // ============================================

    /**
     * 提交或更新分析结果反馈。
     *
     * 同一商家对同一分析类型的同一分析记录，再次提交时更新原记录（AC-7）。
     * analysisId 为 null 时表示对该分析类型的整体反馈，
     * 此时不检查唯一性（允许多条整体反馈）。
     *
     * @param merchantId 商家ID
     * @param userId     当前登录用户ID
     * @param request    反馈提交请求
     * @return 保存后的反馈记录
     */
    public AnalysisFeedbackVO submitFeedback(
            Long merchantId,
            Long userId,
            AnalysisFeedbackSubmitRequest request
    ) {
        // ---- 参数校验 ----
        validateSubmitRequest(request);

        // ---- 检查是否已有反馈，有则更新 ----
        if (request.getAnalysisId() != null) {
            AnalysisFeedback existing = feedbackMapper.selectOne(
                    new LambdaQueryWrapper<AnalysisFeedback>()
                            .eq(AnalysisFeedback::getMerchantId, merchantId)
                            .eq(AnalysisFeedback::getAnalysisType, request.getAnalysisType())
                            .eq(AnalysisFeedback::getAnalysisId, request.getAnalysisId())
            );
            if (existing != null) {
                log.info("更新已有分析反馈: feedbackId={}, merchantId={}, analysisType={}, analysisId={}",
                        existing.getId(), merchantId, request.getAnalysisType(), request.getAnalysisId());
                existing.setFeedbackType(request.getFeedbackType());
                existing.setContent(trimToNull(request.getContent()));
                feedbackMapper.updateById(existing);
                return toVO(existing);
            }
        }

        // ---- 新增反馈 ----
        AnalysisFeedback feedback = new AnalysisFeedback();
        feedback.setMerchantId(merchantId);
        feedback.setAnalysisType(request.getAnalysisType());
        feedback.setAnalysisId(request.getAnalysisId());
        feedback.setFeedbackType(request.getFeedbackType());
        feedback.setContent(trimToNull(request.getContent()));
        feedback.setCreatedBy(userId);

        feedbackMapper.insert(feedback);
        log.info("新增分析反馈: feedbackId={}, merchantId={}, analysisType={}, analysisId={}, feedbackType={}",
                feedback.getId(), merchantId, request.getAnalysisType(),
                request.getAnalysisId(), request.getFeedbackType());

        return toVO(feedback);
    }

    // ============================================
    // 商家端：查询自己的反馈列表
    // ============================================

    /**
     * 查询商家自己的分析反馈列表。
     *
     * @param merchantId   商家ID
     * @param analysisType 可选，按分析类型筛选
     * @param feedbackType 可选，按反馈类型筛选
     * @param pageNum      页码
     * @param pageSize     每页条数
     * @return 分页结果
     */
    public PageResult<AnalysisFeedbackVO> getMerchantFeedback(
            Long merchantId,
            String analysisType,
            String feedbackType,
            int pageNum,
            int pageSize
    ) {
        LambdaQueryWrapper<AnalysisFeedback> wrapper =
                new LambdaQueryWrapper<AnalysisFeedback>()
                        .eq(AnalysisFeedback::getMerchantId, merchantId)
                        .orderByDesc(AnalysisFeedback::getCreatedAt);

        if (analysisType != null && !analysisType.isBlank()) {
            wrapper.eq(AnalysisFeedback::getAnalysisType, analysisType.toUpperCase());
        }
        if (feedbackType != null && !feedbackType.isBlank()) {
            wrapper.eq(AnalysisFeedback::getFeedbackType, feedbackType.toUpperCase());
        }

        Page<AnalysisFeedback> page = feedbackMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        List<AnalysisFeedbackVO> vos = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return new PageResult<>(pageNum, pageSize, page.getTotal(), vos);
    }

    /**
     * 查询单条反馈详情（商家端，会校验商家归属）。
     */
    public AnalysisFeedbackVO getFeedbackDetail(Long feedbackId, Long merchantId) {
        AnalysisFeedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "反馈记录不存在");
        }
        if (!feedback.getMerchantId().equals(merchantId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "无权查看该反馈记录");
        }
        return toVO(feedback);
    }

    // ============================================
    // 管理员端：统计与列表
    // ============================================

    /**
     * 管理员：按分析类型和反馈类型汇总统计（AC-4）。
     *
     * @return 反馈统计视图
     */
    public AnalysisFeedbackStatisticsVO getStatistics() {
        List<Map<String, Object>> rows = feedbackMapper.statisticsByAnalysisType();

        long totalCount = 0;
        long accurateCount = 0;
        long inaccurateCount = 0;

        List<AnalysisFeedbackStatisticsVO.AnalysisTypeStat> byType = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String at = (String) row.get("analysisType");
            long tc = ((Number) row.get("totalCount")).longValue();
            long ac = ((Number) row.get("accurateCount")).longValue();
            long ic = ((Number) row.get("inaccurateCount")).longValue();

            totalCount += tc;
            accurateCount += ac;
            inaccurateCount += ic;

            byType.add(AnalysisFeedbackStatisticsVO.AnalysisTypeStat.builder()
                    .analysisType(at)
                    .analysisTypeText(toAnalysisTypeText(at))
                    .totalCount(tc)
                    .accurateCount(ac)
                    .inaccurateCount(ic)
                    .accuracyRate(tc > 0 ? (double) ac / tc : null)
                    .build());
        }

        return AnalysisFeedbackStatisticsVO.builder()
                .totalCount(totalCount)
                .accurateCount(accurateCount)
                .inaccurateCount(inaccurateCount)
                .accuracyRate(totalCount > 0 ? (double) accurateCount / totalCount : null)
                .byAnalysisType(byType)
                .build();
    }

    /**
     * 管理员：查询所有反馈列表（AC-6）。
     *
     * @param analysisType 可选，按分析类型筛选
     * @param feedbackType 可选，按反馈类型筛选
     * @param merchantId   可选，按商家筛选
     * @param pageNum      页码
     * @param pageSize     每页条数
     * @return 分页结果
     */
    public PageResult<AnalysisFeedbackVO> getAllFeedback(
            String analysisType,
            String feedbackType,
            Long merchantId,
            int pageNum,
            int pageSize
    ) {
        LambdaQueryWrapper<AnalysisFeedback> wrapper =
                new LambdaQueryWrapper<AnalysisFeedback>()
                        .orderByDesc(AnalysisFeedback::getCreatedAt);

        if (analysisType != null && !analysisType.isBlank()) {
            wrapper.eq(AnalysisFeedback::getAnalysisType, analysisType.toUpperCase());
        }
        if (feedbackType != null && !feedbackType.isBlank()) {
            wrapper.eq(AnalysisFeedback::getFeedbackType, feedbackType.toUpperCase());
        }
        if (merchantId != null) {
            wrapper.eq(AnalysisFeedback::getMerchantId, merchantId);
        }

        Page<AnalysisFeedback> page = feedbackMapper.selectPage(
                new Page<>(pageNum, pageSize), wrapper);

        // 批量加载商家和用户信息
        Set<Long> merchantIds = page.getRecords().stream()
                .map(AnalysisFeedback::getMerchantId)
                .collect(Collectors.toSet());
        Set<Long> userIds = page.getRecords().stream()
                .map(AnalysisFeedback::getCreatedBy)
                .collect(Collectors.toSet());

        Map<Long, String> merchantNameMap = new HashMap<>();
        if (!merchantIds.isEmpty()) {
            List<Merchant> merchants = merchantMapper.selectBatchIds(merchantIds);
            for (Merchant m : merchants) {
                merchantNameMap.put(m.getId(), m.getName());
            }
        }

        Map<Long, String> usernameMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            for (User u : users) {
                usernameMap.put(u.getId(), u.getUsername());
            }
        }

        List<AnalysisFeedbackVO> vos = page.getRecords().stream()
                .map(f -> toVO(f, merchantNameMap, usernameMap))
                .collect(Collectors.toList());

        return new PageResult<>(pageNum, pageSize, page.getTotal(), vos);
    }

    // ============================================
    // 私有方法
    // ============================================

    /**
     * 校验提交请求参数。
     */
    private void validateSubmitRequest(AnalysisFeedbackSubmitRequest request) {
        if (request.getAnalysisType() == null || request.getAnalysisType().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "分析类型（analysisType）不能为空");
        }
        String at = request.getAnalysisType().toUpperCase();
        if (!VALID_ANALYSIS_TYPES.contains(at)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "无效的分析类型，可选值：" + String.join(", ", VALID_ANALYSIS_TYPES));
        }
        request.setAnalysisType(at);

        if (request.getFeedbackType() == null || request.getFeedbackType().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "反馈类型（feedbackType）不能为空");
        }
        String ft = request.getFeedbackType().toUpperCase();
        if (!VALID_FEEDBACK_TYPES.contains(ft)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "无效的反馈类型，可选值：ACCURATE, INACCURATE");
        }
        request.setFeedbackType(ft);

        if (request.getContent() != null && request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "问题说明不能超过 " + MAX_CONTENT_LENGTH + " 字符");
        }
    }

    /**
     * 将实体转换为 VO（商家端使用，不加载额外信息）。
     */
    private AnalysisFeedbackVO toVO(AnalysisFeedback entity) {
        return AnalysisFeedbackVO.builder()
                .id(entity.getId())
                .merchantId(entity.getMerchantId())
                .analysisType(entity.getAnalysisType())
                .analysisTypeText(toAnalysisTypeText(entity.getAnalysisType()))
                .analysisId(entity.getAnalysisId())
                .feedbackType(entity.getFeedbackType())
                .feedbackTypeText(toFeedbackTypeText(entity.getFeedbackType()))
                .content(entity.getContent())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 将实体转换为 VO（管理员端使用，加载商家和用户名称）。
     */
    private AnalysisFeedbackVO toVO(
            AnalysisFeedback entity,
            Map<Long, String> merchantNameMap,
            Map<Long, String> usernameMap
    ) {
        AnalysisFeedbackVO vo = toVO(entity);
        vo.setMerchantName(merchantNameMap.getOrDefault(entity.getMerchantId(), "未知商家"));
        vo.setCreatedByUsername(usernameMap.getOrDefault(entity.getCreatedBy(), "未知用户"));
        return vo;
    }

    private static String toAnalysisTypeText(String type) {
        return ANALYSIS_TYPE_TEXT.getOrDefault(type, type);
    }

    private static String toFeedbackTypeText(String type) {
        return "ACCURATE".equals(type) ? "准确" : "INACCURATE".equals(type) ? "不准确" : type;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
