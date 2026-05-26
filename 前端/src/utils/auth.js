import axios from 'axios';
import { ElMessage } from 'element-plus';
import router from '@/router';
import { API_BASE_URL } from '@/config';
import request from '@/utils/request';

// 定义TOKEN_KEY常量
const TOKEN_KEY = 'auth_token';

/**
 * 从localStorage获取认证令牌
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * 保存认证令牌到localStorage
 * @param {string} token - 认证令牌
 */
export function setToken(token) {
  // 确保令牌格式正确
  if (token && !isValidTokenFormat(token)) {
    // 尝试从用户信息构建完整令牌
    const userInfo = getUserInfo();
    if (userInfo && userInfo.username) {
      // 构建标准格式令牌
      token = standardizeToken(token, userInfo);
      console.log('已将令牌转换为标准格式');
    }
  }
  
  localStorage.setItem(TOKEN_KEY, token);
}

/**
 * 移除认证令牌
 */
export function removeToken() {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * 获取认证请求头
 * @returns {Object} 认证头对象
 */
export function getAuthHeader() {
  const token = getToken();
  if (token) {
    return { 'Authorization': `Bearer ${token}` };
  }
  return {};
}

/**
 * 从localStorage获取用户信息
 * @returns {Object} 用户信息对象
 */
export function getUserInfo() {
  try {
    const userStr = localStorage.getItem('user');
    if (userStr) {
      return JSON.parse(userStr);
    }
  } catch (e) {
    console.warn('解析用户信息失败:', e);
  }
  return null;
}

/**
 * 验证令牌格式是否正确
 * @param {string} token - 认证令牌
 * @returns {boolean} 是否为有效格式
 */
export function isValidTokenFormat(token) {
  if (!token) return false;
  
  // 检查令牌格式: [hash]_[username]_[userId]_[role]_[timestamp]
  const parts = token.split('_');
  return parts.length >= 5;
}

/**
 * 标准化令牌格式
 * @param {string} token - 原始令牌
 * @param {Object} userInfo - 用户信息
 * @returns {string} 标准格式令牌
 */
export function standardizeToken(token, userInfo) {
  if (!token) return null;
  
  // 解析原始令牌
  let hash = token;
  let timestamp = Date.now();
  
  if (token.includes('_')) {
    const parts = token.split('_');
    if (parts.length >= 1) {
      hash = parts[0];
    }
    if (parts.length >= 5) {
      timestamp = parts[4];
    } else if (parts.length >= 3) {
      timestamp = parts[parts.length - 1];
    }
  }
  
  // 确保用户信息存在
  if (!userInfo) {
    console.warn('缺少用户信息，尝试从localStorage获取');
    userInfo = getUserInfo() || {};
  }
  
  const username = userInfo.username || 'unknown';
  const userId = userInfo.id || 'unknown';
  const role = userInfo.role || 'user';
  
  // 生成新格式令牌：[hash]_[username]_[userId]_[role]_[timestamp]
  return `${hash}_${username}_${userId}_${role}_${timestamp}`;
}

/**
 * 尝试刷新认证令牌
 * @returns {Promise<string>} 新的认证令牌
 */
export async function refreshToken() {
  try {
    console.log('尝试刷新令牌，当前令牌:', getToken()?.substring(0, 10) + '...');
    
    // 先检查是否有token，如果没有就不必尝试刷新
    if (!getToken()) {
      throw new Error('没有认证令牌可供刷新');
    }
    
    // 获取当前登录的用户信息
    const userInfo = getUserInfo();
    
    if (!userInfo || !userInfo.username) {
      console.error('刷新令牌失败：缺少用户名信息');
      throw new Error('缺少用户信息');
    }
    
    // 注意：使用axios实例而非request，避免循环引用
    const response = await axios({
      url: '/api/auth/refresh',
      method: 'post',
      data: {
        token: getToken(),
        username: userInfo.username
      },
      headers: {
        ...getAuthHeader(),
        'Content-Type': 'application/json'
      },
      withCredentials: true
    });
    
    if (response.data && response.data.token) {
      // 确保新令牌格式正确
      let newToken = response.data.token;
      if (!isValidTokenFormat(newToken)) {
        newToken = standardizeToken(newToken, userInfo);
      }
      
      console.log('刷新令牌成功，新令牌:', newToken.substring(0, 10) + '...');
      setToken(newToken);
      return newToken;
    } else if (response.data && response.data.success) {
      // 某些API可能不直接返回token而是在success字段指示成功
      console.log('刷新令牌操作成功，但未返回新令牌，使用旧令牌继续');
      return getToken();
    }
    
    throw new Error('刷新令牌失败: 未收到新令牌');
  } catch (error) {
    console.error('刷新认证令牌失败:', error);
    // 刷新失败，需要重新登录
    handleAuthError();
    throw error;
  }
}

/**
 * 处理认证错误
 */
export function handleAuthError() {
  removeToken();
  
  // 显示错误消息
  ElMessage.error({
    message: '登录已过期，请重新登录',
    duration: 3000
  });
  
  // 记录当前路径，以便登录后重定向回来
  const currentPath = router.currentRoute.value.fullPath;
  if (currentPath !== '/login') {
    router.push({
      path: '/login',
      query: { redirect: currentPath }
    });
  }
}

/**
 * 检查是否已认证
 * @returns {boolean} 是否已认证
 */
export function isAuthenticated() {
  return !!getToken();
}

/**
 * 检查并刷新令牌如果到期或接近到期
 * @param {Error} error - 可能包含401状态码的错误
 * @returns {Promise<boolean>} 是否成功刷新
 */
export async function checkAndRefreshToken(error) {
  // 详细记录错误信息，帮助诊断
  console.log('检查是否需要刷新令牌:', 
    error?.response?.status, 
    error?.message,
    '当前认证状态:', isAuthenticated()
  );
  
  if (error?.response?.status === 401) {
    try {
      await refreshToken();
      return true; // 刷新成功
    } catch (refreshError) {
      console.error('刷新令牌失败，详细错误:', refreshError);
      return false; // 刷新失败
    }
  }
  return false;
}

/**
 * 上传并分析视频
 * @param {FormData} formData - 包含视频文件和其他参数的表单数据
 * @returns {Promise} - 响应结果
 */
export function uploadAndAnalyzeVideo(formData) {
  // 判断是普通视频还是十字路口视频
  const isIntersection = formData.get('roadType') === 'intersection';
  const url = isIntersection 
    ? `${API_BASE_URL}/api/video-analysis/upload/intersection` 
    : `${API_BASE_URL}/api/video-analysis/upload`;

  console.log(`使用API路径: ${url}`);

  return axios.post(url, formData, {
    headers: {
      ...getAuthHeader(),
      'Content-Type': 'multipart/form-data'
    },
    // 添加上传进度事件
    onUploadProgress: (progressEvent) => {
      const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
      // 可以通过事件总线或者回调通知组件更新进度
      console.log(`上传进度: ${percentCompleted}%`);
    },
    timeout: 120000 // 2分钟
  }).catch(error => {
    console.error('视频上传请求失败:', error);
    
    if (error.response) {
      return Promise.reject({
        message: `服务器错误 (${error.response.status}): ${error.response.data?.message || '未知错误'}`,
        status: error.response.status,
        data: error.response.data
      });
    } else if (error.request) {
      return Promise.reject({
        message: '网络错误：服务器未响应，请检查网络连接或服务器状态',
        isNetworkError: true,
        originalError: error
      });
    } else {
      return Promise.reject({
        message: `请求错误: ${error.message}`,
        originalError: error
      });
    }
  });
}

/**
 * 获取视频任务状态
 * @param {string} taskId - 视频分析任务ID
 * @returns {Promise} - 响应结果
 */
export function getVideoTaskStatus(taskId) {
  return request({
    url: `${API_BASE_URL}/api/video-analysis/${taskId}/status`,
    method: 'get'
  });
}

/**
 * 获取视频分析结果
 * @param {string} resultId - 视频分析结果ID
 * @returns {Promise} - 响应结果
 */
export function getVideoResult(resultId) {
  return request({
    url: `${API_BASE_URL}/api/video-analysis/${resultId}/result`,
    method: 'get'
  });
}

/**
 * 获取视频任务列表
 * @param {string} userId - 用户ID（可选，管理员使用）
 * @returns {Promise} - 响应结果
 */
export function getVideoTaskList(userId = null) {
  let url = `${API_BASE_URL}/api/video-analysis/history`;
  if (userId) {
    url += `?user_id=${userId}`;
  }
  return request({
    url,
    method: 'get'
  });
}

/**
 * 重新分析视频
 * @param {string} taskId - 视频分析任务ID
 * @returns {Promise} - 响应结果
 */
export function retryVideoAnalysis(taskId) {
  return request({
    url: `${API_BASE_URL}/api/video-analysis/${taskId}/retry`,
    method: 'post'
  });
}

/**
 * 导出PDF报告
 * @param {string} resultId - 视频分析结果ID
 * @returns {Promise} - 响应结果
 */
export function exportPdfReport(resultId) {
  return request({
    url: `${API_BASE_URL}/api/video-analysis/${resultId}/export`,
    method: 'get',
    responseType: 'blob'
  });
} 