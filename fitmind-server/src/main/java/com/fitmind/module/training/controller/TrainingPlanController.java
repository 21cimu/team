package com.fitmind.module.training.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.service.IAiTrainingPlanService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class TrainingPlanController {

    private final IAiTrainingPlanService aiTrainingPlanService;
    private final SysUserMapper sysUserMapper;

    private Long getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户未认证");
        }
        return user.getId();
    }

    @PostMapping("/plan")
    public Result<AiTrainingPlan> createManualPlan(@RequestBody AiTrainingPlan request) {
        Long userId = getCurrentUserId();

        // 不删除当天旧计划，允许同一天存在多条

        request.setUserId(userId);
        request.setPlanDate(LocalDate.now());
        request.setStatus(0);
        request.setCreateTime(LocalDateTime.now());
        request.setUpdateTime(LocalDateTime.now());

        if (request.getPlanName() == null || request.getPlanName().isBlank()) {
            request.setPlanName("自定义训练 - " + (request.getTargetMuscleGroup() != null ? request.getTargetMuscleGroup() : "综合"));
        }
        if (request.getTargetMuscleGroup() == null || request.getTargetMuscleGroup().isBlank()) {
            request.setTargetMuscleGroup("综合训练");
        }
        if (request.getEstimatedDuration() == null || request.getEstimatedDuration() <= 0) {
            request.setEstimatedDuration(45);
        }

        aiTrainingPlanService.save(request);
        return Result.success(request);
    }

    @PutMapping("/plan/{id}")
    public Result<AiTrainingPlan> updatePlan(@PathVariable Long id, @RequestBody AiTrainingPlan request) {
        Long userId = getCurrentUserId();

        AiTrainingPlan existing = aiTrainingPlanService.getById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            return Result.error("计划不存在或无权修改");
        }

        if (request.getPlanName() != null && !request.getPlanName().isBlank()) {
            existing.setPlanName(request.getPlanName());
        }
        if (request.getTargetMuscleGroup() != null && !request.getTargetMuscleGroup().isBlank()) {
            existing.setTargetMuscleGroup(request.getTargetMuscleGroup());
        }
        if (request.getEstimatedDuration() != null && request.getEstimatedDuration() > 0) {
            existing.setEstimatedDuration(request.getEstimatedDuration());
        }
        if (request.getContent() != null && !request.getContent().isBlank()) {
            existing.setContent(request.getContent());
        }

        existing.setUpdateTime(LocalDateTime.now());
        aiTrainingPlanService.updateById(existing);
        return Result.success(existing);
    }
}
