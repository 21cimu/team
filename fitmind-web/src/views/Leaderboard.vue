<template>
  <div class="leaderboard-content">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">排行榜</span>
          <strong class="topbar-page-title">{{ currentCategory.heroTitle }}</strong>
          <span class="topbar-page-meta">更新时间 {{ lastUpdate }}</span>
        </div>
        <div class="topbar-page-actions period-selector">
          <button
            v-for="period in periods"
            :key="period.key"
            class="period-btn"
            :class="{ active: activePeriod === period.key }"
            @click="activePeriod = period.key"
          >
            {{ period.label }}
          </button>
        </div>
      </div>
    </Teleport>

    <section class="leaderboard-hero">
      <div class="hero-copy">
        <div class="text-label">[ Ranking Board ]</div>
        <div class="hero-index">07</div>
        <h1 class="text-display-md hero-title">{{ currentCategory.headline }}</h1>
        <p class="hero-description">{{ currentCategory.description }}</p>
        <div class="hero-actions">
          <button class="nd-btn primary" @click="scrollToTable">查看完整榜单</button>
          <button class="nd-btn" @click="router.push('/app/community')">进入社区</button>
        </div>
      </div>

      <div class="nd-card summary-panel">
        <div class="summary-panel-header">
          <div>
            <div class="text-label">[ Summary ]</div>
            <h2>{{ currentPeriod.label }}概览</h2>
          </div>
          <span class="text-caption">{{ currentCategory.scoreLabel }}</span>
        </div>

        <div class="summary-grid">
          <article v-for="item in summaryCards" :key="item.label" class="summary-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>

        <div class="summary-footnote">
          <span>{{ currentCategory.footnote }}</span>
          <strong>{{ currentCategory.scoreLabel }}</strong>
        </div>
      </div>
    </section>

    <section class="nd-card controls-card">
      <div class="controls-header">
        <div>
          <div class="text-label">[ Category ]</div>
          <h2>切换比较维度</h2>
        </div>
        <span class="text-caption">当前显示 {{ rankedList.length }} 位成员</span>
      </div>

      <div class="category-tabs">
        <button
          v-for="category in categories"
          :key="category.key"
          class="cat-btn"
          :class="{ active: activeCategory === category.key }"
          @click="activeCategory = category.key"
        >
          <span class="cat-icon">{{ category.icon }}</span>
          <span class="cat-copy">
            <strong>{{ category.label }}</strong>
            <small>{{ category.short }}</small>
          </span>
        </button>
      </div>

      <div class="controls-toolbar">
        <input
          v-model.trim="searchQuery"
          class="nd-input search-input"
          placeholder="定位某位成员"
        />
        <div class="scope-group">
          <button
            v-for="scope in scopes"
            :key="scope.key"
            class="scope-chip"
            :class="{ active: viewScope === scope.key }"
            @click="viewScope = scope.key"
          >
            {{ scope.label }}
          </button>
        </div>
      </div>
    </section>

    <div v-if="loading" class="loading-state">
      <span class="status-dot blink"></span>
      <span class="text-caption ml-sm">正在汇总排行榜数据...</span>
    </div>

    <div v-else class="leaderboard-layout">
      <div class="table-section">
        <section class="nd-card podium-card">
          <div class="section-header">
            <div>
              <div class="text-label">[ Podium ]</div>
              <h2>前三名</h2>
            </div>
            <span class="text-caption">{{ currentPeriod.label }}表现最强</span>
          </div>

          <div class="podium">
            <article v-for="winner in podiumWinners" :key="winner.rank" class="podium-item" :class="winner.positionClass">
              <div class="podium-topline">{{ winner.rankLabel }}</div>
              <div class="podium-avatar" :class="winner.positionClass">{{ getInitial(winner.username) }}</div>
              <strong class="podium-name">{{ winner.username }}</strong>
              <span class="podium-score">{{ formatScore(winner.score) }} {{ currentCategory.unit }}</span>
              <div class="podium-block">{{ winner.blockLabel }}</div>
            </article>
          </div>
        </section>

        <section ref="tableRef" class="nd-card rank-table-card">
          <div class="section-header">
            <div>
              <div class="text-label">[ Full Ranking ]</div>
              <h2>榜单明细</h2>
            </div>
            <span class="text-caption">{{ searchQuery ? `检索结果 ${tableRows.length} 条` : currentScopeLabel }}</span>
          </div>

          <div class="rank-table">
            <div class="rank-table-header">
              <span>排名</span>
              <span>成员</span>
              <span>{{ currentCategory.scoreLabel }}</span>
              <span>差距</span>
              <span>变动</span>
            </div>

            <div
              v-for="row in tableRows"
              :key="row.userId"
              class="rank-row"
              :class="{
                'is-me': row.isMe,
                highlighted: isSearchHit(row)
              }"
            >
              <div class="rank-col rank-number">
                <strong>#{{ row.rank }}</strong>
              </div>
              <div class="rank-col rank-user">
                <div class="user-avatar">{{ getInitial(row.username) }}</div>
                <div class="user-copy">
                  <strong>{{ row.username }}</strong>
                  <span>{{ row.isMe ? '当前账号' : rankLabel(row.rank) }}</span>
                </div>
              </div>
              <div class="rank-col rank-score">
                <strong>{{ formatScore(row.score) }}</strong>
                <span>{{ currentCategory.unit }}</span>
              </div>
              <div class="rank-col rank-gap">
                <strong>{{ formatGap(row) }}</strong>
                <span>{{ gapCaption(row) }}</span>
              </div>
              <div class="rank-col rank-change" :class="changeClass(row.change)">
                <strong>{{ formatChange(row.change) }}</strong>
                <span>{{ changeLabel(row.change) }}</span>
              </div>
            </div>

            <div v-if="tableRows.length === 0" class="table-empty">
              <div class="text-label">[ 暂无结果 ]</div>
              <p>当前筛选条件下没有匹配成员。</p>
            </div>
          </div>
        </section>

        <section v-if="myRank" class="nd-card my-rank-card">
          <div class="section-header">
            <div>
              <div class="text-label">[ My Position ]</div>
              <h2>我的排名</h2>
            </div>
            <span class="text-caption">位于前 {{ myPercentile }}%</span>
          </div>

          <div class="my-rank-grid">
            <div class="my-rank-main">
              <span>当前名次</span>
              <strong>#{{ myRank.rank }}</strong>
            </div>
            <div class="my-rank-main">
              <span>当前成绩</span>
              <strong>{{ formatScore(myRank.score) }}</strong>
            </div>
            <div class="my-rank-progress">
              <div class="progress-copy">
                <span>距前一名还差</span>
                <strong>{{ myRank.toNext }} {{ currentCategory.unit }}</strong>
              </div>
              <div class="progress-bar">
                <span :style="{ width: `${myRank.progressToNext}%` }"></span>
              </div>
            </div>
          </div>
        </section>
      </div>

      <aside class="sidebar-section">
        <section class="nd-card sidebar-card">
          <div class="section-header compact">
            <div>
              <div class="text-label">[ Metrics ]</div>
              <h2>榜单统计</h2>
            </div>
          </div>

          <div class="metric-list">
            <div class="metric-row">
              <span>参与成员</span>
              <strong>{{ stats.total }}</strong>
            </div>
            <div class="metric-row">
              <span>有效成绩</span>
              <strong>{{ stats.active }}</strong>
            </div>
            <div class="metric-row">
              <span>平均分</span>
              <strong>{{ formatScore(stats.averageScore) }}</strong>
            </div>
            <div class="metric-row">
              <span>平均变动</span>
              <strong>{{ stats.avgChange }}</strong>
            </div>
          </div>
        </section>

        <section class="nd-card sidebar-card">
          <div class="section-header compact">
            <div>
              <div class="text-label">[ Momentum ]</div>
              <h2>攀升最快</h2>
            </div>
          </div>

          <div v-if="topMovers.length" class="mover-list">
            <div v-for="mover in topMovers" :key="mover.userId" class="mover-row">
              <div class="user-avatar small">{{ getInitial(mover.username) }}</div>
              <div class="mover-copy">
                <strong>{{ mover.username }}</strong>
                <span>{{ formatScore(mover.score) }} {{ currentCategory.unit }}</span>
              </div>
              <span class="mover-change">+{{ mover.change }}</span>
            </div>
          </div>
          <div v-else class="text-caption">当前没有明显的排名变动。</div>
        </section>

        <section class="nd-card sidebar-card">
          <div class="section-header compact">
            <div>
              <div class="text-label">[ Distribution ]</div>
              <h2>区间分布</h2>
            </div>
          </div>

          <div class="distribution-list">
            <div v-for="band in distributionBands" :key="band.label" class="distribution-row">
              <div class="distribution-head">
                <span>{{ band.label }}</span>
                <strong>{{ band.count }}</strong>
              </div>
              <div class="distribution-bar">
                <span :style="{ width: `${band.percent}%` }"></span>
              </div>
            </div>
          </div>
        </section>

        <section class="nd-card sidebar-card">
          <div class="section-header compact">
            <div>
              <div class="text-label">[ Guidance ]</div>
              <h2>上榜建议</h2>
            </div>
          </div>

          <div class="guidance-list">
            <div v-for="item in guidanceItems" :key="item.title" class="guidance-row">
              <strong>{{ item.title }}</strong>
              <p>{{ item.body }}</p>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getLeaderboard } from '../api/community'
import { useUserStore } from '../stores/user'

type PeriodKey = 'daily' | 'weekly' | 'monthly' | 'alltime'
type CategoryKey = 'training' | 'streak' | 'calories' | 'social'
type ScopeKey = 'top10' | 'all'

interface LeaderboardEntry {
  rank: number
  userId: number
  username: string
  score: number
  change: number
  isMe: boolean
}

const router = useRouter()
const userStore = useUserStore()
const tableRef = ref<HTMLElement | null>(null)

const loading = ref(false)
const lastUpdate = ref(formatClock(new Date()))
const activePeriod = ref<PeriodKey>('weekly')
const activeCategory = ref<CategoryKey>('training')
const viewScope = ref<ScopeKey>('top10')
const searchQuery = ref('')
const leaderboardData = ref<LeaderboardEntry[]>([])

const periods = [
  { key: 'daily', label: '今日' },
  { key: 'weekly', label: '本周' },
  { key: 'monthly', label: '本月' },
  { key: 'alltime', label: '全部' }
] as const

const scopes = [
  { key: 'top10', label: '前 10 名' },
  { key: 'all', label: '全部成员' }
] as const

const categories = [
  {
    key: 'training',
    label: '训练完成',
    short: '已完成训练计划',
    icon: '力',
    heroTitle: '训练完成度榜单',
    headline: '用完成量衡量训练执行，先把频率做实。',
    description: '适合观察训练计划落地情况，排名以已完成的训练计划数为核心，能直接反映执行稳定性。',
    scoreLabel: '完成训练数',
    unit: '次',
    footnote: '高排名通常意味着完成频率更稳。'
  },
  {
    key: 'streak',
    label: '连续天数',
    short: '保持连续训练节奏',
    icon: '续',
    heroTitle: '连续训练榜单',
    headline: '连续性比爆发更难得，排名看的是你能坚持多久。',
    description: '连续训练维度更接近真实习惯，不追求单日极值，而强调长期可持续的节奏。',
    scoreLabel: '连续训练天数',
    unit: '天',
    footnote: '稳定的连续性通常比偶发高强度更有价值。'
  },
  {
    key: 'calories',
    label: '热量消耗',
    short: '累计饮食与消耗结果',
    icon: '燃',
    heroTitle: '热量管理榜单',
    headline: '把饮食和训练放到同一视角，才能真正看清能量收支。',
    description: '该维度以累计热量数据排序，更适合观察减脂或体重管理阶段的长期投入。',
    scoreLabel: '累计热量',
    unit: 'kcal',
    footnote: '热量数据更适合做阶段趋势，不适合只看单天波动。'
  },
  {
    key: 'social',
    label: '社区影响',
    short: '动态与互动能力',
    icon: '社',
    heroTitle: '社区影响榜单',
    headline: '高质量输出会被看见，互动不是噪音，而是训练反馈的一部分。',
    description: '社区维度综合发帖和获赞表现，适合识别谁在持续提供有价值的训练内容。',
    scoreLabel: '社区影响分',
    unit: '分',
    footnote: '持续记录与有效交流会提升你的社区权重。'
  }
] as const

const currentPeriod = computed(() => periods.find((period) => period.key === activePeriod.value) || periods[1])
const currentCategory = computed(() => categories.find((category) => category.key === activeCategory.value) || categories[0])

const rankedList = computed(() => [...leaderboardData.value].sort((left, right) => left.rank - right.rank))

const filteredList = computed(() => {
  if (!searchQuery.value) return rankedList.value
  const keyword = searchQuery.value.toLowerCase()
  return rankedList.value.filter((entry) => entry.username.toLowerCase().includes(keyword))
})

const podiumWinners = computed(() => {
  const list = rankedList.value.slice(0, 3)
  return list.map((entry, index) => ({
    ...entry,
    rankLabel: `#${entry.rank}`,
    blockLabel: ['第 1', '第 2', '第 3'][index] || `第 ${entry.rank}`,
    positionClass: index === 0 ? 'first' : index === 1 ? 'second' : 'third'
  }))
})

const tableRows = computed(() => {
  if (searchQuery.value) return filteredList.value
  if (viewScope.value === 'top10') return rankedList.value.slice(3, 10)
  return rankedList.value.slice(3)
})

const currentScopeLabel = computed(() => (viewScope.value === 'top10' ? '展示第 4 至第 10 名' : '展示全部成员'))

const myRank = computed(() => rankedList.value.find((entry) => entry.isMe) || null)

const myPercentile = computed(() => {
  if (!myRank.value || rankedList.value.length === 0) return 0
  return Math.max(1, Math.round(((rankedList.value.length - myRank.value.rank + 1) / rankedList.value.length) * 100))
})

const stats = computed(() => {
  const scores = rankedList.value.map((entry) => entry.score)
  const total = rankedList.value.length
  const active = rankedList.value.filter((entry) => entry.score > 0).length
  const averageScore = total ? Math.round(scores.reduce((sum, score) => sum + score, 0) / total) : 0
  const avgChange = total
    ? (rankedList.value.reduce((sum, entry) => sum + Math.abs(entry.change || 0), 0) / total).toFixed(1)
    : '0.0'

  return {
    total,
    active,
    averageScore,
    avgChange
  }
})

const summaryCards = computed(() => {
  const leader = rankedList.value[0]
  const midpoint = rankedList.value[Math.floor((rankedList.value.length - 1) / 2)]

  return [
    { label: '榜首成绩', value: leader ? `${formatScore(leader.score)} ${currentCategory.value.unit}` : '-' },
    { label: '中位水平', value: midpoint ? `${formatScore(midpoint.score)} ${currentCategory.value.unit}` : '-' },
    { label: '参与成员', value: `${stats.value.total}` },
    { label: '我的百分位', value: myRank.value ? `前 ${myPercentile.value}%` : '未上榜' }
  ]
})

const topMovers = computed(() =>
  [...rankedList.value]
    .filter((entry) => entry.change > 0)
    .sort((left, right) => right.change - left.change)
    .slice(0, 4)
)

const distributionBands = computed(() => {
  const total = Math.max(1, rankedList.value.length)
  const bands = [
    { label: 'Top 3', count: rankedList.value.filter((entry) => entry.rank <= 3).length },
    { label: 'Top 10', count: rankedList.value.filter((entry) => entry.rank > 3 && entry.rank <= 10).length },
    { label: '11-20', count: rankedList.value.filter((entry) => entry.rank > 10 && entry.rank <= 20).length },
    { label: '20+', count: rankedList.value.filter((entry) => entry.rank > 20).length }
  ]

  return bands.map((band) => ({
    ...band,
    percent: Math.round((band.count / total) * 100)
  }))
})

const guidanceItems = computed(() => {
  const map: Record<CategoryKey, Array<{ title: string; body: string }>> = {
    training: [
      { title: '先稳频率', body: '训练榜单最怕断档。先把每周计划完成率稳定住，再去追求更高训练量。' },
      { title: '降低摩擦', body: '提前准备动作清单和器械顺序，能显著提升训练计划的完成概率。' }
    ],
    streak: [
      { title: '留出轻量日', body: '连续性不等于每天高强度。安排低负荷恢复训练更容易延长连续天数。' },
      { title: '记录中断原因', body: '知道自己为什么断掉，才可能把连续天数从偶发延长为习惯。' }
    ],
    calories: [
      { title: '看阶段均值', body: '热量数据更适合观察阶段平均水平，不要因为一两天波动过度修正。' },
      { title: '训练与饮食联动', body: '高训练量日和休息日的摄入策略应该区分，否则数据解释会失真。' }
    ],
    social: [
      { title: '多写过程', body: '比起只发结果，动作选择、失败点和调整思路更容易带来高质量互动。' },
      { title: '保持回应', body: '社区影响力来自持续互动。认真回复评论比单次爆量更可持续。' }
    ]
  }

  return map[activeCategory.value]
})

function formatClock(date: Date) {
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function getInitial(value?: string) {
  return (value || 'U').trim().charAt(0).toUpperCase()
}

function formatScore(score: number) {
  if (score >= 10000) return `${(score / 10000).toFixed(1)}w`
  if (score >= 1000) return `${(score / 1000).toFixed(1)}k`
  return String(score)
}

function rankLabel(rank: number) {
  if (rank <= 3) return '领奖台'
  if (rank <= 10) return '前十'
  return '冲榜中'
}

function previousEntry(entry: LeaderboardEntry) {
  return rankedList.value.find((candidate) => candidate.rank === entry.rank - 1) || null
}

function formatGap(entry: LeaderboardEntry) {
  const previous = previousEntry(entry)
  if (!previous) return '领跑'
  return `${Math.max(0, previous.score - entry.score)}`
}

function gapCaption(entry: LeaderboardEntry) {
  const previous = previousEntry(entry)
  return previous ? `距 #${previous.rank}` : '当前第一'
}

function formatChange(change: number) {
  if (change > 0) return `+${change}`
  if (change < 0) return `${change}`
  return '0'
}

function changeClass(change: number) {
  if (change > 0) return 'positive'
  if (change < 0) return 'negative'
  return 'neutral'
}

function changeLabel(change: number) {
  if (change > 0) return '上升'
  if (change < 0) return '下降'
  return '持平'
}

function isSearchHit(entry: LeaderboardEntry) {
  if (!searchQuery.value) return false
  return entry.username.toLowerCase().includes(searchQuery.value.toLowerCase())
}

function scrollToTable() {
  tableRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function fetchLeaderboardData() {
  loading.value = true
  try {
    const response: any = await getLeaderboard({
      category: activeCategory.value,
      period: activePeriod.value
    })

    if (Array.isArray(response)) {
      const currentUsername = userStore.userInfo?.username
      leaderboardData.value = response.map((item: any, index: number) => ({
        rank: Number(item.rank || index + 1),
        userId: Number(item.userId || index + 1),
        username: item.username || `USER_${index + 1}`,
        score: Number(item.score || 0),
        change: Number(item.change || 0),
        isMe: item.username === currentUsername
      }))
    } else {
      leaderboardData.value = []
    }

    lastUpdate.value = formatClock(new Date())
  } finally {
    loading.value = false
  }
}

watch([activeCategory, activePeriod], () => {
  fetchLeaderboardData()
})

onMounted(() => {
  fetchLeaderboardData()
})
</script>

<style scoped>
.leaderboard-content {
  width: 100%;
}

.leaderboard-hero {
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
  max-width: 12ch;
  margin-top: 18px;
}

.hero-description {
  max-width: 60ch;
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

.summary-panel,
.controls-card,
.podium-card,
.rank-table-card,
.my-rank-card,
.sidebar-card {
  padding: 28px;
  color: var(--text-main);
}

.summary-panel-header,
.controls-header,
.controls-toolbar,
.section-header,
.metric-row,
.mover-row,
.distribution-head,
.guidance-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.summary-panel-header,
.controls-header,
.section-header {
  align-items: flex-start;
}

.summary-panel-header h2,
.controls-header h2,
.section-header h2 {
  margin-top: 8px;
  font-family: var(--font-heading);
  font-size: 1.4rem;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  margin-top: 22px;
  border: 1px solid rgba(17, 17, 17, 0.12);
}

.summary-item {
  min-height: 128px;
  padding: 18px;
  border-right: 1px solid rgba(17, 17, 17, 0.12);
  border-bottom: 1px solid rgba(17, 17, 17, 0.12);
  background: rgba(255, 255, 255, 0.2);
}

.summary-item:nth-child(2n) {
  border-right: none;
}

.summary-item:nth-last-child(-n + 2) {
  border-bottom: none;
}

.summary-item span,
.my-rank-main span {
  display: block;
  color: var(--text-secondary);
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.summary-item strong,
.my-rank-main strong {
  display: block;
  margin-top: 12px;
  font-family: var(--font-heading);
  font-size: 1.9rem;
}

.summary-footnote {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  color: var(--text-secondary);
}

.period-selector,
.scope-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.period-btn,
.scope-chip,
.cat-btn {
  border: 1px solid rgba(17, 17, 17, 0.14);
  background: transparent;
  color: var(--text-secondary);
  padding: 10px 12px;
  font-size: 0.78rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.period-btn.active,
.scope-chip.active,
.cat-btn.active {
  background: #17181f;
  border-color: #17181f;
  color: var(--text-inverse);
}

.category-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0;
  margin-top: 18px;
  border: 1px solid rgba(17, 17, 17, 0.14);
}

.cat-btn {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 78px;
  border: none;
  border-right: 1px solid rgba(17, 17, 17, 0.14);
  text-align: left;
}

.cat-btn:last-child {
  border-right: none;
}

.cat-icon {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(17, 17, 17, 0.12);
  font-family: var(--font-heading);
}

.cat-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.cat-copy strong {
  font-size: 0.9rem;
}

.cat-copy small {
  color: currentColor;
  font-size: 0.72rem;
}

.controls-toolbar {
  align-items: center;
  margin-top: 18px;
}

.search-input {
  flex: 1;
}

.loading-state {
  display: flex;
  align-items: center;
  padding: 28px 0;
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

.leaderboard-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) 320px;
  gap: 24px;
  margin-top: 24px;
}

.table-section,
.sidebar-section {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.section-header.compact {
  margin-bottom: 6px;
}

.podium {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  margin-top: 18px;
}

.podium-item {
  min-height: 272px;
  padding: 20px 16px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  border: 1px solid rgba(17, 17, 17, 0.12);
  background: rgba(255, 255, 255, 0.2);
}

.podium-item.first {
  min-height: 304px;
}

.podium-topline {
  color: var(--text-secondary);
  font-size: 0.76rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.podium-avatar,
.user-avatar {
  display: grid;
  place-items: center;
  font-family: var(--font-heading);
  font-weight: 800;
}

.podium-avatar {
  width: 56px;
  height: 56px;
  margin-top: 14px;
  color: #fffaf4;
  background: linear-gradient(135deg, #8ea7a1, #c2a978);
}

.podium-avatar.first {
  width: 68px;
  height: 68px;
  background: linear-gradient(135deg, #17181f, #b79a72);
}

.podium-avatar.second {
  background: linear-gradient(135deg, #a1a7af, #d0d4d8);
  color: #17181f;
}

.podium-avatar.third {
  background: linear-gradient(135deg, #8d5f42, #c6926a);
}

.podium-name {
  margin-top: 12px;
  font-family: var(--font-heading);
  font-size: 1.08rem;
}

.podium-score {
  margin-top: 6px;
  color: var(--text-secondary);
}

.podium-block {
  width: 100%;
  margin-top: 18px;
  padding: 18px 0;
  border-top: 1px solid rgba(17, 17, 17, 0.12);
  text-align: center;
  font-family: var(--font-heading);
}

.rank-table {
  margin-top: 18px;
  border: 1px solid rgba(17, 17, 17, 0.14);
}

.rank-table-header,
.rank-row {
  display: grid;
  grid-template-columns: 86px minmax(0, 1.3fr) 120px 104px 88px;
  gap: 12px;
  align-items: center;
  padding: 0 20px;
}

.rank-table-header {
  min-height: 58px;
  color: var(--text-secondary);
  font-size: 0.74rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.rank-row {
  min-height: 78px;
  border-top: 1px solid rgba(17, 17, 17, 0.08);
}

.rank-row.is-me {
  background: rgba(183, 154, 114, 0.12);
}

.rank-row.highlighted {
  box-shadow: inset 0 0 0 1px rgba(23, 24, 31, 0.12);
}

.rank-col {
  min-width: 0;
}

.rank-number strong,
.rank-score strong,
.rank-gap strong,
.rank-change strong {
  font-family: var(--font-heading);
  font-size: 1.05rem;
}

.rank-user {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  width: 34px;
  height: 34px;
  color: #fffaf4;
  background: linear-gradient(135deg, #c2a978, #7ea889);
  flex-shrink: 0;
}

.user-avatar.small {
  width: 28px;
  height: 28px;
  font-size: 0.78rem;
}

.user-copy,
.mover-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-copy strong,
.mover-copy strong {
  font-size: 0.92rem;
}

.user-copy span,
.rank-score span,
.rank-gap span,
.rank-change span,
.mover-copy span {
  color: var(--text-secondary);
  font-size: 0.76rem;
}

.rank-score,
.rank-gap,
.rank-change {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rank-change.positive strong,
.mover-change {
  color: var(--success);
}

.rank-change.negative strong {
  color: var(--error);
}

.rank-change.neutral strong {
  color: var(--text-secondary);
}

.table-empty {
  padding: 28px 20px;
  text-align: center;
}

.table-empty p {
  margin: 12px 0 0;
  color: var(--text-secondary);
}

.my-rank-grid {
  display: grid;
  grid-template-columns: 180px 180px minmax(0, 1fr);
  gap: 18px;
  margin-top: 18px;
}

.my-rank-main,
.my-rank-progress {
  padding: 18px;
  border: 1px solid rgba(17, 17, 17, 0.12);
  background: rgba(255, 255, 255, 0.2);
}

.progress-copy {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  color: var(--text-secondary);
}

.progress-copy strong {
  color: var(--text-main);
  font-family: var(--font-heading);
}

.progress-bar {
  height: 8px;
  margin-top: 22px;
  background: rgba(17, 17, 17, 0.08);
}

.progress-bar span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #17181f, #b79a72);
}

.metric-list,
.mover-list,
.distribution-list,
.guidance-list {
  display: grid;
  gap: 10px;
}

.metric-row,
.mover-row,
.guidance-row {
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid rgba(17, 17, 17, 0.08);
}

.metric-row strong {
  font-family: var(--font-heading);
}

.mover-row {
  gap: 10px;
}

.mover-copy {
  flex: 1;
}

.distribution-row + .distribution-row {
  margin-top: 8px;
}

.distribution-head {
  align-items: center;
}

.distribution-bar {
  height: 8px;
  margin-top: 8px;
  background: rgba(17, 17, 17, 0.08);
}

.distribution-bar span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #b79a72, #17181f);
}

.guidance-row {
  display: block;
}

.guidance-row p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  line-height: 1.74;
}

@media (max-width: 1280px) {
  .leaderboard-hero,
  .leaderboard-layout {
    grid-template-columns: 1fr;
  }

  .category-tabs {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .summary-grid,
  .podium,
  .my-rank-grid,
  .category-tabs {
    grid-template-columns: 1fr;
  }

  .summary-item,
  .cat-btn {
    border-right: none;
  }

  .summary-item {
    border-bottom: 1px solid rgba(17, 17, 17, 0.12);
  }

  .summary-item:last-child {
    border-bottom: none;
  }

  .rank-table-header,
  .rank-row {
    grid-template-columns: 72px minmax(0, 1fr) 96px;
  }

  .rank-table-header span:nth-child(4),
  .rank-table-header span:nth-child(5),
  .rank-row .rank-gap,
  .rank-row .rank-change {
    display: none;
  }
}

@media (max-width: 720px) {
  .hero-actions,
  .controls-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .hero-actions .nd-btn,
  .scope-group,
  .scope-chip {
    width: 100%;
  }

  .scope-group {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
}
</style>
