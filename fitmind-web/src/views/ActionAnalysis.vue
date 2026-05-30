<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  analyzeExerciseAction,
  evaluateRealtimeAction,
  type ExerciseActionAnalysisResult,
  type RealtimeActionEvaluationRequest
} from '../api/exercise'
import {
  useLocalPoseCoach,
  type ActionSelectionMode,
  type SupportedRealtimeAction
} from '../composables/useLocalPoseCoach'

type Mode = 'live' | 'upload'
type UploadStatus = 'idle' | 'analyzing' | 'success' | 'error'

const mode = ref<Mode>('live')
const uploadStatus = ref<UploadStatus>('idle')
const fileInputRef = ref<HTMLInputElement | null>(null)
const videoRef = ref<HTMLVideoElement | null>(null)
const overlayRef = ref<HTMLCanvasElement | null>(null)

const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const uploadResult = ref<ExerciseActionAnalysisResult | null>(null)
const uploadError = ref('')
const serverEnhancedResult = ref<ExerciseActionAnalysisResult | null>(null)
const serverEnhanceStatus = ref('')
const serverEnhanceBusy = ref(false)

const {
  status: liveStatus,
  statusMessage: liveStatusMessage,
  supportedActions,
  actionSelectionMode,
  selectedAction,
  manualSelectedAction,
  autoDetectedAction,
  capturedFrames,
  poseFrames,
  requiredFrames,
  result: liveResult,
  isActive,
  start: startLocalCoach,
  stop: stopLocalCoach,
  setSelectedAction,
  setSelectionMode
} = useLocalPoseCoach()

const activeResult = computed(() => {
  if (mode.value !== 'live') {
    return uploadResult.value
  }
  if (!liveResult.value) {
    return null
  }
  if (!serverEnhancedResult.value) {
    return liveResult.value
  }
  return {
    ...liveResult.value,
    label: serverEnhancedResult.value.label,
    labelZh: serverEnhancedResult.value.labelZh,
    score: serverEnhancedResult.value.score,
    scorePercent: serverEnhancedResult.value.scorePercent,
    standard: serverEnhancedResult.value.standard,
    hint: serverEnhancedResult.value.hint,
    suggestions: serverEnhancedResult.value.suggestions,
    topPredictions: serverEnhancedResult.value.topPredictions,
    source: serverEnhancedResult.value.source,
    formChecks: serverEnhancedResult.value.formChecks
  } satisfies ExerciseActionAnalysisResult
})
const recentPhases = computed(() => activeResult.value?.phaseTimeline?.slice(-6) ?? [])
const jointAngles = computed(() => activeResult.value?.jointAngles ?? [])
const formChecks = computed(() => activeResult.value?.formChecks ?? [])
const suggestions = computed(() => activeResult.value?.suggestions ?? [])
const selectedActionMeta = computed(() => supportedActions.find(item => item.key === selectedAction.value) ?? null)
const autoDetectedActionMeta = computed(() => supportedActions.find(item => item.key === autoDetectedAction.value) ?? null)

const scoreTone = computed(() => {
  const score = activeResult.value?.scorePercent ?? 0
  if (score >= 75) return 'excellent'
  if (score >= 60) return 'steady'
  return 'needs-work'
})

const scoreLabel = computed(() => {
  const score = activeResult.value?.scorePercent ?? 0
  if (score >= 75) return '稳定'
  if (score >= 60) return '可提升'
  return '需调整'
})

const liveStatusLabel = computed(() => {
  if (liveStatus.value === 'starting') return '加载中'
  if (liveStatus.value === 'tracking') return '本地识别中'
  if (liveStatus.value === 'error') return '本地识别异常'
  return '尚未启动'
})

let enhanceTimer: number | null = null
let lastEnhanceSignature = ''

function revokePreview() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

function clearEnhancementState() {
  serverEnhancedResult.value = null
  serverEnhanceStatus.value = ''
  serverEnhanceBusy.value = false
  lastEnhanceSignature = ''
  if (enhanceTimer !== null) {
    window.clearTimeout(enhanceTimer)
    enhanceTimer = null
  }
}

function resetUploadState() {
  uploadStatus.value = 'idle'
  uploadError.value = ''
  uploadResult.value = null
  selectedFile.value = null
  revokePreview()
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function stopLiveSession() {
  clearEnhancementState()
  stopLocalCoach()
}

function handleReset() {
  resetUploadState()
  stopLiveSession()
}

function switchMode(nextMode: Mode) {
  if (mode.value === nextMode) return
  if (nextMode === 'upload') {
    stopLiveSession()
  } else {
    resetUploadState()
    clearEnhancementState()
  }
  mode.value = nextMode
}

async function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  const isVideo = file.type.startsWith('video/') || /\.(mp4|mov|avi|webm|mkv)$/i.test(file.name)
  if (!isVideo) {
    ElMessage.error('请上传视频文件')
    target.value = ''
    return
  }

  stopLiveSession()
  mode.value = 'upload'
  resetUploadState()

  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  uploadStatus.value = 'analyzing'

  try {
    uploadResult.value = await analyzeExerciseAction(file)
    uploadStatus.value = 'success'
  } catch (error: any) {
    uploadStatus.value = 'error'
    uploadError.value = error?.message || '动作识别失败，请检查模型环境或重新上传视频。'
  }
}

async function startLiveSession() {
  mode.value = 'live'
  resetUploadState()
  clearEnhancementState()

  if (!videoRef.value || !overlayRef.value) {
    ElMessage.error('实时识别画布还未就绪')
    return
  }

  await startLocalCoach(videoRef.value, overlayRef.value)
}

function handleActionChange(event: Event) {
  const target = event.target as HTMLSelectElement
  clearEnhancementState()
  setSelectedAction(target.value as SupportedRealtimeAction)
}

function handleSelectionModeChange(nextMode: ActionSelectionMode) {
  clearEnhancementState()
  setSelectionMode(nextMode)
}

function buildEnhancementSignature(resultData: ExerciseActionAnalysisResult, actionKey: SupportedRealtimeAction) {
  const poseBucket = Math.floor((resultData.poseFrames ?? 0) / 8)
  return `${actionKey}:${resultData.repetitions}:${resultData.currentPhase}:${poseBucket}`
}

async function requestServerEnhancement(
  resultData: ExerciseActionAnalysisResult,
  actionKey: SupportedRealtimeAction,
  signature: string
) {
  const payload: RealtimeActionEvaluationRequest = {
    actionKey,
    ...resultData
  }

  serverEnhanceBusy.value = true
  serverEnhanceStatus.value = '服务端正在复核最近一段骨架摘要。'

  try {
    const enhanced = await evaluateRealtimeAction(payload)
    if (lastEnhanceSignature !== signature || mode.value !== 'live') {
      return
    }
    serverEnhancedResult.value = enhanced
    serverEnhanceStatus.value = '服务端增强评分已同步，当前显示的是统一口径复核结果。'
  } catch (error: any) {
    if (lastEnhanceSignature !== signature || mode.value !== 'live') {
      return
    }
    serverEnhanceStatus.value = error?.message
      ? `服务端复核暂时失败，当前继续使用本地结果：${error.message}`
      : '服务端复核暂时失败，当前继续使用本地结果。'
  } finally {
    if (lastEnhanceSignature === signature) {
      serverEnhanceBusy.value = false
    }
  }
}

watch([mode, liveResult, selectedAction], ([currentMode, currentLiveResult, currentAction]) => {
  if (currentMode !== 'live') {
    clearEnhancementState()
    return
  }

  if (!currentLiveResult) {
    serverEnhancedResult.value = null
    serverEnhanceStatus.value = ''
    serverEnhanceBusy.value = false
    lastEnhanceSignature = ''
    if (enhanceTimer !== null) {
      window.clearTimeout(enhanceTimer)
      enhanceTimer = null
    }
    return
  }

  if ((currentLiveResult.poseFrames ?? 0) < (currentLiveResult.sequenceFrames ?? 0)) {
    return
  }

  const signature = buildEnhancementSignature(currentLiveResult, currentAction)
  if (signature === lastEnhanceSignature) {
    return
  }
  lastEnhanceSignature = signature

  if (enhanceTimer !== null) {
    window.clearTimeout(enhanceTimer)
  }
  enhanceTimer = window.setTimeout(() => {
    enhanceTimer = null
    requestServerEnhancement(currentLiveResult, currentAction, signature)
  }, 260)
})

onBeforeUnmount(() => {
  revokePreview()
  stopLiveSession()
})

function formCheckClass(passed: boolean) {
  return passed ? 'pass' : 'warn'
}
</script>

<template>
  <div class="action-analysis-page">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">动作识别</span>
          <strong class="topbar-page-title">Realtime Motion Coach</strong>
          <span class="topbar-page-meta">
            实时模式已切换为浏览器端本地姿态识别，上传模式继续保留后端 Python 离线分析。
          </span>
        </div>
      </div>
    </Teleport>

    <div class="page-header">
      <div>
        <div class="text-label mb-xs">[ H5-FIRST MOTION ANALYSIS ]</div>
        <h1 class="text-display-md text-primary">实时动作识别</h1>
        <p class="text-secondary mt-xs">
          实时模式在浏览器本地完成姿态检测和基础反馈，弱网下仍可持续给出计数、阶段和纠错提示。
        </p>
      </div>
      <button
        v-if="activeResult || isActive || uploadStatus !== 'idle'"
        class="nd-btn page-reset-btn"
        @click="handleReset"
      >
        重置
      </button>
    </div>

    <div class="analysis-shell">
      <section class="stage-column">
        <div class="panel-card stage-card">
          <div class="stage-topbar">
            <div class="stage-heading">
              <div class="card-kicker">Input Source</div>
              <h2 class="stage-title">{{ mode === 'live' ? '本地实时识别' : '离线上传分析' }}</h2>
              <p class="stage-subtitle">
                实时模式优先服务移动端和浏览器端场景，不再把摄像头帧持续发送到后端。
              </p>
            </div>

            <div class="mode-switch">
              <button :class="['mode-chip', { active: mode === 'live' }]" @click="switchMode('live')">实时摄像头</button>
              <button :class="['mode-chip', { active: mode === 'upload' }]" @click="switchMode('upload')">视频上传</button>
            </div>
          </div>

          <div class="stage-body">
            <div v-if="mode === 'live'" class="stage-mode-panel live-panel">
              <div class="live-toolbar">
                <div class="toolbar-selection">
                  <div class="selection-mode-switch">
                    <button
                      :class="['selection-chip', { active: actionSelectionMode === 'auto' }]"
                      @click="handleSelectionModeChange('auto')"
                    >
                      自动识别
                    </button>
                    <button
                      :class="['selection-chip', { active: actionSelectionMode === 'manual' }]"
                      @click="handleSelectionModeChange('manual')"
                    >
                      手动覆盖
                    </button>
                  </div>

                  <label class="action-picker" :class="{ disabled: actionSelectionMode === 'auto' }">
                    <span>{{ actionSelectionMode === 'auto' ? '手动覆盖动作' : '当前动作' }}</span>
                    <select
                      :value="manualSelectedAction"
                      :disabled="actionSelectionMode === 'auto'"
                      @change="handleActionChange"
                    >
                      <option v-for="item in supportedActions" :key="item.key" :value="item.key">
                        {{ item.labelZh }}
                      </option>
                    </select>
                  </label>
                </div>
                <div class="toolbar-note">
                  <strong>
                    {{ actionSelectionMode === 'auto'
                      ? `自动锁定：${selectedActionMeta?.labelZh || '等待判断'}`
                      : `手动锁定：${selectedActionMeta?.labelZh || '未选择动作'}` }}
                  </strong>
                  <span v-if="actionSelectionMode === 'auto' && autoDetectedActionMeta">
                    当前最高候选：{{ autoDetectedActionMeta.labelZh }}
                  </span>
                  <span v-else>
                    复杂统一评分仍保留给上传分析链路。
                  </span>
                </div>
              </div>

              <div class="video-shell stage-viewport-shell">
                <video ref="videoRef" class="camera-video mirror" autoplay muted playsinline />
                <canvas ref="overlayRef" class="pose-overlay"></canvas>
                <div v-if="!isActive" class="video-overlay">
                  <strong>打开摄像头后开始本地实时识别</strong>
                  <span>建议全身入镜、机位固定，优先选择正侧前方视角。</span>
                </div>
                <div class="stage-live-badge">
                  <span class="stage-live-dot"></span>
                  <span>{{ isActive ? 'LOCAL POSE' : 'CAMERA READY' }}</span>
                </div>
              </div>

              <div class="stage-controlbar">
                <div class="control-row">
                  <button class="primary-btn" :disabled="liveStatus === 'starting'" @click="startLiveSession">
                    {{ isActive ? '重新启动本地识别' : '启动本地识别' }}
                  </button>
                  <button class="ghost-btn" :disabled="!isActive" @click="stopLiveSession">停止摄像头</button>
                </div>

                <div class="stage-meta-grid">
                  <div class="status-card status-card-wide" :class="liveStatus">
                    <strong>{{ liveStatusLabel }}</strong>
                    <p v-if="liveStatus !== 'error'">{{ liveStatusMessage || '等待启动本地识别。' }}</p>
                    <p v-else>{{ liveStatusMessage || '本地识别启动失败。' }}</p>
                    <p v-if="serverEnhanceStatus" class="server-enhance-note" :class="{ busy: serverEnhanceBusy }">
                      {{ serverEnhanceStatus }}
                    </p>
                  </div>

                  <div class="metric-row stage-metrics">
                    <div class="metric-pill">
                      <span>采样帧</span>
                      <strong>{{ capturedFrames }}</strong>
                    </div>
                    <div class="metric-pill">
                      <span>有效姿态帧</span>
                      <strong>{{ poseFrames }}</strong>
                    </div>
                    <div class="metric-pill">
                      <span>最少要求</span>
                      <strong>{{ requiredFrames }}</strong>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div v-else class="stage-mode-panel upload-panel">
              <input
                ref="fileInputRef"
                type="file"
                accept="video/mp4,video/quicktime,video/x-msvideo,video/webm,video/x-matroska"
                class="hidden-input"
                @change="handleFileChange"
              />

              <button class="upload-dropzone stage-viewport-shell" type="button" @click="fileInputRef?.click()">
                <template v-if="previewUrl">
                  <video class="preview-video" :src="previewUrl" controls playsinline muted />
                </template>
                <template v-else>
                  <div class="drop-copy">
                    <span class="drop-index">Offline Python Analysis</span>
                    <strong>上传训练视频</strong>
                    <span>上传模式仍走后端完整视频分析，适合作为更统一的离线复核和训练记录来源。</span>
                  </div>
                </template>
              </button>

              <div class="stage-controlbar">
                <div v-if="selectedFile" class="file-meta">
                  <span>{{ selectedFile.name }}</span>
                  <span>{{ Math.max(1, Math.round(selectedFile.size / 1024 / 1024)) }} MB</span>
                </div>

                <div class="status-card status-card-wide" :class="uploadStatus">
                  <strong>
                    {{
                      uploadStatus === 'analyzing'
                        ? '视频分析中'
                        : uploadStatus === 'error'
                          ? '识别失败'
                          : uploadStatus === 'success'
                            ? '识别完成'
                            : '等待上传'
                    }}
                  </strong>
                  <p v-if="uploadStatus === 'analyzing'">后端正在调用本地 Python 模型分析完整视频序列。</p>
                  <p v-else-if="uploadStatus === 'error'">{{ uploadError }}</p>
                  <p v-else>离线上传模式会返回更完整的结构化分析结果，适合作为训练记录与复核。</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <aside class="result-column analysis-sidebar">
        <div class="panel-card score-card" :class="scoreTone">
          <div class="score-header">
            <div>
              <div class="card-kicker">识别结果</div>
              <h2>{{ activeResult?.labelZh || '等待识别' }}</h2>
              <p>{{ activeResult?.label || 'Start local realtime analysis or upload a workout clip.' }}</p>
            </div>
            <div class="score-badge">
              <span class="score-number">{{ activeResult?.scorePercent ?? '--' }}</span>
              <small>分</small>
            </div>
          </div>

          <div class="score-strip">
            <span>{{ scoreLabel }}</span>
            <strong>{{ activeResult?.standard ? '动作识别稳定' : '动作仍需进一步校准' }}</strong>
          </div>

          <p class="score-hint">
            {{ activeResult?.hint || '识别结果会在这里显示。本地实时模式优先反馈基础动作质量，上传模式返回更完整结果。' }}
          </p>
        </div>

        <div class="panel-card">
          <div class="detail-header">
            <div class="card-kicker">Phase & Reps</div>
            <span v-if="activeResult" class="text-caption">sequence {{ activeResult.sequenceFrames }} frames</span>
          </div>

          <div v-if="activeResult" class="phase-overview">
            <div class="metric-tile">
              <span>重复计数</span>
              <strong>{{ activeResult.repetitions }}</strong>
            </div>
            <div class="metric-tile">
              <span>当前阶段</span>
              <strong>{{ activeResult.currentPhase }}</strong>
            </div>
          </div>

          <div v-if="activeResult && recentPhases.length" class="timeline-row">
            <div v-for="segment in recentPhases" :key="`${segment.phase}-${segment.startFrame}`" class="timeline-chip">
              <strong>{{ segment.phase }}</strong>
              <span>{{ segment.startFrame }}-{{ segment.endFrame }}</span>
            </div>
          </div>
          <div v-else class="empty-detail">动作阶段和重复计数会在姿态序列稳定后显示。</div>
        </div>

        <div class="panel-card">
          <div class="detail-header">
            <div class="card-kicker">Top Predictions</div>
            <span v-if="activeResult" class="text-caption">{{ activeResult.poseFrames }} / {{ activeResult.totalFrames }} frames with pose</span>
          </div>

          <div v-if="activeResult" class="prediction-list">
            <div v-for="item in activeResult.topPredictions" :key="item.label" class="prediction-row">
              <div class="prediction-copy">
                <strong>{{ item.labelZh }}</strong>
                <span>{{ item.label }}</span>
              </div>
              <div class="prediction-meter">
                <div class="prediction-bar">
                  <div class="prediction-fill" :style="{ width: `${item.scorePercent}%` }"></div>
                </div>
                <strong>{{ item.scorePercent }}</strong>
              </div>
            </div>
          </div>
          <div v-else class="empty-detail">识别完成后会展示当前最可能的动作类别。</div>
        </div>

        <div class="panel-card">
          <div class="detail-header">
            <div class="card-kicker">Joint Metrics</div>
            <span v-if="activeResult" class="text-caption">{{ jointAngles.length }} metrics</span>
          </div>

          <div v-if="jointAngles.length" class="angle-table">
            <div v-for="item in jointAngles" :key="item.key" class="angle-row">
              <div class="angle-name">
                <strong>{{ item.label }}</strong>
                <span>{{ item.key }}</span>
              </div>
              <div class="angle-values">
                <span>当前 {{ item.current }}{{ item.unit }}</span>
                <span>均值 {{ item.average }}{{ item.unit }}</span>
                <span>范围 {{ item.min }}-{{ item.max }}{{ item.unit }}</span>
              </div>
            </div>
          </div>
          <div v-else class="empty-detail">姿态稳定后会显示关节角度和动作幅度指标。</div>
        </div>

        <div class="panel-card">
          <div class="detail-header">
            <div class="card-kicker">Form Checks</div>
            <span v-if="activeResult" class="text-caption">{{ activeResult.source }}</span>
          </div>

          <div v-if="formChecks.length" class="check-list">
            <div v-for="check in formChecks" :key="check.name" class="check-item" :class="formCheckClass(check.passed)">
              <div class="check-head">
                <strong>{{ check.name }}</strong>
                <span>{{ check.passed ? '通过' : '需调整' }}</span>
              </div>
              <p>{{ check.detail }}</p>
            </div>
          </div>
          <div v-else class="empty-detail">规则检查会在动作识别稳定后出现。</div>
        </div>

        <div class="panel-card">
          <div class="detail-header">
            <div class="card-kicker">Suggestions</div>
            <span class="text-caption">coach-style feedback</span>
          </div>

          <div v-if="suggestions.length" class="suggestion-list">
            <div v-for="item in suggestions" :key="item" class="suggestion-item">{{ item }}</div>
          </div>
          <div v-else class="empty-detail">建议会根据当前动作、节奏和基础姿态质量生成。</div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.action-analysis-page {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 24px;
  color: var(--text-main);
}

.page-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.page-reset-btn {
  flex-shrink: 0;
}

.analysis-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) 320px;
  gap: 24px;
  min-height: 0;
  align-items: start;
}

.stage-column {
  min-width: 0;
}

.result-column {
  display: grid;
  gap: 14px;
}

.panel-card {
  position: relative;
  overflow: hidden;
  border: 1px solid rgba(31, 29, 27, 0.16);
  border-radius: 0;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.24), rgba(255, 255, 255, 0) 18%),
    rgba(255, 251, 246, 0.84);
  box-shadow: none;
}

.panel-card::before {
  content: "";
  position: absolute;
  inset: 10px;
  border: 1px solid rgba(17, 17, 17, 0.06);
  pointer-events: none;
}

.stage-card {
  display: flex;
  flex-direction: column;
  min-height: calc(100dvh - 190px);
  padding: 0;
}

.stage-topbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 18px;
  padding: 24px 28px 18px;
  border-bottom: 1px solid rgba(31, 29, 27, 0.12);
}

.stage-heading {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}

.stage-title {
  margin: 0;
  font-family: var(--font-heading);
  font-size: clamp(1.8rem, 3vw, 2.6rem);
  line-height: 0.96;
  color: var(--text-main);
}

.stage-subtitle {
  margin: 0;
  max-width: 62ch;
  color: var(--text-secondary);
  line-height: 1.8;
}

.card-kicker {
  font-size: 12px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--primary);
  margin-bottom: 14px;
  font-weight: 800;
}

.stage-topbar .card-kicker {
  margin-bottom: 0;
}

.mode-switch {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.mode-chip,
.action-picker select,
.primary-btn,
.ghost-btn,
.nd-btn {
  min-height: 48px;
  border: 1px solid rgba(31, 29, 27, 0.14);
  border-radius: 0;
}

.mode-chip {
  background: transparent;
  color: var(--text-secondary);
  padding: 0 18px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.2s ease;
}

.mode-chip.active {
  background: #17181f;
  border-color: #17181f;
  color: var(--text-inverse);
}

.stage-body {
  flex: 1;
  min-height: 0;
  padding: 24px 28px 28px;
}

.stage-mode-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  min-height: 0;
}

.live-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: end;
}

.toolbar-selection {
  display: grid;
  gap: 10px;
}

.selection-mode-switch {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}

.selection-chip {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid rgba(31, 29, 27, 0.14);
  background: transparent;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  cursor: pointer;
}

.selection-chip.active {
  background: #17181f;
  border-color: #17181f;
  color: var(--text-inverse);
}

.action-picker {
  display: grid;
  gap: 8px;
  min-width: 220px;
}

.action-picker.disabled {
  opacity: 0.64;
}

.action-picker span {
  color: var(--text-secondary);
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.action-picker select {
  width: 100%;
  padding: 0 14px;
  background: rgba(255, 251, 246, 0.9);
  color: var(--text-main);
  font-weight: 700;
}

.toolbar-note {
  max-width: 42ch;
  color: var(--text-secondary);
  line-height: 1.6;
  text-align: right;
  display: grid;
  gap: 4px;
}

.toolbar-note strong {
  color: var(--text-main);
  font-size: 13px;
}

.toolbar-note span {
  font-size: 12px;
}

.video-shell,
.upload-dropzone,
.stage-viewport-shell {
  position: relative;
  flex: 1;
  min-height: 460px;
  border-radius: 0;
  border: 1px solid rgba(31, 29, 27, 0.14);
  background:
    linear-gradient(180deg, rgba(22, 24, 31, 0.92), rgba(11, 12, 16, 0.96)),
    repeating-linear-gradient(
      90deg,
      rgba(244, 238, 232, 0.04) 0,
      rgba(244, 238, 232, 0.04) 1px,
      transparent 1px,
      transparent 72px
    );
  overflow: hidden;
}

.upload-dropzone {
  width: 100%;
  cursor: pointer;
  padding: 0;
}

.camera-video,
.preview-video,
.pose-overlay {
  width: 100%;
  height: 100%;
  min-height: 460px;
  display: block;
}

.camera-video,
.preview-video {
  object-fit: cover;
}

.mirror {
  transform: scaleX(-1);
}

.pose-overlay {
  position: absolute;
  inset: 0;
  pointer-events: none;
  transform: scaleX(-1);
}

.video-overlay,
.drop-copy {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 12px;
  color: var(--text-inverse);
  padding: 28px;
  text-align: center;
  background: linear-gradient(180deg, rgba(9, 10, 14, 0.18), rgba(9, 10, 14, 0.48));
}

.video-overlay strong,
.drop-copy strong {
  font-family: var(--font-heading);
  font-size: 1.18rem;
  letter-spacing: 0.04em;
}

.video-overlay span,
.drop-copy span {
  max-width: 42ch;
  color: var(--text-inverse-muted);
  line-height: 1.8;
}

.drop-index {
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(244, 238, 232, 0.52);
}

.stage-live-badge {
  position: absolute;
  top: 18px;
  left: 18px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 0;
  background: rgba(244, 238, 232, 0.08);
  border: 1px solid rgba(244, 238, 232, 0.16);
  color: var(--text-inverse);
  font-size: 11px;
  letter-spacing: 0.12em;
}

.stage-live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
  box-shadow: 0 0 0 5px rgba(113, 136, 113, 0.14);
}

.stage-controlbar {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.control-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.stage-meta-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  gap: 16px;
  align-items: stretch;
}

.metric-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.phase-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.primary-btn,
.ghost-btn,
.nd-btn {
  padding: 0 18px;
  font-weight: 700;
  cursor: pointer;
}

.primary-btn {
  background: #17181f;
  color: var(--text-inverse);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.ghost-btn,
.nd-btn {
  background: transparent;
  color: var(--text-main);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.primary-btn:disabled,
.ghost-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.status-card {
  border-radius: 0;
  padding: 16px 18px;
  background: rgba(255, 251, 246, 0.64);
  border: 1px solid rgba(31, 29, 27, 0.14);
}

.status-card.starting,
.status-card.analyzing {
  border-left: 4px solid var(--accent);
  background: rgba(111, 125, 135, 0.08);
}

.status-card.tracking,
.status-card.success {
  border-left: 4px solid var(--success);
  background: rgba(113, 136, 113, 0.08);
}

.status-card.error {
  border-left: 4px solid var(--error);
  background: rgba(185, 116, 105, 0.08);
}

.status-card strong {
  display: block;
  font-family: var(--font-heading);
  font-size: 1rem;
  color: var(--text-main);
}

.status-card p {
  margin: 10px 0 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.server-enhance-note {
  padding-top: 10px;
  border-top: 1px dashed rgba(31, 29, 27, 0.16);
}

.server-enhance-note.busy {
  color: var(--primary);
}

.metric-pill,
.metric-tile {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 132px;
  padding: 20px 18px;
  border-radius: 0;
  background: rgba(255, 251, 246, 0.68);
  border: 1px solid rgba(31, 29, 27, 0.12);
  justify-content: flex-end;
}

.metric-pill span,
.metric-tile span,
.text-caption {
  color: var(--text-secondary);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metric-pill strong,
.metric-tile strong {
  font-family: var(--font-heading);
  font-size: clamp(1.2rem, 1.7vw, 1.8rem);
  color: var(--text-main);
}

.analysis-sidebar {
  align-content: start;
  position: sticky;
  top: 88px;
  max-height: calc(100dvh - 104px);
  overflow: auto;
  padding-right: 2px;
}

.analysis-sidebar::-webkit-scrollbar {
  width: 8px;
}

.analysis-sidebar::-webkit-scrollbar-thumb {
  background: rgba(31, 29, 27, 0.18);
}

.file-meta,
.timeline-chip,
.prediction-row,
.angle-row,
.check-item,
.suggestion-item {
  border-radius: 0;
  padding: 10px 12px;
  background: rgba(255, 251, 246, 0.64);
  border: 1px solid rgba(31, 29, 27, 0.12);
}

.file-meta,
.check-head,
.score-header,
.detail-header,
.score-strip,
.timeline-chip {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.score-card.excellent {
  border-left: 4px solid var(--success);
}

.score-card.steady {
  border-left: 4px solid var(--accent);
}

.score-card.needs-work {
  border-left: 4px solid var(--warning);
}

.score-header {
  align-items: flex-start;
  gap: 12px;
  padding: 18px 20px 0;
}

.score-header h2 {
  margin: 6px 0 2px;
  font-size: clamp(1.35rem, 2vw, 1.9rem);
  color: var(--text-main);
  line-height: 1.05;
}

.score-header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.score-badge {
  min-width: 84px;
  padding: 10px 12px;
  border-radius: 0;
  border: 1px solid rgba(31, 29, 27, 0.12);
  background: rgba(255, 251, 246, 0.68);
  text-align: right;
}

.score-number {
  font-size: 30px;
  line-height: 1;
  color: var(--text-main);
  font-weight: 800;
  font-family: var(--font-heading);
}

.score-badge small {
  color: var(--text-secondary);
  font-size: 11px;
}

.score-strip {
  margin: 12px 20px 0;
  padding: 10px 12px;
  border-radius: 0;
  border-top: 1px solid rgba(31, 29, 27, 0.12);
  border-bottom: 1px solid rgba(31, 29, 27, 0.12);
  background: rgba(17, 17, 17, 0.04);
}

.score-hint {
  margin: 12px 20px 18px;
  color: var(--text-secondary);
  line-height: 1.6;
  font-size: 13px;
}

.detail-header {
  margin-bottom: 0;
  padding: 16px 18px 12px;
  border-bottom: 1px solid rgba(31, 29, 27, 0.08);
}

.timeline-row,
.prediction-list,
.angle-table,
.check-list,
.suggestion-list {
  display: grid;
  gap: 0;
  padding: 0 18px 18px;
}

.prediction-row,
.angle-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(120px, 168px);
  gap: 12px;
  align-items: center;
}

.prediction-copy,
.angle-name {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.prediction-copy span,
.angle-name span,
.angle-values span,
.file-meta span {
  color: var(--text-secondary);
  font-size: 11px;
  line-height: 1.4;
}

.prediction-meter,
.angle-values {
  display: grid;
  gap: 8px;
}

.prediction-bar {
  height: 7px;
  background: rgba(31, 29, 27, 0.1);
  overflow: hidden;
}

.prediction-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary), #17181f);
}

.check-item.pass {
  border-left: 4px solid var(--success);
}

.check-item.warn {
  border-left: 4px solid var(--warning);
}

.check-item p,
.empty-detail {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.6;
  font-size: 13px;
}

.phase-overview,
.timeline-row,
.prediction-list,
.angle-table,
.check-list,
.suggestion-list {
  padding-top: 12px;
}

.phase-overview,
.empty-detail {
  padding-left: 18px;
  padding-right: 18px;
  padding-bottom: 18px;
}

.suggestion-item {
  border-left: 4px solid rgba(183, 154, 114, 0.42);
}

.file-meta {
  justify-content: flex-start;
}

.file-meta span:last-child {
  margin-left: auto;
}

.timeline-chip strong,
.prediction-copy strong,
.angle-name strong,
.check-head strong {
  color: var(--text-main);
}

.prediction-meter strong {
  color: var(--text-main);
  font-family: var(--font-heading);
  font-size: 13px;
}

.hidden-input {
  display: none;
}

@media (max-width: 1180px) {
  .analysis-shell {
    grid-template-columns: 1fr;
  }

  .analysis-sidebar {
    position: static;
    max-height: none;
    overflow: visible;
    padding-right: 0;
  }

  .stage-card {
    min-height: auto;
  }

  .stage-meta-grid {
    grid-template-columns: 1fr;
  }

  .live-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-note {
    text-align: left;
  }

  .video-shell,
  .upload-dropzone,
  .stage-viewport-shell,
  .camera-video,
  .preview-video,
  .pose-overlay {
    min-height: 380px;
  }
}

@media (max-width: 720px) {
  .page-header,
  .stage-topbar,
  .score-header,
  .detail-header,
  .score-strip,
  .prediction-row,
  .angle-row,
  .control-row,
  .metric-row,
  .phase-overview {
    display: grid;
    grid-template-columns: 1fr;
  }

  .stage-topbar,
  .mode-switch {
    justify-content: flex-start;
  }

  .stage-body {
    padding: 18px;
  }

  .stage-topbar,
  .score-header,
  .detail-header,
  .score-strip,
  .score-hint,
  .phase-overview,
  .empty-detail,
  .timeline-row,
  .prediction-list,
  .angle-table,
  .check-list,
  .suggestion-list {
    padding-left: 18px;
    padding-right: 18px;
  }

  .video-shell,
  .upload-dropzone,
  .stage-viewport-shell,
  .camera-video,
  .preview-video,
  .pose-overlay {
    min-height: 280px;
  }

  .score-badge {
    text-align: left;
  }
}
</style>
