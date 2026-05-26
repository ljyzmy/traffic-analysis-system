<template>
  <div id="app">
    <navbar v-if="$route.meta.requiresAuth" />
    
    <notification 
      v-if="notification"
      :type="notification.type"
      :message="notification.message"
      @close="clearNotification"
    />
    
    <main>
      <router-view />
    </main>
    
    <app-footer />
  </div>
</template>

<script>
import { computed, onMounted } from 'vue';
import { useStore } from 'vuex';
import Navbar from '@/components/common/Navbar.vue';
import AppFooter from '@/components/common/Footer.vue';
import Notification from '@/components/common/Notification.vue';
import apiClient from '@/utils/http-common';

export default {
  name: 'App',
  components: {
    Navbar,
    AppFooter,
    Notification
  },
  setup() {
    const store = useStore();
    
    const notification = computed(() => store.state.notification);
    
    const clearNotification = () => {
      store.commit('CLEAR_NOTIFICATION');
    };
    
    onMounted(() => {
      // 恢复认证状态
      const token = localStorage.getItem('auth_token');
      if (token) {
        console.log('恢复认证状态，设置令牌');
        store.commit('setAuthToken', token);
        
        // 确保所有HTTP客户端都使用相同的令牌
        apiClient.defaults.headers.common['Authorization'] = `Bearer ${token}`;
        
        // 尝试获取用户信息（如果需要）
        try {
          store.dispatch('fetchUserProfile');
        } catch (error) {
          console.error('获取用户信息失败:', error);
        }
      }
    });
    
    return {
      notification,
      clearNotification
    };
  }
};
</script>

<style>
body {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: #2c3e50;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

main {
  flex: 1;
}
</style>
