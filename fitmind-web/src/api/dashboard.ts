import request from '../utils/request'

export function getDashboardStats() {
  return request({
    url: '/dashboard/stats',
    method: 'get'
  })
}

export function getWeeklyTraining() {
  return request({
    url: '/dashboard/weekly-training',
    method: 'get'
  })
}

export function getHeatmap() {
  return request({
    url: '/dashboard/heatmap',
    method: 'get'
  })
}

export function getNutritionToday() {
  return request({
    url: '/dashboard/nutrition-today',
    method: 'get'
  })
}

export function getBodyMetricsTrend() {
  return request({
    url: '/dashboard/body-metrics-trend',
    method: 'get'
  })
}
