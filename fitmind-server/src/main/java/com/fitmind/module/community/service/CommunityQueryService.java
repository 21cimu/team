package com.fitmind.module.community.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.module.community.entity.CommunityComment;
import com.fitmind.module.community.entity.CommunityPost;
import com.fitmind.module.community.entity.UserFollow;
import com.fitmind.module.community.mapper.CommunityCommentMapper;
import com.fitmind.module.community.mapper.CommunityPostMapper;
import com.fitmind.module.community.mapper.UserFollowMapper;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.mapper.AiDietPlanMapper;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.mapper.AiTrainingPlanMapper;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CommunityQueryService {

    private final CommunityPostMapper communityPostMapper;
    private final CommunityCommentMapper communityCommentMapper;
    private final UserFollowMapper userFollowMapper;
    private final SysUserMapper sysUserMapper;
    private final AiTrainingPlanMapper aiTrainingPlanMapper;
    private final AiDietPlanMapper aiDietPlanMapper;

    public List<CommunityComment> getComments(Long postId) {
        List<CommunityComment> comments = communityCommentMapper.selectList(
                new LambdaQueryWrapper<CommunityComment>()
                        .eq(CommunityComment::getPostId, postId)
                        .orderByAsc(CommunityComment::getCreateTime));
        for (CommunityComment comment : comments) {
            SysUser user = sysUserMapper.selectById(comment.getUserId());
            comment.setUsername(user != null ? user.getUsername() : "UNKNOWN");
        }
        return comments;
    }

    public List<Map<String, Object>> getFollowing(Long currentUserId) {
        List<UserFollow> follows = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, currentUserId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserFollow follow : follows) {
            SysUser user = sysUserMapper.selectById(follow.getFollowingId());
            if (user == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", user.getId());
            item.put("username", user.getUsername() != null ? user.getUsername() : "USER");
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> getTrendingTags() {
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
            for (String word : content.split("\\s+")) {
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

        if (!trending.isEmpty()) {
            return trending;
        }

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
        return trending;
    }

    public Map<String, Object> getNetworkStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active", sysUserMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.put("postsToday", communityPostMapper.selectCount(new LambdaQueryWrapper<>()));
        stats.put("workouts", aiTrainingPlanMapper.selectCount(
                new LambdaQueryWrapper<AiTrainingPlan>().eq(AiTrainingPlan::getStatus, 1)));
        return stats;
    }

    public List<Map<String, Object>> getLeaderboard(String category, String period) {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().last("LIMIT 20"));
        List<Map<String, Object>> leaderboard = new ArrayList<>();
        int rank = 1;

        for (SysUser user : users) {
            long score = resolveScore(category, period, user);
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
        return leaderboard;
    }

    private long resolveScore(String category, String period, SysUser user) {
        switch (category) {
            case "streak":
                List<AiTrainingPlan> userPlans = aiTrainingPlanMapper.selectList(
                        new LambdaQueryWrapper<AiTrainingPlan>()
                                .eq(AiTrainingPlan::getUserId, user.getId())
                                .orderByDesc(AiTrainingPlan::getPlanDate)
                                .last("LIMIT 30"));
                return calculateStreak(userPlans);
            case "calories":
                List<AiDietPlan> dietPlans = aiDietPlanMapper.selectList(
                        new LambdaQueryWrapper<AiDietPlan>().eq(AiDietPlan::getUserId, user.getId()));
                return dietPlans.stream()
                        .mapToLong(plan -> plan.getTotalCalories() != null ? plan.getTotalCalories() : 0)
                        .sum();
            case "social":
                long postCount = communityPostMapper.selectCount(
                        new LambdaQueryWrapper<CommunityPost>().eq(CommunityPost::getUserId, user.getId()));
                long totalLikes = communityPostMapper.selectList(
                                new LambdaQueryWrapper<CommunityPost>().eq(CommunityPost::getUserId, user.getId()))
                        .stream()
                        .mapToLong(post -> post.getLikes() != null ? post.getLikes() : 0)
                        .sum();
                return postCount + totalLikes;
            case "training":
            default:
                return aiTrainingPlanMapper.selectCount(new LambdaQueryWrapper<AiTrainingPlan>()
                        .eq(AiTrainingPlan::getUserId, user.getId())
                        .eq(AiTrainingPlan::getStatus, 1));
        }
    }

    private int calculateStreak(List<AiTrainingPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return 0;
        }
        int streak = 1;
        for (int i = 1; i < plans.size(); i++) {
            if (plans.get(i - 1).getPlanDate() != null && plans.get(i).getPlanDate() != null) {
                long diff = ChronoUnit.DAYS.between(plans.get(i).getPlanDate(), plans.get(i - 1).getPlanDate());
                if (diff <= 1) {
                    streak++;
                } else {
                    break;
                }
            }
        }
        return streak;
    }
}
