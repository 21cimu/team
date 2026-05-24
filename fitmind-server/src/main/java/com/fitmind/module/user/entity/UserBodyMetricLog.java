package com.fitmind.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_body_metric_log")
public class UserBodyMetricLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private BigDecimal height;
    private BigDecimal weight;
    private BigDecimal bodyFatPercentage;
    private LocalDateTime recordTime;
    private LocalDateTime createTime;
}
