package com.fitmind.module.training.strategy.impl;

import com.fitmind.module.training.strategy.ExerciseAdjustmentStrategy;
import com.fitmind.module.training.strategy.ExerciseType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class StrengthAdjustmentStrategy implements ExerciseAdjustmentStrategy {

    @Override
    public ExerciseType getSupportedType() {
        return ExerciseType.STRENGTH;
    }

    @Override
    public Map<String, Object> analyzeAndAdjust(Map<String, Object> exerciseData, Map<String, Object> performanceData) {
        Map<String, Object> adjustments = new HashMap<>();
        adjustments.put("type", ExerciseType.STRENGTH.getValue());

        Number targetReps = toNumber(exerciseData.get("reps"));
        Number actualReps = toNumber(performanceData.get("actualReps"));
        Number weight = toNumber(performanceData.get("weight"));
        Number setNum = toNumber(performanceData.get("setNum"));
        Number totalSets = toNumber(exerciseData.get("sets"));

        if (targetReps == null || actualReps == null) {
            adjustments.put("adjustmentReason", "数据不足，无法分析");
            return adjustments;
        }

        double ratio = actualReps.doubleValue() / targetReps.doubleValue();

        if (ratio < 0.5) {
            if (weight != null && weight.doubleValue() > 0) {
                double newWeight = weight.doubleValue() * 0.75;
                adjustments.put("suggestedWeight", Math.round(newWeight * 10) / 10.0);
                adjustments.put("adjustmentReason", String.format(
                    "肌肉耐力显著衰退，完成率 %.0f%%。下一组已自动下调目标重量至 %.1fKG。(PROTOCOL OVERRIDDEN)",
                    ratio * 100, newWeight));
            } else {
                adjustments.put("adjustmentReason", String.format(
                    "肌肉耐力显著衰退，完成率 %.0f%%。建议大幅降低负重。(PROTOCOL OVERRIDDEN)", ratio * 100));
            }
            adjustments.put("severity", "HIGH");
        } else if (ratio < 1.0) {
            if (weight != null && weight.doubleValue() > 0) {
                double newWeight = weight.doubleValue() * 0.9;
                adjustments.put("suggestedWeight", Math.round(newWeight * 10) / 10.0);
                adjustments.put("adjustmentReason", String.format(
                    "次数不足 %dREPS，建议下调重量至 %.1fKG 以保证训练容量。(ADJUSTMENT ADVISED)",
                    targetReps.intValue() - actualReps.intValue(), newWeight));
            } else {
                adjustments.put("adjustmentReason", String.format(
                    "次数不足 %dREPS，建议适当降低负重以保证训练容量。(ADJUSTMENT ADVISED)",
                    targetReps.intValue() - actualReps.intValue()));
            }
            adjustments.put("severity", "MEDIUM");
        } else if (ratio > 1.3) {
            if (weight != null && weight.doubleValue() > 0) {
                double newWeight = weight.doubleValue() * 1.1;
                adjustments.put("suggestedWeight", Math.round(newWeight * 10) / 10.0);
                adjustments.put("adjustmentReason", String.format(
                    "超额完成 %.0f%%，能力储备充足。建议增重至 %.1fKG 以维持刺激强度。(PROGRESSIVE OVERLOAD)",
                    ratio * 100, newWeight));
            } else {
                adjustments.put("adjustmentReason", String.format(
                    "超额完成 %.0f%%，能力储备充足。建议适当增重以维持刺激强度。(PROGRESSIVE OVERLOAD)", ratio * 100));
            }
            adjustments.put("severity", "LOW");
        }

        if (setNum != null && setNum.intValue() >= 2) {
            Number prevReps = toNumber(performanceData.get("prevReps"));
            if (prevReps != null && prevReps.doubleValue() > 0) {
                double dropPercent = ((prevReps.doubleValue() - actualReps.doubleValue()) / prevReps.doubleValue()) * 100;
                if (dropPercent > 30) {
                    Number restSeconds = toNumber(exerciseData.get("restSeconds"));
                    int suggestedRest = restSeconds != null ? (int)(restSeconds.doubleValue() * 1.5) : 90;
                    adjustments.put("suggestedRestSeconds", suggestedRest);
                    adjustments.put("fatigueAlert", String.format(
                        "连续组间衰退 %.0f%%，肌肉疲劳积累过快。建议延长组间休息至 %ds。(FATIGUE ALERT)",
                        dropPercent, suggestedRest));
                }
            }
        }

        if (setNum != null && totalSets != null && setNum.intValue() >= totalSets.intValue() && ratio >= 1.0) {
            adjustments.put("canAddSet", true);
            adjustments.put("addSetReason", "最后一组轻松完成，可追加力竭组以最大化肌肉刺激。(FAILURE SET AVAILABLE)");
        }

        return adjustments;
    }

    @Override
    public Map<String, Object> getDefaultParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", ExerciseType.STRENGTH.getValue());
        params.put("sets", 3);
        params.put("reps", 10);
        params.put("restSeconds", 60);
        return params;
    }

    @Override
    public boolean validateParameters(Map<String, Object> params) {
        if (params == null) return false;
        Number sets = toNumber(params.get("sets"));
        Number reps = toNumber(params.get("reps"));
        Number rest = toNumber(params.get("restSeconds"));
        return sets != null && sets.intValue() > 0
            && reps != null && reps.intValue() > 0
            && rest != null && rest.intValue() > 0;
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
