package com.fitmind.module.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.service.IAiDietPlanService;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.service.IAiTrainingPlanService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final IAiTrainingPlanService aiTrainingPlanService;
    private final IAiDietPlanService aiDietPlanService;
    private final SysUserMapper sysUserMapper;

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户未认证");
        }
        return user.getId();
    }

    @GetMapping("/training")
    public Result<List<AiTrainingPlan>> getTrainingHistory() {
        List<AiTrainingPlan> history = aiTrainingPlanService.list(
                new LambdaQueryWrapper<AiTrainingPlan>()
                        .eq(AiTrainingPlan::getUserId, getCurrentUserId())
                        .orderByDesc(AiTrainingPlan::getPlanDate)
        );
        return Result.success(history);
    }

    @GetMapping("/diet")
    public Result<List<AiDietPlan>> getDietHistory() {
        List<AiDietPlan> history = aiDietPlanService.list(
                new LambdaQueryWrapper<AiDietPlan>()
                        .eq(AiDietPlan::getUserId, getCurrentUserId())
                        .orderByDesc(AiDietPlan::getPlanDate)
        );
        return Result.success(history);
    }
}
