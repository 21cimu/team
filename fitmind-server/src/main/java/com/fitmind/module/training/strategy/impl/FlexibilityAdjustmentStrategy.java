package com.fitmind.module.training.strategy.impl;

import com.fitmind.module.training.strategy.ExerciseAdjustmentStrategy;
import com.fitmind.module.training.strategy.ExerciseType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FlexibilityAdjustmentStrategy implements ExerciseAdjustmentStrategy {

    @Override
    public ExerciseType getSupportedType() {
        return ExerciseType.FLEXIBILITY;
    }

    @Override
    public Map<String, Object> analyzeAndAdjust(Map<String, Object> exerciseData, Map<String, Object> performanceData) {
        Map<String, Object> adjustments = new HashMap<>();
        adjustments.put("type", ExerciseType.FLEXIBILITY.getValue());

        Number targetHoldTime = toNumber(exerciseData.get("holdTime"));
        Number actualHoldTime = toNumber(performanceData.get("actualHoldTime"));
        Number targetRounds = toNumber(exerciseData.get("rounds"));
        Number completedRounds = toNumber(performanceData.get("completedRounds"));

        if (targetHoldTime != null && actualHoldTime != null) {
            double holdRatio = actualHoldTime.doubleValue() / targetHoldTime.doubleValue();

            if (holdRatio < 0.6) {
                int newHoldTime = (int)(targetHoldTime.doubleValue() * 0.7);
                adjustments.put("suggestedHoldTime", newHoldTime);
                adjustments.put("adjustmentReason", String.format(
                    "保持时长不足，完成率 %.0f%%。建议缩短保持时间至 %d 秒，避免过度拉伸导致损伤。(MOBILITY FOUNDATION)",
                    holdRatio * 100, newHoldTime));
                adjustments.put("severity", "MEDIUM");
            } else if (holdRatio < 0.85) {
                adjustments.put("adjustmentReason", String.format(
                    "保持时长完成率 %.0f%%，柔韧性正在改善中。建议保持当前保持时长，逐步增加。(FLEXIBILITY PROGRESS)",
                    holdRatio * 100));
                adjustments.put("severity", "LOW");
            } else if (holdRatio >= 1.0) {
                int newHoldTime = (int)(targetHoldTime.doubleValue() * 1.2);
                adjustments.put("suggestedHoldTime", newHoldTime);
                adjustments.put("adjustmentReason", String.format(
                    "保持时长达标 %.0f%%，柔韧性良好。建议延长保持时间至 %d 秒以深化拉伸效果。(DEEP STRETCH)",
                    holdRatio * 100, newHoldTime));
                adjustments.put("severity", "LOW");
            }
        }

        if (targetRounds != null && completedRounds != null) {
            double roundRatio = completedRounds.doubleValue() / targetRounds.doubleValue();
            if (roundRatio < 0.5) {
                int newRounds = Math.max(1, (int)(targetRounds.doubleValue() * 0.6));
                adjustments.put("suggestedRounds", newRounds);
                adjustments.put("roundAdjustment", String.format(
                    "轮数完成率 %.0f%%，建议减少至 %d 轮，确保每轮质量。(QUALITY OVER QUANTITY)",
                    roundRatio * 100, newRounds));
            }
        }

        return adjustments;
    }

    @Override
    public Map<String, Object> getDefaultParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", ExerciseType.FLEXIBILITY.getValue());
        params.put("holdTime", 30);
        params.put("rounds", 3);
        return params;
    }

    @Override
    public boolean validateParameters(Map<String, Object> params) {
        if (params == null) return false;
        Number holdTime = toNumber(params.get("holdTime"));
        Number rounds = toNumber(params.get("rounds"));
        return holdTime != null && holdTime.intValue() > 0
            && rounds != null && rounds.intValue() > 0;
    }

    private Number toNumber(Object val) {
        if (val instanceof Number) return (Number) val;
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); }
            catch (Exception e) { return null; }
        }
        return null;
    }
}
