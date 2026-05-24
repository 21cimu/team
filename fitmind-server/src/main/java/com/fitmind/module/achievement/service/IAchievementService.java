package com.fitmind.module.achievement.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.achievement.entity.Achievement;

import java.util.List;
import java.util.Map;

public interface IAchievementService extends IService<Achievement> {
    List<Map<String, Object>> getUserAchievements(Long userId);
    void checkAndUnlockAchievements(Long userId);
}
