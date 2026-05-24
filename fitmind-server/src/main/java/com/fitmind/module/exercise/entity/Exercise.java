package com.fitmind.module.exercise.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("exercise")
public class Exercise {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String target;
    private String category;
    private String difficulty;
    private String equipIcon;
    private String description;
    private String primaryMuscle;
    private String secondaryMuscles;
    private String reps;
    private Integer sets;
    private String tips;
    private Integer sortOrder;

    @TableField(exist = false)
    private String type;

    @TableField(exist = false)
    private String sourceUrl;

    @TableField(exist = false)
    private String imageUrl;

    public String getType() {
        if (type != null && !type.isEmpty()) {
            return type;
        }
        if (category == null) {
            return "strength";
        }
        String cat = category.toUpperCase();
        if ("CARDIO".equals(cat)) {
            return "cardio";
        }
        if ("FLEXIBILITY".equals(cat) || "STRETCH".equals(cat)) {
            return "flexibility";
        }
        return "strength";
    }
}
