package com.fitmind.module.exercise.dto;

import lombok.Data;

import java.util.List;

@Data
public class ExerciseActionAnalysisResponse {
    private Boolean success;
    private String label;
    private String labelZh;
    private Double score;
    private Integer scorePercent;
    private Boolean standard;
    private String hint;
    private List<String> suggestions;
    private List<ActionPrediction> topPredictions;
    private Integer poseFrames;
    private Integer totalFrames;
    private Integer sequenceFrames;
    private String source;
}
