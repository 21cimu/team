package com.fitmind.module.achievement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.module.achievement.entity.Achievement;
import com.fitmind.module.achievement.entity.UserAchievement;
import com.fitmind.module.achievement.mapper.AchievementMapper;
import com.fitmind.module.achievement.mapper.UserAchievementMapper;
import com.fitmind.module.achievement.service.impl.AchievementServiceImpl;
import com.fitmind.module.community.entity.CommunityPost;
import com.fitmind.module.community.mapper.CommunityPostMapper;
import com.fitmind.module.diet.entity.AiDietPlan;
import com.fitmind.module.diet.mapper.AiDietPlanMapper;
import com.fitmind.module.training.entity.AiTrainingPlan;
import com.fitmind.module.training.mapper.AiTrainingPlanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceImplTest {

    @Mock
    private AchievementMapper achievementMapper;

    @Mock
    private UserAchievementMapper userAchievementMapper;

    @Mock
    private AiTrainingPlanMapper aiTrainingPlanMapper;

    @Mock
    private AiDietPlanMapper aiDietPlanMapper;

    @Mock
    private CommunityPostMapper communityPostMapper;

    @InjectMocks
    private AchievementServiceImpl achievementService;

    private List<Achievement> sampleAchievements;

    @BeforeEach
    void setUp() {
        sampleAchievements = new ArrayList<>();
        Achievement a1 = new Achievement();
        a1.setId(1L);
        a1.setName("初试锋芒");
        a1.setCategory("training");
        a1.setTarget(1);
        a1.setSortOrder(1);
        sampleAchievements.add(a1);

        Achievement a2 = new Achievement();
        a2.setId(2L);
        a2.setName("纯净饮食");
        a2.setCategory("nutrition");
        a2.setTarget(1);
        a2.setSortOrder(2);
        sampleAchievements.add(a2);

        Achievement a3 = new Achievement();
        a3.setId(3L);
        a3.setName("首次发声");
        a3.setCategory("social");
        a3.setTarget(1);
        a3.setSortOrder(3);
        sampleAchievements.add(a3);
    }

    @Test
    void getUserAchievements_shouldReturnAllAchievementsWithProgress() {
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleAchievements);
        when(userAchievementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

        List<Map<String, Object>> result = achievementService.getUserAchievements(1L);

        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("初试锋芒", result.get(0).get("name"));
        assertEquals(0, result.get(0).get("progress"));
        assertEquals(false, result.get(0).get("unlocked"));
    }

    @Test
    void getUserAchievements_shouldReturnUnlockedStatusForExistingRecords() {
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(sampleAchievements);

        UserAchievement ua = new UserAchievement();
        ua.setAchievementId(1L);
        ua.setProgress(1);
        ua.setUnlocked(true);
        when(userAchievementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(ua));

        List<Map<String, Object>> result = achievementService.getUserAchievements(1L);

        assertEquals(1, result.get(0).get("progress"));
        assertEquals(true, result.get(0).get("unlocked"));
        assertEquals(0, result.get(1).get("progress"));
        assertEquals(false, result.get(1).get("unlocked"));
    }

    @Test
    void checkAndUnlockAchievements_shouldCreateNewUserAchievement() {
        when(achievementMapper.selectList(any())).thenReturn(sampleAchievements);
        when(aiTrainingPlanMapper.selectCount(any())).thenReturn(5L);
        when(aiDietPlanMapper.selectCount(any())).thenReturn(3L);
        when(communityPostMapper.selectCount(any())).thenReturn(2L);
        when(userAchievementMapper.selectOne(any())).thenReturn(null);

        achievementService.checkAndUnlockAchievements(1L);

        verify(userAchievementMapper, times(3)).insert(any(UserAchievement.class));
    }

    @Test
    void checkAndUnlockAchievements_shouldUpdateExistingUserAchievement() {
        when(achievementMapper.selectList(any())).thenReturn(sampleAchievements);
        when(aiTrainingPlanMapper.selectCount(any())).thenReturn(5L);
        when(aiDietPlanMapper.selectCount(any())).thenReturn(3L);
        when(communityPostMapper.selectCount(any())).thenReturn(2L);

        UserAchievement existingUa = new UserAchievement();
        existingUa.setId(1L);
        existingUa.setAchievementId(1L);
        existingUa.setProgress(0);
        existingUa.setUnlocked(false);
        when(userAchievementMapper.selectOne(any())).thenReturn(existingUa);

        achievementService.checkAndUnlockAchievements(1L);

        verify(userAchievementMapper, times(3)).updateById(any(UserAchievement.class));
    }

    @Test
    void checkAndUnlockAchievements_shouldUnlockWhenTargetMet() {
        Achievement trainingAch = new Achievement();
        trainingAch.setId(1L);
        trainingAch.setCategory("training");
        trainingAch.setTarget(5);
        when(achievementMapper.selectList(any())).thenReturn(List.of(trainingAch));
        when(aiTrainingPlanMapper.selectCount(any())).thenReturn(5L);
        when(aiDietPlanMapper.selectCount(any())).thenReturn(0L);
        when(communityPostMapper.selectCount(any())).thenReturn(0L);

        UserAchievement ua = new UserAchievement();
        ua.setId(1L);
        ua.setUnlocked(false);
        ua.setProgress(0);
        when(userAchievementMapper.selectOne(any())).thenReturn(ua);

        achievementService.checkAndUnlockAchievements(1L);

        verify(userAchievementMapper).updateById(argThat(argument ->
                argument.getUnlocked() && argument.getProgress() == 5
        ));
    }
}
