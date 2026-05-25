package com.fitmind.module.exercise.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmind.module.exercise.dto.ActionPrediction;
import com.fitmind.module.exercise.dto.ActionVisionResult;
import com.fitmind.module.exercise.dto.ExerciseActionAnalysisResponse;
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

    private static final Map<String, List<String>> ACTION_TIPS = Map.ofEntries(
            Map.entry("BenchPress", List.of("肩胛先收紧再推起，避免耸肩。", "手腕尽量垂直叠在肘部上方。")),
            Map.entry("BodyWeightSquats", List.of("先向后坐髋，再同步屈膝下蹲。", "下蹲过程中保持胸椎稳定，不要塌腰。")),
            Map.entry("JumpingJack", List.of("手脚张开和回收节奏尽量一致。", "落地时膝盖朝向脚尖，避免内扣。")),
            Map.entry("JumpRope", List.of("手肘贴近躯干，用手腕发力而不是大幅甩肩。", "前脚掌轻落地，保持弹性节奏。")),
            Map.entry("Lunges", List.of("前后脚距离拉开，躯干保持竖直。", "下落时垂直下沉，不要明显前冲。")),
            Map.entry("PullUps", List.of("先稳定悬垂，再向下收肘发力。", "减少摆腿和借力，保持躯干控制。")),
            Map.entry("PushUps", List.of("核心收紧，避免塌腰或撅臀。", "下放到清晰屈肘后再稳定推起。")),
            Map.entry("WallPushups", List.of("从肩到脚跟尽量保持一条直线。", "接近墙面时控制速度，不要反弹借力。"))
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
            throw new IllegalStateException("无法保存上传视频", e);
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

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传动作视频文件");
        }
        String filename = file.getOriginalFilename();
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".avi") || lower.endsWith(".webm") || lower.endsWith(".mkv"))) {
            throw new IllegalArgumentException("仅支持 MP4、MOV、AVI、WEBM 或 MKV 视频");
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
                throw new IllegalStateException("动作识别超时，请缩短视频时长后重试");
            }

            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(extractError(stderr, stdout));
            }
            if (stdout == null || stdout.isBlank()) {
                throw new IllegalStateException("视觉模型没有返回识别结果");
            }

            ActionVisionResult result = objectMapper.readValue(stdout, ActionVisionResult.class);
            if (!Boolean.TRUE.equals(result.getSuccess())) {
                throw new IllegalStateException("视觉模型未返回有效识别结果");
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("调用视觉模型失败: " + e.getMessage(), e);
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
        return response;
    }

    private String buildHint(ActionVisionResult raw) {
        int scorePercent = raw.getScorePercent() == null ? 0 : raw.getScorePercent();
        if (scorePercent >= 75) {
            return "动作识别稳定，当前节奏和姿态较完整，可以继续关注动作幅度和控制。";
        }
        if (scorePercent >= 60) {
            return "动作已识别，但稳定性一般。建议放慢节奏，确保关键关节轨迹更清晰。";
        }
        Integer poseFrames = raw.getPoseFrames();
        Integer totalFrames = raw.getTotalFrames();
        if (poseFrames != null && totalFrames != null && totalFrames > 0 && poseFrames * 1.0 / totalFrames < 0.35) {
            return "有效骨架帧过少，优先调整拍摄距离、光线和机位，再重新上传。";
        }
        return "当前识别置信度偏低，可能是动作幅度不足，或视频角度没有覆盖关键关节。";
    }

    private List<String> buildSuggestions(ActionVisionResult raw) {
        List<String> suggestions = new ArrayList<>();
        suggestions.addAll(ACTION_TIPS.getOrDefault(raw.getLabel(), List.of()));

        int scorePercent = raw.getScorePercent() == null ? 0 : raw.getScorePercent();
        if (scorePercent < 75) {
            suggestions.add("确保人物全身完整入镜，避免裁掉头部、手腕或脚踝。");
        }
        if (scorePercent < 60) {
            suggestions.add("使用更稳定的机位连续录制 3 到 6 秒，避免强逆光和明显模糊。");
        }
        suggestions.add("如需更可靠评分，优先使用侧前方或正侧方固定视角录制。");
        return suggestions.stream().distinct().limit(4).toList();
    }

    private String extractError(String stderr, String stdout) {
        String candidate = stderr != null && !stderr.isBlank() ? stderr : stdout;
        if (candidate == null || candidate.isBlank()) {
            return "视觉模型执行失败";
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
