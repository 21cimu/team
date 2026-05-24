package com.fitmind.module.training.strategy.impl;

import com.fitmind.module.training.strategy.ExerciseAdjustmentStrategy;
import com.fitmind.module.training.strategy.ExerciseType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CardioAdjustmentStrategy implements ExerciseAdjustmentStrategy {

    @Override
    public ExerciseType getSupportedType() {
        return ExerciseType.CARDIO;
    }

    @Override
    public Map<String, Object> analyzeAndAdjust(Map<String, Object> exerciseData, Map<String, Object> performanceData) {
        Map<String, Object> adjustments = new HashMap<>();
        adjustments.put("type", ExerciseType.CARDIO.getValue());

        Number targetDuration = toNumber(exerciseData.get("duration"));
        Number actualDuration = toNumber(performanceData.get("actualDuration"));
        Number targetPace = toNumber(exerciseData.get("pace"));
        Number actualPace = toNumber(performanceData.get("actualPace"));
        Number targetDistance = toNumber(exerciseData.get("distance"));
        Number actualDistance = toNumber(performanceData.get("actualDistance"));

        if (actualDuration != null && targetDuration != null) {
            double durationRatio = actualDuration.doubleValue() / targetDuration.doubleValue();

            if (durationRatio < 0.7) {
                int newDuration = (int)(targetDuration.doubleValue() * 0.8);
                adjustments.put("suggestedDuration", newDuration);
                adjustments.put("adjustmentReason", String.format(
                    "有氧耐力不足，仅完成目标时长的 %.0f%%。建议下调目标时长至 %d 分钟，逐步建立有氧基础。(ENDURANCE BUILD-UP)",
                    durationRatio * 100, newDuration));
                adjustments.put("severity", "HIGH");
            } else if (durationRatio < 0.9) {
                adjustments.put("adjustmentReason", String.format(
                    "有氧时长完成率 %.0f%%，接近目标。建议保持当前配速，逐步延长运动时间。(STEADY PROGRESS)",
                    durationRatio * 100));
                adjustments.put("severity", "MEDIUM");
            } else if (durationRatio > 1.1) {
                int newDuration = (int)(targetDuration.doubleValue() * 1.15);
                adjustments.put("suggestedDuration", newDuration);
                adjustments.put("adjustmentReason", String.format(
                    "超额完成有氧时长 %.0f%%，心肺能力充足。建议延长至 %d 分钟以持续提升有氧阈值。(AEROBIC THRESHOLD ADVANCE)",
                    durationRatio * 100, newDuration));
                adjustments.put("severity", "LOW");
            }
        }

        if (targetPace != null && actualPace != null && targetPace.doubleValue() > 0) {
            double paceDiff = actualPace.doubleValue() - targetPace.doubleValue();
            if (paceDiff > 30) {
                double newPace = targetPace.doubleValue() * 1.1;
                adjustments.put("suggestedPace", Math.round(newPace));
                adjustments.put("paceAdjustment", String.format(
                    "配速偏慢 %d秒/km，建议调整目标配速至 %d秒/km。(PACE CORRECTION)",
                    (int) paceDiff, Math.round(newPace)));
            } else if (paceDiff < -15) {
                double newPace = targetPace.doubleValue() * 0.95;
                adjustments.put("suggestedPace", Math.round(newPace));
                adjustments.put("paceAdjustment", String.format(
                    "配速优于目标 %d秒/km，建议提升目标配速至 %d秒/km。(PACE UPGRADE)",
                    (int) Math.abs(paceDiff), Math.round(newPace)));
            }
        }

        if (actualDistance != null && targetDistance != null && targetDistance.doubleValue() > 0) {
            double distRatio = actualDistance.doubleValue() / targetDistance.doubleValue();
            if (distRatio < 0.6) {
                double newDistance = targetDistance.doubleValue() * 0.75;
                adjustments.put("suggestedDistance", Math.round(newDistance * 100) / 100.0);
                adjustments.put("distanceAdjustment", String.format(
                    "距离完成率 %.0f%%，建议缩短目标距离至 %.2fkm 以保证训练质量。(DISTANCE SCALED)",
                    distRatio * 100, newDistance));
            }
        }

        return adjustments;
    }

    @Override
    public Map<String, Object> getDefaultParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", ExerciseType.CARDIO.getValue());
        params.put("duration", 30);
        params.put("distance", 5.0);
        params.put("pace", 360);
        params.put("incline", 0);
        return params;
    }

    @Override
    public boolean validateParameters(Map<String, Object> params) {
        if (params == null) return false;
        Number duration = toNumber(params.get("duration"));
        return duration != null && duration.intValue() > 0;
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
