import request from '../utils/request'

export const getMyAchievements = () => {
  return request({
    url: '/achievement/list',
    method: 'get'
  })
}

export const checkAchievements = () => {
  return request({
    url: '/achievement/check',
    method: 'post'
  })
}
