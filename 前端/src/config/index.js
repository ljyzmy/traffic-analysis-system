/**
 * 系统配置文件
 */

// API基础URL
export const API_BASE_URL = process.env.VUE_APP_BASE_API || ''

// 图片上传路径
export const UPLOAD_IMAGE_URL = `${API_BASE_URL}/api/upload/image`

// 视频上传路径
export const UPLOAD_VIDEO_URL = `${API_BASE_URL}/api/upload/video`

// API请求超时时间 (毫秒)
export const REQUEST_TIMEOUT = 30000

// 默认分页大小
export const DEFAULT_PAGE_SIZE = 10

// 默认头像地址
export const DEFAULT_AVATAR = '/img/default-avatar.png'

// Token存储键名
export const TOKEN_KEY = 'auth_token'

// 用户信息存储键名
export const USER_INFO_KEY = 'user'

// 系统名称
export const SYSTEM_NAME = '交通分析系统' 