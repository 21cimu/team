package com.fitmind.module.diet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.common.cache.UserCacheInvalidationService;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.service.IAiDietPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/diet")
@RequiredArgsConstructor
public class DietPlanController {

    private final IAiDietPlanService aiDietPlanService;
    private final CurrentUserProvider currentUserProvider;
    private final UserCacheInvalidationService cacheInvalidationService;

    @PostMapping("/plan")
    public Result<AiDietPlan> createManualPlan(@RequestBody AiDietPlan request) {
        Long userId = currentUserProvider.getCurrentUserId();

        aiDietPlanService.remove(new LambdaQueryWrapper<AiDietPlan>()
                .eq(AiDietPlan::getUserId, userId)
                .eq(AiDietPlan::getPlanDate, LocalDate.now()));

        request.setUserId(userId);
        request.setPlanDate(LocalDate.now());
        request.setStatus(0);
        request.setCreateTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());

        if (request.getTotalCalories() == null || request.getTotalCalories() <= 0) {
            request.setTotalCalories(2000);
        }

        aiDietPlanService.save(request);
        cacheInvalidationService.evictDietPlanData(userId);
        return Result.success(request);
    }

    @PutMapping("/plan/{id}")
    public Result<AiDietPlan> updatePlan(@PathVariable Long id, @RequestBody AiDietPlan request) {
        Long userId = currentUserProvider.getCurrentUserId();

        AiDietPlan existing = aiDietPlanService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("计划不存在或无权修改");
        }

        if (request.getTotalCalories() != null && request.getTotalCalories() > 0) {
            existing.setTotalCalories(request.getTotalCalories());
        }
        if (request.getProtein() != null) {
            existing.setProtein(request.getProtein());
        }
        if (request.getCarbs() != null) {
            existing.setCarbs(request.getCarbs());
        }
        if (request.getFat() != null) {
            existing.setFat(request.getFat());
        }
        if (request.getContent() != null && !request.getContent().isBlank()) {
            existing.setContent(request.getContent());
        }

        existing.setUpdateTime(LocalDateTime.now());
        aiDietPlanService.updateById(existing);
        cacheInvalidationService.evictDietPlanData(userId);
        return Result.success(existing);
    }
}
