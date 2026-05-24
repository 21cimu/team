package com.fitmind.module.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatSessionSummary {
    private String sessionId;
    private String preview;
    private LocalDateTime lastMessageTime;
}
