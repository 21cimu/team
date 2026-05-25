package com.fitmind.module.exercise.service;

import com.fitmind.module.exercise.dto.ExerciseActionAnalysisResponse;
import org.springframework.web.multipart.MultipartFile;

public interface IExerciseActionAnalysisService {
    ExerciseActionAnalysisResponse analyzeVideo(MultipartFile file);
}
