package com.fitmind.module.user.controller;

import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.user.entity.UserBodyProfile;
import com.fitmind.module.user.service.IUserBodyProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserBodyProfileController {

    private final IUserBodyProfileService userBodyProfileService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/me")
    public Result<UserBodyProfile> getMyProfile() {
        UserBodyProfile profile = userBodyProfileService.getByUsername(currentUserProvider.getCurrentUsername());
        return Result.success(profile);
    }

    @PostMapping("/save")
    public Result<String> saveProfile(@RequestBody UserBodyProfile profile) {
        try {
            userBodyProfileService.saveOrUpdateProfile(currentUserProvider.getCurrentUsername(), profile);
            return Result.success("身体指标保存成功");
        } catch (Exception e) {
            return Result.error("保存身体指标失败：" + e.getMessage());
        }
    }
}
