package com.fitmind.module.exercise.dto;

import lombok.Data;

@Data
public class ActionPhaseSegment {
    private String phase;
    private Integer startFrame;
    private Integer endFrame;
    private Integer frameCount;
}
