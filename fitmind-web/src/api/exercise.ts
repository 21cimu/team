import request from '../utils/request'

export interface ActionPrediction {
  label: string
  labelZh: string
  score: number
  scorePercent: number
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
}

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
