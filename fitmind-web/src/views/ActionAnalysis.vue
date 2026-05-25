<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { analyzeExerciseAction, type ExerciseActionAnalysisResult } from '../api/exercise'

const status = ref<'idle' | 'analyzing' | 'success' | 'error'>('idle')
const fileInputRef = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const previewUrl = ref('')
const result = ref<ExerciseActionAnalysisResult | null>(null)
const errorMessage = ref('')

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

const revokePreview = () => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
}

const handleReset = () => {
  status.value = 'idle'
  selectedFile.value = null
  result.value = null
  errorMessage.value = ''
  revokePreview()
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
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

  revokePreview()
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
  result.value = null
  errorMessage.value = ''
  status.value = 'analyzing'

  try {
    const data = await analyzeExerciseAction(file)
    result.value = data as ExerciseActionAnalysisResult
    status.value = 'success'
  } catch (error: any) {
    console.error('Action analysis failed:', error)
    errorMessage.value = error?.message || '动作识别失败，请检查模型环境或重新上传视频'
    status.value = 'error'
  }
}

onBeforeUnmount(() => {
  revokePreview()
})
</script>

<template>
  <div class="action-analysis-page">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">动作识别</span>
          <strong class="topbar-page-title">Fitness Vision</strong>
          <span class="topbar-page-meta">上传训练视频，返回动作类别、识别评分和纠正建议</span>
        </div>
      </div>
    </Teleport>

    <div class="page-header mb-3xl">
      <div>
        <div class="text-label mb-xs">[ Fitness Vision ]</div>
        <h1 class="text-display-md text-primary">动作视觉评分</h1>
        <p class="text-secondary mt-xs">基于 MediaPipe + PyTorch 的动作识别链路接入当前项目</p>
      </div>
      <button v-if="status !== 'idle'" class="nd-btn" @click="handleReset">重新上传</button>
    </div>

    <div class="analysis-grid">
      <section class="nd-card upload-card">
        <div class="card-kicker">视频输入</div>
        <input
          ref="fileInputRef"
          type="file"
          accept="video/mp4,video/quicktime,video/x-msvideo,video/webm,video/x-matroska"
          class="hidden-input"
          @change="handleFileChange"
        />

        <button class="dropzone" type="button" @click="fileInputRef?.click()">
          <template v-if="previewUrl">
            <video class="preview-video" :src="previewUrl" controls playsinline muted />
          </template>
          <template v-else>
            <div class="drop-copy">
              <span class="drop-index">01</span>
              <strong>上传健身动作视频</strong>
              <span>建议 3-6 秒、固定机位、人物全身入镜</span>
            </div>
          </template>
        </button>

        <div class="file-meta" v-if="selectedFile">
          <span>{{ selectedFile.name }}</span>
          <span>{{ Math.max(1, Math.round(selectedFile.size / 1024 / 1024)) }} MB</span>
        </div>

        <div v-if="status === 'analyzing'" class="status-panel analyzing">
          <span class="pulse-dot"></span>
          <div>
            <strong>视觉模型分析中</strong>
            <p>后端正在调用本地 Python 模型，对视频骨架序列做动作分类。</p>
          </div>
        </div>

        <div v-else-if="status === 'error'" class="status-panel error">
          <strong>识别失败</strong>
          <p>{{ errorMessage }}</p>
        </div>

        <div v-else class="capture-tips">
          <div class="tips-label">拍摄建议</div>
          <p>正侧前方固定视角效果最好。避免裁掉手腕、膝盖和脚踝，确保动作过程连续完整。</p>
        </div>
      </section>

      <section class="result-column">
        <div class="nd-card score-card" :class="scoreTone">
          <div class="score-header">
            <div>
              <div class="card-kicker">识别结果</div>
              <h2>{{ result?.labelZh || '等待分析' }}</h2>
              <p>{{ result?.label || 'Upload a workout clip to start.' }}</p>
            </div>
            <div class="score-badge">
              <span class="score-number">{{ result?.scorePercent ?? '--' }}</span>
              <small>分</small>
            </div>
          </div>

          <div class="score-strip">
            <span>{{ scoreLabel }}</span>
            <strong>{{ result?.standard ? '动作识别稳定' : '动作需进一步校准' }}</strong>
          </div>

          <p class="score-hint">{{ result?.hint || '识别完成后，这里会显示整体判断。' }}</p>
        </div>

        <div class="nd-card detail-card">
          <div class="detail-header">
            <div class="card-kicker">Top Predictions</div>
            <span v-if="result" class="text-caption">{{ result.poseFrames }} / {{ result.totalFrames }} 帧完成骨架提取</span>
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
          <div v-else class="empty-detail">上传视频后展示 Top-3 动作预测。</div>
        </div>

        <div class="nd-card advice-card">
          <div class="detail-header">
            <div class="card-kicker">Correction Notes</div>
            <span v-if="result" class="text-caption">{{ result.source }}</span>
          </div>

          <div v-if="result" class="advice-list">
            <div v-for="tip in result.suggestions" :key="tip" class="advice-item">
              <span class="advice-index">/</span>
              <p>{{ tip }}</p>
            </div>
          </div>
          <div v-else class="empty-detail">这里会返回结合识别结果生成的动作建议。</div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.action-analysis-page {
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}

.analysis-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 24px;
}

.upload-card,
.detail-card,
.advice-card,
.score-card {
  padding: 26px;
}

.result-column {
  display: grid;
  gap: 20px;
}

.card-kicker {
  font-family: var(--font-heading);
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.hidden-input {
  display: none;
}

.dropzone {
  width: 100%;
  min-height: 420px;
  border: 2px dashed rgba(194, 169, 120, 0.45);
  background:
    radial-gradient(circle at top left, rgba(249, 115, 22, 0.14), transparent 36%),
    linear-gradient(135deg, rgba(17, 17, 17, 0.03), rgba(17, 17, 17, 0.08));
  padding: 0;
  cursor: pointer;
  overflow: hidden;
}

.drop-copy {
  min-height: 420px;
  display: grid;
  place-content: center;
  gap: 10px;
  text-align: center;
  padding: 28px;
}

.drop-index {
  font-family: var(--font-heading);
  font-size: 72px;
  line-height: 1;
  color: rgba(249, 115, 22, 0.28);
}

.drop-copy strong {
  font-family: var(--font-heading);
  font-size: 28px;
  color: var(--text-main);
}

.drop-copy span:last-child {
  color: var(--text-secondary);
  line-height: 1.7;
}

.preview-video {
  display: block;
  width: 100%;
  height: 420px;
  object-fit: cover;
  background: #050505;
}

.file-meta,
.detail-header,
.score-strip,
.status-panel,
.advice-item,
.prediction-row {
  display: flex;
  align-items: center;
}

.file-meta,
.detail-header,
.score-strip,
.prediction-row {
  justify-content: space-between;
}

.file-meta {
  margin-top: 14px;
  color: var(--text-secondary);
  font-size: 13px;
}

.status-panel {
  gap: 14px;
  margin-top: 18px;
  padding: 16px 18px;
  border-left: 4px solid var(--primary);
  background: rgba(17, 17, 17, 0.04);
}

.status-panel p,
.capture-tips p,
.score-hint,
.advice-item p {
  margin: 0;
  line-height: 1.7;
}

.status-panel.error {
  border-left-color: #c97c7a;
  background: rgba(201, 124, 122, 0.08);
}

.pulse-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--primary);
  box-shadow: 0 0 0 0 rgba(249, 115, 22, 0.45);
  animation: pulse 1.4s infinite;
}

@keyframes pulse {
  70% { box-shadow: 0 0 0 14px rgba(249, 115, 22, 0); }
  100% { box-shadow: 0 0 0 0 rgba(249, 115, 22, 0); }
}

.capture-tips {
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid rgba(17, 17, 17, 0.1);
}

.tips-label {
  font-family: var(--font-heading);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--primary);
  margin-bottom: 8px;
}

.score-card {
  background:
    linear-gradient(140deg, rgba(249, 115, 22, 0.09), transparent 42%),
    linear-gradient(180deg, rgba(17, 17, 17, 0.02), rgba(17, 17, 17, 0.06));
}

.score-card.steady {
  background:
    linear-gradient(140deg, rgba(59, 130, 246, 0.12), transparent 42%),
    linear-gradient(180deg, rgba(17, 17, 17, 0.02), rgba(17, 17, 17, 0.06));
}

.score-card.needs-work {
  background:
    linear-gradient(140deg, rgba(201, 124, 122, 0.16), transparent 42%),
    linear-gradient(180deg, rgba(17, 17, 17, 0.02), rgba(17, 17, 17, 0.06));
}

.score-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.score-header h2 {
  margin: 0;
  font-family: var(--font-heading);
  font-size: 36px;
  line-height: 1.05;
}

.score-header p {
  margin: 8px 0 0;
  color: var(--text-secondary);
}

.score-badge {
  width: 116px;
  min-width: 116px;
  aspect-ratio: 1;
  border-radius: 50%;
  display: grid;
  place-content: center;
  border: 2px solid rgba(17, 17, 17, 0.12);
  background: rgba(255, 255, 255, 0.6);
}

.score-number {
  font-family: var(--font-heading);
  font-size: 44px;
  line-height: 1;
}

.score-badge small {
  text-align: center;
  color: var(--text-secondary);
}

.score-strip {
  margin: 18px 0 14px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(17, 17, 17, 0.08);
}

.score-strip span {
  color: var(--text-secondary);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
}

.score-hint {
  color: var(--text-main);
}

.prediction-list,
.advice-list {
  display: grid;
  gap: 14px;
}

.prediction-copy {
  display: grid;
  gap: 4px;
}

.prediction-copy strong {
  font-family: var(--font-heading);
  font-size: 18px;
}

.prediction-copy span {
  color: var(--text-secondary);
  font-size: 12px;
}

.prediction-meter {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 180px;
}

.prediction-bar {
  width: 132px;
  height: 8px;
  background: rgba(17, 17, 17, 0.08);
  overflow: hidden;
}

.prediction-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--primary), #ef4444);
}

.prediction-meter strong {
  min-width: 28px;
  text-align: right;
  font-family: var(--font-heading);
}

.advice-item {
  align-items: flex-start;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(17, 17, 17, 0.08);
}

.advice-item:first-child {
  padding-top: 0;
  border-top: none;
}

.advice-index {
  color: var(--primary);
  font-family: var(--font-heading);
  font-size: 24px;
  line-height: 1;
}

.empty-detail {
  color: var(--text-secondary);
  line-height: 1.7;
}

@media (max-width: 1024px) {
  .analysis-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page-header,
  .score-header,
  .prediction-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .dropzone,
  .drop-copy,
  .preview-video {
    min-height: 300px;
    height: 300px;
  }

  .score-badge {
    width: 88px;
    min-width: 88px;
  }

  .score-number {
    font-size: 32px;
  }

  .prediction-meter {
    width: 100%;
    min-width: 0;
  }

  .prediction-bar {
    flex: 1;
    width: auto;
  }
}
</style>
