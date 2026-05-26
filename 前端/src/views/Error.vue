<template>
  <div class="error-container">
    <div class="error-card">
      <div class="error-icon">
        <i class="bi bi-exclamation-triangle"></i>
      </div>
      <h1 class="error-title">页面加载失败</h1>
      <p class="error-message">{{ errorMessage }}</p>
      <div class="error-actions">
        <button class="btn btn-primary" @click="goBack">
          <i class="bi bi-arrow-left"></i> 返回上一页
        </button>
        <button class="btn btn-secondary" @click="goHome">
          <i class="bi bi-house"></i> 返回首页
        </button>
        <button class="btn btn-outline" @click="reload">
          <i class="bi bi-arrow-clockwise"></i> 刷新页面
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

export default {
  name: 'ErrorView',
  setup() {
    const route = useRoute();
    const router = useRouter();

    // 错误信息
    const errorMessage = computed(() => {
      if (route.params.errorCode === '404') {
        return '请求的页面不存在';
      }
      if (route.query.errorType === 'chunk') {
        return '组件加载失败，可能是由于网络问题或缓存错误';
      }
      return '发生未知错误，请尝试刷新页面或返回首页';
    });

    // 返回上一页
    const goBack = () => {
      router.go(-1);
    };

    // 返回首页
    const goHome = () => {
      router.push('/home');
    };

    // 刷新页面
    const reload = () => {
      window.location.reload();
    };

    return {
      errorMessage,
      goBack,
      goHome,
      reload
    };
  }
};
</script>

<style scoped>
.error-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  background: #111827;
  color: #e5e7eb;
}

.error-card {
  background: rgba(31, 41, 55, 0.7);
  backdrop-filter: blur(10px);
  border-radius: 1rem;
  padding: 3rem;
  max-width: 600px;
  width: 100%;
  text-align: center;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.error-icon {
  font-size: 4rem;
  color: #f59e0b;
  margin-bottom: 1.5rem;
}

.error-title {
  font-size: 2.5rem;
  font-weight: 700;
  margin-bottom: 1rem;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.error-message {
  font-size: 1.2rem;
  color: #9ca3af;
  margin-bottom: 2rem;
}

.error-actions {
  display: flex;
  justify-content: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.75rem 1.5rem;
  border-radius: 0.5rem;
  font-weight: 600;
  transition: all 0.3s ease;
  gap: 0.5rem;
  cursor: pointer;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
}

.btn-secondary {
  background: linear-gradient(135deg, #8b5cf6, #7c3aed);
  color: white;
}

.btn-outline {
  background: rgba(255, 255, 255, 0.05);
  color: #e5e7eb;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}

@media (max-width: 640px) {
  .error-card {
    padding: 2rem;
    margin: 0 1rem;
  }
  
  .error-title {
    font-size: 2rem;
  }
  
  .error-actions {
    flex-direction: column;
  }
}
</style> 