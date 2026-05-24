package com.fitmind.module.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fitmind.module.community.entity.CommunityComment;
import com.fitmind.module.community.entity.CommunityPost;
import com.fitmind.module.community.entity.CommunityPostLike;
import com.fitmind.module.community.mapper.CommunityCommentMapper;
import com.fitmind.module.community.mapper.CommunityPostLikeMapper;
import com.fitmind.module.community.mapper.CommunityPostMapper;
import com.fitmind.module.community.service.ICommunityPostService;
import com.fitmind.module.user.entity.SysUser;
import com.fitmind.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityPostServiceImpl extends ServiceImpl<CommunityPostMapper, CommunityPost> implements ICommunityPostService {

    private final SysUserMapper sysUserMapper;
    private final CommunityCommentMapper communityCommentMapper;
    private final CommunityPostLikeMapper communityPostLikeMapper;

    @Override
    public void createPost(Long userId, String content) {
        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setContent(content);
        post.setLikes(0);
        post.setCreateTime(LocalDateTime.now());
        this.save(post);
    }

    @Override
    public Page<CommunityPost> getFeed(Long currentUserId, int current, int size) {
        Page<CommunityPost> page = new Page<>(current, size);
        LambdaQueryWrapper<CommunityPost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(CommunityPost::getCreateTime);

        Page<CommunityPost> postPage = this.page(page, queryWrapper);
        List<CommunityPost> records = postPage.getRecords();
        Set<Long> likedPostIds = findLikedPostIds(currentUserId, records);

        for (CommunityPost post : records) {
            SysUser user = sysUserMapper.selectById(post.getUserId());
            post.setUsername(user != null ? user.getUsername() : "UNKNOWN");
            post.setLiked(likedPostIds.contains(post.getId()));
        }

        return postPage;
    }

    @Override
    @Transactional
    public CommunityPost likePost(Long postId, Long userId) {
        CommunityPost existingPost = this.getById(postId);
        if (existingPost == null) {
            throw new RuntimeException("帖子不存在");
        }

        CommunityPostLike existingLike = communityPostLikeMapper.selectOne(
                new LambdaQueryWrapper<CommunityPostLike>()
                        .eq(CommunityPostLike::getPostId, postId)
                        .eq(CommunityPostLike::getUserId, userId));

        boolean liked;
        if (existingLike == null) {
            try {
                CommunityPostLike postLike = new CommunityPostLike();
                postLike.setPostId(postId);
                postLike.setUserId(userId);
                postLike.setCreateTime(LocalDateTime.now());
                communityPostLikeMapper.insert(postLike);
                this.update(null, new LambdaUpdateWrapper<CommunityPost>()
                        .eq(CommunityPost::getId, postId)
                        .setSql("likes = likes + 1"));
            } catch (DuplicateKeyException ignored) {
                // Another request already created the active like.
            }
            liked = true;
        } else {
            int deleted = communityPostLikeMapper.deleteById(existingLike.getId());
            if (deleted > 0) {
                this.update(null, new LambdaUpdateWrapper<CommunityPost>()
                        .eq(CommunityPost::getId, postId)
                        .setSql("likes = CASE WHEN likes > 0 THEN likes - 1 ELSE 0 END"));
            }
            liked = false;
        }

        CommunityPost post = this.getById(postId);
        post.setLiked(liked);
        return post;
    }

    @Override
    @Transactional
    public void deletePost(Long postId, Long userId) {
        CommunityPost post = this.getById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        if (!userId.equals(post.getUserId())) {
            throw new RuntimeException("只能删除自己发布的帖子");
        }
        communityCommentMapper.delete(new LambdaQueryWrapper<CommunityComment>()
                .eq(CommunityComment::getPostId, postId));
        communityPostLikeMapper.delete(new LambdaQueryWrapper<CommunityPostLike>()
                .eq(CommunityPostLike::getPostId, postId));
        this.removeById(postId);
    }

    private Set<Long> findLikedPostIds(Long currentUserId, List<CommunityPost> posts) {
        if (currentUserId == null || posts.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> postIds = posts.stream().map(CommunityPost::getId).collect(Collectors.toList());
        return communityPostLikeMapper.selectList(new LambdaQueryWrapper<CommunityPostLike>()
                        .eq(CommunityPostLike::getUserId, currentUserId)
                        .in(CommunityPostLike::getPostId, postIds))
                .stream()
                .map(CommunityPostLike::getPostId)
                .collect(Collectors.toSet());
    }
}
