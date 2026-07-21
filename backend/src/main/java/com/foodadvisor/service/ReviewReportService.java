package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.foodadvisor.dto.report.MyReportListVO;
import com.foodadvisor.dto.report.ReviewReportRequest;
import com.foodadvisor.entity.Merchant;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewReport;
import com.foodadvisor.exception.ApiException;
import com.foodadvisor.mapper.MerchantMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.ReviewReportMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 评价举报服务（EPIC-08 故事5）
 */
@Service
public class ReviewReportService extends ServiceImpl<ReviewReportMapper, ReviewReport> {

    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private static final Set<String> VALID_REASONS = Set.of(
            "ADVERTISING", "FALSE_REVIEW", "MALICIOUS_ATTACK",
            "SEXUAL_OR_VULGAR", "PRIVACY_LEAK", "OTHER"
    );

    private static final Map<String, String> REASON_TEXT_MAP = Map.of(
            "ADVERTISING", "广告引流",
            "FALSE_REVIEW", "虚假评价",
            "MALICIOUS_ATTACK", "恶意攻击",
            "SEXUAL_OR_VULGAR", "色情低俗",
            "PRIVACY_LEAK", "泄露隐私",
            "OTHER", "其他"
    );

    private static final Map<String, String> STATUS_TEXT_MAP = Map.of(
            "PENDING", "待处理",
            "RESOLVED", "已处理",
            "REJECTED", "已驳回"
    );

    private final ReviewMapper reviewMapper;
    private final MerchantMapper merchantMapper;

    public ReviewReportService(ReviewMapper reviewMapper, MerchantMapper merchantMapper) {
        this.reviewMapper = reviewMapper;
        this.merchantMapper = merchantMapper;
    }

    /**
     * 提交举报
     */
    @Transactional
    public ReviewReport submitReport(Long reporterUserId, ReviewReportRequest request) {
        // 校验举报原因
        if (request.getReason() == null || !VALID_REASONS.contains(request.getReason())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_REASON",
                    "请选择有效的举报原因");
        }

        // 校验补充说明长度
        if (request.getDescription() != null
                && request.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "DESCRIPTION_TOO_LONG",
                    "补充说明不能超过" + MAX_DESCRIPTION_LENGTH + "字");
        }

        // 校验被举报评价是否存在
        Review review = reviewMapper.selectById(request.getReportedReviewId());
        if (review == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "REVIEW_NOT_FOUND",
                    "被举报的评价不存在");
        }

        Long merchantId = request.getMerchantId() != null
                ? request.getMerchantId()
                : review.getMerchantId();

        // 检查是否已有未处理的举报记录（同一用户对同一评价）
        LambdaQueryWrapper<ReviewReport> duplicateWrapper = new LambdaQueryWrapper<>();
        duplicateWrapper.eq(ReviewReport::getReporterUserId, reporterUserId)
                .eq(ReviewReport::getReportedReviewId, request.getReportedReviewId())
                .eq(ReviewReport::getStatus, "PENDING");
        ReviewReport existing = this.getOne(duplicateWrapper);
        if (existing != null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "ALREADY_REPORTED",
                    "已举报过该评价，请等待处理结果");
        }

        // 创建举报记录
        ReviewReport report = new ReviewReport();
        report.setReporterUserId(reporterUserId);
        report.setReportedReviewId(request.getReportedReviewId());
        report.setMerchantId(merchantId);
        report.setReason(request.getReason());
        report.setDescription(request.getDescription());
        report.setStatus("PENDING");

        boolean saved = this.save(report);
        if (!saved) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "REPORT_SUBMIT_FAILED",
                    "举报提交失败，请稍后重试");
        }

        return report;
    }

    /**
     * 查询当前用户的举报列表（分页，时间倒序）
     */
    public Page<MyReportListVO> listMyReports(Long userId, int pageNum, int pageSize,
                                               String status) {
        LambdaQueryWrapper<ReviewReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewReport::getReporterUserId, userId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(ReviewReport::getStatus, status);
        }
        wrapper.orderByDesc(ReviewReport::getCreatedAt);

        Page<ReviewReport> page = this.page(Page.of(pageNum, pageSize), wrapper);

        // 收集评价ID和商家ID，批量查询
        List<ReviewReport> records = page.getRecords();
        Set<Long> reviewIds = records.stream()
                .map(ReviewReport::getReportedReviewId)
                .collect(Collectors.toSet());
        Set<Long> merchantIds = records.stream()
                .map(ReviewReport::getMerchantId)
                .collect(Collectors.toSet());

        Map<Long, Review> reviewMap = reviewMapper.selectBatchIds(reviewIds).stream()
                .collect(Collectors.toMap(Review::getId, r -> r, (a, b) -> a));
        Map<Long, Merchant> merchantMap = merchantMapper.selectBatchIds(merchantIds).stream()
                .collect(Collectors.toMap(Merchant::getId, m -> m, (a, b) -> a));

        // 转换为 VO
        List<MyReportListVO> voList = records.stream().map(report -> {
            MyReportListVO vo = new MyReportListVO();
            vo.setId(report.getId());
            vo.setReportedReviewId(report.getReportedReviewId());
            vo.setMerchantId(report.getMerchantId());
            vo.setReason(report.getReason());
            vo.setReasonText(REASON_TEXT_MAP.getOrDefault(report.getReason(), report.getReason()));
            vo.setDescription(report.getDescription());
            vo.setStatus(report.getStatus());
            vo.setStatusText(STATUS_TEXT_MAP.getOrDefault(report.getStatus(), report.getStatus()));
            vo.setResolution(report.getResolution());
            vo.setCreatedAt(report.getCreatedAt());
            vo.setHandledAt(report.getHandledAt());

            // 商家名称
            Merchant merchant = merchantMap.get(report.getMerchantId());
            vo.setMerchantName(merchant != null ? merchant.getName() : "未知商家");

            // 评价摘要（前80字）
            Review review = reviewMap.get(report.getReportedReviewId());
            if (review != null && review.getContent() != null) {
                String content = review.getContent();
                vo.setReviewSummary(content.length() > 80
                        ? content.substring(0, 80) + "…"
                        : content);
            } else {
                vo.setReviewSummary("（原评价已删除）");
            }

            return vo;
        }).collect(Collectors.toList());

        Page<MyReportListVO> resultPage = new Page<>();
        resultPage.setCurrent(page.getCurrent());
        resultPage.setSize(page.getSize());
        resultPage.setTotal(page.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    /**
     * 获取举报详情
     */
    public MyReportListVO getReportDetail(Long userId, Long reportId) {
        ReviewReport report = this.getById(reportId);
        if (report == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "REPORT_NOT_FOUND",
                    "举报记录不存在");
        }
        if (!report.getReporterUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "无权查看该举报记录");
        }

        Review review = reviewMapper.selectById(report.getReportedReviewId());
        Merchant merchant = merchantMapper.selectById(report.getMerchantId());

        MyReportListVO vo = new MyReportListVO();
        vo.setId(report.getId());
        vo.setReportedReviewId(report.getReportedReviewId());
        vo.setMerchantId(report.getMerchantId());
        vo.setMerchantName(merchant != null ? merchant.getName() : "未知商家");
        vo.setReason(report.getReason());
        vo.setReasonText(REASON_TEXT_MAP.getOrDefault(report.getReason(), report.getReason()));
        vo.setDescription(report.getDescription());
        vo.setStatus(report.getStatus());
        vo.setStatusText(STATUS_TEXT_MAP.getOrDefault(report.getStatus(), report.getStatus()));
        vo.setResolution(report.getResolution());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setHandledAt(report.getHandledAt());

        if (review != null && review.getContent() != null) {
            vo.setReviewSummary(review.getContent());
        } else {
            vo.setReviewSummary("（原评价已删除）");
        }

        return vo;
    }
}
