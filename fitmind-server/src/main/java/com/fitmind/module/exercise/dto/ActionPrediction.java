package com.fitmind.module.exercise.dto;

import lombok.Data;

@Data
public class ActionPrediction {
    private String label;
    private String labelZh;
    private Double score;
    private Integer scorePercent;
}
