package com.fitmind.module.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fitmind.module.ai.dto.ChatSessionSummary;
import com.fitmind.module.ai.entity.ChatMessage;
import com.fitmind.module.ai.mapper.ChatMessageMapper;
import com.fitmind.module.ai.service.IChatMessageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

    @Override
    public void saveMessage(Long userId, String sessionId, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setUserId(userId);
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        this.save(msg);
    }

    @Override
    public List<ChatMessage> getSessionMessages(Long userId, String sessionId) {
        return this.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreateTime));
    }

    @Override
    public List<ChatMessage> getRecentMessages(Long userId, String sessionId, int limit) {
        return this.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + limit))
                .stream()
                .sorted((a, b) -> a.getCreateTime().compareTo(b.getCreateTime()))
                .toList();
    }

    @Override
    public List<ChatSessionSummary> getRecentSessions(Long userId, int limit) {
        int fetchSize = Math.max(limit * 20, 200);
        List<ChatMessage> recentMessages = this.list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + fetchSize));

        Map<String, ChatSessionSummary> sessions = new LinkedHashMap<>();
        for (ChatMessage message : recentMessages) {
            if (message.getSessionId() == null || message.getSessionId().isBlank()) {
                continue;
            }

            ChatSessionSummary summary = sessions.computeIfAbsent(message.getSessionId(), sessionId -> {
                ChatSessionSummary item = new ChatSessionSummary();
                item.setSessionId(sessionId);
                item.setLastMessageTime(message.getCreateTime());
                item.setPreview(buildPreview(message.getContent()));
                return item;
            });

            if ((summary.getPreview() == null || summary.getPreview().isBlank()) && message.getContent() != null) {
                summary.setPreview(buildPreview(message.getContent()));
            }

            if ("user".equals(message.getRole()) && message.getContent() != null && !message.getContent().isBlank()) {
                summary.setPreview(buildPreview(message.getContent()));
            }

            if (sessions.size() >= limit && sessions.containsKey(message.getSessionId())) {
                continue;
            }
            if (sessions.size() >= limit) {
                break;
            }
        }

        return new ArrayList<>(sessions.values());
    }

    @Override
    public void removeSessionMessages(Long userId, String sessionId) {
        this.remove(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getSessionId, sessionId));
    }

    private String buildPreview(String content) {
        if (content == null || content.isBlank()) {
            return "新对话";
        }
        String compact = content.replaceAll("\\s+", " ").trim();
        return compact.length() > 28 ? compact.substring(0, 28) : compact;
    }
}
