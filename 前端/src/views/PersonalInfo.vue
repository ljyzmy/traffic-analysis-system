<template>
  <div class="personal-info-container">
    <div class="container py-4">
      <div class="row justify-content-center">
        <div class="col-md-10">
          <div class="card">
            <div class="card-header">
              <h2 class="section-title mb-0"><i class="bi bi-person-circle"></i> 个人信息</h2>
            </div>
            <div class="card-body p-0">
              <!-- 用户页签 -->
              <ul class="nav nav-tabs" id="userTabs" role="tablist">
                <li class="nav-item" role="presentation">
                  <button class="nav-link active" id="profile-tab" data-bs-toggle="tab" 
                    data-bs-target="#profile" type="button" role="tab" aria-selected="true">
                    <i class="bi bi-person"></i> 个人资料
                  </button>
                </li>
                <li class="nav-item" role="presentation">
                  <button class="nav-link" id="password-tab" data-bs-toggle="tab" 
                    data-bs-target="#password" type="button" role="tab" aria-selected="false">
                    <i class="bi bi-key"></i> 修改密码
                  </button>
                </li>
              </ul>
              
              <div class="tab-content p-4" id="userTabsContent">
                <!-- 个人资料页签 -->
                <div class="tab-pane fade show active" id="profile" role="tabpanel">
                  <div class="row mb-4">
                    <div class="col-md-3 text-center">
                      <div class="avatar-container mb-3">
                        <div class="avatar">
                          <span>{{ avatarText }}</span>
                        </div>
                      </div>
                      <p class="username-text">{{ user.username }}</p>
                    </div>
                    
                    <div class="col-md-9">
                      <form @submit.prevent="updateProfile">
                        <div class="mb-3">
                          <label for="username" class="form-label">用户名</label>
                          <input 
                            type="text" 
                            class="form-control custom-input" 
                            id="username" 
                            v-model="userForm.username" 
                            required
                          >
                        </div>
                        
                        <div class="mb-3">
                          <label for="email" class="form-label">邮箱地址</label>
                          <input 
                            type="email" 
                            class="form-control custom-input" 
                            id="email" 
                            v-model="userForm.email"
                          >
                        </div>
                        
                        <div class="mb-3">
                          <label for="phone" class="form-label">手机号码</label>
                          <input 
                            type="tel" 
                            class="form-control custom-input" 
                            id="phone" 
                            v-model="userForm.phone"
                          >
                        </div>
                        
                        <button type="submit" class="btn btn-primary custom-btn" :disabled="loading">
                          <span v-if="loading" class="spinner-border spinner-border-sm me-2"></span>
                          保存更改
                        </button>
                      </form>
                    </div>
                  </div>
                </div>
                
                <!-- 修改密码页签 -->
                <div class="tab-pane fade" id="password" role="tabpanel">
                  <form @submit.prevent="changePassword">
                    <div class="mb-3">
                      <label for="currentPassword" class="form-label">当前密码</label>
                      <input 
                        type="password" 
                        class="form-control custom-input" 
                        id="currentPassword" 
                        v-model="passwordForm.currentPassword" 
                        required
                      >
                    </div>
                    
                    <div class="mb-3">
                      <label for="newPassword" class="form-label">新密码</label>
                      <input 
                        type="password" 
                        class="form-control custom-input" 
                        id="newPassword" 
                        v-model="passwordForm.newPassword" 
                        required
                        minlength="6"
                      >
                      <div class="form-text">密码至少需要6个字符</div>
                    </div>
                    
                    <div class="mb-3">
                      <label for="confirmPassword" class="form-label">确认新密码</label>
                      <input 
                        type="password" 
                        class="form-control custom-input" 
                        id="confirmPassword" 
                        v-model="passwordForm.confirmPassword" 
                        required
                        minlength="6"
                      >
                    </div>
                    
                    <div class="alert alert-danger custom-alert" v-if="passwordForm.error">
                      {{ passwordForm.error }}
                    </div>
                    
                    <button type="submit" class="btn btn-primary custom-btn" :disabled="passwordLoading">
                      <span v-if="passwordLoading" class="spinner-border spinner-border-sm me-2"></span>
                      更新密码
                    </button>
                  </form>
                </div>
              </div>
            </div>
          </div>
          
          <!-- 账户操作卡片 -->
          <div class="card mt-4 danger-card">
            <div class="card-header danger-header">
              <h3 class="mb-0"><i class="bi bi-exclamation-triangle"></i> 账户操作</h3>
            </div>
            <div class="card-body">
              <p class="card-text">删除账户将会移除所有与您相关的数据和分析历史，此操作不可撤销。</p>
              <button class="btn btn-outline-danger custom-btn-outline" @click="showDeleteConfirm = true">
                <i class="bi bi-trash"></i> 删除我的账户
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 删除账户确认弹窗 -->
    <div class="modal fade custom-modal" id="deleteConfirmModal" tabindex="-1" ref="deleteModal">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <h5 class="modal-title">确认删除账户</h5>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
          </div>
          <div class="modal-body">
            <p>您确定要删除您的账户吗？此操作不可撤销，所有数据将被永久删除。</p>
            <div class="mb-3">
              <label for="deleteConfirmPassword" class="form-label">请输入您的密码以确认</label>
              <input 
                type="password" 
                class="form-control custom-input" 
                id="deleteConfirmPassword" 
                v-model="deleteConfirmPassword"
                required
              >
            </div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-secondary custom-btn-secondary" data-bs-dismiss="modal">取消</button>
            <button 
              type="button" 
              class="btn btn-danger custom-btn-danger" 
              @click="deleteAccount" 
              :disabled="deleteLoading"
            >
              <span v-if="deleteLoading" class="spinner-border spinner-border-sm me-2"></span>
              确认删除
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 装饰元素 -->
    <div class="decoration-elements">
      <div class="circle circle-1"></div>
      <div class="circle circle-2"></div>
      <div class="line line-1"></div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Modal } from 'bootstrap';
import { apiService } from '@/api/index';
import AuthService from '@/services/auth.service';

export default {
  name: 'PersonalInfoView',
  setup() {
    const router = useRouter();
    
    // 用户数据
    const user = ref(JSON.parse(localStorage.getItem('user')) || {});
    const userForm = ref({
      username: user.value.username || '',
      email: user.value.email || '',
      phone: user.value.phone || ''
    });
    
    // 保存原始用户名以便后续比较
    const originalUsername = ref(user.value.username || '');
    
    // 头像文本
    const avatarText = computed(() => {
      if (!user.value.username) return '';
      return user.value.username.substring(0, 2).toUpperCase();
    });
    
    // 修改密码表单
    const passwordForm = ref({
      currentPassword: '',
      newPassword: '',
      confirmPassword: '',
      error: ''
    });
    
    // 加载状态
    const loading = ref(false);
    const passwordLoading = ref(false);
    const deleteLoading = ref(false);
    
    // 删除确认
    const showDeleteConfirm = ref(false);
    const deleteConfirmPassword = ref('');
    const deleteModal = ref(null);
    
    // 监听删除确认
    watch(showDeleteConfirm, (newVal) => {
      if (newVal && deleteModal.value) {
        new Modal(deleteModal.value).show();
      }
    });
    
    // 挂载时初始化
    onMounted(() => {
      // 检查用户是否已登录
      if (!AuthService.isAuthenticated()) {
        router.push('/login');
        return;
      }
      
      // 初始化删除模态框
      deleteModal.value = document.getElementById('deleteConfirmModal');
      
      // 加载用户信息
      loadUserInfo();
    });
    
    // 加载用户信息
    const loadUserInfo = async () => {
      loading.value = true;
      try {
        // 使用 apiService 获取用户信息
        const response = await apiService.getUserInfo();
        
        if (response.status === 200) {
          const userData = response.data?.data || response.data || {};
          user.value = userData;
          
          // 更新表单数据
          userForm.value = {
            username: userData.username || user.value.username || '',
            email: userData.email || user.value.email || '',
            phone: userData.phone || user.value.phone || ''
          };
          
          // 保存原始用户名
          originalUsername.value = userForm.value.username;
          
          // 更新本地存储中的用户信息
          localStorage.setItem('user', JSON.stringify(userData));
        } else {
          // 如果API请求失败，至少确保显示localStorage中的用户信息
          userForm.value = {
            username: user.value.username || '',
            email: user.value.email || '',
            phone: user.value.phone || ''
          };
        }
      } catch (error) {
        console.error('获取用户信息失败', error);
        // 发生错误时，确保仍然显示localStorage中的用户信息
        userForm.value = {
          username: user.value.username || '',
          email: user.value.email || '',
          phone: user.value.phone || ''
        };
      } finally {
        loading.value = false;
      }
    };
    
    // 更新个人资料
    const updateProfile = async () => {
      loading.value = true;
      console.log('发送更新请求:', userForm.value);
      try {
        const response = await apiService.updateUserInfo(userForm.value);
        
        if (response.status === 200) {
          console.log('更新响应:', response);
          const updatedUser = response.data?.data || response.data || {};
          // 更新前后端数据比对
          console.log('原始用户数据:', user.value);
          console.log('更新后用户数据:', updatedUser);
          
          // 确认是否用户名已经更新
          if (updatedUser.username !== userForm.value.username) {
            alert('用户名未能更新，可能后端不支持用户名修改。请联系管理员。');
          } else {
            user.value = { ...user.value, ...updatedUser };
            // 更新本地存储
            localStorage.setItem('user', JSON.stringify(user.value));
            // 如果用户名变更，需要更新认证信息
            if (userForm.value.username !== originalUsername.value) {
              alert('用户名已更新，请重新登录以应用更改');
              AuthService.logout();
              router.push('/login');
              return;
            }
            alert('个人资料更新成功');
          }
        } else {
          alert('更新失败，请重试');
        }
      } catch (error) {
        console.error('更新个人资料失败', error);
        try {
          if (error.response && error.response.data) {
            alert(`更新失败: ${error.response.data.message || '服务器错误'}`);
          } else {
            alert('更新失败，请稍后重试');
          }
        } catch {
          alert('更新失败，请稍后重试');
        }
      } finally {
        loading.value = false;
      }
    };
    
    // 修改密码
    const changePassword = async () => {
      // 验证两次输入的密码是否一致
      if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
        passwordForm.value.error = '两次输入的新密码不一致';
        return;
      }

      // 验证新密码长度
      if (passwordForm.value.newPassword.length < 6) {
        passwordForm.value.error = '新密码长度至少需要6个字符';
        return;
      }

      // 验证新密码不能与原密码相同
      if (passwordForm.value.newPassword === passwordForm.value.currentPassword) {
        passwordForm.value.error = '新密码不能与当前密码相同';
        return;
      }
      
      passwordLoading.value = true;
      passwordForm.value.error = '';
      
      try {
        console.log('发送修改密码请求:', {
          currentPassword: passwordForm.value.currentPassword,
          newPassword: passwordForm.value.newPassword,
          confirmPassword: passwordForm.value.confirmPassword
        });

        const response = await apiService.changePassword({
          currentPassword: passwordForm.value.currentPassword,
          newPassword: passwordForm.value.newPassword,
          confirmPassword: passwordForm.value.confirmPassword
        });
        
        console.log('修改密码响应:', response);
        
        if (response.status === 200) {
          alert('密码修改成功，请重新登录');
          AuthService.logout();
          router.push('/login');
        }
      } catch (error) {
        console.error('修改密码失败', error);
        if (error.response) {
          // 处理具体的错误情况
          const errorMessage = error.response.data?.message || '未知错误';
          console.error('服务器返回错误:', errorMessage);
          passwordForm.value.error = errorMessage;
        } else if (error.request) {
          console.error('请求未收到响应');
          passwordForm.value.error = '无法连接到服务器，请检查网络连接';
        } else {
          console.error('请求配置错误:', error.message);
          passwordForm.value.error = '请求配置错误，请稍后重试';
        }
      } finally {
        passwordLoading.value = false;
        // 清空密码表单
        passwordForm.value.currentPassword = '';
        passwordForm.value.newPassword = '';
        passwordForm.value.confirmPassword = '';
      }
    };
    
    // 删除账户
    const deleteAccount = async () => {
      if (!deleteConfirmPassword.value) {
        alert('请输入密码以确认删除');
        return;
      }
      
      deleteLoading.value = true;
      try {
        // 使用 apiService 删除账户
        const response = await apiService.deleteAccount(deleteConfirmPassword.value);
        
        // 先隐藏模态框，避免DOM操作冲突
        if (deleteModal.value) {
          Modal.getInstance(deleteModal.value).hide();
        }
        
        // 确认响应成功后再继续操作
        if (response.status === 200) {
          // 延迟执行注销和跳转，确保请求完全完成
          setTimeout(() => {
            alert('您的账户已被删除');
            AuthService.logout(); // 先注销再跳转
            router.push('/register');
          }, 300);
        } else {
          alert(response.data?.message || '删除账户失败，请检查密码是否正确');
        }
      } catch (error) {
        console.error('删除账户失败', error);
        // 5. 提供更具体的错误信息
        if (error.response) {
          alert(`删除失败: ${error.response.data?.message || `服务器返回状态码 ${error.response.status}`}`);
        } else if (error.request) {
          alert('服务器未响应，请检查网络连接');
        } else {
          alert('请求配置错误，请稍后重试');
        }
      } finally {
        deleteLoading.value = false;
        deleteConfirmPassword.value = '';
      }
    };
    
    return {
      user,
      userForm,
      avatarText,
      passwordForm,
      loading,
      passwordLoading,
      deleteLoading,
      showDeleteConfirm,
      deleteConfirmPassword,
      deleteModal,
      updateProfile,
      changePassword,
      deleteAccount
    };
  }
};
</script>

<style scoped>
.personal-info-container {
  min-height: 100vh;
  background-color: #111827;
  color: #e5e7eb;
  padding: 2rem 0;
  position: relative;
  overflow: hidden;
}

/* 装饰元素 */
.decoration-elements {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 0;
  pointer-events: none;
}

.circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
}

.circle-1 {
  width: 300px;
  height: 300px;
  background: radial-gradient(#3b82f6, transparent);
  top: 10%;
  right: 5%;
  animation: float 8s ease-in-out infinite;
}

.circle-2 {
  width: 200px;
  height: 200px;
  background: radial-gradient(#8b5cf6, transparent);
  bottom: 15%;
  left: 10%;
  animation: float 6s ease-in-out infinite;
  animation-delay: 1s;
}

.line {
  position: absolute;
  background: rgba(255, 255, 255, 0.05);
}

.line-1 {
  width: 100%;
  height: 1px;
  top: 35%;
  transform: rotate(-5deg);
  animation: lineGlow 4s infinite alternate;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-20px); }
}

@keyframes lineGlow {
  0% { opacity: 0.05; }
  100% { opacity: 0.2; }
}

.container {
  position: relative;
  z-index: 1;
}

.card {
  background: rgba(26, 32, 50, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 1rem;
  overflow: hidden;
  backdrop-filter: blur(5px);
  transition: all 0.3s ease;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.card-header {
  background: rgba(17, 24, 39, 0.7);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  padding: 1.5rem 2rem;
}

.danger-header {
  background: rgba(239, 68, 68, 0.2);
  border-color: rgba(239, 68, 68, 0.3);
}

.danger-header h3 {
  color: #ffffff;
  font-weight: 600;
}

.danger-header i {
  color: #ef4444;
}

.section-title {
  font-weight: 700;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.card-body {
  background: rgba(26, 32, 50, 0.5);
}

.nav-tabs {
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background-color: rgba(17, 24, 39, 0.5);
}

.nav-link {
  color: #9ca3af;
  border: none;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  padding: 1rem 1.5rem;
  transition: all 0.3s ease;
  font-weight: 500;
}

.nav-link:hover {
  color: #e5e7eb;
  background-color: rgba(255, 255, 255, 0.05);
}

.nav-link.active {
  color: #3b82f6;
  border-bottom: 2px solid #3b82f6;
  background-color: rgba(59, 130, 246, 0.05);
  font-weight: 600;
}

.avatar-container {
  display: flex;
  justify-content: center;
}

.avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2.5rem;
  font-weight: bold;
  box-shadow: 0 10px 25px -5px rgba(59, 130, 246, 0.5);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.avatar:hover {
  transform: translateY(-5px);
  box-shadow: 0 15px 30px -5px rgba(59, 130, 246, 0.6);
}

.username-text {
  font-size: 1.2rem;
  font-weight: 500;
  color: #e5e7eb;
  margin-top: 1rem;
}

.form-label {
  color: #e5e7eb;
  font-weight: 500;
  margin-bottom: 0.5rem;
}

.form-text {
  color: #9ca3af;
  font-size: 0.875rem;
  margin-top: 0.5rem;
}

.custom-input {
  background: rgba(17, 24, 39, 0.6);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #e5e7eb;
  border-radius: 0.5rem;
  padding: 0.75rem 1rem;
  transition: all 0.3s ease;
}

.custom-input:focus {
  background: rgba(17, 24, 39, 0.8);
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.25);
}

.custom-input::placeholder {
  color: #6b7280;
}

.custom-btn {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border: none;
  border-radius: 0.75rem;
  font-weight: 600;
  padding: 0.75rem 1.5rem;
  display: inline-flex;
  align-items: center;
  transition: all 0.3s ease;
  box-shadow: 0 4px 6px -1px rgba(59, 130, 246, 0.2);
}

.custom-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  box-shadow: 0 6px 10px -1px rgba(59, 130, 246, 0.3);
}

.custom-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.custom-btn-outline {
  background: rgba(239, 68, 68, 0.1);
  border: 2px solid #ef4444;
  color: #ffffff;
  border-radius: 0.75rem;
  padding: 0.75rem 1.5rem;
  font-weight: 600;
  transition: all 0.3s ease;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2);
}

.custom-btn-outline:hover {
  background: rgba(239, 68, 68, 0.25);
  border-color: #ef4444;
  color: #ffffff;
  transform: translateY(-2px);
  box-shadow: 0 4px 8px -1px rgba(239, 68, 68, 0.4);
}

.custom-btn-outline i {
  color: #ef4444;
  margin-right: 6px;
}

.custom-btn-secondary {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #e5e7eb;
}

.custom-btn-secondary:hover {
  background: rgba(255, 255, 255, 0.15);
}

.custom-btn-danger {
  background: linear-gradient(135deg, #ef4444, #dc2626);
  border: none;
}

.custom-btn-danger:hover:not(:disabled) {
  background: linear-gradient(135deg, #dc2626, #b91c1c);
}

.danger-card .card-body {
  background: rgba(26, 32, 50, 0.7);
}

.danger-card .card-text {
  color: #f1f5f9;
  font-weight: 500;
}

.custom-alert {
  background: rgba(239, 68, 68, 0.1);
  border-left: 4px solid #ef4444;
  color: #ef4444;
}

/* Modal styling */
.custom-modal .modal-content {
  background: rgba(26, 32, 50, 0.95);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 1rem;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.custom-modal .modal-header {
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(17, 24, 39, 0.7);
}

.custom-modal .modal-title {
  color: #e5e7eb;
  font-weight: 600;
}

.custom-modal .modal-footer {
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(17, 24, 39, 0.5);
}

.custom-modal .modal-body {
  color: #d1d5db;
}

.custom-modal .btn-close {
  color: #e5e7eb;
  opacity: 0.7;
  transition: opacity 0.3s ease;
}

.custom-modal .btn-close:hover {
  opacity: 1;
}

/* Responsive styles */
@media (max-width: 768px) {
  .personal-info-container {
    padding: 1rem 0;
  }
  
  .card-header {
    padding: 1rem;
  }
  
  .avatar {
    width: 100px;
    height: 100px;
    font-size: 2rem;
  }
  
  .section-title {
    font-size: 1.5rem;
  }
  
  .nav-link {
    padding: 0.75rem 1rem;
    font-size: 0.9rem;
  }
  
  .custom-btn,
  .custom-btn-outline {
    width: 100%;
    justify-content: center;
    margin-top: 1rem;
  }
  
  .circle-1,
  .circle-2 {
    transform: scale(0.7);
  }
}
</style> 