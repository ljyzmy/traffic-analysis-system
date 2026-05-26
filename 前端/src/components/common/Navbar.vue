<template>
  <nav class="navbar navbar-expand-lg">
    <div class="container">
      <router-link class="navbar-brand" to="/">交通分析系统</router-link>
      <button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarNav" aria-controls="navbarNav" aria-expanded="false" aria-label="Toggle navigation">
        <span class="navbar-toggler-icon"></span>
      </button>
      <div class="collapse navbar-collapse" id="navbarNav">
        <ul class="navbar-nav me-auto mb-2 mb-lg-0">
          <li class="nav-item dropdown">
            <a class="nav-link dropdown-toggle" href="#" id="analysisDropdown" role="button" @click.prevent="toggleDropdown('analysis')">
              分析功能
            </a>
            <ul class="dropdown-menu" :class="{ show: activeDropdown === 'analysis' }">
              <li>
                <router-link class="dropdown-item" to="/upload" @click="closeDropdowns">
                  <i class="bi bi-image"></i> 图像分析
                </router-link>
              </li>
              <li>
                <router-link class="dropdown-item" to="/video-upload" @click="closeDropdowns">
                  <i class="bi bi-camera-video"></i> 视频分析
                </router-link>
              </li>
            </ul>
          </li>
          <li class="nav-item dropdown">
            <a class="nav-link dropdown-toggle" href="#" id="historyDropdown" role="button" @click.prevent="toggleDropdown('history')">
              历史记录
            </a>
            <ul class="dropdown-menu" :class="{ show: activeDropdown === 'history' }">
              <li>
                <router-link class="dropdown-item" to="/history" @click="closeDropdowns">
                  <i class="bi bi-image"></i> 图像分析历史
                </router-link>
              </li>
              <li>
                <router-link class="dropdown-item" to="/video-history" @click="closeDropdowns">
                  <i class="bi bi-camera-video"></i> 视频分析历史
                </router-link>
              </li>
            </ul>
          </li>
          <li class="nav-item" v-if="user">
            <router-link class="nav-link" :class="{ active: $route.path === '/user/profile' }" to="/user/profile" @click="closeDropdowns">个人信息</router-link>
          </li>
          <li class="nav-item">
            <router-link class="nav-link" :class="{ active: $route.path === '/settings' }" to="/settings" @click="closeDropdowns">
              <i class="bi bi-gear"></i> 系统设置
            </router-link>
          </li>
          <li class="nav-item" v-if="isAdmin">
            <router-link class="nav-link" :class="{ active: $route.path === '/admin/users' }" to="/admin/users" @click="closeDropdowns">
              <i class="bi bi-people"></i> 用户管理
              <span class="admin-badge">管理员</span>
            </router-link>
          </li>
        </ul>
        <div class="d-flex align-items-center">
          <div class="d-flex" v-if="user">
            <span class="navbar-text me-3">
              欢迎, {{ user.username }}
            </span>
            <button class="logout-btn" type="button" @click="logout">退出</button>
          </div>
          <div v-else>
            <router-link class="auth-btn login-btn" to="/login">登录</router-link>
            <router-link class="auth-btn register-btn" to="/register">注册</router-link>
          </div>
        </div>
      </div>
    </div>
  </nav>
</template>

<script>
import { ref, onMounted, computed, watch, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';

export default {
  name: 'NavbarComponent',
  setup() {
    const router = useRouter();
    const user = ref(null);
    const activeDropdown = ref(null);
    
    // 计算属性：检查用户是否为管理员
    const isAdmin = computed(() => {
      if (!user.value) return false;
      
      // 兼容不同格式的管理员角色名称（大小写不敏感比较）
      const role = user.value.role?.toLowerCase() || '';
      return role === 'admin' || role === 'administrator';
    });
    
    // 切换下拉菜单显示状态
    const toggleDropdown = (dropdownId) => {
      console.log(`切换下拉菜单: ${dropdownId}, 当前状态: ${activeDropdown.value === dropdownId ? '开启' : '关闭'}`);
      
      // 如果点击当前已打开的下拉菜单，则关闭它
      if (activeDropdown.value === dropdownId) {
        activeDropdown.value = null;
        console.log(`关闭下拉菜单: ${dropdownId}`);
      } else {
        // 否则打开新的下拉菜单，并关闭其他已打开的菜单
        activeDropdown.value = dropdownId;
        console.log(`打开下拉菜单: ${dropdownId}`);
      }
    };
    
    // 关闭所有下拉菜单
    const closeDropdowns = () => {
      if (activeDropdown.value) {
        console.log(`关闭活动下拉菜单: ${activeDropdown.value}`);
        activeDropdown.value = null;
      }
    };
    
    // 处理页面点击事件，点击菜单外部时关闭下拉菜单
    const handleOutsideClick = (event) => {
      // 如果没有活动的下拉菜单，无需处理
      if (!activeDropdown.value) return;
      
      // 检查点击是否发生在下拉菜单或下拉菜单触发器上
      const isClickOnDropdown = event.target.closest('.dropdown-menu');
      const isClickOnDropdownToggle = event.target.closest('.dropdown-toggle');
      
      // 如果点击不是发生在下拉菜单或其触发器上，则关闭所有下拉菜单
      if (!isClickOnDropdown && !isClickOnDropdownToggle) {
        closeDropdowns();
      }
    };
    
    onMounted(() => {
      // 读取用户信息
      const userInfo = localStorage.getItem('user');
      if (userInfo) {
        user.value = JSON.parse(userInfo);
      }
      
      // 添加点击事件监听，用于处理点击菜单外部区域
      document.addEventListener('click', handleOutsideClick);
    });
    
    // 组件卸载时清理事件
    onUnmounted(() => {
      document.removeEventListener('click', handleOutsideClick);
    });
    
    // 退出登录
    const logout = () => {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('user');
      user.value = null;
      activeDropdown.value = null; // 关闭任何打开的下拉菜单
      router.push('/login');
    };
    
    return {
      user,
      logout,
      isAdmin,
      activeDropdown,
      toggleDropdown,
      closeDropdowns
    };
  }
};
</script>

<style scoped>
.navbar {
  background-color: #111827;
  padding: 1rem 0;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.3);
  position: sticky;
  top: 0;
  z-index: 1000;
}

.navbar-brand {
  font-weight: 800;
  font-size: 1.5rem;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  text-decoration: none;
  padding: 0.5rem 0;
  transition: none;
}

.navbar-brand:hover {
  transform: none;
  opacity: 1;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.navbar-nav {
  margin-left: 2rem;
}

.nav-item {
  margin: 0 0.5rem;
  position: relative;
}

.nav-link {
  color: #d1d5db;
  font-weight: 500;
  padding: 0.5rem 1rem;
  border-radius: 0.5rem;
  transition: none;
  text-decoration: none;
}

.nav-link:hover, 
.nav-link.active {
  color: #d1d5db;
  background-color: transparent;
}

.nav-link i {
  margin-right: 0.5rem;
  color: #3b82f6;
}

.dropdown-toggle {
  cursor: pointer;
  display: flex;
  align-items: center;
}

.dropdown-toggle::after {
  margin-left: 0.5rem;
  border-top: 0.3em solid #3b82f6;
}

.dropdown-menu {
  display: none;
  position: absolute;
  background-color: #1a2032;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 0.75rem;
  min-width: 10rem;
  padding: 0.5rem 0;
  margin-top: 0.5rem;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  z-index: 1000;
  overflow: hidden;
  animation: dropdown-appear 0.2s ease;
}

@keyframes dropdown-appear {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dropdown-menu.show {
  display: block;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0.75rem 1.25rem;
  color: #d1d5db;
  text-decoration: none;
  transition: none;
  height: 36px;
  line-height: normal;
}

.dropdown-item:hover {
  background-color: transparent;
  color: #d1d5db;
}

.dropdown-item i {
  color: #3b82f6;
  font-size: 1rem;
}

.navbar-text {
  color: #d1d5db;
  padding: 0.5rem 1rem;
}

.admin-badge {
  display: inline-block;
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: #111827;
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.2rem 0.5rem;
  border-radius: 1rem;
  margin-left: 0.5rem;
  text-transform: uppercase;
}

.auth-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0.5rem 1.25rem;
  border-radius: 0.75rem;
  font-weight: 600;
  transition: none;
  text-decoration: none;
  margin-left: 0.5rem;
  font-size: 0.9rem;
}

.login-btn {
  background-color: transparent;
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: #d1d5db;
}

.login-btn:hover {
  background-color: transparent;
  color: #d1d5db;
  transform: none;
}

.register-btn {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
  border: none;
}

.register-btn:hover {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  transform: none;
}

.logout-btn {
  background: rgba(255, 255, 255, 0.05);
  color: #d1d5db;
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 0.5rem 1.25rem;
  border-radius: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: none;
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #d1d5db;
  border-color: rgba(255, 255, 255, 0.1);
}

.navbar-toggler {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background-color: rgba(255, 255, 255, 0.03);
}

.navbar-toggler-icon {
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 30 30'%3e%3cpath stroke='rgba%28255, 255, 255, 0.5%29' stroke-linecap='round' stroke-miterlimit='10' stroke-width='2' d='M4 7h22M4 15h22M4 23h22'/%3e%3c/svg%3e");
}

@media (max-width: 992px) {
  .navbar-nav {
    margin-left: 0;
    margin-top: 1rem;
  }
  
  .nav-item {
    margin: 0;
  }
  
  .dropdown-menu {
    position: static;
    background-color: rgba(26, 32, 50, 0.5);
    border: none;
    box-shadow: none;
    width: 100%;
    margin-top: 0.25rem;
    margin-bottom: 0.25rem;
    padding-left: 1.5rem;
  }
  
  .auth-btn {
    margin: 0.5rem 0;
    width: 100%;
    justify-content: center;
  }
}
</style> 