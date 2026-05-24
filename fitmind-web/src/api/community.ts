import request from '../utils/request'

export function getFeed(params: any) {
  return request({
    url: '/community/feed',
    method: 'get',
    params
  })
}

export function createPost(data: { content: string }) {
  return request({
    url: '/community/post',
    method: 'post',
    data
  })
}

export function deletePost(postId: number) {
  return request({
    url: `/community/post/${postId}`,
    method: 'delete'
  })
}

export function likePost(postId: number) {
  return request({
    url: `/community/like/${postId}`,
    method: 'post'
  })
}

export function addComment(postId: number, content: string) {
  return request({
    url: `/community/comment/${postId}`,
    method: 'post',
    data: { content }
  })
}

export function getComments(postId: number) {
  return request({
    url: `/community/comments/${postId}`,
    method: 'get'
  })
}

export function followUser(userId: number) {
  return request({
    url: `/community/follow/${userId}`,
    method: 'post'
  })
}

export function unfollowUser(userId: number) {
  return request({
    url: `/community/unfollow/${userId}`,
    method: 'post'
  })
}

export function getLeaderboard(params: { category: string; period: string }) {
  return request({
    url: '/community/leaderboard',
    method: 'get',
    params
  })
}

export function getMyFollowing() {
  return request({
    url: '/community/following',
    method: 'get'
  })
}

export function getTrendingTags() {
  return request({
    url: '/community/trending',
    method: 'get'
  })
}

export function getNetworkStats() {
  return request({
    url: '/community/stats',
    method: 'get'
  })
}
