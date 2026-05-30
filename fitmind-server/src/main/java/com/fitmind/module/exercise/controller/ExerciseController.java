package com.fitmind.module.exercise.controller;

import com.fitmind.common.api.Result;
import com.fitmind.module.exercise.data.AceExerciseCatalog;
import com.fitmind.module.exercise.dto.ExerciseActionAnalysisResponse;
import com.fitmind.module.exercise.dto.RealtimeActionEvaluationRequest;
import com.fitmind.module.exercise.entity.Exercise;
import com.fitmind.module.exercise.service.IExerciseActionAnalysisService;
import com.fitmind.module.exercise.service.IExerciseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/exercise")
@RequiredArgsConstructor
public class ExerciseController {

    private final IExerciseService exerciseService;
    private final IExerciseActionAnalysisService exerciseActionAnalysisService;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getExercises(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        try {
            List<Exercise> exercises = shouldUseAceCatalog()
                    ? applyFilter(AceExerciseCatalog.defaults(), keyword, category)
                    : exerciseService.searchExercises(keyword, category);

            return Result.success(exercises.stream()
                    .map(this::enrichWithAceCatalog)
                    .map(this::toMap)
                    .toList());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getExerciseDetail(@PathVariable Long id) {
        try {
            Exercise exercise = shouldUseAceCatalog()
                    ? AceExerciseCatalog.findById(id).orElse(null)
                    : exerciseService.getById(id);
            if (exercise == null) {
                exercise = AceExerciseCatalog.findById(id).orElse(null);
            }
            if (exercise == null) {
                return Result.error("Exercise not found");
            }
            return Result.success(toMap(enrichWithAceCatalog(exercise)));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ExerciseActionAnalysisResponse> analyzeExerciseAction(@RequestParam("file") MultipartFile file) {
        try {
            return Result.success(exerciseActionAnalysisService.analyzeVideo(file));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("动作识别失败: " + e.getMessage());
        }
    }

    @PostMapping("/realtime/evaluate")
    public Result<ExerciseActionAnalysisResponse> evaluateRealtimeAction(@RequestBody RealtimeActionEvaluationRequest request) {
        try {
            return Result.success(exerciseActionAnalysisService.evaluateRealtimeSummary(request));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error("Realtime action evaluation failed: " + e.getMessage());
        }
    }

    private List<Exercise> applyFilter(List<Exercise> exercises, String keyword, String category) {
        return exercises.stream()
                .filter(ex -> matchesCategory(ex, category))
                .filter(ex -> matchesKeyword(ex, keyword))
                .toList();
    }

    private boolean shouldUseAceCatalog() {
        return exerciseService.count() < AceExerciseCatalog.size();
    }

    private boolean matchesCategory(Exercise exercise, String category) {
        return category == null
                || category.isEmpty()
                || "ALL".equals(category)
                || category.equals(exercise.getCategory());
    }

    private boolean matchesKeyword(Exercise exercise, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        String query = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(exercise.getName(), query)
                || contains(exercise.getTarget(), query)
                || contains(exercise.getDescription(), query)
                || contains(exercise.getPrimaryMuscle(), query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private Exercise enrichWithAceCatalog(Exercise exercise) {
        if (exercise == null) {
            return null;
        }
        if (exercise.getImageUrl() != null && !exercise.getImageUrl().isBlank()
                && exercise.getSourceUrl() != null && !exercise.getSourceUrl().isBlank()) {
            return exercise;
        }

        Exercise aceExercise = AceExerciseCatalog.findById(exercise.getId())
                .or(() -> AceExerciseCatalog.findByName(exercise.getName()))
                .orElse(null);
        if (aceExercise == null) {
            return exercise;
        }

        if (exercise.getSourceUrl() == null || exercise.getSourceUrl().isBlank()) {
            exercise.setSourceUrl(aceExercise.getSourceUrl());
        }
        if (exercise.getImageUrl() == null || exercise.getImageUrl().isBlank()) {
            exercise.setImageUrl(aceExercise.getImageUrl());
        }
        if (exercise.getDescription() == null || exercise.getDescription().isBlank()) {
            exercise.setDescription(aceExercise.getDescription());
        }
        return exercise;
    }

    private Map<String, Object> toMap(Exercise exercise) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", exercise.getId());
        item.put("name", exercise.getName());
        item.put("target", exercise.getTarget());
        item.put("category", exercise.getCategory());
        item.put("difficulty", exercise.getDifficulty());
        item.put("equipIcon", exercise.getEquipIcon());
        item.put("description", exercise.getDescription());
        item.put("primaryMuscle", exercise.getPrimaryMuscle());
        item.put("secondaryMuscles", split(exercise.getSecondaryMuscles(), ","));
        item.put("reps", exercise.getReps());
        item.put("sets", exercise.getSets());
        item.put("tips", split(exercise.getTips(), "\\|"));
        item.put("type", exercise.getType());
        item.put("sourceUrl", exercise.getSourceUrl());
        item.put("imageUrl", exercise.getImageUrl());
        return item;
    }

    private List<String> split(String value, String regex) {
        if (value == null || value.isBlank()) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(regex))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
