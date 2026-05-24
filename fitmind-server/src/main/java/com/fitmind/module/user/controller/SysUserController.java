package com.fitmind.module.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.common.api.Result;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.service.ISysUserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
public class SysUserController {
    private static final long PROFILE_PROMPT_INACTIVE_DAYS = 3;

    private final ISysUserService sysUserService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request) {
        try {
            String token = sysUserService.login(request.getUsername(), request.getPassword());
            SysUser user = sysUserService.getOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
            boolean promptRequired = true;
            try {
                promptRequired = updateLoginProfilePromptState(user);
            } catch (Exception e) {
                log.warn("Failed to update login profile state for user {}", request.getUsername(), e);
            }

            Map<String, Object> loginResult = new HashMap<>();
            loginResult.put("token", token);
            loginResult.put("profilePromptRequired", promptRequired);
            return Result.success(loginResult);
        } catch (AuthenticationException e) {
            return Result.error(401, "用户名或密码错误");
        } catch (Exception e) {
            log.error("Login failed for user {}", request.getUsername(), e);
            return Result.error("登录失败，请检查服务器或数据库配置");
        }
    }

    @PostMapping("/register")
    public Result<String> register(@RequestBody SysUser user) {
        try {
            sysUserService.register(user);
            return Result.success("注册成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
    
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserService.getOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            return Result.error(401, "用户不存在");
        }
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("avatar", user.getAvatar());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        info.put("role", user.getRole());
        info.put("status", user.getStatus());
        info.put("createTime", user.getCreateTime());
        info.put("lastLoginTime", user.getLastLoginTime());
        info.put("profilePromptRequired", Boolean.TRUE.equals(user.getProfilePromptRequired()));
        return Result.success(info);
    }

    private boolean updateLoginProfilePromptState(SysUser user) {
        if (user == null) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean inactiveForLongTime = user.getLastLoginTime() != null
                && user.getLastLoginTime().isBefore(now.minusDays(PROFILE_PROMPT_INACTIVE_DAYS));
        boolean promptRequired = Boolean.TRUE.equals(user.getProfilePromptRequired()) || inactiveForLongTime;

        user.setLastLoginTime(now);
        user.setProfilePromptRequired(false);
        sysUserService.updateById(user);
        return promptRequired;
    }

    @PutMapping("/update-profile")
    public Result<String> updateProfile(@RequestBody UpdateProfileRequest request) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            SysUser user = sysUserService.getOne(
                    new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
            if (user == null) {
                return Result.error(401, "用户不存在");
            }
            if (request.getNickname() != null) user.setNickname(request.getNickname());
            if (request.getEmail() != null) user.setEmail(request.getEmail());
            if (request.getPhone() != null) user.setPhone(request.getPhone());
            if (request.getAvatar() != null) user.setAvatar(request.getAvatar());
            sysUserService.updateById(user);
            return Result.success("个人资料更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/change-password")
    public Result<String> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            sysUserService.changePassword(username, request.getOldPassword(), request.getNewPassword());
            return Result.success("密码修改成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    static class UpdateProfileRequest {
        private String nickname;
        private String email;
        private String phone;
        private String avatar;
    }

    @Data
    static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }
}
