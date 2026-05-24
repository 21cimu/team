package com.fitmind.module.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmind.module.ai.dto.WeatherContextSnapshot;
import com.fitmind.module.ai.entity.ChatMessage;
import com.fitmind.module.exercise.entity.Exercise;
import com.fitmind.module.exercise.service.IExerciseService;
import com.fitmind.module.user.entity.UserBodyProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekAiService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    private static final int MAX_HISTORY_MESSAGES = 20;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final IChatMessageService chatMessageService;
    private final PromptLoader promptLoader;
    private final PromptTemplateService templateService;
    private final IExerciseService exerciseService;

    @Value("${ai.deepseek.api-url}")
    private String apiUrl;

    @Value("${ai.deepseek.api-key}")
    private String apiKey;

    public String generateTrainingPlan(UserBodyProfile profile) {
        return generateTrainingPlan(profile, null, null);
    }

    public String generateTrainingPlan(UserBodyProfile profile, String trainingContext, WeatherContextSnapshot weatherContext) {
        return generateTrainingPlan(profile, trainingContext, weatherContext, null);
    }

    public String generateTrainingPlan(UserBodyProfile profile, String trainingContext, WeatherContextSnapshot weatherContext, String targetMuscleGroup) {
        String systemContent = promptLoader.getSystem("FITMIND_TRAINING_PLAN");
        String userTemplate = promptLoader.getUserTemplate("FITMIND_TRAINING_PLAN");

        String exerciseCandidates = buildExerciseCandidates();

        Map<String, String> vars = buildProfileVars(profile);
        vars.put("EXERCISE_CANDIDATES", exerciseCandidates);

        String userContent = templateService.render(userTemplate, vars);
        String supplement = buildTrainingGenerationSupplement(profile, trainingContext, weatherContext, targetMuscleGroup);
        if (!supplement.isBlank()) {
            userContent = userContent
                    + "\n\n补充上下文：\n"
                    + supplement
                    + "\n\n请结合以上身体状态、近期训练表现和天气条件调整训练类型、场地、强度、时长、补水与热身安排，但仍然只返回合法 JSON。";
        }

        return callDeepSeekWithSystemUser(systemContent, userContent);
    }

    public String generateDietPlan(UserBodyProfile profile) {
        return generateDietPlan(profile, null, null, null, null, null, null);
    }

    public String generateDietPlan(UserBodyProfile profile, String trainingContext, String dietHistoryContext,
                                   WeatherContextSnapshot weatherContext, String dayScheduleContext, String bodyMetricTrendContext,
                                   String recognizedFoodsContext) {
        String systemContent = promptLoader.getSystem("FITMIND_DIET_PLAN");
        String userTemplate = promptLoader.getUserTemplate("FITMIND_DIET_PLAN");

        Map<String, String> vars = buildProfileVars(profile);
        vars.put("DIET_CONTEXT_SUPPLEMENT", buildDietGenerationSupplement(
                profile,
                trainingContext,
                dietHistoryContext,
                weatherContext,
                dayScheduleContext,
                bodyMetricTrendContext,
                recognizedFoodsContext));
        String userContent = templateService.render(userTemplate, vars);

        return callDeepSeekWithSystemUser(systemContent, userContent);
    }

    public String chat(Long userId, String sessionId, String message, UserBodyProfile profile) {
        return chat(userId, sessionId, message, profile, null, null);
    }

    public String chat(Long userId, String sessionId, String message, UserBodyProfile profile,
                       String trainingContext, WeatherContextSnapshot weatherContext) {
        List<Map<String, Object>> messages = buildChatMessages(userId, sessionId, message, profile, trainingContext, weatherContext);

        chatMessageService.saveMessage(userId, sessionId, "user", message);

        String aiResponse = callDeepSeekChat(messages);
        chatMessageService.saveMessage(userId, sessionId, "assistant", aiResponse);

        return aiResponse;
    }

    public void streamChat(Long userId, String sessionId, String message, UserBodyProfile profile,
                           String trainingContext, WeatherContextSnapshot weatherContext, SseEmitter emitter) {
        chatMessageService.saveMessage(userId, sessionId, "user", message);
        List<Map<String, Object>> messages = buildChatMessages(userId, sessionId, message, profile, trainingContext, weatherContext);

        CompletableFuture.runAsync(() -> {
            StringBuilder assistantContent = new StringBuilder();
            try {
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "session",
                        "sessionId", sessionId
                )));

                if (apiKey.contains("xxx")) {
                    String mock = "这是来自 FitMind AI 教练的模拟回复。您的 API 密钥尚未配置。建议您专注于渐进式训练，并根据天气和恢复状态灵活调整今天计划。";
                    assistantContent.append(mock);
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "chunk",
                            "sessionId", sessionId,
                            "content", mock
                    )));
                    chatMessageService.saveMessage(userId, sessionId, "assistant", assistantContent.toString());
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "done",
                            "sessionId", sessionId
                    )));
                    emitter.complete();
                    return;
                }

                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", "deepseek-chat");
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.7);
                requestBody.put("stream", true);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl + "/chat/completions"))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                        .build();

                HttpResponse<java.io.InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeException("DeepSeek stream failed: " + errorBody);
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank() || !line.startsWith("data:")) {
                            continue;
                        }

                        String payload = line.substring(5).trim();
                        if ("[DONE]".equals(payload)) {
                            break;
                        }

                        JsonNode root = objectMapper.readTree(payload);
                        JsonNode delta = root.path("choices").path(0).path("delta");
                        String chunk = delta.path("content").asText("");
                        if (chunk.isEmpty()) {
                            continue;
                        }

                        assistantContent.append(chunk);
                        emitter.send(SseEmitter.event().data(Map.of(
                                "type", "chunk",
                                "sessionId", sessionId,
                                "content", chunk
                        )));
                    }
                }

                chatMessageService.saveMessage(userId, sessionId, "assistant", assistantContent.toString());
                emitter.send(SseEmitter.event().data(Map.of(
                        "type", "done",
                        "sessionId", sessionId
                )));
                emitter.complete();
            } catch (Exception e) {
                log.error("[DeepSeekAiService] stream chat failed", e);
                try {
                    emitter.send(SseEmitter.event().data(Map.of(
                            "type", "error",
                            "sessionId", sessionId,
                            "message", "AI 服务异常，请稍后重试"
                    )));
                } catch (Exception ignored) {
                }
                emitter.completeWithError(e);
            }
        });
    }

    public String chat(String message, UserBodyProfile profile) {
        return chat(message, profile, null, null);
    }

    public String chat(String message, UserBodyProfile profile, String trainingContext, WeatherContextSnapshot weatherContext) {
        String systemContent = promptLoader.getSystem("FITMIND_QA_COACH");

        String userTemplate = promptLoader.getUserTemplate("FITMIND_QA_COACH");
        Map<String, String> vars = new HashMap<>();
        vars.put("USER_CONTEXT", buildUserContextText(profile, trainingContext, weatherContext));
        vars.put("CHAT_HISTORY", "");
        vars.put("USER_INPUT", message);
        String userContent = templateService.render(userTemplate, vars);

        return callDeepSeekWithSystemUser(systemContent, userContent);
    }

    public String generateAdjustedPlan(UserBodyProfile profile, String checkInData) {
        String systemContent = promptLoader.getSystem("FITMIND_TRAINING_PLAN");
        String userTemplate = promptLoader.getUserTemplate("FITMIND_TRAINING_PLAN");

        String exerciseCandidates = buildExerciseCandidates();

        Map<String, String> vars = buildProfileVars(profile);
        vars.put("EXERCISE_CANDIDATES", exerciseCandidates);

        String userContent = templateService.render(userTemplate, vars)
                + "\n\n近期训练完成情况：" + checkInData;

        return callDeepSeekWithSystemUser(systemContent, userContent);
    }

    private List<Map<String, Object>> buildChatMessages(Long userId, String sessionId, String message, UserBodyProfile profile,
                                                        String trainingContext, WeatherContextSnapshot weatherContext) {
        List<Map<String, Object>> messages = new ArrayList<>();

        String systemContent = promptLoader.getSystem("FITMIND_QA_COACH");
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemContent);
        messages.add(systemMsg);

        List<ChatMessage> history = chatMessageService.getRecentMessages(userId, sessionId, MAX_HISTORY_MESSAGES);
        for (ChatMessage historyMessage : history) {
            Map<String, Object> item = new HashMap<>();
            item.put("role", historyMessage.getRole());
            item.put("content", historyMessage.getContent());
            messages.add(item);
        }

        String userTemplate = promptLoader.getUserTemplate("FITMIND_QA_COACH");
        Map<String, String> vars = new HashMap<>();
        vars.put("USER_CONTEXT", buildUserContextText(profile, trainingContext, weatherContext));
        vars.put("CHAT_HISTORY", "");
        vars.put("USER_INPUT", message);
        String userContent = templateService.render(userTemplate, vars);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);
        return messages;
    }

    private Map<String, String> buildProfileVars(UserBodyProfile profile) {
        Map<String, String> vars = new HashMap<>();
        if (profile == null) {
            vars.put("height", "未知");
            vars.put("weight", "未知");
            vars.put("age", "未知");
            vars.put("gender", "未知");
            vars.put("bodyFat", "未知");
            vars.put("goal", "综合健身");
            vars.put("activityLevel", "中等活动水平");
            vars.put("hasInjury", "未知");
            vars.put("injuryParts", "无");
            return vars;
        }
        vars.put("height", valueOrUnknown(profile.getHeight()));
        vars.put("weight", valueOrUnknown(profile.getWeight()));
        vars.put("age", valueOrUnknown(profile.getAge()));
        vars.put("gender", resolveGender(profile.getGender()));
        vars.put("bodyFat", valueOrUnknown(profile.getBodyFatPercentage()));
        String goal = profile.getTrainingGoals();
        if (goal == null || goal.isBlank()) {
            goal = profile.getFitnessGoal();
        }
        vars.put("goal", valueOrUnknown(goal));
        vars.put("activityLevel", valueOrUnknown(profile.getActivityLevel()));
        vars.put("hasInjury", Boolean.TRUE.equals(profile.getHasInjury()) ? "有" : "无");
        vars.put("injuryParts", profile.getInjuryParts() == null || profile.getInjuryParts().isBlank() ? "无" : profile.getInjuryParts());
        return vars;
    }

    private String buildUserContextText(UserBodyProfile profile, String trainingContext, WeatherContextSnapshot weatherContext) {
        StringBuilder sb = new StringBuilder();
        if (profile == null) {
            sb.append("用户身体档案：暂无数据\n");
        } else {
            String goal = profile.getTrainingGoals();
            if (goal == null || goal.isBlank()) {
                goal = profile.getFitnessGoal();
            }

            sb.append("用户身体档案：\n");
            sb.append("- 身高：").append(valueOrUnknown(profile.getHeight())).append(" cm\n");
            sb.append("- 体重：").append(valueOrUnknown(profile.getWeight())).append(" kg\n");
            sb.append("- 年龄：").append(valueOrUnknown(profile.getAge())).append("\n");
            sb.append("- 性别：").append(resolveGender(profile.getGender())).append("\n");
            sb.append("- 体脂率：").append(valueOrUnknown(profile.getBodyFatPercentage())).append("%\n");
            sb.append("- BMI：").append(calculateBmi(profile)).append("\n");
            sb.append("- 健身目标：").append(valueOrUnknown(goal)).append("\n");
            sb.append("- 活动水平：").append(valueOrUnknown(profile.getActivityLevel())).append("\n");
            sb.append("- 伤病状态：").append(Boolean.TRUE.equals(profile.getHasInjury()) ? "有" : "无").append("\n");
            if (Boolean.TRUE.equals(profile.getHasInjury()) && profile.getInjuryParts() != null && !profile.getInjuryParts().isBlank()) {
                sb.append("- 伤病部位：").append(profile.getInjuryParts()).append("\n");
            }
        }

        if (trainingContext != null && !trainingContext.isBlank()) {
            sb.append("近期训练状态：\n").append(trainingContext).append("\n");
        }

        String weatherText = buildWeatherContextText(weatherContext);
        if (!weatherText.isBlank()) {
            sb.append("当前天气：\n").append(weatherText).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildTrainingGenerationSupplement(UserBodyProfile profile, String trainingContext,
                                                     WeatherContextSnapshot weatherContext, String targetMuscleGroup) {
        List<String> sections = new ArrayList<>();

        if (profile != null) {
            StringBuilder profileSummary = new StringBuilder();
            profileSummary.append("身体状态摘要：BMI ").append(calculateBmi(profile))
                    .append("，活动水平 ").append(valueOrUnknown(profile.getActivityLevel()));
            if (Boolean.TRUE.equals(profile.getHasInjury())) {
                profileSummary.append("，存在伤病限制");
                if (profile.getInjuryParts() != null && !profile.getInjuryParts().isBlank()) {
                    profileSummary.append("，主要部位为 ").append(profile.getInjuryParts());
                }
            } else {
                profileSummary.append("，无已知伤病限制");
            }
            sections.add(profileSummary.toString());
        }

        if (trainingContext != null && !trainingContext.isBlank()) {
            sections.add("近期训练状态：" + trainingContext);
        }

        String weatherText = buildWeatherContextText(weatherContext);
        if (!weatherText.isBlank()) {
            sections.add("天气状态：" + weatherText);
        }

        if (!isBlank(targetMuscleGroup)) {
            sections.add("定向目标：用户本次明确要求重点训练 " + targetMuscleGroup + "。请优先安排该部位为主核心的训练，同时保留合理的热身、辅助和放松动作，并让返回 JSON 中的 targetMuscleGroup 与该目标保持一致。");
        }

        return String.join("\n", sections);
    }

    private String buildDietGenerationSupplement(UserBodyProfile profile, String trainingContext, String dietHistoryContext,
                                                 WeatherContextSnapshot weatherContext, String dayScheduleContext,
                                                 String bodyMetricTrendContext, String recognizedFoodsContext) {
        List<String> sections = new ArrayList<>();

        if (profile != null) {
            StringBuilder profileSummary = new StringBuilder();
            profileSummary.append("身体状态摘要：BMI ").append(calculateBmi(profile))
                    .append("，活动水平 ").append(valueOrUnknown(profile.getActivityLevel()));
            if (Boolean.TRUE.equals(profile.getHasInjury())) {
                profileSummary.append("，存在伤病限制");
                if (profile.getInjuryParts() != null && !profile.getInjuryParts().isBlank()) {
                    profileSummary.append("，主要部位为 ").append(profile.getInjuryParts());
                }
            } else {
                profileSummary.append("，无已知伤病限制");
            }
            sections.add(profileSummary.toString());
        }

        if (trainingContext != null && !trainingContext.isBlank()) {
            sections.add("近期训练状态：" + trainingContext);
        }

        if (dietHistoryContext != null && !dietHistoryContext.isBlank()) {
            sections.add("近期饮食执行状态：" + dietHistoryContext);
        }

        if (dayScheduleContext != null && !dayScheduleContext.isBlank()) {
            sections.add("今日安排：" + dayScheduleContext);
        }

        if (bodyMetricTrendContext != null && !bodyMetricTrendContext.isBlank()) {
            sections.add(bodyMetricTrendContext);
        }

        if (!isBlank(recognizedFoodsContext)) {
            sections.add("本次已识别饮食摄入：" + recognizedFoodsContext);
        }

        String weatherText = buildWeatherContextText(weatherContext);
        if (!weatherText.isBlank()) {
            sections.add("天气状态：" + weatherText);
        }

        if (sections.isEmpty()) {
            return "";
        }

        return "补充上下文：\n" + String.join("\n", sections)
                + "\n\n请结合以上身体状态、近期训练负荷、饮食执行情况、今日训练/休息安排、天气与伤病限制调整今日饮食计划，保证可执行性和连续性。";
    }

    private String buildWeatherContextText(WeatherContextSnapshot weatherContext) {
        if (weatherContext == null) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        if (!isBlank(weatherContext.getCity()) || !isBlank(weatherContext.getProvince())) {
            String location = !isBlank(weatherContext.getCity()) ? weatherContext.getCity() : weatherContext.getProvince();
            parts.add("地点 " + location);
        }
        if (!isBlank(weatherContext.getWeather())) {
            parts.add("天气 " + weatherContext.getWeather());
        }
        if (!isBlank(weatherContext.getTemperature())) {
            parts.add("温度 " + weatherContext.getTemperature() + "℃");
        }
        if (!isBlank(weatherContext.getHumidity())) {
            parts.add("湿度 " + weatherContext.getHumidity() + "%");
        }
        if (!isBlank(weatherContext.getWindDirection()) || !isBlank(weatherContext.getWindPower())) {
            parts.add("风况 " + safeJoin(weatherContext.getWindDirection(), weatherContext.getWindPower() == null ? "" : weatherContext.getWindPower() + "级"));
        }
        if (!isBlank(weatherContext.getReportTime())) {
            parts.add("观测时间 " + weatherContext.getReportTime());
        }

        String impact = buildWeatherImpactText(weatherContext);
        if (!impact.isBlank()) {
            parts.add("天气影响建议 " + impact);
        }

        return String.join("，", parts);
    }

    private String buildWeatherImpactText(WeatherContextSnapshot weatherContext) {
        String weather = safeLower(weatherContext.getWeather());
        Integer temperature = parseInteger(weatherContext.getTemperature());
        Integer humidity = parseInteger(weatherContext.getHumidity());
        Integer windPower = parseInteger(weatherContext.getWindPower());

        List<String> impacts = new ArrayList<>();
        if (temperature != null && temperature >= 32) {
            impacts.add("高温下优先室内或清晨训练，降低中高强度有氧时长并强调补水");
        } else if (temperature != null && temperature <= 8) {
            impacts.add("低温下要延长热身时间，户外训练前先做关节激活");
        }

        if (humidity != null && humidity >= 80) {
            impacts.add("湿度偏高，体感负担更大，避免长时间高强度连续输出");
        }

        if (windPower != null && windPower >= 6) {
            impacts.add("风力较大，不适合高暴露户外训练");
        }

        if (weather.contains("雨") || weather.contains("雷")) {
            impacts.add("雨天或雷暴天气应优先室内训练，避免湿滑路面跑步");
        } else if (weather.contains("雪") || weather.contains("冰")) {
            impacts.add("冰雪天气要避免冲刺和爆发式户外动作");
        } else if (weather.contains("雾") || weather.contains("霾") || weather.contains("沙")) {
            impacts.add("空气条件较差，减少户外耐力训练暴露");
        }

        return String.join("；", impacts);
    }

    private String buildExerciseCandidates() {
        try {
            List<Exercise> exercises = exerciseService.list();
            if (exercises == null || exercises.isEmpty()) {
                return "（候选动作库暂无数据）";
            }
            return exercises.stream()
                    .map(e -> String.format("id:%s | name:%s | type:%s | primaryMuscle:%s | category:%s | difficulty:%s",
                            e.getId(),
                            e.getName(),
                            e.getType(),
                            valueOrUnknown(e.getPrimaryMuscle()),
                            valueOrUnknown(e.getCategory()),
                            valueOrUnknown(e.getDifficulty())))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("[DeepSeekAiService] 获取候选动作列表失败: {}", e.getMessage());
            return "（候选动作库查询异常）";
        }
    }

    private String callDeepSeekWithSystemUser(String systemContent, String userContent) {
        Map<String, Object> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemContent);

        Map<String, Object> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);

        return callDeepSeekChat(List.of(systemMsg, userMsg));
    }

    private String callDeepSeekChat(List<Map<String, Object>> messages) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "deepseek-chat");
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            if (apiKey.contains("xxx")) {
                return "这是来自 FitMind AI 教练的模拟回复。您的 API 密钥尚未配置。建议您专注于渐进式超负荷训练，并保持热量缺口以实现减脂目标。";
            }

            String response = restTemplate.postForObject(apiUrl + "/chat/completions", request, String.class);
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            if (content.startsWith("```json")) {
                content = content.replace("```json", "").replace("```", "").trim();
            } else if (content.startsWith("```")) {
                content = content.replace("```", "").trim();
            }
            return content;
        } catch (Exception e) {
            log.error("[DeepSeekAiService] 调用 DeepSeek API 失败", e);
            throw new RuntimeException("AI 服务异常，请稍后重试");
        }
    }

    private String resolveGender(Integer gender) {
        if (gender == null) return "未知";
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }

    private String calculateBmi(UserBodyProfile profile) {
        if (profile == null || profile.getHeight() == null || profile.getWeight() == null
                || profile.getHeight().compareTo(BigDecimal.ZERO) <= 0) {
            return "未知";
        }
        BigDecimal heightInMeters = profile.getHeight().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal bmi = profile.getWeight().divide(heightInMeters.multiply(heightInMeters), 1, RoundingMode.HALF_UP);
        return bmi.toPlainString();
    }

    private String valueOrUnknown(Object value) {
        if (value == null) return "未知";
        String text = String.valueOf(value);
        return text.isBlank() ? "未知" : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String safeJoin(String left, String right) {
        if (isBlank(left)) return valueOrUnknown(right);
        if (isBlank(right)) return valueOrUnknown(left);
        return left + " " + right;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9-]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private String getMockJson() {
        return "{\"planName\":\"模拟训练计划\",\"targetMuscleGroup\":\"全身\",\"estimatedDuration\":45,"
                + "\"intensity\":\"medium\",\"planReason\":\"API 密钥未配置，返回模拟数据\","
                + "\"exercises\":["
                + "{\"exerciseId\":null,\"name\":\"热身慢跑\",\"type\":\"cardio\",\"sets\":null,\"reps\":null,\"restSeconds\":null,\"duration\":5,\"distance\":0.8,\"pace\":375,\"incline\":0,\"holdTime\":null,\"rounds\":null},"
                + "{\"exerciseId\":null,\"name\":\"俯卧撑\",\"type\":\"strength\",\"sets\":3,\"reps\":10,\"restSeconds\":60,\"duration\":null,\"distance\":null,\"pace\":null,\"incline\":null,\"holdTime\":null,\"rounds\":null},"
                + "{\"exerciseId\":null,\"name\":\"全身拉伸\",\"type\":\"flexibility\",\"sets\":null,\"reps\":null,\"restSeconds\":null,\"duration\":null,\"distance\":null,\"pace\":null,\"incline\":null,\"holdTime\":30,\"rounds\":2}"
                + "]}";
    }
}
