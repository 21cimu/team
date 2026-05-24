<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useThree } from '../composables/useThree';
import { muscleGroups, exercises, type MuscleGroup } from '../data/muscles';
import { useUserStore } from '../stores/user';

const router = useRouter();
const containerRef = ref<HTMLElement | null>(null);
const selectedMuscle = ref<MuscleGroup | null>(null);
const currentExercise = ref<string | null>(null);
const missingNodes = ref<string[]>([]);
const activeTab = ref<'muscles' | 'exercises'>('muscles');
const hoveredMuscleFromModel = ref<MuscleGroup | null>(null);
const tooltipPos = ref({ x: 0, y: 0 });
const showModelTooltip = ref(false);
const isHoveringList = ref(false);
const hoveredListItemId = ref<string | null>(null);
let hoverDebounceTimer: ReturnType<typeof setTimeout> | null = null;

const userStore = useUserStore();
const username = computed(() => userStore.userInfo?.username || '操作员');

const {
  modelLoaded,
  loading,
  loadProgress,
  loadError,
  init,
  loadModel,
  retryLoad,
  highlightMuscles,
  highlightMeshesByKeywords,
  resetMuscles,
  hoverMeshesByKeywords,
  hoveredMeshName,
  setOnMeshHover,
  registerMuscleGroupMapping,
  getMuscleGroupIdByMesh,
} = useThree();

const MODEL_URL = '/assets/body.glb';

const initDebug = ref<string>('初始化中...');

const buildMeshToMuscleMap = (): Map<string, string> => {
  const map = new Map<string, string>();
  muscleGroups.forEach((mg) => {
    mg.matchKeywords.forEach((keyword) => {
      map.set(keyword.toLowerCase(), mg.id);
    });
  });
  return map;
};

const findMuscleByMeshName = (meshName: string): MuscleGroup | null => {
  const lowerMeshName = meshName.toLowerCase();
  for (const mg of muscleGroups) {
    for (const keyword of mg.matchKeywords) {
      if (lowerMeshName.includes(keyword.toLowerCase())) {
        return mg;
      }
    }
  }
  return null;
};

const clearHoverDebounce = () => {
  if (hoverDebounceTimer) {
    clearTimeout(hoverDebounceTimer);
    hoverDebounceTimer = null;
  }
};

onMounted(async () => {
  console.log('[Muscle3D] onMounted fired, containerRef:', containerRef.value ? 'exists' : 'NULL');
  await nextTick();
  console.log('[Muscle3D] after nextTick, containerRef:', containerRef.value ? 'exists' : 'NULL');

  registerMuscleGroupMapping(buildMeshToMuscleMap());

  setOnMeshHover((meshName: string | null) => {
    if (isHoveringList.value) return;

    if (meshName) {
      const muscle = findMuscleByMeshName(meshName);
      hoveredMuscleFromModel.value = muscle;
      showModelTooltip.value = true;
      if (muscle) {
        hoverMeshesByKeywords(muscle.matchKeywords);
      } else {
        hoverMeshesByKeywords([meshName]);
      }
    } else {
      hoveredMuscleFromModel.value = null;
      showModelTooltip.value = false;
      hoverMeshesByKeywords([]);
    }
  });

  if (containerRef.value) {
    const rect = containerRef.value.getBoundingClientRect();
    console.log('[Muscle3D] container rect:', rect.width, 'x', rect.height);
    initDebug.value = `容器: ${Math.round(rect.width)}x${Math.round(rect.height)}`;

    try {
      await init(containerRef.value);
      initDebug.value = 'Three.js 初始化完成';
      console.log('[Muscle3D] init() completed, loading model...');
    } catch (initError) {
      console.error('[Muscle3D] init() failed:', initError);
      initDebug.value = `初始化失败: ${initError}`;
      return;
    }

    try {
      const meshes = await loadModel(MODEL_URL);
      console.log('[Muscle3D] Model loaded! Meshes:', meshes.length);
      initDebug.value = `模型已加载 (${meshes.length} 网格)`;
    } catch (error) {
      console.error('[Muscle3D] loadModel() failed:', error);
      initDebug.value = `加载失败: ${error}`;
    }
  } else {
    console.error('[Muscle3D] containerRef is NULL after nextTick!');
    initDebug.value = '容器元素未找到';
  }
});

onUnmounted(() => {
  clearHoverDebounce();
});

const handleRetry = async () => {
  try {
    await retryLoad(MODEL_URL);
  } catch (error) {
    console.error('Retry failed:', error);
  }
};

const handleMuscleSelect = (muscle: MuscleGroup) => {
  selectedMuscle.value = muscle;
  currentExercise.value = null;
  missingNodes.value = [];
  const missing = highlightMeshesByKeywords(muscle.matchKeywords);
  if (missing.length > 0) {
    missingNodes.value = missing;
    console.info(`[Muscle3D] 肌肉群 "${muscle.name}" 未匹配关键词(已记录):`, missing);
  }
};

const handleExerciseSelect = (exercise: typeof exercises[0]) => {
  currentExercise.value = exercise.name;
  selectedMuscle.value = null;
  missingNodes.value = [];
  const missing = highlightMeshesByKeywords(exercise.matchKeywords);
  if (missing.length > 0) {
    missingNodes.value = missing;
    console.info(`[Muscle3D] 训练动作 "${exercise.name}" 未匹配关键词(已记录):`, missing);
  }
};

const handleMuscleHover = (muscle: MuscleGroup) => {
  isHoveringList.value = true;
  hoveredMuscleFromModel.value = null;
  showModelTooltip.value = false;
  hoveredListItemId.value = muscle.id;
  clearHoverDebounce();
  hoverMeshesByKeywords(muscle.matchKeywords);
};

const handleMuscleLeave = () => {
  isHoveringList.value = false;
  hoveredListItemId.value = null;
  clearHoverDebounce();
  hoverMeshesByKeywords([]);
};

const handleExerciseHover = (exercise: typeof exercises[0]) => {
  isHoveringList.value = true;
  hoveredMuscleFromModel.value = null;
  showModelTooltip.value = false;
  hoveredListItemId.value = 'ex-' + exercise.name;
  clearHoverDebounce();
  hoverMeshesByKeywords(exercise.matchKeywords);
};

const handleExerciseLeave = () => {
  isHoveringList.value = false;
  hoveredListItemId.value = null;
  clearHoverDebounce();
  hoverMeshesByKeywords([]);
};

const handleViewportMouseMove = (event: MouseEvent) => {
  tooltipPos.value = { x: event.clientX, y: event.clientY };
};

const handleClosePanel = () => {
  selectedMuscle.value = null;
};

const handleReset = () => {
  resetMuscles();
  selectedMuscle.value = null;
  currentExercise.value = null;
  missingNodes.value = [];
  hoveredMuscleFromModel.value = null;
  showModelTooltip.value = false;
  hoveredListItemId.value = null;
  isHoveringList.value = false;
};

const muscleColorMap: Record<string, string> = {
  chest: '#F97316',
  back: '#3B82F6',
  shoulders: '#A855F7',
  biceps: '#22C55E',
  triceps: '#FBBF24',
  abs: '#EF4444',
  legs: '#06B6D4',
  glutes: '#EC4899',
};

const goToTraining = () => {
  router.push('/app/training');
};

const goToCoach = () => {
  router.push('/app/coach');
};
</script>

<template>
  <div class="muscle-3d-content">
    <div class="page-header mb-xl">
      <div>
        <div class="text-label mb-xs">[ 肌肉可视化 ]</div>
        <h1 class="text-display-md text-primary">3D 解剖终端</h1>
        <p class="text-secondary mt-xs">操作员: {{ username }} · 交互式肌肉群映射系统</p>
      </div>
      <button class="nd-btn" @click="handleReset" :disabled="!modelLoaded">
        ↺ 重置模型
      </button>
    </div>

    <div class="main-layout">
      <div class="viewport-section">
        <div class="nd-card viewport-card">
          <div class="viewport-header">
            <span class="text-label">3D 视窗</span>
            <span class="text-caption" v-if="modelLoaded">● 模型就绪</span>
            <span class="text-caption text-warning" v-else-if="loading">○ 加载中... {{ loadProgress }}%</span>
            <span class="text-caption" v-else-if="loadError" style="color: #EF4444;">○ 加载失败</span>
            <span class="text-caption" v-else style="color: #EF4444;">○ 未加载</span>
            <span class="text-caption text-secondary" style="font-size: 10px; margin-left: 8px;">{{ initDebug }}</span>
          </div>
          <div class="viewport-container" ref="containerRef" @mousemove="handleViewportMouseMove">
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
            <div v-if="loading" class="viewport-loading">
              <div class="loading-ring"></div>
              <span class="text-caption text-warning mt-md">正在加载3D模型... {{ loadProgress }}%</span>
              <div class="progress-bar-container">
                <div class="progress-bar-fill" :style="{ width: loadProgress + '%' }"></div>
              </div>
            </div>
            <div v-if="loadError && !loading" class="viewport-error">
              <span class="error-icon">⚠</span>
              <span class="text-caption" style="color: #EF4444;">模型加载失败</span>
              <span class="text-caption text-secondary" style="font-size: 11px; max-width: 300px; text-align: center;">{{ loadError }}</span>
              <button class="nd-btn mt-md" @click="handleRetry" style="font-size: 12px; padding: 6px 16px;">↻ 重试加载</button>
            </div>
            <div v-if="!loading && !modelLoaded && !loadError" class="viewport-placeholder">
              <span class="placeholder-icon">◈</span>
              <span class="text-caption text-secondary">等待模型加载</span>
            </div>
          </div>
          <div class="viewport-footer">
            <span class="text-caption">🖱 左键 旋转</span>
            <span class="text-caption">滚轮 缩放</span>
            <span class="text-caption">右键 平移</span>
          </div>
        </div>
      </div>

      <div class="control-section">
        <div class="nd-card control-card">
          <div class="tab-bar">
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'muscles' }"
              @click="activeTab = 'muscles'"
            >
              肌肉群
            </button>
            <button
              class="tab-btn"
              :class="{ active: activeTab === 'exercises' }"
              @click="activeTab = 'exercises'"
            >
              训练动作
            </button>
          </div>

          <div class="tab-content">
            <div v-if="activeTab === 'muscles'" class="muscle-list">
              <div class="text-caption text-secondary mb-sm">悬停或点击肌肉群以高亮显示</div>
              <button
                v-for="muscle in muscleGroups"
                :key="muscle.id"
                @click="handleMuscleSelect(muscle)"
                @mouseenter="handleMuscleHover(muscle)"
                @mouseleave="handleMuscleLeave"
                class="muscle-item"
                :class="{ selected: selectedMuscle?.id === muscle.id, hovered: hoveredListItemId === muscle.id && !selectedMuscle }"
              >
                <span class="muscle-dot" :style="{ backgroundColor: muscleColorMap[muscle.id] || '#9CA3AF' }"></span>
                <div class="muscle-item-info">
                  <span class="muscle-item-name">{{ muscle.name }}</span>
                  <span class="muscle-item-en">{{ muscle.nameEn }}</span>
                </div>
                <span class="muscle-arrow">→</span>
              </button>
            </div>

            <div v-if="activeTab === 'exercises'" class="exercise-list">
              <div class="text-caption text-secondary mb-sm">悬停或点击训练动作以高亮目标肌肉</div>
              <button
                v-for="exercise in exercises"
                :key="exercise.name"
                @click="handleExerciseSelect(exercise)"
                @mouseenter="handleExerciseHover(exercise)"
                @mouseleave="handleExerciseLeave"
                class="exercise-item"
                :class="{ selected: currentExercise === exercise.name, hovered: hoveredListItemId === 'ex-' + exercise.name && currentExercise !== exercise.name }"
              >
                <div class="exercise-item-main">
                  <span class="exercise-item-name">{{ exercise.name }}</span>
                  <span class="exercise-item-desc">{{ exercise.desc }}</span>
                </div>
                <span class="exercise-arrow">▶</span>
              </button>
            </div>
          </div>
        </div>

        <div v-if="selectedMuscle" class="nd-card detail-card green-shadow">
          <div class="detail-header">
            <div class="detail-title-row">
              <span class="muscle-dot" :style="{ backgroundColor: muscleColorMap[selectedMuscle.id] || '#9CA3AF' }"></span>
              <h3 class="text-heading">{{ selectedMuscle.name }}</h3>
            </div>
            <button class="close-btn" @click="handleClosePanel">✕</button>
          </div>
          <div class="detail-body">
            <div class="detail-row">
              <span class="text-caption text-secondary">英文名</span>
              <span class="text-body-sm" style="color: var(--primary);">{{ selectedMuscle.nameEn }}</span>
            </div>
            <div class="detail-row">
              <span class="text-caption text-secondary">位置</span>
              <span class="text-body-sm">{{ selectedMuscle.location }}</span>
            </div>
            <div class="detail-row">
              <span class="text-caption text-secondary">功能</span>
              <span class="text-body-sm">{{ selectedMuscle.function }}</span>
            </div>
            <div class="detail-block">
              <span class="text-caption text-secondary">描述</span>
              <p class="text-body-sm mt-xs">{{ selectedMuscle.description }}</p>
            </div>
            <div class="detail-block">
              <span class="text-caption text-secondary">推荐训练</span>
              <div class="tag-list mt-xs">
                <span v-for="ex in selectedMuscle.targetExercises" :key="ex" class="ex-tag">{{ ex }}</span>
              </div>
              <div class="action-links mt-md">
                <button class="action-link" @click="goToTraining">→ 生成训练协议</button>
                <button class="action-link" @click="goToCoach">→ 询问 AI 教练</button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="currentExercise && !selectedMuscle" class="nd-card detail-card">
          <div class="detail-header">
            <div class="detail-title-row">
              <span class="muscle-dot" style="backgroundColor: var(--primary);"></span>
              <h3 class="text-heading">{{ currentExercise }}</h3>
            </div>
            <button class="close-btn" @click="handleReset">✕</button>
          </div>
          <div class="detail-body">
            <div class="detail-row">
              <span class="text-caption text-secondary">当前高亮</span>
              <span class="text-body-sm text-primary">{{ currentExercise }}</span>
            </div>
            <div class="detail-block">
              <span class="text-caption text-secondary">提示</span>
              <p class="text-body-sm mt-xs">选择左侧肌肉群或训练动作进行交互式探索</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.muscle-3d-content {
  width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.main-layout {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 24px;
  height: calc(100vh - 180px);
  min-height: 500px;
}

.viewport-section {
  min-height: 0;
}

.viewport-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.viewport-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid rgba(248, 250, 252, 0.1);
}

.viewport-container {
  flex: 1;
  position: relative;
  background: #111827;
  min-height: 0;
}

.model-tooltip {
  position: fixed;
  z-index: 1000;
  pointer-events: none;
  background: rgba(17, 24, 39, 0.95);
  border: 1px solid rgba(249, 115, 22, 0.4);
  border-radius: 4px;
  padding: 10px 14px;
  min-width: 180px;
  max-width: 280px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.5), 0 0 8px rgba(249, 115, 22, 0.15);
  backdrop-filter: blur(8px);
  animation: tooltipFadeIn 0.12s ease-out;
}

@keyframes tooltipFadeIn {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.tooltip-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid rgba(248, 250, 252, 0.1);
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

.tooltip-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tooltip-row {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.tooltip-label {
  font-size: 10px;
  color: var(--text-secondary);
  white-space: nowrap;
  min-width: 28px;
  font-family: var(--font-heading);
  letter-spacing: 0.5px;
}

.tooltip-value {
  font-size: 11px;
  color: var(--text-main);
  line-height: 1.4;
}

.viewport-container :deep(canvas) {
  display: block;
  width: 100% !important;
  height: 100% !important;
  position: relative;
  z-index: 1;
}

.viewport-loading,
.viewport-placeholder,
.viewport-error {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  z-index: 10;
}

.viewport-error {
  background: rgba(17, 24, 39, 0.9);
}

.error-icon {
  font-size: 36px;
  color: #EF4444;
}

.progress-bar-container {
  width: 200px;
  height: 4px;
  background: rgba(248, 250, 252, 0.1);
  border-radius: 2px;
  overflow: hidden;
  margin-top: 4px;
}

.progress-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #FBBF24, #F59E0B);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.loading-ring {
  width: 32px;
  height: 32px;
  border: 3px solid rgba(251, 191, 36, 0.2);
  border-top-color: #FBBF24;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.placeholder-icon {
  font-size: 48px;
  color: rgba(248, 250, 252, 0.1);
}

.viewport-footer {
  display: flex;
  justify-content: center;
  gap: 24px;
  padding: 8px 20px;
  border-top: 1px solid rgba(248, 250, 252, 0.05);
  background: rgba(17, 24, 39, 0.5);
}

.control-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 0;
  overflow-y: auto;
}

.control-card {
  padding: 0;
  display: flex;
  flex-direction: column;
  max-height: 50%;
  min-height: 200px;
}

.tab-bar {
  display: flex;
  border-bottom: 2px solid rgba(248, 250, 252, 0.1);
}

.tab-btn {
  flex: 1;
  padding: 12px 16px;
  background: none;
  border: none;
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.15s;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
}

.tab-btn:hover {
  color: var(--primary);
}

.tab-btn.active {
  color: var(--primary);
  border-bottom-color: var(--primary);
}

.tab-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.muscle-list,
.exercise-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.muscle-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: rgba(248, 250, 252, 0.03);
  border: 1px solid rgba(248, 250, 252, 0.08);
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
  width: 100%;
  color: var(--text-main);
}

.muscle-item:hover {
  background: rgba(249, 115, 22, 0.08);
  border-color: rgba(249, 115, 22, 0.2);
}

.muscle-item.selected {
  background: rgba(249, 115, 22, 0.15);
  border-color: rgba(249, 115, 22, 0.4);
}

.muscle-item.hovered {
  background: rgba(255, 136, 0, 0.1);
  border-color: rgba(255, 136, 0, 0.3);
}

.muscle-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.muscle-item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.muscle-item-name {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1px;
}

.muscle-item-en {
  font-size: 10px;
  color: var(--text-secondary);
  font-family: var(--font-mono);
}

.muscle-arrow {
  color: var(--text-secondary);
  font-size: 12px;
  transition: transform 0.15s;
}

.muscle-item:hover .muscle-arrow {
  transform: translateX(3px);
  color: var(--primary);
}

.exercise-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: rgba(248, 250, 252, 0.03);
  border: 1px solid rgba(248, 250, 252, 0.08);
  border-radius: 3px;
  cursor: pointer;
  transition: all 0.15s;
  text-align: left;
  width: 100%;
  color: var(--text-main);
}

.exercise-item:hover {
  background: rgba(249, 115, 22, 0.08);
  border-color: rgba(249, 115, 22, 0.2);
}

.exercise-item.selected {
  background: rgba(249, 115, 22, 0.15);
  border-color: rgba(249, 115, 22, 0.4);
}

.exercise-item.hovered {
  background: rgba(255, 136, 0, 0.1);
  border-color: rgba(255, 136, 0, 0.3);
}

.exercise-item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.exercise-item-name {
  font-family: var(--font-heading);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 1px;
}

.exercise-item-desc {
  font-size: 10px;
  color: var(--text-secondary);
}

.exercise-arrow {
  color: var(--text-secondary);
  font-size: 10px;
  transition: transform 0.15s;
}

.exercise-item:hover .exercise-arrow {
  transform: translateX(3px);
  color: var(--primary);
}

.detail-card {
  padding: 0;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(248, 250, 252, 0.1);
}

.detail-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.close-btn {
  background: none;
  border: 1px solid rgba(248, 250, 252, 0.15);
  color: var(--text-secondary);
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 2px;
  font-size: 12px;
  transition: all 0.15s;
}

.close-btn:hover {
  border-color: #EF4444;
  color: #EF4444;
}

.detail-body {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  border-bottom: 1px solid rgba(248, 250, 252, 0.06);
}

.detail-block {
  display: flex;
  flex-direction: column;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ex-tag {
  font-family: var(--font-heading);
  font-size: 10px;
  letter-spacing: 1px;
  padding: 3px 10px;
  background: rgba(249, 115, 22, 0.1);
  color: var(--primary);
  border-radius: 2px;
  border: 1px solid rgba(249, 115, 22, 0.2);
}

.action-links {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.action-link {
  background: none;
  border: 1px solid rgba(34, 197, 94, 0.3);
  color: var(--cta);
  padding: 6px 12px;
  font-family: var(--font-heading);
  font-size: 11px;
  letter-spacing: 1px;
  text-transform: uppercase;
  cursor: pointer;
  border-radius: 2px;
  transition: all 0.15s;
  text-align: left;
}

.action-link:hover {
  background: rgba(34, 197, 94, 0.1);
  border-color: var(--cta);
}

.warn-card {
  border-color: rgba(251, 191, 36, 0.4);
  box-shadow: 4px 4px 0px #FBBF24;
}

.warn-tag {
  font-family: var(--font-mono);
  font-size: 10px;
  padding: 2px 8px;
  background: rgba(251, 191, 36, 0.1);
  color: #FBBF24;
  border-radius: 2px;
  border: 1px solid rgba(251, 191, 36, 0.2);
}

@media (max-width: 1024px) {
  .main-layout {
    grid-template-columns: 1fr;
    height: auto;
  }

  .viewport-card {
    min-height: 400px;
  }

  .control-card {
    max-height: none;
  }
}
</style>
