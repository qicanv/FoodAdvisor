package com.foodadvisor.controller;

import com.foodadvisor.dto.notification.NotificationVO;
import com.foodadvisor.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationVO>> list(
            @RequestHeader("X-User-Id") Long userId
    ) {
        List<NotificationVO> notifications = notificationService.listByUser(userId);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/unread")
    public ResponseEntity<List<NotificationVO>> listUnread(
            @RequestHeader("X-User-Id") Long userId
    ) {
        List<NotificationVO> notifications = notificationService.listUnreadByUser(userId);
        return ResponseEntity.ok(notifications);
    }

    @GetMapping("/count-unread")
    public ResponseEntity<Map<String, Long>> countUnread(
            @RequestHeader("X-User-Id") Long userId
    ) {
        Long count = notificationService.countUnread(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Boolean> markAsRead(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long notificationId
    ) {
        boolean success = notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(success);
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @RequestHeader("X-User-Id") Long userId
    ) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/review/{reviewId}/disable")
    public ResponseEntity<Void> disableReviewNotifications(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long reviewId
    ) {
        notificationService.disableReviewNotifications(userId, reviewId);
        return ResponseEntity.ok().build();
    }
}