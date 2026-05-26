<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  analyzeExerciseAction,
  type ExerciseActionAnalysisResult,
  type ExerciseRealtimeServerMessage
} from '../api/exercise'
import { useUserStore } from '../stores/user'

type Mode = 'live' | 'upload'
type UploadStatus = 'idle' | 'analyzing' | 'success' | 'error'
type LiveStatus = 'idle' | 'starting' | 'streaming' | 'error'

const userStore = useUserStore()

const mode = ref<Mode>('live')
const uploadStatus = ref<UploadStatus>('idle')
const liveStatus = ref<LiveStatus>('idle')

const fileInputRef = ref<HTMLInputElement | null>(null)
const videoRef = ref<HTMLVideoElement | null>(null)

const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const result = ref<ExerciseActionAnalysisResult | null>(null)
const errorMessage = ref('')
const liveMessage = ref('')
const liveCapturedFrames = ref(0)
const livePoseFrames = ref(0)
const liveRequiredFrames = ref(20)

const recentPhases = computed(() => result.value?.phaseTimeline?.slice(-6) ?? [])
const jointAngles = computed(() => result.value?.jointAngles ?? [])
const formChecks = computed(() => result.value?.formChecks ?? [])
const suggestions = computed(() => result.value?.suggestions ?? [])

const scoreTone = computed(() => {
  const score = result.value?.scorePercent ?? 0
  if (score >= 75) return 'excellent'
  if (score >= 60) return 'steady'
  return 'needs-work'
})

const scoreLabel = computed(() => {
  const score = result.value?.scorePercent ?? 0
  if (score >= 75) return '稳定'
  if (score >= 60) return '可提升'
  return '需调整'
})

const isLiveActive = computed(() => liveStatus.value === 'starting' || liveStatus.value === 'streaming')
const isBusy = computed(() => uploadStatus.value === 'analyzing' || liveStatus.value === 'starting')

let liveSocket: WebSocket | null = null
let liveStream: MediaStream | null = null
let captureTimer: number | null = null
let frameInFlight = false
let manualSocketClose = false
const captureCanvas = document.createElement('canvas')

const revokePreview = () => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

const resetResult = () => {
  result.value = null
  errorMessage.value = ''
}

const resetUploadState = () => {
  uploadStatus.value = 'idle'
  selectedFile.value = null
  revokePreview()
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

const stopCaptureLoop = () => {
  if (captureTimer !== null) {
    window.clearInterval(captureTimer)
    captureTimer = null
  }
  frameInFlight = false
}

const stopMediaStream = () => {
  if (liveStream) {
    liveStream.getTracks().forEach(track => track.stop())
    liveStream = null
  }
  if (videoRef.value) {
    videoRef.value.srcObject = null
  }
}

const closeSocket = () => {
  if (!liveSocket) return
  manualSocketClose = true
  const socket = liveSocket
  liveSocket = null
  if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
    socket.close()
  }
}

const stopLiveSession = (resetState = true) => {
  stopCaptureLoop()
  closeSocket()
  stopMediaStream()
  if (resetState) {
    liveStatus.value = 'idle'
    liveMessage.value = ''
    liveCapturedFrames.value = 0
    livePoseFrames.value = 0
    liveRequiredFrames.value = 20
  }
}

const handleReset = () => {
  resetResult()
  resetUploadState()
  stopLiveSession()
}

const switchMode = (nextMode: Mode) => {
  if (mode.value === nextMode) return
  if (mode.value === 'live') {
    stopLiveSession()
  }
  mode.value = nextMode
  resetResult()
  errorMessage.value = ''
  if (nextMode === 'upload') {
    liveMessage.value = ''
  } else {
    resetUploadState()
  }
}

const handleFileChange = async (event: Event) => {
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
  resetResult()
  revokePreview()

  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  uploadStatus.value = 'analyzing'

  try {
    result.value = await analyzeExerciseAction(file)
    uploadStatus.value = 'success'
  } catch (error: any) {
    console.error('Action analysis failed:', error)
    errorMessage.value = error?.message || '动作识别失败，请检查模型环境或重新上传视频。'
    uploadStatus.value = 'error'
  }
}

const waitForVideoReady = (video: HTMLVideoElement) =>
  new Promise<void>((resolve, reject) => {
    if (video.readyState >= HTMLMediaElement.HAVE_METADATA) {
      resolve()
      return
    }

    const handleLoaded = () => {
      cleanup()
      resolve()
    }
    const handleError = () => {
      cleanup()
      reject(new Error('摄像头预览启动失败'))
    }
    const cleanup = () => {
      video.removeEventListener('loadedmetadata', handleLoaded)
      video.removeEventListener('error', handleError)
    }

    video.addEventListener('loadedmetadata', handleLoaded)
    video.addEventListener('error', handleError)
  })

const buildRealtimeSocketUrl = () => {
  const token = userStore.token || localStorage.getItem('token') || ''
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const base = window.location.host
  return `${protocol}//${base}/ws/exercise/realtime?token=${encodeURIComponent(token)}`
}

const captureAndSendFrame = () => {
  if (!videoRef.value || !liveSocket || liveSocket.readyState !== WebSocket.OPEN || frameInFlight) {
    return
  }

  const video = videoRef.value
  if (video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
    return
  }

  const width = video.videoWidth
  const height = video.videoHeight
  if (!width || !height) {
    return
  }

  const maxWidth = 640
  const scale = Math.min(1, maxWidth / width)
  captureCanvas.width = Math.round(width * scale)
  captureCanvas.height = Math.round(height * scale)

  const context = captureCanvas.getContext('2d')
  if (!context) {
    return
  }

  context.drawImage(video, 0, 0, captureCanvas.width, captureCanvas.height)
  frameInFlight = true
  liveSocket.send(JSON.stringify({
    type: 'frame',
    imageBase64: captureCanvas.toDataURL('image/jpeg', 0.72)
  }))
}

const startCaptureLoop = () => {
  if (captureTimer !== null) return
  captureTimer = window.setInterval(captureAndSendFrame, 280)
}

const handleRealtimeMessage = (raw: string) => {
  const message = JSON.parse(raw) as ExerciseRealtimeServerMessage
  if (message.type === 'ready') {
    liveStatus.value = 'streaming'
    liveRequiredFrames.value = message.requiredFrames
    liveMessage.value = '摄像头已连接，正在收集稳定姿态帧。'
    startCaptureLoop()
    return
  }

  if (message.type === 'pong') {
    return
  }

  frameInFlight = false

  if (message.type === 'pending') {
    liveStatus.value = 'streaming'
    liveCapturedFrames.value = message.capturedFrames
    livePoseFrames.value = message.poseFrames
    liveRequiredFrames.value = message.requiredFrames
    liveMessage.value = `正在建立动作序列：${message.poseFrames}/${message.requiredFrames} 有效姿态帧`
    return
  }

  if (message.type === 'result') {
    liveStatus.value = 'streaming'
    result.value = message.data
    liveCapturedFrames.value = message.data.totalFrames
    livePoseFrames.value = message.data.poseFrames
    liveMessage.value = `实时识别中：已分析 ${message.data.totalFrames} 帧`
    return
  }

  if (message.type === 'error') {
    liveStatus.value = 'error'
    errorMessage.value = message.message
    liveMessage.value = ''
  }
}

const startLiveSession = async () => {
  if (!userStore.token) {
    ElMessage.error('请先登录后再使用实时识别')
    return
  }

  stopLiveSession()
  resetUploadState()
  resetResult()
  mode.value = 'live'
  liveStatus.value = 'starting'
  errorMessage.value = ''
  liveMessage.value = '正在连接摄像头和实时识别服务。'
  liveCapturedFrames.value = 0
  livePoseFrames.value = 0

  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: 960 },
        height: { ideal: 540 },
        facingMode: 'user'
      },
      audio: false
    })
    liveStream = stream

    const video = videoRef.value
    if (!video) {
      throw new Error('摄像头预览组件未就绪')
    }

    video.srcObject = stream
    video.muted = true
    video.playsInline = true
    await waitForVideoReady(video)
    await video.play()

    manualSocketClose = false
    liveSocket = new WebSocket(buildRealtimeSocketUrl())
    liveSocket.onmessage = event => handleRealtimeMessage(event.data)
    liveSocket.onerror = () => {
      liveStatus.value = 'error'
      errorMessage.value = '实时连接失败，请检查后端 WebSocket 和 Python 实时模型进程。'
      liveMessage.value = ''
      stopCaptureLoop()
    }
    liveSocket.onclose = () => {
      stopCaptureLoop()
      if (!manualSocketClose) {
        liveStatus.value = 'error'
        errorMessage.value = errorMessage.value || '实时识别连接已断开'
        stopMediaStream()
      }
      manualSocketClose = false
    }
  } catch (error: any) {
    console.error('Failed to start realtime session:', error)
    liveStatus.value = 'error'
    errorMessage.value = error?.message || '无法启动摄像头识别'
    liveMessage.value = ''
    stopLiveSession(false)
    stopMediaStream()
  }
}

onBeforeUnmount(() => {
  revokePreview()
  stopLiveSession()
})

const formCheckClass = (passed: boolean) => (passed ? 'pass' : 'warn')
</script>

<template>
  <div class="action-analysis-page">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">动作识别</span>
          <strong class="topbar-page-title">Fitness Vision</strong>
          <span class="topbar-page-meta">实时摄像头或上传训练视频，持续返回动作类别、阶段、计数和纠正建议。</span>
        </div>
      </div>
    </Teleport>

    <div class="page-header">
      <div>
        <div class="text-label mb-xs">[ Fitness Vision ]</div>
        <h1 class="text-display-md text-primary">实时动作视觉评分</h1>
        <p class="text-secondary mt-xs">左侧保留完整输入画面，右侧聚合识别结果、阶段判断与训练建议。</p>
      </div>
      <button
        v-if="result || isLiveActive || uploadStatus !== 'idle'"
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
              <h2 class="stage-title">{{ mode === 'live' ? '实时输入视窗' : '上传视频视窗' }}</h2>
              <p class="stage-subtitle">输入源作为主视图固定在左侧，适合边看画面边观察右侧数据反馈。</p>
            </div>

            <div class="mode-switch">
              <button :class="['mode-chip', { active: mode === 'live' }]" @click="switchMode('live')">实时摄像头</button>
              <button :class="['mode-chip', { active: mode === 'upload' }]" @click="switchMode('upload')">视频上传</button>
            </div>
          </div>

          <div class="stage-body">
            <div v-if="mode === 'live'" class="stage-mode-panel live-panel">
              <div class="video-shell stage-viewport-shell">
                <video ref="videoRef" class="camera-video" autoplay muted playsinline />
                <div v-if="!isLiveActive" class="video-overlay">
                  <strong>打开摄像头后开始实时识别</strong>
                  <span>建议全身入镜、机位固定，优先使用正侧前方视角。</span>
                </div>
                <div class="stage-live-badge">
                  <span class="stage-live-dot"></span>
                  <span>{{ liveStatus === 'streaming' ? 'LIVE ANALYSIS' : 'CAMERA READY' }}</span>
                </div>
              </div>

              <div class="stage-controlbar">
                <div class="control-row">
                  <button class="primary-btn" :disabled="isBusy" @click="startLiveSession">
                    {{ isLiveActive ? '重新连接' : '启动实时识别' }}
                  </button>
                  <button class="ghost-btn" :disabled="!isLiveActive" @click="stopLiveSession">停止摄像头</button>
                </div>

                <div class="stage-meta-grid">
                  <div class="status-card status-card-wide" :class="liveStatus">
                    <strong>
                      {{
                        liveStatus === 'starting'
                          ? '连接中'
                          : liveStatus === 'streaming'
                            ? '实时识别中'
                            : liveStatus === 'error'
                              ? '连接异常'
                              : '尚未启动'
                      }}
                    </strong>
                    <p v-if="liveStatus === 'streaming' || liveStatus === 'starting'">{{ liveMessage }}</p>
                    <p v-else-if="liveStatus === 'error'">{{ errorMessage }}</p>
                    <p v-else>点击启动后，页面会打开摄像头并通过 WebSocket 持续返回识别结果。</p>
                  </div>

                  <div class="metric-row stage-metrics">
                    <div class="metric-pill">
                      <span>采集帧</span>
                      <strong>{{ liveCapturedFrames }}</strong>
                    </div>
                    <div class="metric-pill">
                      <span>有效姿态帧</span>
                      <strong>{{ livePoseFrames }}</strong>
                    </div>
                    <div class="metric-pill">
                      <span>最低要求</span>
                      <strong>{{ liveRequiredFrames }}</strong>
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
                    <span class="drop-index">01</span>
                    <strong>上传健身动作视频</strong>
                    <span>建议 3 到 6 秒、机位固定、人物全身入镜，便于后端提取稳定骨架序列。</span>
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
                  <p v-else-if="uploadStatus === 'error'">{{ errorMessage }}</p>
                  <p v-else>上传模式会在视频分析结束后返回一次完整结果。</p>
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
              <h2>{{ result?.labelZh || '等待识别' }}</h2>
              <p>{{ result?.label || 'Start realtime analysis or upload a workout clip.' }}</p>
            </div>
            <div class="score-badge">
              <span class="score-number">{{ result?.scorePercent ?? '--' }}</span>
              <small>分</small>
            </div>
          </div>

          <div class="score-strip">
            <span>{{ scoreLabel }}</span>
            <strong>{{ result?.standard ? '动作识别稳定' : '动作仍需进一步校准' }}</strong>
          </div>

          <p class="score-hint">{{ result?.hint || '识别结果会显示在这里。' }}</p>
        </div>

        <div class="panel-card">
          <div class="detail-header">
            <div class="card-kicker">Phase & Reps</div>
            <span v-if="result" class="text-caption">sequence {{ result.sequenceFrames }} frames</span>
          </div>

          <div v-if="result" class="phase-overview">
            <div class="metric-tile">
              <span>重复计数</span>
              <strong>{{ result.repetitions }}</strong>
            </div>
            <div class="metric-tile">
              <span>当前阶段</span>
              <strong>{{ result.currentPhase }}</strong>
            </div>
          </div>

          <div v-if="result && recentPhases.length" class="timeline-row">
            <div v-for="segment in recentPhases" :key="`${segment.phase}-${segment.startFrame}`" class="timeline-chip">
              <strong>{{ segment.phase }}</strong>
              <span>{{ segment.startFrame }}-{{ segment.endFrame }}</span>
            </div>
          </div>
          <div v-else class="empty-detail">阶段分段和重复计数会在动作序列稳定后显示。</div>
        </div>

        <div class="panel-card">
          <div class="detail-header">
            <div class="card-kicker">Top Predictions</div>
            <span v-if="result" class="text-caption">{{ result.poseFrames }} / {{ result.totalFrames }} frames with pose</span>
          </div>

          <div v-if="result" class="prediction-list">
            <div v-for="item in result.topPredictions" :key="item.label" class="prediction-row">
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
          <div v-else class="empty-detail">识别完成后会展示模型当前最可能的动作类别。</div>
        </div>

        <div class="panel-card">
          <div class="detail-header">
            <div class="card-kicker">Joint Angles</div>
            <span v-if="result" class="text-caption">{{ jointAngles.length }} metrics</span>
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
          <div v-else class="empty-detail">暂未提取到稳定关节角度数据。</div>
        </div>

        <div class="panel-card">
          <div class="detail-header">
            <div class="card-kicker">Form Checks</div>
            <span v-if="result" class="text-caption">{{ result.source }}</span>
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
          <div v-else class="empty-detail">建议会根据动作阶段、关节角和规则检查生成。</div>
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
  gap: 18px;
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
  background: transparent;
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

.mode-chip {
  min-height: 48px;
  border: 1px solid rgba(31, 29, 27, 0.14);
  background: transparent;
  color: var(--text-secondary);
  padding: 0 18px;
  border-radius: 0;
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
  box-shadow: none;
}

.stage-body {
  flex: 1;
  min-height: 0;
  padding: 24px 28px 28px;
  background: transparent;
}

.stage-mode-panel,
.live-panel,
.upload-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  min-height: 0;
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
.preview-video {
  width: 100%;
  height: 100%;
  min-height: 460px;
  object-fit: cover;
  display: block;
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
  max-width: 40ch;
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

.stage-metrics {
  align-self: stretch;
}

.phase-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.primary-btn,
.ghost-btn,
.nd-btn {
  min-height: 52px;
  border: 1px solid rgba(31, 29, 27, 0.16);
  border-radius: 0;
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

.status-card-wide {
  height: 100%;
}

.status-card.starting,
.status-card.analyzing {
  border-left: 4px solid var(--accent);
  background: rgba(111, 125, 135, 0.08);
}

.status-card.streaming,
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
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.metric-pill strong,
.metric-tile strong {
  font-family: var(--font-heading);
  font-size: clamp(1.6rem, 2.2vw, 2.4rem);
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
  border-radius: 0;
}

.file-meta,
.timeline-chip,
.prediction-row,
.angle-row,
.check-item,
.suggestion-item {
  border-radius: 0;
  padding: 14px 16px;
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
  gap: 18px;
  padding: 24px 28px 0;
}

.score-header h2 {
  margin: 6px 0 2px;
  font-size: clamp(2rem, 3.4vw, 3rem);
  color: var(--text-main);
}

.score-header p {
  margin: 0;
  color: var(--text-secondary);
}

.score-badge {
  min-width: 110px;
  padding: 16px 18px;
  border-radius: 0;
  border: 1px solid rgba(31, 29, 27, 0.12);
  background: rgba(255, 251, 246, 0.68);
  text-align: right;
}

.score-number {
  font-size: 42px;
  line-height: 1;
  color: var(--text-main);
  font-weight: 800;
  font-family: var(--font-heading);
}

.score-badge small {
  color: var(--text-secondary);
}

.score-strip {
  margin: 18px 28px 0;
  padding: 16px 18px;
  border-radius: 0;
  border-top: 1px solid rgba(31, 29, 27, 0.12);
  border-bottom: 1px solid rgba(31, 29, 27, 0.12);
  background: rgba(17, 17, 17, 0.04);
}

.score-hint {
  margin: 18px 28px 24px;
  color: var(--text-secondary);
  line-height: 1.8;
}

.detail-header {
  margin-bottom: 0;
  padding: 22px 24px 16px;
  border-bottom: 1px solid rgba(31, 29, 27, 0.08);
}

.timeline-row,
.prediction-list,
.angle-table,
.check-list,
.suggestion-list {
  display: grid;
  gap: 0;
  padding: 0 24px 24px;
}

.prediction-row,
.angle-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 220px);
  gap: 16px;
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
  font-size: 12px;
}

.prediction-meter,
.angle-values {
  display: grid;
  gap: 8px;
}

.prediction-bar {
  height: 9px;
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
  line-height: 1.8;
}

.phase-overview,
.timeline-row,
.prediction-list,
.angle-table,
.check-list,
.suggestion-list {
  padding-top: 18px;
}

.phase-overview,
.empty-detail {
  padding-left: 24px;
  padding-right: 24px;
  padding-bottom: 24px;
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
}

.empty-detail {
  color: var(--text-secondary);
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

  .video-shell,
  .upload-dropzone,
  .stage-viewport-shell,
  .camera-video,
  .preview-video {
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
  .preview-video {
    min-height: 280px;
  }

  .score-badge {
    text-align: left;
  }
}
</style>
