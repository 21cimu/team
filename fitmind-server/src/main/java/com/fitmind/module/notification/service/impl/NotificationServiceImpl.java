package com.fitmind.module.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fitmind.module.notification.entity.Notification;
import com.fitmind.module.notification.mapper.NotificationMapper;
import com.fitmind.module.notification.service.INotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification> implements INotificationService {

    @Override
    public void sendNotification(Long userId, String type, String title, String content, Long relatedId, String relatedType) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setIsRead(0);
        notification.setRelatedId(relatedId);
        notification.setRelatedType(relatedType);
        notification.setCreateTime(LocalDateTime.now());
        this.save(notification);
    }

    @Override
    public List<Notification> getUserNotifications(Long userId) {
        return this.list(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .orderByDesc(Notification::getCreateTime)
                .last("LIMIT 50"));
    }

    @Override
    public Map<String, Object> getUnreadCount(Long userId) {
        long unread = this.count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0));
        long total = this.count(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId));
        Map<String, Object> result = new HashMap<>();
        result.put("unread", unread);
        result.put("total", total);
        return result;
    }

    @Override
    public void markAsRead(Long notificationId, Long userId) {
        this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public void markAllAsRead(Long userId) {
        this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }
}
