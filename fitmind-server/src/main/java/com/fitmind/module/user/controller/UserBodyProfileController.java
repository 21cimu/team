package com.fitmind.module.user.controller;

import com.fitmind.common.api.Result;
import com.fitmind.module.user.entity.UserBodyProfile;
import com.fitmind.module.user.service.IUserBodyProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserBodyProfileController {

    private final IUserBodyProfileService userBodyProfileService;

    @GetMapping("/me")
    public Result<UserBodyProfile> getMyProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        UserBodyProfile profile = userBodyProfileService.getByUsername(username);
        return Result.success(profile);
    }

    @PostMapping("/save")
    public Result<String> saveProfile(@RequestBody UserBodyProfile profile) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        try {
            userBodyProfileService.saveOrUpdateProfile(username, profile);
            return Result.success("身体指标保存成功");
        } catch (Exception e) {
            return Result.error("保存身体指标失败：" + e.getMessage());
        }
    }
}
