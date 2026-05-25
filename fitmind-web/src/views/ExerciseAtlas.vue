<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useThree } from '../composables/useThree'
import { muscleGroups, exerciseMatchMap, categoryToMuscleId, type MuscleGroup } from '../data/muscles'
import { getExercises } from '../api/exercise'
import { localizeExercise } from '../utils/exerciseLocalization'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'

type ViewPreset = 'focus' | 'front' | 'back' | 'left' | 'right'

const router = useRouter()
const userStore = useUserStore()
const username = computed(() => userStore.userInfo?.username || '操作员')

const containerRef = ref<HTMLElement | null>(null)
const selectedMuscle = ref<MuscleGroup | null>(null)
const selectedApiExercise = ref<any>(null)
const activeMuscleFilter = ref<string | null>(null)
const searchQuery = ref('')
const hoveredMuscleFromModel = ref<MuscleGroup | null>(null)
const tooltipPos = ref({ x: 0, y: 0 })
const showModelTooltip = ref(false)
const isHoveringList = ref(false)
const hoveredListItemId = ref<string | null>(null)
const hoveredExercisePreview = ref<any>(null)
const exercisePreviewPos = ref({ x: 0, y: 0 })
const apiExercises = ref<any[]>([])
const apiLoading = ref(false)
const activeViewPreset = ref<ViewPreset>('focus')
let hoverDebounceTimer: ReturnType<typeof setTimeout> | null = null

const {
  modelLoaded,
  loading: modelLoading,
  loadProgress,
  loadError,
  init,
  loadModel,
  retryLoad,
  setCameraView,
  highlightMeshesByKeywords,
  resetMuscles,
  hoverMeshesByKeywords,
  setOnMeshHover,
  registerMuscleGroupMapping,
} = useThree()

const MODEL_URL = '/assets/body.glb'

const muscleColorMap: Record<string, string> = {
  chest: '#F97316',
  back: '#3B82F6',
  shoulders: '#A855F7',
  biceps: '#22C55E',
  triceps: '#FBBF24',
  abs: '#EF4444',
  legs: '#06B6D4',
  glutes: '#EC4899',
}

const muscleCategories = computed(() => [
  { id: 'ALL', label: '全部' },
  ...muscleGroups.map(mg => ({ id: mg.id, label: `${mg.name} (${mg.nameEn})` }))
])

const difficultyMap: Record<string, string> = {
  'BEGINNER': '入门',
  'INTERMEDIATE': '进阶',
  'ADVANCED': '精英',
}

const difficultyStars: Record<string, number> = {
  'BEGINNER': 1,
  'INTERMEDIATE': 2,
  'ADVANCED': 3,
}

const buildMeshToMuscleMap = (): Map<string, string> => {
  const map = new Map<string, string>()
  muscleGroups.forEach(mg => {
    mg.matchKeywords.forEach(kw => {
      map.set(kw.toLowerCase(), mg.id)
    })
  })
  return map
}

const findMuscleByMeshName = (meshName: string): MuscleGroup | null => {
  const lower = meshName.toLowerCase()
  for (const mg of muscleGroups) {
    for (const kw of mg.matchKeywords) {
      if (lower.includes(kw.toLowerCase())) return mg
    }
  }
  return null
}

const filteredExercises = computed(() => {
  let result = apiExercises.value.map(localizeExercise)

  if (activeMuscleFilter.value) {
    const filterId = activeMuscleFilter.value
    result = result.filter(ex => {
      const mappedId = categoryToMuscleId[ex.category]
      return mappedId === filterId || ex.category?.toLowerCase() === filterId
    })
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

  return result
})

const currentFocusLabel = computed(() => {
  if (selectedMuscle.value) return selectedMuscle.value.name
  if (selectedApiExercise.value?.displayName) return selectedApiExercise.value.displayName
  return '全身总览'
})

const currentFocusHint = computed(() => {
  if (selectedMuscle.value) return `${selectedMuscle.value.location} · ${selectedMuscle.value.function}`
  if (selectedApiExercise.value?.displayPrimaryMuscle) return `主要刺激 ${selectedApiExercise.value.displayPrimaryMuscle}`
  return '旋转查看身体结构与目标肌群分布'
})

const viewPresets = [
  { id: 'focus', label: '透视', hint: '整体' },
  { id: 'front', label: '正面', hint: '胸腹' },
  { id: 'back', label: '背面', hint: '背臀' },
  { id: 'left', label: '左侧', hint: '轮廓' },
  { id: 'right', label: '右侧', hint: '轮廓' },
] as const

const suggestedViewPreset = (muscleId?: string | null): ViewPreset => {
  switch (muscleId) {
    case 'back':
    case 'glutes':
      return 'back'
    case 'shoulders':
      return 'left'
    default:
      return 'front'
  }
}

const applyViewPreset = (preset: ViewPreset) => {
  activeViewPreset.value = preset
  setCameraView(preset)
}

const handleMuscleSelect = (muscle: MuscleGroup) => {
  selectedMuscle.value = muscle
  selectedApiExercise.value = null
  activeMuscleFilter.value = muscle.id
  highlightMeshesByKeywords(muscle.matchKeywords)
  applyViewPreset(suggestedViewPreset(muscle.id))
}

const handleApiExerciseSelect = (ex: any) => {
  selectedApiExercise.value = ex
  selectedMuscle.value = null

  let matchKeywords = exerciseMatchMap[ex.name]
  if (!matchKeywords) {
    const muscle = muscleGroups.find(mg =>
      mg.keywords.some(k => (ex.primaryMuscle || '').toLowerCase().includes(k.toLowerCase()))
    )
    if (muscle) {
      matchKeywords = muscle.matchKeywords
    }
  }
  if (matchKeywords) {
    highlightMeshesByKeywords(matchKeywords)
  }
  applyViewPreset(suggestedViewPreset(categoryToMuscleId[ex.category]))
}

const handleMuscleChipClick = (id: string) => {
  if (id === 'ALL') {
    activeMuscleFilter.value = null
    selectedMuscle.value = null
    resetMuscles()
  } else {
    const muscle = muscleGroups.find(mg => mg.id === id)
    if (muscle) handleMuscleSelect(muscle)
  }
}

const handleMuscleHover = (muscle: MuscleGroup) => {
  isHoveringList.value = true
  hoveredMuscleFromModel.value = null
  showModelTooltip.value = false
  hoveredExercisePreview.value = null
  hoveredListItemId.value = muscle.id
  clearHoverDebounce()
  hoverMeshesByKeywords(muscle.matchKeywords)
}

const handleMuscleLeave = () => {
  isHoveringList.value = false
  hoveredListItemId.value = null
  hoveredExercisePreview.value = null
  clearHoverDebounce()
  hoverMeshesByKeywords([])
}

const updateExercisePreviewPos = (element: HTMLElement) => {
  const rect = element.getBoundingClientRect()
  const cardWidth = 288
  const cardHeight = 200
  const gap = 16
  const maxY = Math.max(12, window.innerHeight - cardHeight - 12)

  exercisePreviewPos.value = {
    x: Math.max(12, rect.left - cardWidth - gap),
    y: Math.min(Math.max(12, rect.top + rect.height / 2 - cardHeight / 2), maxY),
  }
}

const handleExerciseHover = (ex: any, event: MouseEvent) => {
  isHoveringList.value = true
  hoveredMuscleFromModel.value = null
  showModelTooltip.value = false
  hoveredListItemId.value = 'ex-' + ex.name
  hoveredExercisePreview.value = ex
  updateExercisePreviewPos(event.currentTarget as HTMLElement)
  const keywords = exerciseMatchMap[ex.name]
  if (keywords) hoverMeshesByKeywords(keywords)
}

const handleExerciseLeave = () => {
  isHoveringList.value = false
  hoveredListItemId.value = null
  hoveredExercisePreview.value = null
  clearHoverDebounce()
  hoverMeshesByKeywords([])
}

const handleViewportMouseMove = (event: MouseEvent) => {
  tooltipPos.value = { x: event.clientX, y: event.clientY }
}

const handleReset = () => {
  resetMuscles()
  selectedMuscle.value = null
  selectedApiExercise.value = null
  activeMuscleFilter.value = null
  searchQuery.value = ''
  hoveredMuscleFromModel.value = null
  showModelTooltip.value = false
  hoveredListItemId.value = null
  isHoveringList.value = false
  hoveredExercisePreview.value = null
  applyViewPreset('focus')
}

const handleRetry = async () => {
  try {
    await retryLoad(MODEL_URL)
  } catch (error) {
    console.error('Retry failed:', error)
  }
}

const goToTraining = () => {
  router.push('/app/training')
}

const goToCoach = () => {
  router.push('/app/coach')
}

const clearHoverDebounce = () => {
  if (hoverDebounceTimer) {
    clearTimeout(hoverDebounceTimer)
    hoverDebounceTimer = null
  }
}

const fetchApiExercises = async () => {
  apiLoading.value = true
  try {
    const data: any = await getExercises()
    if (Array.isArray(data)) {
      apiExercises.value = data
    }
  } catch (e) {
    console.error('Failed to fetch exercises:', e)
  } finally {
    apiLoading.value = false
  }
}

onMounted(async () => {
  fetchApiExercises()

  await nextTick()

  registerMuscleGroupMapping(buildMeshToMuscleMap())

  setOnMeshHover((meshName: string | null) => {
    if (isHoveringList.value) return
    if (meshName) {
      const muscle = findMuscleByMeshName(meshName)
      hoveredMuscleFromModel.value = muscle
      showModelTooltip.value = true
      if (muscle) {
        hoverMeshesByKeywords(muscle.matchKeywords)
      } else {
        hoverMeshesByKeywords([meshName])
      }
    } else {
      hoveredMuscleFromModel.value = null
      showModelTooltip.value = false
      hoverMeshesByKeywords([])
    }
  })

  if (containerRef.value) {
    try {
      await init(containerRef.value)
      await loadModel(MODEL_URL)
      applyViewPreset('focus')
    } catch (error) {
      console.error('[ExerciseAtlas] 3D init failed:', error)
    }
  }
})

onUnmounted(() => {
  clearHoverDebounce()
})
</script>

<template>
  <div class="atlas-page">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">鍔ㄤ綔鍥捐氨</span>
          <strong class="topbar-page-title">3D 铻嶅悎缁堢</strong>
          <span class="topbar-page-meta">{{ currentFocusLabel }}</span>
        </div>
        <div class="topbar-page-actions">
          <button class="nd-btn" @click="handleReset" :disabled="!modelLoaded" style="font-size: 12px; padding: 6px 16px;">
            鈫?閲嶇疆
          </button>
        </div>
      </div>
    </Teleport>
    <div class="atlas-header">
      <div>
        <div class="text-label mb-xs">[ 动作图谱 ]</div>
        <h1 class="text-display-md text-primary">3D 融合终端</h1>
      </div>
      <div class="header-right">
        <span class="text-caption text-secondary">操作员: {{ username }}</span>
        <button class="nd-btn" @click="router.push('/app/action-analysis')" style="font-size: 12px; padding: 6px 16px;">
          动作识别
        </button>
        <button class="nd-btn" @click="handleReset" :disabled="!modelLoaded" style="font-size: 12px; padding: 6px 16px;">
          ↺ 重置
        </button>
      </div>
    </div>

    <div class="atlas-grid">
      <div class="atlas-viewport">
        <div class="nd-card viewport-card border-normal">
          <div class="viewport-header">
            <span class="text-label">3D 视窗</span>
            <span class="text-caption" v-if="modelLoaded">● 就绪</span>
            <span class="text-caption text-warning" v-else-if="modelLoading">○ 加载中 {{ loadProgress }}%</span>
            <span class="text-caption" v-else-if="loadError" style="color: #EF4444;">○ 失败</span>
          </div>
          <div class="view-toolbar">
            <button
              v-for="view in viewPresets"
              :key="view.id"
              class="view-chip"
              :class="{ active: activeViewPreset === view.id }"
              @click="applyViewPreset(view.id)"
              :disabled="!modelLoaded && view.id !== 'focus'"
            >
              <span>{{ view.label }}</span>
              <small>{{ view.hint }}</small>
            </button>
          </div>
          <div class="viewport-container" ref="containerRef" @mousemove="handleViewportMouseMove">
            <div class="model-hud">
              <div class="hud-pill">
                <span class="hud-label">当前焦点</span>
                <strong>{{ currentFocusLabel }}</strong>
              </div>
              <div class="hud-note">{{ currentFocusHint }}</div>
            </div>
            <div v-if="showModelTooltip && hoveredMuscleFromModel" class="model-tooltip" :style="{ left: tooltipPos.x + 16 + 'px', top: tooltipPos.y - 10 + 'px' }">
              <div class="tooltip-header">
                <span class="muscle-dot" :style="{ backgroundColor: muscleColorMap[hoveredMuscleFromModel.id] || '#9CA3AF' }"></span>
                <span class="tooltip-name">{{ hoveredMuscleFromModel.name }}</span>
                <span class="tooltip-en">{{ hoveredMuscleFromModel.nameEn }}</span>
              </div>
              <div class="tooltip-body">
                <div class="tooltip-row">
                  <span class="tooltip-label">位置</span>
                  <span class="tooltip-value">{{ hoveredMuscleFromModel.location }}</span>
                </div>
                <div class="tooltip-row">
                  <span class="tooltip-label">功能</span>
                  <span class="tooltip-value">{{ hoveredMuscleFromModel.function }}</span>
                </div>
              </div>
            </div>
            <div v-if="modelLoading" class="viewport-overlay">
              <div class="loading-ring"></div>
              <span class="text-caption text-warning mt-md">加载3D模型... {{ loadProgress }}%</span>
              <div class="progress-bar-container">
                <div class="progress-bar-fill" :style="{ width: loadProgress + '%' }"></div>
              </div>
            </div>
            <div v-if="loadError && !modelLoading" class="viewport-overlay">
              <span style="font-size: 36px; color: #EF4444;">⚠</span>
              <span class="text-caption" style="color: #EF4444;">模型加载失败</span>
              <button class="nd-btn mt-md" @click="handleRetry" style="font-size: 12px; padding: 6px 16px;">↻ 重试</button>
            </div>
          </div>
          <div class="viewport-footer">
            <span class="text-caption">🖱 旋转</span>
            <span class="text-caption">滚轮 缩放</span>
            <span class="text-caption">右键 平移</span>
          </div>
        </div>

        <div v-if="selectedMuscle" class="nd-card muscle-info-card border-warn">
          <div class="info-header">
            <div class="info-title-row">
              <span class="muscle-dot" :style="{ backgroundColor: muscleColorMap[selectedMuscle.id] || '#9CA3AF' }"></span>
              <h3 class="text-heading">{{ selectedMuscle.name }}</h3>
              <span class="text-caption text-secondary ml-sm">{{ selectedMuscle.nameEn }}</span>
            </div>
          </div>
          <div class="info-body">
            <p class="text-body-sm text-secondary">{{ selectedMuscle.description }}</p>
            <div class="tag-list mt-sm">
              <span v-for="ex in selectedMuscle.targetExercises" :key="ex" class="ex-tag">{{ ex }}</span>
            </div>
            <div class="action-links mt-md">
              <button class="action-link" @click="goToTraining">→ 生成训练协议</button>
              <button class="action-link" @click="goToCoach">→ 问 AI 教练</button>
            </div>
          </div>
        </div>
      </div>

      <div class="atlas-panel">
        <div class="panel-search">
          <input
            v-model="searchQuery"
            class="atlas-search-input"
            placeholder="🔍 搜索动作..."
          />
          <div class="chip-bar">
            <button
              v-for="cat in muscleCategories"
              :key="cat.id"
              class="filter-chip"
              :class="{ active: activeMuscleFilter === cat.id || (!activeMuscleFilter && cat.id === 'ALL') }"
              @click="handleMuscleChipClick(cat.id)"
            >{{ cat.label }}</button>
          </div>
        </div>

        <Teleport to="body">
          <div
            v-if="hoveredExercisePreview"
            class="exercise-preview-floating"
            :style="{ left: exercisePreviewPos.x + 'px', top: exercisePreviewPos.y + 'px' }"
          >
            <div
              class="exercise-preview-tooltip"
            >
              <div v-if="hoveredExercisePreview.imageUrl" class="preview-image-wrap">
                <img
                  class="preview-image"
                  :src="hoveredExercisePreview.imageUrl"
                  :alt="hoveredExercisePreview.displayName || hoveredExercisePreview.name"
                  loading="lazy"
                />
              </div>
              <div class="preview-body">
                <div class="preview-title-row">
                  <span class="preview-title">{{ hoveredExercisePreview.displayName }}</span>
                  <span class="preview-badge">
                    {{ difficultyMap[hoveredExercisePreview.difficulty] || hoveredExercisePreview.difficulty }}
                  </span>
                </div>
                <div class="preview-muscle">{{ hoveredExercisePreview.displayPrimaryMuscle }}</div>
                <div class="preview-target">{{ hoveredExercisePreview.displayTarget }}</div>
                <a
                  v-if="hoveredExercisePreview.sourceUrl"
                  class="preview-source"
                  :href="hoveredExercisePreview.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >ACE Reference</a>
              </div>
            </div>
          </div>
        </Teleport>

        <div class="panel-list">
          <div v-if="apiLoading" class="list-loading">
            <span class="status-dot blink"></span>
            <span class="text-caption ml-sm">加载动作库...</span>
          </div>
          <div v-else-if="filteredExercises.length === 0" class="list-empty">
            <span class="text-caption text-secondary">没有匹配的动作</span>
          </div>
          <div
            v-else
            v-for="ex in filteredExercises"
            :key="ex.id || ex.name"
            class="exercise-row"
            :class="{ selected: selectedApiExercise?.id === ex.id || selectedApiExercise?.name === ex.name }"
            @click="handleApiExerciseSelect(ex)"
            @mouseenter="handleExerciseHover(ex, $event)"
            @mouseleave="handleExerciseLeave"
          >
            <div class="ex-row-main">
              <span class="ex-row-name">{{ ex.displayName }}</span>
              <div class="ex-row-meta">
                <span class="meta-stars">{{ '⭐'.repeat(difficultyStars[ex.difficulty] || 1) }}</span>
                <span class="meta-difficulty" :class="'diff-' + (ex.difficulty || '').toLowerCase()">{{ difficultyMap[ex.difficulty] || ex.difficulty }}</span>
              </div>
            </div>
            <div class="ex-row-muscles">
              <span class="text-caption text-primary">{{ ex.displayPrimaryMuscle }}</span>
              <span v-if="ex.displaySecondaryMuscles && ex.displaySecondaryMuscles.length" class="text-caption text-secondary">
                · {{ ex.displaySecondaryMuscles.join(' · ') }}
              </span>
            </div>
          </div>
        </div>

        <div v-if="selectedApiExercise" class="panel-detail">
          <div class="detail-divider"></div>
          <div class="detail-title">
            <div>
              <h3 class="text-heading">{{ selectedApiExercise.displayName }}</h3>
              <div class="text-caption text-secondary mt-xs">{{ selectedApiExercise.displayNameEn }}</div>
            </div>
            <button class="close-detail-btn" @click="selectedApiExercise = null">✕</button>
          </div>
          <div class="detail-content">
            <p class="text-body-sm text-secondary mb-md">{{ selectedApiExercise.displayDescription }}</p>
            <div class="detail-grid">
              <div class="detail-block">
                <span class="text-caption text-primary">执行提示</span>
                <ul class="tips-list" v-if="selectedApiExercise.displayTips && selectedApiExercise.displayTips.length">
                  <li v-for="tip in selectedApiExercise.displayTips" :key="tip" class="text-caption text-secondary">{{ tip }}</li>
                </ul>
                <span v-else class="text-caption text-secondary">暂无提示</span>
              </div>
              <div class="detail-block">
                <span class="text-caption text-primary">肌肉群</span>
                <div class="muscles-list mt-xs">
                  <div class="muscle-item primary-muscle">
                    <span class="dot primary-dot"></span>
                    <span class="text-caption">{{ selectedApiExercise.displayPrimaryMuscle }} (主要)</span>
                  </div>
                  <div v-for="m in (selectedApiExercise.displaySecondaryMuscles || [])" :key="m" class="muscle-item">
                    <span class="dot secondary-dot"></span>
                    <span class="text-caption text-secondary">{{ m }}</span>
                  </div>
                </div>
                <div class="text-caption text-secondary mt-md" v-if="selectedApiExercise.reps || selectedApiExercise.sets">
                  推荐: {{ selectedApiExercise.reps || '-' }} × {{ selectedApiExercise.sets || '-' }} 组
                </div>
              </div>
            </div>
            <div class="detail-actions mt-md">
              <button class="nd-btn primary" @click="goToTraining" style="font-size: 12px; padding: 8px 16px;">→ 生成训练</button>
              <button class="nd-btn" @click="goToCoach" style="font-size: 12px; padding: 8px 16px;">→ 问 AI 教练</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.atlas-page {
  width: 100%;
  height: calc(100dvh - 162px);
  max-height: calc(100dvh - 162px);
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.atlas-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 0 2px;
  flex-shrink: 0;
}

.atlas-header .text-label {
  font-size: 10px;
}

.atlas-header .text-display-md {
  font-size: 1.55rem;
  line-height: 1;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.atlas-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 420px;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}

.atlas-viewport {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 14px;
}

.viewport-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  min-height: 0;
  overflow: hidden;
}

.viewport-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px 8px;
  border-bottom: 1px solid rgba(88, 78, 67, 0.08);
  flex-shrink: 0;
}

.view-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 16px 8px;
  border-bottom: 1px solid rgba(88, 78, 67, 0.08);
  background: rgba(255, 252, 247, 0.74);
}

.view-chip {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 58px;
  padding: 6px 9px;
  border-radius: 10px;
  border: 1px solid rgba(88, 78, 67, 0.12);
  background: rgba(255, 255, 255, 0.58);
  color: var(--text-secondary);
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.view-chip small {
  font-size: 9px;
  color: var(--text-muted);
}

.view-chip:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: rgba(127, 157, 135, 0.24);
  color: var(--text-main);
}

.view-chip.active {
  border-color: transparent;
  background: linear-gradient(135deg, rgba(127, 157, 135, 0.92), rgba(163, 187, 171, 0.92));
  color: #fffaf4;
  box-shadow: 0 12px 24px rgba(127, 157, 135, 0.18);
}

.view-chip.active small {
  color: rgba(255, 250, 244, 0.76);
}

.view-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.viewport-container {
  flex: 1;
  position: relative;
  background:
    radial-gradient(circle at top, rgba(194, 169, 120, 0.16), transparent 30%),
    linear-gradient(180deg, #f6f1e9 0%, #e9e2d6 100%);
  min-height: 0;
}

.viewport-container :deep(canvas) {
  display: block;
  width: 100% !important;
  height: 100% !important;
  position: relative;
  z-index: 1;
}

.viewport-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  z-index: 10;
}

.loading-ring {
  width: 32px;
  height: 32px;
  border: 3px solid rgba(194, 169, 120, 0.2);
  border-top-color: #c2a978;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.progress-bar-container {
  width: 200px;
  height: 4px;
  background: rgba(88, 78, 67, 0.12);
  border-radius: 2px;
  overflow: hidden;
  margin-top: 4px;
}

.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #c2a978, #d8bc8a);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.viewport-footer {
  display: flex;
  justify-content: center;
  gap: 18px;
  padding: 7px 14px;
  border-top: 1px solid rgba(88, 78, 67, 0.08);
  background: rgba(255, 252, 247, 0.74);
  flex-shrink: 0;
}

.model-hud {
  position: absolute;
  left: 16px;
  bottom: 16px;
  z-index: 12;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: min(320px, calc(100% - 32px));
}

.hud-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  padding: 10px 12px;
  border-radius: 14px;
  border: 1px solid rgba(88, 78, 67, 0.1);
  background: rgba(255, 252, 247, 0.84);
  box-shadow: 0 12px 28px rgba(82, 69, 54, 0.1);
  backdrop-filter: blur(10px);
}

.hud-label {
  font-size: 10px;
  letter-spacing: 0.08em;
  color: var(--text-muted);
}

.hud-pill strong {
  font-family: var(--font-heading);
  color: var(--text-main);
}

.hud-note {
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.68);
  border: 1px solid rgba(88, 78, 67, 0.08);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
  box-shadow: 0 10px 22px rgba(82, 69, 54, 0.08);
}

.model-tooltip {
  position: fixed;
  z-index: 1000;
  pointer-events: none;
  background: rgba(255, 252, 247, 0.96);
  border: 1px solid rgba(88, 78, 67, 0.12);
  border-radius: 14px;
  padding: 10px 14px;
  min-width: 180px;
  max-width: 280px;
  box-shadow: 0 14px 30px rgba(82, 69, 54, 0.12);
  backdrop-filter: blur(8px);
  animation: tooltipFadeIn 0.12s ease-out;
}

@keyframes tooltipFadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.tooltip-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(88, 78, 67, 0.08);
}

.tooltip-name {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 700;
  color: var(--primary);
  letter-spacing: 1px;
}

.tooltip-en {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-secondary);
}

.tooltip-body { display: flex; flex-direction: column; gap: 4px; }
.tooltip-row { display: flex; gap: 8px; align-items: baseline; }
.tooltip-label { font-size: 10px; color: var(--text-secondary); white-space: nowrap; min-width: 28px; font-family: var(--font-heading); }
.tooltip-value { font-size: 11px; color: var(--text-main); line-height: 1.4; }

.muscle-info-card {
  padding: 12px 14px;
  flex-shrink: 0;
  max-height: 150px;
  overflow: auto;
}

.info-title-row { display: flex; align-items: center; gap: 8px; }

.muscle-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  display: inline-block;
}

.tag-list { display: flex; gap: 6px; flex-wrap: wrap; }
.ex-tag {
  font-family: var(--font-heading);
  font-size: 10px;
  letter-spacing: 1px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(194, 169, 120, 0.14);
  color: #967a49;
}

.action-links { display: flex; gap: 16px; }
.action-link {
  background: none;
  border: none;
  color: var(--primary);
  cursor: pointer;
  font-family: var(--font-heading);
  font-size: 11px;
  letter-spacing: 1px;
  padding: 0;
}

.action-link:hover { text-decoration: underline; }

.atlas-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  border: 1px solid rgba(88, 78, 67, 0.12);
  border-radius: 16px;
  background: rgba(255, 252, 247, 0.72);
  box-shadow: 0 18px 40px rgba(82, 69, 54, 0.08);
  backdrop-filter: blur(12px);
}

.panel-search {
  padding: 12px;
  border-bottom: 1px solid rgba(88, 78, 67, 0.08);
  flex-shrink: 0;
}

.atlas-search-input {
  width: 100%;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(88, 78, 67, 0.12);
  padding: 10px 14px;
  color: var(--text-main);
  font-family: var(--font-body);
  font-size: 13px;
  outline: none;
  border-radius: 12px;
  box-sizing: border-box;
  transition: border-color 0.15s;
}

.atlas-search-input:focus { border-color: rgba(127, 157, 135, 0.3); }

.chip-bar {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.filter-chip {
  background: rgba(255, 255, 255, 0.54);
  border: 1px solid rgba(88, 78, 67, 0.1);
  color: var(--text-secondary);
  padding: 4px 9px;
  font-family: var(--font-heading);
  font-size: 10px;
  letter-spacing: 1px;
  cursor: pointer;
  border-radius: 999px;
  transition: all 0.15s;
}

.filter-chip:hover { border-color: var(--primary); color: var(--primary); }
.filter-chip.active { background: var(--primary); border-color: var(--primary); color: #fffaf4; }

.panel-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  padding: 10px 10px 0;
}

.exercise-preview-floating {
  position: fixed;
  z-index: 1400;
  width: 288px;
  pointer-events: none;
}

.exercise-preview-tooltip {
  position: relative;
  width: 100%;
  height: 200px;
  display: grid;
  grid-template-rows: 132px minmax(0, 1fr);
  overflow: hidden;
  border-radius: 18px;
  border: 1px solid rgba(88, 78, 67, 0.12);
  background: rgba(255, 252, 247, 0.98);
  box-shadow: 0 20px 38px rgba(82, 69, 54, 0.18);
  backdrop-filter: blur(10px);
  animation: tooltipFadeIn 0.12s ease-out;
}

.preview-image-wrap {
  background:
    linear-gradient(180deg, rgba(194, 169, 120, 0.22), rgba(194, 169, 120, 0.04)),
    rgba(244, 239, 231, 0.92);
}

.preview-image {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.preview-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 0;
  padding: 10px 14px 12px;
  overflow: hidden;
}

.preview-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.preview-title {
  font-family: var(--font-heading);
  font-size: 14px;
  line-height: 1.35;
  color: var(--text-main);
}

.preview-badge {
  flex-shrink: 0;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(127, 157, 135, 0.14);
  color: var(--primary);
  font-family: var(--font-heading);
  font-size: 10px;
  letter-spacing: 0.08em;
}

.preview-muscle {
  font-size: 12px;
  color: var(--primary);
  font-weight: 600;
}

.preview-target {
  font-size: 11px;
  line-height: 1.5;
  color: var(--text-secondary);
}

.preview-source {
  width: fit-content;
  color: #8f6c36;
  font-family: var(--font-heading);
  font-size: 10px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  text-decoration: none;
}

.list-loading, .list-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: var(--warning);
  border-radius: 50%;
  display: inline-block;
}

.status-dot.blink { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0.2; } }

.exercise-row {
  padding: 12px 16px;
  margin-bottom: 8px;
  border-bottom: 1px solid rgba(88, 78, 67, 0.05);
  border-radius: 14px;
  cursor: pointer;
  transition: background 0.15s;
}

.exercise-row:hover { background: rgba(194, 169, 120, 0.08); }
.exercise-row.selected { background: rgba(127, 157, 135, 0.1); border-left: 3px solid var(--primary); }

.ex-row-main { display: flex; justify-content: space-between; align-items: center; }
.ex-row-name { font-family: var(--font-heading); font-size: 15px; font-weight: 700; letter-spacing: 1px; color: var(--text-main); }

.ex-row-meta { display: flex; align-items: center; gap: 8px; }
.meta-stars { font-size: 10px; }
.meta-difficulty {
  font-family: var(--font-heading);
  font-size: 10px;
  letter-spacing: 1px;
  padding: 1px 6px;
  border-radius: 2px;
}

.diff-beginner { background: rgba(126, 168, 137, 0.14); color: #688473; }
.diff-intermediate { background: rgba(201, 167, 107, 0.14); color: #a8864d; }
.diff-advanced { background: rgba(201, 124, 122, 0.14); color: #b56f6c; }

.ex-row-muscles { margin-top: 4px; }

.panel-detail {
  flex-shrink: 0;
  border-top: 1px solid rgba(88, 78, 67, 0.08);
  background: rgba(255, 255, 255, 0.62);
  max-height: 40%;
  overflow-y: auto;
}

.detail-divider { height: 2px; background: linear-gradient(90deg, var(--primary), rgba(127, 157, 135, 0)); }

.detail-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px 8px;
}

.close-detail-btn {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(88, 78, 67, 0.12);
  color: var(--text-secondary);
  cursor: pointer;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  font-size: 12px;
}

.close-detail-btn:hover { border-color: var(--primary); color: var(--primary); }

.detail-content { padding: 0 16px 16px; }

.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }

.tips-list { padding-left: 16px; margin: 6px 0 0; }
.tips-list li { margin-bottom: 4px; line-height: 1.5; }

.muscles-list { display: flex; flex-direction: column; gap: 4px; margin-top: 6px; }
.muscle-item { display: flex; align-items: center; gap: 6px; }
.dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
.primary-dot { background: var(--primary); }
.secondary-dot { background: rgba(156, 163, 175, 0.5); }

.detail-actions { display: flex; gap: 10px; }

@media (max-width: 1024px) {
  .exercise-preview-floating {
    display: none;
  }

  .atlas-page {
    height: calc(100dvh - 150px);
    max-height: calc(100dvh - 150px);
  }

  .atlas-grid {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(260px, 1fr) minmax(220px, 0.85fr);
    height: 100%;
  }

  .atlas-viewport { min-height: 0; }
  .detail-grid { grid-template-columns: 1fr; }
}

@media (max-width: 720px) {
  .atlas-page {
    height: calc(100dvh - 178px);
    max-height: calc(100dvh - 178px);
    gap: 10px;
  }

  .atlas-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .view-toolbar {
    overflow-x: auto;
    flex-wrap: nowrap;
  }

  .viewport-footer,
  .hud-note {
    display: none;
  }

  .model-hud {
    left: 10px;
    bottom: 10px;
  }
}
</style>
