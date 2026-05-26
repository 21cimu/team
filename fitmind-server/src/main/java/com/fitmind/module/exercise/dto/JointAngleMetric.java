package com.fitmind.module.exercise.dto;

import lombok.Data;

@Data
public class JointAngleMetric {
    private String key;
    private String label;
    private Double current;
    private Double average;
    private Double min;
    private Double max;
    private String unit;
}
