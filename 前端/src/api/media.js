import axios from 'axios';
import http from '@/utils/http-common';
import config from '@/config/env';

// 后端基础URL
const BACKEND_URL = config.baseUrl;

// 检查是否启用视频代理 (可以在localStorage中设置videoProxyEnabled=false来禁用)
const isVideoProxyEnabled = () => {
  const setting = localStorage.getItem('videoProxyEnabled');
  return setting === null || setting !== 'false'; // 默认启用
};

/**
 * 为视频创建代理流，解决CORS问题
 * @param {string} videoId - 视频ID或路径
 * @returns {Promise<Blob>} - 返回视频Blob对象
 */
export const getVideoBlob = async (videoId) => {
  try {
    // 如果禁用了视频代理，则抛出异常
    if (!isVideoProxyEnabled()) {
      throw new Error('视频代理已禁用');
    }
    
    // 获取认证令牌
    const token = localStorage.getItem('auth_token');
    
    // 构建完整URL
    let url = '';
    if (/^[0-9a-f]{24}$/i.test(videoId)) {
      url = `${BACKEND_URL}${config.video.apiPath}${videoId}`;
    } else if (videoId.startsWith('/')) {
      url = `${BACKEND_URL}${videoId}`;
    } else {
      url = `${BACKEND_URL}/api/static/videos/${videoId}`;
    }
    
    // 添加认证令牌作为URL参数
    if (token) {
      url += (url.includes('?') ? '&' : '?') + 'auth_token=' + encodeURIComponent(token);
    }
    
    if (config.video.debug) console.log('请求视频URL:', url);
    
    // 使用axios直接请求视频内容并返回blob
    const response = await axios.get(url, {
      responseType: 'blob',
      headers: {
        'Authorization': token ? `Bearer ${token}` : '',
      },
      // 添加超时设置
      timeout: 30000 // 30秒超时
    });
    
    // 检查返回的内容类型和大小
    if (config.video.debug) {
      console.log('视频响应内容类型:', response.data.type);
      console.log('视频响应大小:', response.data.size);
    }
    
    // 检查响应内容是否为视频
    if (!response.data.type.includes('video/')) {
      // 如果没有正确的视频MIME类型，则手动创建新的Blob并指定类型为video/mp4
      const videoBlob = new Blob([response.data], { type: 'video/mp4' });
      if (config.video.debug) {
        console.log('创建了新的视频Blob，类型:', videoBlob.type);
      }
      return videoBlob;
    }
    
    return response.data;
  } catch (error) {
    console.error('通过axios获取视频数据失败，尝试使用XMLHttpRequest:', error);
    return getVideoBlobWithXHR(videoId);
  }
};

/**
 * 使用XMLHttpRequest获取视频Blob，作为备用方法
 * @param {string} videoId - 视频ID或路径 
 * @returns {Promise<Blob>} - 返回视频Blob对象
 */
export const getVideoBlobWithXHR = (videoId) => {
  return new Promise((resolve, reject) => {
    // 检查是否启用视频代理
    if (!isVideoProxyEnabled()) {
      reject(new Error('视频代理已禁用'));
      return;
    }
    
    // 获取认证令牌
    const token = localStorage.getItem('auth_token');
    
    // 构建完整URL
    let url = '';
    if (/^[0-9a-f]{24}$/i.test(videoId)) {
      url = `${BACKEND_URL}${config.video.apiPath}${videoId}`;
    } else if (videoId.startsWith('/')) {
      url = `${BACKEND_URL}${videoId}`;
    } else {
      url = `${BACKEND_URL}/api/static/videos/${videoId}`;
    }
    
    // 添加认证令牌作为URL参数
    if (token) {
      url += (url.includes('?') ? '&' : '?') + 'auth_token=' + encodeURIComponent(token);
    }
    
    if (config.video.debug) console.log('使用XHR请求视频URL:', url);
    
    const xhr = new XMLHttpRequest();
    xhr.open('GET', url, true);
    xhr.responseType = 'blob';
    
    // 设置认证头
    if (token) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    }
    
    xhr.onload = function() {
      if (this.status === 200) {
        const blob = this.response;
        if (config.video.debug) {
          console.log('XHR视频响应类型:', blob.type);
          console.log('XHR视频响应大小:', blob.size);
        }
        
        // 如果没有正确的视频MIME类型，手动创建
        if (!blob.type.includes('video/')) {
          const videoBlob = new Blob([blob], { type: 'video/mp4' });
          if (config.video.debug) {
            console.log('XHR创建了新的视频Blob，类型:', videoBlob.type);
          }
          resolve(videoBlob);
        } else {
          resolve(blob);
        }
      } else {
        reject(new Error('XHR请求失败: ' + this.status));
      }
    };
    
    xhr.onerror = function() {
      reject(new Error('XHR请求网络错误'));
    };
    
    xhr.send();
  });
};

/**
 * 为视频创建Blob URL
 * @param {string} videoId - 视频ID或路径
 * @returns {Promise<string>} - 返回可以在浏览器中直接使用的Blob URL
 */
export const createVideoBlobUrl = async (videoId) => {
  try {
    // 如果禁用了视频代理，抛出异常
    if (!isVideoProxyEnabled()) {
      throw new Error('视频代理已禁用');
    }
    
    const blob = await getVideoBlob(videoId);
    const url = URL.createObjectURL(blob);
    if (config.video.debug) {
      console.log('创建的Blob URL:', url, '类型:', blob.type, '大小:', blob.size);
    }
    return url;
  } catch (error) {
    console.error('创建视频Blob URL失败:', error);
    throw error;
  }
}; 