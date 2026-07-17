package com.foodadvisor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.foodadvisor.dto.notification.NotificationVO;
import com.foodadvisor.entity.Notification;
import com.foodadvisor.entity.Review;
import com.foodadvisor.entity.ReviewReply;
import com.foodadvisor.entity.User;
import com.foodadvisor.mapper.NotificationMapper;
import com.foodadvisor.mapper.ReviewMapper;
import com.foodadvisor.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;

    @Transactional
    public void createReplyNotification(Long reviewId, Long merchantId, String replyContent, String merchantName) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            log.warn("评价不存在: reviewId={}", reviewId);
            return;
        }

        Long userId = review.getUserId();

        if (isReviewNotificationsDisabled(userId, reviewId)) {
            log.info("该评价通知已被禁用: userId={}, reviewId={}", userId, reviewId);
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setReviewId(reviewId);
        notification.setMerchantId(merchantId);
        notification.setType("REVIEW_REPLY");
        notification.setTitle("商家回复了您的评价");
        notification.setReviewSummary(truncate(review.getContent(), 50));
        notification.setReplySummary(truncate(replyContent, 50));
        notification.setMerchantName(merchantName);
        notification.setStatus("UNREAD");
        notification.setNotified(false);
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setUpdatedAt(OffsetDateTime.now());

        notificationMapper.insert(notification);
        log.info("创建通知: userId={}, reviewId={}, merchantId={}", userId, reviewId, merchantId);
    }

    public List<NotificationVO> listByUser(Long userId) {
        List<Notification> notifications = notificationMapper.selectByUserId(userId);
        return notifications.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public List<NotificationVO> listUnreadByUser(Long userId) {
        List<Notification> notifications = notificationMapper.selectUnreadByUserId(userId);
        return notifications.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public NotificationVO getById(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification != null && notification.getUserId().equals(userId)) {
            return toVO(notification);
        }
        return null;
    }

    @Transactional
    public boolean markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification != null && notification.getUserId().equals(userId)) {
            notification.setStatus("READ");
            notification.setUpdatedAt(OffsetDateTime.now());
            notificationMapper.updateById(notification);
            return true;
        }
        return false;
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationMapper.markAllAsRead(userId);
    }

    public Long countUnread(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    public void disableReviewNotifications(Long userId, Long reviewId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getReviewId, reviewId);
        List<Notification> notifications = notificationMapper.selectList(wrapper);
        for (Notification notification : notifications) {
            notification.setNotified(true);
            notificationMapper.updateById(notification);
        }
    }

    public boolean isReviewNotificationsDisabled(Long userId, Long reviewId) {
        Long count = notificationMapper.countByReviewId(userId, reviewId);
        if (count == 0) {
            return false;
        }
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId)
                .eq(Notification::getReviewId, reviewId)
                .eq(Notification::getNotified, true);
        return notificationMapper.selectCount(wrapper) > 0;
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setReviewId(notification.getReviewId());
        vo.setMerchantId(notification.getMerchantId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setReviewSummary(notification.getReviewSummary());
        vo.setReplySummary(notification.getReplySummary());
        vo.setMerchantName(notification.getMerchantName());
        vo.setStatus(notification.getStatus());
        vo.setNotified(notification.getNotified());
        vo.setCreatedAt(notification.getCreatedAt());
        vo.setUpdatedAt(notification.getUpdatedAt());
        return vo;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        text = text.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}