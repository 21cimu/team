package com.fitmind.module.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.ai.dto.ChatSessionSummary;
import com.fitmind.module.ai.entity.ChatMessage;

import java.util.List;

public interface IChatMessageService extends IService<ChatMessage> {
    void saveMessage(Long userId, String sessionId, String role, String content);
    List<ChatMessage> getSessionMessages(Long userId, String sessionId);
    List<ChatMessage> getRecentMessages(Long userId, String sessionId, int limit);
    List<ChatSessionSummary> getRecentSessions(Long userId, int limit);
    void removeSessionMessages(Long userId, String sessionId);
}
