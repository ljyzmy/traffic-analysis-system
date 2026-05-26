import apiClient from '@/utils/http-common';

/**
 * 系统设置API服务
 */
const SystemService = {
  /**
   * 获取系统状态信息
   * @returns {Promise} 包含系统状态信息的Promise
   */
  getSystemStatus() {
    return apiClient.get('/system/status');
  },
  
  /**
   * 切换模型服务状态
   * @param {string} action - 'start' 或 'stop'
   * @returns {Promise} 包含操作结果的Promise
   */
  toggleModelService(action) {
    return apiClient.post(`/system/model/${action}`);
  },
  
  /**
   * 切换数据库服务状态
   * @param {string} action - 'start' 或 'stop'
   * @returns {Promise} 包含操作结果的Promise
   */
  toggleDatabaseService(action) {
    return apiClient.post(`/system/database/${action}`);
  },
  
  /**
   * 测试模型连接
   * @returns {Promise} 包含测试结果的Promise
   */
  testModelConnection() {
    return apiClient.post('/system/model/test');
  },
  
  /**
   * 测试数据库连接
   * @returns {Promise} 包含测试结果的Promise
   */
  testDatabaseConnection() {
    return apiClient.post('/system/database/test');
  },
  
  /**
   * 清除系统日志
   * @returns {Promise} 包含操作结果的Promise
   */
  clearSystemLogs() {
    return apiClient.delete('/system/logs');
  }
};

export default SystemService; 