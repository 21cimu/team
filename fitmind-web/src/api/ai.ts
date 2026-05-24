import request from '../utils/request'

export interface WeatherContextPayload {
  province?: string
  city?: string
  weather?: string
  temperature?: string
  windDirection?: string
  windPower?: string
  humidity?: string
  reportTime?: string
}

export interface ChatStreamEvent {
  type: 'session' | 'chunk' | 'done' | 'error'
  sessionId?: string
  content?: string
  message?: string
}

export interface ChatSessionSummary {
  sessionId: string
  preview: string
  lastMessageTime: string
}

export interface TrainingGeneratePayload {
  weather?: WeatherContextPayload
  targetMuscleGroup?: string
  replaceExisting?: boolean
}

export interface DietGeneratePayload {
  weather?: WeatherContextPayload
  recognizedFoodsContext?: string
}

export function generateTrainingPlan(data?: TrainingGeneratePayload) {
  return request({
    url: '/ai/generate/training',
    method: 'post',
    data
  })
}

export function generateDietPlan(data?: DietGeneratePayload) {
  return request({
    url: '/ai/generate/diet',
    method: 'post',
    data
  })
}

export function getTodayTrainingPlan() {
  return request({
    url: '/ai/training/today',
    method: 'get'
  })
}

export function getTodayDietPlan() {
  return request({
    url: '/ai/diet/today',
    method: 'get'
  })
}

export function createManualTrainingPlan(data: any) {
  return request({
    url: '/training/plan',
    method: 'post',
    data
  })
}

export function updateTrainingPlan(planId: number, data: any) {
  return request({
    url: `/training/plan/${planId}`,
    method: 'put',
    data
  })
}

export function createManualDietPlan(data: any) {
  return request({
    url: '/diet/plan',
    method: 'post',
    data
  })
}

export function updateDietPlan(planId: number, data: any) {
  return request({
    url: `/diet/plan/${planId}`,
    method: 'put',
    data
  })
}

export function checkInTrainingPlan(planId: number) {
  return request({
    url: `/ai/training/checkin/${planId}`,
    method: 'post'
  })
}

export function checkInDietPlan(planId: number) {
  return request({
    url: `/ai/diet/checkin/${planId}`,
    method: 'post'
  })
}

export function sendChatMessage(message: string, sessionId?: string, weather?: WeatherContextPayload) {
  return request({
    url: '/ai/chat',
    method: 'post',
    data: { message, sessionId, weather }
  })
}

export async function streamChatMessage(
  message: string,
  sessionId: string | undefined,
  weather: WeatherContextPayload | undefined,
  onEvent: (event: ChatStreamEvent) => void
) {
  const response = await fetch('/api/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${localStorage.getItem('token') || ''}`
    },
    body: JSON.stringify({ message, sessionId, weather })
  })

  if (!response.ok || !response.body) {
    throw new Error(`HTTP ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })

    let separatorIndex = buffer.indexOf('\n\n')
    while (separatorIndex >= 0) {
      const rawEvent = buffer.slice(0, separatorIndex)
      buffer = buffer.slice(separatorIndex + 2)

      const dataLines = rawEvent
        .split('\n')
        .filter(line => line.startsWith('data:'))
        .map(line => line.slice(5).trim())

      if (dataLines.length > 0) {
        const payload = JSON.parse(dataLines.join(''))
        onEvent(payload as ChatStreamEvent)
      }

      separatorIndex = buffer.indexOf('\n\n')
    }

    if (done) break
  }
}

export function getChatHistory(sessionId: string) {
  return request({
    url: '/ai/chat/history',
    method: 'get',
    params: { sessionId }
  })
}

export function getChatSessions(limit = 20) {
  return request({
    url: '/ai/chat/sessions',
    method: 'get',
    params: { limit }
  })
}

export function deleteChatSession(sessionId: string) {
  return request({
    url: `/ai/chat/session/${sessionId}`,
    method: 'delete'
  })
}
