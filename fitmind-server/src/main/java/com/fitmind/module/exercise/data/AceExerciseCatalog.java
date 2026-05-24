package com.fitmind.module.exercise.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitmind.module.exercise.entity.Exercise;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class AceExerciseCatalog {

    private static final String RESOURCE_PATH = "/data/ace-exercises.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<Exercise> EXERCISES = loadExercises();

    private AceExerciseCatalog() {
    }

    public static List<Exercise> defaults() {
        return EXERCISES.stream().map(AceExerciseCatalog::copy).toList();
    }

    public static Optional<Exercise> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return EXERCISES.stream()
                .filter(exercise -> id.equals(exercise.getId()))
                .findFirst()
                .map(AceExerciseCatalog::copy);
    }

    public static Optional<Exercise> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return EXERCISES.stream()
                .filter(exercise -> exercise.getName() != null
                        && exercise.getName().trim().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .map(AceExerciseCatalog::copy);
    }

    public static int size() {
        return EXERCISES.size();
    }

    private static List<Exercise> loadExercises() {
        try (InputStream inputStream = AceExerciseCatalog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (inputStream == null) {
                throw new IllegalStateException("ACE exercise data resource not found: " + RESOURCE_PATH);
            }
            List<AceExerciseRecord> records = OBJECT_MAPPER.readValue(inputStream, new TypeReference<>() {
            });

            List<Exercise> exercises = new ArrayList<>(records.size());
            int sortOrder = 1;
            for (AceExerciseRecord record : records) {
                exercises.add(toExercise(record, sortOrder++));
            }
            return List.copyOf(exercises);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load ACE exercise data", e);
        }
    }

    private static Exercise toExercise(AceExerciseRecord record, int sortOrder) {
        String difficulty = normalizeDifficulty(record.difficulty());
        String category = categoryFor(record);
        String primaryMuscle = primaryMuscleFor(category, record);

        Exercise exercise = new Exercise();
        exercise.setId(record.id());
        exercise.setName(record.name());
        exercise.setTarget(record.target());
        exercise.setCategory(category);
        exercise.setDifficulty(difficulty);
        exercise.setEquipIcon(equipmentIconFor(record.equipment()));
        exercise.setDescription(descriptionFor(record));
        exercise.setPrimaryMuscle(primaryMuscle);
        exercise.setSecondaryMuscles(secondaryMusclesFor(record.target(), primaryMuscle));
        exercise.setReps(repsFor(difficulty));
        exercise.setSets(setsFor(difficulty));
        exercise.setTips("Move with control|Keep the trunk braced|Use a pain-free range of motion");
        exercise.setSortOrder(sortOrder);
        exercise.setSourceUrl(record.url());
        exercise.setImageUrl(record.imageUrl());
        return exercise;
    }

    private static Exercise copy(Exercise source) {
        Exercise exercise = new Exercise();
        exercise.setId(source.getId());
        exercise.setName(source.getName());
        exercise.setTarget(source.getTarget());
        exercise.setCategory(source.getCategory());
        exercise.setDifficulty(source.getDifficulty());
        exercise.setEquipIcon(source.getEquipIcon());
        exercise.setDescription(source.getDescription());
        exercise.setPrimaryMuscle(source.getPrimaryMuscle());
        exercise.setSecondaryMuscles(source.getSecondaryMuscles());
        exercise.setReps(source.getReps());
        exercise.setSets(source.getSets());
        exercise.setTips(source.getTips());
        exercise.setSortOrder(source.getSortOrder());
        exercise.setType(source.getType());
        exercise.setSourceUrl(source.getSourceUrl());
        exercise.setImageUrl(source.getImageUrl());
        return exercise;
    }

    private static String categoryFor(AceExerciseRecord record) {
        String name = lower(record.name());
        String target = lower(record.target());

        if (target.contains("back") && containsAny(name, "row", "pull", "chin", "lat", "deadlift")) {
            return "BACK";
        }
        if (target.contains("chest") && containsAny(name, "press", "push", "chest", "fly")) {
            return "CHEST";
        }
        if (target.contains("shoulders") && containsAny(name, "press", "raise", "shoulder", "windmill")) {
            return "SHOULDERS";
        }
        if (target.contains("arms") && containsAny(name, "curl", "triceps", "biceps")) {
            return "ARMS";
        }
        if (target.contains("butt/hips") && containsAny(name, "glute", "hip", "bridge")) {
            return "GLUTES";
        }
        if (target.contains("legs") && containsAny(name, "squat", "lunge", "step", "calf")) {
            return "LEGS";
        }

        String firstTarget = firstTarget(record.target());
        return switch (firstTarget) {
            case "Abs" -> "CORE";
            case "Arms" -> "ARMS";
            case "Back" -> "BACK";
            case "Butt/Hips" -> "GLUTES";
            case "Chest" -> "CHEST";
            case "Legs - Calves and Shins", "Legs - Thighs" -> "LEGS";
            case "Shoulders" -> "SHOULDERS";
            default -> "CORE";
        };
    }

    private static String primaryMuscleFor(String category, AceExerciseRecord record) {
        String name = lower(record.name());
        String target = lower(record.target());

        return switch (category) {
            case "CHEST" -> "Pectorals";
            case "BACK" -> target.contains("deadlift") || name.contains("deadlift") ? "Erector Spinae" : "Latissimus Dorsi";
            case "LEGS" -> target.contains("calves and shins") ? "Gastrocnemius" : "Quadriceps";
            case "SHOULDERS" -> "Deltoids";
            case "ARMS" -> name.contains("triceps") ? "Triceps" : "Biceps";
            case "GLUTES" -> "Gluteus Maximus";
            default -> "Rectus Abdominis";
        };
    }

    private static String secondaryMusclesFor(String target, String primaryMuscle) {
        Set<String> muscles = new LinkedHashSet<>();
        String normalizedTarget = lower(target);

        if (normalizedTarget.contains("abs")) {
            addAll(muscles, "Rectus Abdominis", "Obliques", "Transverse Abdominis");
        }
        if (normalizedTarget.contains("arms")) {
            addAll(muscles, "Biceps", "Triceps", "Brachialis");
        }
        if (normalizedTarget.contains("back")) {
            addAll(muscles, "Latissimus Dorsi", "Rhomboids", "Trapezius", "Erector Spinae");
        }
        if (normalizedTarget.contains("butt/hips")) {
            addAll(muscles, "Gluteus Maximus", "Gluteus Medius", "Hamstrings");
        }
        if (normalizedTarget.contains("chest")) {
            addAll(muscles, "Pectorals", "Anterior Deltoids", "Triceps");
        }
        if (normalizedTarget.contains("full body/integrated")) {
            addAll(muscles, "Core", "Shoulders", "Gluteus Maximus", "Quadriceps");
        }
        if (normalizedTarget.contains("legs - calves and shins")) {
            addAll(muscles, "Gastrocnemius", "Soleus", "Tibialis Anterior");
        }
        if (normalizedTarget.contains("legs - thighs")) {
            addAll(muscles, "Quadriceps", "Hamstrings", "Adductors");
        }
        if (normalizedTarget.contains("neck")) {
            addAll(muscles, "Sternocleidomastoid", "Upper Trapezius");
        }
        if (normalizedTarget.contains("shoulders")) {
            addAll(muscles, "Deltoids", "Rotator Cuff", "Trapezius");
        }

        muscles.remove(primaryMuscle);
        return String.join(",", muscles);
    }

    private static String equipmentIconFor(String equipment) {
        String normalized = lower(equipment);
        if (normalized.isBlank() || normalized.contains("no equipment")) {
            return "BW";
        }
        if (normalized.contains("dumbbells")) {
            return "DB";
        }
        if (normalized.contains("barbell")) {
            return "BB";
        }
        if (normalized.contains("kettlebells")) {
            return "KB";
        }
        if (normalized.contains("medicine ball")) {
            return "MB";
        }
        if (normalized.contains("stability ball")) {
            return "BALL";
        }
        if (normalized.contains("resistance bands")) {
            return "BAND";
        }
        if (normalized.contains("trx")) {
            return "TRX";
        }
        if (normalized.contains("bosu")) {
            return "BOSU";
        }
        if (normalized.contains("bench")) {
            return "BENCH";
        }
        if (normalized.contains("raised platform") || normalized.contains("box")) {
            return "BOX";
        }
        if (normalized.contains("weight machines")) {
            return "MACHINE";
        }
        if (normalized.contains("pull up bar")) {
            return "BAR";
        }
        if (normalized.contains("ladder")) {
            return "LADDER";
        }
        if (normalized.contains("hurdles")) {
            return "HURDLE";
        }
        if (normalized.contains("cones")) {
            return "CONE";
        }
        if (normalized.contains("heavy ropes")) {
            return "ROPE";
        }
        return "EQ";
    }

    private static String descriptionFor(AceExerciseRecord record) {
        String equipment = record.equipment() == null || record.equipment().isBlank()
                ? "bodyweight"
                : record.equipment();
        return "ACE Exercise Library reference movement targeting " + record.target()
                + " using " + equipment + ".";
    }

    private static String normalizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "BEGINNER";
        }
        return difficulty.toUpperCase(Locale.ROOT);
    }

    private static String repsFor(String difficulty) {
        return switch (difficulty) {
            case "ADVANCED" -> "6-10";
            case "INTERMEDIATE" -> "8-12";
            default -> "8-12";
        };
    }

    private static int setsFor(String difficulty) {
        return switch (difficulty) {
            case "ADVANCED" -> 4;
            case "INTERMEDIATE" -> 3;
            default -> 2;
        };
    }

    private static String firstTarget(String target) {
        if (target == null || target.isBlank()) {
            return "";
        }
        return target.split(",")[0].trim();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static void addAll(Set<String> values, String... items) {
        values.addAll(List.of(items));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record AceExerciseRecord(
            Long id,
            String name,
            String target,
            String equipment,
            String difficulty,
            String url,
            String imageUrl
    ) {
    }
}
