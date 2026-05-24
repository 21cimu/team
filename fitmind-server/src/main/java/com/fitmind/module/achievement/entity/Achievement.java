package com.fitmind.module.achievement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("achievement")
public class Achievement {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String icon;
    private String name;
    private String description;
    private String category;
    private String rarity;
    private Integer target;
    private Integer sortOrder;
}
