package com.fitmind.module.diet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fitmind.common.cache.CacheNames;
import com.fitmind.common.cache.UserCacheInvalidationService;
import com.fitmind.module.ai.dto.WeatherContextSnapshot;
import com.fitmind.module.ai.service.DeepSeekAiService;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.mapper.AiDietPlanMapper;
import com.fitmind.module.diet.service.IAiDietPlanService;
import com.fitmind.module.notification.service.INotificationService;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.service.IAiTrainingPlanService;
import com.fitmind.module.user.entity.UserBodyMetricLog;
import com.fitmind.module.user.entity.UserBodyProfile;
import com.fitmind.module.user.mapper.UserBodyMetricLogMapper;
import com.fitmind.module.user.mapper.UserBodyProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiDietPlanServiceImpl extends ServiceImpl<AiDietPlanMapper, AiDietPlan> implements IAiDietPlanService {

    private final DeepSeekAiService aiService;
    private final UserBodyProfileMapper profileMapper;
    private final ObjectMapper objectMapper;
    private final INotificationService notificationService;
    private final IAiTrainingPlanService aiTrainingPlanService;
    private final UserBodyMetricLogMapper userBodyMetricLogMapper;
    private final UserCacheInvalidationService cacheInvalidationService;

    @Override
    @Cacheable(cacheNames = CacheNames.DIET_TODAY, key = "#userId", unless = "#result == null")
    public AiDietPlan getPlanForToday(Long userId) {
        return this.getOne(new LambdaQueryWrapper<AiDietPlan>()
                .eq(AiDietPlan::getUserId, userId)
                .eq(AiDietPlan::getPlanDate, LocalDate.now()), false);
    }

    @Override
    public AiDietPlan generateAndSavePlan(Long userId) {
        return generateAndSavePlan(userId, null);
    }

    @Override
    public AiDietPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext) {
        return generateAndSavePlan(userId, weatherContext, null);
    }

    @Override
    public AiDietPlan generateAndSavePlan(Long userId, WeatherContextSnapshot weatherContext, String recognizedFoodsContext) {
        UserBodyProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<UserBodyProfile>().eq(UserBodyProfile::getUserId, userId));
        if (profile == null) {
            throw new RuntimeException("请先完善身体指标信息");
        }

        String recentTrainingContext = aiTrainingPlanService.buildRecentTrainingContext(userId);
        String recentDietContext = buildRecentDietContext(userId);
        String todayPlanContext = buildTodayScheduleContext(userId);
        String bodyTrendContext = buildBodyMetricTrendContext(userId, profile);

        String jsonContent;
        try {
            jsonContent = aiService.generateDietPlan(
                    profile,
                    recentTrainingContext,
                    recentDietContext,
                    weatherContext,
                    todayPlanContext,
                    bodyTrendContext,
                    recognizedFoodsContext);
        } catch (Exception e) {
            log.warn("AI diet generation failed, fallback to local template: {}", e.getMessage());
            jsonContent = buildFallbackDietPlan(profile);
        }

        jsonContent = ensureValidDietPlan(jsonContent, profile);

        AiDietPlan plan = new AiDietPlan();
        plan.setUserId(userId);
        plan.setPlanDate(LocalDate.now());
        plan.setStatus(0);
        plan.setContent(jsonContent);
        plan.setCreateTime(LocalDateTime.now());
        plan.setUpdateTime(LocalDateTime.now());

        try {
            JsonNode root = objectMapper.readTree(jsonContent);
            plan.setTotalCalories(root.path("totalCalories").asInt(0));
            plan.setProtein(BigDecimal.valueOf(root.path("protein").asDouble(0.0)));
            plan.setCarbs(BigDecimal.valueOf(root.path("carbs").asDouble(0.0)));
            plan.setFat(BigDecimal.valueOf(root.path("fat").asDouble(0.0)));
        } catch (Exception e) {
            log.warn("Diet plan parse failed after fallback, using default numeric values: {}", e.getMessage());
            JsonNode fallback = readFallbackNode(profile);
            plan.setTotalCalories(fallback.path("totalCalories").asInt(0));
            plan.setProtein(BigDecimal.valueOf(fallback.path("protein").asDouble(0.0)));
            plan.setCarbs(BigDecimal.valueOf(fallback.path("carbs").asDouble(0.0)));
            plan.setFat(BigDecimal.valueOf(fallback.path("fat").asDouble(0.0)));
            plan.setContent(fallback.toString());
        }

        this.remove(new LambdaQueryWrapper<AiDietPlan>()
                .eq(AiDietPlan::getUserId, userId)
                .eq(AiDietPlan::getPlanDate, LocalDate.now()));

        this.save(plan);
        cacheInvalidationService.evictDietPlanData(userId);
        return plan;
    }

    @Override
    public void checkIn(Long userId, Long planId) {
        AiDietPlan plan = this.getById(planId);
        if (plan == null || !plan.getUserId().equals(userId)) {
            throw new RuntimeException("计划不存在或无权操作");
        }
        if (plan.getStatus() != null && plan.getStatus() == 1) {
            throw new RuntimeException("计划已签到");
        }
        plan.setStatus(1);
        plan.setUpdateTime(LocalDateTime.now());
        this.updateById(plan);

        notificationService.sendNotification(userId, "DIET", "饮食打卡完成",
                "今日饮食计划已完成打卡，摄入热量: " + plan.getTotalCalories() + " kcal", plan.getId(), "diet");
        cacheInvalidationService.evictDietPlanData(userId);
    }

    private String buildRecentDietContext(Long userId) {
        List<AiDietPlan> recentPlans = this.list(new LambdaQueryWrapper<AiDietPlan>()
                .eq(AiDietPlan::getUserId, userId)
                .orderByDesc(AiDietPlan::getPlanDate)
                .orderByDesc(AiDietPlan::getCreateTime)
                .last("LIMIT 7"));

        if (recentPlans == null || recentPlans.isEmpty()) {
            return "近期暂无饮食计划记录，请按易执行、规律、不过度激进的方式生成今日方案。";
        }

        long completedCount = recentPlans.stream().filter(plan -> plan.getStatus() != null && plan.getStatus() == 1).count();
        int completionRate = (int) Math.round(completedCount * 100.0 / recentPlans.size());

        double avgCalories = recentPlans.stream()
                .map(AiDietPlan::getTotalCalories)
                .filter(v -> v != null && v > 0)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        String recentSummary = recentPlans.stream()
                .limit(3)
                .map(plan -> String.format("%s %s/%skcal/%s",
                        plan.getPlanDate(),
                        plan.getStatus() != null && plan.getStatus() == 1 ? "已完成" : "待执行",
                        plan.getTotalCalories() == null ? 0 : plan.getTotalCalories(),
                        summarizeMeals(plan)))
                .collect(Collectors.joining("；"));

        return String.format(
                "最近 %d 份饮食计划中，已完成 %d 份，完成率约 %d%%，平均计划热量约 %.0f kcal。最近记录示例：%s。请结合依从性和最近节奏，避免与近期饮食负荷严重脱节。",
                recentPlans.size(),
                completedCount,
                completionRate,
                avgCalories,
                recentSummary);
    }

    private String buildTodayScheduleContext(Long userId) {
        AiTrainingPlan todayTraining = aiTrainingPlanService.getPlanForToday(userId);
        if (todayTraining == null) {
            return "今日更接近休息日或未安排训练，请避免把碳水与总热量推到过高，优先保持恢复、饱腹感和执行稳定性。";
        }

        String target = todayTraining.getTargetMuscleGroup() == null || todayTraining.getTargetMuscleGroup().isBlank()
                ? "综合训练"
                : todayTraining.getTargetMuscleGroup();
        int duration = todayTraining.getEstimatedDuration() == null ? 0 : todayTraining.getEstimatedDuration();
        String status = todayTraining.getStatus() != null && todayTraining.getStatus() == 1 ? "今日训练已完成" : "今日存在待执行训练";

        return String.format(
                "%s，训练主题为%s，预计时长%d分钟。请据此决定碳水分配、训练前后进餐安排与补水策略。",
                status,
                target,
                duration);
    }

    private String buildBodyMetricTrendContext(Long userId, UserBodyProfile profile) {
        List<UserBodyMetricLog> logs = userBodyMetricLogMapper.selectList(new LambdaQueryWrapper<UserBodyMetricLog>()
                .eq(UserBodyMetricLog::getUserId, userId)
                .orderByDesc(UserBodyMetricLog::getRecordTime)
                .last("LIMIT 6"));

        if (logs == null || logs.isEmpty()) {
            return buildProfileFreshnessFallback(profile);
        }

        List<UserBodyMetricLog> ordered = logs.stream()
                .sorted((a, b) -> {
                    LocalDateTime at = a.getRecordTime() == null ? a.getCreateTime() : a.getRecordTime();
                    LocalDateTime bt = b.getRecordTime() == null ? b.getCreateTime() : b.getRecordTime();
                    if (at == null && bt == null) return 0;
                    if (at == null) return -1;
                    if (bt == null) return 1;
                    return at.compareTo(bt);
                })
                .collect(Collectors.toList());

        if (ordered.size() < 2) {
            return buildProfileFreshnessFallback(profile);
        }

        UserBodyMetricLog first = ordered.get(0);
        UserBodyMetricLog last = ordered.get(ordered.size() - 1);
        long days = resolveDaysBetween(first, last);

        List<String> facts = new java.util.ArrayList<>();
        String weightTrend = summarizeMetricTrend("体重", first.getWeight(), last.getWeight(), "kg", days);
        if (weightTrend != null) {
            facts.add(weightTrend);
        }
        String bodyFatTrend = summarizeMetricTrend("体脂率", first.getBodyFatPercentage(), last.getBodyFatPercentage(), "%", days);
        if (bodyFatTrend != null) {
            facts.add(bodyFatTrend);
        }

        if (facts.isEmpty()) {
            return buildProfileFreshnessFallback(profile);
        }

        return "最近身体指标趋势：" + String.join("；", facts) + "。请结合趋势控制今日热量与宏量营养分配，避免与近期变化方向冲突。";
    }

    private String ensureValidDietPlan(String content, UserBodyProfile profile) {
        try {
            JsonNode root = readJsonObject(content);
            if (root == null || !root.isObject()) {
                return buildFallbackDietPlan(profile);
            }

            ObjectNode normalized = normalizeDietPlan(root);
            boolean invalid = normalized.path("totalCalories").asInt(0) <= 0
                    || normalized.path("protein").asDouble(0) <= 0
                    || normalized.path("carbs").asDouble(0) <= 0
                    || normalized.path("fat").asDouble(0) <= 0
                    || !normalized.path("meals").isArray()
                    || normalized.path("meals").size() < 3;

            if (invalid) {
                return buildFallbackDietPlan(profile);
            }

            for (JsonNode meal : normalized.path("meals")) {
                if (meal.path("mealName").asText("").isBlank()
                        || !meal.path("items").isArray()
                        || meal.path("items").isEmpty()) {
                    return buildFallbackDietPlan(profile);
                }
            }

            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            log.warn("Diet plan JSON invalid, fallback to local template: {}", e.getMessage());
            return buildFallbackDietPlan(profile);
        }
    }

    private JsonNode readJsonObject(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(content);
        } catch (Exception ignored) {
        }

        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }

        try {
            return objectMapper.readTree(content.substring(start, end + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private ObjectNode normalizeDietPlan(JsonNode root) {
        ObjectNode normalized = objectMapper.createObjectNode();

        int totalCalories = readInt(root, "totalCalories", "total_calories", "calories", "总热量", "热量");
        int tdee = readInt(root, "tdee", "TDEE");
        int targetCalories = readInt(root, "targetCalories", "target_calories", "target", "目标热量");
        double protein = readDouble(root, "protein", "蛋白质");
        double carbs = readDouble(root, "carbs", "carbohydrates", "碳水", "碳水化合物");
        double fat = readDouble(root, "fat", "fats", "脂肪");

        normalized.put("totalCalories", totalCalories);
        normalized.put("tdee", tdee > 0 ? tdee : totalCalories);
        normalized.put("targetCalories", targetCalories > 0 ? targetCalories : totalCalories);
        normalized.put("calorieStrategy", readText(root, "calorieStrategy", "strategy", "热量策略"));
        normalized.put("protein", protein);
        normalized.put("carbs", carbs);
        normalized.put("fat", fat);
        normalized.put("hydration", readText(root, "hydration", "water", "水分建议", "饮水建议"));

        JsonNode macroStrategyNode = findFirst(root, "macroStrategy", "macro_strategy");
        if (macroStrategyNode != null && macroStrategyNode.isObject()) {
            ObjectNode macroStrategy = objectMapper.createObjectNode();
            String proteinRule = readText(macroStrategyNode, "proteinRule", "protein", "蛋白质");
            String carbsRule = readText(macroStrategyNode, "carbsRule", "carbs", "碳水");
            String fatRule = readText(macroStrategyNode, "fatRule", "fat", "脂肪");
            if (!proteinRule.isBlank()) {
                macroStrategy.put("proteinRule", proteinRule);
            }
            if (!carbsRule.isBlank()) {
                macroStrategy.put("carbsRule", carbsRule);
            }
            if (!fatRule.isBlank()) {
                macroStrategy.put("fatRule", fatRule);
            }
            if (macroStrategy.size() > 0) {
                normalized.set("macroStrategy", macroStrategy);
            }
        }

        JsonNode tipsNode = findFirst(root, "tips", "suggestions", "advice");
        if (tipsNode != null) {
            ArrayNode tips = objectMapper.createArrayNode();
            if (tipsNode.isArray()) {
                for (JsonNode tip : tipsNode) {
                    String text = tip.asText("").trim();
                    if (!text.isBlank()) {
                        tips.add(text);
                    }
                }
            } else {
                String text = tipsNode.asText("").trim();
                if (!text.isBlank()) {
                    tips.add(text);
                }
            }
            if (!tips.isEmpty()) {
                normalized.set("tips", tips);
            }
        }

        ArrayNode meals = objectMapper.createArrayNode();
        JsonNode mealNodes = findFirst(root, "meals", "mealPlan", "dietMeals", "meal_list", "餐食", "餐次");
        if (mealNodes != null && mealNodes.isArray()) {
            int index = 0;
            for (JsonNode mealNode : mealNodes) {
                ObjectNode normalizedMeal = normalizeMeal(mealNode, index++);
                if (normalizedMeal != null) {
                    meals.add(normalizedMeal);
                }
            }
        }
        normalized.set("meals", meals);

        return normalized;
    }

    private ObjectNode normalizeMeal(JsonNode mealNode, int index) {
        if (mealNode == null || mealNode.isNull()) {
            return null;
        }

        ObjectNode meal = objectMapper.createObjectNode();
        String mealName = readText(mealNode, "mealName", "name", "title", "餐名", "餐次");
        if (mealName.isBlank()) {
            mealName = defaultMealName(index);
        }
        meal.put("mealName", mealName);

        ArrayNode items = objectMapper.createArrayNode();
        JsonNode itemNodes = findFirst(mealNode, "items", "foods", "foodItems", "ingredients", "食物");
        if (itemNodes != null && itemNodes.isArray()) {
            for (JsonNode itemNode : itemNodes) {
                ObjectNode normalizedItem = normalizeMealItem(itemNode);
                if (normalizedItem != null) {
                    items.add(normalizedItem);
                }
            }
        }

        if (items.isEmpty()) {
            return null;
        }

        int mealCalories = readInt(mealNode, "mealCalories", "calories", "kcal", "热量");
        if (mealCalories <= 0) {
            mealCalories = 0;
            for (JsonNode item : items) {
                mealCalories += item.path("calories").asInt(0);
            }
        }

        meal.put("mealCalories", mealCalories);
        meal.set("items", items);
        return meal;
    }

    private ObjectNode normalizeMealItem(JsonNode itemNode) {
        if (itemNode == null || itemNode.isNull()) {
            return null;
        }

        ObjectNode item = objectMapper.createObjectNode();
        if (itemNode.isTextual()) {
            String name = itemNode.asText("").trim();
            if (name.isBlank()) {
                return null;
            }
            item.put("name", name);
            item.put("amount", "");
            item.put("calories", 0);
            return item;
        }

        String name = readText(itemNode, "name", "foodName", "title", "食物", "食材");
        if (name.isBlank()) {
            return null;
        }

        item.put("name", name);
        item.put("amount", readText(itemNode, "amount", "quantity", "portion", "serving", "分量", "份量"));
        item.put("calories", readInt(itemNode, "calories", "kcal", "energy", "热量"));
        return item;
    }

    private JsonNode findFirst(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode child = node.get(fieldName);
            if (child != null && !child.isNull()) {
                return child;
            }
        }
        return null;
    }

    private String readText(JsonNode node, String... fieldNames) {
        JsonNode value = findFirst(node, fieldNames);
        if (value == null) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText("").trim();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText("").trim();
        }
        return "";
    }

    private int readInt(JsonNode node, String... fieldNames) {
        JsonNode value = findFirst(node, fieldNames);
        if (value == null) {
            return 0;
        }
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        if (value.isNumber()) {
            return BigDecimal.valueOf(value.asDouble()).setScale(0, RoundingMode.HALF_UP).intValue();
        }

        String text = value.asText("").trim();
        if (text.isBlank()) {
            return 0;
        }

        String normalized = text.replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized)) {
            return 0;
        }

        try {
            return new BigDecimal(normalized).setScale(0, RoundingMode.HALF_UP).intValue();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double readDouble(JsonNode node, String... fieldNames) {
        JsonNode value = findFirst(node, fieldNames);
        if (value == null) {
            return 0;
        }
        if (value.isNumber()) {
            return value.asDouble();
        }

        String text = value.asText("").trim();
        if (text.isBlank()) {
            return 0;
        }

        String normalized = text.replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized) || ".".equals(normalized)) {
            return 0;
        }

        try {
            return new BigDecimal(normalized).setScale(1, RoundingMode.HALF_UP).doubleValue();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String defaultMealName(int index) {
        switch (index) {
            case 0:
                return "早餐";
            case 1:
                return "午餐";
            case 2:
                return "晚餐";
            case 3:
                return "加餐";
            default:
                return "餐次" + (index + 1);
        }
    }

    private JsonNode readFallbackNode(UserBodyProfile profile) {
        try {
            return objectMapper.readTree(buildFallbackDietPlan(profile));
        } catch (Exception e) {
            throw new RuntimeException("Fallback diet plan build failed", e);
        }
    }

    private String buildFallbackDietPlan(UserBodyProfile profile) {
        BigDecimal weight = safeDecimal(profile.getWeight(), BigDecimal.valueOf(65));
        BigDecimal height = safeDecimal(profile.getHeight(), BigDecimal.valueOf(170));
        int age = profile.getAge() == null || profile.getAge() <= 0 ? 25 : profile.getAge();
        boolean male = profile.getGender() == null || profile.getGender() == 1;

        double bmr = male
                ? 10 * weight.doubleValue() + 6.25 * height.doubleValue() - 5 * age + 5
                : 10 * weight.doubleValue() + 6.25 * height.doubleValue() - 5 * age - 161;
        double tdee = bmr * resolveActivityFactor(profile.getActivityLevel());

        String goal = resolveGoal(profile).toLowerCase();
        double calorieFactor = goal.contains("减") || goal.contains("fat") ? 0.8
                : goal.contains("增") || goal.contains("muscle") ? 1.1
                : 1.0;
        int targetCalories = roundInt(tdee * calorieFactor);

        double proteinPerKg = goal.contains("减") || goal.contains("fat") ? 2.0
                : goal.contains("增") || goal.contains("muscle") ? 2.2
                : 1.8;
        double carbsPerKg = goal.contains("减") || goal.contains("fat") ? 3.0
                : goal.contains("增") || goal.contains("muscle") ? 5.0
                : 4.0;
        double fatPerKg = goal.contains("减") || goal.contains("fat") ? 0.8
                : goal.contains("增") || goal.contains("muscle") ? 1.0
                : 0.9;

        int protein = roundInt(weight.doubleValue() * proteinPerKg);
        int carbs = roundInt(weight.doubleValue() * carbsPerKg);
        int fat = roundInt(weight.doubleValue() * fatPerKg);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("totalCalories", targetCalories);
        root.put("tdee", roundInt(tdee));
        root.put("targetCalories", targetCalories);
        root.put("calorieStrategy", buildCalorieStrategy(goal));
        root.put("protein", protein);
        root.put("carbs", carbs);
        root.put("fat", fat);

        ObjectNode macroStrategy = objectMapper.createObjectNode();
        macroStrategy.put("proteinRule", formatRule(proteinPerKg));
        macroStrategy.put("carbsRule", formatRule(carbsPerKg));
        macroStrategy.put("fatRule", formatRule(fatPerKg));
        root.set("macroStrategy", macroStrategy);

        ArrayNode meals = objectMapper.createArrayNode();
        meals.add(buildMeal("早餐", targetCalories, 0.25,
                mealItem("燕麦", "60g", 230),
                mealItem("鸡蛋", "2个", 140),
                mealItem("牛奶", "250ml", 120)));
        meals.add(buildMeal("午餐", targetCalories, 0.35,
                mealItem("米饭", "200g", 230),
                mealItem("鸡胸肉", "180g", 300),
                mealItem("西兰花", "200g", 70)));
        meals.add(buildMeal("晚餐", targetCalories, 0.30,
                mealItem("红薯", "250g", 220),
                mealItem("鱼肉", "180g", 260),
                mealItem("时蔬沙拉", "1份", 90)));
        meals.add(buildMeal("加餐", targetCalories, 0.10,
                mealItem("无糖酸奶", "200g", 110),
                mealItem("香蕉", "1根", 100),
                mealItem("坚果", "15g", 90)));
        root.set("meals", meals);

        root.put("hydration", "建议全天饮水 2-2.5L，训练前后适当补水。");
        ArrayNode tips = objectMapper.createArrayNode();
        tips.add("优先保证蛋白质摄入。");
        tips.add("主食尽量分配到白天和训练前后。");
        tips.add("避免高糖饮料和过度油炸食物。");
        root.set("tips", tips);

        return root.toString();
    }

    private String summarizeMeals(AiDietPlan plan) {
        try {
            JsonNode root = objectMapper.readTree(plan.getContent());
            JsonNode meals = root.path("meals");
            if (!meals.isArray() || meals.isEmpty()) {
                return "无餐次明细";
            }
            return streamMeals(meals)
                    .limit(2)
                    .map(meal -> meal.path("mealName").asText("未命名餐次"))
                    .collect(Collectors.joining("+"));
        } catch (Exception e) {
            return "餐次解析失败";
        }
    }

    private java.util.stream.Stream<JsonNode> streamMeals(JsonNode meals) {
        List<JsonNode> nodes = new java.util.ArrayList<>();
        meals.forEach(nodes::add);
        return nodes.stream();
    }

    private ObjectNode buildMeal(String mealName, int totalCalories, double ratio, ObjectNode... items) {
        ObjectNode meal = objectMapper.createObjectNode();
        meal.put("mealName", mealName);
        int mealCalories = roundInt(totalCalories * ratio);
        meal.put("mealCalories", mealCalories);
        ArrayNode array = objectMapper.createArrayNode();
        for (ObjectNode item : items) {
            array.add(item);
        }
        meal.set("items", array);
        return meal;
    }

    private ObjectNode mealItem(String name, String amount, int calories) {
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", name);
        item.put("amount", amount);
        item.put("calories", calories);
        return item;
    }

    private String buildCalorieStrategy(String goal) {
        if (goal.contains("减") || goal.contains("fat")) {
            return "减脂目标，按 TDEE 的 80% 生成保守热量方案";
        }
        if (goal.contains("增") || goal.contains("muscle")) {
            return "增肌目标，按 TDEE 的 110% 生成轻盈余热量方案";
        }
        return "维持目标，按估算 TDEE 生成平衡热量方案";
    }

    private String formatRule(double perKg) {
        BigDecimal value = BigDecimal.valueOf(perKg).setScale(1, RoundingMode.HALF_UP);
        return value.stripTrailingZeros().toPlainString() + "g/kg体重";
    }

    private String resolveGoal(UserBodyProfile profile) {
        String goal = profile.getTrainingGoals();
        if (goal == null || goal.isBlank()) {
            goal = profile.getFitnessGoal();
        }
        return goal == null ? "维持" : goal;
    }

    private double resolveActivityFactor(String activityLevel) {
        if (activityLevel == null || activityLevel.isBlank()) {
            return 1.55;
        }
        String level = activityLevel.toLowerCase();
        if (level.contains("久坐") || level.contains("sedentary")) return 1.2;
        if (level.contains("轻") || level.contains("light")) return 1.375;
        if (level.contains("高") || level.contains("heavy") || level.contains("active")) return 1.725;
        return 1.55;
    }

    private BigDecimal safeDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0 ? fallback : value;
    }

    private int roundInt(double value) {
        return (int) Math.round(value);
    }

    private long resolveDaysBetween(UserBodyMetricLog first, UserBodyMetricLog last) {
        LocalDateTime start = first.getRecordTime() == null ? first.getCreateTime() : first.getRecordTime();
        LocalDateTime end = last.getRecordTime() == null ? last.getCreateTime() : last.getRecordTime();
        if (start == null || end == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()));
    }

    private String summarizeMetricTrend(String label, BigDecimal first, BigDecimal last, String unit, long days) {
        if (first == null || last == null) {
            return null;
        }
        BigDecimal delta = last.subtract(first).setScale(1, RoundingMode.HALF_UP);
        int direction = delta.compareTo(BigDecimal.ZERO);
        String window = days > 0 ? String.format("最近%d天", days) : "最近几次记录";

        if (direction == 0) {
            return String.format("%s%s基本稳定（当前%s%s）", window, label,
                    last.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(), unit);
        }

        String trend = direction > 0 ? "上升" : "下降";
        BigDecimal absDelta = delta.abs().setScale(1, RoundingMode.HALF_UP);
        return String.format("%s%s%s约%s%s（当前%s%s）",
                window,
                label,
                trend,
                absDelta.stripTrailingZeros().toPlainString(),
                unit,
                last.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
                unit);
    }

    private String buildProfileFreshnessFallback(UserBodyProfile profile) {
        if (profile == null || profile.getLastProfileUpdateTime() == null) {
            return "暂无足够的身体指标历史趋势，本次主要依据当前身体档案生成饮食计划。";
        }
        long daysSinceUpdate = Math.max(0, ChronoUnit.DAYS.between(profile.getLastProfileUpdateTime().toLocalDate(), LocalDate.now()));
        return String.format("暂无足够的身体指标历史趋势，当前档案最近一次更新于%d天前，本次主要依据当前身体档案生成饮食计划。", daysSinceUpdate);
    }
}
