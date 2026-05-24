<template>
  <div class="exercises-content">
    <div class="page-header mb-3xl">
      <div>
        <div class="text-label mb-xs">[ 动作库 ]</div>
        <h1 class="text-display-md text-primary">动作库</h1>
        <p class="text-secondary mt-xs">{{ filteredExercises.length }} 个动作</p>
      </div>
    </div>

    <!-- Search & Filter -->
    <div class="filter-bar mb-2xl">
      <input
        v-model="searchQuery"
        class="search-input"
        placeholder="🔍 搜索动作..."
      />
      <div class="muscle-filters">
        <button
          v-for="cat in muscleCategories"
          :key="cat.id"
          class="filter-chip"
          :class="{ active: activeCategory === cat.id }"
          @click="activeCategory = cat.id"
        >{{ cat.label }}</button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="loading-state">
      <span class="status-dot blink"></span>
      <span class="text-caption ml-sm">加载动作库...</span>
    </div>

    <!-- Exercise Grid -->
    <div v-else class="exercises-grid">
      <div
        v-for="(ex, index) in filteredExercises"
        :key="index"
        class="nd-card ex-card"
        :class="{ 'green-shadow': index % 3 === 1, 'expanded': expandedEx === index }"
        @click="expandedEx = expandedEx === index ? null : index"
      >
        <div class="ex-header">
          <div>
            <h3 class="text-heading ex-name">{{ ex.displayName }}</h3>
            <div class="text-caption text-secondary mt-xs">{{ ex.displayNameEn }}</div>
            <div class="ex-tags mt-xs">
              <span class="tag primary-tag">{{ ex.displayTarget }}</span>
              <span class="tag">{{ ex.categoryLabel }}</span>
              <span class="tag" :class="`difficulty-${ex.difficulty.toLowerCase()}`">{{ ex.difficultyLabel }}</span>
            </div>
          </div>
          <div class="ex-equipment">
            <span class="equip-icon">{{ ex.equipIcon }}</span>
          </div>
        </div>

        <div class="ex-body mt-md">
          <p class="text-body-sm text-secondary">{{ ex.displayDescription }}</p>
        </div>

        <!-- Expanded: Tips & Muscles -->
        <div v-if="expandedEx === index" class="ex-detail mt-md">
          <div class="detail-divider mb-md"></div>
          <div class="tips-grid">
            <div>
              <div class="text-caption text-primary mb-sm">执行提示</div>
              <ul class="tips-list">
                <li v-for="tip in ex.displayTips" :key="tip" class="text-caption text-secondary">{{ tip }}</li>
              </ul>
            </div>
            <div>
              <div class="text-caption text-primary mb-sm">肌肉群</div>
              <div class="muscles-list">
                <div class="muscle-item primary-muscle">
                  <span class="dot primary-dot"></span>
                  <span class="text-caption">{{ ex.displayPrimaryMuscle }} (主要)</span>
                </div>
                <div v-for="m in ex.displaySecondaryMuscles" :key="m" class="muscle-item">
                  <span class="dot secondary-dot"></span>
                  <span class="text-caption text-secondary">{{ m }}</span>
                </div>
              </div>
              <div class="text-caption text-secondary mt-md">
                <span class="text-caption">推荐:</span>
                <span class="text-caption ml-sm">{{ ex.reps }} × {{ ex.sets }} 组</span>
              </div>
            </div>
          </div>
        </div>

        <div class="ex-footer mt-md">
          <span class="text-caption text-secondary">{{ expandedEx === index ? '点击收起 ▲' : '点击查看详情 ▼' }}</span>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!filteredExercises.length" class="empty-state nd-card">
      <p class="text-label text-warning">[ 没有符合您筛选条件的动作 ]</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { getExercises } from '../api/exercise'
import { localizeExercise } from '../utils/exerciseLocalization'

const searchQuery = ref('')
const activeCategory = ref('ALL')
const expandedEx = ref<number | null>(null)
const loading = ref(false)
const exercises = ref<any[]>([])

const muscleCategories = [
  { id: 'ALL', label: '全部' },
  { id: 'CHEST', label: '胸部' },
  { id: 'BACK', label: '背部' },
  { id: 'LEGS', label: '腿部' },
  { id: 'SHOULDERS', label: '肩部' },
  { id: 'ARMS', label: '手臂' },
  { id: 'CORE', label: '核心' },
  { id: 'CARDIO', label: '有氧' }
]

const difficultyMap: Record<string, string> = {
  'BEGINNER': '入门',
  'INTERMEDIATE': '进阶',
  'ADVANCED': '精英'
}

const categoryMap: Record<string, string> = {
  'CHEST': '胸部训练',
  'BACK': '背部训练',
  'LEGS': '腿部训练',
  'SHOULDERS': '肩部训练',
  'ARMS': '手臂训练',
  'CORE': '核心训练',
  'CARDIO': '有氧训练'
}

const fetchExercises = async () => {
  loading.value = true
  try {
    const data: any = await getExercises()
    if (Array.isArray(data)) {
      exercises.value = data
    }
  } catch (e) {
    console.error('Failed to fetch exercises:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchExercises()
})

const filteredExercises = computed(() => {
  let result = exercises.value.map(localizeExercise)

  if (activeCategory.value !== 'ALL') {
    result = result.filter(ex => ex.category === activeCategory.value)
  }

  if (searchQuery.value.trim()) {
    const kw = searchQuery.value.trim().toLowerCase()
    result = result.filter(ex =>
      (ex.name && ex.name.toLowerCase().includes(kw)) ||
      (ex.displayName && ex.displayName.toLowerCase().includes(kw)) ||
      (ex.target && ex.target.toLowerCase().includes(kw)) ||
      (ex.displayTarget && ex.displayTarget.toLowerCase().includes(kw)) ||
      (ex.description && ex.description.toLowerCase().includes(kw)) ||
      (ex.primaryMuscle && ex.primaryMuscle.toLowerCase().includes(kw)) ||
      (ex.displayPrimaryMuscle && ex.displayPrimaryMuscle.toLowerCase().includes(kw))
    )
  }

  return result.map(ex => ({
    ...ex,
    categoryLabel: categoryMap[ex.category] || ex.category,
    difficultyLabel: difficultyMap[ex.difficulty] || ex.difficulty
  }))
})
</script>

<style scoped>
.exercises-content { width: 100%; }

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: var(--warning);
  border-radius: 50%;
  display: inline-block;
}

.status-dot.blink {
  animation: blink 1s infinite;
}

@keyframes blink {
  50% { opacity: 0.2; }
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

/* Filter Bar */
.filter-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-input {
  width: 100%;
  max-width: 480px;
  background: rgba(55, 65, 81, 0.5);
  border: 2px solid rgba(248,250,252,0.15);
  padding: 10px 16px;
  color: var(--text-main);
  font-family: var(--font-body);
  font-size: 14px;
  outline: none;
  border-radius: 4px;
  transition: border-color 0.15s;
  box-sizing: border-box;
}

.search-input:focus {
  border-color: var(--primary);
}

.muscle-filters {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.filter-chip {
  background: none;
  border: 1px solid rgba(248,250,252,0.12);
  color: var(--text-secondary);
  padding: 5px 12px;
  font-family: var(--font-heading);
  font-size: 11px;
  letter-spacing: 1.5px;
  cursor: pointer;
  border-radius: 2px;
  transition: all 0.15s;
}

.filter-chip:hover {
  border-color: var(--primary);
  color: var(--primary);
}

.filter-chip.active {
  background: var(--primary);
  border-color: var(--primary);
  color: #111827;
}

/* Exercise Grid */
.exercises-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.ex-card {
  padding: 20px;
  cursor: pointer;
}

.ex-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.ex-name {
  font-size: 18px;
  letter-spacing: 1px;
}

.ex-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  font-family: var(--font-heading);
  font-size: 10px;
  letter-spacing: 1px;
  padding: 2px 8px;
  border-radius: 2px;
  background: rgba(248,250,252,0.06);
  color: var(--text-secondary);
}

.primary-tag {
  background: rgba(249, 115, 22, 0.12);
  color: var(--primary);
}

.difficulty-beginner { background: rgba(34, 197, 94, 0.12); color: #22C55E; }
.difficulty-intermediate { background: rgba(251, 191, 36, 0.12); color: #FBBF24; }
.difficulty-advanced { background: rgba(239, 68, 68, 0.12); color: #ef4444; }

.ex-equipment {
  font-size: 28px;
  opacity: 0.7;
}

/* Detail */
.detail-divider {
  height: 1px;
  background: rgba(248,250,252,0.08);
}

.tips-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.tips-list {
  padding-left: 16px;
  margin: 0;
}

.tips-list li {
  margin-bottom: 6px;
  line-height: 1.5;
}

.muscles-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.muscle-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.primary-dot { background: var(--primary); }
.secondary-dot { background: rgba(156, 163, 175, 0.5); }

.ex-footer {
  text-align: right;
  font-size: 11px;
  opacity: 0.5;
}

/* Empty */
.empty-state {
  text-align: center;
  padding: 48px;
  border: 2px dashed rgba(249, 115, 22, 0.2);
  box-shadow: none;
  background: transparent;
}

@media (max-width: 1024px) {
  .exercises-grid { grid-template-columns: 1fr; }
  .tips-grid { grid-template-columns: 1fr; }
}
</style>
