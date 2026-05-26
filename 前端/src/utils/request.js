/**
 * 网络请求工具
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, handleAuthError, checkAndRefreshToken } from '@/utils/auth'

// 创建axios实例
const service = axios.create({
  baseURL: process.env.VUE_APP_BASE_API || '', // url 前缀
  timeout: 30000, // 请求超时时间
  withCredentials: true // 跨域请求发送cookie
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    // 添加认证令牌到请求头
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    
    // 记录请求开始时间（用于计算请求耗时）
    config.startTime = new Date().getTime()
    
    return config
  },
  error => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    const res = response.data
    
    // 如果是下载文件类型，直接返回
    if (response.config.responseType === 'blob') {
      return response.data
    }
    
    // 记录请求完成时间（用于计算请求耗时）
    const endTime = new Date().getTime()
    const requestTime = endTime - response.config.startTime
    console.log(`请求 ${response.config.url} 完成，耗时 ${requestTime}ms`)
    
    // 正常响应直接返回数据
    if (res.status === 'success' || res.code === 200 || res.code === 0 || response.status === 200) {
      return res
    }
    
    // 处理特定错误
    if (res.code === 401) {
      handleAuthError()
      return Promise.reject(new Error('登录已过期'))
    }
    
    // 其他错误显示错误信息
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  async error => {
    // 处理网络错误
    if (error.response) {
      const status = error.response.status
      
      // 处理401认证错误
      if (status === 401) {
        // 尝试刷新令牌
        const refreshed = await checkAndRefreshToken(error)
        if (refreshed) {
          // 令牌已刷新，重试原始请求
          const config = error.config
          // 更新请求头中的令牌
          config.headers['Authorization'] = `Bearer ${getToken()}`
          // 创建新的axios实例以避免循环
          return axios(config)
        } else {
          // 刷新失败，跳转登录
          handleAuthError()
        }
      } else if (status === 403) {
        ElMessage.error('权限不足，无法执行此操作')
      } else if (status === 404) {
        ElMessage.error('请求的资源不存在')
      } else if (status === 413) {
        ElMessage.error('请求的数据太大，超出服务器限制')
      } else if (status === 429) {
        ElMessage.error('请求过于频繁，请稍后再试')
      } else if (status === 500) {
        ElMessage.error('服务器错误，请稍后重试')
      } else {
        ElMessage.error(error.response.data?.message || '请求失败')
      }
    } else if (error.message && error.message.includes('timeout')) {
      ElMessage.error('请求超时，请检查网络连接')
    } else {
      ElMessage.error('网络错误，请检查您的网络连接')
    }
    
    return Promise.reject(error)
  }
)

export default service 