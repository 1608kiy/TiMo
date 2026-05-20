import axios from 'axios'
import { ElMessage } from 'element-plus'
import emitter from '../events'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

let failCount = 0
let isRedirecting = false

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  emitter.emit('api:call-start')
  return config
})

request.interceptors.response.use(
  response => {
    failCount = 0
    emitter.emit('api:call-success')
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    // 401 未认证：静默处理，跳转登录页
    if (error.response?.status === 401) {
      if (!isRedirecting) {
        isRedirecting = true
        localStorage.removeItem('token')
        router.push('/login').finally(() => {
          isRedirecting = false
        })
      }
      return Promise.reject(error)
    }

    failCount++
    if (failCount >= 3) {
      emitter.emit('api:meltdown')
    }
    if (!error.response) {
      emitter.emit('network:offline')
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
