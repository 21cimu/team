package com.fitmind.module.notification.controller;

import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.notification.entity.Notification;
import com.fitmind.module.notification.service.INotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final INotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/list")
    public Result<List<Notification>> getMyNotifications() {
        return Result.success(notificationService.getUserNotifications(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> getUnreadCount() {
        return Result.success(notificationService.getUnreadCount(currentUserProvider.getCurrentUserId()));
    }

    @PutMapping("/read/{id}")
    public Result<String> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id, currentUserProvider.getCurrentUserId());
        return Result.success("已标记为已读");
    }

    @PutMapping("/read-all")
    public Result<String> markAllAsRead() {
        notificationService.markAllAsRead(currentUserProvider.getCurrentUserId());
        return Result.success("所有通知已标记为已读");
    }
}
