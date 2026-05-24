<template>
  <div class="community-content">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">训练社区</span>
          <strong class="topbar-page-title">围绕训练过程建立高质量交流。</strong>
          <span class="topbar-page-meta">{{ communityMeta }}</span>
        </div>
        <div class="topbar-page-actions">
          <button class="nd-btn" @click="router.push('/app/leaderboard')">排行榜</button>
          <button class="nd-btn primary" @click="refreshAll" :disabled="loadingFeed">刷新动态</button>
        </div>
      </div>
    </Teleport>

    <section class="community-hero">
      <div class="hero-copy">
        <div class="text-label">[ Community Desk ]</div>
        <div class="hero-index">06</div>
        <h1 class="text-display-md hero-title">训练记录不该只停留在计划页，它也需要被看见、被回应。</h1>
        <p class="hero-description">
          在这里发布训练复盘、饮食执行和恢复状态。页面会优先呈现高互动内容、热门标签和你关注的人，让社区从单纯发帖变成持续有反馈的训练网络。
        </p>
        <div class="hero-actions">
          <button class="nd-btn primary" @click="scrollToComposer">发布一条动态</button>
          <button class="nd-btn" @click="router.push('/app/leaderboard')">查看排行榜</button>
        </div>
      </div>

      <div class="nd-card hero-panel">
        <div class="hero-panel-header">
          <div>
            <div class="text-label">[ 社区脉冲 ]</div>
            <h2>今日概览</h2>
          </div>
          <span class="text-caption">按当前可见数据计算</span>
        </div>

        <div class="hero-stats">
          <article class="hero-stat">
            <span>活跃成员</span>
            <strong>{{ networkStats.active }}</strong>
          </article>
          <article class="hero-stat">
            <span>动态总量</span>
            <strong>{{ networkStats.postsToday }}</strong>
          </article>
          <article class="hero-stat">
            <span>完成训练</span>
            <strong>{{ networkStats.workouts }}</strong>
          </article>
        </div>

        <div class="hero-brief">
          <div v-for="item in communityBulletin" :key="item.label" class="brief-row">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>
    </section>

    <div class="community-grid">
      <div class="feed-column">
        <section ref="composerRef" class="nd-card composer-card">
          <div class="composer-header">
            <div class="author-lockup">
              <div class="avatar-sm">{{ currentUserInitial }}</div>
              <div>
                <div class="text-label">[ Broadcast ]</div>
                <strong class="composer-title">{{ currentUsername }}</strong>
              </div>
            </div>
            <span class="text-caption">建议附上标签，例如 #胸部训练 #减脂进度</span>
          </div>

          <textarea
            v-model="newPostContent"
            class="nd-input composer-input"
            rows="4"
            maxlength="500"
            placeholder="记录今天的训练完成情况、动作感受、恢复状态或饮食执行。"
          />

          <div class="composer-templates">
            <button
              v-for="template in composeTemplates"
              :key="template.label"
              class="template-chip"
              @click="applyTemplate(template.text)"
            >
              {{ template.label }}
            </button>
          </div>

          <div class="composer-footer">
            <div class="text-caption">{{ newPostContent.length }} / 500</div>
            <button
              class="nd-btn primary"
              :disabled="publishing || !newPostContent.trim() || newPostContent.length > 500"
              @click="handlePublish"
            >
              {{ publishing ? '发布中...' : '发布动态' }}
            </button>
          </div>
        </section>

        <section class="nd-card feed-toolbar-card">
          <div class="toolbar-main">
            <div class="text-label">[ Feed Controls ]</div>
            <div class="feed-mode-group">
              <button
                v-for="mode in feedModes"
                :key="mode.key"
                class="mode-chip"
                :class="{ active: activeFeedMode === mode.key }"
                @click="activeFeedMode = mode.key"
              >
                {{ mode.label }}
              </button>
            </div>
          </div>

          <div class="toolbar-search">
            <input
              v-model.trim="searchQuery"
              class="nd-input search-input"
              placeholder="搜索用户、内容或标签"
            />
            <button class="nd-btn" @click="refreshAll" :disabled="loadingFeed">刷新</button>
          </div>
        </section>

        <div v-if="loadingFeed" class="loading-state">
          <span class="status-dot blink"></span>
          <span class="text-caption ml-sm">正在整理社区动态...</span>
        </div>

        <transition-group name="post-list" tag="div" class="feed-stack">
          <article
            v-for="post in visibleFeed"
            :key="post.id"
            class="nd-card post-card"
            :class="{ featured: featuredPost?.id === post.id }"
          >
            <div class="post-header">
              <div class="author-lockup">
                <div class="avatar-sm">{{ getInitial(post.username) }}</div>
                <div>
                  <strong class="author-name">{{ post.username }}</strong>
                  <div class="post-meta-line">
                    <span>{{ formatTime(post.createTime) }}</span>
                    <span v-if="post.isFollowing">已关注</span>
                    <span v-if="post.commentCount > 0">{{ post.commentCount }} 条讨论</span>
                  </div>
                </div>
              </div>

              <div class="post-header-actions">
                <span v-if="isHotPost(post)" class="insight-badge">高热</span>
                <button
                  v-if="post.canDelete"
                  class="utility-btn danger"
                  @click="handleDelete(post)"
                >
                  删除
                </button>
                <button
                  v-else-if="post.canFollow"
                  class="utility-btn"
                  :class="{ following: post.isFollowing }"
                  @click="handleFollow(post)"
                >
                  {{ post.isFollowing ? '已关注' : '关注' }}
                </button>
              </div>
            </div>

            <div v-if="post.tags.length" class="tag-strip">
              <span v-for="tag in post.tags" :key="tag" class="tag-pill">#{{ tag }}</span>
            </div>

            <p class="post-content">{{ post.content }}</p>

            <div v-if="post.commentsLoaded && post.comments.length" class="preview-thread">
              <div
                v-for="comment in post.comments.slice(0, 2)"
                :key="comment.id || `${comment.username}-${comment.createTime || comment.content}`"
                class="preview-row"
              >
                <span class="preview-author">{{ comment.username }}</span>
                <span class="preview-copy">{{ comment.content }}</span>
              </div>
            </div>

            <div class="post-stats">
              <div class="stat-chip">
                <span>互动分</span>
                <strong>{{ getEngagementScore(post) }}</strong>
              </div>
              <div class="stat-chip">
                <span>点赞</span>
                <strong>{{ post.likes }}</strong>
              </div>
              <div class="stat-chip">
                <span>讨论</span>
                <strong>{{ post.commentCount }}</strong>
              </div>
            </div>

            <div class="post-footer">
              <button
                class="action-btn"
                :class="{ liked: post.liked }"
                :disabled="post.liking"
                @click="handleLike(post)"
              >
                <span>{{ post.liked ? '已点赞' : '点赞' }}</span>
                <strong>{{ post.likes }}</strong>
              </button>
              <button class="action-btn" @click="toggleComments(post)">
                <span>{{ post.showComments ? '收起讨论' : '展开讨论' }}</span>
                <strong>{{ post.commentCount }}</strong>
              </button>
              <button class="action-btn" @click="handleShare(post)">
                <span>复制内容</span>
              </button>
            </div>

            <div v-if="post.showComments" class="comments-section">
              <div v-if="post.commentsLoading" class="comment-loading text-caption">正在加载评论...</div>

              <div v-else-if="post.comments.length" class="comment-list">
                <div
                  v-for="comment in post.comments"
                  :key="comment.id || `${comment.username}-${comment.createTime || comment.content}`"
                  class="comment-row"
                >
                  <div class="avatar-xs">{{ getInitial(comment.username) }}</div>
                  <div class="comment-body">
                    <div class="comment-head">
                      <strong>{{ comment.username }}</strong>
                      <span>{{ formatTime(comment.createTime) }}</span>
                    </div>
                    <p>{{ comment.content }}</p>
                  </div>
                </div>
              </div>

              <div v-else class="comment-empty text-caption">还没有讨论，补充一句你的观察。</div>

              <div class="comment-input-row">
                <div class="avatar-xs">{{ currentUserInitial }}</div>
                <input
                  v-model="post.newComment"
                  class="comment-input"
                  placeholder="写下你的反馈"
                  @keyup.enter="handleComment(post)"
                />
                <button class="comment-send" @click="handleComment(post)" :disabled="post.commentSubmitting">
                  {{ post.commentSubmitting ? '发送中' : '发送' }}
                </button>
              </div>
            </div>
          </article>
        </transition-group>

        <div v-if="!loadingFeed && visibleFeed.length === 0" class="nd-card empty-state">
          <div class="text-label">{{ emptyState.kicker }}</div>
          <h3>{{ emptyState.title }}</h3>
          <p>{{ emptyState.description }}</p>
        </div>

        <div v-if="hasMore" class="load-more">
          <button class="nd-btn" @click="loadMore" :disabled="loadingMore">
            {{ loadingMore ? '加载中...' : '加载更多' }}
          </button>
        </div>
      </div>

      <aside class="sidebar-column">
        <section class="nd-card sidebar-card spotlight-card">
          <div class="sidebar-card-header">
            <div>
              <div class="text-label">[ Featured Post ]</div>
              <h2>今日高热动态</h2>
            </div>
            <button class="mini-link" @click="router.push('/app/leaderboard')">去看榜单</button>
          </div>

          <div v-if="featuredPost" class="spotlight-post">
            <strong>{{ featuredPost.username }}</strong>
            <p>{{ getExcerpt(featuredPost.content, 92) }}</p>
            <div class="spotlight-metrics">
              <span>{{ featuredPost.likes }} 赞</span>
              <span>{{ featuredPost.commentCount }} 评</span>
              <span>{{ getEngagementScore(featuredPost) }} 分</span>
            </div>
          </div>
          <div v-else class="text-caption">当前没有可展示的动态。</div>
        </section>

        <section class="nd-card sidebar-card">
          <div class="sidebar-card-header">
            <div>
              <div class="text-label">[ Trending Tags ]</div>
              <h2>热门标签</h2>
            </div>
          </div>
          <div v-if="trendingTags.length" class="tag-cloud">
            <button
              v-for="tag in trendingTags"
              :key="tag.name"
              class="trend-chip"
              @click="searchQuery = String(tag.name)"
            >
              <span>#{{ tag.name }}</span>
              <strong>{{ tag.count }}</strong>
            </button>
          </div>
          <div v-else class="text-caption">近期暂无明显聚合话题。</div>
        </section>

        <section class="nd-card sidebar-card">
          <div class="sidebar-card-header">
            <div>
              <div class="text-label">[ Following ]</div>
              <h2>我的关注</h2>
            </div>
          </div>
          <div v-if="followingList.length" class="following-list">
            <div v-for="user in followingList" :key="user.id" class="following-row">
              <div class="avatar-xs">{{ getInitial(user.username) }}</div>
              <span>{{ user.username }}</span>
            </div>
          </div>
          <div v-else class="text-caption">你还没有关注任何成员。</div>
        </section>

        <section class="nd-card sidebar-card">
          <div class="sidebar-card-header">
            <div>
              <div class="text-label">[ Briefing ]</div>
              <h2>社区速览</h2>
            </div>
          </div>
          <div class="brief-list">
            <div v-for="item in communityInsights" :key="item.title" class="brief-list-row">
              <span>{{ item.title }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  addComment,
  createPost,
  deletePost,
  followUser,
  getComments,
  getFeed,
  getMyFollowing,
  getNetworkStats,
  getTrendingTags,
  likePost,
  unfollowUser
} from '../api/community'
import { useUserStore } from '../stores/user'

interface CommunityCommentItem {
  id?: number
  username: string
  content: string
  createTime?: string
}

interface CommunityPostItem {
  id: number
  userId: number
  username: string
  content: string
  likes: number
  liked: boolean
  createTime: string
  tags: string[]
  isFollowing: boolean
  canFollow: boolean
  canDelete: boolean
  showComments: boolean
  commentsLoaded: boolean
  commentsLoading: boolean
  commentSubmitting: boolean
  comments: CommunityCommentItem[]
  commentCount: number
  newComment: string
  liking: boolean
}

type FeedMode = 'latest' | 'hot' | 'following'

const router = useRouter()
const userStore = useUserStore()
const composerRef = ref<HTMLElement | null>(null)

const FEED_PAGE_SIZE = 8

const currentUserId = computed(() => userStore.userInfo?.id ?? null)
const currentUsername = computed(() => userStore.userInfo?.username || '你的账号')
const currentUserInitial = computed(() => getInitial(currentUsername.value))

const publishing = ref(false)
const loadingFeed = ref(false)
const loadingMore = ref(false)
const newPostContent = ref('')
const searchQuery = ref('')
const activeFeedMode = ref<FeedMode>('latest')
const currentPage = ref(1)
const hasMore = ref(true)

const networkStats = ref({
  active: 0,
  postsToday: 0,
  workouts: 0
})
const trendingTags = ref<Array<{ name: string; count: number }>>([])
const followingList = ref<Array<{ id: number; username: string }>>([])
const feedList = ref<CommunityPostItem[]>([])

const feedModes = [
  { key: 'latest', label: '最新' },
  { key: 'hot', label: '高热' },
  { key: 'following', label: '仅看关注' }
] as const

const composeTemplates = [
  { label: '训练复盘', text: '#训练复盘 今天完成了 ___，动作感觉 ___，下一次准备 ___。' },
  { label: '饮食执行', text: '#饮食执行 今天最稳的一餐是 ___，还需要调整 ___。' },
  { label: '恢复记录', text: '#恢复状态 睡眠 ___ 小时，身体反馈 ___。' },
  { label: '打卡提醒', text: '#今日打卡 已完成今日训练，继续保持节奏。' }
]

const followingIds = computed(() => new Set(followingList.value.map((user) => user.id)))

const communityMeta = computed(() => {
  const visibleCount = visibleFeed.value.length
  return `当前可见 ${visibleCount} 条动态 · ${followingList.value.length} 个关注关系`
})

const visibleFeed = computed(() => {
  let list = [...feedList.value]

  if (activeFeedMode.value === 'following') {
    list = list.filter((post) => post.isFollowing)
  }

  if (searchQuery.value) {
    const keyword = searchQuery.value.toLowerCase()
    list = list.filter((post) => {
      const pool = [post.username, post.content, post.tags.join(' ')]
      return pool.some((item) => String(item).toLowerCase().includes(keyword))
    })
  }

  if (activeFeedMode.value === 'hot') {
    list.sort((left, right) => getEngagementScore(right) - getEngagementScore(left))
  } else {
    list.sort((left, right) => new Date(right.createTime).getTime() - new Date(left.createTime).getTime())
  }

  return list
})

const featuredPost = computed(() => {
  const list = [...feedList.value]
  list.sort((left, right) => getEngagementScore(right) - getEngagementScore(left))
  return list[0] || null
})

const communityBulletin = computed(() => {
  const authors = new Set(feedList.value.map((post) => post.username)).size
  const discussions = feedList.value.reduce((sum, post) => sum + post.commentCount, 0)
  const likes = feedList.value.reduce((sum, post) => sum + post.likes, 0)

  return [
    { label: '发声成员', value: `${authors || 0} 人` },
    { label: '讨论总数', value: `${discussions} 条` },
    { label: '点赞总数', value: `${likes} 次` }
  ]
})

const communityInsights = computed(() => {
  const hottestTag = trendingTags.value[0]?.name ? `#${trendingTags.value[0].name}` : '暂无'
  const avgLikes = feedList.value.length
    ? (feedList.value.reduce((sum, post) => sum + post.likes, 0) / feedList.value.length).toFixed(1)
    : '0'
  const followedVisible = visibleFeed.value.filter((post) => post.isFollowing).length

  return [
    { title: '当前热门', value: hottestTag },
    { title: '平均点赞', value: `${avgLikes} / 帖` },
    { title: '关注动态', value: `${followedVisible} 条` }
  ]
})

const emptyState = computed(() => {
  if (searchQuery.value) {
    return {
      kicker: '[ 未匹配结果 ]',
      title: '没有找到相关动态',
      description: '换一个关键词，或者直接发布一条新的训练记录。'
    }
  }

  if (activeFeedMode.value === 'following') {
    return {
      kicker: '[ 关注为空 ]',
      title: '当前没有关注成员的动态',
      description: '去主列表关注几位训练节奏接近的人，后续这里会只显示他们的内容。'
    }
  }

  return {
    kicker: '[ 暂无动态 ]',
    title: '社区还没有新内容',
    description: '成为第一个发帖的人，把今天的训练过程留在这里。'
  }
})

function getInitial(value?: string) {
  return (value || 'U').trim().charAt(0).toUpperCase()
}

function getExcerpt(content: string, max = 88) {
  if (content.length <= max) return content
  return `${content.slice(0, max).trim()}...`
}

function extractTags(content: string) {
  const matches = content.match(/#([A-Za-z0-9_\-\u4e00-\u9fa5]+)/g) || []
  return Array.from(new Set(matches.map((tag) => tag.replace('#', '')))).slice(0, 4)
}

function createDecoratedPost(post: any): CommunityPostItem {
  return {
    id: Number(post.id),
    userId: Number(post.userId),
    username: post.username || 'UNKNOWN',
    content: post.content || '',
    likes: Number(post.likes || 0),
    liked: Boolean(post.liked),
    createTime: post.createTime || new Date().toISOString(),
    tags: extractTags(post.content || ''),
    isFollowing: followingIds.value.has(Number(post.userId)),
    canFollow: currentUserId.value !== null && Number(post.userId) !== currentUserId.value,
    canDelete: currentUserId.value !== null && Number(post.userId) === currentUserId.value,
    showComments: false,
    commentsLoaded: false,
    commentsLoading: false,
    commentSubmitting: false,
    comments: [],
    commentCount: Number(post.commentCount || 0),
    newComment: '',
    liking: false
  }
}

function syncFeedRelationshipState() {
  feedList.value = feedList.value.map((post) => ({
    ...post,
    isFollowing: followingIds.value.has(post.userId),
    canFollow: currentUserId.value !== null && post.userId !== currentUserId.value,
    canDelete: currentUserId.value !== null && post.userId === currentUserId.value
  }))
}

function formatTime(time?: string) {
  if (!time) return '刚刚'
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return '刚刚'

  const diff = Date.now() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} 天前`
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' })
}

function getEngagementScore(post: CommunityPostItem) {
  const freshness = Math.max(0, 24 - Math.floor((Date.now() - new Date(post.createTime).getTime()) / 3600000))
  return post.likes * 4 + post.commentCount * 3 + freshness
}

function isHotPost(post: CommunityPostItem) {
  return getEngagementScore(post) >= 16
}

function applyTemplate(text: string) {
  if (!newPostContent.value.trim()) {
    newPostContent.value = text
    return
  }

  newPostContent.value = `${newPostContent.value.trim()}\n${text}`
}

function scrollToComposer() {
  composerRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function fetchSidebarData() {
  const [statsRes, tagsRes, followingRes] = await Promise.allSettled([
    getNetworkStats(),
    getTrendingTags(),
    getMyFollowing()
  ])

  if (statsRes.status === 'fulfilled' && statsRes.value) {
    networkStats.value = {
      active: Number((statsRes.value as any).active || 0),
      postsToday: Number((statsRes.value as any).postsToday || 0),
      workouts: Number((statsRes.value as any).workouts || 0)
    }
  }

  if (tagsRes.status === 'fulfilled' && Array.isArray(tagsRes.value)) {
    trendingTags.value = (tagsRes.value as any[]).map((tag) => ({
      name: String(tag.name || '').trim(),
      count: Number(tag.count || 0)
    }))
  }

  if (followingRes.status === 'fulfilled' && Array.isArray(followingRes.value)) {
    followingList.value = (followingRes.value as any[]).map((user) => ({
      id: Number(user.id),
      username: user.username || 'USER'
    }))
    syncFeedRelationshipState()
  }
}

async function fetchCommentsForPost(post: CommunityPostItem, silent = false) {
  if (post.commentsLoading) return
  if (post.commentsLoaded && silent) return

  post.commentsLoading = true
  try {
    const comments = await getComments(post.id)
    post.comments = Array.isArray(comments)
      ? comments.map((comment: any) => ({
          id: comment.id ? Number(comment.id) : undefined,
          username: comment.username || 'USER',
          content: comment.content || '',
          createTime: comment.createTime
        }))
      : []
    post.commentCount = post.comments.length
    post.commentsLoaded = true
  } catch (error) {
    if (!silent) {
      ElMessage.error('评论加载失败')
    }
  } finally {
    post.commentsLoading = false
  }
}

async function hydrateCommentSnapshots(posts: CommunityPostItem[]) {
  await Promise.allSettled(posts.slice(0, 6).map((post) => fetchCommentsForPost(post, true)))
}

async function fetchFeed(page = 1) {
  if (page === 1) {
    loadingFeed.value = true
  } else {
    loadingMore.value = true
  }

  try {
    const response: any = await getFeed({ current: page, size: FEED_PAGE_SIZE })
    const records = Array.isArray(response?.records)
      ? response.records.map((post: any) => createDecoratedPost(post))
      : []

    if (page === 1) {
      feedList.value = records
    } else {
      feedList.value.push(...records)
    }

    hasMore.value = records.length >= FEED_PAGE_SIZE
    syncFeedRelationshipState()
    hydrateCommentSnapshots(records)
  } finally {
    loadingFeed.value = false
    loadingMore.value = false
  }
}

async function refreshAll() {
  currentPage.value = 1
  await Promise.all([fetchFeed(1), fetchSidebarData()])
}

async function loadMore() {
  currentPage.value += 1
  await fetchFeed(currentPage.value)
}

async function handlePublish() {
  const content = newPostContent.value.trim()
  if (!content) return

  publishing.value = true
  try {
    await createPost({ content })
    newPostContent.value = ''
    ElMessage.success('动态已发布')
    await refreshAll()
  } finally {
    publishing.value = false
  }
}

async function handleDelete(post: CommunityPostItem) {
  if (!window.confirm('确定删除这条动态吗？')) return

  try {
    await deletePost(post.id)
    feedList.value = feedList.value.filter((item) => item.id !== post.id)
    ElMessage.success('动态已删除')
    await fetchSidebarData()
  } catch (error) {
    // handled by interceptor
  }
}

async function handleLike(post: CommunityPostItem) {
  if (post.liking) return

  post.liking = true
  try {
    const result: any = await likePost(post.id)
    post.liked = Boolean(result?.liked)
    post.likes = typeof result?.likes === 'number'
      ? result.likes
      : Math.max(0, post.likes + (post.liked ? 1 : -1))
  } finally {
    post.liking = false
  }
}

async function toggleComments(post: CommunityPostItem) {
  post.showComments = !post.showComments
  if (post.showComments && !post.commentsLoaded) {
    await fetchCommentsForPost(post)
  }
}

async function handleComment(post: CommunityPostItem) {
  const content = post.newComment.trim()
  if (!content || post.commentSubmitting) return

  post.commentSubmitting = true
  try {
    await addComment(post.id, content)
    post.comments.push({
      username: currentUsername.value,
      content,
      createTime: new Date().toISOString()
    })
    post.commentCount = post.comments.length
    post.commentsLoaded = true
    post.newComment = ''
    ElMessage.success('评论已发送')
  } finally {
    post.commentSubmitting = false
  }
}

async function handleFollow(post: CommunityPostItem) {
  try {
    if (post.isFollowing) {
      await unfollowUser(post.userId)
      followingList.value = followingList.value.filter((user) => user.id !== post.userId)
      ElMessage.success(`已取消关注 ${post.username}`)
    } else {
      await followUser(post.userId)
      if (!followingList.value.some((user) => user.id === post.userId)) {
        followingList.value.push({ id: post.userId, username: post.username })
      }
      ElMessage.success(`已关注 ${post.username}`)
    }

    syncFeedRelationshipState()
  } catch (error) {
    // handled by interceptor
  }
}

async function handleShare(post: CommunityPostItem) {
  const payload = `${post.username}：${post.content}`

  try {
    await navigator.clipboard.writeText(payload)
    ElMessage.success('内容已复制')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

onMounted(async () => {
  await refreshAll()
})
</script>

<style scoped>
.community-content {
  width: 100%;
}

.community-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.9fr);
  gap: 24px;
  padding-top: 22px;
  border-top: 1px solid rgba(244, 238, 232, 0.12);
}

.hero-copy {
  position: relative;
  padding-top: 58px;
  color: var(--text-main);
}

.hero-index {
  position: absolute;
  top: 0;
  left: 0;
  color: rgba(17, 17, 17, 0.12);
  font-family: var(--font-heading);
  font-size: clamp(4.6rem, 8vw, 7rem);
  line-height: 0.84;
}

.hero-title {
  max-width: 11ch;
  margin-top: 18px;
}

.hero-description {
  max-width: 62ch;
  margin: 20px 0 0;
  color: var(--text-secondary);
  line-height: 1.84;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

.hero-panel {
  padding: 26px;
  color: var(--text-main);
}

.hero-panel-header,
.sidebar-card-header,
.composer-header,
.toolbar-main,
.toolbar-search,
.post-header,
.post-footer,
.comment-head,
.comment-input-row,
.brief-row,
.brief-list-row,
.following-row,
.spotlight-metrics,
.composer-footer {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
}

.hero-panel-header {
  align-items: flex-start;
}

.hero-panel-header h2,
.sidebar-card-header h2 {
  margin-top: 8px;
  font-family: var(--font-heading);
  font-size: 1.4rem;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  margin-top: 22px;
  border: 1px solid rgba(17, 17, 17, 0.12);
}

.hero-stat {
  min-height: 120px;
  padding: 18px;
  border-right: 1px solid rgba(17, 17, 17, 0.12);
  background: rgba(255, 255, 255, 0.2);
}

.hero-stat:last-child {
  border-right: none;
}

.hero-stat span {
  display: block;
  color: var(--text-secondary);
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.hero-stat strong {
  display: block;
  margin-top: 12px;
  font-family: var(--font-heading);
  font-size: 2rem;
}

.hero-brief,
.brief-list,
.following-list {
  display: grid;
  gap: 10px;
  margin-top: 20px;
}

.brief-row,
.brief-list-row,
.following-row {
  padding-top: 12px;
  border-top: 1px solid rgba(17, 17, 17, 0.08);
  color: var(--text-main);
}

.community-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) 320px;
  gap: 24px;
  margin-top: 28px;
}

.feed-column,
.sidebar-column {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.composer-card,
.feed-toolbar-card,
.post-card,
.sidebar-card,
.empty-state {
  padding: 28px;
  color: var(--text-main);
}

.author-lockup {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.composer-title,
.author-name {
  font-family: var(--font-heading);
  font-size: 1.05rem;
}

.composer-input {
  min-height: 140px;
  margin-top: 18px;
  resize: vertical;
}

.composer-templates {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.template-chip,
.mode-chip,
.trend-chip,
.utility-btn,
.comment-send,
.mini-link,
.tag-pill,
.insight-badge {
  border: 1px solid rgba(17, 17, 17, 0.14);
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.76rem;
  letter-spacing: 0.08em;
  padding: 10px 12px;
  text-transform: uppercase;
}

.template-chip:hover,
.mode-chip:hover,
.trend-chip:hover,
.utility-btn:hover,
.mini-link:hover {
  border-color: rgba(17, 17, 17, 0.26);
  color: var(--text-main);
}

.feed-mode-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.mode-chip.active,
.utility-btn.following,
.comment-send,
.insight-badge {
  background: #17181f;
  border-color: #17181f;
  color: var(--text-inverse);
}

.toolbar-search {
  margin-top: 16px;
}

.search-input {
  flex: 1;
}

.loading-state {
  display: flex;
  align-items: center;
  padding: 10px 0 0;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--warning);
}

.status-dot.blink {
  animation: blink 1s infinite;
}

@keyframes blink {
  50% {
    opacity: 0.2;
  }
}

.feed-stack {
  display: grid;
  gap: 18px;
}

.post-card {
  border-left: 4px solid rgba(183, 154, 114, 0.46) !important;
}

.post-card.featured {
  border-left-color: rgba(113, 136, 113, 0.62) !important;
}

.post-meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.78rem;
}

.post-header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.utility-btn.danger:hover {
  border-color: rgba(185, 116, 105, 0.42);
  color: var(--error);
}

.tag-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 18px;
}

.tag-pill {
  padding: 7px 10px;
  font-size: 0.72rem;
}

.post-content {
  margin: 18px 0 0;
  color: var(--text-main);
  line-height: 1.82;
  white-space: pre-wrap;
}

.preview-thread,
.post-stats,
.comments-section {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(17, 17, 17, 0.08);
}

.preview-thread {
  display: grid;
  gap: 10px;
}

.preview-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  color: var(--text-secondary);
}

.preview-author {
  font-weight: 700;
  color: var(--text-main);
}

.preview-copy {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
}

.stat-chip {
  padding-right: 12px;
  border-right: 1px solid rgba(17, 17, 17, 0.08);
}

.stat-chip:last-child {
  border-right: none;
  padding-right: 0;
}

.stat-chip span {
  display: block;
  color: var(--text-secondary);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.stat-chip strong {
  display: block;
  margin-top: 8px;
  font-family: var(--font-heading);
  font-size: 1.25rem;
}

.post-footer {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(17, 17, 17, 0.08);
  flex-wrap: wrap;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--text-secondary);
}

.action-btn strong {
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 1rem;
}

.action-btn.liked strong,
.action-btn.liked span {
  color: var(--error);
}

.comment-list {
  display: grid;
  gap: 12px;
}

.comment-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.comment-body {
  flex: 1;
  padding: 12px;
  border: 1px solid rgba(17, 17, 17, 0.08);
  background: rgba(255, 255, 255, 0.22);
}

.comment-head {
  align-items: baseline;
}

.comment-head strong {
  font-size: 0.9rem;
}

.comment-head span {
  color: var(--text-secondary);
  font-size: 0.76rem;
}

.comment-body p {
  margin: 8px 0 0;
  line-height: 1.7;
}

.comment-empty,
.comment-loading {
  color: var(--text-secondary);
}

.comment-input-row {
  margin-top: 14px;
}

.comment-input {
  flex: 1;
}

.comment-send {
  white-space: nowrap;
}

.avatar-sm,
.avatar-xs {
  display: grid;
  place-items: center;
  font-family: var(--font-heading);
  font-weight: 800;
  color: #fffaf4;
  flex-shrink: 0;
  background: linear-gradient(135deg, #c2a978, #7ea889);
}

.avatar-sm {
  width: 38px;
  height: 38px;
  font-size: 0.98rem;
}

.avatar-xs {
  width: 28px;
  height: 28px;
  font-size: 0.78rem;
  background: linear-gradient(135deg, #8ea7a1, #c2a978);
}

.spotlight-post p {
  margin: 12px 0 0;
  color: var(--text-secondary);
  line-height: 1.74;
}

.spotlight-metrics,
.following-row {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid rgba(17, 17, 17, 0.08);
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.trend-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.trend-chip strong {
  color: var(--text-main);
  font-family: var(--font-heading);
}

.mini-link {
  padding: 0;
  border: none;
  text-transform: none;
  letter-spacing: 0.06em;
}

.empty-state {
  text-align: center;
}

.empty-state h3 {
  margin-top: 16px;
  font-family: var(--font-heading);
  font-size: 1.5rem;
}

.empty-state p {
  max-width: 42ch;
  margin: 12px auto 0;
  color: var(--text-secondary);
}

.load-more {
  display: flex;
  justify-content: center;
}

.post-list-enter-active,
.post-list-leave-active {
  transition: all 0.26s ease;
}

.post-list-enter-from,
.post-list-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

@media (max-width: 1200px) {
  .community-hero,
  .community-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 820px) {
  .hero-stats,
  .post-stats {
    grid-template-columns: 1fr;
  }

  .hero-stat,
  .stat-chip {
    border-right: none;
    border-bottom: 1px solid rgba(17, 17, 17, 0.12);
  }

  .hero-stat:last-child,
  .stat-chip:last-child {
    border-bottom: none;
  }

  .toolbar-search,
  .composer-footer,
  .post-header,
  .post-footer,
  .comment-input-row {
    flex-direction: column;
    align-items: stretch;
  }

  .post-header-actions {
    justify-content: flex-start;
  }

  .preview-row {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .hero-actions .nd-btn,
  .toolbar-search .nd-btn,
  .comment-send {
    width: 100%;
  }
}
</style>
