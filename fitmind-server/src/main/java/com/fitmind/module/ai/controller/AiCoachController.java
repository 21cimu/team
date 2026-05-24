package com.fitmind.module.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.module.ai.dto.ChatSessionSummary;
import com.fitmind.module.ai.dto.WeatherContextSnapshot;
import com.fitmind.module.ai.entity.ChatMessage;
import com.fitmind.module.ai.service.DeepSeekAiService;
import com.fitmind.module.ai.service.IChatMessageService;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.service.IAiDietPlanService;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.service.IAiTrainingPlanService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.entity.UserBodyProfile;
import com.fitmind.module.user.mapper.SysUserMapper;
import com.fitmind.module.user.mapper.UserBodyProfileMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiCoachController {

    private final IAiTrainingPlanService aiTrainingPlanService;
    private final IAiDietPlanService aiDietPlanService;
    private final SysUserMapper sysUserMapper;
    private final UserBodyProfileMapper userBodyProfileMapper;
    private final DeepSeekAiService deepSeekAiService;
    private final IChatMessageService chatMessageService;
    private final com.fitmind.module.ai.service.PromptLoader promptLoader;

    @PostMapping("/prompts/reload")
    public Result<String> reloadPrompts() {
        int count = promptLoader.reload();
        return Result.success("提示词已重载，共 " + count + " 个");
    }

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户未认证");
        }
        return user.getId();
    }

    @PostMapping("/generate/training")
    public Result<AiTrainingPlan> generateTrainingPlan(@RequestBody(required = false) TrainingGenerateRequest request) {
        try {
            AiTrainingPlan plan = aiTrainingPlanService.generateAndSavePlan(
                    getCurrentUserId(),
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
                    getCurrentUserId(),
                    request == null ? null : request.getWeather(),
                    request == null ? null : request.getRecognizedFoodsContext());
            return Result.success(plan);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/training/today")
    public Result<AiTrainingPlan> getTodayTrainingPlan() {
        return Result.success(aiTrainingPlanService.getPlanForToday(getCurrentUserId()));
    }

    @GetMapping("/diet/today")
    public Result<AiDietPlan> getTodayDietPlan() {
        return Result.success(aiDietPlanService.getPlanForToday(getCurrentUserId()));
    }

    @PostMapping("/training/checkin/{planId}")
    public Result<String> checkInTrainingPlan(@PathVariable Long planId) {
        try {
            aiTrainingPlanService.checkIn(getCurrentUserId(), planId);
            return Result.success("签到成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/diet/checkin/{planId}")
    public Result<String> checkInDietPlan(@PathVariable Long planId) {
        try {
            aiDietPlanService.checkIn(getCurrentUserId(), planId);
            return Result.success("签到成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/chat")
    public Result<Map<String, Object>> chatWithCoach(@RequestBody ChatRequest request) {
        try {
            Long userId = getCurrentUserId();
            UserBodyProfile profile = loadProfile(userId);
            String sessionId = resolveSessionId(request.getSessionId());

            String response = deepSeekAiService.chat(
                    userId,
                    sessionId,
                    request.getMessage(),
                    profile,
                    aiTrainingPlanService.buildRecentTrainingContext(userId),
                    request.getWeather());

            Map<String, Object> result = new HashMap<>();
            result.put("response", response);
            result.put("sessionId", sessionId);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatWithCoach(@RequestBody ChatRequest request) {
        Long userId = getCurrentUserId();
        UserBodyProfile profile = loadProfile(userId);
        String sessionId = resolveSessionId(request.getSessionId());
        SseEmitter emitter = new SseEmitter(0L);

        deepSeekAiService.streamChat(
                userId,
                sessionId,
                request.getMessage(),
                profile,
                aiTrainingPlanService.buildRecentTrainingContext(userId),
                request.getWeather(),
                emitter);

        return emitter;
    }

    @GetMapping("/chat/history")
    public Result<List<ChatMessage>> getChatHistory(@RequestParam String sessionId) {
        try {
            Long userId = getCurrentUserId();
            return Result.success(chatMessageService.getSessionMessages(userId, sessionId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/chat/sessions")
    public Result<List<ChatSessionSummary>> getChatSessions(@RequestParam(defaultValue = "20") int limit) {
        try {
            Long userId = getCurrentUserId();
            return Result.success(chatMessageService.getRecentSessions(userId, Math.max(1, Math.min(limit, 50))));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/chat/session/{sessionId}")
    public Result<String> deleteChatSession(@PathVariable String sessionId) {
        try {
            chatMessageService.removeSessionMessages(getCurrentUserId(), sessionId);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private UserBodyProfile loadProfile(Long userId) {
        try {
            return userBodyProfileMapper.selectOne(
                    new LambdaQueryWrapper<UserBodyProfile>().eq(UserBodyProfile::getUserId, userId));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? UUID.randomUUID().toString() : sessionId;
    }

    @Data
    static class ChatRequest {
        private String message;
        private String sessionId;
        private WeatherContextSnapshot weather;
    }

    @Data
    static class TrainingGenerateRequest {
        private WeatherContextSnapshot weather;
        private String targetMuscleGroup;
        private Boolean replaceExisting;
    }

    @Data
    static class DietGenerateRequest {
        private WeatherContextSnapshot weather;
        private String recognizedFoodsContext;
    }
}
