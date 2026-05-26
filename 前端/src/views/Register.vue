<template>
  <div class="register-page">
    <div class="register-card">
      <h1 class="title">交通分析系统</h1>
      <p class="subtitle">创建您的账号</p>
      
      <form @submit.prevent="handleRegister">
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
          <label for="email" class="form-label">邮箱地址</label>
          <input 
            type="email" 
            id="email" 
            v-model="email" 
            class="form-control" 
            required
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
            minlength="6"
          >
          <div class="form-text">密码至少包含6个字符</div>
        </div>
        
        <div class="form-group">
          <label for="confirmPassword">确认密码</label>
          <input 
            type="password" 
            id="confirmPassword" 
            v-model="confirmPassword" 
            class="form-control" 
            required
          >
        </div>
        
        <button type="submit" class="btn-register" :disabled="loading || !isFormValid">
          <span v-if="loading" class="spinner"></span>
          注册
        </button>
      </form>
      
      <div class="links">
        <p>已有账号？<router-link to="/login">立即登录</router-link></p>
      </div>
      
      <div v-if="error" class="alert-error">
        {{ error }}
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { apiService } from '@/api';

export default {
  name: 'RegisterView',
  setup() {
    const router = useRouter();
    const username = ref('');
    const password = ref('');
    const confirmPassword = ref('');
    const email = ref('');
    const loading = ref(false);
    const error = ref('');

    const validateEmail = (email) => {
      const re = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      return re.test(email);
    };

    const isFormValid = computed(() => {
      return username.value.length >= 3 && 
             password.value.length >= 6 && 
             password.value === confirmPassword.value &&
             validateEmail(email.value);
    });

    const handleRegister = async () => {
      if (!isFormValid.value) {
        if (username.value.length < 3) {
          error.value = '用户名长度至少3个字符';
        } else if (password.value.length < 6) {
          error.value = '密码长度至少6个字符';
        } else if (password.value !== confirmPassword.value) {
          error.value = '两次输入的密码不一致';
        } else if (!validateEmail(email.value)) {
          error.value = '请输入有效的邮箱地址';
        }
        return;
      }

      loading.value = true;
      error.value = '';
      
      try {
        const response = await apiService.register(username.value, password.value, email.value);
        
        if (response.data && (response.data.success || response.data.status === 'success')) {
          alert('注册成功！');
          router.push('/login');
        } else {
          error.value = response.data?.message || '注册失败，请重试';
        }
      } catch (err) {
        console.error('注册错误:', err);
        if (err.response?.status === 409 || err.response?.data?.message?.includes('已存在')) {
          error.value = '该用户名已被注册，请使用其他用户名';
        } else if (err.response?.data?.message) {
          error.value = err.response.data.message;
        } else {
          error.value = '注册时发生错误，请稍后重试';
        }
      } finally {
        loading.value = false;
      }
    };

    return {
      username,
      password,
      confirmPassword,
      email,
      loading,
      error,
      isFormValid,
      handleRegister,
      validateEmail
    };
  }
};
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #111827;
  position: relative;
  overflow: hidden;
}

/* 背景装饰元素 */
.register-page::before {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  background: radial-gradient(#8b5cf6, transparent 70%);
  border-radius: 50%;
  top: -100px;
  right: -100px;
  opacity: 0.15;
  z-index: 0;
}

.register-page::after {
  content: '';
  position: absolute;
  width: 250px;
  height: 250px;
  background: radial-gradient(#3b82f6, transparent 70%);
  border-radius: 50%;
  bottom: -100px;
  left: -100px;
  opacity: 0.15;
  z-index: 0;
}

.register-card {
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

.form-text {
  margin-top: 0.25rem;
  font-size: 0.875em;
  color: #9ca3af;
}

.btn-register {
  display: block;
  width: 100%;
  padding: 0.75rem 1rem;
  font-size: 1rem;
  font-weight: 600;
  line-height: 1.5;
  color: #fff;
  background: linear-gradient(135deg, #8b5cf6, #7c3aed);
  border: none;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 1.5rem;
  position: relative;
}

.btn-register:hover {
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(124, 58, 237, 0.3);
}

.btn-register:active {
  transform: translateY(0);
}

.btn-register:disabled {
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