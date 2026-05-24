package com.fitmind.module.training.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.ai.dto.WeatherContextSnapshot;
import com.fitmind.module.training.entity.AiTrainingPlan;

public interface IAiTrainingPlanService extends IService<AiTrainingPlan> {
    AiTrainingPlan getPlanForToday(Long userId);
    AiTrainingPlan generateAndSavePlan(Long userId);
    AiTrainingPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext);
    AiTrainingPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext, String targetMuscleGroup);
    AiTrainingPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext, String targetMuscleGroup, boolean replaceExisting);
    String buildRecentTrainingContext(Long userId);
    void checkIn(Long userId, Long planId);
}
