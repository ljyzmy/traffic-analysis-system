import axios from 'axios';

// 创建API实例
const apiClient = axios.create({
  baseURL: '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json'
  },
  withCredentials: false
});

// 请求拦截器
apiClient.interceptors.request.use(
  config => {
    const token = localStorage.getItem('auth_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => {
    console.error('Request error:', error);
    return Promise.reject(error);
  }
);

// 响应拦截器
apiClient.interceptors.response.use(
  response => response,
  error => {
    console.error('Response error:', error);
    
    if (error.response) {
      switch (error.response.status) {
        case 401:
          // 清除本地存储的登录信息
          localStorage.removeItem('auth_token');
          localStorage.removeItem('user');
          
          // 如果不是登录页面，则重定向到登录页
          if (window.location.pathname !== '/login') {
            window.location.href = '/login';
          }
          break;
        case 403:
          console.error('没有权限访问该资源');
          break;
        case 404:
          console.error('请求的资源不存在');
          break;
        case 500:
          console.error('服务器内部错误');
          break;
        default:
          console.error('发生错误:', error.response.data?.message || '未知错误');
      }
    } else if (error.request) {
      console.error('没有收到响应:', error.request);
    } else {
      console.error('请求配置错误:', error.message);
    }
    
    return Promise.reject(error);
  }
);

// API服务对象
export const apiService = {
  // 设置认证token
  setAuthToken(token) {
    apiClient.defaults.headers.common['Authorization'] = `Bearer ${token}`;
  },
  
  // 清除认证token
  clearAuthToken() {
    delete apiClient.defaults.headers.common['Authorization'];
  },
  
  // 用户登录
  async login(username, password) {
    const response = await apiClient.post('/auth/login', { username, password });
    if (response.data?.token) {
      this.setAuthToken(response.data.token);
      localStorage.setItem('auth_token', response.data.token);
      localStorage.setItem('user', JSON.stringify(response.data.user));
    }
    return response;
  },
  
  // 用户注册
  register(username, password, email) {
    return apiClient.post('/auth/register', { username, password, email });
  },
  
  // 用户登出
  logout() {
    this.clearAuthToken();
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user');
  },
  
  // 上传并分析图片
  analyzeImage(formData) {
    return apiClient.post('/analysis/analyze', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
  },
  
  // 获取历史记录
  getHistory() {
    return apiClient.get('/history');
  },
  
  // 获取分析结果
  getResult(id) {
    return apiClient.get(`/result/${id}`);
  },
  
  // 删除历史记录
  deleteHistory(id) {
    return apiClient.delete(`/history/${id}`);
  },
  
  // 批量删除历史记录
  batchDeleteHistory(ids) {
    return apiClient.post('/history/batch-delete', { ids });
  },
  
  // 检查数据库状态
  checkDbStatus() {
    return apiClient.get('/history/check-db');
  },
  
  // 获取用户信息
  getUserInfo() {
    return apiClient.get('/user/info');
  },
  
  // 更新用户信息
  updateUserInfo(userData) {
    return apiClient.put('/user/info', userData);
  },
  
  // 修改用户密码
  changePassword(passwordData) {
    return apiClient.put('/user/change-password', passwordData);
  },
  
  // 删除个人账户
  deleteAccount(password) {
    return apiClient.delete('/user/delete', {
      data: { password }
    });
  },
  
  // 管理员API: 获取用户列表
  getUsers(page = 1, limit = 10, query = '') {
    let url = `/admin/users?page=${page}&limit=${limit}`;
    if (query) {
      url += `&query=${encodeURIComponent(query)}`;
    }
    return apiClient.get(url);
  },
  
  // 管理员API: 创建用户
  createUser(userData) {
    return apiClient.post('/admin/users', userData);
  },
  
  // 管理员API: 更新用户
  updateUser(userId, userData) {
    return apiClient.put(`/admin/users/${userId}`, userData);
  },
  
  // 管理员API: 重置用户密码
  resetUserPassword(userId, newPassword) {
    return apiClient.post(`/admin/users/${userId}/reset-password`, { newPassword });
  },
  
  // 管理员API: 切换用户状态
  toggleUserStatus(userId, active) {
    return apiClient.patch(`/admin/users/${userId}/status`, { active });
  },
  
  // 管理员API: 删除用户
  deleteUser(userId) {
    return apiClient.delete(`/admin/users/${userId}`);
  }
}; 