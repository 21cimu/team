<template>
  <div class="history-content">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">历史记录</span>
          <strong class="topbar-page-title">训练档案</strong>
        </div>
        <div class="topbar-page-actions tabs">
          <button
            :class="['tab-btn', activeTab === 'training' ? 'active' : '']"
            @click="activeTab = 'training'"
          >训练计划</button>
          <button
            :class="['tab-btn', activeTab === 'diet' ? 'active' : '']"
            @click="activeTab = 'diet'"
          >饮食计划</button>
        </div>
      </div>
    </Teleport>

    <div class="page-header mb-3xl">
      <div>
        <div class="text-label mb-xs">[ 历史记录 ]</div>
        <h1 class="text-display-md text-primary">档案</h1>
      </div>
      <div class="tabs">
        <button
          :class="['tab-btn', activeTab === 'training' ? 'active' : '']"
          @click="activeTab = 'training'"
        >⌖ 训练协议</button>
        <button
          :class="['tab-btn', activeTab === 'diet' ? 'active' : '']"
          @click="activeTab = 'diet'"
        >◒ 营养计划</button>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="stats-row mb-3xl">
      <div class="nd-card stat-box">
        <div class="text-caption text-secondary">历史会话总计</div>
        <div class="text-display-md text-primary">{{ activeTab === 'training' ? trainingStats.total : dietStats.total }}</div>
      </div>
      <div class="nd-card stat-box green-shadow">
        <div class="text-caption text-secondary">累计完成</div>
        <div class="text-display-md text-success">{{ activeTab === 'training' ? trainingStats.completed : dietStats.completed }}</div>
      </div>
      <div class="nd-card stat-box">
        <div class="text-caption text-secondary">平均完成率</div>
        <div class="text-display-md">{{ activeTab === 'training' ? trainingStats.rate : dietStats.rate }}%</div>
      </div>
    </div>

    <!-- Log List -->
    <div v-if="loading" class="loading-state">
      <span class="status-dot blink"></span>
      <span class="text-caption ml-sm">加载记录中...</span>
    </div>

    <div v-else class="history-list">
      <template v-if="activeTab === 'training'">
        <div
          v-for="(item, index) in trainingLogs"
          :key="item.id"
          class="nd-card log-card mb-md"
          :class="{ 'green-shadow': item.status === 1 }"
          @click="expandedLog = expandedLog === item.id ? null : item.id"
        >
          <div class="log-main">
            <div class="log-info">
              <div class="log-date text-heading">{{ item.planDate }}</div>
              <div class="log-meta text-caption text-secondary mt-xs">
                <span>{{ item.targetMuscleGroup || '综合' }}</span>
                <span class="separator">·</span>
                <span>{{ item.estimatedDuration || '?' }} 分钟</span>
              </div>
            </div>
            <div class="log-right">
              <div class="log-status" :class="item.status === 1 ? 'completed' : 'pending'">
                {{ item.status === 1 ? '✓ 已完成' : '○ 待执行' }}
              </div>
              <span class="expand-icon">{{ expandedLog === item.id ? '▲' : '▼' }}</span>
            </div>
          </div>

          <!-- Expanded Detail -->
          <div v-if="expandedLog === item.id" class="log-detail mt-md">
            <div class="detail-divider"></div>
            <div class="detail-content mt-md" v-if="parseContent(item.content)">
              <div v-for="(ex, ei) in parseContent(item.content)?.exercises" :key="ei" class="ex-row">
                <span class="text-caption text-primary">{{ String(ei + 1).padStart(2, '0') }}</span>
                <span class="text-body-sm ml-sm">{{ ex.name }}</span>
                <span class="text-caption text-secondary ml-md">{{ ex.sets }}×{{ ex.reps }} · {{ ex.restSeconds }}秒 休息</span>
              </div>
            </div>
            <div v-else class="text-caption text-secondary mt-md">暂无动作详情</div>
          </div>
        </div>
        <div v-if="!trainingLogs.length" class="empty-state nd-card">
          <p class="text-label text-warning">[ 未找到协议记录 ]</p>
          <button class="nd-btn mt-md" @click="$router.push('/app/training')">开始第一个协议</button>
        </div>
      </template>

      <template v-else>
        <div
          v-for="(item, index) in dietLogs"
          :key="item.id"
          class="nd-card log-card mb-md"
          :class="{ 'green-shadow': item.status === 1 }"
          @click="expandedLog = expandedLog === item.id ? null : item.id"
        >
          <div class="log-main">
            <div class="log-info">
              <div class="log-date text-heading">{{ item.planDate }}</div>
              <div class="log-meta text-caption text-secondary mt-xs">
                <span class="text-primary">{{ item.totalCalories || '?' }} 千卡</span>
                <span class="separator">·</span>
                <span>蛋白质: {{ item.protein || '?' }}g · 碳水: {{ item.carbs || '?' }}g · 脂肪: {{ item.fat || '?' }}g</span>
              </div>
            </div>
            <div class="log-right">
              <div class="log-status" :class="item.status === 1 ? 'completed' : 'pending'">
                {{ item.status === 1 ? '✓ 已完成' : '○ 待执行' }}
              </div>
              <span class="expand-icon">{{ expandedLog === item.id ? '▲' : '▼' }}</span>
            </div>
          </div>

          <div v-if="expandedLog === item.id" class="log-detail mt-md">
            <div class="detail-divider"></div>
            <div class="detail-content mt-md" v-if="parseContent(item.content)">
              <div v-for="(meal, mi) in parseContent(item.content)?.meals" :key="mi" class="meal-expand">
                <div class="meal-name text-caption text-primary mb-xs">{{ meal.mealName }}</div>
                <div v-for="(food, fi) in meal.items" :key="fi" class="food-row">
                  <span class="text-body-sm">{{ food.name }}</span>
                  <span class="text-caption text-secondary">{{ food.amount }}</span>
                  <span class="text-caption ml-md">{{ food.calories }} 千卡</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="!dietLogs.length" class="empty-state nd-card">
          <p class="text-label text-warning">[ 未找到营养记录 ]</p>
          <button class="nd-btn mt-md" @click="$router.push('/app/diet')">开始第一个饮食计划</button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { getTrainingHistory, getDietHistory } from '../api/history'

const activeTab = ref('training')
const trainingLogs = ref<any[]>([])
const dietLogs = ref<any[]>([])
const loading = ref(false)
const expandedLog = ref<number | null>(null)

const trainingStats = computed(() => {
  const total = trainingLogs.value.length
  const completed = trainingLogs.value.filter(l => l.status === 1).length
  return {
    total,
    completed,
    rate: total ? Math.round(completed / total * 100) : 0
  }
})

const dietStats = computed(() => {
  const total = dietLogs.value.length
  const completed = dietLogs.value.filter(l => l.status === 1).length
  return {
    total,
    completed,
    rate: total ? Math.round(completed / total * 100) : 0
  }
})

const parseContent = (content: string) => {
  try {
    return JSON.parse(content)
  } catch {
    return null
  }
}

const fetchLogs = async () => {
  loading.value = true
  try {
    const [tRes, dRes] = await Promise.all([
      getTrainingHistory(),
      getDietHistory()
    ])
    trainingLogs.value = (tRes as any) || []
    dietLogs.value = (dRes as any) || []
  } catch (e) {
    // error handled
  } finally {
    loading.value = false
  }
}

watch(activeTab, () => {
  expandedLog.value = null
})

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped>
.history-content { width: 100%; }

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.tabs {
  display: flex;
  gap: 8px;
}

.tab-btn {
  background: none;
  border: 1px solid rgba(248,250,252,0.15);
  color: var(--text-secondary);
  padding: 8px 16px;
  font-family: var(--font-heading);
  font-size: 13px;
  letter-spacing: 1.5px;
  cursor: pointer;
  transition: all 0.15s;
  border-radius: 2px;
}

.tab-btn.active {
  background: var(--primary);
  border-color: var(--primary);
  color: #111827;
}

/* Stats */
.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.stat-box {
  padding: 16px 20px;
}

/* Log Card */
.log-card {
  padding: 16px 20px;
  cursor: pointer;
  transition: all 0.2s;
}

.log-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.log-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.log-date {
  font-size: 18px;
}

.log-meta {
  display: flex;
  align-items: center;
  gap: 0;
}

.separator {
  margin: 0 8px;
  opacity: 0.4;
}

.log-status {
  font-family: var(--font-heading);
  font-size: 12px;
  letter-spacing: 1.5px;
  padding: 4px 10px;
  border-radius: 2px;
}

.log-status.completed {
  background: rgba(34, 197, 94, 0.12);
  color: #22C55E;
}

.log-status.pending {
  background: rgba(156, 163, 175, 0.1);
  color: var(--text-secondary);
}

.expand-icon {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Detail */
.detail-divider {
  height: 1px;
  background: rgba(248,250,252,0.08);
}

.ex-row {
  display: flex;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid rgba(248,250,252,0.04);
}

.meal-expand {
  margin-bottom: 12px;
}

.meal-name {
  font-family: var(--font-heading);
  letter-spacing: 1px;
}

.food-row {
  display: flex;
  align-items: center;
  padding: 4px 0;
  gap: 12px;
}

.food-row > span:first-child { flex: 1; }

/* Empty / Loading */
.loading-state {
  display: flex;
  align-items: center;
  padding: 40px 0;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: var(--warning);
  border-radius: 50%;
}

.status-dot.blink { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0.2; } }

.empty-state {
  text-align: center;
  padding: 48px;
  border: 2px dashed rgba(249, 115, 22, 0.25);
  box-shadow: none;
  background: transparent;
}

@media (max-width: 768px) {
  .stats-row { grid-template-columns: 1fr 1fr; }
}
</style>
