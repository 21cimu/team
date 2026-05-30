package com.fitmind.module.ai.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String sessionId;
    private WeatherContextSnapshot weather;
}
