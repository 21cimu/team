package com.fitmind.module.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.achievement.entity.Achievement;
import com.fitmind.module.achievement.service.IAchievementService;
import com.fitmind.module.community.entity.CommunityPost;
import com.fitmind.module.community.service.ICommunityPostService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.service.ISysUserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final CurrentUserProvider currentUserProvider;
    private final ISysUserService sysUserService;
    private final IAchievementService achievementService;
    private final ICommunityPostService communityPostService;

    private void checkAdmin() {
        SysUser user = currentUserProvider.getCurrentUser();
        if (!"ADMIN".equals(user.getRole()) && !"admin".equals(user.getRole())) {
            throw new RuntimeException("权限不足：需要管理员角色");
        }
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        checkAdmin();
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", sysUserService.count());
        stats.put("totalPosts", communityPostService.count());
        stats.put("totalAchievements", achievementService.count());
        return Result.success(stats);
    }

    @GetMapping("/users")
    public Result<Page<SysUser>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        checkAdmin();
        Page<SysUser> pageReq = new Page<>(page, size);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SysUser::getUsername, keyword).or().like(SysUser::getNickname, keyword);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> result = sysUserService.page(pageReq, wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return Result.success(result);
    }

    @PutMapping("/users/{id}/status")
    public Result<String> updateUserStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        checkAdmin();
        SysUser user = sysUserService.getById(id);
        if (user == null) return Result.error("用户不存在");
        user.setStatus(request.getStatus());
        sysUserService.updateById(user);
        return Result.success("状态更新成功");
    }

    @PutMapping("/users/{id}/role")
    public Result<String> updateUserRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        checkAdmin();
        SysUser user = sysUserService.getById(id);
        if (user == null) return Result.error("用户不存在");
        user.setRole(request.getRole());
        sysUserService.updateById(user);
        return Result.success("角色更新成功");
    }

    @DeleteMapping("/users/{id}")
    public Result<String> deleteUser(@PathVariable Long id) {
        checkAdmin();
        SysUser user = sysUserService.getById(id);
        if (user == null) return Result.error("用户不存在");
        if ("ADMIN".equals(user.getRole()) || "admin".equals(user.getRole())) {
            return Result.error("不能删除管理员账户");
        }
        sysUserService.removeById(id);
        return Result.success("用户删除成功");
    }

    @GetMapping("/posts")
    public Result<Page<CommunityPost>> listPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        checkAdmin();
        Page<CommunityPost> pageReq = new Page<>(page, size);
        Page<CommunityPost> result = communityPostService.page(pageReq,
                new LambdaQueryWrapper<CommunityPost>().orderByDesc(CommunityPost::getCreateTime));
        return Result.success(result);
    }

    @DeleteMapping("/posts/{id}")
    public Result<String> deletePost(@PathVariable Long id) {
        checkAdmin();
        communityPostService.removeById(id);
        return Result.success("帖子删除成功");
    }

    @PostMapping("/achievements")
    public Result<String> createAchievement(@RequestBody AchievementRequest request) {
        checkAdmin();
        Achievement ach = new Achievement();
        ach.setName(request.getName());
        ach.setDescription(request.getDescription());
        ach.setIcon(request.getIcon());
        ach.setCategory(request.getCategory());
        ach.setRarity(request.getRarity());
        ach.setTarget(request.getTarget());
        ach.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        achievementService.save(ach);
        return Result.success("成就创建成功");
    }

    @PutMapping("/achievements/{id}")
    public Result<String> updateAchievement(@PathVariable Long id, @RequestBody AchievementRequest request) {
        checkAdmin();
        Achievement ach = achievementService.getById(id);
        if (ach == null) return Result.error("成就不存在");
        if (request.getName() != null) ach.setName(request.getName());
        if (request.getDescription() != null) ach.setDescription(request.getDescription());
        if (request.getIcon() != null) ach.setIcon(request.getIcon());
        if (request.getCategory() != null) ach.setCategory(request.getCategory());
        if (request.getRarity() != null) ach.setRarity(request.getRarity());
        if (request.getTarget() != null) ach.setTarget(request.getTarget());
        if (request.getSortOrder() != null) ach.setSortOrder(request.getSortOrder());
        achievementService.updateById(ach);
        return Result.success("成就更新成功");
    }

    @DeleteMapping("/achievements/{id}")
    public Result<String> deleteAchievement(@PathVariable Long id) {
        checkAdmin();
        achievementService.removeById(id);
        return Result.success("成就删除成功");
    }

    @Data
    static class StatusRequest {
        private Integer status;
    }

    @Data
    static class RoleRequest {
        private String role;
    }

    @Data
    static class AchievementRequest {
        private String name;
        private String description;
        private String icon;
        private String category;
        private String rarity;
        private Integer target;
        private Integer sortOrder;
    }
}
