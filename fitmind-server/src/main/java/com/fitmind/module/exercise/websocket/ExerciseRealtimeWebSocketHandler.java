package com.fitmind.module.exercise.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExerciseRealtimeWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, FitnessVisionRealtimeProcessSession> realtimeSessions = new ConcurrentHashMap<>();

    @Value("${fitness-vision.python-executable:python}")
    private String pythonExecutable;

    @Value("${fitness-vision.realtime-script-path:../Fitness model/stream_action_server.py}")
    private String realtimeScriptPath;

    @Value("${fitness-vision.model-path:../Fitness model/trained_models/best_model.pth}")
    private String modelPath;

    @Value("${fitness-vision.class-mapping-path:../Fitness model/processed_data/class_mapping.json}")
    private String classMappingPath;

    @Value("${fitness-vision.working-directory:../Fitness model}")
    private String workingDirectory;

    @Value("${fitness-vision.realtime-timeout-seconds:30}")
    private long timeoutSeconds;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            FitnessVisionRealtimeProcessSession realtimeSession = FitnessVisionRealtimeProcessSession.start(
                    objectMapper,
                    resolveExecutable(pythonExecutable),
                    resolvePath(realtimeScriptPath),
                    resolvePath(modelPath),
                    resolvePath(classMappingPath),
                    resolvePath(workingDirectory),
                    timeoutSeconds
            );
            realtimeSessions.put(session.getId(), realtimeSession);
            session.sendMessage(new TextMessage(realtimeSession.getReadyMessage()));
        } catch (Exception e) {
            log.warn("Failed to start realtime analysis session {}", session.getId(), e);
            session.sendMessage(new TextMessage(errorJson("实时动作识别初始化失败: " + e.getMessage())));
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        FitnessVisionRealtimeProcessSession realtimeSession = realtimeSessions.get(session.getId());
        if (realtimeSession == null) {
            session.sendMessage(new TextMessage(errorJson("实时分析会话不存在或已关闭")));
            return;
        }

        JsonNode payload = objectMapper.readTree(message.getPayload());
        String type = payload.path("type").asText("");

        try {
            if ("frame".equals(type)) {
                String imageBase64 = payload.path("imageBase64").asText("");
                if (imageBase64.isBlank()) {
                    session.sendMessage(new TextMessage(errorJson("缺少图像帧数据")));
                    return;
                }
                session.sendMessage(new TextMessage(realtimeSession.analyzeFrame(imageBase64)));
                return;
            }
            if ("ping".equals(type)) {
                session.sendMessage(new TextMessage(realtimeSession.sendPing()));
                return;
            }
            session.sendMessage(new TextMessage(errorJson("不支持的实时消息类型: " + type)));
        } catch (Exception e) {
            log.warn("Realtime analysis failed for session {}", session.getId(), e);
            session.sendMessage(new TextMessage(errorJson("实时动作识别失败: " + e.getMessage())));
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.debug("Realtime websocket transport error: {}", session.getId(), exception);
        closeSession(session.getId());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeSession(session.getId());
    }

    private void closeSession(String sessionId) {
        FitnessVisionRealtimeProcessSession realtimeSession = realtimeSessions.remove(sessionId);
        if (realtimeSession != null) {
            realtimeSession.close();
        }
    }

    private String errorJson(String message) {
        try {
            return objectMapper.createObjectNode()
                    .put("type", "error")
                    .put("message", message)
                    .toString();
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"" + message.replace("\"", "'") + "\"}";
        }
    }

    private Path resolveExecutable(String value) {
        Path path = Paths.get(value);
        if (path.isAbsolute() || value.contains("\\") || value.contains("/")) {
            return path.toAbsolutePath().normalize();
        }
        return path;
    }

    private Path resolvePath(String value) {
        return Paths.get(value).toAbsolutePath().normalize();
    }
}
