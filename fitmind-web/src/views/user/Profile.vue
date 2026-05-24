<template>
  <div class="profile-content">
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">个人画像</span>
          <strong class="topbar-page-title">身体参数</strong>
          <span class="topbar-page-meta">{{ userDisplayName }}</span>
        </div>
      </div>
    </Teleport>

    <div class="page-header mb-xl">
      <div>
        <div class="text-label mb-xs">[ 用户档案 ]</div>
        <h1 class="text-display-md text-primary">个人中心</h1>
        <p class="text-secondary mt-xs">{{ userDisplayName }}</p>
      </div>
    </div>

    <section class="nd-card profile-hero mb-2xl">
      <div class="profile-hero-copy">
        <div class="profile-hero-kicker">[ Profile Console ]</div>
        <h2 class="profile-hero-title">把训练、饮食和恢复建立在同一份身体档案上</h2>
        <p class="profile-hero-desc">
          这里维护你的身体参数、训练目标和身体限制。AI 生成训练与饮食计划时，会优先读取这份画像。
        </p>
        <div class="profile-hero-tags">
          <span class="profile-hero-tag">{{ profileStatusText }}</span>
          <span class="profile-hero-tag">{{ activityLevelLabel }}</span>
          <span class="profile-hero-tag">{{ primaryGoalLabel }}</span>
        </div>
      </div>
      <div class="profile-hero-metrics">
        <article v-for="metric in headlineMetrics" :key="metric.label" class="hero-metric">
          <span class="hero-metric-label">{{ metric.label }}</span>
          <strong class="hero-metric-value" :class="metric.tone">{{ metric.value }}</strong>
          <p class="hero-metric-note">{{ metric.note }}</p>
        </article>
        <article class="hero-metric hero-quick-card">
          <div class="hero-quick-head">
            <span class="hero-metric-label">[ 快速访问 ]</span>
            <span class="hero-quick-badge">常用入口</span>
          </div>
          <div class="hero-quick-links">
            <button
              v-for="link in quickLinks"
              :key="link.to"
              type="button"
              class="hero-quick-link"
              @click="router.push(link.to)"
            >
              <div class="hero-quick-link-copy">
                <strong>{{ link.title }}</strong>
                <span>{{ link.description }}</span>
              </div>
              <span class="hero-quick-link-arrow">→</span>
            </button>
          </div>
        </article>
      </div>
    </section>

    <div class="profile-grid">
      <div class="profile-main">
        <div class="nd-card form-card">
          <div class="editor-head">
            <div>
              <div class="text-label">[ 档案编辑 ]</div>
              <h2 class="editor-title">基础信息与目标设置</h2>
              <p class="editor-desc">填写越完整，训练计划和饮食建议越贴近你当前的状态。</p>
            </div>
            <div class="editor-completion">
              <span class="editor-completion-label">档案完整度</span>
              <strong class="editor-completion-value">{{ profileCompletionPercent }}%</strong>
            </div>
          </div>

          <div class="profile-sections">
            <section class="profile-section">
              <div class="profile-section-head">
                <div>
                  <div class="text-label">01. 基本指标</div>
                  <h3 class="profile-section-title">建立身体基线</h3>
                </div>
                <p class="profile-section-note">身高、体重、年龄和性别会直接影响热量估算与训练建议。</p>
              </div>
              <div class="grid-2">
                <div class="form-group">
                  <label class="text-caption mb-xs block">身高（厘米）</label>
                  <input v-model.number="form.height" class="nd-input" type="number" step="0.1" />
                </div>
                <div class="form-group">
                  <label class="text-caption mb-xs block">体重（公斤）</label>
                  <input v-model.number="form.weight" class="nd-input" type="number" step="0.1" />
                </div>
                <div class="form-group">
                  <label class="text-caption mb-xs block">年龄</label>
                  <input v-model.number="form.age" class="nd-input" type="number" />
                </div>
                <div class="form-group">
                  <label class="text-caption mb-xs block">性别</label>
                  <div class="radio-group">
                    <button
                      v-for="option in genderOptions"
                      :key="option.value"
                      type="button"
                      class="radio-option"
                      :class="{ active: form.gender === option.value }"
                      @click="form.gender = option.value"
                    >
                      {{ option.label }}
                    </button>
                  </div>
                </div>
              </div>
            </section>

            <section class="profile-section">
              <div class="profile-section-head">
                <div>
                  <div class="text-label">02. 训练上下文</div>
                  <h3 class="profile-section-title">补充身体结构与活动水平</h3>
                </div>
                <p class="profile-section-note">这部分决定计划强度、训练分配和恢复节奏。</p>
              </div>
              <div class="grid-2">
                <div class="form-group">
                  <label class="text-caption mb-xs block">BMI</label>
                  <input :value="bmiSummary" class="nd-input" type="text" readonly />
                </div>
                <div class="form-group">
                  <label class="text-caption mb-xs block">活动水平</label>
                  <select v-model="form.activityLevel" class="nd-input nd-select">
                    <option v-for="option in activityLevelOptions" :key="option.value" :value="option.value">
                      {{ option.label }}
                    </option>
                  </select>
                </div>
                <div class="form-group form-group-full">
                  <label class="text-caption mb-xs block">体型类别</label>
                  <select v-model="form.bodyShape" class="nd-input nd-select">
                    <option value="">请选择体型</option>
                    <option v-for="shape in bodyShapeOptions" :key="shape" :value="shape">{{ shape }}</option>
                  </select>
                </div>
              </div>
            </section>

            <section class="profile-section">
              <div class="profile-section-head">
                <div>
                  <div class="text-label">03. 训练目标</div>
                  <h3 class="profile-section-title">告诉系统你这一阶段在追求什么</h3>
                </div>
                <p class="profile-section-note">可多选，首个目标会作为主要目标写入你的画像。</p>
              </div>
              <div class="selection-summary">
                <span class="selection-count">{{ selectedTrainingGoals.length }}</span>
                <span class="text-caption text-secondary">已选择目标</span>
              </div>
              <div class="tag-grid">
                <button
                  v-for="goal in trainingGoalOptions"
                  :key="goal"
                  type="button"
                  class="radio-option tag-option"
                  :class="{ active: selectedTrainingGoals.includes(goal) }"
                  @click="toggleTrainingGoal(goal)"
                >
                  {{ goal }}
                </button>
              </div>
            </section>

            <section class="profile-section">
              <div class="profile-section-head">
                <div>
                  <div class="text-label">04. 伤病与限制</div>
                  <h3 class="profile-section-title">标记需要规避的部位</h3>
                </div>
                <p class="profile-section-note">如果存在伤病困扰，训练计划会更保守地绕开风险动作。</p>
              </div>
              <div class="radio-group injury-toggle">
                <button type="button" class="radio-option" :class="{ active: !form.hasInjury }" @click="setInjury(false)">
                  无明显伤病
                </button>
                <button type="button" class="radio-option" :class="{ active: form.hasInjury }" @click="setInjury(true)">
                  有需要规避的部位
                </button>
              </div>
              <div v-if="form.hasInjury" class="tag-grid mt-md">
                <button
                  v-for="part in injuryPartOptions"
                  :key="part"
                  type="button"
                  class="radio-option tag-option"
                  :class="{ active: selectedInjuryParts.includes(part) }"
                  @click="toggleInjuryPart(part)"
                >
                  {{ part }}
                </button>
              </div>
              <div v-else class="section-empty-hint">
                当前未设置需要规避的伤病部位。
              </div>
            </section>
          </div>

          <div class="editor-footer">
            <div class="feedback-stack">
              <div v-if="successMessage" class="feedback success-text">{{ successMessage }}</div>
              <div v-if="errorMessage" class="feedback error-text">{{ errorMessage }}</div>
            </div>
            <button class="nd-btn primary editor-submit" :disabled="loading" @click="submitProfile">
              {{ loading ? '[ 保存中... ]' : '保存档案更新' }}
            </button>
          </div>
        </div>
      </div>

      <div class="side-panel">
        <div class="nd-card stats-card">
          <div class="panel-head">
            <div class="text-label">[ 身体洞察 ]</div>
            <span class="panel-badge">{{ hasBodyMetrics ? bmiCategory : '待补充' }}</span>
          </div>
          <template v-if="hasBodyMetrics">
            <div class="bmi-display">
              <div class="bmi-value text-display-md" :style="{ color: bmiColor }">{{ bmiValue }}</div>
              <div class="bmi-label text-caption">BMI</div>
            </div>
            <div class="bmi-category mt-sm text-caption" :style="{ color: bmiColor }">{{ bmiCategory }}</div>
            <div class="bmi-bar mt-md">
              <div class="bmi-fill" :style="{ width: bmiPercent + '%', background: bmiColor }"></div>
            </div>
            <div class="bmi-scale-labels">
              <span class="text-caption">18.5</span>
              <span class="text-caption">24</span>
              <span class="text-caption">28</span>
            </div>
          </template>
          <div v-else class="panel-empty">
            补充身高和体重后，这里会生成 BMI 及身体状态提示。
          </div>

          <div class="body-stats-grid mt-lg">
            <div class="body-stat">
              <div class="text-caption text-secondary">身高</div>
              <div class="text-heading">{{ form.height || '--' }}<span class="text-caption ml-xs">cm</span></div>
            </div>
            <div class="body-stat">
              <div class="text-caption text-secondary">体重</div>
              <div class="text-heading">{{ form.weight || '--' }}<span class="text-caption ml-xs">kg</span></div>
            </div>
            <div class="body-stat">
              <div class="text-caption text-secondary">年龄</div>
              <div class="text-heading">{{ form.age || '--' }}<span class="text-caption ml-xs">岁</span></div>
            </div>
            <div class="body-stat">
              <div class="text-caption text-secondary">体型</div>
              <div class="text-heading">{{ form.bodyShape || '--' }}</div>
            </div>
            <div class="body-stat calorie-stat">
              <div class="calorie-stat-head">
                <div class="text-caption text-secondary">今日热量进度</div>
                <div class="text-heading calorie-stat-value" :class="{ 'over-target': calorieProgressExceeded }">
                  {{ calorieProgressText }}
                </div>
              </div>
              <div class="calorie-progress">
                <div
                  class="calorie-progress-fill"
                  :class="{ 'over-target': calorieProgressExceeded }"
                  :style="{ width: `${calorieProgressPercent}%` }"
                ></div>
              </div>
            </div>
          </div>
        </div>

        <div class="nd-card profile-status-card">
          <div class="panel-head">
            <div class="text-label">[ 档案状态 ]</div>
            <span class="panel-badge">{{ profileCompletionPercent }}%</span>
          </div>
          <p class="status-summary">{{ profileNarrative }}</p>
          <div class="status-checklist">
            <div v-for="item in completionItems" :key="item.label" class="status-check-item">
              <span class="status-check-dot" :class="{ done: item.done }"></span>
              <span class="text-caption">{{ item.label }}</span>
            </div>
          </div>
        </div>

        <div class="nd-card achievements-preview green-shadow">
          <div class="panel-head">
            <div class="text-label">[ 我的成就 ]</div>
            <span class="panel-badge">6 枚徽章位</span>
          </div>
          <div class="achievements-mini-grid">
            <div
              v-for="item in achievementPreview"
              :key="item.title"
              class="mini-ach"
              :class="item.unlocked ? 'unlocked' : 'locked'"
              :title="item.title"
            >
              {{ item.icon }}
            </div>
          </div>
          <button class="nd-btn mt-md card-button" @click="router.push('/app/achievements')">
            查看所有成就
          </button>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onActivated, computed } from 'vue'
import { useUserStore } from '../../stores/user'
import { useRouter } from 'vue-router'
import { getProfile, saveProfile } from '../../api/profile'
import { getTodayDietPlan } from '../../api/ai'
import { getNutritionToday } from '../../api/dashboard'
import { getFoodRecords, type FoodRecord } from '../../api/food'

const userStore = useUserStore()
const router = useRouter()

const loading = ref(false)
const successMessage = ref('')
const errorMessage = ref('')
const calorieProgress = ref({
  consumed: 0,
  target: 0
})

const genderOptions = [
  { label: '男', value: 1 },
  { label: '女', value: 2 },
  { label: '保密', value: 0 }
]

const activityLevelOptions = [
  { label: '久坐', value: 'Sedentary' },
  { label: '轻度活动', value: 'Lightly Active' },
  { label: '活跃', value: 'Active' },
  { label: '非常活跃', value: 'Very Active' }
]

const bodyShapeOptions = ['苹果型', '梨型', '沙漏型', '直筒型']
const trainingGoalOptions = [
  '全身减脂减重',
  '局部变瘦更紧致',
  '增肌，肌肉线条更明显',
  '体态体形改善',
  '保持身体健康',
  '跑步专项提升',
  '运动能力提升'
]
const injuryPartOptions = ['颈肩', '腰背', '膝盖', '踝关节', '髋部', '手腕/肘部', '其他']

const quickLinks = [
  { title: '概览控制台', description: '查看最近状态与系统摘要', to: '/app/dashboard' },
  { title: '训练记录', description: '回看训练执行与历史明细', to: '/app/history' },
  { title: '排行榜', description: '浏览当前积分与排名表现', to: '/app/leaderboard' }
]

const achievementPreview = [
  { title: 'First Blood', icon: '🔥', unlocked: true },
  { title: 'Tonnage King', icon: '🏋️', unlocked: true },
  { title: 'Clean Eater', icon: '🥗', unlocked: true },
  { title: 'Locked', icon: '🔒', unlocked: false },
  { title: 'Locked', icon: '🔒', unlocked: false },
  { title: 'Locked', icon: '🔒', unlocked: false }
]

const selectedTrainingGoals = ref<string[]>([])
const selectedInjuryParts = ref<string[]>([])

const form = reactive({
  height: null as number | null,
  weight: null as number | null,
  age: null as number | null,
  gender: 0,
  fitnessGoal: '',
  trainingGoals: '',
  activityLevel: '',
  bodyShape: '',
  hasInjury: false,
  injuryParts: ''
})

const userDisplayName = computed(() => userStore.userInfo?.username || '当前用户')
const hasBodyMetrics = computed(() => !!form.height && !!form.weight)

const normalizeBodyShape = (value?: string | null) => {
  const text = (value || '').trim()
  if (!text) return ''
  if (text.includes('苹果') || text.includes('鑻规灉')) return '苹果型'
  if (text.includes('梨') || text.includes('姊')) return '梨型'
  if (text.includes('沙漏') || text.includes('娌欐紡')) return '沙漏型'
  if (text.includes('直筒') || text.includes('鐩寸瓛')) return '直筒型'
  return text
}

const splitTags = (value?: string | null) => {
  if (!value) return []
  return value.split(',').map(item => item.trim()).filter(Boolean)
}

const toggleTrainingGoal = (goal: string) => {
  if (selectedTrainingGoals.value.includes(goal)) {
    selectedTrainingGoals.value = selectedTrainingGoals.value.filter(item => item !== goal)
  } else {
    selectedTrainingGoals.value = [...selectedTrainingGoals.value, goal]
  }
}

const setInjury = (hasInjury: boolean) => {
  form.hasInjury = hasInjury
  if (!hasInjury) {
    selectedInjuryParts.value = []
  }
}

const toggleInjuryPart = (part: string) => {
  if (selectedInjuryParts.value.includes(part)) {
    selectedInjuryParts.value = selectedInjuryParts.value.filter(item => item !== part)
  } else {
    selectedInjuryParts.value = [...selectedInjuryParts.value, part]
  }
}

const bmiValue = computed(() => {
  if (!form.height || !form.weight) return '--'
  const h = form.height / 100
  return (form.weight / (h * h)).toFixed(1)
})

const bmiSummary = computed(() => {
  if (bmiValue.value === '--') return '填写身高和体重后自动计算'
  return `${bmiValue.value} (${bmiCategory.value})`
})

const bmiCategory = computed(() => {
  const bmi = parseFloat(bmiValue.value)
  if (isNaN(bmi)) return ''
  if (bmi < 18.5) return '偏瘦'
  if (bmi < 24) return '正常'
  if (bmi < 28) return '超重'
  return '肥胖'
})

const bmiColor = computed(() => {
  const bmi = parseFloat(bmiValue.value)
  if (isNaN(bmi)) return 'var(--text-secondary)'
  if (bmi < 18.5) return '#60a5fa'
  if (bmi < 24) return '#22C55E'
  if (bmi < 28) return '#FBBF24'
  return '#ef4444'
})

const bmiPercent = computed(() => {
  const bmi = parseFloat(bmiValue.value)
  if (isNaN(bmi)) return 0
  return Math.min(Math.max(((bmi - 15) / 25) * 100, 0), 100)
})

const calorieProgressText = computed(() => {
  if (!calorieProgress.value.target) return `${calorieProgress.value.consumed}/-- 千卡`
  return `${calorieProgress.value.consumed}/${calorieProgress.value.target} 千卡`
})

const calorieProgressPercent = computed(() => {
  if (calorieProgress.value.target <= 0) return 0
  return Math.min((calorieProgress.value.consumed / calorieProgress.value.target) * 100, 100)
})

const calorieProgressExceeded = computed(() => {
  return calorieProgress.value.target > 0 && calorieProgress.value.consumed > calorieProgress.value.target
})

const activityLevelLabel = computed(() => {
  return activityLevelOptions.find(option => option.value === form.activityLevel)?.label || '活动水平待设置'
})

const primaryGoalLabel = computed(() => {
  return selectedTrainingGoals.value[0] || '训练目标待设置'
})

const completionItems = computed(() => {
  const items = [
    { label: '身高体重', done: !!form.height && !!form.weight },
    { label: '年龄', done: !!form.age },
    { label: '活动水平', done: !!form.activityLevel },
    { label: '体型类别', done: !!form.bodyShape },
    { label: '训练目标', done: selectedTrainingGoals.value.length > 0 },
    { label: '伤病设置', done: !form.hasInjury || selectedInjuryParts.value.length > 0 }
  ]
  return items
})

const profileCompletionPercent = computed(() => {
  const items = completionItems.value
  const done = items.filter(item => item.done).length
  return Math.round((done / items.length) * 100)
})

const profileStatusText = computed(() => {
  if (profileCompletionPercent.value >= 100) return '画像已完整'
  if (profileCompletionPercent.value >= 67) return '画像接近完成'
  if (profileCompletionPercent.value >= 34) return '画像基础已建立'
  return '画像待完善'
})

const profileNarrative = computed(() => {
  if (!hasBodyMetrics.value) return '先补齐身高和体重，系统才能生成更可靠的身体状态判断。'
  if (form.hasInjury && selectedInjuryParts.value.length === 0) return '你已开启伤病规避，但还没有标记具体部位。'
  if (selectedTrainingGoals.value.length === 0) return '建议至少补充一个训练目标，方便系统给出明确方向。'
  if (profileCompletionPercent.value < 100) return '画像已经具备基础信息，再补充剩余字段后建议会更稳定。'
  return '当前画像信息较完整，后续训练与饮食建议会基于这份档案协同生成。'
})

const heroMetrics = computed(() => [
  {
    label: 'BMI',
    value: bmiValue.value,
    note: hasBodyMetrics.value ? bmiCategory.value || '身体状态' : '等待身体数据',
    tone: 'tone-body'
  },
  {
    label: '热量进度',
    value: calorieProgressText.value,
    note: calorieProgressExceeded.value ? '今日已超过目标' : '今日摄入与目标对照',
    tone: calorieProgressExceeded.value ? 'tone-warning' : 'tone-energy'
  },
  {
    label: '训练目标',
    value: `${selectedTrainingGoals.value.length} 项`,
    note: primaryGoalLabel.value,
    tone: 'tone-goal'
  },
  {
    label: '伤病规避',
    value: form.hasInjury ? `${selectedInjuryParts.value.length} 处` : '无',
    note: form.hasInjury ? '已启用限制条件' : '可正常安排训练',
    tone: form.hasInjury ? 'tone-warning' : 'tone-safe'
  }
])

const headlineMetrics = computed(() => heroMetrics.value.slice(0, 3))

const getLocalDateKey = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const parseRecordDateKey = (value?: string | null) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return getLocalDateKey(date)
}

const fetchCalorieProgress = async () => {
  const todayKey = getLocalDateKey(new Date())
  const [nutritionRes, recordsRes, todayPlanRes] = await Promise.allSettled([
    getNutritionToday(),
    getFoodRecords(),
    getTodayDietPlan()
  ])

  let target = 0
  if (todayPlanRes.status === 'fulfilled' && todayPlanRes.value?.planDate) {
    target = Math.round(Number(todayPlanRes.value?.totalCalories) || 0)
  } else if (nutritionRes.status === 'fulfilled' && nutritionRes.value?.isToday !== false) {
    target = Math.round(Number(nutritionRes.value?.calories) || 0)
  }

  let consumed = 0
  if (recordsRes.status === 'fulfilled' && Array.isArray(recordsRes.value)) {
    consumed = Math.round(recordsRes.value
      .filter((record: FoodRecord) => parseRecordDateKey(record.createTime) === todayKey)
      .reduce((sum: number, record: FoodRecord) => sum + (Number(record.calories) || 0), 0))
  }

  if (consumed <= 0 && todayPlanRes.status === 'fulfilled' && todayPlanRes.value?.status === 1) {
    consumed = Math.max(consumed, Math.round(Number(todayPlanRes.value?.totalCalories) || 0))
  }

  calorieProgress.value = { consumed, target }
}

const fetchProfile = async () => {
  try {
    const res: any = await getProfile()
    if (res) {
      Object.assign(form, {
        height: res.height ?? null,
        weight: res.weight ?? null,
        age: res.age ?? null,
        gender: res.gender ?? 0,
        fitnessGoal: res.fitnessGoal || '',
        trainingGoals: res.trainingGoals || res.fitnessGoal || '',
        activityLevel: res.activityLevel || '',
        bodyShape: normalizeBodyShape(res.bodyShape),
        hasInjury: !!res.hasInjury,
        injuryParts: res.injuryParts || ''
      })
      selectedTrainingGoals.value = splitTags(res.trainingGoals || res.fitnessGoal)
      selectedInjuryParts.value = splitTags(res.injuryParts)
    }
  } catch {
    // profile might not exist yet
  }
}

const submitProfile = async () => {
  loading.value = true
  successMessage.value = ''
  errorMessage.value = ''
  try {
    const trainingGoals = selectedTrainingGoals.value.join(',')
    const injuryParts = form.hasInjury ? selectedInjuryParts.value.join(',') : ''

    await saveProfile({
      ...form,
      bodyShape: normalizeBodyShape(form.bodyShape),
      trainingGoals,
      fitnessGoal: selectedTrainingGoals.value[0] || form.fitnessGoal,
      injuryParts,
      profileCompleted: true
    })

    form.trainingGoals = trainingGoals
    form.injuryParts = injuryParts
    userStore.setProfilePromptRequired(false)
    successMessage.value = '[ 个人资料已更新成功 ]'
    setTimeout(() => {
      successMessage.value = ''
    }, 3000)
  } catch (e: any) {
    errorMessage.value = '更新失败：' + (e.message || '请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchProfile()
  fetchCalorieProgress()
})

onActivated(() => {
  fetchProfile()
  fetchCalorieProgress()
})
</script>

<style scoped>
.profile-content {
  width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.profile-hero {
  position: relative;
  overflow: hidden;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.95fr);
  gap: 24px;
  padding: 30px;
  border: 1px solid rgba(194, 169, 120, 0.16);
  background:
    radial-gradient(circle at top right, rgba(194, 169, 120, 0.14), transparent 34%),
    linear-gradient(135deg, rgba(255, 251, 246, 0.94), rgba(247, 243, 235, 0.9));
  box-shadow: 0 20px 42px rgba(88, 78, 67, 0.08);
}

.profile-hero::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.34), transparent 36%),
    linear-gradient(180deg, transparent, rgba(255, 255, 255, 0.18));
  pointer-events: none;
}

.profile-hero-copy,
.profile-hero-metrics {
  position: relative;
  z-index: 1;
}

.profile-hero-copy {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 240px;
}

.profile-hero-kicker {
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #b79a72;
  font-weight: 700;
  margin-bottom: 14px;
}

.profile-hero-title {
  margin: 0;
  max-width: 12ch;
  font-family: var(--font-heading);
  font-size: clamp(2rem, 3vw, 2.8rem);
  line-height: 1.02;
  letter-spacing: -0.04em;
  color: var(--text-main);
}

.profile-hero-desc {
  max-width: 560px;
  margin: 18px 0 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.profile-hero-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 24px;
}

.profile-hero-tag {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(88, 78, 67, 0.12);
  background: rgba(255, 255, 255, 0.58);
  color: var(--text-main);
  font-size: 0.78rem;
  font-weight: 600;
}

.profile-hero-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  align-content: stretch;
}

.hero-metric {
  padding: 18px;
  border-radius: 18px;
  border: 1px solid rgba(88, 78, 67, 0.1);
  background: rgba(255, 255, 255, 0.62);
  backdrop-filter: blur(10px);
}

.hero-quick-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hero-quick-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.hero-quick-badge {
  font-size: 0.7rem;
  color: var(--text-secondary);
  letter-spacing: 0.08em;
}

.hero-quick-links {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: auto;
}

.hero-quick-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 48px;
  padding: 10px 0;
  border: 0;
  border-top: 1px solid rgba(88, 78, 67, 0.08);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: transform 0.14s ease, color 0.14s ease;
}

.hero-quick-link:first-of-type {
  border-top: 0;
  padding-top: 0;
}

.hero-quick-link:hover {
  transform: translateX(2px);
}

.hero-quick-link-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.hero-quick-link-copy strong {
  font-family: var(--font-heading);
  font-size: 0.92rem;
  color: var(--text-main);
}

.hero-quick-link-copy span {
  font-size: 0.78rem;
  line-height: 1.45;
  color: var(--text-secondary);
}

.hero-quick-link-arrow {
  flex-shrink: 0;
  color: var(--text-secondary);
  font-family: var(--font-heading);
}

.hero-metric-label {
  display: block;
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.hero-metric-value {
  display: block;
  margin-top: 14px;
  font-family: var(--font-heading);
  font-size: 1.6rem;
  line-height: 1;
  color: var(--text-main);
}

.hero-metric-value.tone-body { color: #7f9d87; }
.hero-metric-value.tone-energy { color: #b79a72; }
.hero-metric-value.tone-goal { color: #17181f; }
.hero-metric-value.tone-warning { color: #dc2626; }
.hero-metric-value.tone-safe { color: #4b7a57; }

.hero-metric-note {
  margin: 10px 0 0;
  font-size: 0.8rem;
  line-height: 1.55;
  color: var(--text-secondary);
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(340px, 0.82fr) !important;
  gap: 26px;
  align-items: start;
}

.profile-main,
.side-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.side-panel {
  position: sticky;
  top: 24px;
  align-self: start;
}

.form-card {
  padding: 28px;
}

.editor-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 22px;
  border-bottom: 1px solid rgba(88, 78, 67, 0.08);
}

.editor-title {
  margin: 10px 0 8px;
  font-family: var(--font-heading);
  font-size: 1.5rem;
  line-height: 1.1;
  color: var(--text-main);
}

.editor-desc {
  margin: 0;
  max-width: 520px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.editor-completion {
  min-width: 120px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(194, 169, 120, 0.16);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.82), rgba(247, 242, 233, 0.94));
  text-align: right;
}

.editor-completion-label {
  display: block;
  font-size: 0.74rem;
  letter-spacing: 0.08em;
  color: var(--text-secondary);
}

.editor-completion-value {
  display: block;
  margin-top: 12px;
  font-family: var(--font-heading);
  font-size: 2rem;
  line-height: 1;
  color: var(--text-main);
}

.profile-sections {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 22px;
}

.profile-section {
  padding: 22px;
  border-radius: 22px;
  border: 1px solid rgba(88, 78, 67, 0.08);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(250, 247, 241, 0.76));
}

.profile-section-head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.profile-section-title {
  margin: 10px 0 0;
  font-family: var(--font-heading);
  font-size: 1.15rem;
  color: var(--text-main);
}

.profile-section-note {
  margin: 0;
  max-width: 320px;
  color: var(--text-secondary);
  line-height: 1.65;
  font-size: 0.84rem;
  text-align: right;
}

.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group-full {
  grid-column: 1 / -1;
}

.radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.radio-option {
  flex: 1;
  min-height: 46px;
  padding: 11px 14px;
  border: 1px solid rgba(88, 78, 67, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.54);
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-align: center;
  cursor: pointer;
  transition: all 0.18s ease;
}

.radio-option:hover {
  border-color: rgba(127, 157, 135, 0.28);
  color: var(--text-main);
  transform: translateY(-1px);
}

.radio-option.active {
  border-color: transparent;
  background: linear-gradient(135deg, rgba(127, 157, 135, 0.9), rgba(166, 187, 171, 0.9));
  color: #fffaf4;
  box-shadow: 0 10px 22px rgba(127, 157, 135, 0.18);
}

.tag-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.tag-option {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 54px;
  line-height: 1.4;
}

.selection-summary {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(194, 169, 120, 0.1);
}

.selection-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: rgba(194, 169, 120, 0.18);
  color: var(--text-main);
  font-family: var(--font-heading);
  font-weight: 800;
}

.injury-toggle {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.section-empty-hint {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px dashed rgba(88, 78, 67, 0.12);
  color: var(--text-secondary);
  font-size: 0.84rem;
}

.nd-select {
  appearance: none;
  background: var(--surface);
  cursor: pointer;
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 18px;
  margin-top: 24px;
}

.feedback-stack {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feedback {
  font-family: var(--font-heading);
  font-size: 12px;
  letter-spacing: 0.06em;
  padding: 10px 14px;
  border-radius: 12px;
}

.success-text {
  background: rgba(127, 157, 135, 0.12);
  border: 1px solid rgba(127, 157, 135, 0.22);
  color: #688473;
}

.error-text {
  background: rgba(239, 68, 68, 0.1);
  border: 1px solid rgba(201, 124, 122, 0.22);
  color: #b56f6c;
}

.editor-submit {
  min-width: 180px;
}

.stats-card,
.profile-status-card,
.achievements-preview,
.quick-links-card {
  padding: 22px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-badge {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(88, 78, 67, 0.1);
  color: var(--text-secondary);
  font-size: 0.74rem;
  font-weight: 600;
}

.panel-empty {
  padding: 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.54);
  color: var(--text-secondary);
  line-height: 1.65;
  font-size: 0.84rem;
}

.bmi-display {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.bmi-value {
  line-height: 1;
}

.bmi-label {
  opacity: 0.6;
}

.bmi-category {
  font-family: var(--font-heading);
  font-size: 13px;
  letter-spacing: 1px;
}

.bmi-bar {
  height: 4px;
  background: rgba(111, 103, 93, 0.12);
  border-radius: 2px;
  overflow: hidden;
}

.bmi-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.5s ease, background 0.5s ease;
}

.bmi-scale-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  opacity: 0.4;
}

.body-stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  border-top: 1px solid rgba(88, 78, 67, 0.08);
  padding-top: 14px;
}

.body-stat {
  min-width: 0;
  padding: 12px 0;
}

.calorie-stat {
  grid-column: 1 / -1;
}

.calorie-stat-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
}

.calorie-stat-value {
  text-align: right;
  font-size: 20px;
  line-height: 1.1;
}

.calorie-stat-value.over-target {
  color: #dc2626;
}

.calorie-progress {
  margin-top: 10px;
  height: 8px;
  border-radius: 999px;
  overflow: hidden;
  background: rgba(111, 103, 93, 0.12);
}

.calorie-progress-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #7f9d87 0%, #c2a978 100%);
  transition: width 0.35s ease;
}

.calorie-progress-fill.over-target {
  background: linear-gradient(90deg, #f87171 0%, #dc2626 100%);
}

.status-summary {
  margin: 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.status-checklist {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.status-check-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 0 2px;
  border-top: 1px solid rgba(88, 78, 67, 0.06);
}

.status-check-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: rgba(88, 78, 67, 0.16);
}

.status-check-dot.done {
  background: #7f9d87;
  box-shadow: 0 0 0 4px rgba(127, 157, 135, 0.14);
}

.achievements-mini-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.mini-ach {
  width: 100%;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  border-radius: 14px;
  cursor: pointer;
}

.mini-ach.unlocked {
  background: rgba(194, 169, 120, 0.14);
  border: 1px solid rgba(194, 169, 120, 0.22);
}

.mini-ach.locked {
  background: rgba(255, 255, 255, 0.44);
  border: 1px solid rgba(88, 78, 67, 0.08);
  opacity: 0.4;
  filter: grayscale(1);
}

.card-button {
  width: 100%;
}

.quick-link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  width: 100%;
  padding: 14px 0;
  border: 0;
  border-top: 1px solid rgba(88, 78, 67, 0.08);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: transform 0.14s ease, color 0.14s ease;
}

.quick-link:first-of-type {
  margin-top: 6px;
}

.quick-link:hover {
  transform: translateX(2px);
}

.quick-link-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.quick-link-copy strong {
  font-family: var(--font-heading);
  font-size: 0.95rem;
  color: var(--text-main);
}

.quick-link-copy span {
  color: var(--text-secondary);
}

.quick-link-arrow {
  flex-shrink: 0;
  color: var(--text-secondary);
  font-family: var(--font-heading);
}

.block {
  display: block;
}

@media (max-width: 1280px) {
  .profile-hero {
    grid-template-columns: 1fr;
  }

  .profile-hero-title {
    max-width: none;
  }
}

@media (max-width: 1024px) {
  .profile-grid {
    grid-template-columns: 1fr !important;
  }

  .editor-head,
  .profile-section-head,
  .editor-footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .editor-completion {
    text-align: left;
  }

  .tag-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .side-panel {
    position: static;
  }
}

@media (max-width: 820px) {
  .profile-hero {
    padding: 22px;
  }

  .profile-hero-metrics,
  .grid-2,
  .body-stats-grid,
  .tag-grid,
  .injury-toggle {
    grid-template-columns: 1fr;
  }

  .form-card,
  .stats-card,
  .profile-status-card,
  .achievements-preview,
  .quick-links-card {
    padding: 20px;
  }

  .profile-section {
    padding: 18px;
  }

  .editor-submit {
    width: 100%;
  }
}
</style>
