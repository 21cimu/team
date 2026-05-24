package com.fitmind.module.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("ai_training_plan")
public class AiTrainingPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate planDate;
    private String planName;
    private String targetMuscleGroup;
    private Integer estimatedDuration;
    private Integer status;
    private String content; // JSON string
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
