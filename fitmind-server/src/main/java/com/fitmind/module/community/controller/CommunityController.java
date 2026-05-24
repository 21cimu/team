package com.fitmind.module.community.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fitmind.common.api.Result;
import com.fitmind.module.community.entity.CommunityComment;
import com.fitmind.module.community.entity.CommunityPost;
import com.fitmind.module.community.entity.UserFollow;
import com.fitmind.module.community.mapper.CommunityCommentMapper;
import com.fitmind.module.community.mapper.CommunityPostMapper;
import com.fitmind.module.community.mapper.UserFollowMapper;
import com.fitmind.module.community.service.ICommunityPostService;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.mapper.AiDietPlanMapper;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.mapper.AiTrainingPlanMapper;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.mapper.SysUserMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final ICommunityPostService communityPostService;
    private final CommunityPostMapper communityPostMapper;
    private final SysUserMapper sysUserMapper;
    private final CommunityCommentMapper communityCommentMapper;
    private final UserFollowMapper userFollowMapper;
    private final AiTrainingPlanMapper aiTrainingPlanMapper;
    private final AiDietPlanMapper aiDietPlanMapper;

    private SysUser getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new RuntimeException("用户未认证");
        }
        return user;
    }

    @PostMapping("/post")
    public Result<String> createPost(@RequestBody PostRequest request) {
        try {
            communityPostService.createPost(getCurrentUser().getId(), request.getContent());
            return Result.success("动态发布成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/post/{postId}")
    public Result<String> deletePost(@PathVariable Long postId) {
        try {
            communityPostService.deletePost(postId, getCurrentUser().getId());
            return Result.success("帖子删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/feed")
    public Result<Page<CommunityPost>> getFeed(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<CommunityPost> feed = communityPostService.getFeed(getCurrentUser().getId(), current, size);
            return Result.success(feed);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/like/{postId}")
    public Result<CommunityPost> likePost(@PathVariable Long postId) {
        try {
            CommunityPost post = communityPostService.likePost(postId, getCurrentUser().getId());
            return Result.success(post);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/comment/{postId}")
    public Result<String> addComment(@PathVariable Long postId, @RequestBody CommentRequest request) {
        try {
            SysUser user = getCurrentUser();
            CommunityComment comment = new CommunityComment();
            comment.setPostId(postId);
            comment.setUserId(user.getId());
            comment.setContent(request.getContent());
            comment.setCreateTime(LocalDateTime.now());
            communityCommentMapper.insert(comment);
            return Result.success("评论添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/comments/{postId}")
    public Result<List<CommunityComment>> getComments(@PathVariable Long postId) {
        List<CommunityComment> comments = communityCommentMapper.selectList(
                new LambdaQueryWrapper<CommunityComment>()
                        .eq(CommunityComment::getPostId, postId)
                        .orderByAsc(CommunityComment::getCreateTime));
        for (CommunityComment comment : comments) {
            SysUser user = sysUserMapper.selectById(comment.getUserId());
            comment.setUsername(user != null ? user.getUsername() : "UNKNOWN");
        }
        return Result.success(comments);
    }

    @PostMapping("/follow/{targetUserId}")
    public Result<String> followUser(@PathVariable Long targetUserId) {
        try {
            SysUser currentUser = getCurrentUser();
            if (currentUser.getId().equals(targetUserId)) {
                return Result.error("不能关注自己");
            }
            boolean exists = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, currentUser.getId())
                    .eq(UserFollow::getFollowingId, targetUserId)) > 0;
            if (!exists) {
                UserFollow follow = new UserFollow();
                follow.setFollowerId(currentUser.getId());
                follow.setFollowingId(targetUserId);
                follow.setCreateTime(LocalDateTime.now());
                userFollowMapper.insert(follow);
            }
            return Result.success("关注成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/unfollow/{targetUserId}")
    public Result<String> unfollowUser(@PathVariable Long targetUserId) {
        try {
            SysUser currentUser = getCurrentUser();
            userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getFollowerId, currentUser.getId())
                    .eq(UserFollow::getFollowingId, targetUserId));
            return Result.success("已取消关注");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/following")
    public Result<List<Map<String, Object>>> getMyFollowing() {
        try {
            SysUser currentUser = getCurrentUser();
            List<UserFollow> follows = userFollowMapper.selectList(
                    new LambdaQueryWrapper<UserFollow>()
                            .eq(UserFollow::getFollowerId, currentUser.getId()));
            List<Map<String, Object>> result = new ArrayList<>();
            for (UserFollow follow : follows) {
                SysUser user = sysUserMapper.selectById(follow.getFollowingId());
                if (user != null) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", user.getId());
                    item.put("username", user.getUsername() != null ? user.getUsername() : "USER");
                    result.add(item);
                }
            }
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/trending")
    public Result<List<Map<String, Object>>> getTrendingTags() {
        try {
            List<CommunityPost> posts = communityPostMapper.selectList(
                    new LambdaQueryWrapper<CommunityPost>()
                            .isNotNull(CommunityPost::getContent)
                            .orderByDesc(CommunityPost::getCreateTime)
                            .last("LIMIT 100"));

            Map<String, Integer> tagCount = new HashMap<>();
            for (CommunityPost post : posts) {
                String content = post.getContent();
                if (content == null) {
                    continue;
                }
                String[] words = content.split("\\s+");
                for (String word : words) {
                    if (word.startsWith("#") && word.length() > 1) {
                        String tag = word.substring(1).toUpperCase();
                        tagCount.merge(tag, 1, Integer::sum);
                    }
                }
            }

            List<Map<String, Object>> trending = new ArrayList<>();
            tagCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("name", entry.getKey());
                        item.put("count", entry.getValue());
                        trending.add(item);
                    });

            if (trending.isEmpty()) {
                String[][] defaultTags = {
                        {"CHEST DAY", "42"},
                        {"FAT LOSS", "38"},
                        {"MUSCLE GAIN", "31"},
                        {"HIIT", "27"},
                        {"PROTEIN", "22"}
                };
                for (String[] tag : defaultTags) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", tag[0]);
                    item.put("count", Integer.parseInt(tag[1]));
                    trending.add(item);
                }
            }

            return Result.success(trending);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getNetworkStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            long totalUsers = sysUserMapper.selectCount(new LambdaQueryWrapper<>());
            long totalPosts = communityPostMapper.selectCount(new LambdaQueryWrapper<>());
            long totalWorkouts = aiTrainingPlanMapper.selectCount(
                    new LambdaQueryWrapper<AiTrainingPlan>().eq(AiTrainingPlan::getStatus, 1));
            stats.put("active", totalUsers);
            stats.put("postsToday", totalPosts);
            stats.put("workouts", totalWorkouts);
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/leaderboard")
    public Result<List<Map<String, Object>>> getLeaderboard(
            @RequestParam(defaultValue = "training") String category,
            @RequestParam(defaultValue = "weekly") String period) {
        try {
            List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().last("LIMIT 20"));
            List<Map<String, Object>> leaderboard = new ArrayList<>();
            int rank = 1;
            for (SysUser user : users) {
                long score;
                switch (category) {
                    case "training":
                        score = aiTrainingPlanMapper.selectCount(new LambdaQueryWrapper<AiTrainingPlan>()
                                .eq(AiTrainingPlan::getUserId, user.getId())
                                .eq(AiTrainingPlan::getStatus, 1));
                        break;
                    case "streak":
                        List<AiTrainingPlan> userPlans = aiTrainingPlanMapper.selectList(
                                new LambdaQueryWrapper<AiTrainingPlan>()
                                        .eq(AiTrainingPlan::getUserId, user.getId())
                                        .orderByDesc(AiTrainingPlan::getPlanDate)
                                        .last("LIMIT 30"));
                        score = calculateStreak(userPlans);
                        break;
                    case "calories":
                        List<AiDietPlan> dietPlans = aiDietPlanMapper.selectList(
                                new LambdaQueryWrapper<AiDietPlan>()
                                        .eq(AiDietPlan::getUserId, user.getId()));
                        score = dietPlans.stream()
                                .mapToLong(plan -> plan.getTotalCalories() != null ? plan.getTotalCalories() : 0)
                                .sum();
                        break;
                    case "social":
                        long postCount = communityPostMapper.selectCount(
                                new LambdaQueryWrapper<CommunityPost>()
                                        .eq(CommunityPost::getUserId, user.getId()));
                        long totalLikes = communityPostMapper.selectList(
                                new LambdaQueryWrapper<CommunityPost>()
                                        .eq(CommunityPost::getUserId, user.getId()))
                                .stream()
                                .mapToLong(post -> post.getLikes() != null ? post.getLikes() : 0)
                                .sum();
                        score = postCount + totalLikes;
                        break;
                    default:
                        score = aiTrainingPlanMapper.selectCount(new LambdaQueryWrapper<AiTrainingPlan>()
                                .eq(AiTrainingPlan::getUserId, user.getId())
                                .eq(AiTrainingPlan::getStatus, 1));
                }
                int change = rank <= 5 ? (int) (Math.random() * 5) : 0;
                Map<String, Object> entry = new HashMap<>();
                entry.put("rank", rank++);
                entry.put("userId", user.getId());
                entry.put("username", user.getUsername() != null ? user.getUsername() : "USER");
                entry.put("score", score);
                entry.put("change", change);
                leaderboard.add(entry);
            }
            leaderboard.sort((left, right) -> Long.compare((Long) right.get("score"), (Long) left.get("score")));
            for (int i = 0; i < leaderboard.size(); i++) {
                leaderboard.get(i).put("rank", i + 1);
            }
            return Result.success(leaderboard);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    private int calculateStreak(List<AiTrainingPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return 0;
        }
        int streak = 1;
        for (int i = 1; i < plans.size(); i++) {
            if (plans.get(i - 1).getPlanDate() != null && plans.get(i).getPlanDate() != null) {
                long diff = java.time.temporal.ChronoUnit.DAYS.between(
                        plans.get(i).getPlanDate(), plans.get(i - 1).getPlanDate());
                if (diff <= 1) {
                    streak++;
                } else {
                    break;
                }
            }
        }
        return streak;
    }

    @Data
    static class PostRequest {
        private String content;
    }

    @Data
    static class CommentRequest {
        private String content;
    }
}
