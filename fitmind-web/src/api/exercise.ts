import request from '../utils/request'

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
