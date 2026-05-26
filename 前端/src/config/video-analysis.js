/**
 * 视频分析配置文件
 */

// 视频分析类型
export const VIDEO_ANALYSIS_TYPES = {
  TRAFFIC_FLOW: 'traffic_flow',
  VIOLATION_DETECTION: 'violation_detection',
  ACCIDENT_DETECTION: 'accident_detection'
};

// 视频分析类型文本映射
export const VIDEO_ANALYSIS_TYPE_TEXT = {
  [VIDEO_ANALYSIS_TYPES.TRAFFIC_FLOW]: '交通流量分析',
  [VIDEO_ANALYSIS_TYPES.VIOLATION_DETECTION]: '车辆违规检测',
  [VIDEO_ANALYSIS_TYPES.ACCIDENT_DETECTION]: '交通事故识别'
};

// 视频处理状态
export const VIDEO_TASK_STATUS = {
  QUEUED: 'queued',
  PROCESSING: 'processing',
  COMPLETED: 'completed',
  FAILED: 'failed'
};

// 视频处理状态文本映射
export const VIDEO_TASK_STATUS_TEXT = {
  [VIDEO_TASK_STATUS.QUEUED]: '排队中',
  [VIDEO_TASK_STATUS.PROCESSING]: '处理中',
  [VIDEO_TASK_STATUS.COMPLETED]: '已完成',
  [VIDEO_TASK_STATUS.FAILED]: '失败'
};

// 车辆类型
export const VEHICLE_TYPES = {
  CAR: 'car',
  BUS: 'bus',
  TRUCK: 'truck',
  MOTORCYCLE: 'motorcycle',
  BICYCLE: 'bicycle',
  PEDESTRIAN: 'pedestrian'
};

// 车辆类型文本映射
export const VEHICLE_TYPE_TEXT = {
  [VEHICLE_TYPES.CAR]: '小型汽车',
  [VEHICLE_TYPES.BUS]: '公交车',
  [VEHICLE_TYPES.TRUCK]: '卡车',
  [VEHICLE_TYPES.MOTORCYCLE]: '摩托车',
  [VEHICLE_TYPES.BICYCLE]: '自行车',
  [VEHICLE_TYPES.PEDESTRIAN]: '行人'
};

// 违规类型
export const VIOLATION_TYPES = {
  SPEEDING: 'speeding',
  WRONG_DIRECTION: 'wrong_direction',
  RUNNING_RED_LIGHT: 'running_red_light',
  ILLEGAL_PARKING: 'illegal_parking',
  ILLEGAL_TURN: 'illegal_turn',
  NO_SAFETY_BELT: 'no_safety_belt'
};

// 违规类型文本映射
export const VIOLATION_TYPE_TEXT = {
  [VIOLATION_TYPES.SPEEDING]: '超速行驶',
  [VIOLATION_TYPES.WRONG_DIRECTION]: '逆向行驶',
  [VIOLATION_TYPES.RUNNING_RED_LIGHT]: '闯红灯',
  [VIOLATION_TYPES.ILLEGAL_PARKING]: '违规停车',
  [VIOLATION_TYPES.ILLEGAL_TURN]: '违规转弯',
  [VIOLATION_TYPES.NO_SAFETY_BELT]: '未系安全带'
};

// 视频上传配置
export const VIDEO_UPLOAD_CONFIG = {
  MAX_SIZE: 500 * 1024 * 1024, // 500MB
  ALLOWED_TYPES: ['video/mp4', 'video/avi', 'video/quicktime', 'video/x-msvideo'],
  ALLOWED_EXTENSIONS: ['mp4', 'avi', 'mov']
};

// 视频分析API路径
export const VIDEO_API_PATHS = {
  UPLOAD: '/api/video/analyze',
  TASK_STATUS: '/api/video/task/:taskId/status',
  RESULT: '/api/video/result/:resultId',
  TASKS: '/api/video/tasks',
  RETRY: '/api/video/task/:taskId/retry',
  EXPORT: '/api/video/result/:resultId/export'
}; 