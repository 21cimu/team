package com.fitmind.module.training.strategy;

import com.fitmind.module.training.strategy.impl.CardioAdjustmentStrategy;
import com.fitmind.module.training.strategy.impl.FlexibilityAdjustmentStrategy;
import com.fitmind.module.training.strategy.impl.StrengthAdjustmentStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExerciseStrategyFactory {

    private final StrengthAdjustmentStrategy strengthStrategy;
    private final CardioAdjustmentStrategy cardioStrategy;
    private final FlexibilityAdjustmentStrategy flexibilityStrategy;

    private final Map<ExerciseType, ExerciseAdjustmentStrategy> strategyMap = new HashMap<>();

    public ExerciseAdjustmentStrategy getStrategy(ExerciseType type) {
        if (strategyMap.isEmpty()) {
            strategyMap.put(ExerciseType.STRENGTH, strengthStrategy);
            strategyMap.put(ExerciseType.CARDIO, cardioStrategy);
            strategyMap.put(ExerciseType.FLEXIBILITY, flexibilityStrategy);
        }
        return strategyMap.getOrDefault(type, strengthStrategy);
    }

    public ExerciseAdjustmentStrategy getStrategy(String typeValue) {
        return getStrategy(ExerciseType.fromValue(typeValue));
    }

    public Map<String, Object> analyzeAndAdjust(String typeValue, Map<String, Object> exerciseData, Map<String, Object> performanceData) {
        ExerciseAdjustmentStrategy strategy = getStrategy(typeValue);
        return strategy.analyzeAndAdjust(exerciseData, performanceData);
    }

    public Map<String, Object> getDefaultParameters(String typeValue) {
        ExerciseAdjustmentStrategy strategy = getStrategy(typeValue);
        return strategy.getDefaultParameters();
    }

    public boolean validateParameters(String typeValue, Map<String, Object> params) {
        ExerciseAdjustmentStrategy strategy = getStrategy(typeValue);
        return strategy.validateParameters(params);
    }

    public List<ExerciseType> getSupportedTypes() {
        return List.of(ExerciseType.values());
    }
}
