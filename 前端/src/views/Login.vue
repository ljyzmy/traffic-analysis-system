<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="title">交通分析系统</h1>
      <p class="subtitle">基于先进AI模型的交通图像分析</p>
      
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label for="username">用户名</label>
          <input 
            type="text" 
            id="username" 
            v-model="username" 
            class="form-control" 
            required
            autofocus
          >
        </div>
        
        <div class="form-group">
          <label for="password">密码</label>
          <input 
            type="password" 
            id="password" 
            v-model="password" 
            class="form-control" 
            required
          >
        </div>
        
        <button type="submit" class="btn-login" :disabled="loading">
          <span v-if="loading" class="spinner"></span>
          登录
        </button>
      </form>
      
      <div class="links">
        <p>还没有账号？<router-link to="/register">立即注册</router-link></p>
      </div>
      
      <div v-if="error" class="alert-error">
        {{ error }}
      </div>
    </div>
  </div>
</template>

<script>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import AuthService from '@/services/auth.service';

export default {
  name: 'LoginView',
  setup() {
    const router = useRouter();
    const username = ref('');
    const password = ref('');
    const loading = ref(false);
    const error = ref('');

    const handleLogin = async () => {
      loading.value = true;
      error.value = '';
      
      try {
        const response = await AuthService.login({
          username: username.value,
          password: password.value
        });
        
        if (response && (response.token || AuthService.isAuthenticated())) {
          console.log("保存的认证令牌:", localStorage.getItem('auth_token'));
          router.push('/home');
        } else {
          error.value = '登录失败，请检查登录信息';
        }
      } catch (err) {
        console.error('登录错误:', err);
        
        // 使用AuthService中新增的方法检查是否是禁用用户错误
        if (AuthService.isPossiblyDisabledUserError(err)) {
          error.value = '您的账户已被禁用，请联系管理员';
        } else if (err.response) {
          // 检查是否是401未授权错误，通常表示用户名或密码错误
          if (err.response.status === 401) {
            error.value = err.response.data?.message || '用户名或密码错误';
          } else if (err.response.status === 403) {
            // 403通常表示禁止访问
            error.value = '您没有访问权限，请联系管理员。';
          } else {
            // 其他错误状态码
            error.value = err.response.data?.message || '登录时发生错误，请稍后再试';
          }
        } else if (err.request) {
          // 请求已发送但未收到响应
          error.value = '无法连接到服务器，请检查网络连接';
        } else {
          // 发送请求前出错
          error.value = '登录请求失败，请稍后再试';
        }
      } finally {
        loading.value = false;
      }
    };

    return {
      username,
      password,
      loading,
      error,
      handleLogin
    };
  }
};
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #111827;
  position: relative;
  overflow: hidden;
}

/* 背景装饰元素 */
.login-page::before {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  background: radial-gradient(#3b82f6, transparent 70%);
  border-radius: 50%;
  top: -100px;
  right: -100px;
  opacity: 0.15;
  z-index: 0;
}

.login-page::after {
  content: '';
  position: absolute;
  width: 250px;
  height: 250px;
  background: radial-gradient(#8b5cf6, transparent 70%);
  border-radius: 50%;
  bottom: -100px;
  left: -100px;
  opacity: 0.15;
  z-index: 0;
}

.login-card {
  position: relative;
  z-index: 1;
  max-width: 450px;
  width: 100%;
  padding: 2.5rem;
  background-color: rgba(31, 41, 55, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 1rem;
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.3);
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.title {
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-align: center;
  margin-bottom: 0.5rem;
  font-weight: 700;
  font-size: 2.5rem;
}

.subtitle {
  text-align: center;
  color: #9ca3af;
  margin-bottom: 2rem;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  color: #e5e7eb;
  font-weight: 500;
}

.form-control {
  width: 100%;
  padding: 0.75rem 1rem;
  font-size: 1rem;
  line-height: 1.5;
  color: #e5e7eb;
  background-color: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 0.5rem;
  transition: border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
}

.form-control:focus {
  border-color: #3b82f6;
  outline: 0;
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.3);
  background-color: rgba(255, 255, 255, 0.1);
}

.btn-login {
  display: block;
  width: 100%;
  padding: 0.75rem 1rem;
  font-size: 1rem;
  font-weight: 600;
  line-height: 1.5;
  color: #fff;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
}

.btn-login:hover {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.3);
}

.btn-login:active {
  transform: translateY(0);
}

.btn-login:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.links {
  margin-top: 1.5rem;
  text-align: center;
  color: #9ca3af;
}

.links a {
  color: #60a5fa;
  text-decoration: none;
  transition: color 0.2s;
}

.links a:hover {
  color: #93c5fd;
}

.alert-error {
  margin-top: 1rem;
  padding: 0.75rem 1.25rem;
  color: #fecdd3;
  background-color: rgba(220, 38, 38, 0.2);
  border: 1px solid rgba(220, 38, 38, 0.3);
  border-radius: 0.5rem;
}

/* 加载动画 */
.spinner {
  display: inline-block;
  width: 1rem;
  height: 1rem;
  margin-right: 0.5rem;
  border-radius: 50%;
  border: 0.2em solid rgba(255, 255, 255, 0.3);
  border-left-color: #ffffff;
  animation: spin 1s infinite linear;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}
</style> 