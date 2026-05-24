import request from '../utils/request'

export function getTrainingHistory() {
  return request({
    url: '/history/training',
    method: 'get'
  })
}

export function getDietHistory() {
  return request({
    url: '/history/diet',
    method: 'get'
  })
}
