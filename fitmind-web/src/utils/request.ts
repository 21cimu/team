import axios from 'axios'
import { useUserStore } from '../stores/user'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// Request Interceptor
request.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = `Bearer ${userStore.token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// Response Interceptor
request.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    } else if (res.code === 401) {
      // Token 失效，清除状态并跳转登录
      const userStore = useUserStore()
      userStore.logout()
      router.push('/login')
      return Promise.reject(new Error('Unauthorized'))
    } else {
      // 业务错误（如 404 数据不存在）：只弹提示，不跳转
      ElMessage.error(res.message || 'Request failed')
      return Promise.reject(new Error(res.message || 'Error'))
    }
  },
  error => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        // HTTP 401：Token 失效
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      } else if (status === 403) {
        ElMessage.error('[ ACCESS DENIED ]')
      } else if (status === 500) {
        ElMessage.error('[ SERVER ERROR ] Please try again later')
      } else {
        ElMessage.error(`[ ERROR ${status} ] ${error.response.data?.message || error.message}`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('[ TIMEOUT ] Request timed out')
    } else {
      // 网络错误（后端未启动等），只提示，不跳登录
      ElMessage.error('[ NETWORK ERROR ] Cannot connect to server')
    }
    return Promise.reject(error)
  }
)

export default request
