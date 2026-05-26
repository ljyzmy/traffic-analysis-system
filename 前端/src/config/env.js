/**
 * 环境配置
 * 可以根据不同的环境配置不同的参数
 */

// 开发环境后端URL
const DEV_BACKEND_URL = 'http://localhost:8080';

// 生产环境后端URL
const PROD_BACKEND_URL = '';  // 使用相对路径时设置为空字符串

// 当前环境
const isProduction = process.env.NODE_ENV === 'production';

// 导出配置
export default {
  // 后端API基础URL
  baseUrl: isProduction ? PROD_BACKEND_URL : DEV_BACKEND_URL,
  
  // 视频相关配置
  video: {
    // 默认播放模式: 'direct'(直连模式) 或 'proxy'(代理模式)
    defaultPlayMode: 'proxy',
    
    // 是否在控制台显示调试信息
    debug: !isProduction,
    
    // 媒体API路径
    apiPath: '/api/media/video/'
  }
}; 