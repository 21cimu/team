package com.fitmind.module.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fitmind.module.community.entity.CommunityPost;

public interface ICommunityPostService extends IService<CommunityPost> {
    void createPost(Long userId, String content);
    Page<CommunityPost> getFeed(Long currentUserId, int current, int size);
    CommunityPost likePost(Long postId, Long userId);
    void deletePost(Long postId, Long userId);
}
