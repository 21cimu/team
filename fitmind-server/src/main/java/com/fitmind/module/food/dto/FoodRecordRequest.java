package com.fitmind.module.food.dto;

import lombok.Data;

@Data
public class FoodRecordRequest {
    private String foodId;
    private String foodName;
    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;
    private String servingSize;
}
