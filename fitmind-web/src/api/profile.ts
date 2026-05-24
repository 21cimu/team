import request from '../utils/request'

export function getProfile() {
  return request({
    url: '/profile/me',
    method: 'get'
  })
}

export function saveProfile(data: any) {
  return request({
    url: '/profile/save',
    method: 'post',
    data
  })
}
