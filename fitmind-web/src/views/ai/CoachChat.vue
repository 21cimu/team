<template>
  <div class="coach-page" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <!-- Topbar -->
    <Teleport to="#topbar-page-header">
      <div class="topbar-page-shell">
        <div class="topbar-page-copy">
          <span class="topbar-page-kicker">FitMind 教练</span>
          <strong class="topbar-page-title">对话终端</strong>
        </div>
        <div class="topbar-page-actions coach-status">
          <button class="nd-btn" style="font-size:11px;padding:6px 12px;" @click="createNewSession">+ 新会话</button>
        </div>
      </div>
    </Teleport>

    <!-- History Sidebar -->
    <aside class="history-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="sidebar-header">
        <span class="sidebar-title" v-if="!sidebarCollapsed">历史对话</span>
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed" :title="sidebarCollapsed ? '展开历史' : '收起历史'">
          <span class="collapse-icon">{{ sidebarCollapsed ? '›' : '‹' }}</span>
        </button>
      </div>

      <div class="sidebar-new-btn" v-if="!sidebarCollapsed">
        <button class="nd-btn sidebar-new" @click="createNewSession">＋ 新会话</button>
      </div>
      <div class="sidebar-new-icon" v-else>
        <button class="collapse-btn" @click="createNewSession" title="新会话">＋</button>
      </div>

      <div class="session-list" v-if="!sidebarCollapsed">
        <div
          v-for="s in sessionList"
          :key="s.id"
          class="session-item"
          :class="{ active: s.id === currentSessionId }"
          @click="switchSession(s)"
        >
          <div class="session-icon">◈</div>
          <div class="session-info">
            <div class="session-preview">{{ s.preview }}</div>
            <div class="session-date">{{ s.date }}</div>
          </div>
          <button class="session-delete" title="删除对话" @click.stop="handleDeleteSession(s)">×</button>
        </div>
        <div v-if="sessionList.length === 0" class="session-empty">暂无历史对话</div>
      </div>

      <!-- Collapsed: icon only dots -->
      <div class="session-dots" v-else>
        <div
          v-for="s in sessionList"
          :key="s.id"
          class="session-dot"
          :class="{ active: s.id === currentSessionId }"
          :title="s.preview"
          @click="switchSession(s)"
        ></div>
      </div>
    </aside>

    <!-- Main Chat Area -->
    <div class="chat-main">
      <!-- Page Header -->
      <div class="page-header mb-2xl">
        <div>
          <div class="text-label mb-xs">[ FitMind 教练 ]</div>
          <h1 class="text-display-md text-primary">对话终端</h1>
        </div>
      </div>

      <!-- Quick Prompts -->
      <div class="quick-prompts mb-xl">
        <button
          v-for="prompt in quickPrompts"
          :key="prompt.text"
          class="prompt-chip"
          @click="usePrompt(prompt.text)"
        >
          <span>{{ prompt.icon }}</span> {{ prompt.text }}
        </button>
      </div>

      <!-- Chat Container -->
      <div class="nd-card chat-container">
        <!-- Chat Messages -->
        <div class="chat-history" ref="chatHistoryRef">
          <div
            v-for="(msg, index) in messages"
            :key="index"
            :class="['message-row', msg.role === 'user' ? 'user-msg' : 'ai-msg']"
          >
            <div class="msg-avatar" v-if="msg.role === 'ai'">◈</div>
            <div class="message-bubble" :class="msg.role === 'user' ? 'user-bubble' : 'ai-bubble'">
              <div class="msg-header">
                <span class="msg-role">{{ msg.role === 'user' ? '您' : 'FitMind 教练' }}</span>
                <span class="msg-time">{{ msg.time }}</span>
              </div>
              <div class="msg-content" v-html="formatMessage(msg.content)"></div>
              <div v-if="msg.action && msg.content && !(loading && streamingMessageIndex === index)" class="message-actions">
                <button class="nd-btn primary message-action-btn" @click="goToTrainingPlan(msg)">
                  加入训练计划
                </button>
              </div>
            </div>
            <div class="msg-avatar user-avatar" v-if="msg.role === 'user'">
              {{ currentUserInitial }}
            </div>
          </div>

          <!-- Loading -->
          <div v-if="loading && streamingMessageIndex === null" class="message-row ai-msg">
            <div class="msg-avatar">◈</div>
            <div class="message-bubble ai-bubble loading-bubble">
              <div class="typing-dots">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>

        <!-- Input Area -->
        <div class="chat-input-area">
          <div class="input-wrapper">
            <textarea
              v-model="inputMsg"
              class="nd-input chat-input"
              placeholder="[ 输入查询内容给 FitMind 教练... ]"
              @keydown.enter.exact.prevent="handleSend"
              @keydown.enter.shift.exact="inputMsg += '\n'"
              rows="2"
            ></textarea>
            <div class="input-hint text-caption">按 ENTER 发送 · SHIFT+ENTER 换行</div>
          </div>
          <button
            class="nd-btn primary send-btn"
            @click="handleSend"
            :disabled="loading || !inputMsg.trim()"
          >
            {{ loading ? '...' : '→' }}
          </button>
        </div>
      </div>

      <!-- Coach Info -->
      <div class="coach-info-row mt-xl">
        <div class="nd-card info-card">
          <div class="text-label mb-xs">教练能力</div>
          <div class="capability-list">
            <span class="cap-item">⌖ 训练计划</span>
            <span class="cap-item">◒ 饮食建议</span>
            <span class="cap-item">▤ 进度分析</span>
            <span class="cap-item">▥ 动作指导</span>
            <span class="cap-item">☍ 激励</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { deleteChatSession, getChatHistory, getChatSessions, sendChatMessage, streamChatMessage, type ChatSessionSummary } from '../../api/ai'
import { useWeather } from '../../composables/useWeather'
import { useUserStore } from '../../stores/user'
import { toWeatherContextPayload } from '../../utils/aiContext'

const ACTIVE_SESSION_KEY = 'fitmind-ai-active-session'
const CHAT_TRAINING_DRAFT_KEY = 'fitmind-chat-training-draft'
const INTRO_MESSAGE = 'FitMind AI 教练已初始化。你可以咨询训练、饮食、恢复或进度相关问题。'
const TRAINING_PLAN_KEYWORDS = ['训练计划', '训练方案']
const TRAINING_PLAN_VERBS = ['制定', '生成', '安排', '设计', '规划', '做一套', '给我一套', '帮我做']
const TARGET_MUSCLE_GROUPS = ['胸部', '背部', '腿部', '肩部', '手臂', '腹部', '核心', '臀部', '全身']

const userStore = useUserStore()
const router = useRouter()
const currentUserInitial = (userStore.userInfo?.username || 'U').charAt(0).toUpperCase()
const { weather, fetchWeather: refreshWeather } = useWeather()

interface Message {
  role: 'user' | 'ai'
  content: string
  time: string
  action?: TrainingPlanAction
}

interface SessionEntry {
  id: string
  preview: string
  date: string
  messages: Message[]
}

interface TrainingPlanAction {
  targetMuscleGroup?: string
  prompt: string
}

interface TrainingPlanDraft {
  prompt: string
  response: string
  targetMuscleGroup?: string
}

// ── 快捷提示 ──────────────────────────────
const quickPrompts = [
  { icon: '⌖', text: '为我制定胸部训练计划' },
  { icon: '◒', text: '今天应该吃什么？' },
  { icon: '▤', text: '分析我的训练进度' },
  { icon: '△', text: '如何更快减脂？' },
  { icon: '▽', text: '我需要多少休息时间？' }
]

// ── 侧边栏状态 ────────────────────────────
const sidebarCollapsed = ref(false)
const sessionList = ref<SessionEntry[]>([])
const currentSessionId = ref('')
const streamingMessageIndex = ref<number | null>(null)

// ── 消息状态 ──────────────────────────────
const messages = ref<Message[]>([
  {
    role: 'ai',
    content: 'FitMind 教练已初始化。我是您的 AI 健身教练。您可以向我咨询训练、营养、恢复或个人健身数据相关的问题。请问有什么可以帮助您的？',
    time: new Date().toLocaleTimeString()
  }
])

const inputMsg = ref('')
const loading = ref(false)
const chatHistoryRef = ref<HTMLElement | null>(null)

// ── 工具函数 ──────────────────────────────
const formatMessage = (text: string) => {
  if (!text) return ''
  // 先转义 HTML 实体防止 XSS，再将换行替换为 <br>
  const escaped = text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
  return escaped.replace(/\n/g, '<br>')
}

const resolveTrainingPlanAction = (prompt: string): TrainingPlanAction | null => {
  const normalizedPrompt = prompt.trim()
  if (!normalizedPrompt) return null

  const hasPlanKeyword = TRAINING_PLAN_KEYWORDS.some(keyword => normalizedPrompt.includes(keyword))
  const hasVerb = TRAINING_PLAN_VERBS.some(keyword => normalizedPrompt.includes(keyword))
  if (!hasPlanKeyword || !hasVerb) return null

  return {
    targetMuscleGroup: TARGET_MUSCLE_GROUPS.find(keyword => normalizedPrompt.includes(keyword)),
    prompt: normalizedPrompt
  }
}

const attachMessageActions = (items: Message[]) => {
  for (let i = 0; i < items.length; i += 1) {
    const current = items[i]
    const previous = items[i - 1]
    current.action = undefined
    if (current.role === 'ai' && previous?.role === 'user' && current.content && !current.content.startsWith('[ 错误')) {
      current.action = resolveTrainingPlanAction(previous.content) || undefined
    }
  }
  return items
}

const goToTrainingPlan = (message: Message) => {
  if (!message.action || !message.content.trim()) return

  const draft: TrainingPlanDraft = {
    prompt: message.action.prompt,
    response: message.content,
    targetMuscleGroup: message.action.targetMuscleGroup
  }
  localStorage.setItem(CHAT_TRAINING_DRAFT_KEY, JSON.stringify(draft))

  router.push({
    path: '/app/training',
    query: {
      from: 'coach',
      import: 'chat-plan'
    }
  })
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatHistoryRef.value) {
      chatHistoryRef.value.scrollTop = chatHistoryRef.value.scrollHeight
    }
  })
}

const formatDate = (d: Date) =>
  `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`

const buildIntroMessage = (): Message => ({
  role: 'ai',
  content: INTRO_MESSAGE,
  time: ''
})

const persistCurrentSessionId = () => {
  if (currentSessionId.value) {
    localStorage.setItem(ACTIVE_SESSION_KEY, currentSessionId.value)
  } else {
    localStorage.removeItem(ACTIVE_SESSION_KEY)
  }
}

const normalizeSession = (session: ChatSessionSummary): SessionEntry => ({
  id: session.sessionId,
  preview: session.preview || '新对话',
  date: session.lastMessageTime ? formatDate(new Date(session.lastMessageTime)) : '',
  messages: []
})

const saveCurrentSession = () => {
  if (!currentSessionId.value) return
  const idx = sessionList.value.findIndex(s => s.id === currentSessionId.value)
  const userMsgs = messages.value.filter(m => m.role === 'user')
  const preview = userMsgs.length > 0 ? userMsgs[userMsgs.length - 1].content.slice(0, 28) : '新对话'
  const entry: SessionEntry = {
    id: currentSessionId.value,
    preview,
    date: formatDate(new Date()),
    messages: [...messages.value]
  }
  if (idx >= 0) {
    sessionList.value[idx] = entry
  } else {
    sessionList.value.unshift(entry)
  }
  persistCurrentSessionId()
}

// ── 新会话 ────────────────────────────────
const createNewSession = () => {
  saveCurrentSession()
  currentSessionId.value = ''
  persistCurrentSessionId()
  messages.value = [
    {
      role: 'ai',
      content: 'FitMind 教练已初始化。我是您的 AI 健身教练。您可以向我咨询训练、营养、恢复或个人健身数据相关的问题。请问有什么可以帮助您的？',
      time: new Date().toLocaleTimeString()
    }
  ]
}

// ── 切换历史会话 ──────────────────────────
const loadSessionMessages = async (sessionId: string, fallbackMessages?: Message[]) => {
  try {
    const res = await getChatHistory(sessionId) as any
    const history = res?.data || res || []
    if (Array.isArray(history) && history.length > 0) {
      messages.value = attachMessageActions([
        buildIntroMessage(),
        ...history.map((h: any) => ({
          role: h.role === 'assistant' ? 'ai' : 'user',
          content: h.content,
          time: h.createTime
            ? new Date(h.createTime).toLocaleTimeString()
            : ''
        } as Message))
      ])
      return
    }
  } catch {
    // ignore and use fallback
  }

  messages.value = attachMessageActions(
    fallbackMessages && fallbackMessages.length > 0
      ? [...fallbackMessages]
      : [buildIntroMessage()]
  )
}

const loadSessions = async () => {
  const res = await getChatSessions(20) as any
  const items = (res?.data || res || []) as ChatSessionSummary[]
  sessionList.value = Array.isArray(items) ? items.map(normalizeSession) : []
}

const handleDeleteSession = async (session: SessionEntry) => {
  try {
    await ElMessageBox.confirm('删除后该对话历史将不可恢复，是否继续？', '删除对话', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteChatSession(session.id)
    sessionList.value = sessionList.value.filter(item => item.id !== session.id)

    if (currentSessionId.value === session.id) {
      const nextSession = sessionList.value[0]
      if (nextSession) {
        currentSessionId.value = nextSession.id
        persistCurrentSessionId()
        await loadSessionMessages(nextSession.id, nextSession.messages)
      } else {
        currentSessionId.value = ''
        persistCurrentSessionId()
        messages.value = [buildIntroMessage()]
      }
    }

    ElMessage.success('对话已删除')
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

const switchSession = async (s: SessionEntry) => {
  // 先保存当前
  saveCurrentSession()
  currentSessionId.value = s.id
  persistCurrentSessionId()
  await loadSessionMessages(s.id, s.messages)
  scrollToBottom()
  return

  // 尝试从服务端拉取完整历史，失败则用缓存
  try {
    const res = await getChatHistory(s.id) as any
    const history = res?.data || res || []
    if (Array.isArray(history) && history.length > 0) {
      const initMsg: Message = {
        role: 'ai',
        content: 'FitMind 教练已初始化。我是您的 AI 健身教练。您可以向我咨询训练、营养、恢复或个人健身数据相关的问题。请问有什么可以帮助您的？',
        time: ''
      }
      messages.value = [
        initMsg,
        ...history.map((h: any) => ({
          role: h.role === 'assistant' ? 'ai' : 'user',
          content: h.content,
          time: h.createTime
            ? new Date(h.createTime).toLocaleTimeString()
            : ''
        } as Message))
      ]
    } else {
      messages.value = [...s.messages]
    }
  } catch {
    messages.value = [...s.messages]
  }
  scrollToBottom()
}

// ── 使用快捷提示 ──────────────────────────
const usePrompt = (text: string) => {
  inputMsg.value = text
  handleSend()
}

const getAiWeatherContext = async () => {
  if (!weather.value) {
    try {
      await refreshWeather()
    } catch {
      return undefined
    }
  }
  return toWeatherContextPayload(weather.value)
}

// ── 发送消息 ──────────────────────────────
const handleSend = async () => {
  if (!inputMsg.value.trim() || loading.value) return

  const userText = inputMsg.value.trim()
  messages.value.push({
    role: 'user',
    content: userText,
    time: new Date().toLocaleTimeString()
  })
  inputMsg.value = ''
  loading.value = true
  messages.value.push({
    role: 'ai',
    content: '',
    time: new Date().toLocaleTimeString(),
    action: resolveTrainingPlanAction(userText) || undefined
  })
  streamingMessageIndex.value = messages.value.length - 1
  scrollToBottom()

  try {
    const weatherContext = await getAiWeatherContext()
    await streamChatMessage(userText, currentSessionId.value || undefined, weatherContext, event => {
      if (event.type === 'session' && event.sessionId) {
        currentSessionId.value = event.sessionId
        persistCurrentSessionId()
      } else if (event.type === 'chunk' && streamingMessageIndex.value !== null) {
        messages.value[streamingMessageIndex.value].content += event.content || ''
        scrollToBottom()
      } else if (event.type === 'error') {
        throw new Error(event.message || 'AI 服务异常')
      }
    })
    await loadSessions()
    saveCurrentSession()
    return
    const res = await sendChatMessage(userText, currentSessionId.value, weatherContext) as any
    if (res?.sessionId && !currentSessionId.value) {
      currentSessionId.value = res.sessionId
    }
    if (streamingMessageIndex.value !== null) {
      messages.value[streamingMessageIndex.value].content = '[ 错误：AI 引擎暂时不可用，请稍后重试 ]'
    } else messages.value.push({
      role: 'ai',
      content: res?.response || res || '无响应',
      time: new Date().toLocaleTimeString()
    })
    // 发送后自动更新侧边栏预览
    saveCurrentSession()
  } catch {
    messages.value.push({
      role: 'ai',
      content: '[ 错误：AI 引擎暂时不可用，请稍后重试 ]',
      time: new Date().toLocaleTimeString()
    })
  } finally {
    loading.value = false
    streamingMessageIndex.value = null
    scrollToBottom()
  }
}

onMounted(async () => {
  try {
    await loadSessions()
    currentSessionId.value = ''
    persistCurrentSessionId()
    messages.value = [buildIntroMessage()]
  } catch {
    // ignore and keep intro state
  }
  scrollToBottom()
})
</script>

<style scoped>
/* ── 整体布局 ─────────────────────────────────── */
.coach-page {
  display: flex;
  gap: 0;
  width: 100%;
  height: calc(100vh - 140px);
  overflow: hidden;
  transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ── 侧边栏 ──────────────────────────────────── */
.history-sidebar {
  width: 220px;
  min-width: 220px;
  flex-shrink: 0;
  background: rgba(255, 250, 244, 0.7);
  border-right: 1px solid rgba(88, 78, 67, 0.1);
  backdrop-filter: blur(14px);
  display: flex;
  flex-direction: column;
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1),
              min-width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  border-radius: 18px 0 0 18px;
}

.history-sidebar.collapsed {
  width: 52px;
  min-width: 52px;
}

.message-actions {
  margin-top: 14px;
}

.message-action-btn {
  min-width: 144px;
}

.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 14px 10px;
  flex-shrink: 0;
  gap: 8px;
}

.history-sidebar.collapsed .sidebar-header {
  justify-content: center;
  padding: 16px 10px 10px;
}

.sidebar-title {
  font-family: var(--font-heading);
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--primary);
  white-space: nowrap;
  overflow: hidden;
}

.collapse-btn {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 1px solid rgba(88, 78, 67, 0.14);
  background: rgba(255, 255, 255, 0.7);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.15s;
  font-size: 14px;
  line-height: 1;
}

.collapse-btn:hover {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(127, 157, 135, 0.3);
  color: var(--primary);
}

.collapse-icon {
  font-size: 16px;
  font-weight: bold;
  line-height: 1;
}

/* 新会话按钮 */
.sidebar-new-btn {
  padding: 0 12px 10px;
  flex-shrink: 0;
}

.sidebar-new {
  width: 100%;
  font-size: 12px;
  padding: 7px 12px;
  text-align: center;
}

.sidebar-new-icon {
  display: flex;
  justify-content: center;
  padding: 0 0 10px;
  flex-shrink: 0;
}

/* 会话列表 */
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  scrollbar-width: thin;
  scrollbar-color: rgba(194, 169, 120, 0.24) transparent;
}

.session-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid transparent;
}

.session-item:hover {
  background: rgba(255, 255, 255, 0.72);
  border-color: rgba(194, 169, 120, 0.2);
}

.session-item.active {
  background: rgba(255, 255, 255, 0.86);
  border-color: rgba(127, 157, 135, 0.3);
  box-shadow: 0 4px 12px rgba(82, 69, 54, 0.07);
}

.session-icon {
  font-size: 13px;
  color: var(--primary);
  flex-shrink: 0;
  margin-top: 2px;
}

.session-info {
  min-width: 0;
}

.session-delete {
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: rgba(111, 103, 93, 0.52);
  cursor: pointer;
  flex-shrink: 0;
  font-size: 14px;
  line-height: 1;
  transition: all 0.15s;
}

.session-delete:hover {
  background: rgba(196, 88, 72, 0.12);
  color: #c45848;
}

.session-preview {
  font-size: 12px;
  color: var(--text-main);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.4;
}

.session-date {
  font-size: 10px;
  color: rgba(111, 103, 93, 0.54);
  margin-top: 2px;
}

.session-empty {
  font-size: 12px;
  color: rgba(111, 103, 93, 0.5);
  text-align: center;
  padding: 20px 0;
}

/* 折叠状态的小圆点 */
.session-dots {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.session-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(194, 169, 120, 0.35);
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.session-dot:hover,
.session-dot.active {
  background: var(--primary);
  transform: scale(1.3);
}

/* ── 主聊天区域 ───────────────────────────────── */
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  padding-left: 24px;
  overflow: hidden;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-shrink: 0;
}

/* 快捷提示 */
.quick-prompts {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.prompt-chip {
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(194, 169, 120, 0.22);
  color: var(--text-secondary);
  padding: 7px 14px;
  font-size: 12px;
  font-family: var(--font-body);
  cursor: pointer;
  border-radius: 999px;
  transition: all 0.15s;
  white-space: nowrap;
}

.prompt-chip:hover {
  background: rgba(255, 255, 255, 0.84);
  color: var(--text-main);
  border-color: rgba(127, 157, 135, 0.26);
}

/* 聊天容器 */
.chat-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
  min-height: 0;
  border-color: rgba(88, 78, 67, 0.12);
  box-shadow: 0 18px 40px rgba(82, 69, 54, 0.08);
}

.chat-container:hover { transform: none; }

.chat-history {
  flex: 1;
  padding: 22px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  scrollbar-width: thin;
  scrollbar-color: rgba(194, 169, 120, 0.24) transparent;
  min-height: 0;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.message-row.user-msg { justify-content: flex-end; }

.msg-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
  background: linear-gradient(135deg, rgba(194, 169, 120, 0.2), rgba(127, 157, 135, 0.18));
  border: 1px solid rgba(88, 78, 67, 0.1);
  color: #8a7652;
}

.user-avatar {
  background: linear-gradient(135deg, #c2a978, #7ea889);
  font-family: var(--font-heading);
  font-size: 16px;
  font-weight: 900;
  color: #fffaf4;
}

.message-bubble {
  max-width: 72%;
  padding: 14px 16px;
  border-radius: 18px;
  box-shadow: 0 12px 24px rgba(82, 69, 54, 0.06);
}

.user-bubble {
  background: linear-gradient(135deg, rgba(194, 169, 120, 0.18), rgba(255, 255, 255, 0.84));
  border: 1px solid rgba(194, 169, 120, 0.24);
}

.ai-bubble {
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(88, 78, 67, 0.1);
}

.msg-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  gap: 16px;
}

.msg-role {
  font-family: var(--font-heading);
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--primary);
}

.user-bubble .msg-role { color: #9f8353; }

.msg-time {
  font-size: 10px;
  color: rgba(111, 103, 93, 0.56);
}

.msg-content {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

/* 打字指示器 */
.loading-bubble { padding: 14px 20px; }

.typing-dots {
  display: flex;
  gap: 6px;
  align-items: center;
  height: 20px;
}

.typing-dots span {
  width: 8px;
  height: 8px;
  background: #7ea889;
  border-radius: 50%;
  animation: bounce 1.4s infinite;
}

.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.3; }
  30% { transform: translateY(-6px); opacity: 1; }
}

/* 输入区 */
.chat-input-area {
  display: flex;
  gap: 12px;
  padding: 16px;
  background: rgba(255, 250, 244, 0.78);
  border-top: 1px solid rgba(88, 78, 67, 0.08);
  backdrop-filter: blur(12px);
  flex-shrink: 0;
}

.input-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-input {
  resize: none;
  min-height: 72px;
  border-width: 1px;
  background: rgba(255, 255, 255, 0.82);
}

.input-hint {
  margin-top: 4px;
  font-size: 10px;
  opacity: 0.56;
}

.send-btn {
  height: auto;
  min-width: 68px;
  padding: 0 20px;
  font-size: 22px;
  align-self: stretch;
}

/* 教练能力 */
.coach-info-row { flex-shrink: 0; }

.info-card { padding: 16px 20px; }

.capability-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.cap-item {
  font-size: 12px;
  padding: 6px 11px;
  background: rgba(255, 255, 255, 0.56);
  border: 1px solid rgba(88, 78, 67, 0.1);
  border-radius: 999px;
  color: var(--text-secondary);
}

/* ── 响应式 ───────────────────────────────────── */
@media (max-width: 768px) {
  .coach-page {
    flex-direction: column;
    height: auto;
    min-height: calc(100vh - 120px);
  }

  .history-sidebar {
    width: 100% !important;
    min-width: 100% !important;
    height: auto;
    border-right: none;
    border-bottom: 1px solid rgba(88, 78, 67, 0.1);
    border-radius: 18px 18px 0 0;
    max-height: 180px;
  }

  .history-sidebar.collapsed {
    max-height: 52px;
  }

  .session-list {
    flex-direction: row;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 0 8px 8px;
  }

  .session-item {
    min-width: 140px;
    max-width: 160px;
  }

  .session-dots {
    flex-direction: row;
    padding: 0 12px 8px;
  }

  .chat-main { padding-left: 0; padding-top: 12px; }

  .message-bubble { max-width: calc(100% - 52px); }

  .chat-input-area { flex-direction: column; }

  .send-btn { min-height: 48px; }
}
</style>
