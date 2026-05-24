package com.fitmind.module.training.strategy;

public enum ExerciseType {
    STRENGTH("strength"),
    CARDIO("cardio"),
    FLEXIBILITY("flexibility");

    private final String value;

    ExerciseType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ExerciseType fromValue(String value) {
        if (value == null) {
            return STRENGTH;
        }
        for (ExerciseType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return STRENGTH;
    }
}
