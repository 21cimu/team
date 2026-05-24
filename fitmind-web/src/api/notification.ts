import request from '../utils/request'

export function getNotifications() {
  return request({
    url: '/notification/list',
    method: 'get'
  })
}

export function getUnreadCount() {
  return request({
    url: '/notification/unread-count',
    method: 'get'
  })
}

export function markAsRead(id: number) {
  return request({
    url: `/notification/read/${id}`,
    method: 'put'
  })
}

export function markAllAsRead() {
  return request({
    url: '/notification/read-all',
    method: 'put'
  })
}
