package com.fitmind.module.food.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("food_record")
public class FoodRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String foodId;
    private String foodName;
    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;
    private String servingSize;
    private LocalDateTime createTime;
}
