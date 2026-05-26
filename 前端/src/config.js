// 应用全局配置

// 基础API URL，在开发环境中使用相对路径以支持代理，生产环境中使用完整URL
export const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? 'http://api-server.example.com/api'  // 生产环境API地址
  : '/api';  // 开发环境使用相对路径，通过Vite代理转发

// 文件上传限制
export const FILE_SIZE_LIMIT = 500 * 1024 * 1024; // 500MB

// 请求超时设置
export const REQUEST_TIMEOUT = 60000; // 60秒

// WebSocket配置
export const WS_BASE_URL = process.env.NODE_ENV === 'production'
  ? 'ws://api-server.example.com'
  : window.location.protocol === 'https:'
    ? `wss://${window.location.hostname}:${window.location.port || 443}`
    : `ws://${window.location.hostname}:${window.location.port || 8080}`;

// SockJS和STOMP配置
export const SOCKJS_ENDPOINT = process.env.NODE_ENV === 'production'
  ? 'https://api-server.example.com/api/ws'
  : `${window.location.protocol}//${window.location.hostname}:${window.location.port || 8080}/api/ws`;

export const STOMP_TOPIC_PREFIX = '/topic';
export const STOMP_VIDEO_PROGRESS = 'video-progress';

// 其他全局常量
export const APP_NAME = '交通数据分析系统';
export const APP_VERSION = '1.0.0';

// API路径配置
export const API_PATHS = {
  AUTH: {
    LOGIN: '/auth/login',
    LOGOUT: '/auth/logout',
    REFRESH: '/auth/refresh',
    REGISTER: '/auth/register',
  },
  USER: {
    PROFILE: '/user/profile',
    CHANGE_PASSWORD: '/user/change-password',
  },
  VIDEO: {
    UPLOAD: '/video-analysis/upload',
    UPLOAD_INTERSECTION: '/video-analysis/upload/intersection',
    STATUS: '/video-analysis/status',
    RESULT: '/video-analysis/result',
    HISTORY: '/video-analysis/history',
  }
} 