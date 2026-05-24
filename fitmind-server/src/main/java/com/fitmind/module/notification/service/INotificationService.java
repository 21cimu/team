package com.fitmind.module.notification.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.notification.entity.Notification;

import java.util.List;
import java.util.Map;

public interface INotificationService extends IService<Notification> {
    void sendNotification(Long userId, String type, String title, String content, Long relatedId, String relatedType);
    List<Notification> getUserNotifications(Long userId);
    Map<String, Object> getUnreadCount(Long userId);
    void markAsRead(Long notificationId, Long userId);
    void markAllAsRead(Long userId);
}
