<template>
  <div class="platform-shell">
    <div class="ambient-orb orb-one"></div>
    <div class="ambient-orb orb-two"></div>

    <div class="platform-frame">
      <aside class="editorial-rail desktop-rail">
        <button type="button" class="brand-lockup rail-brand" @click="router.push('/')">
          <span class="brand-mark">FM</span>
          <span class="brand-copy">
            <strong>FitMind</strong>
            <small>Editorial Training OS</small>
          </span>
        </button>

        <div class="rail-story">
          <span class="rail-index">{{ currentSectionIndex }}</span>
          <div class="text-label">[ {{ currentView.section }} ]</div>
          <h1 class="rail-title">{{ currentView.title }}</h1>
          <p class="rail-copy">{{ currentView.description }}</p>
        </div>

        <div class="rail-ledger">
          <div class="rail-ledger-card">
            <span>Status</span>
            <strong>{{ profileStatusLabel }}</strong>
          </div>
          <div class="rail-ledger-card">
            <span>Alerts</span>
            <strong>{{ unreadCount }}</strong>
          </div>
        </div>

        <nav class="rail-nav">
          <section v-for="section in visibleNavigationSections" :key="section.label" class="rail-group">
            <div class="rail-group-label">{{ section.label }}</div>
            <button
              v-for="item in section.items"
              :key="item.to"
              type="button"
              class="rail-link nav-button"
              :class="{ 'router-link-active': isActiveRoute(item.to) }"
              @click="navigateTo(item.to)"
            >
              <span class="rail-link-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" v-html="getUiIcon(item.icon)"></svg>
              </span>
              <span class="rail-link-copy">
                <strong>{{ item.label }}</strong>
                <small>{{ item.description }}</small>
              </span>
            </button>
          </section>
        </nav>
      </aside>

      <section class="editorial-stage">
        <header class="stage-header">
          <div class="stage-header-main">
            <button type="button" class="brand-lockup mobile-brand" @click="router.push('/')">
              <span class="brand-mark">FM</span>
              <span class="brand-copy">
                <strong>FitMind</strong>
                <small>{{ currentView.title }}</small>
              </span>
            </button>

            <div class="topbar-actions">
              <button type="button" class="action-btn mobile-only" @click="mobileNavOpen = !mobileNavOpen">
                {{ mobileNavOpen ? 'Close' : 'Menu' }}
              </button>
              <button type="button" class="action-btn" @click="toggleNotificationPanel">
                Alerts
                <span v-if="unreadCount > 0" class="count-badge">{{ unreadCount > 9 ? '9+' : unreadCount }}</span>
              </button>
              <button type="button" class="profile-pill" @click="router.push('/app/profile')">
                <span class="profile-initial">{{ userInitial }}</span>
                <span class="profile-copy">
                  <strong>{{ userStore.userInfo?.username || 'Guest' }}</strong>
                  <small>{{ currentView.section }}</small>
                </span>
              </button>
              <button type="button" class="action-btn subtle-btn" @click="handleLogout">Logout</button>
            </div>
          </div>

          <div class="stage-header-meta">
            <div class="page-caption">
              <span class="page-caption-label">{{ currentView.section }}</span>
              <strong>{{ currentView.title }}</strong>
              <small v-if="currentTopbarContext">{{ currentTopbarContext.label }} · {{ currentTopbarContext.value }}</small>
              <small v-else>{{ currentView.description }}</small>
            </div>

            <div class="topbar-page-portal">
              <div id="topbar-page-header"></div>
            </div>
          </div>
        </header>
      </section>
    </div>

    <header class="topbar">
      <div class="topbar-main">
        <div class="topbar-intro">
          <button type="button" class="brand-lockup" @click="router.push('/')">
            <span class="brand-mark">FM</span>
            <span class="brand-copy">
              <strong>FitMind</strong>
              <small>{{ currentView.title }}</small>
            </span>
          </button>
        </div>

        <nav class="top-nav desktop-nav">
          <button
            v-for="item in primaryNavItems"
            :key="item.to"
            type="button"
            class="top-nav-link nav-button"
            :class="{ 'router-link-active': isActiveRoute(item.to) }"
            @click="navigateTo(item.to)"
          >
            <span class="top-nav-code" aria-hidden="true">
              <svg viewBox="0 0 24 24" v-html="getUiIcon(item.icon)"></svg>
            </span>
            <span class="top-nav-label">{{ item.label }}</span>
          </button>

          <div
            v-if="overflowNavItems.length > 0"
            ref="moreWrapRef"
            class="nav-more-wrap"
            @mouseenter="openMoreMenu"
            @mouseleave="closeMoreMenu"
          >
            <button type="button" class="top-nav-link more-trigger" :class="{ 'router-link-active': showMoreMenu }">
              <span class="top-nav-code" aria-hidden="true">
                <svg viewBox="0 0 24 24" v-html="getUiIcon('more')"></svg>
              </span>
              <span class="top-nav-label">更多</span>
            </button>
          </div>

          <!-- 更多菜单 Teleport 到 body -->
          <Teleport to="body">
            <Transition name="more-menu">
              <div
                v-if="showMoreMenu"
                class="floating-menu-teleport"
                :style="moreMenuStyle"
                @mouseenter="keepMoreMenu"
                @mouseleave="closeMoreMenu"
              >
                <div v-for="section in visibleNavigationSections" :key="section.label" class="floating-group">
                  <div class="floating-group-label">{{ section.label }}</div>
                  <button
                    v-for="item in section.items.filter((entry) => overflowNavLookup.has(entry.to))"
                    :key="item.to"
                    type="button"
                    class="floating-link nav-button"
                    :class="{ 'router-link-active': isActiveRoute(item.to) }"
                    @click="navigateTo(item.to)"
                  >
                    <span class="floating-code" aria-hidden="true">
                      <svg viewBox="0 0 24 24" v-html="getUiIcon(item.icon)"></svg>
                    </span>
                    <span class="floating-copy">
                      <strong>{{ item.label }}</strong>
                      <small>{{ item.description }}</small>
                    </span>
                  </button>
                </div>
              </div>
            </Transition>
          </Teleport>
        </nav>

        <div class="topbar-page-portal desktop-nav">
          <div id="topbar-page-header"></div>
        </div>

        <div class="topbar-actions">
          <!-- 天气行内显示 -->
          <button
            type="button"
            class="weather-inline"
            :title="weather ? `${weather.city} · ${weather.weather} · 湿度 ${weather.humidity}% · 风向 ${weather.windDirection} · 点击刷新` : '点击刷新天气'"
            @click="refreshWeather"
          >
            <span v-if="weatherLoading" class="weather-inline-spin"></span>
            <template v-else>
              <span class="weather-inline-icon">{{ weather ? weatherEmoji() : '🌡️' }}</span>
              <span class="weather-inline-text">
                <span class="weather-inline-temp">{{ weather ? `${weather.temperature}°` : '--' }}</span>
                <span class="weather-inline-meta">{{ weather ? `${weather.city} · ${weather.weather}` : '' }}</span>
              </span>
            </template>
          </button>
          <span class="weather-divider" aria-hidden="true"></span>

          <button type="button" class="action-btn mobile-only" @click="mobileNavOpen = !mobileNavOpen">
            {{ mobileNavOpen ? '关闭' : '目录' }}
          </button>
          <button type="button" class="action-btn" @click="toggleNotificationPanel">
            消息
            <span v-if="unreadCount > 0" class="count-badge">{{ unreadCount > 9 ? '9+' : unreadCount }}</span>
          </button>
          <button type="button" class="profile-pill" @click="router.push('/app/profile')">
            <span class="profile-initial">{{ userInitial }}</span>
            <span class="profile-copy">
              <strong>{{ userStore.userInfo?.username || '访客' }}</strong>
            </span>
          </button>
          <button type="button" class="action-btn subtle-btn" @click="handleLogout">退出</button>
        </div>
      </div>

    </header>

    <transition name="fade">
      <div v-if="showNotificationPanel" class="notification-panel">
        <div class="panel-header">
          <div>
            <div class="text-label">[ 通知中心 ]</div>
            <strong>最新动态</strong>
          </div>
          <button v-if="unreadCount > 0" type="button" class="action-btn subtle-btn" @click="handleMarkAllRead">
            全部已读
          </button>
        </div>
        <div v-if="notificationLoading" class="panel-state">
          <span class="panel-loader"></span>
          <span>正在同步消息...</span>
        </div>
        <div v-else-if="notifications.length === 0" class="panel-state">
          <span>当前没有新的系统提醒。</span>
        </div>
        <div v-else class="notification-list">
          <button
            v-for="notification in notifications"
            :key="notification.id"
            type="button"
            class="notification-item"
            :class="{ unread: notification.isRead === 0 }"
            @click="handleNotificationClick(notification)"
          >
            <span class="notification-code" aria-hidden="true">
              <svg viewBox="0 0 24 24" v-html="getUiIcon(getTypeIcon(notification.type))"></svg>
            </span>
            <span class="notification-copy">
              <strong>{{ notification.title }}</strong>
              <small>{{ notification.content }}</small>
              <em>{{ formatTime(notification.createTime) }}</em>
            </span>
          </button>
        </div>
      </div>
    </transition>

    <transition name="fade">
      <div v-if="mobileNavOpen" class="mobile-nav-mask" @click="mobileNavOpen = false">
        <div class="mobile-nav-panel" @click.stop>
          <div class="mobile-nav-head">
            <div>
              <div class="text-label">[ 目录 ]</div>
              <strong>模块切换</strong>
            </div>
            <button type="button" class="action-btn subtle-btn" @click="mobileNavOpen = false">关闭</button>
          </div>

          <section v-for="section in visibleNavigationSections" :key="section.label" class="mobile-nav-group">
            <div class="mobile-group-label">{{ section.label }}</div>
            <button
              v-for="item in section.items"
              :key="item.to"
              type="button"
              class="mobile-nav-link nav-button"
              :class="{ 'router-link-active': isActiveRoute(item.to) }"
              @click="navigateTo(item.to)"
            >
              <span class="floating-code" aria-hidden="true">
                <svg viewBox="0 0 24 24" v-html="getUiIcon(item.icon)"></svg>
              </span>
              <span class="floating-copy">
                <strong>{{ item.label }}</strong>
                <small>{{ item.description }}</small>
              </span>
            </button>
          </section>
        </div>
      </div>
    </transition>

    <main class="content-host">
      <section class="content-paper">
        <div class="content-body">
          <component :is="activePageComponent" :key="route.fullPath" />
        </div>
      </section>
    </main>

    <div v-if="showProfileSetup" class="profile-setup-overlay">
      <div class="profile-setup-dialog">
        <div class="setup-header">
          <div>
            <div class="text-label mb-xs">[ 身体画像引导 ]</div>
            <h2 class="setup-title">{{ setupSteps[currentProfileStep].title }}</h2>
            <p class="setup-header-copy">{{ setupSteps[currentProfileStep].description }}</p>
          </div>
          <div class="setup-step-badge">{{ currentProfileStep + 1 }} / {{ setupSteps.length }}</div>
        </div>

        <div class="setup-progress">
          <button
            v-for="(step, index) in setupSteps"
            :key="step.key"
            type="button"
            class="setup-progress-step"
            :class="{ active: index === currentProfileStep, done: index < currentProfileStep }"
            @click="jumpToProfileStep(index)"
          >
            <span class="setup-progress-index">{{ index + 1 }}</span>
            <span class="setup-progress-label">{{ step.shortLabel }}</span>
          </button>
        </div>

        <div v-if="currentProfileStep === 0" class="setup-panel">
          <div class="setup-intro-card">
            <div>
              <div class="setup-kicker">基础参数</div>
              <h3>先确定你当前的身体状态</h3>
              <p>这些指标会影响训练强度、恢复节奏和热量建议，是整个系统的基础输入。</p>
            </div>
            <div class="setup-metric-preview">
              <div class="metric-chip">
                <span>身高</span>
                <strong>{{ profileForm.height || '--' }}</strong>
              </div>
              <div class="metric-chip">
                <span>体重</span>
                <strong>{{ profileForm.weight || '--' }}</strong>
              </div>
              <div class="metric-chip">
                <span>BMI</span>
                <strong>{{ bmiValueLabel }}</strong>
              </div>
            </div>
          </div>

          <div class="setup-grid">
            <div class="form-group">
              <label class="text-caption mb-xs block">身高 cm</label>
              <input v-model.number="profileForm.height" class="setup-input" type="number" min="80" max="240" />
            </div>
            <div class="form-group">
              <label class="text-caption mb-xs block">体重 kg</label>
              <input v-model.number="profileForm.weight" class="setup-input" type="number" min="20" max="250" step="0.1" />
            </div>
            <div class="form-group">
              <label class="text-caption mb-xs block">年龄</label>
              <input v-model.number="profileForm.age" class="setup-input" type="number" min="10" max="100" />
            </div>
            <div class="form-group">
              <label class="text-caption mb-xs block">性别</label>
              <div class="segmented-control">
                <button type="button" :class="{ active: profileForm.gender === 1 }" @click="profileForm.gender = 1">男</button>
                <button type="button" :class="{ active: profileForm.gender === 2 }" @click="profileForm.gender = 2">女</button>
                <button type="button" :class="{ active: profileForm.gender === 0 }" @click="profileForm.gender = 0">保密</button>
              </div>
            </div>
            <div class="form-group">
              <label class="text-caption mb-xs block">BMI</label>
              <input :value="bmiSummaryLabel" class="setup-input" type="text" readonly />
            </div>
            <div class="form-group">
              <label class="text-caption mb-xs block">活动水平</label>
              <select v-model="profileForm.activityLevel" class="setup-input">
                <option value="Sedentary">久坐</option>
                <option value="Lightly Active">轻度活动</option>
                <option value="Active">活跃</option>
                <option value="Very Active">高强度活跃</option>
              </select>
            </div>
          </div>
        </div>

        <div v-else-if="currentProfileStep === 1" class="setup-panel">
          <div class="setup-shape-grid">
            <button
              v-for="shape in bodyShapeOptions"
              :key="shape.value"
              type="button"
              class="shape-card"
              :class="{ active: profileForm.bodyShape === shape.value }"
              @click="profileForm.bodyShape = shape.value"
            >
              <img class="shape-card-image" :src="shape.image" :alt="shape.value" />
              <div class="shape-card-body">
                <div class="shape-card-title">{{ shape.value }}</div>
                <div class="shape-card-note text-caption">{{ shape.note }}</div>
              </div>
            </button>
          </div>
        </div>

        <div v-else-if="currentProfileStep === 2" class="setup-panel">
          <div class="goal-header-card">
            <div>
              <div class="setup-kicker">目标标签</div>
              <h3>选择当前最优先解决的问题</h3>
              <p>可以多选。系统会把第一个目标作为主目标，其余标签用于限制条件和调优。</p>
            </div>
            <div class="goal-selection-summary">已选 {{ selectedTrainingGoals.length }} 项</div>
          </div>
          <div class="goal-card-grid">
            <button
              v-for="goal in trainingGoalOptions"
              :key="goal.value"
              type="button"
              class="goal-card"
              :class="{ active: selectedTrainingGoals.includes(goal.value) }"
              @click="toggleTrainingGoal(goal.value)"
            >
              <span class="goal-card-icon">{{ goal.icon }}</span>
              <span class="goal-card-text">{{ goal.value }}</span>
            </button>
          </div>
        </div>

        <div v-else class="setup-panel">
          <div class="injury-layout">
            <div class="injury-intro-card">
              <div class="setup-kicker">风险约束</div>
              <h3>把训练限制说明白</h3>
              <p>如果存在伤病困扰，系统会主动规避高风险动作，并优先提供低风险替代方案。</p>
            </div>
            <div class="injury-control-card">
              <label class="text-caption mb-sm block">是否存在伤病限制</label>
              <div class="segmented-control injury-toggle">
                <button type="button" :class="{ active: !profileForm.hasInjury }" @click="setInjury(false)">否</button>
                <button type="button" :class="{ active: profileForm.hasInjury }" @click="setInjury(true)">是</button>
              </div>
              <div v-if="profileForm.hasInjury" class="injury-parts-panel">
                <div class="text-caption text-secondary mb-sm">伤病部位</div>
                <div class="tag-grid injury-parts">
                  <button
                    v-for="part in injuryPartOptions"
                    :key="part"
                    type="button"
                    :class="{ active: selectedInjuryParts.includes(part) }"
                    @click="toggleInjuryPart(part)"
                  >
                    {{ part }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="profileSetupError" class="setup-error text-caption">{{ profileSetupError }}</div>

        <div class="setup-footer">
          <button v-if="currentProfileStep > 0" type="button" class="setup-secondary" @click="goToPreviousProfileStep">
            上一步
          </button>
          <div class="setup-footer-spacer"></div>
          <button
            v-if="currentProfileStep < setupSteps.length - 1"
            type="button"
            class="setup-submit"
            @click="goToNextProfileStep"
          >
            下一步
          </button>
          <button
            v-else
            type="button"
            class="setup-submit"
            :disabled="profileSaving"
            @click="submitProfileSetup"
          >
            {{ profileSaving ? '保存中...' : '保存并继续' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, reactive, ref, onMounted, onUnmounted, watch } from 'vue'
import { useWeather } from '../composables/useWeather'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { getUserInfo } from '../api/user'
import { getNotifications, getUnreadCount, markAsRead, markAllAsRead } from '../api/notification'
import { getProfile, saveProfile } from '../api/profile'

type NavItem = {
  icon: string
  label: string
  description: string
  to: string
  adminOnly?: boolean
}

type NavSection = {
  label: string
  items: NavItem[]
}

type TopbarContextDetail = {
  route: string
  label: string
  value: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const mobileNavOpen = ref(false)
const showMoreMenu = ref(false)
const moreWrapRef = ref<HTMLElement | null>(null)
const moreMenuStyle = ref<Record<string, string>>({})
let moreHideTimer: ReturnType<typeof setTimeout> | null = null

function openMoreMenu() {
  if (moreHideTimer) { clearTimeout(moreHideTimer); moreHideTimer = null }
  if (moreWrapRef.value) {
    const rect = moreWrapRef.value.getBoundingClientRect()
    moreMenuStyle.value = {
      top: `${rect.bottom + 6}px`,
      right: `${window.innerWidth - rect.right}px`
    }
  }
  showMoreMenu.value = true
}
function closeMoreMenu() {
  moreHideTimer = setTimeout(() => { showMoreMenu.value = false }, 120)
}
function keepMoreMenu() {
  if (moreHideTimer) { clearTimeout(moreHideTimer); moreHideTimer = null }
}
const showNotificationPanel = ref(false)
const notifications = ref<any[]>([])
const unreadCount = ref(0)
const notificationLoading = ref(false)
const showProfileSetup = ref(false)
const profileSaving = ref(false)
const profileSetupError = ref('')
const currentProfileStep = ref(0)
const topbarContext = ref<TopbarContextDetail | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

// ── 天气 ────────────────────────────────────────────────────────────────────
const { weather, loading: weatherLoading, weatherEmoji, fetchWeather: refreshWeather, init: initWeather, dispose: disposeWeather } = useWeather()

const navigationSections: NavSection[] = [
  {
    label: '核心',
    items: [
      { icon: 'dashboard', label: '训练总览', description: '查看训练与营养全局状态', to: '/app/dashboard' },
      { icon: 'coach', label: 'FitMind 教练', description: '和 FitMind AI 教练实时对话', to: '/app/coach' },
      { icon: 'training', label: '训练计划', description: '安排日程与负荷', to: '/app/training' },
      { icon: 'diet', label: '饮食计划', description: '管理热量与营养', to: '/app/diet' },
      { icon: 'food', label: '饮食识别', description: '拍照识别食物', to: '/app/food-recognition' },
      { icon: 'atlas', label: '动作图谱', description: '浏览动作与部位', to: '/app/exercise-atlas' }
    ]
  },
  {
    label: '记录',
    items: [
      { icon: 'history', label: '历史记录', description: '追踪阶段进展', to: '/app/history' },
      { icon: 'achievement', label: '成就系统', description: '查看里程碑表现', to: '/app/achievements' },
      { icon: 'profile', label: '个人画像', description: '维护身体与目标参数', to: '/app/profile' }
    ]
  },
  {
    label: '社交',
    items: [
      { icon: 'community', label: '训练社区', description: '分享训练动态', to: '/app/community' },
      { icon: 'leaderboard', label: '排行榜', description: '对比训练表现', to: '/app/leaderboard' }
    ]
  },
  {
    label: '管理',
    items: [
      { icon: 'admin', label: '系统管理', description: '后台运营与资源配置', to: '/app/admin', adminOnly: true }
    ]
  }
]

const uiIcons: Record<string, string> = {
  dashboard: '<path d="M4 5.5h7v5.5H4zM13 5.5h7v9H13zM4 13h7v5.5H4zM13 16.5h7V20H13z" />',
  coach: '<path d="M7 7h10a3 3 0 0 1 3 3v4a3 3 0 0 1-3 3h-4.2L9 20v-3H7a3 3 0 0 1-3-3v-4a3 3 0 0 1 3-3Z" /><path d="M9.5 11.5h5M12 9v5" />',
  training: '<path d="M3 10.5v3M6 8v8M9 10.5v3M15 10.5v3M18 8v8M21 10.5v3" /><path d="M6 12h12" />',
  diet: '<path d="M12 4c4.5 2 6.5 5 6.5 8.4A5.5 5.5 0 0 1 13 18h-1a5.5 5.5 0 0 1-5.5-5.6C6.5 9 8.3 6.1 12 4Z" /><path d="M12 8v8M9 11.5h6" />',
  food: '<path d="M5 8.5h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2Z" /><path d="M8 8.5 9.5 6h5L16 8.5" /><path d="M12 16a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z" />',
  atlas: '<path d="M8.5 5.5a3.5 3.5 0 1 1 7 0c0 1.3-.7 2.4-1.7 3.1l1.4 3.9 2.6 2.4-1.4 1.5-2-1.5-1.1 4.6h-2.6L9.6 14.9l-2 1.5-1.4-1.5 2.6-2.4 1.4-3.9A3.9 3.9 0 0 1 8.5 5.5Z" />',
  history: '<path d="M4 12a8 8 0 1 0 2.3-5.7" /><path d="M4 4v4h4" /><path d="M12 8v4.5l3 1.5" />',
  achievement: '<path d="M8 5h8v3a4 4 0 0 1-8 0V5Z" /><path d="M8 7H6a2 2 0 0 0 0 4h2M16 7h2a2 2 0 0 1 0 4h-2" /><path d="M12 12v3M9.5 20h5M10 15h4l1 3h-6l1-3Z" />',
  profile: '<path d="M12 12a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Z" /><path d="M5 19a7 7 0 0 1 14 0" />',
  community: '<path d="M9 11a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Zm6 1a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z" /><path d="M3.8 18a5.2 5.2 0 0 1 10.4 0M14 18a4 4 0 0 1 6 0" />',
  leaderboard: '<path d="M5 19V11M12 19V5M19 19v-8" /><path d="M3 19h18" />',
  admin: '<path d="M12 3.5 18.5 6v5c0 4.3-2.8 7.4-6.5 9.5C8.3 18.4 5.5 15.3 5.5 11V6L12 3.5Z" /><path d="M9.5 12h5M12 9.5v5" />',
  more: '<path d="M5 12h.01M12 12h.01M19 12h.01" />',
  system: '<path d="M12 4v3M12 17v3M4 12h3M17 12h3M6.3 6.3l2.1 2.1M15.6 15.6l2.1 2.1M17.7 6.3l-2.1 2.1M8.4 15.6l-2.1 2.1" /><path d="M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" />',
  reminder: '<path d="M9 17h6M10 20h4" /><path d="M6 16h12l-1.2-1.5V11a4.8 4.8 0 1 0-9.6 0v3.5L6 16Z" />',
  uplink: '<path d="M12 18V7M8 11l4-4 4 4" /><path d="M6 19h12" />'
}

const getUiIcon = (name: string) => uiIcons[name] || uiIcons.uplink

const isActiveRoute = (target: string) => route.path === target

const navigateTo = (target: string) => {
  mobileNavOpen.value = false
  showMoreMenu.value = false
  showNotificationPanel.value = false
  if (route.path !== target) {
    router.push(target)
  }
}

const pageComponentLoaders: Record<string, () => Promise<any>> = {
  '/app/dashboard': () => import('../views/Dashboard.vue'),
  '/app/coach': () => import('../views/ai/CoachChat.vue'),
  '/app/training': () => import('../views/plan/Training.vue'),
  '/app/diet': () => import('../views/plan/Diet.vue'),
  '/app/community': () => import('../views/Community.vue'),
  '/app/history': () => import('../views/History.vue'),
  '/app/exercise-atlas': () => import('../views/ExerciseAtlas.vue'),
  '/app/profile': () => import('../views/user/Profile.vue'),
  '/app/achievements': () => import('../views/Achievements.vue'),
  '/app/leaderboard': () => import('../views/Leaderboard.vue'),
  '/app/food-recognition': () => import('../views/FoodRecognition.vue'),
  '/app/action-analysis': () => import('../views/ActionAnalysis.vue'),
  '/app/admin': () => import('../views/admin/AdminPanel.vue')
}

const asyncPageComponents = Object.fromEntries(
  Object.entries(pageComponentLoaders).map(([path, loader]) => [path, defineAsyncComponent(loader)])
) as Record<string, ReturnType<typeof defineAsyncComponent>>

const activePageComponent = computed(
  () => asyncPageComponents[route.path] || asyncPageComponents['/app/dashboard']
)

const setupSteps = [
  {
    key: 'metrics',
    shortLabel: '基础',
    title: '完善基础参数',
    description: '先确认当前身体状态，作为训练负荷和恢复安排的底层输入。'
  },
  {
    key: 'shape',
    shortLabel: '体型',
    title: '选择体型类别',
    description: '根据示意图选择最接近的体型，用于理解脂肪分布和训练关注点。'
  },
  {
    key: 'goal',
    shortLabel: '目标',
    title: '明确训练目标',
    description: '把现在最关心的结果标记出来，系统会按优先级生成计划。'
  },
  {
    key: 'injury',
    shortLabel: '伤病',
    title: '补充伤病限制',
    description: '如果存在伤病困扰，后续计划会主动规避高风险动作。'
  }
]

const bodyShapeOptions = [
  { value: '苹果型', image: '/assets/body-shape-apple.png', note: '上半身更集中，腰腹区域更容易堆积。' },
  { value: '梨型', image: '/assets/body-shape-pear.png', note: '下半身更明显，臀腿比例相对更突出。' },
  { value: '沙漏型', image: '/assets/body-shape-hourglass.png', note: '肩臀较均衡，腰部线条更明显。' },
  { value: '直筒型', image: '/assets/body-shape-rectangle.png', note: '肩腰臀差异较小，整体轮廓更平直。' }
]

const trainingGoalOptions = [
  { value: '全身减脂减重', icon: 'G1' },
  { value: '局部变瘦更紧致', icon: 'G2' },
  { value: '增肌，肌肉线条更明显', icon: 'G3' },
  { value: '体态体形改善', icon: 'G4' },
  { value: '保持身体健康', icon: 'G5' },
  { value: '跑步专项提升', icon: 'G6' },
  { value: '运动能力提升', icon: 'G7' }
]

const injuryPartOptions = ['颈肩', '腰背', '膝盖', '踝关节', '髋部', '手臂或肘部', '其他']

const selectedTrainingGoals = ref<string[]>([])
const selectedInjuryParts = ref<string[]>([])
const profileForm = reactive({
  height: null as number | null,
  weight: null as number | null,
  age: null as number | null,
  gender: 0,
  activityLevel: 'Lightly Active',
  bodyShape: '',
  hasInjury: false,
  injuryParts: '',
  trainingGoals: '',
  fitnessGoal: ''
})

const isAdmin = computed(() => {
  const role = userStore.userInfo?.role
  return role === 'ADMIN' || role === 'admin'
})

const visibleNavigationSections = computed(() =>
  navigationSections
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => !item.adminOnly || isAdmin.value)
    }))
    .filter((section) => section.items.length > 0)
)

const allNavigationItems = computed(() => visibleNavigationSections.value.flatMap((section) => section.items))
const primaryNavItems = computed(() => allNavigationItems.value.slice(0, 6))
const overflowNavItems = computed(() => allNavigationItems.value.slice(6))
const overflowNavLookup = computed(() => new Set(overflowNavItems.value.map((item) => item.to)))

const currentView = computed(() => {
  const routeMeta = route.meta as Record<string, string | undefined>
  const matchedItem = allNavigationItems.value.find((item) => item.to === route.path)
  return {
    section: routeMeta.section || matchedItem?.label || '训练空间',
    title: routeMeta.title || matchedItem?.label || 'FitMind',
    description: routeMeta.description || matchedItem?.description || '你的训练、营养与恢复体验中心。'
  }
})

const currentTopbarContext = computed(() => {
  if (!topbarContext.value) return null
  return topbarContext.value.route === route.path ? topbarContext.value : null
})

const currentSectionIndex = computed(() => {
  const index = visibleNavigationSections.value.findIndex((section) =>
    section.items.some((item) => item.to === route.path)
  )
  return String((index >= 0 ? index : 0) + 1).padStart(2, '0')
})

const userInitial = computed(() => (userStore.userInfo?.username || 'U').charAt(0).toUpperCase())
const profileStatusLabel = computed(() => (userStore.profilePromptRequired ? '画像待完善' : '训练档案已就绪'))

const normalizeBodyShape = (value?: string | null) => {
  const text = (value || '').trim()
  if (!text) return ''
  if (text.includes('苹果') || /apple/i.test(text)) return '苹果型'
  if (text.includes('梨') || /pear/i.test(text)) return '梨型'
  if (text.includes('沙漏') || /hourglass/i.test(text)) return '沙漏型'
  if (text.includes('直筒') || /rectangle/i.test(text)) return '直筒型'
  return text
}

const bmiValue = computed(() => {
  if (!profileForm.height || !profileForm.weight) return null
  const heightInMeters = profileForm.height / 100
  if (!heightInMeters) return null
  return profileForm.weight / (heightInMeters * heightInMeters)
})

const bmiValueLabel = computed(() => (bmiValue.value ? bmiValue.value.toFixed(1) : '--'))
const bmiCategoryLabel = computed(() => {
  if (!bmiValue.value) return '待计算'
  if (bmiValue.value < 18.5) return '偏瘦'
  if (bmiValue.value < 24) return '正常'
  if (bmiValue.value < 28) return '超重'
  return '肥胖'
})

const bmiSummaryLabel = computed(() => {
  if (!bmiValue.value) return '填写身高和体重后自动计算'
  return `${bmiValue.value.toFixed(1)} (${bmiCategoryLabel.value})`
})

const splitTags = (value?: string | null) => {
  if (!value) return []
  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

const handleLogout = () => {
  userStore.logout()
  router.push('/login')
}

const applyProfileToForm = (profile: any) => {
  if (!profile) return
  Object.assign(profileForm, {
    height: profile.height ?? null,
    weight: profile.weight ?? null,
    age: profile.age ?? null,
    gender: profile.gender ?? 0,
    activityLevel: profile.activityLevel || 'Lightly Active',
    bodyShape: normalizeBodyShape(profile.bodyShape),
    hasInjury: !!profile.hasInjury,
    injuryParts: profile.injuryParts || '',
    trainingGoals: profile.trainingGoals || profile.fitnessGoal || '',
    fitnessGoal: profile.fitnessGoal || ''
  })
  selectedTrainingGoals.value = splitTags(profileForm.trainingGoals)
  selectedInjuryParts.value = splitTags(profileForm.injuryParts)
}

const loadProfileSetupState = async () => {
  if (!userStore.token) return
  currentProfileStep.value = 0
  profileSetupError.value = ''
  try {
    const profile: any = await getProfile()
    applyProfileToForm(profile)
    showProfileSetup.value = true
  } catch (_error) {
    showProfileSetup.value = true
  }
}

const toggleTrainingGoal = (goal: string) => {
  if (selectedTrainingGoals.value.includes(goal)) {
    selectedTrainingGoals.value = selectedTrainingGoals.value.filter((item) => item !== goal)
  } else {
    selectedTrainingGoals.value = [...selectedTrainingGoals.value, goal]
  }
}

const setInjury = (hasInjury: boolean) => {
  profileForm.hasInjury = hasInjury
  if (!hasInjury) {
    selectedInjuryParts.value = []
  }
}

const toggleInjuryPart = (part: string) => {
  if (selectedInjuryParts.value.includes(part)) {
    selectedInjuryParts.value = selectedInjuryParts.value.filter((item) => item !== part)
  } else {
    selectedInjuryParts.value = [...selectedInjuryParts.value, part]
  }
}

const validateProfileStep = (step = currentProfileStep.value) => {
  profileSetupError.value = ''
  if (step === 0 && (!profileForm.height || !profileForm.weight || !profileForm.age)) {
    profileSetupError.value = '请先填写身高、体重和年龄。'
    return false
  }
  if (step === 1 && !profileForm.bodyShape) {
    profileSetupError.value = '请选择体型类别。'
    return false
  }
  if (step === 2 && selectedTrainingGoals.value.length === 0) {
    profileSetupError.value = '请至少选择一个训练目标。'
    return false
  }
  if (step === 3 && profileForm.hasInjury && selectedInjuryParts.value.length === 0) {
    profileSetupError.value = '请补充伤病部位。'
    return false
  }
  return true
}

const goToNextProfileStep = () => {
  if (!validateProfileStep()) return
  currentProfileStep.value = Math.min(currentProfileStep.value + 1, setupSteps.length - 1)
}

const goToPreviousProfileStep = () => {
  profileSetupError.value = ''
  currentProfileStep.value = Math.max(currentProfileStep.value - 1, 0)
}

const jumpToProfileStep = (targetStep: number) => {
  if (targetStep <= currentProfileStep.value) {
    profileSetupError.value = ''
    currentProfileStep.value = targetStep
    return
  }
  for (let step = 0; step < targetStep; step += 1) {
    if (!validateProfileStep(step)) return
  }
  currentProfileStep.value = targetStep
}

const submitProfileSetup = async () => {
  if (!validateProfileStep(0) || !validateProfileStep(1) || !validateProfileStep(2) || !validateProfileStep(3)) {
    return
  }

  profileSaving.value = true
  try {
    const trainingGoals = selectedTrainingGoals.value.join(',')
    const injuryParts = profileForm.hasInjury ? selectedInjuryParts.value.join(',') : ''
    await saveProfile({
      ...profileForm,
      bodyShape: normalizeBodyShape(profileForm.bodyShape),
      trainingGoals,
      fitnessGoal: selectedTrainingGoals.value[0],
      injuryParts,
      profileCompleted: true
    })
    userStore.setProfilePromptRequired(false)
    try {
      const userInfo = await getUserInfo()
      userStore.setUserInfo(userInfo)
    } catch (_error) {
      // ignore user info refresh failure
    }
    showProfileSetup.value = false
  } catch (error: any) {
    profileSetupError.value = error?.message || '保存失败，请稍后重试。'
  } finally {
    profileSaving.value = false
  }
}

const toggleNotificationPanel = () => {
  showNotificationPanel.value = !showNotificationPanel.value
  showMoreMenu.value = false
  if (showNotificationPanel.value) {
    fetchNotifications()
  }
}

const fetchNotifications = async () => {
  notificationLoading.value = true
  try {
    const [notifs, countRes] = await Promise.all([getNotifications(), getUnreadCount()])
    notifications.value = (notifs as any) || []
    unreadCount.value = (countRes as any)?.unread || 0
  } catch (_error) {
    // silently fail
  } finally {
    notificationLoading.value = false
  }
}

const fetchUnreadCount = async () => {
  try {
    const res = await getUnreadCount()
    unreadCount.value = (res as any)?.unread || 0
  } catch (_error) {
    // silently fail
  }
}

const handleNotificationClick = async (notification: any) => {
  if (notification.isRead === 0) {
    try {
      await markAsRead(notification.id)
      notification.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch (_error) {
      // ignore mark read failure
    }
  }

  if (notification.relatedType === 'achievement' && notification.relatedId) {
    router.push('/app/achievements')
  } else if (notification.relatedType === 'training' && notification.relatedId) {
    router.push('/app/training')
  } else if (notification.relatedType === 'community' && notification.relatedId) {
    router.push('/app/community')
  }

  showNotificationPanel.value = false
}

const handleMarkAllRead = async () => {
  try {
    await markAllAsRead()
    notifications.value.forEach((notification) => {
      notification.isRead = 1
    })
    unreadCount.value = 0
  } catch (_error) {
    // ignore batch mark read failure
  }
}

const getTypeIcon = (type: string) => {
  const icons: Record<string, string> = {
    ACHIEVEMENT: 'achievement',
    TRAINING: 'training',
    DIET: 'diet',
    COMMUNITY: 'community',
    SYSTEM: 'system',
    REMINDER: 'reminder'
  }
  return icons[type] || 'uplink'
}

const formatTime = (time: string) => {
  if (!time) return ''
  const d = new Date(time)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return `${Math.floor(diff / 86400000)} 天前`
}

const handleTopbarContextSync = (event: Event) => {
  const detail = (event as CustomEvent<TopbarContextDetail | null>).detail
  topbarContext.value = detail
}

watch(
  () => route.fullPath,
  () => {
    mobileNavOpen.value = false
    showMoreMenu.value = false
    showNotificationPanel.value = false
    if (topbarContext.value && topbarContext.value.route !== route.path) {
      topbarContext.value = null
    }
  }
)

onMounted(async () => {
  window.addEventListener('fitmind:topbar-context', handleTopbarContextSync as EventListener)
  if (userStore.token && !userStore.userInfo) {
    try {
      const userInfo = await getUserInfo()
      userStore.setUserInfo(userInfo)
    } catch (_error) {
      // token might be invalid
    }
  }

  if (userStore.token) {
    if (userStore.consumeProfileSetupPending()) {
      loadProfileSetupState()
    }
    fetchUnreadCount()
    pollTimer = setInterval(fetchUnreadCount, 60000)
  }

  // 初始化天气（IP 定位后自动拉取，每 30 分钟刷新）
  initWeather()
})

onUnmounted(() => {
  window.removeEventListener('fitmind:topbar-context', handleTopbarContextSync as EventListener)
  if (pollTimer) clearInterval(pollTimer)
  disposeWeather()
})
</script>

<style scoped>
.platform-shell {
  position: relative;
  min-height: 100vh;
  padding: 20px 24px 32px;
  isolation: isolate;
}

.ambient-orb {
  position: fixed;
  border-radius: 999px;
  filter: blur(88px);
  opacity: 0.4;
  pointer-events: none;
  z-index: 0;
}

.orb-one {
  top: -140px;
  left: -100px;
  width: 280px;
  height: 280px;
  background: rgba(127, 157, 135, 0.18);
}

.orb-two {
  top: 0;
  right: -120px;
  width: 260px;
  height: 260px;
  background: rgba(194, 169, 120, 0.16);
}

.topbar {
  position: sticky;
  top: 16px;
  z-index: 40;
  isolation: isolate;
  border: 1px solid rgba(79, 73, 65, 0.08);
  border-radius: 20px;
  background:
    linear-gradient(180deg, rgba(255, 252, 247, 0.94), rgba(247, 241, 232, 0.96)),
    rgba(250, 246, 238, 0.96);
  backdrop-filter: blur(18px) saturate(140%);
  -webkit-backdrop-filter: blur(18px) saturate(140%);
  box-shadow:
    0 18px 38px rgba(70, 59, 42, 0.12),
    0 1px 0 rgba(255, 255, 255, 0.46) inset,
    var(--shadow-soft);
}

.topbar::after {
  content: '';
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: -14px;
  height: 18px;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(246, 239, 229, 0.34), rgba(246, 239, 229, 0));
}

.topbar-main {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: auto fit-content(520px) minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 16px 20px 14px;
}

.topbar-intro {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  border: none;
  background: transparent;
  color: var(--text-main);
  cursor: pointer;
  padding: 0;
}

.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #7f9d87, #91a694);
  color: #f8f5ef;
  font-family: var(--font-heading);
  font-size: 0.92rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.1;
}

.brand-copy strong {
  font-family: var(--font-heading);
  font-size: 1.08rem;
  font-weight: 800;
}

.brand-copy small {
  color: var(--text-secondary);
  font-size: 0.74rem;
}

.topbar-context {
  display: inline-flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
  padding: 10px 14px;
  border-radius: 16px;
  border: 1px solid rgba(127, 157, 135, 0.18);
  background: rgba(127, 157, 135, 0.08);
}

.topbar-context-label {
  color: var(--text-secondary);
  font-size: 0.8rem;
  white-space: nowrap;
}

.topbar-context-value {
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 1rem;
  letter-spacing: 0.06em;
  white-space: nowrap;
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  position: relative;
  z-index: 100;
  overflow: visible;
}

.topbar-page-portal {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  margin-left: 428px;
  overflow: hidden;
  pointer-events: none;
}

#topbar-page-header {
  width: 100%;
  min-width: 0;
  display: block;
  overflow: hidden;
  pointer-events: none;
}

:deep(.topbar-page-shell) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  width: 100%;
  min-width: 0;
  overflow: hidden;
  pointer-events: auto;
}

:deep(.topbar-page-copy) {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  gap: 10px;
  overflow: hidden;
  pointer-events: auto;
}

:deep(.topbar-page-kicker) {
  color: rgba(89, 81, 72, 0.64);
  font-family: var(--font-heading);
  font-size: 0.68rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  white-space: nowrap;
  flex-shrink: 0;
}

:deep(.topbar-page-title) {
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 0.92rem;
  font-weight: 800;
  white-space: nowrap;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.topbar-page-meta) {
  color: var(--text-secondary);
  font-size: 0.76rem;
  white-space: nowrap;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

:deep(.topbar-page-actions) {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: nowrap;
  gap: 8px;
  flex: 0 0 auto;
  flex-shrink: 0;
  pointer-events: auto;
}

.top-nav-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 11px;
  border-radius: 999px;
  border: 1px solid rgba(79, 73, 65, 0.08);
  background: transparent;
  color: var(--text-secondary);
  text-decoration: none;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease;
}

.nav-button {
  appearance: none;
  -webkit-appearance: none;
  font: inherit;
}

.top-nav-link:hover,
.top-nav-link.router-link-active,
.more-trigger:hover {
  border-color: rgba(127, 157, 135, 0.22);
  background: rgba(127, 157, 135, 0.08);
  color: var(--text-main);
}

.top-nav-code {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(127, 157, 135, 0.12);
  color: var(--secondary);
}

.top-nav-code svg,
.floating-code svg,
.notification-code svg {
  width: 14px;
  height: 14px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.top-nav-label {
  font-family: var(--font-heading);
  font-size: 0.85rem;
  font-weight: 700;
  white-space: nowrap;
}

.nav-more-wrap {
  position: relative;
}

/* Teleport 版浮动菜单（挂在 body，不受层叠上下文限制） */
:global(.floating-menu-teleport) {
  position: fixed;
  z-index: 9999;
  width: 360px;
  max-width: min(360px, calc(100vw - 40px));
  padding: 10px;
  border-radius: 18px;
  border: 1px solid rgba(79, 73, 65, 0.1);
  background: rgba(252, 248, 241, 0.99);
  box-shadow: 0 28px 70px rgba(0,0,0,0.22);
  text-align: left;
  /* 覆盖 body 深色主题的继承 */
  color: #1f1c19;
  font-family: "Aptos", "Segoe UI Variable Text", "Microsoft YaHei UI", sans-serif;
  font-size: 1rem;
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}

:global(.more-menu-enter-active),
:global(.more-menu-leave-active) {
  transition: opacity 0.16s ease, transform 0.16s ease;
}
:global(.more-menu-enter-from),
:global(.more-menu-leave-to) {
  opacity: 0;
  transform: translateY(-6px);
}

/* 强制覆盖 Teleport 内所有文字元素的颜色（body 深色主题不污染面板） */
:global(.floating-menu-teleport *) {
  color: inherit;
}
:global(.floating-menu-teleport .floating-group-label) {
  color: rgba(89, 81, 72, 0.6) !important;
  font-family: "Iowan Old Style", "Palatino Linotype", "Times New Roman", serif;
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}
:global(.floating-menu-teleport .floating-link) {
  color: #1f1c19 !important;
  font-family: "Aptos", "Segoe UI Variable Text", "Microsoft YaHei UI", sans-serif;
}
:global(.floating-menu-teleport .floating-copy strong) {
  color: #1f1c19 !important;
  font-size: 0.9rem;
}
:global(.floating-menu-teleport .floating-copy small) {
  color: #645b53 !important;
  font-size: 0.75rem;
}

.floating-group + .floating-group {
  margin-top: 10px;
}

.floating-group-label,
.mobile-group-label {
  margin: 6px 6px 8px;
  color: rgba(89, 81, 72, 0.54);
  font-family: var(--font-heading);
  font-size: 0.7rem;
  font-weight: 800;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.floating-link,
.mobile-nav-link {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  width: 100%;
  padding: 12px;
  border-radius: 16px;
  text-decoration: none;
  color: var(--text-main);
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
}

.floating-link:hover,
.mobile-nav-link:hover,
.mobile-nav-link.router-link-active {
  border-color: rgba(127, 157, 135, 0.18);
  background: rgba(127, 157, 135, 0.08);
}

.floating-code,
.notification-code {
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: rgba(127, 157, 135, 0.12);
  color: var(--secondary);
}

.floating-code svg,
.notification-code svg {
  width: 18px;
  height: 18px;
}

.floating-copy,
.notification-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-start;
  text-align: left;
}

.floating-copy strong,
.notification-copy strong {
  font-size: 0.9rem;
}

.floating-copy small,
.notification-copy small,
.notification-copy em {
  color: var(--text-secondary);
  font-size: 0.75rem;
  font-style: normal;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-btn {
  position: relative;
  min-height: 38px;
  padding: 0 13px;
  border: 1px solid rgba(79, 73, 65, 0.1);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.55);
  color: var(--text-main);
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease;
}

.action-btn:hover {
  border-color: rgba(127, 157, 135, 0.2);
  background: rgba(127, 157, 135, 0.09);
}

.subtle-btn {
  color: var(--text-secondary);
}

.count-badge {
  margin-left: 8px;
  padding: 2px 7px;
  border-radius: 999px;
  background: linear-gradient(135deg, #7f9d87, #97b19d);
  color: #111615;
  font-family: var(--font-heading);
  font-size: 0.72rem;
  font-weight: 800;
}

.profile-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 0 12px 0 6px;
  border-radius: 999px;
  border: 1px solid rgba(79, 73, 65, 0.1);
  background: rgba(255, 255, 255, 0.55);
  cursor: pointer;
}

.profile-initial {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #7f9d87, #8ea7a1);
  color: #f8f5ef;
  font-family: var(--font-heading);
  font-size: 0.78rem;
  font-weight: 800;
}

.profile-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.05;
}

.profile-copy strong {
  font-size: 0.84rem;
}

.profile-copy small {
  color: var(--text-secondary);
  font-size: 0.68rem;
}

.notification-panel {
  position: absolute;
  top: 82px;
  right: 18px;
  z-index: 35;
  width: 400px;
  max-width: calc(100vw - 36px);
  border-radius: 18px;
  border: 1px solid rgba(79, 73, 65, 0.1);
  background: rgba(252, 248, 241, 0.99);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 18px;
  border-bottom: 1px solid rgba(79, 73, 65, 0.08);
}

.panel-header strong {
  font-family: var(--font-heading);
  font-size: 1rem;
}

.panel-state {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 20px;
  color: var(--text-secondary);
  text-align: center;
}

.panel-loader {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--secondary);
  box-shadow: 16px 0 0 rgba(194, 169, 120, 0.35), -16px 0 0 rgba(194, 169, 120, 0.16);
  animation: pulse 1.1s infinite ease-in-out;
}

@keyframes pulse {
  50% {
    transform: scale(0.72);
    opacity: 0.55;
  }
}

.notification-list {
  max-height: 440px;
  overflow-y: auto;
  padding: 10px;
}

.notification-item {
  width: 100%;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  border-radius: 16px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
}

.notification-item:hover,
.notification-item.unread {
  border-color: rgba(194, 169, 120, 0.14);
  background: rgba(127, 157, 135, 0.07);
}

.content-host {
  position: relative;
  z-index: 1;
  margin-top: 18px;
  padding: 0 10px 12px;
}

.mobile-only {
  display: none;
}

.mobile-nav-mask {
  position: fixed;
  inset: 0;
  z-index: 12;
  display: flex;
  justify-content: flex-end;
  background: rgba(5, 8, 18, 0.68);
  backdrop-filter: blur(8px);
}

.mobile-nav-panel {
  width: min(380px, 100vw);
  height: 100%;
  overflow-y: auto;
  padding: 18px;
  background: rgba(252, 248, 241, 0.99);
  border-left: 1px solid rgba(79, 73, 65, 0.1);
}

.mobile-nav-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.mobile-nav-head strong {
  font-family: var(--font-heading);
  font-size: 1.1rem;
}

.mobile-nav-group + .mobile-nav-group {
  margin-top: 14px;
}

.profile-setup-overlay {
  position: fixed;
  inset: 0;
  z-index: 20;
  background:
    radial-gradient(circle at top left, rgba(194, 169, 120, 0.18), transparent 30%),
    radial-gradient(circle at bottom right, rgba(127, 157, 135, 0.16), transparent 32%),
    rgba(244, 238, 229, 0.78);
  backdrop-filter: blur(16px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.profile-setup-dialog {
  width: min(1120px, 100%);
  max-height: min(760px, calc(100vh - 48px));
  overflow-y: auto;
  padding: 26px;
  border: 1px solid rgba(88, 78, 67, 0.1);
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(194, 169, 120, 0.12), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.78), rgba(245, 238, 228, 0.96));
  box-shadow:
    0 30px 80px rgba(82, 69, 54, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.setup-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
}

.setup-title {
  margin: 4px 0 0;
  font-family: var(--font-heading);
  font-size: 2rem;
}

.setup-header-copy {
  margin: 10px 0 0;
  max-width: 640px;
  color: var(--text-secondary);
}

.setup-step-badge {
  min-width: 76px;
  height: 38px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(127, 157, 135, 0.22);
  color: var(--secondary);
  font-family: var(--font-heading);
  font-size: 0.8rem;
  font-weight: 800;
}

.setup-progress {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.setup-progress-step {
  min-height: 54px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid rgba(88, 78, 67, 0.1);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.54);
  color: var(--text-secondary);
  cursor: pointer;
}

.setup-progress-step.active {
  border-color: rgba(127, 157, 135, 0.34);
  background: rgba(127, 157, 135, 0.12);
  color: var(--text-main);
}

.setup-progress-step.done {
  border-color: rgba(127, 157, 135, 0.18);
  background: rgba(127, 157, 135, 0.08);
}

.setup-progress-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(244, 237, 227, 0.96);
  font-family: var(--font-heading);
  font-size: 0.78rem;
  font-weight: 800;
}

.setup-progress-step.active .setup-progress-index,
.setup-progress-step.done .setup-progress-index {
  background: currentColor;
  color: #fffaf2;
}

.setup-progress-label {
  font-family: var(--font-heading);
  font-size: 0.84rem;
  font-weight: 800;
}

.setup-panel {
  margin-top: 18px;
}

.setup-intro-card,
.goal-header-card,
.injury-intro-card,
.injury-control-card {
  border: 1px solid rgba(88, 78, 67, 0.1);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.62);
  box-shadow: 0 14px 28px rgba(82, 69, 54, 0.06);
}

.setup-intro-card,
.goal-header-card {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 18px;
  margin-bottom: 14px;
}

.setup-intro-card h3,
.goal-header-card h3,
.injury-intro-card h3 {
  margin: 8px 0 6px;
  font-family: var(--font-heading);
  font-size: 1.45rem;
}

.setup-intro-card p,
.goal-header-card p,
.injury-intro-card p {
  margin: 0;
  color: var(--text-secondary);
}

.setup-kicker {
  color: var(--secondary);
  font-family: var(--font-heading);
  font-size: 0.74rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.setup-metric-preview {
  display: grid;
  grid-template-columns: repeat(3, minmax(90px, 1fr));
  gap: 8px;
  min-width: 290px;
}

.metric-chip {
  padding: 12px 10px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(243, 236, 226, 0.92));
  border: 1px solid rgba(88, 78, 67, 0.08);
}

.metric-chip span {
  display: block;
  font-size: 0.72rem;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.metric-chip strong,
.goal-selection-summary {
  font-family: var(--font-heading);
  font-size: 1.25rem;
  color: var(--text-main);
}

.setup-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.setup-input {
  width: 100%;
  min-height: 48px;
  border: 1px solid rgba(88, 78, 67, 0.12);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--text-main);
  padding: 12px 14px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7);
}

.setup-input:focus {
  outline: none;
  border-color: rgba(127, 157, 135, 0.46);
  box-shadow: 0 0 0 4px rgba(127, 157, 135, 0.1);
}

.segmented-control {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.segmented-control.injury-toggle {
  max-width: 240px;
  grid-template-columns: repeat(2, 1fr);
}

.segmented-control button,
.tag-grid button {
  min-height: 42px;
  border: 1px solid rgba(88, 78, 67, 0.12);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.7);
  color: var(--text-secondary);
  cursor: pointer;
  font-family: var(--font-heading);
  font-size: 0.8rem;
  font-weight: 700;
}

.segmented-control button:hover,
.tag-grid button:hover {
  border-color: rgba(194, 169, 120, 0.26);
  color: var(--secondary);
}

.segmented-control button.active,
.tag-grid button.active {
  background: linear-gradient(135deg, #7f9d87, #97b19d);
  border-color: transparent;
  color: #111615;
}

.tag-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.setup-shape-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.shape-card {
  padding: 0;
  border: 1px solid rgba(88, 78, 67, 0.08);
  border-radius: 22px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.72);
  text-align: left;
  cursor: pointer;
}

.shape-card:hover {
  border-color: rgba(194, 169, 120, 0.18);
}

.shape-card.active {
  border-color: rgba(127, 157, 135, 0.36);
  box-shadow: 0 0 0 4px rgba(127, 157, 135, 0.1);
}

.shape-card-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  background: #fff;
}

.shape-card-body {
  padding: 12px;
}

.shape-card-title {
  font-family: var(--font-heading);
  font-size: 1rem;
  font-weight: 800;
  color: var(--text-main);
  margin-bottom: 4px;
}

.shape-card-note {
  line-height: 1.45;
}

.goal-card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.goal-card {
  min-height: 78px;
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  border-radius: 18px;
  border: 1px solid rgba(88, 78, 67, 0.1);
  background: rgba(255, 255, 255, 0.66);
  text-align: left;
  cursor: pointer;
}

.goal-card:hover {
  border-color: rgba(194, 169, 120, 0.22);
}

.goal-card.active {
  border-color: rgba(127, 157, 135, 0.34);
  background: linear-gradient(135deg, rgba(127, 157, 135, 0.16), rgba(194, 169, 120, 0.06));
}

.goal-card-icon {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(241, 233, 220, 0.96));
  color: var(--secondary);
  font-family: var(--font-heading);
  font-size: 0.82rem;
  font-weight: 800;
  flex-shrink: 0;
}

.goal-card-text {
  font-family: var(--font-heading);
  font-size: 0.92rem;
  font-weight: 800;
  line-height: 1.35;
  color: var(--text-main);
}

.injury-layout {
  display: grid;
  grid-template-columns: minmax(260px, 0.9fr) minmax(0, 1.1fr);
  gap: 14px;
}

.injury-intro-card,
.injury-control-card {
  padding: 18px;
}

.injury-parts-panel {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(88, 78, 67, 0.1);
}

.setup-error {
  margin-top: 16px;
  padding: 12px 14px;
  border: 1px solid rgba(201, 124, 122, 0.28);
  background: rgba(201, 124, 122, 0.1);
  color: #b56f6c;
  border-radius: 14px;
}

.setup-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
}

.setup-footer-spacer {
  flex: 1;
}

.setup-secondary,
.setup-submit {
  min-height: 46px;
  padding: 0 18px;
  border-radius: 14px;
  cursor: pointer;
  font-family: var(--font-heading);
  font-size: 0.92rem;
  font-weight: 800;
  letter-spacing: 0.06em;
}

.setup-secondary {
  border: 1px solid rgba(88, 78, 67, 0.12);
  background: rgba(255, 255, 255, 0.72);
  color: var(--text-main);
}

.setup-submit {
  min-width: 148px;
  border: 1px solid transparent;
  background: linear-gradient(135deg, #7f9d87, #97b19d);
  color: #111615;
}

.setup-submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 1180px) {
  .topbar-main {
    grid-template-columns: auto 1fr auto;
  }

  .topbar-intro {
    min-width: 0;
  }

  .topbar-page-portal {
    display: none;
  }

  .topbar-actions {
    justify-content: flex-end;
  }
}

@media (max-width: 980px) {
  .platform-shell {
    padding: 16px 18px 28px;
  }

  .topbar-context {
    display: none;
  }

  .desktop-nav {
    display: none;
  }

  .mobile-only {
    display: inline-flex;
  }

  .subline-description {
    white-space: normal;
  }

  .setup-progress,
  .setup-grid,
  .setup-shape-grid,
  .goal-card-grid,
  .injury-layout,
  .tag-grid {
    grid-template-columns: 1fr;
  }

  .setup-intro-card,
  .goal-header-card,
  .setup-header {
    flex-direction: column;
  }

  .setup-metric-preview {
    min-width: 0;
    grid-template-columns: 1fr;
  }
}

@media (min-width: 1181px) {
  :deep(.page-header) {
    display: none !important;
  }
}

@media (max-width: 720px) {
  .topbar-main {
    grid-template-columns: 1fr;
  }

  .content-host {
    padding: 0 4px 10px;
  }

  .topbar-actions {
    justify-content: flex-start;
  }

  .profile-pill {
    max-width: 100%;
  }

  .notification-panel {
    right: 12px;
    left: 12px;
    width: auto;
    max-width: none;
  }

  .profile-setup-overlay {
    padding: 12px;
  }

  .profile-setup-dialog {
    padding: 18px;
  }

  .setup-footer {
    flex-wrap: wrap;
  }

  .setup-footer-spacer {
    display: none;
  }

  .setup-secondary,
  .setup-submit {
    width: 100%;
  }
}

.platform-shell {
  position: relative;
  min-height: 100vh;
  padding: 28px 28px 40px;
}

.platform-shell > .topbar {
  display: none;
}

.platform-frame {
  position: relative;
  z-index: 2;
  display: grid;
  grid-template-columns: minmax(280px, 330px) minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.ambient-orb {
  position: fixed;
  border-radius: 999px;
  filter: blur(120px);
  pointer-events: none;
  opacity: 0.5;
  z-index: 0;
}

.orb-one {
  top: -70px;
  left: -120px;
  width: 380px;
  height: 380px;
  background: rgba(183, 154, 114, 0.12);
}

.orb-two {
  right: -90px;
  bottom: 5%;
  width: 420px;
  height: 420px;
  background: rgba(119, 133, 146, 0.12);
}

.editorial-rail,
.stage-header,
.content-paper,
.notification-panel,
.mobile-nav-panel,
.profile-setup-dialog {
  border: 1px solid var(--border);
  background: rgba(16, 18, 24, 0.78);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.28);
}

.editorial-rail {
  position: sticky;
  top: 28px;
  border-radius: 34px;
  padding: 28px 22px 24px;
}

.rail-brand {
  width: 100%;
  justify-content: flex-start;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 14px;
  border: none;
  background: transparent;
  color: var(--text-inverse);
  padding: 0;
}

.brand-mark {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(244, 238, 232, 0.14);
  border-radius: 14px;
  background: rgba(244, 238, 232, 0.08);
  color: var(--text-inverse);
  font-family: var(--font-heading);
  font-size: 0.92rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.brand-copy strong {
  color: var(--text-inverse);
  font-family: var(--font-heading);
  font-size: 1.14rem;
}

.brand-copy small {
  color: var(--text-inverse-muted);
  font-size: 0.72rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.rail-story {
  position: relative;
  margin-top: 28px;
  padding-top: 64px;
}

.rail-index {
  position: absolute;
  top: 0;
  left: 0;
  color: rgba(244, 238, 232, 0.12);
  font-family: var(--font-heading);
  font-size: clamp(4.6rem, 6vw, 6.2rem);
  line-height: 0.9;
}

.rail-title {
  margin-top: 8px;
  color: var(--text-inverse);
  font-size: clamp(2rem, 2.6vw, 3rem);
  line-height: 0.98;
}

.rail-copy {
  margin: 14px 0 0;
  color: var(--text-inverse-muted);
  line-height: 1.78;
}

.rail-ledger {
  display: grid;
  gap: 12px;
  margin-top: 28px;
}

.rail-ledger-card {
  padding: 14px 16px;
  border: 1px solid rgba(244, 238, 232, 0.1);
  border-radius: 20px;
  background: rgba(244, 238, 232, 0.04);
}

.rail-ledger-card span,
.rail-ledger-card strong {
  display: block;
}

.rail-ledger-card span {
  color: rgba(244, 238, 232, 0.52);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.rail-ledger-card strong {
  margin-top: 8px;
  color: var(--text-inverse);
  font-family: var(--font-heading);
  font-size: 1.08rem;
}

.rail-nav {
  margin-top: 28px;
}

.rail-group + .rail-group {
  margin-top: 18px;
}

.rail-group-label {
  margin: 0 0 10px;
  color: rgba(244, 238, 232, 0.44);
  font-size: 0.7rem;
  font-weight: 700;
  letter-spacing: 0.22em;
  text-transform: uppercase;
}

.rail-link {
  width: 100%;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 20px;
  background: transparent;
  color: var(--text-inverse);
  text-align: left;
}

.rail-link + .rail-link {
  margin-top: 8px;
}

.rail-link:hover,
.rail-link.router-link-active {
  border-color: rgba(244, 238, 232, 0.12);
  background: rgba(244, 238, 232, 0.06);
}

.rail-link-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  border: 1px solid rgba(244, 238, 232, 0.1);
  background: rgba(244, 238, 232, 0.05);
  color: var(--primary);
}

.rail-link-icon svg,
.page-caption svg,
.notification-code svg,
.floating-code svg {
  width: 18px;
  height: 18px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.rail-link-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rail-link-copy strong {
  color: var(--text-inverse);
  font-size: 0.92rem;
}

.rail-link-copy small {
  color: var(--text-inverse-muted);
  font-size: 0.76rem;
  line-height: 1.5;
}

.editorial-stage {
  display: grid;
  gap: 20px;
}

.stage-header {
  position: relative;
  z-index: 2;
  border-radius: 34px;
  padding: 22px 24px;
}

.stage-header-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.mobile-brand {
  display: none;
}

.topbar-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.action-btn,
.profile-pill {
  min-height: 44px;
  border: 1px solid rgba(244, 238, 232, 0.1);
  border-radius: 999px;
  background: rgba(244, 238, 232, 0.05);
  color: var(--text-inverse);
}

.action-btn {
  position: relative;
  padding: 0 15px;
  transition: background 0.2s ease, border-color 0.2s ease;
}

.action-btn:hover,
.profile-pill:hover {
  border-color: rgba(244, 238, 232, 0.18);
  background: rgba(244, 238, 232, 0.1);
}

.subtle-btn {
  color: var(--text-inverse-muted);
}

.count-badge {
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--bg-panel-strong);
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 0.72rem;
  font-weight: 800;
}

.profile-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px 0 6px;
}

.profile-initial {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--bg-panel-strong);
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 0.78rem;
  font-weight: 800;
}

.profile-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.05;
}

.profile-copy strong {
  color: var(--text-inverse);
  font-size: 0.84rem;
}

.profile-copy small {
  color: var(--text-inverse-muted);
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.stage-header-meta {
  display: grid;
  grid-template-columns: minmax(260px, 360px) minmax(0, 1fr);
  gap: 20px;
  align-items: start;
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid rgba(244, 238, 232, 0.08);
}

.page-caption {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.page-caption-label {
  color: rgba(244, 238, 232, 0.5);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.page-caption strong {
  color: var(--text-inverse);
  font-family: var(--font-heading);
  font-size: 1.5rem;
}

.page-caption small {
  color: var(--text-inverse-muted);
  line-height: 1.6;
}

.topbar-page-portal {
  min-width: 0;
}

#topbar-page-header {
  width: 100%;
  min-width: 0;
}

:deep(.topbar-page-shell) {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 14px;
  width: 100%;
}

:deep(.topbar-page-copy) {
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
}

:deep(.topbar-page-kicker) {
  color: rgba(244, 238, 232, 0.44);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

:deep(.topbar-page-title) {
  color: var(--text-inverse);
  font-family: var(--font-heading);
  font-size: 1rem;
  font-weight: 700;
}

:deep(.topbar-page-meta) {
  color: var(--text-inverse-muted);
  font-size: 0.78rem;
}

:deep(.topbar-page-actions) {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.content-host {
  position: relative;
  z-index: 1;
  margin-top: 24px;
}

.content-paper {
  position: relative;
  overflow: hidden;
  border-radius: 40px;
  padding: 28px;
}

.content-paper::before {
  content: "";
  position: absolute;
  inset: 18px;
  border: 1px solid rgba(244, 238, 232, 0.08);
  border-radius: 28px;
  pointer-events: none;
}

.content-body {
  margin-top: 0;
}

.notification-panel {
  position: fixed;
  top: 110px;
  right: 32px;
  z-index: 35;
  width: 420px;
  max-width: calc(100vw - 32px);
  border-radius: 28px;
}

.panel-header {
  border-bottom-color: rgba(244, 238, 232, 0.08);
}

.panel-header strong,
.panel-state,
.notification-copy strong {
  color: var(--text-inverse);
}

.notification-copy small,
.notification-copy em {
  color: var(--text-inverse-muted);
}

.notification-item:hover,
.notification-item.unread {
  border-color: rgba(244, 238, 232, 0.1);
  background: rgba(244, 238, 232, 0.06);
}

.mobile-only {
  display: none;
}

.mobile-nav-mask {
  background: rgba(5, 7, 12, 0.76);
}

.mobile-nav-panel {
  border-left: 1px solid rgba(244, 238, 232, 0.08);
  background: rgba(16, 18, 24, 0.96);
}

.mobile-nav-head strong,
.floating-copy strong,
.mobile-nav-link .floating-copy strong {
  color: var(--text-inverse);
}

.floating-copy small,
.mobile-nav-link .floating-copy small {
  color: var(--text-inverse-muted);
}

.mobile-nav-link:hover,
.mobile-nav-link.router-link-active {
  border-color: rgba(244, 238, 232, 0.1);
  background: rgba(244, 238, 232, 0.06);
}

.profile-setup-overlay {
  background: rgba(7, 8, 12, 0.82);
}

.profile-setup-dialog {
  background: rgba(16, 18, 24, 0.96);
  border-radius: 36px;
}

.setup-title,
.setup-intro-card h3,
.goal-header-card h3,
.injury-intro-card h3,
.shape-card-title,
.goal-card-text,
.metric-chip strong,
.goal-selection-summary {
  color: var(--text-inverse);
}

.setup-header-copy,
.setup-intro-card p,
.goal-header-card p,
.injury-intro-card p,
.shape-card-note,
.setup-error,
.metric-chip span {
  color: var(--text-inverse-muted);
}

.setup-intro-card,
.goal-header-card,
.injury-intro-card,
.injury-control-card,
.shape-card,
.goal-card,
.setup-progress-step,
.metric-chip,
.setup-input,
.segmented-control button,
.tag-grid button {
  border-color: rgba(244, 238, 232, 0.08);
  background: rgba(244, 238, 232, 0.05);
  color: var(--text-inverse);
  box-shadow: none;
}

.setup-input {
  color: var(--text-inverse);
}

.setup-input:focus {
  border-color: rgba(244, 238, 232, 0.18);
  box-shadow: 0 0 0 4px rgba(244, 238, 232, 0.06);
}

.setup-step-badge,
.setup-progress-index {
  background: rgba(244, 238, 232, 0.08);
  color: var(--text-inverse);
}

.setup-submit {
  background: var(--bg-panel-strong);
  color: var(--text-main);
}

.setup-secondary {
  border-color: rgba(244, 238, 232, 0.1);
  background: rgba(244, 238, 232, 0.05);
  color: var(--text-inverse);
}

@media (max-width: 1180px) {
  .platform-frame {
    grid-template-columns: 1fr;
  }

  .desktop-rail {
    display: none;
  }

  .mobile-brand {
    display: inline-flex;
  }

  .mobile-only {
    display: inline-flex;
  }

  .stage-header-meta {
    grid-template-columns: 1fr;
  }

  .page-caption {
    max-width: 100%;
  }
}

@media (max-width: 768px) {
  .platform-shell {
    padding: 16px 14px 26px;
  }

  .stage-header,
  .content-paper {
    padding: 18px;
    border-radius: 28px;
  }

  .stage-header-main,
  .topbar-actions {
    justify-content: flex-start;
  }

  .notification-panel {
    right: 14px;
    left: 14px;
    width: auto;
    max-width: none;
  }
}

.ambient-orb {
  display: none;
}

.platform-frame {
  gap: 0;
  border: 1px solid rgba(244, 238, 232, 0.1);
  background: #0f1117;
}

.editorial-rail,
.stage-header,
.notification-panel,
.mobile-nav-panel,
.profile-setup-dialog {
  background: #0f1117;
  border-color: rgba(244, 238, 232, 0.1);
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  box-shadow: none;
}

.editorial-rail {
  top: 0;
  min-height: 100vh;
  border-right: 1px solid rgba(244, 238, 232, 0.08);
  border-radius: 0;
  padding: 32px 24px;
}

.stage-header {
  border-radius: 0;
  border-bottom: 1px solid rgba(244, 238, 232, 0.08);
  padding: 22px 28px 24px;
}

.stage-header-meta {
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
}

.brand-mark,
.rail-link-icon,
.count-badge,
.profile-initial {
  border-radius: 0;
}

.brand-mark,
.rail-link-icon {
  background: transparent;
}

.rail-ledger-card,
.rail-link,
.action-btn,
.profile-pill {
  border-radius: 0;
  background: transparent;
}

.rail-ledger-card {
  border-left: 3px solid var(--primary);
}

.rail-link {
  border-bottom: 1px solid rgba(244, 238, 232, 0.08);
  padding-left: 0;
  padding-right: 0;
}

.rail-link:hover,
.rail-link.router-link-active {
  border-color: rgba(244, 238, 232, 0.18);
  background: transparent;
}

.rail-link.router-link-active .rail-link-copy strong {
  color: var(--bg-panel-strong);
}

.page-caption strong,
.page-caption small,
:deep(.topbar-page-title),
:deep(.topbar-page-meta) {
  max-width: 100%;
}

.content-host {
  margin-top: 0;
}

.content-paper {
  border-radius: 0;
  background:
    linear-gradient(90deg, transparent 0, transparent calc(100% - 320px), rgba(17, 17, 17, 0.04) calc(100% - 320px), rgba(17, 17, 17, 0.04) 100%),
    var(--bg-panel-strong);
  color: var(--text-main);
  box-shadow: none;
}

.content-paper::before {
  inset: 0;
  border: none;
  border-top: 10px solid #0f1117;
  border-bottom: 10px solid #0f1117;
  border-radius: 0;
}

.setup-title,
.setup-intro-card h3,
.goal-header-card h3,
.injury-intro-card h3 {
  color: var(--text-main);
}

.notification-panel {
  border-radius: 0;
}

.mobile-nav-panel,
.profile-setup-dialog {
  border-radius: 0;
}

.profile-setup-overlay {
  background: rgba(6, 7, 10, 0.86);
}

.setup-intro-card,
.goal-header-card,
.injury-intro-card,
.injury-control-card,
.shape-card,
.goal-card,
.setup-progress-step,
.metric-chip,
.setup-input,
.segmented-control button,
.tag-grid button {
  border-radius: 0;
}

@media (max-width: 1180px) {
  .platform-frame {
    border-left: none;
    border-right: none;
  }

  .stage-header {
    padding-left: 18px;
    padding-right: 18px;
  }
}

@media (max-width: 768px) {
  .content-paper {
    padding: 18px 16px 22px;
  }

}

.platform-frame {
  display: none;
}

.platform-shell > .topbar {
  display: block;
  position: fixed;
  top: 20px;
  left: 28px;
  right: 28px;
  z-index: 40;
  border: 1px solid rgba(244, 238, 232, 0.1);
  background: #0f1117;
}

.topbar-main {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  gap: 18px;
  align-items: center;
  padding: 18px 22px;
}

.topbar-intro {
  min-width: 0;
}

.top-nav {
  display: flex;
  align-items: center;
  gap: 0;
  min-width: 0;
  overflow-x: auto;
}

.top-nav-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 0 14px;
  border: none;
  border-bottom: 1px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--text-inverse-muted);
  white-space: nowrap;
}

.top-nav-link:hover,
.top-nav-link.router-link-active,
.more-trigger:hover {
  color: var(--bg-panel-strong);
  border-bottom-color: rgba(244, 238, 232, 0.4);
  background: transparent;
}

.top-nav-code {
  width: 22px;
  height: 22px;
  display: grid;
  place-items: center;
  color: var(--primary);
}

.top-nav-code svg {
  width: 15px;
  height: 15px;
  stroke: currentColor;
  fill: none;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.top-nav-label {
  font-size: 0.8rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.topbar-page-portal {
  min-width: 0;
}

#topbar-page-header {
  min-width: 0;
}

.floating-menu {
  right: 0;
  border-radius: 0;
  border: 1px solid rgba(244, 238, 232, 0.1);
  background: #0f1117;
}

.floating-link {
  border-radius: 0;
}

.content-host {
  margin-top: 126px;
}

.notification-panel {
  top: 92px;
}

@media (max-width: 1180px) {
  .platform-shell > .topbar {
    left: 18px;
    right: 18px;
  }

  .topbar-main {
    grid-template-columns: auto 1fr auto;
  }

  .topbar-page-portal {
    display: none;
  }
}

@media (max-width: 980px) {
  .platform-shell > .topbar {
    top: 12px;
  }

  .desktop-nav {
    display: none;
  }

  .mobile-only {
    display: inline-flex;
  }

  .topbar-main {
    grid-template-columns: 1fr auto;
  }
}

@media (max-width: 768px) {
  .platform-shell > .topbar {
    left: 14px;
    right: 14px;
  }

  .topbar-main {
    padding: 14px 16px;
  }

  .content-host {
    margin-top: 110px;
  }
}

.platform-shell {
  padding-top: 0;
}

.platform-shell > .topbar {
  top: 0;
  left: 0;
  right: 0;
  border: none;
  border-bottom: 1px solid rgba(17, 17, 17, 0.12);
  border-radius: 0 !important;
  background: var(--bg-panel-strong);
  box-shadow: none;
}

.topbar::before,
.topbar::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  height: 1px;
  background: rgba(17, 17, 17, 0.08);
  pointer-events: none;
}

.topbar::before {
  top: 62px;
}

.topbar::after {
  bottom: 0;
}

.topbar-main {
  grid-template-columns: 260px minmax(0, 1fr) minmax(280px, auto) auto;
  gap: 0;
  padding: 0 24px;
  min-height: 62px;
  background: var(--bg-panel-strong);
}

.topbar .brand-copy strong,
.topbar .profile-copy strong,
.topbar :deep(.topbar-page-title) {
  color: var(--text-main);
}

.topbar .brand-copy small,
.topbar .profile-copy small,
.topbar :deep(.topbar-page-kicker),
.topbar :deep(.topbar-page-meta),
.topbar .page-caption-label,
.topbar .page-caption small {
  color: var(--text-secondary);
}

.topbar .page-caption strong {
  color: var(--text-main);
}

.topbar-intro,
.topbar-page-portal,
.topbar-actions {
  min-height: 62px;
  display: flex;
  align-items: center;
}

.topbar-intro {
  padding-right: 20px;
  border-right: 1px solid rgba(17, 17, 17, 0.12);
  background: var(--bg-panel-strong);
}

.top-nav {
  padding: 0 10px;
  border-right: 1px solid rgba(17, 17, 17, 0.12);
  overflow-x: auto;
  scrollbar-width: none;
  background: var(--bg-panel-strong);
}

.top-nav::-webkit-scrollbar {
  display: none;
}

.top-nav-link {
  min-height: 62px;
  padding: 0 16px;
  border-bottom: none;
  border-right: 1px solid rgba(17, 17, 17, 0.08);
  color: var(--text-secondary);
  background: var(--bg-panel-strong);
}

.top-nav-link:first-child {
  border-left: 1px solid rgba(17, 17, 17, 0.08);
}

.top-nav-link:hover,
.top-nav-link.router-link-active,
.more-trigger:hover {
  color: var(--text-main);
  background: rgba(17, 17, 17, 0.04);
  border-bottom-color: transparent;
}

.top-nav-code {
  width: 18px;
  height: 18px;
}

.top-nav-label {
  font-size: 0.74rem;
  letter-spacing: 0.16em;
}

.topbar-page-portal {
  padding: 0 18px;
  border-right: 1px solid rgba(17, 17, 17, 0.12);
  background: var(--bg-panel-strong);
}

.topbar-actions {
  gap: 0;
  justify-content: flex-end;
  background: var(--bg-panel-strong);
}

.action-btn,
.profile-pill {
  min-height: 62px;
  padding: 0 16px;
  border: none;
  border-left: 1px solid rgba(17, 17, 17, 0.08);
  border-radius: 0;
  background: var(--bg-panel-strong);
  color: var(--text-main);
}

.action-btn:hover,
.profile-pill:hover {
  background: rgba(17, 17, 17, 0.04);
  border-color: rgba(17, 17, 17, 0.08);
}

.profile-pill {
  gap: 12px;
  padding-right: 18px;
}

.profile-initial {
  width: 28px;
  height: 28px;
  border: 1px solid rgba(17, 17, 17, 0.16);
  border-radius: 0;
  background: var(--bg-panel-strong);
  color: var(--text-main);
}

.count-badge {
  border-radius: 0;
  background: var(--bg-panel-strong);
  color: var(--primary);
  border: 1px solid rgba(17, 17, 17, 0.16);
}

.floating-menu {
  top: calc(100% + 1px);
  width: 380px;
  padding: 0;
  border-radius: 0;
  border: 1px solid rgba(17, 17, 17, 0.12);
  background: var(--bg-panel-strong);
  box-shadow: none;
}

.floating-group + .floating-group {
  margin-top: 0;
  border-top: 1px solid rgba(17, 17, 17, 0.08);
}

.floating-group-label {
  margin: 0;
  padding: 12px 14px 8px;
}

.floating-link {
  padding: 12px 14px;
  border-top: 1px solid rgba(17, 17, 17, 0.06);
}

.floating-link:hover,
.floating-link.router-link-active {
  background: rgba(17, 17, 17, 0.04);
}

.content-host {
  margin-top: 63px;
}

.notification-panel {
  top: 64px;
  right: 0;
  border-top: none;
}

@media (max-width: 1180px) {
  .platform-shell > .topbar {
    left: 0;
    right: 0;
  }

  .topbar-main {
    grid-template-columns: 240px minmax(0, 1fr) auto;
    padding: 0 18px;
  }
}

@media (max-width: 980px) {
  .platform-shell > .topbar {
    top: 0;
  }

  .topbar::before {
    top: 58px;
  }

  .topbar-main {
    grid-template-columns: 1fr auto;
    min-height: 58px;
    padding: 0 14px;
  }

  .topbar-intro,
  .topbar-actions,
  .action-btn,
  .profile-pill {
    min-height: 58px;
  }

  .topbar-intro {
    border-right: none;
    padding-right: 0;
  }

  .action-btn,
  .profile-pill {
    padding: 0 14px;
  }

  .content-host {
    margin-top: 59px;
  }
}

@media (max-width: 768px) {
  .platform-shell > .topbar {
    left: 0;
    right: 0;
  }

  .topbar-main {
    padding: 0 10px;
  }

  .content-host {
    margin-top: 59px;
  }
}

/* ── 天气行内嵌入（无卡片、无边框）────────────────────────────────────────── */
.weather-inline {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 2px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  line-height: 1;
  transition: color 0.18s ease;
  white-space: nowrap;
}

.weather-inline:hover {
  color: var(--text-main);
}

.weather-inline-icon {
  font-size: 1rem;
  line-height: 1;
  flex-shrink: 0;
}

.weather-inline-text {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.weather-inline-temp {
  font-family: var(--font-heading);
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: -0.02em;
}

.weather-inline-meta {
  font-size: 0.72rem;
  color: var(--text-secondary);
}

.weather-inline-spin {
  display: inline-block;
  width: 13px;
  height: 13px;
  border: 1.5px solid rgba(79, 73, 65, 0.15);
  border-top-color: rgba(79, 73, 65, 0.4);
  border-radius: 50%;
  animation: weather-spin 0.75s linear infinite;
}

@keyframes weather-spin {
  to { transform: rotate(360deg); }
}

/* 天气与操作按钮之间的细分隔线 */
.weather-divider {
  display: inline-block;
  width: 1px;
  height: 15px;
  background: rgba(79, 73, 65, 0.14);
  flex-shrink: 0;
}

/* 小屏响应式 */
@media (max-width: 900px) {
  .weather-inline-meta {
    display: none;
  }
}

@media (max-width: 640px) {
  .weather-inline-text {
    display: none;
  }
  .weather-divider {
    display: none;
  }
}

</style>
