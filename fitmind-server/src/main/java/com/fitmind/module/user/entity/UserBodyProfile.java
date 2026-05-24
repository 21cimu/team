package com.fitmind.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_body_profile")
public class UserBodyProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private BigDecimal height;
    private BigDecimal weight;
    private Integer age;
    private Integer gender; // 1: Male, 2: Female, 0: Unknown
    private String bodyShape;
    private BigDecimal bodyFatPercentage;
    private String fitnessGoal; // e.g. "Fat Loss", "Muscle Gain"
    private String trainingGoals;
    private String activityLevel; // e.g. "Sedentary", "Active"
    private Boolean hasInjury;
    private String injuryParts;
    private Boolean profileCompleted;
    private LocalDateTime lastProfileUpdateTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
