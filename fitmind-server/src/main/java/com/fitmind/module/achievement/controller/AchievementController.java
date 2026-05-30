package com.fitmind.module.achievement.controller;

import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.achievement.service.IAchievementService;
import com.fitmind.module.user.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/achievement")
@RequiredArgsConstructor
public class AchievementController {

    private final IAchievementService achievementService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getMyAchievements() {
        try {
            SysUser user = currentUserProvider.getCurrentUser();
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
            SysUser user = currentUserProvider.getCurrentUser();
            achievementService.checkAndUnlockAchievements(user.getId());
            return Result.success("成就检查完成");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
