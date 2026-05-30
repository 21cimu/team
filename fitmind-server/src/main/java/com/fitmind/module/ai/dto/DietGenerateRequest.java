package com.fitmind.module.ai.dto;

import lombok.Data;

@Data
public class DietGenerateRequest {
    private WeatherContextSnapshot weather;
    private String recognizedFoodsContext;
}
