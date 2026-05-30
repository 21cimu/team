package com.fitmind.module.ai.controller;

import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.ai.dto.DietGenerateRequest;
import com.fitmind.module.ai.dto.TrainingGenerateRequest;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.service.IAiDietPlanService;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.service.IAiTrainingPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiPlanController {

    private final CurrentUserProvider currentUserProvider;
    private final IAiTrainingPlanService aiTrainingPlanService;
    private final IAiDietPlanService aiDietPlanService;

    @PostMapping("/generate/training")
    public Result<AiTrainingPlan> generateTrainingPlan(@RequestBody(required = false) TrainingGenerateRequest request) {
        try {
            AiTrainingPlan plan = aiTrainingPlanService.generateAndSavePlan(
                    currentUserProvider.getCurrentUserId(),
                    request == null ? null : request.getWeather(),
                    request == null ? null : request.getTargetMuscleGroup(),
                    request != null && Boolean.TRUE.equals(request.getReplaceExisting()));
            return Result.success(plan);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/generate/diet")
    public Result<AiDietPlan> generateDietPlan(@RequestBody(required = false) DietGenerateRequest request) {
        try {
            AiDietPlan plan = aiDietPlanService.generateAndSavePlan(
                    currentUserProvider.getCurrentUserId(),
                    request == null ? null : request.getWeather(),
                    request == null ? null : request.getRecognizedFoodsContext());
            return Result.success(plan);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/training/today")
    public Result<AiTrainingPlan> getTodayTrainingPlan() {
        return Result.success(aiTrainingPlanService.getPlanForToday(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/diet/today")
    public Result<AiDietPlan> getTodayDietPlan() {
        return Result.success(aiDietPlanService.getPlanForToday(currentUserProvider.getCurrentUserId()));
    }

    @PostMapping("/training/checkin/{planId}")
    public Result<String> checkInTrainingPlan(@PathVariable Long planId) {
        try {
            aiTrainingPlanService.checkIn(currentUserProvider.getCurrentUserId(), planId);
            return Result.success("签到成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/diet/checkin/{planId}")
    public Result<String> checkInDietPlan(@PathVariable Long planId) {
        try {
            aiDietPlanService.checkIn(currentUserProvider.getCurrentUserId(), planId);
            return Result.success("签到成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
