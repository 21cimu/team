package com.fitmind.module.dashboard.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.service.IAiDietPlanService;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.service.IAiTrainingPlanService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.entity.UserBodyProfile;
import com.fitmind.module.user.mapper.SysUserMapper;
import com.fitmind.module.user.mapper.UserBodyProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final IAiTrainingPlanService aiTrainingPlanService;
    private final IAiDietPlanService aiDietPlanService;
    private final SysUserMapper sysUserMapper;
    private final UserBodyProfileMapper userBodyProfileMapper;

    private Long getCurrentUserId() {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户未认证");
        }
        return user.getId();
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getDashboardStats() {
        Long userId = getCurrentUserId();
        Map<String, Object> stats = new HashMap<>();

        List<AiTrainingPlan> allTraining = aiTrainingPlanService.list(
                new LambdaQueryWrapper<AiTrainingPlan>()
                        .eq(AiTrainingPlan::getUserId, userId)
                        .orderByDesc(AiTrainingPlan::getPlanDate));

        List<AiDietPlan> allDiet = aiDietPlanService.list(
                new LambdaQueryWrapper<AiDietPlan>()
                        .eq(AiDietPlan::getUserId, userId)
                        .orderByDesc(AiDietPlan::getPlanDate));

        int streak = calculateStreak(allTraining, allDiet);
        stats.put("streak", streak);

        LocalDate weekStart = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        long weeklyTraining = allTraining.stream()
                .filter(p -> p.getPlanDate() != null && !p.getPlanDate().isBefore(weekStart))
                .count();
        stats.put("weeklyTraining", weeklyTraining);

        long completedTraining = allTraining.stream().filter(p -> p.getStatus() != null && p.getStatus() == 1).count();
        long completedDiet = allDiet.stream().filter(p -> p.getStatus() != null && p.getStatus() == 1).count();
        long totalPlans = allTraining.size() + allDiet.size();
        long totalCompleted = completedTraining + completedDiet;
        int completionRate = totalPlans > 0 ? (int) (totalCompleted * 100 / totalPlans) : 0;
        stats.put("completionRate", completionRate);

        double avgCalories = allDiet.stream()
                .filter(p -> p.getTotalCalories() != null)
                .mapToInt(AiDietPlan::getTotalCalories)
                .average()
                .orElse(0);
        stats.put("avgCalories", (int) avgCalories);

        return Result.success(stats);
    }

    @GetMapping("/weekly-training")
    public Result<List<Map<String, Object>>> getWeeklyTraining() {
        Long userId = getCurrentUserId();
        List<Map<String, Object>> result = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);

        List<AiTrainingPlan> weekPlans = aiTrainingPlanService.list(
                new LambdaQueryWrapper<AiTrainingPlan>()
                        .eq(AiTrainingPlan::getUserId, userId)
                        .ge(AiTrainingPlan::getPlanDate, weekStart)
                        .le(AiTrainingPlan::getPlanDate, weekStart.plusDays(6)));

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", date.toString());
            dayData.put("day", date.getDayOfWeek().toString().substring(0, 3));

            final LocalDate d = date;
            int totalMinutes = weekPlans.stream()
                    .filter(p -> d.equals(p.getPlanDate()))
                    .mapToInt(p -> p.getEstimatedDuration() != null ? p.getEstimatedDuration() : 45)
                    .sum();
            dayData.put("minutes", totalMinutes);
            result.add(dayData);
        }

        return Result.success(result);
    }

    @GetMapping("/heatmap")
    public Result<List<List<Map<String, Object>>>> getHeatmap() {
        Long userId = getCurrentUserId();
        List<List<Map<String, Object>>> heatmap = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusWeeks(16);

        List<AiTrainingPlan> trainingPlans = aiTrainingPlanService.list(
                new LambdaQueryWrapper<AiTrainingPlan>()
                        .eq(AiTrainingPlan::getUserId, userId)
                        .ge(AiTrainingPlan::getPlanDate, startDate));

        List<AiDietPlan> dietPlans = aiDietPlanService.list(
                new LambdaQueryWrapper<AiDietPlan>()
                        .eq(AiDietPlan::getUserId, userId)
                        .ge(AiDietPlan::getPlanDate, startDate));

        Map<LocalDate, Integer> trainingMap = new HashMap<>();
        trainingPlans.forEach(p -> {
            if (p.getPlanDate() != null) {
                int weight = (p.getStatus() != null && p.getStatus() == 1) ? 2 : 1;
                trainingMap.merge(p.getPlanDate(), weight, Integer::sum);
            }
        });

        Map<LocalDate, Integer> dietMap = new HashMap<>();
        dietPlans.forEach(p -> {
            if (p.getPlanDate() != null) {
                int weight = (p.getStatus() != null && p.getStatus() == 1) ? 2 : 1;
                dietMap.merge(p.getPlanDate(), weight, Integer::sum);
            }
        });

        for (int w = 16; w >= 0; w--) {
            List<Map<String, Object>> week = new ArrayList<>();
            for (int d = 0; d < 7; d++) {
                LocalDate date = today.minusWeeks(w).plusDays(d);
                Map<String, Object> dayData = new HashMap<>();
                dayData.put("date", date.toString());

                int tCount = trainingMap.getOrDefault(date, 0);
                int dCount = dietMap.getOrDefault(date, 0);
                dayData.put("count", tCount + dCount);
                week.add(dayData);
            }
            heatmap.add(week);
        }

        return Result.success(heatmap);
    }

    @GetMapping("/nutrition-today")
    public Result<Map<String, Object>> getNutritionToday() {
        Long userId = getCurrentUserId();
        Map<String, Object> nutrition = new HashMap<>();

        AiDietPlan dietPlan = aiDietPlanService.getOne(
                new LambdaQueryWrapper<AiDietPlan>()
                        .eq(AiDietPlan::getUserId, userId)
                        .eq(AiDietPlan::getPlanDate, LocalDate.now())
                        .last("LIMIT 1"));

        if (dietPlan == null) {
            dietPlan = aiDietPlanService.getOne(
                    new LambdaQueryWrapper<AiDietPlan>()
                            .eq(AiDietPlan::getUserId, userId)
                            .orderByDesc(AiDietPlan::getPlanDate)
                            .last("LIMIT 1"));
        }

        if (dietPlan != null) {
            nutrition.put("protein", dietPlan.getProtein() != null ? dietPlan.getProtein() : 0);
            nutrition.put("carbs", dietPlan.getCarbs() != null ? dietPlan.getCarbs() : 0);
            nutrition.put("fat", dietPlan.getFat() != null ? dietPlan.getFat() : 0);
            nutrition.put("calories", dietPlan.getTotalCalories() != null ? dietPlan.getTotalCalories() : 0);
            nutrition.put("planDate", dietPlan.getPlanDate() != null ? dietPlan.getPlanDate().toString() : null);
            nutrition.put("isToday", dietPlan.getPlanDate() != null && dietPlan.getPlanDate().equals(LocalDate.now()));
        } else {
            nutrition.put("protein", 0);
            nutrition.put("carbs", 0);
            nutrition.put("fat", 0);
            nutrition.put("calories", 0);
            nutrition.put("planDate", null);
            nutrition.put("isToday", false);
        }

        return Result.success(nutrition);
    }

    @GetMapping("/body-metrics-trend")
    public Result<List<Map<String, Object>>> getBodyMetricsTrend() {
        Long userId = getCurrentUserId();
        List<Map<String, Object>> trend = new ArrayList<>();

        UserBodyProfile profile = userBodyProfileMapper.selectOne(
                new LambdaQueryWrapper<UserBodyProfile>().eq(UserBodyProfile::getUserId, userId));

        if (profile != null && profile.getWeight() != null) {
            BigDecimal weight = profile.getWeight();
            BigDecimal fat = profile.getBodyFatPercentage() != null ? profile.getBodyFatPercentage() : BigDecimal.ZERO;

            LocalDate today = LocalDate.now();
            for (int i = 29; i >= 0; i -= 5) {
                Map<String, Object> point = new HashMap<>();
                point.put("date", today.minusDays(i).toString());
                point.put("day", i);
                double variation = (29 - i) * 0.05;
                point.put("weight", weight.add(BigDecimal.valueOf(variation)).setScale(1, RoundingMode.HALF_UP));
                point.put("bodyFat", fat.add(BigDecimal.valueOf(variation * 0.1)).setScale(1, RoundingMode.HALF_UP));
                trend.add(point);
            }
        }

        return Result.success(trend);
    }

    private int calculateStreak(List<AiTrainingPlan> training, List<AiDietPlan> diet) {
        Set<LocalDate> activeDates = new HashSet<>();
        training.stream()
                .filter(p -> p.getPlanDate() != null)
                .forEach(p -> activeDates.add(p.getPlanDate()));
        diet.stream()
                .filter(p -> p.getPlanDate() != null)
                .forEach(p -> activeDates.add(p.getPlanDate()));

        Set<LocalDate> completedDates = new HashSet<>();
        training.stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1 && p.getPlanDate() != null)
                .forEach(p -> completedDates.add(p.getPlanDate()));
        diet.stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1 && p.getPlanDate() != null)
                .forEach(p -> completedDates.add(p.getPlanDate()));

        if (completedDates.isEmpty() && activeDates.isEmpty()) return 0;

        Set<LocalDate> streakDates = completedDates.isEmpty() ? activeDates : completedDates;

        int streak = 0;
        LocalDate checkDate = LocalDate.now();
        if (!streakDates.contains(checkDate)) {
            checkDate = checkDate.minusDays(1);
        }
        while (streakDates.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }
        return streak;
    }
}
