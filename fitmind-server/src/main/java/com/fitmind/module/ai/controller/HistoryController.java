package com.fitmind.module.ai.controller;

import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.service.IAiDietPlanService;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.service.IAiTrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final IAiTrainingPlanService aiTrainingPlanService;
    private final IAiDietPlanService aiDietPlanService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/training")
    public Result<List<AiTrainingPlan>> getTrainingHistory() {
        List<AiTrainingPlan> history = aiTrainingPlanService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiTrainingPlan>()
                        .eq(AiTrainingPlan::getUserId, currentUserProvider.getCurrentUserId())
                        .orderByDesc(AiTrainingPlan::getPlanDate)
        );
        return Result.success(history);
    }

    @GetMapping("/diet")
    public Result<List<AiDietPlan>> getDietHistory() {
        List<AiDietPlan> history = aiDietPlanService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiDietPlan>()
                        .eq(AiDietPlan::getUserId, currentUserProvider.getCurrentUserId())
                        .orderByDesc(AiDietPlan::getPlanDate)
        );
        return Result.success(history);
    }
}
