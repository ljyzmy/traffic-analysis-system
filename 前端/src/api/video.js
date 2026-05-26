import apiClient, { refreshAuthToken, getFullResourceUrl } from '@/utils/http-common';
import { ElMessage } from 'element-plus';

// 定义文件大小限制
const MAX_FILE_SIZE = 500 * 1024 * 1024; // 500MB
const MAX_SERVER_SIZE = 500 * 1024 * 1024; // 500MB (服务器限制)

/**
 * 从cookie中获取值的辅助函数
 * @param {string} name - cookie名称
 * @returns {string|null} - cookie值
 */
function getCookieValue(name) {
  const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
  return match ? match[2] : null;
}

/**
 * 验证令牌有效性
 * @returns {Promise<boolean>} 令牌是否有效
 */
export async function checkToken() {
  try {
    const authToken = localStorage.getItem('auth_token');
    if (!authToken) {
      console.warn('没有找到认证令牌');
      return false;
    }
    
    // 添加更多日志记录帮助调试
    console.log('检查令牌有效性，令牌前20字符:', authToken.substring(0, 20));
    
    // 使用统一的API客户端
    const response = await apiClient.get('/api/user/info');
    
    console.log('令牌验证成功:', response.status);
    return true;
  } catch (error) {
    console.error('令牌验证失败:', error.message);
    // 检查是否是认证错误
    if (error.response && error.response.status === 401) {
      console.error('令牌已过期或无效');
      // http-common的响应拦截器会处理认证错误
    }
    return false;
  }
}

/**
 * 上传并分析视频
 * @param {FormData} formData - 包含视频文件和其他参数的表单数据
 * @returns {Promise} - 响应结果
 */
export function uploadAndAnalyzeVideo(formData) {
  return new Promise((resolve, reject) => {
  // 判断是普通视频还是十字路口视频
  const isIntersection = formData.get('roadType') === 'intersection';
  const url = isIntersection 
      ? '/api/video-analysis/upload/intersection' 
      : '/api/video-analysis/upload';

    // 确保formData包含所需参数
    try {
      const userInfo = JSON.parse(localStorage.getItem('user') || '{}');
      if (userInfo.username && !formData.has('username')) {
        formData.append('username', userInfo.username);
      }
      if (userInfo.id && !formData.has('userId')) {
        formData.append('userId', userInfo.id);
      }
      if (userInfo.role && !formData.has('role')) {
        formData.append('role', userInfo.role);
      }
      
      // 从令牌中提取信息作为备用
      const token = localStorage.getItem('auth_token');
      if (token) {
        const parts = token.split('_');
        if (parts.length >= 5) {
          formData.append('tokenHash', parts[0]);
          formData.append('tokenUsername', parts[1]);
          formData.append('tokenUserId', parts[2]);
          formData.append('tokenRole', parts[3]);
          formData.append('tokenTimestamp', parts[4]);
        }
      }
    } catch (e) {
      console.warn('获取用户信息失败:', e);
    }
    
    // 确保FormData中包含必要参数
    if (!formData.has('direction') && !isIntersection) {
      formData.append('direction', 'horizontal');  // 默认为横向
  }

  // 检查文件大小
  let video;
  if (isIntersection) {
    const horizontalVideo = formData.get('horizontalVideo');
    const verticalVideo = formData.get('verticalVideo');
    
    if (horizontalVideo && horizontalVideo.size > MAX_FILE_SIZE) {
      ElMessage.error(`横向视频文件太大 (${(horizontalVideo.size / (1024*1024)).toFixed(2)}MB)，超过最大限制(${MAX_FILE_SIZE / (1024*1024)}MB)`);
        reject({
        message: `横向视频文件太大，请使用小于${MAX_FILE_SIZE / (1024*1024)}MB的文件`
      });
        return;
    }
    
    if (verticalVideo && verticalVideo.size > MAX_FILE_SIZE) {
      ElMessage.error(`纵向视频文件太大 (${(verticalVideo.size / (1024*1024)).toFixed(2)}MB)，超过最大限制(${MAX_FILE_SIZE / (1024*1024)}MB)`);
        reject({
        message: `纵向视频文件太大，请使用小于${MAX_FILE_SIZE / (1024*1024)}MB的文件`
      });
        return;
    }
    
      // 检查服务器限制 - 大文件使用分片上传
      if ((horizontalVideo && horizontalVideo.size > MAX_SERVER_SIZE) || 
          (verticalVideo && verticalVideo.size > MAX_SERVER_SIZE)) {
        ElMessage.warning(`视频文件超过服务器限制(${MAX_SERVER_SIZE / (1024*1024)}MB)，将使用分片上传`);
        // 使用现有的分片上传大文件
        uploadLargeIntersectionVideos(formData)
          .then(response => resolve(response))
          .catch(error => reject(error));
        return;
    }
  } else {
    video = formData.get('video');
    if (video && video.size > MAX_FILE_SIZE) {
      ElMessage.error(`视频文件太大 (${(video.size / (1024*1024)).toFixed(2)}MB)，超过最大限制(${MAX_FILE_SIZE / (1024*1024)}MB)`);
        reject({
        message: `视频文件太大，请使用小于${MAX_FILE_SIZE / (1024*1024)}MB的文件`
      });
        return;
    }
    
    // 检查服务器限制，如果超过则使用分片上传
    if (video && video.size > MAX_SERVER_SIZE) {
      ElMessage.warning(`视频文件(${(video.size / (1024*1024)).toFixed(2)}MB)超过服务器限制(${MAX_SERVER_SIZE / (1024*1024)}MB)，将使用分片上传`);
      const direction = formData.get('direction') || 'horizontal';
        uploadLargeVideo(video, { direction })
          .then(response => resolve(response))
          .catch(error => reject(error));
        return;
    }
  }

    // 调试认证信息
    debugAuthInfo();
    
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    
    // 获取令牌并添加到请求头
    const token = localStorage.getItem('auth_token');
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      
      // 解析令牌中的信息并添加到请求头
      const parts = token.split('_');
      if (parts.length >= 5) {
        // 令牌格式: [hash]_[username]_[userId]_[role]_[timestamp]
        const tokenUsername = parts[1];
        const tokenUserId = parts[2];
        const tokenRole = parts[3];
  
        // 添加令牌中的用户信息到请求头
        xhr.setRequestHeader('X-Token-Username', tokenUsername);
        xhr.setRequestHeader('X-Token-UserId', tokenUserId);
        xhr.setRequestHeader('X-Token-Role', tokenRole);
        
        // 添加到表单数据中，确保提供多种方式识别用户
        if (!formData.has('tokenUsername')) {
          formData.append('tokenUsername', tokenUsername);
        }
        if (!formData.has('tokenUserId')) {
          formData.append('tokenUserId', tokenUserId);
        }
        if (!formData.has('tokenRole')) {
          formData.append('tokenRole', tokenRole);
      }
      }
      
      // 添加额外的认证信息到请求头
      try {
        const userInfo = JSON.parse(localStorage.getItem('user') || '{}');
        if (userInfo.username) {
          xhr.setRequestHeader('X-User-Name', userInfo.username);
        }
        if (userInfo.id) {
          xhr.setRequestHeader('X-User-ID', userInfo.id);
        }
        if (userInfo.role) {
          xhr.setRequestHeader('X-User-Role', userInfo.role);
        }
      } catch (e) {
        console.warn('解析用户信息失败:', e);
      }
    } else {
      console.error('未找到认证令牌，无法上传视频');
      reject({
        message: '未找到认证令牌，请先登录',
        status: 401
      });
      return;
    }
    
    // 添加进度事件
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        const percentComplete = Math.round((e.loaded / e.total) * 100);
        console.log(`上传进度: ${percentComplete}%`);
        
        // 如果有进度回调函数，调用它
        if (typeof window.onVideoUploadProgress === 'function') {
          window.onVideoUploadProgress(percentComplete);
            }
          }
    });
    
    xhr.onload = function() {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const response = JSON.parse(xhr.responseText);
          console.log('视频上传成功，响应:', response);
          resolve({ data: response });
        } catch (e) {
          console.log('视频上传成功，但响应解析失败:', e);
          resolve({ data: xhr.responseText });
        }
      } else {
        console.error('视频上传失败，状态码:', xhr.status);
        
        // 检查是否是认证错误
        if (xhr.status === 401) {
          ElMessage.error('认证失败，请重新登录');
          reject({
            status: xhr.status,
            message: '认证失败，请重新登录',
            authError: true
        });
      } else {
          reject({
            status: xhr.status,
            message: `服务器错误 (${xhr.status}): ${xhr.statusText}`,
            data: xhr.responseText ? safeJsonParse(xhr.responseText) : null
          });
        }
      }
    };
    
    xhr.onerror = function() {
      console.error('网络错误，无法上传视频');
      reject({
        status: xhr.status || 0,
        message: '网络错误',
        data: null
      });
    };
    
    xhr.send(formData);
  });
}

/**
 * 调试认证信息
 */
function debugAuthInfo() {
  const token = localStorage.getItem('auth_token');
  const user = localStorage.getItem('user');
  
  console.group('认证信息调试');
  console.log('Token存在:', !!token);
  if (token) console.log('Token(前20位):', token.substring(0, 20));
  console.log('User信息存在:', !!user);
  if (user) {
    try {
      const userObj = JSON.parse(user);
      console.log('用户名:', userObj.username);
      console.log('角色:', userObj.role);
      console.log('ID:', userObj.id);
    } catch (e) {
      console.error('解析用户信息失败:', e);
      }
  }
  console.groupEnd();
}

/**
 * 获取视频任务状态
 * @param {string} taskId - 视频分析任务ID
 * @returns {Promise} - 响应结果
 */
export function getVideoTaskStatus(taskId) {
  return apiClient.get(`/api/video-analysis/${taskId}/status`);
}

/**
 * 获取视频分析结果
 * @param {string} resultId - 视频分析结果ID
 * @param {Object} options - 可选的请求选项
 * @returns {Promise} - 响应结果
 */
export function getVideoResult(resultId, options = {}) {
  return new Promise(async (resolve, reject) => {
    try {
      // 添加日志
      console.log(`请求视频分析结果: ID=${resultId}, 类型=${options.idType || '自动检测'}`);
      
      // 检查用户认证状态
      const token = localStorage.getItem('auth_token');
      console.log(`认证令牌: ${token ? '已设置' : '未设置'}`);
      
      if (!token) {
        console.warn('用户未登录，可能导致认证问题');
      }

      // 检测ID格式类型
      const isMongoObjectId = /^[0-9a-f]{24}$/i.test(resultId);
      const isUuid = resultId.includes('-');

      // 发送请求前记录时间
      const startTime = Date.now();
      
      // 构建额外的请求头，包含所有可能的认证信息
      const extraHeaders = {
        ...(options.headers || {})
      };
      
      if (token) {
        const parts = token.split('_');
        if (parts.length >= 5) {
          // 添加用户相关信息
          extraHeaders['X-Token-Username'] = parts[1];
          extraHeaders['X-Token-UserId'] = parts[2];
          extraHeaders['X-Token-Role'] = parts[3];
          extraHeaders['X-User-Name'] = parts[1];
          extraHeaders['X-User-ID'] = parts[2];
          extraHeaders['X-User-Role'] = parts[3];
        }
      }
      
      // 获取请求参数
      const params = {
        ...(options.params || {})
      };
      
      // 从本地存储获取用户信息
      try {
        const userStr = localStorage.getItem('user');
        if (userStr && !params.userId) {
          const user = JSON.parse(userStr);
          params.userId = user.id || '';
          params.username = user.username || '';
          params.role = user.role || '';
        }
      } catch (e) {
        console.warn('获取用户信息失败:', e);
      }
      
      // 根据ID类型构建API路径
      let apiPath;
      if (options.idType === 'mongodb') {
        // 显式指定为MongoDB ID
        apiPath = `/api/video-analysis/result/mongodb/${resultId}`;
      } else if (options.idType === 'uuid') {
        // 显式指定为UUID
        apiPath = `/api/video-analysis/result/uuid/${resultId}`;
      } else {
        // 使用智能转换API (默认)
        apiPath = `/api/video-analysis/result/${resultId}`;
      }
      
      // 如果是MongoDB ObjectId，尝试添加debug标记
      if (isMongoObjectId && options.retryWithUuid) {
        params.useSmartConversion = true;
      }
      
      console.log(`使用API路径: ${apiPath}, 参数:`, params);
      
      // 发送请求
      const response = await apiClient.get(apiPath, {
        validateStatus: function (status) {
          return status < 500; // 接受任何非500错误的响应
        },
        headers: extraHeaders,
        params: params,
        transformResponse: [function (data) {
          // 自定义响应处理，防止JSON解析错误
          try {
            // 检查是否为HTML响应
            if (typeof data === 'string' && data.trim().startsWith('<')) {
              console.warn('服务器返回了HTML而不是JSON:', data.substring(0, 100) + '...');
              return { htmlError: true, originalContent: data.substring(0, 200) };
            }
            return JSON.parse(data);
          } catch (e) {
            console.error('转换响应失败:', e.message);
            return { parseError: true, message: e.message, originalContent: typeof data === 'string' ? data.substring(0, 200) : null };
          }
        }]
      });
      
      // 请求耗时
      const requestTime = Date.now() - startTime;
      console.log(`获取视频结果请求耗时: ${requestTime}ms`);
      
      // 检查是否为HTML响应或认证错误
      if (response.data && response.data.htmlError) {
        console.error('服务器返回了HTML而不是JSON，可能是认证问题');
        return reject({ 
          message: '认证失败或会话过期，请重新登录',
          status: 'auth_error',
          authError: true,
          htmlResponse: true
        });
      }
      
      // 检查解析错误
      if (response.data && response.data.parseError) {
        console.error('解析服务器响应失败:', response.data.message);
        return reject({ 
          message: '解析服务器响应失败: ' + response.data.message,
          status: 'parse_error'
        });
      }
      
      // 检查是否为401认证错误
      if (response.status === 401) {
        console.error('认证失败，需要重新登录');
        
        // 如果没有refreshAuthToken函数，使用以下简化逻辑
        if (typeof refreshAuthToken === 'function') {
          try {
            const refreshed = await refreshAuthToken();
            if (refreshed) {
              console.log('令牌已刷新，重试获取结果');
              // 使用新令牌重试
              return getVideoResult(resultId)
                .then(result => resolve(result))
                .catch(error => reject(error));
            }
          } catch (refreshError) {
            console.error('刷新令牌失败:', refreshError);
          }
        } else {
          console.warn('refreshAuthToken函数未定义，无法刷新令牌');
        }
        
        return reject({
          message: '认证失败，请重新登录',
          status: 'auth_error',
          authError: true
        });
      }
      
      // 检查响应状态
      if (response.status >= 400) {
        console.error(`服务器返回错误状态码: ${response.status}`);
        return reject({ 
          message: response.data?.message || response.data?.error || `服务器错误 (${response.status})`,
          status: 'error',
          serverStatus: response.status
        });
      }
      
      // 检查响应数据
      if (response.data) {
        // 确保返回格式一致
        const result = {
          status: 'success',
          data: response.data
        };
        
        // 确保必要的嵌套属性存在
        if (result.data) {
          // 1. 确保 vehicleTypeStats 被映射为 vehicle_type_stats
          if (result.data.vehicleTypeStats && !result.data.vehicle_type_stats) {
            console.log('将 vehicleTypeStats 映射为 vehicle_type_stats');
            result.data.vehicle_type_stats = result.data.vehicleTypeStats;
          }

          // 2. 确保 vehicleCount 被映射为 vehicle_count
          if (result.data.vehicleCount !== undefined && result.data.vehicle_count === undefined) {
            console.log('将 vehicleCount 映射为 vehicle_count');
            result.data.vehicle_count = result.data.vehicleCount;
          }

          // 3. 处理嵌套的视频信息
          if (!result.data.video_info) {
            console.warn('视频信息字段缺失，添加默认值');
            result.data.video_info = {
              filename: result.data.video_filename || result.data.videoFilename || '未知文件名',
              direction: result.data.direction || 'horizontal',
              duration_seconds: 0,
              width: 1280,
              height: 720
            };
          }
          
          // 确保视频URL和图像URL包含完整域名
          const baseUrl = process.env.VUE_APP_API_BASE_URL || '';
          
          // 修正视频URL
          if (result.data.videoUrl && !result.data.videoUrl.startsWith('http')) {
            console.log('修正视频URL:', result.data.videoUrl);
            const backendUrl = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080';
            if (result.data.videoUrl.startsWith('/api/media/video/')) {
              result.data.videoUrl = `${backendUrl}${result.data.videoUrl}`;
            } else {
              result.data.videoUrl = `${backendUrl}${result.data.videoUrl}`;
            }
          }
          
          // 修正结果路径URL
          if (result.data.result_path || result.data.resultPath) {
            const path = result.data.result_path || result.data.resultPath;
            console.log('修正视频结果路径:', path);
            result.data.videoUrl = processVideoUrl(path);
          }
          
          // 修正图像URL
          if (result.data.imageUrl && !result.data.imageUrl.startsWith('http')) {
            console.log('修正图像URL:', result.data.imageUrl);
            result.data.imageUrl = getFullResourceUrl(result.data.imageUrl);
          }
          
          // 修正报告URL
          if (result.data.reportUrl && !result.data.reportUrl.startsWith('http')) {
            console.log('修正报告URL:', result.data.reportUrl);
            result.data.reportUrl = getFullResourceUrl(result.data.reportUrl);
          }
          
          // 4. 处理嵌套的分析摘要
          if (!result.data.analysis_summary) {
            console.warn('分析摘要字段缺失，添加默认值');
            result.data.analysis_summary = {
              total_vehicles: result.data.vehicle_count || result.data.vehicleCount || 0,
              traffic_density: 0,
              peak_times: []
            };
          }
          
          // 5. 确保其他可能的字段映射
          if (result.data.processingTime !== undefined && result.data.processing_time === undefined) {
            result.data.processing_time = result.data.processingTime;
          }

          if (result.data.resultPath && !result.data.result_path) {
            result.data.result_path = result.data.resultPath;
          }
          
          // 6. 确保报告URL字段存在 (新增)
          if (result.data.report_url && !result.data.reportUrl) {
            console.log('将 report_url 映射为 reportUrl');
            result.data.reportUrl = result.data.report_url;
          } else if (result.data.reportPath && !result.data.reportUrl && !result.data.report_url) {
            console.log('将 reportPath 映射为 reportUrl');
            result.data.reportUrl = result.data.reportPath;
          }
          
          if (!result.data.mode && result.data.direction) {
            // 默认为单向分析模式
            result.data.mode = 'single';
          }
          
          // 确保数据格式标准化
          if (!result.data.frames && result.data.detection_frames) {
            console.log('将detection_frames标准化为frames');
            result.data.frames = result.data.detection_frames;
          }
          
          // 6. 打印调试信息以便于验证
          console.log('数据映射完成，关键字段: ', {
            vehicle_count: result.data.vehicle_count,
            vehicle_type_stats: result.data.vehicle_type_stats,
            video_info: result.data.video_info,
            processing_time: result.data.processing_time,
            report_url: result.data.reportUrl || result.data.report_url
          });
        }
        
        resolve(result);
      } else {
        reject({ 
          message: '响应数据格式不正确',
          status: 'error' 
        });
      }
    } catch (err) {
      console.error('获取视频分析结果失败:', err);
      
      // 检查是否是网络错误
      if (err.message && (err.message.includes('Network Error') || err.message.includes('网络错误'))) {
        reject({
          message: '网络连接失败，请检查您的网络连接',
          status: 'network_error',
          error: err
        });
        return;
      }
      
      // 检查是否是认证错误
      if (err.response && err.response.status === 401) {
        reject({
          message: '认证失败，请重新登录',
          status: 'auth_error',
          authError: true,
          error: err
        });
        return;
      }
      
      reject({ 
        message: err.message || '获取分析结果失败',
        status: 'error',
        error: err
      });
    }
  });
}

/**
 * 获取视频任务列表
 * @param {Object} params - 查询参数
 * @param {Object} customHeaders - 自定义请求头
 * @returns {Promise} - 响应结果
 */
export function getVideoTaskList(params = {}, customHeaders = {}) {
  let url = `/api/history/list`;
  
  // 确保类型为video
  params.type = 'video';
  
  return apiClient.get(url, {
    params,
    headers: customHeaders
  });
}

/**
 * 重新分析视频
 * @param {string} taskId - 视频分析任务ID
 * @returns {Promise} - 响应结果
 */
export function retryVideoAnalysis(taskId) {
  return apiClient.post(`/api/video-analysis/${taskId}/retry`);
}

/**
 * 导出PDF报告
 * @param {string} resultId - 视频分析结果ID
 * @returns {Promise} - 响应结果
 */
export function exportPdfReport(resultId) {
  // 确保URL包含完整域名
  const url = getFullResourceUrl(`/api/video-analysis/${resultId}/export`);
  console.log('导出PDF报告URL:', url);
  
  return new Promise((resolve, reject) => {
    apiClient.get(url, {
    responseType: 'blob'
    })
    .then(response => {
      // 检查内容类型是否为PDF
      const contentType = response.headers['content-type'];
      if (contentType && contentType.includes('application/pdf')) {
        resolve(response);
      } else {
        // 如果不是PDF，那么转换为文本并处理
        const reader = new FileReader();
        reader.onload = () => {
          const text = reader.result;
          console.error('导出PDF失败: 服务器没有返回PDF文件', text.substring(0, 200));
          
          // 尝试检测HTML响应
          if (text.includes('<html') || text.includes('<!DOCTYPE')) {
            reject({
              message: '服务器返回了HTML而不是PDF (可能是认证问题)',
              htmlResponse: true,
              data: text.substring(0, 1000)
            });
          } else {
            // 尝试解析为JSON
            try {
              const jsonData = JSON.parse(text);
              reject({
                message: jsonData.message || '服务器返回了JSON而不是PDF',
                data: jsonData
              });
            } catch (e) {
              reject({
                message: '服务器返回了无效数据',
                data: text.substring(0, 200)
              });
            }
          }
        };
        reader.onerror = () => {
          reject({
            message: '无法读取服务器响应内容',
            data: null
          });
        };
        reader.readAsText(response.data);
      }
    })
    .catch(error => {
      console.error('导出PDF报告时发生错误:', error);
      reject(error);
    });
  });
}

// 分片上传大文件
export function uploadLargeVideo(file, options = {}) {
  // 确保API路径一致性
  const chunkSize = 5 * 1024 * 1024; // 5MB一片
  const totalChunks = Math.ceil(file.size / chunkSize);
  let currentChunk = 0;
  const taskId = Date.now().toString(); // 临时任务ID
  
  const uploadNextChunk = async () => {
    if (currentChunk >= totalChunks) {
      // 所有分片上传完成
      return mergeVideoChunks(taskId);
    }
    
    const start = currentChunk * chunkSize;
    const end = Math.min(file.size, start + chunkSize);
    const chunk = file.slice(start, end);
    
    const formData = new FormData();
    formData.append('file', chunk);
    formData.append('taskId', taskId);
    formData.append('chunkIndex', currentChunk);
    formData.append('totalChunks', totalChunks);
    
    // 其他需要的参数
    if (options.direction) formData.append('direction', options.direction);
    
    // 添加当前用户信息
    try {
      const userInfo = JSON.parse(localStorage.getItem('user') || '{}');
      if (userInfo.username) {
        formData.append('username', userInfo.username);
      }
      if (userInfo.id) {
        formData.append('userId', userInfo.id);
      }
      if (userInfo.role) {
        formData.append('role', userInfo.role);
      }
    } catch (e) {
      console.warn('添加用户信息失败:', e);
    }
    
      // 使用apiClient发送请求
    await apiClient.post(`/api/video-analysis/chunk-upload`, formData);
      
      // 更新进度
      if (options.onProgress) {
        options.onProgress({
          percent: Math.round(((currentChunk + 1) / totalChunks) * 100)
        });
      }
    
    // 如果有全局进度回调函数，调用它
    if (typeof window.onVideoUploadProgress === 'function') {
      window.onVideoUploadProgress(Math.round(((currentChunk + 1) / totalChunks) * 100));
    }
      
      currentChunk++;
      return uploadNextChunk();
  };
  
  return uploadNextChunk();
}

// 合并视频分片
function mergeVideoChunks(taskId) {
  return apiClient.post(`/api/video-analysis/merge-chunks`, {
    taskId
  });
}

// 为十字路口上传大文件
export function uploadLargeIntersectionVideos(formData) {
  const horizontalVideo = formData.get('horizontalVideo');
  const verticalVideo = formData.get('verticalVideo');
  
  if (!horizontalVideo || !verticalVideo) {
    return Promise.reject({
      message: '缺少横向或纵向视频文件'
    });
  }
  
  // 创建两个上传任务
  const horizontalTask = uploadLargeVideo(horizontalVideo, { 
    direction: 'horizontal',
    isIntersection: true
  });
  
  const verticalTask = uploadLargeVideo(verticalVideo, { 
    direction: 'vertical',
    isIntersection: true
  });
  
  // 等待两个任务完成
  return Promise.all([horizontalTask, verticalTask])
    .then(([horizontalResult, verticalResult]) => {
      // 合并结果并处理
      return apiClient.post('/api/video-analysis/merge-intersection', {
        horizontalTaskId: horizontalResult.data.taskId,
        verticalTaskId: verticalResult.data.taskId
      });
    });
}

/**
 * 使用XMLHttpRequest上传视频 (作为备选上传方式)
 * @param {FormData} formData - 包含视频文件和其他参数的表单数据
 * @returns {Promise} - 响应结果
 */
export function uploadVideoWithXHR(formData) {
  // 判断是普通视频还是十字路口视频
  const isIntersection = formData.get('roadType') === 'intersection';
  const url = isIntersection 
    ? `/api/video-analysis/upload/intersection` 
    : `/api/video-analysis/upload`;

  // 确保FormData中包含必要参数
  if (!formData.has('direction') && !isIntersection) {
    formData.append('direction', 'horizontal');  // 默认为横向
  }
  
  // 向formData添加用户信息，帮助后端识别
  try {
    const userInfo = JSON.parse(localStorage.getItem('user') || '{}');
    if (userInfo.username && !formData.has('username')) {
      formData.append('username', userInfo.username);
    }
    if (userInfo.id && !formData.has('userId')) {
      formData.append('userId', userInfo.id);
    }
    if (userInfo.role && !formData.has('role')) {
      formData.append('role', userInfo.role || 'user');
    }
    
    // 从令牌中提取信息作为备用
    const token = localStorage.getItem('auth_token');
    if (token) {
      const parts = token.split('_');
      if (parts.length >= 5) {
        formData.append('tokenHash', parts[0]);
        formData.append('tokenUsername', parts[1]);
        formData.append('tokenUserId', parts[2]);
        formData.append('tokenRole', parts[3]);
        formData.append('tokenTimestamp', parts[4]);
      }
    }
  } catch (e) {
    console.warn('获取用户信息失败:', e);
  }

  // 调试认证信息
  debugAuthInfo();

  // 手动创建XMLHttpRequest以便更好地控制请求
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', url);
    
    // 获取令牌并添加到请求头
    const token = localStorage.getItem('auth_token');
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      
      // 解析令牌中的信息并添加到请求头
      const parts = token.split('_');
      if (parts.length >= 5) {
        // 令牌格式: [hash]_[username]_[userId]_[role]_[timestamp]
        const tokenUsername = parts[1];
        const tokenUserId = parts[2];
        const tokenRole = parts[3];
        
        // 添加令牌中的用户信息到请求头
        xhr.setRequestHeader('X-Token-Username', tokenUsername);
        xhr.setRequestHeader('X-Token-UserId', tokenUserId);
        xhr.setRequestHeader('X-Token-Role', tokenRole);
        
        // 添加到表单数据中，确保提供多种方式识别用户
        if (!formData.has('tokenUsername')) {
          formData.append('tokenUsername', tokenUsername);
        }
        if (!formData.has('tokenUserId')) {
          formData.append('tokenUserId', tokenUserId);
        }
        if (!formData.has('tokenRole')) {
          formData.append('tokenRole', tokenRole);
        }
      }
      
      // 添加额外的认证信息到请求头
      try {
        const userInfo = JSON.parse(localStorage.getItem('user') || '{}');
        if (userInfo.username) {
          xhr.setRequestHeader('X-User-Name', userInfo.username);
        }
        if (userInfo.id) {
          xhr.setRequestHeader('X-User-ID', userInfo.id);
        }
        if (userInfo.role) {
          xhr.setRequestHeader('X-User-Role', userInfo.role);
        }
      } catch (e) {
        console.warn('解析用户信息失败:', e);
      }
    } else {
      console.error('未找到认证令牌，无法上传视频');
      reject({
        message: '未找到认证令牌，请先登录',
        status: 401
      });
    }
    
    // 进度监听
    xhr.upload.addEventListener('progress', (e) => {
      if (e.lengthComputable) {
        const percentComplete = Math.round((e.loaded / e.total) * 100);
        console.log(`上传进度: ${percentComplete}%`);
        // 这里可以添加进度回调
        
        // 如果有全局进度回调函数，调用它
        if (typeof window.onVideoUploadProgress === 'function') {
          window.onVideoUploadProgress(percentComplete);
        }
      }
    });
    
    xhr.onload = function() {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const response = JSON.parse(xhr.responseText);
          console.log('视频上传成功，响应:', response);
          resolve({ data: response });
        } catch (e) {
          console.log('视频上传成功，但响应解析失败:', e);
          resolve({ data: xhr.responseText });
        }
      } else {
        console.error('视频上传失败，状态码:', xhr.status);
        
        // 检查是否是认证错误
        if (xhr.status === 401) {
          ElMessage.error('认证失败，请重新登录');
          reject({
            status: xhr.status,
            message: '认证失败，请重新登录',
            authError: true
          });
        } else {
          reject({
            status: xhr.status,
            message: `服务器错误 (${xhr.status}): ${xhr.statusText}`,
            data: xhr.responseText ? safeJsonParse(xhr.responseText) : null
          });
        }
      }
    };
    
    xhr.onerror = function() {
      console.error('网络错误，无法上传视频');
      reject({
        status: xhr.status,
        message: '网络错误',
        data: null
      });
    };
    
    xhr.send(formData);
  });
}

/**
 * 发送诊断请求，用于调试认证问题
 * @returns {Promise} - 响应结果
 */
export function debugRequest() {
  console.log('发送诊断请求...');
  
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', '/api/video-analysis/debug');
    
    // 添加所有可能的认证头
    const token = localStorage.getItem('auth_token');
    if (token) {
      console.log('添加令牌到诊断请求:', token.substring(0, 10) + '...');
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
      
      // 解析令牌中的信息并添加到请求头
      const parts = token.split('_');
      if (parts.length >= 5) {
        xhr.setRequestHeader('X-Token-Username', parts[1]);
        xhr.setRequestHeader('X-Token-UserId', parts[2]);
        xhr.setRequestHeader('X-Token-Role', parts[3]);
      }
    }
    
    // 添加Cookie头
    xhr.withCredentials = true;
    
    // 添加用户信息
    try {
      const userStr = localStorage.getItem('user');
      if (userStr) {
        const user = JSON.parse(userStr);
        console.log('添加用户信息到诊断请求头:', user.username);
        xhr.setRequestHeader('X-User-ID', user.id);
        xhr.setRequestHeader('X-User-Name', user.username);
        xhr.setRequestHeader('X-User-Role', user.role || 'user');
        
        // 添加到查询参数
        xhr.open('GET', `/api/video-analysis/debug?userId=${user.id}&username=${user.username}`);
      }
    } catch (e) {
      console.error('获取用户信息失败:', e);
    }
    
    // 添加详细请求头信息
    xhr.setRequestHeader('X-Debug', 'true');
    xhr.setRequestHeader('X-Debug-Time', new Date().toISOString());
    xhr.setRequestHeader('Accept', 'application/json');
    
    xhr.onload = function() {
      console.log('诊断请求响应状态码:', xhr.status);
      
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          const response = JSON.parse(xhr.responseText);
          console.log('诊断请求成功，响应:', response);
          resolve(response);
        } catch (e) {
          console.log('诊断请求成功，但响应解析失败:', e);
          resolve(xhr.responseText);
        }
      } else {
        console.error('诊断请求失败，错误状态码:', xhr.status);
        reject({
          status: xhr.status,
          message: `请求错误 (${xhr.status}): ${xhr.statusText}`,
          response: xhr.responseText
        });
      }
    };
    
    xhr.onerror = function(e) {
      console.error('诊断请求网络错误:', e);
      reject({
        status: 0,
        message: '网络错误',
        error: e
      });
    };
    
    xhr.send();
  });
}

/**
 * 使用axios上传视频（推荐使用此方法替代传统XMLHttpRequest）
 * @param {FormData} formData - 包含视频文件和其他参数的表单数据
 * @returns {Promise} - 响应结果
 */
export function uploadWithAxios(formData) {
  return new Promise((resolve, reject) => {
    // 判断是普通视频还是十字路口视频
    const isIntersection = formData.get('roadType') === 'intersection';
    const url = isIntersection 
      ? '/api/video-analysis/upload/intersection' 
      : '/api/video-analysis/upload';
      
    // 检查文件大小
    let video;
    if (isIntersection) {
      const horizontalVideo = formData.get('horizontalVideo');
      const verticalVideo = formData.get('verticalVideo');
      
      if (horizontalVideo && horizontalVideo.size > MAX_FILE_SIZE) {
        ElMessage.error(`横向视频文件太大 (${(horizontalVideo.size / (1024*1024)).toFixed(2)}MB)，超过最大限制(${MAX_FILE_SIZE / (1024*1024)}MB)`);
        reject({
          message: `横向视频文件太大，请使用小于${MAX_FILE_SIZE / (1024*1024)}MB的文件`
        });
        return;
      }
      
      if (verticalVideo && verticalVideo.size > MAX_FILE_SIZE) {
        ElMessage.error(`纵向视频文件太大 (${(verticalVideo.size / (1024*1024)).toFixed(2)}MB)，超过最大限制(${MAX_FILE_SIZE / (1024*1024)}MB)`);
        reject({
          message: `纵向视频文件太大，请使用小于${MAX_FILE_SIZE / (1024*1024)}MB的文件`
        });
        return;
      }
    } else {
      video = formData.get('video');
      if (video && video.size > MAX_FILE_SIZE) {
        ElMessage.error(`视频文件太大 (${(video.size / (1024*1024)).toFixed(2)}MB)，超过最大限制(${MAX_FILE_SIZE / (1024*1024)}MB)`);
        reject({
          message: `视频文件太大，请使用小于${MAX_FILE_SIZE / (1024*1024)}MB的文件`
        });
        return;
      }
    }
    
    // 添加用户信息
    try {
      const userInfo = JSON.parse(localStorage.getItem('user') || '{}');
      if (userInfo.username && !formData.has('username')) {
        formData.append('username', userInfo.username);
      }
      if (userInfo.id && !formData.has('userId')) {
        formData.append('userId', userInfo.id);
      }
      if (userInfo.role && !formData.has('role')) {
        formData.append('role', userInfo.role);
      }
      
      // 从令牌中提取信息作为备用
      const token = localStorage.getItem('auth_token');
      if (token) {
        const parts = token.split('_');
        if (parts.length >= 5) {
          formData.append('tokenHash', parts[0]);
          formData.append('tokenUsername', parts[1]);
          formData.append('tokenUserId', parts[2]);
          formData.append('tokenRole', parts[3]);
          formData.append('tokenTimestamp', parts[4]);
        }
      }
    } catch (e) {
      console.warn('获取用户信息失败:', e);
    }
    
    // 使用apiClient发送请求
    apiClient.post(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      onUploadProgress: (progressEvent) => {
        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        console.log("上传进度:", percentCompleted + "%");
        
        // 如果有全局进度回调函数，调用它
        if (typeof window.onVideoUploadProgress === 'function') {
          window.onVideoUploadProgress(percentCompleted);
        }
      }
    })
    .then(response => {
      console.log('视频上传成功，响应:', response.data);
      resolve({ data: response.data });
    })
    .catch(error => {
      console.error('视频上传失败:', error);
      
      // 处理认证错误
      if (error.response && error.response.status === 401) {
        ElMessage.error('认证失败，请重新登录');
        reject({
          status: error.response.status,
          message: '认证失败，请重新登录',
          authError: true
        });
      } else {
        reject({
          status: error.response ? error.response.status : 0,
          message: error.message || '上传失败',
          data: error.response ? error.response.data : null
        });
      }
    });
  });
}

/**
 * 使用新的直接上传API上传并分析视频
 * @param {File} file - 视频文件
 * @param {string} direction - 视频方向
 * @param {string} roadType - 道路类型
 * @returns {Promise<Object>} 上传结果
 */
export async function uploadVideoDirectApi(file, direction, roadType) {
  try {
    // 检查文件大小
    if (file && file.size > MAX_FILE_SIZE) {
      ElMessage.error(`视频文件太大 (${(file.size / (1024*1024)).toFixed(2)}MB)，超过最大限制(${MAX_FILE_SIZE / (1024*1024)}MB)`);
      throw new Error(`视频文件太大，请使用小于${MAX_FILE_SIZE / (1024*1024)}MB的文件`);
    }
    
    // 获取用户信息
    const token = localStorage.getItem('auth_token');
    const userStr = localStorage.getItem('user');
    
    if (!token || !userStr) {
      throw new Error('未找到认证信息，请先登录');
    }
    
    const user = JSON.parse(userStr);
    const userId = user.id || '';
    const username = user.username || '';
    const role = user.role || 'user';
    
    console.log("认证信息调试");
    console.log("Token存在:", !!token);
    console.log("Token(前20位):", token.substring(0, 20));
    console.log("User信息存在:", !!user);
    console.log("用户名:", username);
    console.log("角色:", role);
    console.log("ID:", userId);
    
    // 使用新的直接上传API
    const formData = new FormData();
    formData.append('video', file);
    formData.append('direction', direction);
    formData.append('roadType', roadType);
    formData.append('userId', userId);
    formData.append('username', username);
    formData.append('role', role);
    
    // 从令牌中提取信息作为备用
    const parts = token.split('_');
    if (parts.length >= 5) {
      formData.append('tokenHash', parts[0]);
      formData.append('tokenUsername', parts[1]);
      formData.append('tokenUserId', parts[2]);
      formData.append('tokenRole', parts[3]);
      formData.append('tokenTimestamp', parts[4]);
    }
    
    // 创建API客户端配置
    const config = {
      headers: {
        'Authorization': `Bearer ${token}`,
        'X-User-ID': userId,
        'X-User-Name': username,
        'X-User-Role': role
      },
      onUploadProgress: (progressEvent) => {
        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        console.log("上传进度:", percentCompleted + "%");
        // 如果有进度回调函数，调用它
        if (typeof window.onVideoUploadProgress === 'function') {
          window.onVideoUploadProgress(percentCompleted);
        }
      },
      timeout: 180000 // 3分钟
    };
    
    // 发送请求到直接上传API
    const response = await apiClient.post('/api/video-analysis/upload/direct', formData, config);
    
    return response.data;
  } catch (error) {
    console.error("视频上传失败:", error);
    // 如果是认证错误，添加特殊标记
    if (error.response && error.response.status === 401) {
      error.authError = true;
    }
    throw error;
  }
}

/**
 * 上传十字路口的两个视频文件使用直接API
 * @param {File} horizontalFile - 横向视频文件
 * @param {File} verticalFile - 纵向视频文件
 * @returns {Promise<Object>} 上传结果
 */
export async function uploadIntersectionDirectApi(horizontalFile, verticalFile) {
  try {
    // 检查文件大小
    if (horizontalFile && horizontalFile.size > MAX_FILE_SIZE) {
      ElMessage.error(`横向视频文件太大 (${(horizontalFile.size / (1024*1024)).toFixed(2)}MB)，超过最大限制(${MAX_FILE_SIZE / (1024*1024)}MB)`);
      throw new Error(`横向视频文件太大，请使用小于${MAX_FILE_SIZE / (1024*1024)}MB的文件`);
    }
    
    if (verticalFile && verticalFile.size > MAX_FILE_SIZE) {
      ElMessage.error(`纵向视频文件太大 (${(verticalFile.size / (1024*1024)).toFixed(2)}MB)，超过最大限制(${MAX_FILE_SIZE / (1024*1024)}MB)`);
      throw new Error(`纵向视频文件太大，请使用小于${MAX_FILE_SIZE / (1024*1024)}MB的文件`);
    }
    
    // 获取用户信息
    const token = localStorage.getItem('auth_token');
    const userStr = localStorage.getItem('user');
    
    if (!token || !userStr) {
      throw new Error('未找到认证信息，请先登录');
    }
    
    const user = JSON.parse(userStr);
    const userId = user.id || '';
    const username = user.username || '';
    const role = user.role || 'user';
    
    // 使用新的直接上传API
    const formData = new FormData();
    formData.append('horizontalVideo', horizontalFile);
    formData.append('verticalVideo', verticalFile);
    formData.append('roadType', 'intersection');
    formData.append('userId', userId);
    formData.append('username', username);
    formData.append('role', role);
    
    // 从令牌中提取信息作为备用
    const parts = token.split('_');
    if (parts.length >= 5) {
      formData.append('tokenHash', parts[0]);
      formData.append('tokenUsername', parts[1]);
      formData.append('tokenUserId', parts[2]);
      formData.append('tokenRole', parts[3]);
      formData.append('tokenTimestamp', parts[4]);
    }
    
    // 创建API客户端配置
    const config = {
      headers: {
        'Authorization': `Bearer ${token}`,
        'X-User-ID': userId,
        'X-User-Name': username,
        'X-User-Role': role
      },
      onUploadProgress: (progressEvent) => {
        const percentCompleted = Math.round((progressEvent.loaded * 100) / progressEvent.total);
        console.log("上传进度:", percentCompleted + "%");
        // 如果有进度回调函数，调用它
        if (typeof window.onVideoUploadProgress === 'function') {
          window.onVideoUploadProgress(percentCompleted);
        }
      },
      timeout: 300000 // 5分钟，交叉口上传需要更长时间
    };
    
    // 发送请求到直接上传API
    const response = await apiClient.post('/api/video-analysis/upload/direct/intersection', formData, config);
    
    return response.data;
  } catch (error) {
    console.error("十字路口视频上传失败:", error);
    // 如果是认证错误，添加特殊标记
    if (error.response && error.response.status === 401) {
      error.authError = true;
    }
    throw error;
  }
}

/**
 * 更新视频名称
 * @param {string} taskId - 视频分析任务ID
 * @param {string} videoName - 新的视频名称
 * @returns {Promise} - 响应结果
 */
export function updateVideoName(taskId, videoName) {
  console.log(`尝试重命名视频 ${taskId} 为 "${videoName}"`);
  
  return apiClient.post(`/api/history/rename`, {
    taskId: taskId,
    videoName: videoName
  })
  .then(response => {
    console.log(`重命名响应:`, response.status, response.data);
    
    // 如果后端返回不是标准格式，手动构造一个标准格式
    if (response.status >= 200 && response.status < 300) {
      if (!response.data || typeof response.data !== 'object') {
        return {
          status: response.status,
          data: {
            success: true,
            message: '重命名成功',
            videoName: videoName,
            taskId: taskId
          }
        };
      }
    }
    
    return response;
  })
  .catch(error => {
    console.error('视频重命名请求失败:', error);
    throw error;
  });
}

// 安全解析JSON，防止非JSON内容导致错误
function safeJsonParse(text) {
  try {
    // 检查是否为HTML内容
    if (text.trim().startsWith('<')) {
      console.warn('服务器返回了HTML而不是JSON:', text.substring(0, 100) + '...');
      return { htmlError: true, message: '服务器返回了HTML而不是JSON' };
    }
    return JSON.parse(text);
  } catch (e) {
    console.error('JSON解析错误:', e.message, '原始文本:', text.substring(0, 100) + '...');
    return { parseError: true, message: e.message };
  }
}

/**
 * 处理视频资源URL
 * @param {string} path - 原始路径
 * @returns {string} - 处理后的URL
 */
function processVideoUrl(path) {
  if (!path) return '';
  
  // 后端服务器基础URL
  const backendUrl = process.env.VUE_APP_API_BASE_URL || 'http://localhost:8080';
  
  // 检查是否为GridFS ID（24位十六进制字符串）
  if (/^[0-9a-f]{24}$/i.test(path)) {
    return `${backendUrl}/api/media/video/${path}`;
  }
  
  // 检查是否为文件路径
  if (path.includes('\\') || path.includes('/')) {
    const filename = path.split(/[\/\\]/).pop();
    return `${backendUrl}/api/static/videos/${filename}`;
  }
  
  // 默认处理
  return `${backendUrl}/api/static/videos/${path}`;
}

/**
 * 根据taskId获取结果ID
 * @param {string} taskId - 视频分析任务ID
 * @returns {Promise} - 返回包含结果ID的响应
 */
export function getVideoResultId(taskId) {
  return new Promise(async (resolve, reject) => {
    try {
      // 使用新API端点进行任务ID到结果ID的转换
      const response = await apiClient.get(`/api/video-analysis/task/${taskId}/result-id`, {
        validateStatus: function (status) {
          return status < 500; // 接受任何非500错误的响应
        }
      });
      
      if (response.status === 404) {
        return reject({ message: '未找到对应的结果ID', status: 'not_found' });
      }
      
      if (response.status !== 200) {
        return reject({ 
          message: response.data?.message || `获取结果ID失败 (${response.status})`,
          status: 'error'
        });
      }
      
      resolve(response.data);
    } catch (err) {
      console.error('获取结果ID失败:', err);
      reject({ message: err.message || '获取结果ID失败', status: 'error' });
    }
  });
}

/**
 * 保存视频处理时间
 * @param {string} taskId - 视频分析任务ID
 * @param {number} processingTime - 处理时间（秒）
 * @returns {Promise} - 响应结果
 */
export function saveVideoProcessingTime(taskId, processingTime) {
  return apiClient.post(`/api/video-analysis/${taskId}/processing-time?processingTime=${processingTime}`);
} 