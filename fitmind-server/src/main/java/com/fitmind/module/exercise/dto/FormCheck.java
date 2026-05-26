package com.fitmind.module.exercise.dto;

import lombok.Data;

@Data
public class FormCheck {
    private String name;
    private Boolean passed;
    private String detail;
}
