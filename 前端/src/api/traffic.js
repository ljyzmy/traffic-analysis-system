import apiClient from '@/utils/http-common'
import { getAuthHeader } from '@/utils/auth'

/**
 * 获取交通数据分析结果
 * @param {Object} params - 查询参数
 * @param {String} params.id - 分析ID
 * @param {String} params.type - 数据类型 (vehicle|flow|density)
 * @returns {Promise}
 */
export function getTrafficData(params) {
  // 使用统一认证头获取方法
  const authHeaders = getAuthHeader();
  
  return apiClient({
    url: '/analysis/data',
    method: 'get',
    params,
    headers: {
      ...authHeaders,
      // 添加调试头，帮助后端输出更详细的认证过程日志
      'X-Debug': 'true'
    },
    timeout: 30000, // 增加超时时间
    retry: 2,      // 添加重试次数
    retryDelay: 1000, // 重试延迟时间
    validateStatus: function () {
      // 允许所有状态码通过，以便在响应拦截器中处理
      return true;
    }
  })
}

/**
 * 上传视频进行分析
 * @param {FormData} data - 包含视频文件的表单数据
 * @returns {Promise}
 */
export function uploadVideo(data) {
  // 获取用户信息并添加到请求中
  const userInfo = localStorage.getItem('user');
  if (userInfo) {
    const user = JSON.parse(userInfo);
    if (user && user.username) {
      // 将用户名添加到FormData中
      data.append('username', user.username);
      if (user.id) {
        data.append('userId', user.id);
      }
    }
  }
  
  console.log('使用apiClient发送图像分析请求');
  
  // 使用apiClient发送请求，移除重复的/api前缀
  return apiClient.post('/analysis/analyze', data, {
    headers: {
      // 不设置Content-Type，让浏览器自动处理multipart/form-data边界
      'Accept': 'application/json',
      // 添加调试头，帮助后端输出更详细的认证过程日志
      'X-Debug': 'true'
    },
    timeout: 60000, // 60秒超时
    onUploadProgress: (progressEvent) => {
      const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
      console.log(`上传进度: ${percentCompleted}%`);
    }
  })
  .then(response => {
    // 确保返回的是标准格式
    return response.data;
  })
  .catch(error => {
    console.error('图像分析请求失败:', error);
    throw error; // 将错误继续向上抛出，让调用方处理
  });
}

/**
 * 获取特定分析任务的结果
 * @param {String} id - 分析任务ID
 * @returns {Promise}
 */
export function getAnalysisResult(id) {
  // 处理id参数，确保它是一个字符串
  let validId = id;
  
  // 检查id是否为对象
  if (typeof id === 'object' && id !== null) {
    console.warn('接收到对象格式的ID参数，尝试提取有效ID字符串');
    
    // 尝试从对象中获取有效的ID字符串
    if (id.result_id && typeof id.result_id === 'string') {
      validId = id.result_id;
    } else if (id.id && typeof id.id === 'string') {
      validId = id.id;
    } else if (id.analysis_result && id.analysis_result.id) {
      validId = id.analysis_result.id;
    } else if (id.analysisResult && id.analysisResult.id) {
      validId = id.analysisResult.id;
    } else if (id._id) {
      if (typeof id._id === 'string') {
        validId = id._id;
      } else if (typeof id._id === 'object' && id._id.$oid) {
        validId = id._id.$oid;
      } else {
        validId = String(id._id);
      }
    } else {
      // 如果无法找到有效ID，返回错误
      console.error('无法从对象中提取有效ID', id);
      return Promise.reject(new Error('无效的分析ID参数'));
    }
    
    console.log(`已从对象中提取ID: ${validId}`);
  }
  
  // 确保ID是字符串类型
  if (typeof validId !== 'string') {
    validId = String(validId);
  }
  
  // 验证ID格式
  if (!validId || validId === 'undefined' || validId === 'null' || validId === '[object Object]') {
    console.error('无效的分析ID:', validId);
    return Promise.reject(new Error('无效的分析ID参数'));
  }
  
  // 使用统一认证头获取方法
  const authHeaders = getAuthHeader();
  
  console.log(`请求分析结果，使用ID: ${validId}`);
  
  return apiClient({
    url: `/analysis/status/${validId}`,
    method: 'get',
    headers: {
      ...authHeaders,
      // 添加调试头，帮助后端输出更详细的认证过程日志
      'X-Debug': 'true'
    },
    timeout: 30000, // 增加超时时间
    retry: 2,      // 添加重试次数
    retryDelay: 1000 // 重试延迟时间
  })
}

/**
 * 获取分析历史记录
 * @param {Object} params - 分页参数
 * @param {Number} params.limit - 每页数量
 * @param {Number} params.skip - 跳过数量
 * @returns {Promise}
 */
export function getHistory(params = {}) {
  // 确保类型为image
  params.type = 'image';
  
  return apiClient({
    url: '/history',
    method: 'get',
    params,
    timeout: 30000, // 增加超时时间
    retry: 2, // 添加重试次数
    retryDelay: 1000 // 重试延迟时间
  })
}

/**
 * 删除历史记录
 * @param {String} id - 历史记录ID
 * @param {String} type - 历史记录类型，默认为'image'
 * @returns {Promise}
 */
export function deleteHistory(id, type = 'image') {
  return apiClient({
    url: `/history/${id}`,
    method: 'delete',
    params: { type }
  })
}

/**
 * 批量删除历史记录
 * @param {Array} ids - 要删除的历史记录ID数组
 * @param {String} type - 历史记录类型，默认为'image'
 * @returns {Promise} - 响应结果
 */
export function batchDeleteHistory(ids, type = 'image') {
  if (!Array.isArray(ids) || ids.length === 0) {
    return Promise.reject(new Error('无效的ID参数'));
  }
  
  console.log(`批量删除历史记录: ${ids.length}条记录，类型: ${type}`);
  
  return apiClient({
    url: '/api/history/batch-delete',
    method: 'DELETE',  // 保持DELETE方法
    data: { 
      ids: ids,
      type: type 
    }
  });
}

/**
 * 获取实时交通状态
 * @returns {Promise}
 */
export function getRealTimeStatus() {
  return apiClient({
    url: '/status/realtime',
    method: 'get'
  })
}

/**
 * 获取统计数据
 * @param {Object} params - 查询参数
 * @param {String} params.type - 统计类型
 * @param {String} params.period - 时间段
 * @returns {Promise}
 */
export function getStatistics(params) {
  return apiClient({
    url: '/statistics',
    method: 'get',
    params
  })
}

/**
 * 获取模型状态
 * @returns {Promise}
 */
export function getModelStatus() {
  // 直接使用apiClient发送请求，移除重复的/api前缀
  return apiClient.get('/model/status', {
    timeout: 30000, // 将超时时间从10000增加到30000
    retry: 2, // 添加重试次数
    retryDelay: 1000 // 重试延迟时间
  })
    .then(response => {
      // 确保返回的是标准格式
      return response.data;
    })
    .catch(error => {
      console.error('获取模型状态失败:', error);
      // 返回一个默认的离线状态，避免UI错误
      return { status: 'offline' };
    });
}

/**
 * 获取视频分析进度
 * @param {String} id - 分析任务ID
 * @returns {Promise}
 */
export function getAnalysisProgress(id) {
  // 使用统一认证头获取方法
  const authHeaders = getAuthHeader();
  
  return apiClient({
    url: `/analysis/progress/${id}`,
    method: 'get',
    headers: {
      ...authHeaders
    }
  })
} 