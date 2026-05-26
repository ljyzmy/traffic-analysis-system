import { createStore } from 'vuex';
import AuthService from '@/services/auth.service';
import apiClient from '@/utils/http-common';

export default createStore({
  state: {
    user: JSON.parse(localStorage.getItem('user')) || null,
    token: localStorage.getItem('auth_token') || null,
    loading: false,
    notification: null,
    isModelOnline: false,
    currentAnalysis: null
  },
  
  getters: {
    isLoggedIn: state => !!state.token,
    currentUser: state => state.user,
    isLoading: state => state.loading,
    notification: state => state.notification,
    isModelOnline: state => state.isModelOnline,
    currentAnalysis: state => state.currentAnalysis
  },
  
  mutations: {
    SET_USER(state, user) {
      state.user = user;
      if (user) {
        localStorage.setItem('user', JSON.stringify(user));
      } else {
        localStorage.removeItem('user');
      }
    },
    
    SET_TOKEN(state, token) {
      state.token = token;
      if (token) {
        localStorage.setItem('auth_token', token);
      } else {
        localStorage.removeItem('auth_token');
      }
    },
    
    setAuthToken(state, token) {
      state.token = token;
      // 设置到localStorage
      if (token) {
        localStorage.setItem('auth_token', token);
        // 同时设置HTTP客户端的认证头
        apiClient.defaults.headers.common['Authorization'] = `Bearer ${token}`;
      } else {
        localStorage.removeItem('auth_token');
        delete apiClient.defaults.headers.common['Authorization'];
      }
    },
    
    SET_LOADING(state, status) {
      state.loading = status;
    },
    
    SET_NOTIFICATION(state, notification) {
      state.notification = notification;
    },
    
    CLEAR_NOTIFICATION(state) {
      state.notification = null;
    },
    
    SET_MODEL_STATUS(state, status) {
      state.isModelOnline = status;
    },
    
    SET_CURRENT_ANALYSIS(state, analysisId) {
      state.currentAnalysis = analysisId;
    }
  },
  
  actions: {
    // 登录动作
    async login({ commit }, credentials) {
      commit('SET_LOADING', true);
      
      try {
        const response = await AuthService.login(credentials);
        
        if (response && response.token) {
          commit('setAuthToken', response.token); // 使用新的mutation
          commit('SET_USER', response.user);
          return { success: true };
        } else {
          return { success: false, message: '登录失败，请检查用户名和密码' };
        }
      } catch (error) {
        const message = error.response?.data?.message || '登录失败';
        commit('SET_NOTIFICATION', { type: 'error', message });
        return { success: false, message };
      } finally {
        commit('SET_LOADING', false);
      }
    },
    
    // 获取用户信息
    async fetchUserProfile({ commit, state }) {
      if (!state.token) {
        console.error('尝试获取用户信息时没有认证令牌');
        return { success: false, message: '未登录' };
      }
      
      commit('SET_LOADING', true);
      
      try {
        // 请求用户信息的API可能与你的实际API不同
        const response = await apiClient.get('/user/info');
        
        if (response.data) {
          commit('SET_USER', response.data);
          return { success: true, user: response.data };
        } else {
          return { success: false, message: '获取用户信息失败' };
        }
      } catch (error) {
        console.error('获取用户信息失败:', error);
        
        // 如果是401错误，清除认证状态
        if (error.response && error.response.status === 401) {
          commit('setAuthToken', null);
          commit('SET_USER', null);
        }
        
        const message = error.response?.data?.message || '获取用户信息失败';
        commit('SET_NOTIFICATION', { type: 'error', message });
        return { success: false, message };
      } finally {
        commit('SET_LOADING', false);
      }
    },
    
    // 注销动作
    logout({ commit }) {
      AuthService.logout().then(() => {
        commit('SET_USER', null);
        commit('setAuthToken', null); // 使用新的mutation
        
        commit('SET_NOTIFICATION', {
          type: 'success',
          message: '已成功退出'
        });
        
        // 3秒后清除通知
        setTimeout(() => {
          commit('CLEAR_NOTIFICATION');
        }, 3000);
      }).catch(error => {
        console.error('注销时出错:', error);
      });
    },
    
    // 注册动作
    async register({ commit }, userData) {
      commit('SET_LOADING', true);
      
      try {
        const response = await AuthService.register(userData);
        
        if (response.data && response.data.success) {
          commit('SET_NOTIFICATION', {
            type: 'success',
            message: '注册成功，请登录'
          });
          return { success: true };
        } else {
          return { success: false, message: '注册失败' };
        }
      } catch (error) {
        const message = error.response?.data?.message || '注册失败';
        commit('SET_NOTIFICATION', { type: 'error', message });
        return { success: false, message };
      } finally {
        commit('SET_LOADING', false);
      }
    },
    
    // 检查模型状态
    async checkModelStatus({ commit }) {
      try {
        const response = await apiClient.get('/model/status');
        const data = response.data;
        commit('SET_MODEL_STATUS', data.status === 'online');
        return data;
      } catch (error) {
        console.error('检查模型状态失败:', error);
        commit('SET_MODEL_STATUS', false);
      }
    }
  }
}); 