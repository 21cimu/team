package com.fitmind.module.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.ai.dto.ChatRequest;
import com.fitmind.module.ai.dto.ChatSessionSummary;
import com.fitmind.module.ai.entity.ChatMessage;
import com.fitmind.module.ai.service.DeepSeekAiService;
import com.fitmind.module.ai.service.IChatMessageService;
import com.fitmind.module.training.service.IAiTrainingPlanService;
import com.fitmind.module.user.entity.UserBodyProfile;
import com.fitmind.module.user.mapper.UserBodyProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final CurrentUserProvider currentUserProvider;
    private final UserBodyProfileMapper userBodyProfileMapper;
    private final DeepSeekAiService deepSeekAiService;
    private final IChatMessageService chatMessageService;
    private final IAiTrainingPlanService aiTrainingPlanService;

    @PostMapping("/chat")
    public Result<Map<String, Object>> chatWithCoach(@RequestBody ChatRequest request) {
        try {
            Long userId = currentUserProvider.getCurrentUserId();
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
        Long userId = currentUserProvider.getCurrentUserId();
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
            Long userId = currentUserProvider.getCurrentUserId();
            return Result.success(chatMessageService.getSessionMessages(userId, sessionId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/chat/sessions")
    public Result<List<ChatSessionSummary>> getChatSessions(@RequestParam(defaultValue = "20") int limit) {
        try {
            Long userId = currentUserProvider.getCurrentUserId();
            return Result.success(chatMessageService.getRecentSessions(userId, Math.max(1, Math.min(limit, 50))));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/chat/session/{sessionId}")
    public Result<String> deleteChatSession(@org.springframework.web.bind.annotation.PathVariable String sessionId) {
        try {
            chatMessageService.removeSessionMessages(currentUserProvider.getCurrentUserId(), sessionId);
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
}
