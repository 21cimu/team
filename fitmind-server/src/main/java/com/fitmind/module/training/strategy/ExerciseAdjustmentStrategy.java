package com.fitmind.module.training.strategy;

import java.util.Map;

public interface ExerciseAdjustmentStrategy {

    ExerciseType getSupportedType();

    Map<String, Object> analyzeAndAdjust(Map<String, Object> exerciseData, Map<String, Object> performanceData);

    Map<String, Object> getDefaultParameters();

    boolean validateParameters(Map<String, Object> params);
}
