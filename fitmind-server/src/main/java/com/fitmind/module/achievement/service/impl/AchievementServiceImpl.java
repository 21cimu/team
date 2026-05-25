package com.fitmind.module.achievement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fitmind.module.achievement.entity.Achievement;
import com.fitmind.module.achievement.entity.UserAchievement;
import com.fitmind.module.achievement.mapper.AchievementMapper;
import com.fitmind.module.achievement.mapper.UserAchievementMapper;
import com.fitmind.module.achievement.service.IAchievementService;
import com.fitmind.module.community.entity.CommunityPost;
import com.fitmind.module.community.entity.UserFollow;
import com.fitmind.module.community.mapper.CommunityPostMapper;
import com.fitmind.module.community.mapper.UserFollowMapper;
import com.fitmind.module.diet.mapper.AiDietPlanMapper;
import com.fitmind.module.notification.service.INotificationService;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.mapper.AiTrainingPlanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AchievementServiceImpl extends ServiceImpl<AchievementMapper, Achievement> implements IAchievementService {

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final AiTrainingPlanMapper aiTrainingPlanMapper;
    private final AiDietPlanMapper aiDietPlanMapper;
    private final CommunityPostMapper communityPostMapper;
    private final UserFollowMapper userFollowMapper;
    private final INotificationService notificationService;

    @Override
    public List<Map<String, Object>> getUserAchievements(Long userId) {
        List<Achievement> allAchievements = defaultIfNull(achievementMapper.selectList(
                new LambdaQueryWrapper<Achievement>().orderByAsc(Achievement::getSortOrder)));

        List<UserAchievement> userAchievements = defaultIfNull(userAchievementMapper.selectList(
                new LambdaQueryWrapper<UserAchievement>().eq(UserAchievement::getUserId, userId)));

        Map<Long, UserAchievement> userAchievementMap = new HashMap<>();
        for (UserAchievement ua : userAchievements) {
            userAchievementMap.put(ua.getAchievementId(), ua);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Achievement ach : allAchievements) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", ach.getId());
            item.put("icon", ach.getIcon());
            item.put("name", ach.getName());
            item.put("description", ach.getDescription());
            item.put("category", ach.getCategory());
            item.put("rarity", ach.getRarity());
            item.put("target", ach.getTarget());

            UserAchievement ua = userAchievementMap.get(ach.getId());
            if (ua != null) {
                item.put("progress", ua.getProgress());
                item.put("unlocked", ua.getUnlocked());
                item.put("date", ua.getUnlockTime() != null ? ua.getUnlockTime().toString().substring(0, 10) : null);
            } else {
                item.put("progress", 0);
                item.put("unlocked", false);
                item.put("date", null);
            }

            result.add(item);
        }

        return result;
    }

    @Override
    public void checkAndUnlockAchievements(Long userId) {
        long trainingCount = aiTrainingPlanMapper.selectCount(
                new LambdaQueryWrapper<AiTrainingPlan>()
                        .eq(AiTrainingPlan::getUserId, userId)
                        .eq(AiTrainingPlan::getStatus, 1));

        long dietCount = aiDietPlanMapper.selectCount(
                new LambdaQueryWrapper<com.fitmind.module.diet.entity.AiDietPlan>()
                        .eq(com.fitmind.module.diet.entity.AiDietPlan::getUserId, userId));

        long postCount = communityPostMapper.selectCount(
                new LambdaQueryWrapper<com.fitmind.module.community.entity.CommunityPost>()
                        .eq(com.fitmind.module.community.entity.CommunityPost::getUserId, userId));

        int trainingStreak = calculateLongestTrainingStreak(userId);
        int dietStreak = calculateLongestDietStreak(userId);
        int totalLikes = calculateTotalPostLikes(userId);
        int socialCoreProgress = calculateSocialCoreProgress(userId);

        List<Achievement> allAchievements = defaultIfNull(achievementMapper.selectList(new LambdaQueryWrapper<>()));
        for (Achievement ach : allAchievements) {
            int progress = calculateProgress(
                    ach,
                    trainingCount,
                    dietCount,
                    postCount,
                    trainingStreak,
                    dietStreak,
                    totalLikes,
                    socialCoreProgress);

            UserAchievement ua = userAchievementMapper.selectOne(
                    new LambdaQueryWrapper<UserAchievement>()
                            .eq(UserAchievement::getUserId, userId)
                            .eq(UserAchievement::getAchievementId, ach.getId()));

            if (ua == null) {
                ua = new UserAchievement();
                ua.setUserId(userId);
                ua.setAchievementId(ach.getId());
                ua.setProgress(progress);
                ua.setUnlocked(progress >= ach.getTarget());
                ua.setCreateTime(LocalDateTime.now());
                if (ua.getUnlocked()) {
                    ua.setUnlockTime(LocalDateTime.now());
                    notificationService.sendNotification(userId, "ACHIEVEMENT", "成就解锁",
                            "恭喜！你解锁了成就: " + ach.getName() + " - " + ach.getDescription(),
                            ach.getId(), "achievement");
                }
                ua.setUpdateTime(LocalDateTime.now());
                userAchievementMapper.insert(ua);
            } else {
                ua.setProgress(progress);
                if (!ua.getUnlocked() && progress >= ach.getTarget()) {
                    ua.setUnlocked(true);
                    ua.setUnlockTime(LocalDateTime.now());
                    notificationService.sendNotification(userId, "ACHIEVEMENT", "成就解锁",
                            "恭喜！你解锁了成就: " + ach.getName() + " - " + ach.getDescription(),
                            ach.getId(), "achievement");
                }
                ua.setUpdateTime(LocalDateTime.now());
                userAchievementMapper.updateById(ua);
            }
        }
    }

    private int calculateProgress(
            Achievement ach,
            long trainingCount,
            long dietCount,
            long postCount,
            int trainingStreak,
            int dietStreak,
            int totalLikes,
            int socialCoreProgress) {
        String category = ach.getCategory();
        String name = ach.getName();

        if ("training".equals(category)) {
            if ("周而复始".equals(name) || "永不停歇".equals(name)) {
                return Math.min(trainingStreak, ach.getTarget());
            }
            return (int) Math.min(trainingCount, ach.getTarget());
        }

        if ("nutrition".equals(category)) {
            if ("蛋白质大亨".equals(name) || "营养专家".equals(name)) {
                return Math.min(dietStreak, ach.getTarget());
            }
            return (int) Math.min(dietCount, ach.getTarget());
        }

        if ("social".equals(category)) {
            if ("社交达人".equals(name)) {
                return Math.min(totalLikes, ach.getTarget());
            }
            if ("网络核心".equals(name)) {
                return Math.min(socialCoreProgress, ach.getTarget());
            }
            return (int) Math.min(postCount, ach.getTarget());
        }

        return 0;
    }

    private int calculateLongestTrainingStreak(Long userId) {
        List<AiTrainingPlan> completedPlans = defaultIfNull(aiTrainingPlanMapper.selectList(
                new LambdaQueryWrapper<AiTrainingPlan>()
                        .eq(AiTrainingPlan::getUserId, userId)
                        .eq(AiTrainingPlan::getStatus, 1)
                        .orderByAsc(AiTrainingPlan::getPlanDate)));
        return calculateLongestConsecutiveDays(extractPlanDates(completedPlans));
    }

    private int calculateLongestDietStreak(Long userId) {
        List<com.fitmind.module.diet.entity.AiDietPlan> completedPlans = defaultIfNull(aiDietPlanMapper.selectList(
                new LambdaQueryWrapper<com.fitmind.module.diet.entity.AiDietPlan>()
                        .eq(com.fitmind.module.diet.entity.AiDietPlan::getUserId, userId)
                        .eq(com.fitmind.module.diet.entity.AiDietPlan::getStatus, 1)
                        .orderByAsc(com.fitmind.module.diet.entity.AiDietPlan::getPlanDate)));
        return calculateLongestConsecutiveDays(extractDietDates(completedPlans));
    }

    private int calculateTotalPostLikes(Long userId) {
        List<CommunityPost> posts = defaultIfNull(communityPostMapper.selectList(
                new LambdaQueryWrapper<CommunityPost>().eq(CommunityPost::getUserId, userId)));
        return posts.stream()
                .map(CommunityPost::getLikes)
                .filter(likes -> likes != null && likes > 0)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int calculateSocialCoreProgress(Long userId) {
        long followingCount = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, userId));
        long followerCount = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowingId, userId));
        return (int) Math.min(followingCount, followerCount);
    }

    private List<LocalDate> extractPlanDates(List<AiTrainingPlan> plans) {
        Set<LocalDate> uniqueDates = new HashSet<>();
        List<LocalDate> orderedDates = new ArrayList<>();
        for (AiTrainingPlan plan : plans) {
            LocalDate planDate = plan.getPlanDate();
            if (planDate != null && uniqueDates.add(planDate)) {
                orderedDates.add(planDate);
            }
        }
        return orderedDates;
    }

    private List<LocalDate> extractDietDates(List<com.fitmind.module.diet.entity.AiDietPlan> plans) {
        Set<LocalDate> uniqueDates = new HashSet<>();
        List<LocalDate> orderedDates = new ArrayList<>();
        for (com.fitmind.module.diet.entity.AiDietPlan plan : plans) {
            LocalDate planDate = plan.getPlanDate();
            if (planDate != null && uniqueDates.add(planDate)) {
                orderedDates.add(planDate);
            }
        }
        return orderedDates;
    }

    private int calculateLongestConsecutiveDays(List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return 0;
        }

        int longest = 1;
        int current = 1;

        for (int index = 1; index < dates.size(); index += 1) {
            LocalDate previous = dates.get(index - 1);
            LocalDate currentDate = dates.get(index);
            if (previous.plusDays(1).equals(currentDate)) {
                current += 1;
                longest = Math.max(longest, current);
            } else if (!previous.equals(currentDate)) {
                current = 1;
            }
        }

        return longest;
    }

    private <T> List<T> defaultIfNull(List<T> items) {
        return items != null ? items : List.of();
    }
}
