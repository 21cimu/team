package com.fitmind.module.exercise.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmind.module.exercise.dto.ActionPrediction;
import com.fitmind.module.exercise.dto.ActionVisionResult;
import com.fitmind.module.exercise.dto.ExerciseActionAnalysisResponse;
import com.fitmind.module.exercise.dto.FormCheck;
import com.fitmind.module.exercise.dto.JointAngleMetric;
import com.fitmind.module.exercise.dto.RealtimeActionEvaluationRequest;
import com.fitmind.module.exercise.service.IExerciseActionAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExerciseActionAnalysisServiceImpl implements IExerciseActionAnalysisService {

    private static final Map<String, String> ACTION_KEY_TO_LABEL = Map.ofEntries(
            Map.entry("bodyweight_squat", "BodyWeightSquats"),
            Map.entry("jumping_jack", "JumpingJack"),
            Map.entry("forward_lunge", "Lunges"),
            Map.entry("push_up", "PushUps"),
            Map.entry("high_knees", "HighKnees"),
            Map.entry("standing_knee_raise", "StandingKneeRaise"),
            Map.entry("glute_bridge", "GluteBridge"),
            Map.entry("plank", "Plank"),
            Map.entry("burpee", "Burpee")
    );

    private static final Map<String, String> ACTION_LABEL_ZH = Map.ofEntries(
            Map.entry("BodyWeightSquats", "徒手深蹲"),
            Map.entry("JumpingJack", "开合跳"),
            Map.entry("Lunges", "弓步"),
            Map.entry("PushUps", "俯卧撑"),
            Map.entry("HighKnees", "高抬腿"),
            Map.entry("StandingKneeRaise", "站姿提膝"),
            Map.entry("GluteBridge", "臀桥"),
            Map.entry("Plank", "平板支撑"),
            Map.entry("Burpee", "波比跳")
    );

    private static final Map<String, List<String>> ACTION_TIPS = Map.ofEntries(
            Map.entry("BenchPress", List.of("Stabilize the shoulders before pressing.", "Keep the forearms stacked over the elbows.")),
            Map.entry("BodyWeightSquats", List.of("Sit the hips back before bending the knees.", "Keep the chest stable through the full squat.")),
            Map.entry("JumpingJack", List.of("Open and close with a steady rhythm.", "Land softly and keep the knees aligned.")),
            Map.entry("JumpRope", List.of("Keep the elbows close to the torso.", "Use light landings and a steady bounce rhythm.")),
            Map.entry("Lunges", List.of("Set the stride first, then descend vertically.", "Keep the torso stable instead of lunging forward.")),
            Map.entry("PullUps", List.of("Stabilize the hanging position before pulling.", "Reduce swing and keep the core tight.")),
            Map.entry("PushUps", List.of("Brace the core so the trunk stays straight.", "Lower with control before pushing up.")),
            Map.entry("HighKnees", List.of("Raise each knee clearly instead of doing half reps.", "Keep the upper body stable while alternating.")),
            Map.entry("StandingKneeRaise", List.of("Lift the knee from a stable trunk.", "Lower the leg under control instead of dropping it.")),
            Map.entry("GluteBridge", List.of("Drive the hips up and squeeze the glutes at the top.", "Pause briefly at the bridge peak before lowering.")),
            Map.entry("Plank", List.of("Keep a straight line from shoulders to hips.", "Brace the trunk to avoid sagging or piking.")),
            Map.entry("Burpee", List.of("Make the squat-down and kick-back transition continuous.", "Stabilize the support position before standing up again.")),
            Map.entry("WallPushups", List.of("Keep a straight line from shoulders to heels.", "Control the lowering speed without bouncing off the wall."))
    );

    private final ObjectMapper objectMapper;

    @Value("${fitness-vision.python-executable:python}")
    private String pythonExecutable;

    @Value("${fitness-vision.script-path:../Fitness model/infer_action_json.py}")
    private String scriptPath;

    @Value("${fitness-vision.model-path:../Fitness model/trained_models/best_model.pth}")
    private String modelPath;

    @Value("${fitness-vision.class-mapping-path:../Fitness model/processed_data/class_mapping.json}")
    private String classMappingPath;

    @Value("${fitness-vision.working-directory:../Fitness model}")
    private String workingDirectory;

    @Value("${fitness-vision.upload-dir:./uploads/fitness-vision}")
    private String uploadDir;

    @Value("${fitness-vision.timeout-seconds:180}")
    private long timeoutSeconds;

    @Override
    public ExerciseActionAnalysisResponse analyzeVideo(MultipartFile file) {
        validateFile(file);

        Path savedFile = null;
        try {
            savedFile = saveUpload(file);
            ActionVisionResult rawResult = executeInference(savedFile);
            return toResponse(rawResult);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to store uploaded fitness video", e);
        } finally {
            if (savedFile != null) {
                try {
                    Files.deleteIfExists(savedFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp fitness video: {}", savedFile, e);
                }
            }
        }
    }

    @Override
    public ExerciseActionAnalysisResponse evaluateRealtimeSummary(RealtimeActionEvaluationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Realtime evaluation payload is required");
        }
        String expectedLabel = ACTION_KEY_TO_LABEL.get(request.getActionKey());
        if (expectedLabel == null) {
            throw new IllegalArgumentException("Unsupported realtime action: " + request.getActionKey());
        }

        int totalFrames = safeInt(request.getTotalFrames());
        int poseFrames = safeInt(request.getPoseFrames());
        if (totalFrames <= 0 || poseFrames <= 0) {
            throw new IllegalArgumentException("Realtime evaluation requires stable pose frames");
        }

        List<FormCheck> incomingChecks = copyChecks(request.getFormChecks());
        List<JointAngleMetric> incomingMetrics = copyMetrics(request.getJointAngles());
        List<ActionPrediction> predictions = normalizePredictions(request.getTopPredictions(), expectedLabel);

        double poseRatio = totalFrames > 0 ? clamp01((double) poseFrames / totalFrames) : 0.0;
        double localScore = request.getScore() != null ? request.getScore() : percentToScore(request.getScorePercent());
        double predictionScore = predictions.stream()
                .filter(item -> expectedLabel.equals(item.getLabel()))
                .map(ActionPrediction::getScore)
                .filter(value -> value != null)
                .findFirst()
                .orElse(localScore);
        double checkPassRatio = incomingChecks.isEmpty()
                ? (Boolean.TRUE.equals(request.getStandard()) ? 0.7 : 0.45)
                : incomingChecks.stream().filter(item -> Boolean.TRUE.equals(item.getPassed())).count() * 1.0 / incomingChecks.size();
        double motionCoverage = estimateMotionCoverage(incomingMetrics);
        double repetitionFactor = request.getRepetitions() != null && request.getRepetitions() > 0 ? 1.0 : 0.72;

        double enhancedScore = 0.38 * Math.max(localScore, predictionScore)
                + 0.22 * poseRatio
                + 0.20 * checkPassRatio
                + 0.12 * motionCoverage
                + 0.08 * repetitionFactor;
        if (request.getLabel() != null && !request.getLabel().equals(expectedLabel)) {
            enhancedScore -= 0.08;
        }
        enhancedScore = clamp01(enhancedScore);
        int scorePercent = (int) Math.round(enhancedScore * 100);

        List<FormCheck> mergedChecks = appendServerChecks(incomingChecks, poseRatio, motionCoverage);
        List<String> suggestions = buildRealtimeSuggestions(
                expectedLabel,
                mergedChecks,
                scorePercent,
                poseRatio,
                motionCoverage,
                request.getSuggestions()
        );

        ExerciseActionAnalysisResponse response = new ExerciseActionAnalysisResponse();
        response.setSuccess(true);
        response.setLabel(expectedLabel);
        response.setLabelZh(ACTION_LABEL_ZH.getOrDefault(expectedLabel, request.getLabelZh()));
        response.setScore(roundScore(enhancedScore));
        response.setScorePercent(scorePercent);
        response.setStandard(scorePercent >= 70 && poseRatio >= 0.45 && checkPassRatio >= 0.45);
        response.setHint(buildRealtimeHint(scorePercent, poseRatio, motionCoverage, mergedChecks));
        response.setSuggestions(suggestions);
        response.setTopPredictions(predictions);
        response.setPoseFrames(poseFrames);
        response.setTotalFrames(totalFrames);
        response.setSequenceFrames(safeInt(request.getSequenceFrames()));
        response.setSource("browser-local + server-enhanced");
        response.setRepetitions(safeInt(request.getRepetitions()));
        response.setCurrentPhase(request.getCurrentPhase());
        response.setPhaseTimeline(request.getPhaseTimeline());
        response.setJointAngles(incomingMetrics);
        response.setFormChecks(mergedChecks);
        return response;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a workout video file");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".webm") || lower.endsWith(".mkv"))) {
            throw new IllegalArgumentException("Only MP4, MOV, AVI, WEBM, and MKV videos are supported");
        }
    }

    private Path saveUpload(MultipartFile file) throws IOException {
        Path dir = resolvePath(uploadDir);
        Files.createDirectories(dir);

        String ext = getExtension(file.getOriginalFilename());
        Path saved = dir.resolve("action-" + UUID.randomUUID() + ext);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, saved, StandardCopyOption.REPLACE_EXISTING);
        }
        return saved;
    }

    private ActionVisionResult executeInference(Path videoPath) {
        List<String> command = new ArrayList<>();
        command.add(resolveExecutable(pythonExecutable).toString());
        command.add(resolvePath(scriptPath).toString());
        command.add("--source");
        command.add(videoPath.toString());
        command.add("--model-path");
        command.add(resolvePath(modelPath).toString());
        command.add("--class-mapping");
        command.add(resolvePath(classMappingPath).toString());
        command.add("--top-k");
        command.add("3");
        command.add("--min-frames");
        command.add("20");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(resolvePath(workingDirectory).toFile());
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("TF_CPP_MIN_LOG_LEVEL", "2");

        try {
            Process process = processBuilder.start();
            CompletableFuture<String> stdoutFuture = readStream(process.getInputStream());
            CompletableFuture<String> stderrFuture = readStream(process.getErrorStream());

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Action analysis timed out. Try a shorter clip.");
            }

            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(extractError(stderr, stdout));
            }
            if (stdout == null || stdout.isBlank()) {
                throw new IllegalStateException("Vision model returned no result");
            }

            ActionVisionResult result = objectMapper.readValue(stdout, ActionVisionResult.class);
            if (!Boolean.TRUE.equals(result.getSuccess())) {
                throw new IllegalStateException("Vision model did not return a valid result");
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to execute vision model: " + e.getMessage(), e);
        }
    }

    private CompletableFuture<String> readStream(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream stream = inputStream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ExerciseActionAnalysisResponse toResponse(ActionVisionResult raw) {
        ExerciseActionAnalysisResponse response = new ExerciseActionAnalysisResponse();
        response.setSuccess(true);
        response.setLabel(raw.getLabel());
        response.setLabelZh(raw.getLabelZh());
        response.setScore(raw.getScore());
        response.setScorePercent(raw.getScorePercent());
        response.setStandard(raw.getStandard());
        response.setHint(buildHint(raw));
        response.setSuggestions(buildSuggestions(raw));
        response.setPoseFrames(raw.getPoseFrames());
        response.setTotalFrames(raw.getTotalFrames());
        response.setSequenceFrames(raw.getSequenceFrames());
        response.setSource(raw.getSource());
        response.setTopPredictions(raw.getTopK());
        response.setRepetitions(raw.getRepetitions());
        response.setCurrentPhase(raw.getCurrentPhase());
        response.setPhaseTimeline(raw.getPhaseTimeline());
        response.setJointAngles(raw.getJointAngles());
        response.setFormChecks(raw.getFormChecks());
        return response;
    }

    private String buildHint(ActionVisionResult raw) {
        if (raw.getHint() != null && !raw.getHint().isBlank()) {
            return raw.getHint();
        }
        int scorePercent = raw.getScorePercent() == null ? 0 : raw.getScorePercent();
        if (scorePercent >= 75) {
            return "Recognition is stable. Keep focusing on motion range and control.";
        }
        if (scorePercent >= 60) {
            return "Recognition is working, but the motion still needs better consistency.";
        }
        Integer poseFrames = raw.getPoseFrames();
        Integer totalFrames = raw.getTotalFrames();
        if (poseFrames != null && totalFrames != null && totalFrames > 0 && poseFrames * 1.0 / totalFrames < 0.35) {
            return "Too few valid pose frames were captured. Adjust camera angle, lighting, and framing.";
        }
        return "Current confidence is low. Try a clearer angle and a more complete movement range.";
    }

    private List<String> buildSuggestions(ActionVisionResult raw) {
        List<String> suggestions = new ArrayList<>();
        if (raw.getAdvice() != null && !raw.getAdvice().isEmpty()) {
            suggestions.addAll(raw.getAdvice());
        } else {
            suggestions.addAll(ACTION_TIPS.getOrDefault(raw.getLabel(), List.of()));
        }

        int scorePercent = raw.getScorePercent() == null ? 0 : raw.getScorePercent();
        if (scorePercent < 75) {
            suggestions.add("Keep the full body visible in frame and avoid cutting off the head, wrists, or feet.");
        }
        if (scorePercent < 60) {
            suggestions.add("Record a stable 3-6 second clip with consistent lighting and less motion blur.");
        }
        suggestions.add("For more reliable scoring, prefer a fixed front-side or side camera angle.");
        return suggestions.stream().distinct().limit(4).toList();
    }

    private List<FormCheck> copyChecks(List<FormCheck> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<FormCheck> copied = new ArrayList<>();
        for (FormCheck item : source) {
            if (item == null) {
                continue;
            }
            FormCheck copy = new FormCheck();
            copy.setName(item.getName());
            copy.setPassed(item.getPassed());
            copy.setDetail(item.getDetail());
            copied.add(copy);
        }
        return copied;
    }

    private List<JointAngleMetric> copyMetrics(List<JointAngleMetric> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<JointAngleMetric> copied = new ArrayList<>();
        for (JointAngleMetric item : source) {
            if (item == null) {
                continue;
            }
            JointAngleMetric copy = new JointAngleMetric();
            copy.setKey(item.getKey());
            copy.setLabel(item.getLabel());
            copy.setCurrent(item.getCurrent());
            copy.setAverage(item.getAverage());
            copy.setMin(item.getMin());
            copy.setMax(item.getMax());
            copy.setUnit(item.getUnit());
            copied.add(copy);
        }
        return copied;
    }

    private List<ActionPrediction> normalizePredictions(List<ActionPrediction> input, String expectedLabel) {
        List<ActionPrediction> items = new ArrayList<>();
        if (input != null) {
            for (ActionPrediction item : input) {
                if (item == null || item.getLabel() == null || item.getLabel().isBlank()) {
                    continue;
                }
                ActionPrediction copy = new ActionPrediction();
                copy.setLabel(item.getLabel());
                copy.setLabelZh(item.getLabelZh());
                copy.setScore(item.getScore() != null ? roundScore(clamp01(item.getScore())) : percentToScore(item.getScorePercent()));
                copy.setScorePercent(item.getScorePercent() != null
                        ? Math.max(0, Math.min(100, item.getScorePercent()))
                        : (int) Math.round(copy.getScore() * 100));
                items.add(copy);
            }
        }
        boolean hasExpected = items.stream().anyMatch(item -> expectedLabel.equals(item.getLabel()));
        if (!hasExpected) {
            ActionPrediction fallback = new ActionPrediction();
            fallback.setLabel(expectedLabel);
            fallback.setLabelZh(ACTION_LABEL_ZH.getOrDefault(expectedLabel, expectedLabel));
            fallback.setScore(0.55);
            fallback.setScorePercent(55);
            items.add(fallback);
        }
        items.sort(Comparator.comparing(ActionPrediction::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
        return items.stream().limit(3).toList();
    }

    private double estimateMotionCoverage(List<JointAngleMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0.45;
        }
        double accumulated = 0.0;
        int counted = 0;
        for (JointAngleMetric metric : metrics) {
            if (metric == null || metric.getMax() == null || metric.getMin() == null) {
                continue;
            }
            double range = Math.max(0.0, metric.getMax() - metric.getMin());
            double normalized = "x".equalsIgnoreCase(metric.getUnit())
                    ? clamp01(range / 0.9)
                    : clamp01(range / 70.0);
            accumulated += normalized;
            counted += 1;
        }
        if (counted == 0) {
            return 0.45;
        }
        return accumulated / counted;
    }

    private List<FormCheck> appendServerChecks(List<FormCheck> checks, double poseRatio, double motionCoverage) {
        List<FormCheck> merged = new ArrayList<>(checks);

        FormCheck poseCoverage = new FormCheck();
        poseCoverage.setName("Pose coverage");
        poseCoverage.setPassed(poseRatio >= 0.45);
        poseCoverage.setDetail(poseRatio >= 0.45
                ? "Stable body coverage detected across the recent frame window."
                : "Too few stable pose frames were captured in the recent window.");
        merged.add(poseCoverage);

        FormCheck motionWindow = new FormCheck();
        motionWindow.setName("Motion window");
        motionWindow.setPassed(motionCoverage >= 0.45);
        motionWindow.setDetail(motionCoverage >= 0.45
                ? "Recent frame window shows enough movement range for server-side review."
                : "Recent movement range is still limited; a longer or clearer rep window will help.");
        merged.add(motionWindow);
        return merged;
    }

    private List<String> buildRealtimeSuggestions(
            String expectedLabel,
            List<FormCheck> checks,
            int scorePercent,
            double poseRatio,
            double motionCoverage,
            List<String> clientSuggestions
    ) {
        List<String> suggestions = new ArrayList<>();
        if (clientSuggestions != null) {
            suggestions.addAll(clientSuggestions);
        }
        for (FormCheck check : checks) {
            if (check != null && Boolean.FALSE.equals(check.getPassed()) && check.getDetail() != null && !check.getDetail().isBlank()) {
                suggestions.add(check.getDetail());
            }
        }
        suggestions.addAll(ACTION_TIPS.getOrDefault(expectedLabel, List.of()));
        if (poseRatio < 0.45) {
            suggestions.add("Keep the full body in frame and avoid cutting off the head, wrists, or feet.");
        }
        if (motionCoverage < 0.45) {
            suggestions.add("Use a slightly longer movement window so the server can verify the full rep path.");
        }
        if (scorePercent < 65) {
            suggestions.add("Slow the tempo down and make each repetition more deliberate before speeding up.");
        }
        return suggestions.stream()
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private String buildRealtimeHint(int scorePercent, double poseRatio, double motionCoverage, List<FormCheck> checks) {
        long failedChecks = checks.stream().filter(item -> item != null && Boolean.FALSE.equals(item.getPassed())).count();
        if (poseRatio < 0.45) {
            return "Server review is limited because the recent pose window is not stable enough. Keep the whole body in frame.";
        }
        if (motionCoverage < 0.45) {
            return "Server review is active, but the recent movement range is still shallow. Complete a fuller rep window.";
        }
        if (scorePercent >= 78 && failedChecks <= 1) {
            return "Server-enhanced review agrees the current motion pattern is stable and complete.";
        }
        if (scorePercent >= 65) {
            return "Server-enhanced review confirms the rep pattern, but there is still room to improve consistency.";
        }
        return "Server-enhanced review sees the target motion, but the current rep quality is still unstable.";
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double percentToScore(Integer percent) {
        if (percent == null) {
            return 0.0;
        }
        return clamp01(percent / 100.0);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double roundScore(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private String extractError(String stderr, String stdout) {
        String candidate = stderr != null && !stderr.isBlank() ? stderr : stdout;
        if (candidate == null || candidate.isBlank()) {
            return "Vision model execution failed";
        }
        String[] lines = candidate.strip().split("\\R");
        return lines[lines.length - 1].trim();
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return ".mp4";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex) : ".mp4";
    }

    private Path resolveExecutable(String value) {
        Path path = Paths.get(value);
        if (path.isAbsolute() || value.contains(FileSystemsHolder.UNIX_SEPARATOR) || value.contains(FileSystemsHolder.WINDOWS_SEPARATOR)) {
            return path.toAbsolutePath().normalize();
        }
        return path;
    }

    private Path resolvePath(String value) {
        return Paths.get(value).toAbsolutePath().normalize();
    }

    private static final class FileSystemsHolder {
        private static final String WINDOWS_SEPARATOR = "\\";
        private static final String UNIX_SEPARATOR = "/";

        private FileSystemsHolder() {
        }
    }
}
