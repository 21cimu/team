import request from '../utils/request'

export interface ActionPrediction {
  label: string
  labelZh: string
  score: number
  scorePercent: number
}

export interface JointAngleMetric {
  key: string
  label: string
  current: number
  average: number
  min: number
  max: number
  unit: string
}

export interface FormCheck {
  name: string
  passed: boolean
  detail: string
}

export interface ActionPhaseSegment {
  phase: string
  startFrame: number
  endFrame: number
  frameCount: number
}

export interface ExerciseActionAnalysisResult {
  success: boolean
  label: string
  labelZh: string
  score: number
  scorePercent: number
  standard: boolean
  hint: string
  suggestions: string[]
  topPredictions: ActionPrediction[]
  poseFrames: number
  totalFrames: number
  sequenceFrames: number
  source: string
  repetitions: number
  currentPhase: string
  phaseTimeline: ActionPhaseSegment[]
  jointAngles: JointAngleMetric[]
  formChecks: FormCheck[]
}

export interface ExerciseRealtimeReadyMessage {
  type: 'ready'
  message: string
  requiredFrames: number
}

export interface ExerciseRealtimePendingMessage {
  type: 'pending'
  message: string
  capturedFrames: number
  poseFrames: number
  requiredFrames: number
}

export interface ExerciseRealtimeResultMessage {
  type: 'result'
  data: ExerciseActionAnalysisResult
}

export interface ExerciseRealtimeErrorMessage {
  type: 'error'
  message: string
}

export interface ExerciseRealtimePongMessage {
  type: 'pong'
}

export type ExerciseRealtimeServerMessage =
  | ExerciseRealtimeReadyMessage
  | ExerciseRealtimePendingMessage
  | ExerciseRealtimeResultMessage
  | ExerciseRealtimeErrorMessage
  | ExerciseRealtimePongMessage

export const getExercises = (params?: { keyword?: string; category?: string }) => {
  return request({
    url: '/exercise/list',
    method: 'get',
    params
  })
}

export const getExerciseDetail = (id: number) => {
  return request({
    url: `/exercise/${id}`,
    method: 'get'
  })
}

export const analyzeExerciseAction = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)

  return request({
    url: '/exercise/analyze',
    method: 'post',
    timeout: 180000,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    data: formData
  })
}
