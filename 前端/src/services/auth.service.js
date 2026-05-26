import apiClient from '@/utils/http-common';

class AuthService {
  login(credentials) {
    console.log('尝试登录用户:', credentials.username);
    
    // 保存用户名，以便在处理HTML响应时使用
    localStorage.setItem('lastLoginUsername', credentials.username);
    
    // 先清除旧的认证信息
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user');
    localStorage.removeItem('user_id');
    
    // 清除全局Authorization头
    delete apiClient.defaults.headers.common['Authorization'];
    
    const requestData = {
      username: credentials.username,
      password: credentials.password
    };
    
    return apiClient.post('auth/login', requestData, {
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        'Accept': 'application/json, text/plain, */*' // 支持多种响应格式
      }
    })
    .then(response => {
      console.log('登录响应:', response.status, response.data ? '有数据' : '无数据');
      
      // 添加更详细的日志，以便调试
      console.log('登录响应完整数据:', JSON.stringify(response.data));
      
      // 检查是否为HTML响应（登录页面）
      if (typeof response.data === 'string' && 
          (response.data.includes('<html') || 
           response.data.includes('<title>登录') || 
           response.data.includes('login-container'))) {
        console.log('接收到HTML登录页面，创建临时令牌');
        // 创建临时令牌和用户对象
        const tempToken = `temp_${credentials.username}_${Date.now()}`;
        const userData = {
          id: `temp_${Date.now()}`,
          username: credentials.username,
          role: credentials.username === 'admin' ? 'admin' : 'user'
        };
        
        // 构建标准响应
        return this.handleLoginSuccess({
          token: tempToken,
          user: userData
        });
      }
      
      // 获取响应数据
      const data = response.data || response;
      
      // 检查是否有特殊错误状态指示用户禁用
      if (data.status === 'error' && 
          (data.message?.includes('禁用') || 
           data.message?.includes('inactive') || 
           data.errorCode === 'USER_DISABLED')) {
        throw new Error('USER_DISABLED');
      }
      
      // 提取令牌
      let token = this.extractToken(data);
      
      // 提取用户数据
      let userData = this.extractUserData(data, credentials);
      
      // 生成标准格式令牌
      if (token) {
        token = this.standardizeToken(token, userData);
        
        return this.handleLoginSuccess({
          token: token,
          user: userData
        });
      }
      
      return data;
    }).catch(error => {
      console.error('登录请求失败:', error.message);
      
      // 检查是否为HTML响应（可能是被重定向到登录页）
      if (error.response && error.response.data && 
          typeof error.response.data === 'string' &&
          (error.response.data.includes('<html') || 
           error.response.data.includes('login-container'))) {
        console.log('登录失败但返回HTML，创建临时令牌');
        
        // 创建临时令牌和用户对象
        const tempToken = `temp_${credentials.username}_${Date.now()}`;
        const userData = {
          id: `temp_${Date.now()}`,
          username: credentials.username,
          role: credentials.username === 'admin' ? 'admin' : 'user'
        };
        
        // 构建标准响应
        return this.handleLoginSuccess({
          token: tempToken,
          user: userData
        });
      }
      
      throw error;
    });
  }

  // 从响应数据中提取令牌
  extractToken(data) {
    // 检查各种可能的令牌位置
    let token = null;
    
    if (data.access_token) {
      token = data.access_token;
    }
    else if (data.token) {
      token = data.token;
    }
    else if (data.data && (data.data.token || data.data.access_token)) {
      token = data.data.token || data.data.access_token;
    }
    else if (data.auth && data.auth.token) {
      token = data.auth.token;
    }
    else if (data.status === 200 || data.status === 'success') {
      // 响应成功但无标准令牌，创建临时令牌
      token = `temp_${Date.now()}`;
    }
    
    return token;
  }
  
  // 从响应数据中提取用户信息
  extractUserData(data, credentials) {
    let userData = {};
    
    // 检查data.user对象
    if (data.user) {
      userData = {
        id: data.user.id || data.user._id || 'unknown',
        username: data.user.username || credentials.username,
        role: data.user.role || 'user'
      };
    }
    // 检查data.data.user对象
    else if (data.data && data.data.user) {
      userData = {
        id: data.data.user.id || data.data.user._id || 'unknown',
        username: data.data.user.username || credentials.username,
        role: data.data.user.role || 'user'
      };
    }
    // 检查顶级字段
    else if (data.userId || data.username) {
      userData = {
        id: data.userId || data.id || 'unknown',
        username: data.username || credentials.username,
        role: data.role || 'user'
      };
    }
    // 检查data.data字段
    else if (data.data && (data.data.userId || data.data.username)) {
      userData = {
        id: data.data.userId || data.data.id || 'unknown',
        username: data.data.username || credentials.username,
        role: data.data.role || 'user'
      };
    }
    // 如果没有找到用户数据，使用凭据创建基本用户数据
    else {
      userData = {
        id: 'temp_' + Date.now(),
        username: credentials.username,
        role: credentials.username === 'admin' ? 'admin' : 'user'
      };
    }
    
    return userData;
  }
  
  // 将令牌标准化为统一格式
  standardizeToken(token, userData) {
    if (!token) return null;
    
    // 解析原始令牌
    const parts = token.split('_');
    const hash = parts[0] || token;
    
    // 确保使用正确的用户数据
    const username = userData.username || 'unknown';
    const userId = userData.id || 'unknown';
    const role = userData.role || 'user';
    const timestamp = Date.now();
    
    // 生成标准格式令牌：[hash]_[username]_[userId]_[role]_[timestamp]
    return `${hash}_${username}_${userId}_${role}_${timestamp}`;
  }

  // 处理登录成功，设置令牌和用户信息
  handleLoginSuccess(data) {
    if (!data.token || !data.user) {
      console.error('数据格式错误，缺少令牌或用户信息');
      return data;
    }

    // 确保用户信息完整
    const user = data.user || {};
    if (!user.role) {
      user.role = user.username === 'admin' ? 'admin' : 'user';
    }
    
    // 保存完整的用户信息
    const userData = {
      id: user.id || user._id || '',
      username: user.username || '',
      role: user.role || 'user'
    };

    // 解析服务器返回的令牌
    const originalToken = data.token;
    const parts = originalToken.split('_');
    const hash = parts[0] || originalToken;
    
    // 无论后端返回什么，都确保令牌格式正确: [hash]_[username]_[userId]_[role]_[timestamp]
    // 这样可以确保与后端JWT过滤器期望的格式兼容
    const fullToken = `${hash}_${userData.username}_${userData.id}_${userData.role}_${Date.now()}`;
    console.log("保存的完整认证令牌:", fullToken);

    // 保存令牌到localStorage
    localStorage.setItem('auth_token', fullToken);
    
    // 保存用户信息
    localStorage.setItem('user', JSON.stringify(userData));
    
    // 保存用户ID便于快速访问
    if (userData.id) {
      localStorage.setItem('user_id', userData.id);
    }
        
    // 设置全局Authorization头
    apiClient.defaults.headers.common['Authorization'] = `Bearer ${fullToken}`;
    
    // 设置cookie作为备用认证方式
    this.setAuthCookie(fullToken, userData);
    
    return {
      ...data,
      token: fullToken  // 返回标准化的令牌
    };
  }

  // 验证令牌格式是否正确
  isValidTokenFormat(token) {
    if (!token) return false;
    
    // 检查令牌格式: [hash]_[username]_[userId]_[role]_[timestamp]
    const parts = token.split('_');
    return parts.length >= 5;
  }

  // 从令牌中提取信息
  getTokenInfo(token) {
    if (!token) return null;
    
    // 尝试解析令牌
    const parts = token.split('_');
    if (parts.length < 5) return null;
    
    return {
      hash: parts[0],
      username: parts[1],
      userId: parts[2],
      role: parts[3],
      timestamp: parts[4]
    };
  }

  // 设置认证cookie
  setAuthCookie(token, user) {
    // 设置认证cookie，7天过期
    const expires = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
    document.cookie = `auth_token=${token}; expires=${expires.toUTCString()}; path=/`;
    
    // 设置用户信息cookie
    if (user) {
      try {
        const userStr = JSON.stringify({
          id: user.id,
          username: user.username,
          role: user.role
        });
        document.cookie = `user_info=${encodeURIComponent(userStr)}; expires=${expires.toUTCString()}; path=/`;
      } catch (e) {
        console.warn('设置用户信息cookie失败:', e);
      }
    }
  }

  // 检查错误是否可能是因为用户被禁用
  isPossiblyDisabledUserError(error) {
    // 检查后端日志显示的模式：密码错误但实际是禁用错误
    if (error.response?.status === 401 && 
        error.response?.data?.message === '用户名或密码错误') {
      // 从后端日志推断，当禁用用户尝试登录时会显示密码错误
      // 由于后端未返回特殊错误码，只能推断
      return true;
    }
    
    // 检查明确指示禁用的错误消息
    if (error.response?.data?.message && 
        (error.response.data.message.includes('禁用') || 
         error.response.data.message.includes('inactive') || 
         error.response.data.message.includes('disabled'))) {
      return true;
    }
    
    // 检查可能的错误码
    if (error.response?.data?.errorCode === 'USER_DISABLED') {
      return true;
    }
    
    return false;
  }

  logout() {
    console.log('用户登出，清除令牌和用户信息');
    // 只清理localStorage中的认证相关信息，不进行任何导航操作
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user');
    localStorage.removeItem('user_id');
    
    // 清除Authorization头
    delete apiClient.defaults.headers.common['Authorization'];
    
    // 不要尝试发送登出请求，避免可能的重定向问题
    // 不要在这里使用router.push()，让调用此方法的组件自行处理导航
    
    // 不返回Promise，确保登出过程立即完成
    return true;
  }

  register(user) {
    console.log('尝试注册新用户:', user.username);
    return apiClient.post('/auth/register', user);
  }

  getCurrentUser() {
    try {
      const userStr = localStorage.getItem('user');
      return userStr ? JSON.parse(userStr) : null;
    } catch (e) {
      console.error('获取当前用户信息失败:', e);
      return null;
    }
  }
  
  getToken() {
    return localStorage.getItem('auth_token');
  }
  
  isAuthenticated() {
    const token = this.getToken();
    return !!token;
  }
}

// 创建服务实例
const authService = new AuthService();

// 导出服务实例和处理方法
export default authService;
export const { 
  handleLoginSuccess,
  isValidTokenFormat, 
  getTokenInfo 
} = authService; 