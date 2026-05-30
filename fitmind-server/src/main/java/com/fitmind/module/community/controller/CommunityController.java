package com.fitmind.module.community.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fitmind.common.api.Result;
import com.fitmind.common.security.CurrentUserProvider;
import com.fitmind.module.community.entity.CommunityComment;
import com.fitmind.module.community.entity.CommunityPost;
import com.fitmind.module.community.service.CommunityInteractionService;
import com.fitmind.module.community.service.CommunityQueryService;
import com.fitmind.module.community.service.ICommunityPostService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CurrentUserProvider currentUserProvider;
    private final ICommunityPostService communityPostService;
    private final CommunityInteractionService communityInteractionService;
    private final CommunityQueryService communityQueryService;

    @PostMapping("/post")
    public Result<String> createPost(@RequestBody PostRequest request) {
        try {
            communityPostService.createPost(currentUserProvider.getCurrentUserId(), request.getContent());
            return Result.success("动态发布成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/post/{postId}")
    public Result<String> deletePost(@PathVariable Long postId) {
        try {
            communityPostService.deletePost(postId, currentUserProvider.getCurrentUserId());
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
            Page<CommunityPost> feed = communityPostService.getFeed(currentUserProvider.getCurrentUserId(), current, size);
            return Result.success(feed);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/like/{postId}")
    public Result<CommunityPost> likePost(@PathVariable Long postId) {
        try {
            CommunityPost post = communityPostService.likePost(postId, currentUserProvider.getCurrentUserId());
            return Result.success(post);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/comment/{postId}")
    public Result<String> addComment(@PathVariable Long postId, @RequestBody CommentRequest request) {
        try {
            communityInteractionService.addComment(postId, currentUserProvider.getCurrentUserId(), request.getContent());
            return Result.success("评论添加成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/comments/{postId}")
    public Result<List<CommunityComment>> getComments(@PathVariable Long postId) {
        return Result.success(communityQueryService.getComments(postId));
    }

    @PostMapping("/follow/{targetUserId}")
    public Result<String> followUser(@PathVariable Long targetUserId) {
        try {
            communityInteractionService.followUser(currentUserProvider.getCurrentUserId(), targetUserId);
            return Result.success("关注成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/unfollow/{targetUserId}")
    public Result<String> unfollowUser(@PathVariable Long targetUserId) {
        try {
            communityInteractionService.unfollowUser(currentUserProvider.getCurrentUserId(), targetUserId);
            return Result.success("已取消关注");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/following")
    public Result<List<Map<String, Object>>> getMyFollowing() {
        try {
            return Result.success(communityQueryService.getFollowing(currentUserProvider.getCurrentUserId()));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/trending")
    public Result<List<Map<String, Object>>> getTrendingTags() {
        try {
            return Result.success(communityQueryService.getTrendingTags());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getNetworkStats() {
        try {
            return Result.success(communityQueryService.getNetworkStats());
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/leaderboard")
    public Result<List<Map<String, Object>>> getLeaderboard(
            @RequestParam(defaultValue = "training") String category,
            @RequestParam(defaultValue = "weekly") String period) {
        try {
            return Result.success(communityQueryService.getLeaderboard(category, period));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
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
