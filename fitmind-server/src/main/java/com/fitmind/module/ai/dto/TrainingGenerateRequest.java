package com.fitmind.module.ai.dto;

import lombok.Data;

@Data
public class TrainingGenerateRequest {
    private WeatherContextSnapshot weather;
    private String targetMuscleGroup;
    private Boolean replaceExisting;
}
