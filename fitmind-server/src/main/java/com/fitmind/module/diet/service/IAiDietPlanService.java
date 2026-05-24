package com.fitmind.module.diet.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.ai.dto.WeatherContextSnapshot;
import com.fitmind.module.diet.entity.AiDietPlan;

public interface IAiDietPlanService extends IService<AiDietPlan> {
    AiDietPlan getPlanForToday(Long userId);
    AiDietPlan generateAndSavePlan(Long userId);
    AiDietPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext);
    AiDietPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext, String recognizedFoodsContext);
    void checkIn(Long userId, Long planId);
}
