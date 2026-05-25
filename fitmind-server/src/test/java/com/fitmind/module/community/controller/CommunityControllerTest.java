package com.fitmind.module.community.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.module.community.entity.CommunityPost;
import com.fitmind.module.community.mapper.CommunityCommentMapper;
import com.fitmind.module.community.mapper.CommunityPostMapper;
import com.fitmind.module.community.mapper.UserFollowMapper;
import com.fitmind.module.community.service.ICommunityPostService;
import com.fitmind.module.diet.mapper.AiDietPlanMapper;
import com.fitmind.module.training.mapper.AiTrainingPlanMapper;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityControllerTest {

    @Mock
    private ICommunityPostService communityPostService;

    @Mock
    private CommunityPostMapper communityPostMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private CommunityCommentMapper communityCommentMapper;

    @Mock
    private UserFollowMapper userFollowMapper;

    @Mock
    private AiTrainingPlanMapper aiTrainingPlanMapper;

    @Mock
    private AiDietPlanMapper aiDietPlanMapper;

    @InjectMocks
    private CommunityController communityController;

    @BeforeEach
    void setUp() {
        SysUser mockUser = new SysUser();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        lenient().when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testuser", null));
    }

    @Test
    void getTrendingTags_shouldReturnDefaultTagsWhenNoPosts() {
        when(communityPostMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

        var result = communityController.getTrendingTags();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        List<Map<String, Object>> tags = result.getData();
        assertEquals(5, tags.size());
        assertEquals("CHEST DAY", tags.get(0).get("name"));
    }

    @Test
    void getTrendingTags_shouldExtractHashtagsFromPosts() {
        CommunityPost post = new CommunityPost();
        post.setContent("Great #CHEST workout today! #GAINS");
        when(communityPostMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(post));

        var result = communityController.getTrendingTags();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        List<Map<String, Object>> tags = result.getData();
        assertFalse(tags.isEmpty());
    }

    @Test
    void getNetworkStats_shouldReturnStats() {
        when(sysUserMapper.selectCount(any())).thenReturn(100L);
        when(communityPostMapper.selectCount(any())).thenReturn(50L);
        when(aiTrainingPlanMapper.selectCount(any())).thenReturn(200L);

        var result = communityController.getNetworkStats();

        assertNotNull(result);
        assertTrue(result.isSuccess());
        Map<String, Object> stats = result.getData();
        assertEquals(100L, stats.get("active"));
        assertEquals(50L, stats.get("postsToday"));
        assertEquals(200L, stats.get("workouts"));
    }

    @Test
    void getMyFollowing_shouldReturnFollowedList() {
        when(userFollowMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

        var result = communityController.getMyFollowing();

        assertNotNull(result);
        assertTrue(result.isSuccess());
    }
}
