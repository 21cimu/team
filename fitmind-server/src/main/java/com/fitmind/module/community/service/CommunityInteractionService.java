package com.fitmind.module.community.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fitmind.module.community.entity.CommunityComment;
import com.fitmind.module.community.entity.UserFollow;
import com.fitmind.module.community.mapper.CommunityCommentMapper;
import com.fitmind.module.community.mapper.UserFollowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CommunityInteractionService {

    private final CommunityCommentMapper communityCommentMapper;
    private final UserFollowMapper userFollowMapper;

    public void addComment(Long postId, Long userId, String content) {
        CommunityComment comment = new CommunityComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setCreateTime(LocalDateTime.now());
        communityCommentMapper.insert(comment);
    }

    public void followUser(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("不能关注自己");
        }
        boolean exists = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFollowingId, targetUserId)) > 0;
        if (exists) {
            return;
        }

        UserFollow follow = new UserFollow();
        follow.setFollowerId(currentUserId);
        follow.setFollowingId(targetUserId);
        follow.setCreateTime(LocalDateTime.now());
        userFollowMapper.insert(follow);
    }

    public void unfollowUser(Long currentUserId, Long targetUserId) {
        userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFollowingId, targetUserId));
    }
}
