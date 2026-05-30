package com.fitmind.module.exercise.service;

import com.fitmind.module.exercise.dto.ExerciseActionAnalysisResponse;
import com.fitmind.module.exercise.dto.RealtimeActionEvaluationRequest;
import org.springframework.web.multipart.MultipartFile;

public interface IExerciseActionAnalysisService {
    ExerciseActionAnalysisResponse analyzeVideo(MultipartFile file);

    ExerciseActionAnalysisResponse evaluateRealtimeSummary(RealtimeActionEvaluationRequest request);
}
