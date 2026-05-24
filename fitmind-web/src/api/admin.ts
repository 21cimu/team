import request from '../utils/request'

export function getAdminDashboard() {
  return request({ url: '/admin/dashboard', method: 'get' })
}

export function listUsers(params: { page?: number; size?: number; keyword?: string }) {
  return request({ url: '/admin/users', method: 'get', params })
}

export function updateUserStatus(id: number, status: number) {
  return request({ url: `/admin/users/${id}/status`, method: 'put', data: { status } })
}

export function updateUserRole(id: number, role: string) {
  return request({ url: `/admin/users/${id}/role`, method: 'put', data: { role } })
}

export function deleteUser(id: number) {
  return request({ url: `/admin/users/${id}`, method: 'delete' })
}

export function listPosts(params: { page?: number; size?: number }) {
  return request({ url: '/admin/posts', method: 'get', params })
}

export function deletePost(id: number) {
  return request({ url: `/admin/posts/${id}`, method: 'delete' })
}

export function createAchievement(data: any) {
  return request({ url: '/admin/achievements', method: 'post', data })
}

export function updateAchievement(id: number, data: any) {
  return request({ url: `/admin/achievements/${id}`, method: 'put', data })
}

export function deleteAchievement(id: number) {
  return request({ url: `/admin/achievements/${id}`, method: 'delete' })
}
