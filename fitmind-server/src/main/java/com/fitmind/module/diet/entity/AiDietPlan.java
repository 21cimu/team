package com.fitmind.module.diet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ai_diet_plan")
public class AiDietPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate planDate;
    private Integer totalCalories;
    private BigDecimal protein;
    private BigDecimal carbs;
    private BigDecimal fat;
    private Integer status;
    private String content; // JSON string
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
