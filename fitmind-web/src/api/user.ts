import request from '../utils/request'

export function login(data: any) {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

export function register(data: any) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

export function getUserInfo() {
  return request({
    url: '/user/me',
    method: 'get'
  })
}
