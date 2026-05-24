<template>
  <div class="achievements-content">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">成就系统</span>
          <strong class="topbar-page-title">里程碑</strong>
          <span class="topbar-page-meta">已达成 {{ unlockedCount }} / {{ achievements.length }}</span>
        </div>
        <div class="topbar-page-actions">
          <span class="topbar-page-title">{{ achievementPercent }}%</span>
        </div>
      </div>
    </Teleport>

    <div class="achievement-summary nd-card mb-2xl">
      <div>
        <div class="text-label mb-xs">[ 成就系统 ]</div>
        <div class="text-caption text-secondary">已达成 {{ unlockedCount }} / {{ achievements.length }}</div>
      </div>
      <strong class="summary-value">{{ achievementPercent }}%</strong>
    </div>

    <div class="filter-tabs mb-2xl">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="loading" class="loading-state">
      <span class="status-dot blink"></span>
      <span class="text-caption ml-sm">加载成就数据中...</span>
    </div>

    <div v-else-if="filteredAchievements.length === 0" class="empty-state nd-card">
      <div class="text-label mb-sm">[ 暂无成就 ]</div>
      <div class="text-caption text-secondary">当前分类下还没有可展示的成就。</div>
    </div>

    <div v-else class="achievements-grid">
      <div
        v-for="achievement in filteredAchievements"
        :key="achievement.id"
        class="achievement-card nd-card"
        :class="{
          unlocked: achievement.unlocked,
          locked: !achievement.unlocked,
          'rare-shadow': achievement.rarity === 'RARE',
          'epic-shadow': achievement.rarity === 'EPIC',
          'legendary-shadow': achievement.rarity === 'LEGENDARY'
        }"
      >
        <div class="card-top">
          <div class="achievement-icon" :class="`icon-${achievement.rarity.toLowerCase()}`">
            <svg class="achievement-svg" viewBox="0 0 24 24" aria-hidden="true">
              <path
                v-for="(segment, index) in getAchievementIconPaths(achievement.icon, achievement.category)"
                :key="`${achievement.id}-${index}`"
                :d="segment"
              />
            </svg>
          </div>
          <div class="rarity-badge" :class="`badge-${achievement.rarity.toLowerCase()}`">
            {{ rarityMap[achievement.rarity] || achievement.rarity }}
          </div>
        </div>

        <div class="card-body">
          <h3 class="text-heading achievement-name" :class="{ 'text-secondary': !achievement.unlocked }">
            {{ achievement.name }}
          </h3>
          <p class="text-caption text-secondary mt-xs">{{ achievement.description }}</p>
        </div>

        <div class="card-bottom mt-md">
          <div v-if="achievement.unlocked" class="unlock-info">
            <span class="status-badge unlocked-badge">已解锁</span>
            <span class="text-caption achievement-date">{{ achievement.date || '已达成' }}</span>
          </div>
          <div v-else class="progress-wrap">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: `${getProgressPercent(achievement)}%` }"></div>
            </div>
            <span class="text-caption progress-label">{{ achievement.progress }} / {{ achievement.target }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="recentUnlocks.length > 0" class="nd-card recent-section mt-3xl">
      <div class="text-label mb-md">[ 最近达成 ]</div>
      <div class="recent-list">
        <div v-for="achievement in recentUnlocks" :key="achievement.id" class="recent-item">
          <span class="recent-icon" aria-hidden="true">
            <svg class="achievement-svg recent-svg" viewBox="0 0 24 24">
              <path
                v-for="(segment, index) in getAchievementIconPaths(achievement.icon, achievement.category)"
                :key="`recent-${achievement.id}-${index}`"
                :d="segment"
              />
            </svg>
          </span>
          <div>
            <div class="text-caption text-primary">{{ achievement.name }}</div>
            <div class="text-caption achievement-date">{{ achievement.date }}</div>
          </div>
          <span class="rarity-badge" :class="`badge-${achievement.rarity.toLowerCase()}`">
            {{ rarityMap[achievement.rarity] || achievement.rarity }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getMyAchievements } from '../api/achievement'

type AchievementItem = {
  id: number
  icon?: string
  name: string
  description: string
  category: string
  rarity: string
  target: number
  progress: number
  unlocked: boolean
  date?: string | null
}

const activeTab = ref('all')
const loading = ref(false)
const achievements = ref<AchievementItem[]>([])

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'unlocked', label: '已达成' },
  { key: 'locked', label: '未达成' },
  { key: 'training', label: '训练' },
  { key: 'nutrition', label: '营养' },
  { key: 'social', label: '社交' }
]

const rarityMap: Record<string, string> = {
  COMMON: '普通',
  RARE: '稀有',
  EPIC: '史诗',
  LEGENDARY: '传说'
}

const achievementIconPaths: Record<string, string[]> = {
  '\uD83D\uDD25': ['M13.4 3.8c.6 2-1.1 3.4-.5 5.3.3 1 1.3 1.8 2 2.6.9 1 1.6 2.2 1.6 3.8A4.5 4.5 0 0 1 12 20a4.7 4.7 0 0 1-4.8-4.7c0-3 2.2-4.9 3.8-6.8.8-.9 1.6-2 2.4-4.7Z'],
  '\u26A1': ['M13 2 5 13h5l-1 9 8-11h-5l1-9Z'],
  '\uD83D\uDC8E': ['M7 4h10l4 5-9 11L3 9l4-5Z', 'M7 4 12 20 17 4M3 9h18M9.5 9 12 4l2.5 5'],
  '\uD83D\uDC51': ['m4 18 1.6-9 4.4 4 2-6 2 6 4.4-4L20 18H4Z', 'M4 18h16M7 6.5h.01M12 5h.01M17 6.5h.01'],
  '\uD83D\uDDE1': ['m14.5 4.5 5 5-7.5 7.5-2.5.5.5-2.5 7.5-7.5Z', 'M13.5 5.5 18.5 10.5M5 19l5-5M4 20h4'],
  '\uD83C\uDFCB': ['M3 10v4M6 8v8M9 10v4M15 10v4M18 8v8M21 10v4', 'M6 12h12M12 8.2a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z', 'M10 20v-5l-2-2M14 20v-5l2-2M10 10l2 2 2-2'],
  '\uD83E\uDD57': ['M4 14a8 8 0 0 0 16 0H4Z', 'M8 13c.4-2 1.8-3.8 4-5 1 1.2 1.7 2.5 2 4M9 9c-.8-.7-1.1-1.8-.8-3 .9.2 1.7.7 2.2 1.5M15.5 8.5c.8-.5 1.8-.7 2.8-.4-.1 1.1-.7 2-1.6 2.6'],
  '\uD83E\uDD69': ['M14.5 5.5c2.5 0 4.5 2 4.5 4.5 0 3.8-3.8 8-8.3 8-2.8 0-5.2-2-5.2-4.8 0-4 3.7-7.7 9-7.7Z', 'M13 10.5a1.7 1.7 0 1 0 0 3.4 1.7 1.7 0 0 0 0-3.4Z'],
  '\uD83C\uDF3F': ['M12 19c0-5.5 2.6-9 7.5-11-1 5.8-3.8 9.2-7.5 11ZM12 19c0-4.8-2.4-7.9-7-9.8.7 5.2 3.3 8.3 7 9.8ZM12 19V8'],
  '\uD83D\uDCE1': ['M12 20a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3Z', 'M8.5 15.5a5 5 0 0 1 7 0M5.5 12.5a9 9 0 0 1 13 0M2.5 9.5a13 13 0 0 1 19 0'],
  '\u2764': ['M12 20s-7-4.4-7-10.1A4 4 0 0 1 12 7a4 4 0 0 1 7 2.9C19 15.6 12 20 12 20Z'],
  '\uD83C\uDF10': ['M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z', 'M3.5 12h17M12 3c2.6 2.4 4 5.4 4 9s-1.4 6.6-4 9c-2.6-2.4-4-5.4-4-9s1.4-6.6 4-9Z']
}

const categoryIconFallbacks: Record<string, string[]> = {
  training: achievementIconPaths['\uD83D\uDD25'],
  nutrition: achievementIconPaths['\uD83C\uDF3F'],
  social: achievementIconPaths['\uD83D\uDCE1']
}

const getAchievementIconPaths = (icon?: string, category?: string) => {
  const normalized = (icon || '').replace(/\uFE0F/g, '')
  return achievementIconPaths[normalized] || categoryIconFallbacks[category || ''] || achievementIconPaths['\uD83D\uDD25']
}

const getProgressPercent = (achievement: AchievementItem) => {
  if (!achievement.target) return 0
  return Math.min(100, Math.max(0, (achievement.progress / achievement.target) * 100))
}

const fetchAchievements = async () => {
  loading.value = true
  try {
    const data = await getMyAchievements()
    achievements.value = Array.isArray(data) ? (data as AchievementItem[]) : []
  } catch (error) {
    console.error('Failed to fetch achievements:', error)
    achievements.value = []
  } finally {
    loading.value = false
  }
}

const filteredAchievements = computed(() => {
  if (activeTab.value === 'all') return achievements.value
  if (activeTab.value === 'unlocked') return achievements.value.filter((item) => item.unlocked)
  if (activeTab.value === 'locked') return achievements.value.filter((item) => !item.unlocked)
  return achievements.value.filter((item) => item.category === activeTab.value)
})

const unlockedCount = computed(() => achievements.value.filter((item) => item.unlocked).length)
const achievementPercent = computed(() => {
  if (achievements.value.length === 0) return 0
  return Math.round((unlockedCount.value / achievements.value.length) * 100)
})

const recentUnlocks = computed(() =>
  achievements.value
    .filter((item) => item.unlocked && item.date)
    .sort((a, b) => (b.date || '').localeCompare(a.date || ''))
    .slice(0, 3)
)

onMounted(() => {
  fetchAchievements()
})
</script>

<style scoped>
.achievements-content {
  width: 100%;
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
}

.empty-state {
  padding: 32px;
  text-align: center;
}

.status-dot {
  width: 8px;
  height: 8px;
  display: inline-block;
  border-radius: 999px;
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

.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.achievement-summary {
  display: none;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
}

.summary-value {
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 1.1rem;
  letter-spacing: 0.06em;
}

.tab-btn {
  min-height: 38px;
  padding: 0 16px;
  border: 1px solid rgba(127, 157, 135, 0.14);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.58);
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-size: 0.8rem;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease;
}

.tab-btn:hover {
  border-color: rgba(127, 157, 135, 0.26);
  background: rgba(127, 157, 135, 0.08);
  color: var(--text-main);
}

.tab-btn.active {
  border-color: transparent;
  background: linear-gradient(135deg, #7f9d87, #97b19d);
  color: #fffdf9;
}

.achievements-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
}

.achievement-card {
  position: relative;
  overflow: hidden;
  padding: 20px;
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
}

.achievement-card:hover {
  transform: translateY(-2px);
}

.achievement-card.locked {
  opacity: 0.62;
  filter: grayscale(0.18);
}

.achievement-card.rare-shadow {
  box-shadow: 0 18px 34px rgba(59, 130, 246, 0.14);
}

.achievement-card.epic-shadow {
  box-shadow: 0 18px 34px rgba(168, 85, 247, 0.16);
}

.achievement-card.legendary-shadow {
  box-shadow: 0 18px 34px rgba(234, 179, 8, 0.18);
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.achievement-icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: rgba(127, 157, 135, 0.12);
  color: #f59e0b;
}

.achievement-svg {
  width: 30px;
  height: 30px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.recent-svg {
  width: 22px;
  height: 22px;
}

.icon-common {
  background: rgba(245, 158, 11, 0.14);
  color: #f59e0b;
}

.icon-rare {
  background: rgba(59, 130, 246, 0.14);
  color: #60a5fa;
}

.icon-epic {
  background: rgba(168, 85, 247, 0.14);
  color: #c084fc;
}

.icon-legendary {
  background: rgba(234, 179, 8, 0.16);
  box-shadow: 0 0 20px rgba(234, 179, 8, 0.2);
  color: #fbbf24;
}

.rarity-badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-family: var(--font-heading);
  font-size: 0.68rem;
  letter-spacing: 0.12em;
}

.badge-common {
  background: rgba(156, 163, 175, 0.18);
  color: #7b8794;
}

.badge-rare {
  background: rgba(59, 130, 246, 0.16);
  color: #5aa2ff;
}

.badge-epic {
  background: rgba(168, 85, 247, 0.16);
  color: #b77cff;
}

.badge-legendary {
  background: rgba(234, 179, 8, 0.18);
  color: #d99a08;
}

.achievement-name {
  font-size: 1rem;
  letter-spacing: 0.04em;
}

.progress-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.progress-bar {
  flex: 1;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(127, 157, 135, 0.12);
}

.progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #d69d3f, #f0c96e);
}

.progress-label {
  white-space: nowrap;
  font-size: 0.72rem;
}

.status-badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-family: var(--font-heading);
  font-size: 0.72rem;
  letter-spacing: 0.08em;
}

.unlocked-badge {
  background: rgba(34, 197, 94, 0.14);
  color: #1f9d55;
}

.unlock-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.achievement-date {
  font-size: 0.72rem;
  opacity: 0.72;
}

.recent-section {
  padding: 20px;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recent-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 18px;
  background: rgba(127, 157, 135, 0.06);
}

.recent-icon {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
}

.recent-item > div {
  flex: 1;
  min-width: 0;
}

@media (max-width: 1024px) {
  .achievements-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1180px) {
  .achievement-summary {
    display: flex;
  }
}

@media (max-width: 640px) {
  .achievements-grid {
    grid-template-columns: 1fr;
  }
}
</style>
