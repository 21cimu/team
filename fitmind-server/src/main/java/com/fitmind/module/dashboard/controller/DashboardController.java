package com.fitmind.module.dashboard.controller;

import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.dashboard.service.DashboardQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardQueryService dashboardQueryService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/stats")
    public Result<Map<String, Object>> getDashboardStats() {
        return Result.success(dashboardQueryService.getDashboardStats(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/weekly-training")
    public Result<List<Map<String, Object>>> getWeeklyTraining() {
        return Result.success(dashboardQueryService.getWeeklyTraining(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/heatmap")
    public Result<List<List<Map<String, Object>>>> getHeatmap() {
        return Result.success(dashboardQueryService.getHeatmap(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/nutrition-today")
    public Result<Map<String, Object>> getNutritionToday() {
        return Result.success(dashboardQueryService.getNutritionToday(currentUserProvider.getCurrentUserId()));
    }

    @GetMapping("/body-metrics-trend")
    public Result<List<Map<String, Object>>> getBodyMetricsTrend() {
        return Result.success(dashboardQueryService.getBodyMetricsTrend(currentUserProvider.getCurrentUserId()));
    }
}
