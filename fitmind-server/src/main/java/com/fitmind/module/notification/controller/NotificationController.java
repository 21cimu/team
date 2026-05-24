package com.fitmind.module.notification.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.module.notification.entity.Notification;
import com.fitmind.module.notification.service.INotificationService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final INotificationService notificationService;
    private final SysUserMapper sysUserMapper;

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) throw new RuntimeException("用户未认证");
        return user.getId();
    }

    @GetMapping("/list")
    public Result<List<Notification>> getMyNotifications() {
        return Result.success(notificationService.getUserNotifications(getCurrentUserId()));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Object>> getUnreadCount() {
        return Result.success(notificationService.getUnreadCount(getCurrentUserId()));
    }

    @PutMapping("/read/{id}")
    public Result<String> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id, getCurrentUserId());
        return Result.success("已标记为已读");
    }

    @PutMapping("/read-all")
    public Result<String> markAllAsRead() {
        notificationService.markAllAsRead(getCurrentUserId());
        return Result.success("所有通知已标记为已读");
    }
}
