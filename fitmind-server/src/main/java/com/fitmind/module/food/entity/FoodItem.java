package com.fitmind.module.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("food_item")
public class FoodItem {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String name;
    private String nameEn;
    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;
    private Double fiber;
    private String servingSize;
    private String imageUrl;
}