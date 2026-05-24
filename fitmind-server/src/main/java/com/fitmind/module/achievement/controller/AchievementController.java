package com.fitmind.module.achievement.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.module.achievement.service.IAchievementService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/achievement")
@RequiredArgsConstructor
public class AchievementController {

    private final IAchievementService achievementService;
    private final SysUserMapper sysUserMapper;

    private SysUser getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户未认证");
        }
        return user;
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getMyAchievements() {
        try {
            SysUser user = getCurrentUser();
            achievementService.checkAndUnlockAchievements(user.getId());
            List<Map<String, Object>> achievements = achievementService.getUserAchievements(user.getId());
            return Result.success(achievements);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/check")
    public Result<String> checkAchievements() {
        try {
            SysUser user = getCurrentUser();
            achievementService.checkAndUnlockAchievements(user.getId());
            return Result.success("成就检查完成");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
