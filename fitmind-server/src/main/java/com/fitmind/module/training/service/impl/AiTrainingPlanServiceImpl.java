package com.fitmind.module.training.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fitmind.module.ai.dto.WeatherContextSnapshot;
import com.fitmind.module.ai.service.DeepSeekAiService;
import com.fitmind.module.notification.service.INotificationService;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.mapper.AiTrainingPlanMapper;
import com.fitmind.module.training.service.IAiTrainingPlanService;
import com.fitmind.module.training.strategy.ExerciseStrategyFactory;
import com.fitmind.module.training.strategy.ExerciseType;
import com.fitmind.module.user.entity.UserBodyProfile;
import com.fitmind.module.user.mapper.UserBodyProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTrainingPlanServiceImpl extends ServiceImpl<AiTrainingPlanMapper, AiTrainingPlan> implements IAiTrainingPlanService {

    private final DeepSeekAiService aiService;
    private final UserBodyProfileMapper profileMapper;
    private final ObjectMapper objectMapper;
    private final INotificationService notificationService;
    private final ExerciseStrategyFactory strategyFactory;

    @Override
    public AiTrainingPlan getPlanForToday(Long userId) {
        return this.getOne(new LambdaQueryWrapper<AiTrainingPlan>()
                .eq(AiTrainingPlan::getUserId, userId)
                .eq(AiTrainingPlan::getPlanDate, LocalDate.now()), false);
    }

    @Override
    public AiTrainingPlan generateAndSavePlan(Long userId) {
        return generateAndSavePlan(userId, null, null, false);
    }

    @Override
    public AiTrainingPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext) {
        return generateAndSavePlan(userId, weatherContext, null, false);
    }

    @Override
    public AiTrainingPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext, String targetMuscleGroup) {
        return generateAndSavePlan(userId, weatherContext, targetMuscleGroup, false);
    }

    @Override
    public AiTrainingPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext,
                                              String targetMuscleGroup, boolean replaceExisting) {
        UserBodyProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<UserBodyProfile>().eq(UserBodyProfile::getUserId, userId));
        if (profile == null) {
            throw new RuntimeException("请先完善身体指标信息");
        }

        String recentTrainingContext = buildRecentTrainingContext(userId);
        String jsonContent = aiService.generateTrainingPlan(profile, recentTrainingContext, weatherContext, targetMuscleGroup);
        jsonContent = migrateContentIfNeeded(jsonContent);

        AiTrainingPlan plan = replaceExisting ? getPlanForToday(userId) : null;
        boolean isNewPlan = plan == null;
        if (plan == null) {
            plan = new AiTrainingPlan();
            plan.setUserId(userId);
            plan.setPlanDate(LocalDate.now());
            plan.setCreateTime(LocalDateTime.now());
        }
        plan.setStatus(0);
        plan.setContent(jsonContent);
        plan.setUpdateTime(LocalDateTime.now());

        try {
            JsonNode root = objectMapper.readTree(jsonContent);
            plan.setPlanName(root.path("planName").asText("AI Training"));
            plan.setTargetMuscleGroup(root.path("targetMuscleGroup").asText("Unknown"));
            plan.setEstimatedDuration(root.path("estimatedDuration").asInt(0));
        } catch (Exception e) {
            plan.setPlanName("AI Training (Parse Error)");
        }

        if (isNewPlan) {
            this.save(plan);
        } else {
            this.updateById(plan);
        }
        return plan;
    }

    @Override
    public void checkIn(Long userId, Long planId) {
        AiTrainingPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new RuntimeException("计划不存在或无权操作");
        }
        if (plan.getStatus() == 1) {
            throw new RuntimeException("计划已签到");
        }
        plan.setStatus(1);
        plan.setUpdateTime(LocalDateTime.now());
        this.updateById(plan);

        notificationService.sendNotification(userId, "TRAINING", "训练完成",
                "训练协议已完成 " + plan.getPlanName(), plan.getId(), "training");

        triggerDynamicAdjustment(userId);
    }

    @Override
    public String buildRecentTrainingContext(Long userId) {
        List<AiTrainingPlan> recentPlans = this.list(new LambdaQueryWrapper<AiTrainingPlan>()
                .eq(AiTrainingPlan::getUserId, userId)
                .orderByDesc(AiTrainingPlan::getPlanDate)
                .orderByDesc(AiTrainingPlan::getCreateTime)
                .last("LIMIT 7"));

        if (recentPlans == null || recentPlans.isEmpty()) {
            return "近期暂无训练计划记录，请按入门且保守的方式安排今天的训练。";
        }

        long completedCount = recentPlans.stream().filter(plan -> plan.getStatus() != null && plan.getStatus() == 1).count();
        long pendingCount = recentPlans.size() - completedCount;
        int completionRate = (int) Math.round(completedCount * 100.0 / recentPlans.size());

        String recentTargets = recentPlans.stream()
                .limit(3)
                .map(plan -> String.format("%s %s/%s/%s分钟",
                        plan.getPlanDate(),
                        safeText(plan.getPlanName(), "未命名计划"),
                        safeText(plan.getTargetMuscleGroup(), "综合"),
                        plan.getEstimatedDuration() == null ? 0 : plan.getEstimatedDuration()))
                .collect(Collectors.joining("；"));

        return String.format(
                "最近 7 次训练计划中，已完成 %d 次，未完成 %d 次，完成率约 %d%%。最近计划示例：%s。请结合近期负荷、恢复状态和可持续性控制今天训练强度，避免突然堆高容量。",
                completedCount,
                pendingCount,
                completionRate,
                recentTargets);
    }

    private void triggerDynamicAdjustment(Long userId) {
        try {
            List<AiTrainingPlan> recentPlans = this.list(new LambdaQueryWrapper<AiTrainingPlan>()
                    .eq(AiTrainingPlan::getUserId, userId)
                    .orderByDesc(AiTrainingPlan::getPlanDate)
                    .last("LIMIT 7"));

            long completedCount = recentPlans.stream().filter(p -> p.getStatus() != null && p.getStatus() == 1).count();
            long totalCount = recentPlans.size();

            if (totalCount >= 3 && completedCount >= 2) {
                UserBodyProfile profile = profileMapper.selectOne(
                        new LambdaQueryWrapper<UserBodyProfile>().eq(UserBodyProfile::getUserId, userId));

                if (profile != null) {
                    String checkInData = String.format(
                            "Last %d days: %d completed out of %d plans. Completion rate: %d%%",
                            totalCount, completedCount, totalCount,
                            totalCount > 0 ? (completedCount * 100 / totalCount) : 0);

                    String adjustedPlan = aiService.generateAdjustedPlan(profile, checkInData);
                    log.info("Dynamic adjustment generated for user {}: {}", userId, adjustedPlan);

                    notificationService.sendNotification(userId, "TRAINING", "计划调整建议",
                            "AI已根据您近期的训练表现生成了调整建议，请查看明天的训练计划。", null, "training");
                }
            }
        } catch (Exception e) {
            log.warn("Dynamic adjustment failed for user {}: {}", userId, e.getMessage());
        }
    }

    private String migrateContentIfNeeded(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode exercises = root.path("exercises");
            if (!exercises.isArray()) return content;

            boolean modified = false;
            for (JsonNode ex : exercises) {
                if (ex instanceof ObjectNode && !ex.has("type")) {
                    ((ObjectNode) ex).put("type", ExerciseType.STRENGTH.getValue());
                    modified = true;
                }
            }

            return modified ? objectMapper.writeValueAsString(root) : content;
        } catch (Exception e) {
            log.warn("Content migration failed: {}", e.getMessage());
            return content;
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
