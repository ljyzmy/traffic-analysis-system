<template>
  <div class="user-management-page">
    <div class="container">
      <el-card class="main-card">
        <template #header>
          <div class="card-header">
            <h2 class="header-title">用户管理</h2>
          </div>
        </template>
        
        <!-- 搜索和筛选 -->
        <div class="search-section">
          <div class="search-container">
            <el-input 
              v-model="searchQuery" 
              placeholder="搜索用户..." 
              class="search-input"
              clearable
              prefix-icon="el-icon-search"
            ></el-input>
            <el-button type="primary" @click="searchUsers">
              搜索
            </el-button>
          </div>
          <el-button type="primary" @click="showAddUserModal = true">
            添加用户
          </el-button>
        </div>
        
        <!-- 用户列表 -->
        <div class="table-container">
          <el-table 
            :data="users" 
            stripe 
            style="width: 100%" 
            :header-cell-style="{background:'#1a2032', color:'#e5e7eb', fontWeight: '600'}"
            :row-style="{background: 'transparent', height: '60px'}"
            :cell-style="{background:'rgba(17, 24, 39, 0.6)', color:'#d1d5db', borderBottom: '1px solid rgba(255, 255, 255, 0.05)', padding: '12px 16px'}"
            v-loading="loading"
            row-class-name="table-row"
          >
            <template #empty>
              <div class="empty-placeholder">暂无用户数据</div>
            </template>
            <el-table-column prop="id" label="ID" min-width="120" align="center"></el-table-column>
            <el-table-column prop="username" label="用户名" min-width="120" align="center"></el-table-column>
            <el-table-column prop="email" label="邮箱" min-width="180" align="center"></el-table-column>
            <el-table-column prop="phone" label="手机号码" min-width="150" align="center">
              <template #default="scope">
                {{ scope.row.phone || '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="role" label="角色" min-width="100" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.role === 'admin' ? 'danger' : 'info'" size="small" class="tag-style">
                  {{ scope.row.role === 'admin' ? '管理员' : '普通用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="注册时间" min-width="180" align="center">
              <template #default="scope">
                {{ formatDate(scope.row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="active" label="状态" min-width="80" align="center">
              <template #default="scope">
                <el-tag :type="scope.row.active ? 'success' : 'danger'" size="small" class="tag-style">
                  {{ scope.row.active ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column fixed="right" label="操作" min-width="180" align="center">
              <template #default="scope">
                <div class="action-buttons">
                  <el-button type="primary" size="small" circle @click="editUser(scope.row)">
                    <i class="bi bi-pencil"></i>
                  </el-button>
                  <el-button type="primary" size="small" circle @click="resetPassword(scope.row)">
                    <i class="bi bi-key"></i>
                  </el-button>
                  <el-button 
                    :type="scope.row.active ? 'danger' : 'primary'" 
                    size="small"
                    circle
                    @click="toggleUserStatus(scope.row)">
                    <i class="bi" :class="scope.row.active ? 'bi-person-x' : 'bi-person-check'"></i>
                  </el-button>
                  <el-button type="danger" size="small" circle @click="deleteUser(scope.row)">
                    <i class="bi bi-trash"></i>
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <!-- 分页 -->
        <div class="pagination-section">
          <span class="records-info">显示 {{ users.length }} 个用户，共 {{ totalUsers }} 个</span>
          <el-pagination
            background
            layout="prev, pager, next"
            :total="totalUsers"
            :page-size="pageSize"
            :current-page="currentPage"
            @current-change="changePage"
          ></el-pagination>
        </div>
      </el-card>
    </div>
    
    <!-- 编辑用户弹窗 -->
    <el-dialog
      v-model="showEditModal"
      :title="isNewUser ? '添加用户' : '编辑用户'"
      width="500px"
      class="dark-theme stable-dialog"
      custom-class="custom-dialog"
      :append-to-body="true"
      :destroy-on-close="true"
    >
      <el-form :model="editingUser" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="editingUser.username" autocomplete="off"></el-input>
        </el-form-item>
        
        <el-form-item label="邮箱地址">
          <el-input v-model="editingUser.email" type="email"></el-input>
        </el-form-item>
        
        <el-form-item label="手机号码">
          <el-input v-model="editingUser.phone"></el-input>
        </el-form-item>
        
        <el-form-item label="用户角色">
          <el-select v-model="editingUser.role" placeholder="选择角色" style="width: 100%">
            <el-option label="普通用户" value="user"></el-option>
            <el-option label="管理员" value="admin"></el-option>
          </el-select>
        </el-form-item>
        
        <el-form-item>
          <el-checkbox v-model="editingUser.active">账户激活</el-checkbox>
        </el-form-item>
        
        <el-form-item v-if="isNewUser" label="初始密码">
          <el-input v-model="editingUser.password" type="password" autocomplete="new-password"></el-input>
          <div class="form-hint">密码至少需要6个字符</div>
        </el-form-item>
      </el-form>
      
      <div v-if="editingError" class="error-message">{{ editingError }}</div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showEditModal = false">取消</el-button>
          <el-button type="primary" @click="saveUser" :loading="editingLoading">保存</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 重置密码弹窗 -->
    <el-dialog
      v-model="showResetModal"
      title="重置用户密码"
      width="500px"
      class="dark-theme stable-dialog"
      custom-class="custom-dialog"
      :append-to-body="true"
      :destroy-on-close="true"
    >
      <p>您正在为用户 <strong>{{ resetPasswordUser?.username }}</strong> 重置密码。</p>
      <el-form :model="resetPasswordData">
        <el-form-item label="新密码">
          <el-input v-model="resetPasswordData.newPassword" type="password" autocomplete="new-password"></el-input>
          <div class="form-hint">密码至少需要6个字符</div>
        </el-form-item>
        
        <el-form-item label="确认新密码">
          <el-input v-model="resetPasswordData.confirmPassword" type="password" autocomplete="new-password"></el-input>
        </el-form-item>
      </el-form>
      
      <div v-if="resetPasswordError" class="error-message">{{ resetPasswordError }}</div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showResetModal = false">取消</el-button>
          <el-button type="primary" @click="submitResetPassword" :loading="resetPasswordLoading">确认重置</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 删除用户确认弹窗 -->
    <el-dialog
      v-model="showDeleteModal"
      title="确认删除用户"
      width="500px"
      class="dark-theme stable-dialog"
      custom-class="custom-dialog"
      :append-to-body="true"
      :destroy-on-close="true"
    >
      <div class="warning-message">
        <i class="el-icon-warning"></i> 警告：此操作不可逆！
      </div>
      <p>您确定要删除用户 <strong>{{ deletingUser?.username }}</strong> 吗？</p>
      <p>此操作将永久删除该用户及其所有相关数据，且无法恢复。</p>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showDeleteModal = false">取消</el-button>
          <el-button type="danger" @click="confirmDeleteUser" :loading="deleteLoading">确认删除</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, computed, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { apiService } from '@/api/index';
import AuthService from '@/services/auth.service';

export default {
  name: 'UserManagementView',
  setup() {
    const router = useRouter();
    const currentUser = ref(JSON.parse(localStorage.getItem('user')) || {});
    
    // 用户列表数据
    const users = ref([]);
    const loading = ref(false);
    const searchQuery = ref('');
    const currentPage = ref(1);
    const pageSize = ref(10);
    const totalUsers = ref(0);
    const totalPages = computed(() => Math.ceil(totalUsers.value / pageSize.value));
    
    // 编辑用户相关
    const editingUser = ref({});
    const isNewUser = ref(false);
    const editingLoading = ref(false);
    const editingError = ref('');
    const showEditModal = ref(false);
    const showAddUserModal = ref(false);
    
    // 重置密码相关
    const resetPasswordUser = ref(null);
    const resetPasswordData = ref({
      newPassword: '',
      confirmPassword: ''
    });
    const resetPasswordLoading = ref(false);
    const resetPasswordError = ref('');
    const showResetModal = ref(false);
    
    // 删除用户相关
    const deletingUser = ref(null);
    const deleteLoading = ref(false);
    const showDeleteModal = ref(false);
    
    // 检查是否为管理员
    const checkAdmin = () => {
      const user = currentUser.value;
      
      console.log('当前用户信息:', user);
      console.log('用户角色:', user.role);
    
      // 兼容不同格式的管理员角色名称（大小写不敏感比较）
      const isAdmin = user && (
        user.role?.toLowerCase() === 'admin' || 
        user.role?.toLowerCase() === 'administrator' ||
        user.role?.toUpperCase() === 'ADMIN' ||
        user.role?.toUpperCase() === 'ADMINISTRATOR'
      );
      
      if (!isAdmin) {
        console.error('用户没有管理员权限');
        ElMessage.error('您没有管理员权限，无法访问此页面');
        router.push('/user/profile');
        return false;
      }
      
      return true;
    };
    
    // 处理 ResizeObserver 错误
    const fixResizeObserverErrors = () => {
      if (typeof window !== 'undefined') {
        // 防止多次添加
        window._resizeObserverErrorHandlerAdded = window._resizeObserverErrorHandlerAdded || false;
        
        if (!window._resizeObserverErrorHandlerAdded) {
          // 保存原始的 error 函数
          const originalConsoleError = console.error;
          
          // 重写 console.error 来过滤掉 ResizeObserver 错误
          console.error = function(...args) {
            // 不打印任何与 ResizeObserver 循环相关的错误
            if (args.length > 0 && 
                typeof args[0] === 'string' && 
                (args[0].includes('ResizeObserver loop') || 
                 args[0].includes('ResizeObserver loop completed with undelivered notifications'))) {
              return;
            }
            
            // 对于其他错误，正常输出
            originalConsoleError.apply(console, args);
          };
          
          // 更全面的错误处理 - 捕获 error 事件
          window.addEventListener('error', (event) => {
            if (event.message && event.message.includes('ResizeObserver loop')) {
              event.stopImmediatePropagation();
              event.preventDefault();
              return false;
            }
          }, true);
          
          window._resizeObserverErrorHandlerAdded = true;
        }
      }
    };
    
    // 初始化
    onMounted(() => {
      if (!AuthService.isAuthenticated()) {
        router.push('/login');
        return;
      }
      
      if (!checkAdmin()) {
        return;
      }
      
      // 立即修复 ResizeObserver 错误
      fixResizeObserverErrors();
      
      // 监听模态框显示
      watch(showAddUserModal, (newVal) => {
        if (newVal) {
          isNewUser.value = true;
          editingUser.value = {
            username: '',
            email: '',
            phone: '',
            role: 'user',
            active: true,
            password: ''
          };
          showEditModal.value = true;
          showAddUserModal.value = false; // 重置标志
        }
      });
      
      // 添加对话框显示监听
      watch(showEditModal, (newVal) => {
        if (newVal) {
          // 使用 nextTick 确保 DOM 更新后稳定组件尺寸
          nextTick(() => {
            setTimeout(() => {
              window.dispatchEvent(new Event('resize'));
            }, 50);
          });
        }
      });
      
      // 类似地监听其他对话框
      watch([showResetModal, showDeleteModal], (newVal) => {
        if (newVal.some(val => val)) {
          nextTick(() => {
            setTimeout(() => {
              window.dispatchEvent(new Event('resize'));
            }, 50);
          });
        }
      });
      
      // 加载用户列表
      loadUsers();
    });
    
    // 加载用户列表
    const loadUsers = async () => {
      loading.value = true;
      try {
        console.log('正在获取用户列表...');
        console.log('认证令牌:', localStorage.getItem('auth_token'));
        
        // 检查令牌格式
        const token = localStorage.getItem('auth_token');
        if (!token) {
          console.error('认证令牌不存在');
          ElMessage.error('您的登录信息已过期，请重新登录');
          AuthService.logout();
          router.push('/login');
          return;
        }
        
        // 使用认证令牌请求用户列表
        const response = await apiService.getUsers(
          currentPage.value, 
          pageSize.value, 
          searchQuery.value || undefined
        );
        
        console.log('获取用户列表响应:', response);
        console.log('响应数据结构:', JSON.stringify(response.data));
        
        if (response.data && response.data.data) {
          // 获取正确的嵌套数据结构
          const responseData = response.data.data;
          users.value = responseData.items || [];
          totalUsers.value = responseData.total || users.value.length;
        } else if (response.data) {
          // 兼容非嵌套结构
          users.value = response.data.items || [];
          totalUsers.value = response.data.total || users.value.length;
        }
      } catch (error) {
        console.error('获取用户列表失败:', error);
          
        // 提供更详细的错误信息
        if (error.response) {
          console.error('服务器响应状态码:', error.response.status);
          console.error('服务器响应数据:', error.response.data);
          
          if (error.response.status === 401 || error.response.status === 403) {
            ElMessage.error('认证失败或无权限访问，请重新登录');
            AuthService.logout();
            router.push('/login');
          } else {
            ElMessage.error(`获取用户列表失败: ${error.response.data?.message || error.response.statusText || '服务器错误'}`);
          }
        } else if (error.request) {
          console.error('请求已发送但未收到响应');
          ElMessage.error('无法连接到服务器，请检查网络连接');
        } else {
          console.error('请求配置错误:', error.message);
          ElMessage.error('请求发送失败: ' + error.message);
        }
      } finally {
        loading.value = false;
      }
    };
    
    // 搜索用户
    const searchUsers = () => {
      currentPage.value = 1; // 重置到第一页
      loadUsers();
    };
    
    // 改变页码
    const changePage = (page) => {
      if (page < 1 || page > totalPages.value) return;
      currentPage.value = page;
      loadUsers();
    };
    
    // 格式化日期
    const formatDate = (dateString) => {
      if (!dateString) return '-';
      const date = new Date(dateString);
      return date.toLocaleDateString('zh-CN') + ' ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    };
    
    // 编辑用户
    const editUser = (user) => {
      isNewUser.value = false;
      editingUser.value = { ...user };
      editingError.value = '';
      showEditModal.value = true;
    };
    
    // 保存用户
    const saveUser = async () => {
      // 表单验证
      if (!editingUser.value.username || !editingUser.value.email) {
        editingError.value = '用户名和邮箱是必填项';
        return;
      }
      
      if (isNewUser.value && (!editingUser.value.password || editingUser.value.password.length < 6)) {
        editingError.value = '请设置至少6位字符的密码';
        return;
      }
      
      editingLoading.value = true;
      editingError.value = '';
      
      try {
        let response;
        if (isNewUser.value) {
          response = await apiService.createUser(editingUser.value);
        } else {
          // 更新用户时只发送需要更改的字段，而不是整个用户对象
          const updatedFields = {
            username: editingUser.value.username,
            email: editingUser.value.email,
            phone: editingUser.value.phone,
            role: editingUser.value.role,
            active: editingUser.value.active
          };
          response = await apiService.updateUser(editingUser.value.id, updatedFields);
        }
        
        const isSuccess = 
          response.status === 200 || 
          response.status === 201 || 
          (response.data && response.data.status === "success");
          
        if (isSuccess) {
          showEditModal.value = false;
          loadUsers(); // 重新加载用户列表
          ElMessage.success(isNewUser.value ? '用户创建成功' : '用户更新成功');
        }
      } catch (error) {
        console.error('保存用户失败:', error);
        if (error.response?.data?.message) {
          editingError.value = error.response.data.message;
        } else if (error.response?.data?.error) {
          editingError.value = error.response.data.error;
        } else {
          editingError.value = '操作失败，请稍后重试';
        }
      } finally {
        editingLoading.value = false;
      }
    };
    
    // 重置密码
    const resetPassword = (user) => {
      resetPasswordUser.value = user;
      resetPasswordData.value = {
        newPassword: '',
        confirmPassword: ''
      };
      resetPasswordError.value = '';
      showResetModal.value = true;
    };
    
    // 提交重置密码
    const submitResetPassword = async () => {
      // 验证两次输入的密码是否一致
      if (resetPasswordData.value.newPassword !== resetPasswordData.value.confirmPassword) {
        resetPasswordError.value = '两次输入的新密码不一致';
        return;
      }

      // 验证新密码长度
      if (resetPasswordData.value.newPassword.length < 6) {
        resetPasswordError.value = '新密码长度至少需要6个字符';
        return;
      }
      
      resetPasswordLoading.value = true;
      resetPasswordError.value = '';
      
      try {
        const response = await apiService.resetUserPassword(
          resetPasswordUser.value.id, 
          resetPasswordData.value.newPassword
        );

        const isSuccess = 
          response.status === 200 || 
          (response.data && response.data.status === "success");
          
        if (isSuccess) {
          showResetModal.value = false;
          ElMessage.success('密码重置成功');
        }
      } catch (error) {
        console.error('重置密码失败:', error);
        if (error.response?.data?.message) {
          resetPasswordError.value = error.response.data.message;
        } else if (error.response?.data?.error) {
          resetPasswordError.value = error.response.data.error;
        } else {
          resetPasswordError.value = '重置密码失败，请稍后重试';
        }
      } finally {
        resetPasswordLoading.value = false;
      }
    };
    
    // 切换用户状态
    const toggleUserStatus = async (user) => {
      try {
        const newStatus = !user.active;
        // 确保仅发送状态信息，不涉及其他字段，特别是密码
        const response = await apiService.toggleUserStatus(user.id, newStatus);
        
        const isSuccess = 
          response.status === 200 || 
          (response.data && response.data.status === "success");
          
        if (isSuccess) {
          // 不仅更新本地数据，还需要重新从服务器加载最新的用户数据
          ElMessage.success(`用户已${newStatus ? '启用' : '禁用'}`);
          loadUsers(); // 重新加载用户列表以确保显示最新的服务器状态
        }
      } catch (error) {
        console.error('更改用户状态失败:', error);
        let errorMessage = '操作失败';
        if (error.response?.data?.message) {
          errorMessage += ': ' + error.response.data.message;
        } else if (error.response?.data?.error) {
          errorMessage += ': ' + error.response.data.error;
        } else {
          errorMessage += '，请稍后重试';
        }
        ElMessage.error(errorMessage);
      }
    };
    
    // 删除用户
    const deleteUser = (user) => {
      deletingUser.value = user;
      showDeleteModal.value = true;
    };
    
    // 确认删除用户
    const confirmDeleteUser = async () => {
      deleteLoading.value = true;
      
      try {
        const response = await apiService.deleteUser(deletingUser.value.id);
        
        const isSuccess = 
          response.status === 200 || 
          (response.data && response.data.status === "success");
          
        if (isSuccess) {
          showDeleteModal.value = false;
          // 从用户列表中移除
          users.value = users.value.filter(u => u.id !== deletingUser.value.id);
          totalUsers.value--;
          ElMessage.success('用户删除成功');
        }
      } catch (error) {
        console.error('删除用户失败:', error);
        let errorMessage = '删除用户失败';
        if (error.response?.data?.message) {
          errorMessage += ': ' + error.response.data.message;
        } else if (error.response?.data?.error) {
          errorMessage += ': ' + error.response.data.error;
        } else {
          errorMessage += '，请稍后重试';
        }
        ElMessage.error(errorMessage);
      } finally {
        deleteLoading.value = false;
      }
    };
    
    return {
      users,
      loading,
      searchQuery,
      currentPage,
      pageSize,
      totalUsers,
      totalPages,
      editingUser,
      isNewUser,
      editingLoading,
      editingError,
      showEditModal,
      showAddUserModal,
      resetPasswordUser,
      resetPasswordData,
      resetPasswordLoading,
      resetPasswordError,
      showResetModal,
      deletingUser,
      deleteLoading,
      showDeleteModal,
      loadUsers,
      searchUsers,
      changePage,
      formatDate,
      editUser,
      saveUser,
      resetPassword,
      submitResetPassword,
      toggleUserStatus,
      deleteUser,
      confirmDeleteUser
    };
  }
};
</script>

<style>
/* 防止可能导致ResizeObserver错误的样式问题 */
.el-dialog__body, 
.el-dialog__footer,
.el-table__header,
.el-table__body,
.el-select-dropdown__list {
  contain: content !important;
  width: 100% !important;
  box-sizing: border-box !important;
}

/* 全局CSS变量覆盖，确保应用到所有Element Plus组件 */
:root {
  --el-color-primary: #6366f1 !important;
  --el-color-primary-light-3: #818cf8 !important;
  --el-color-primary-light-5: #a5b4fc !important;
  --el-color-primary-dark-2: #4f46e5 !important;
  --el-border-color: rgba(255, 255, 255, 0.1) !important;
  --el-border-color-light: rgba(255, 255, 255, 0.05) !important;
  --el-text-color-primary: #e5e7eb !important;
  --el-text-color-regular: #d1d5db !important;
  --el-bg-color: #1a2032 !important;
  --el-bg-color-overlay: #1a2032 !important;
  --el-fill-color-blank: rgba(26, 32, 50, 0.95) !important;
  --el-fill-color: rgba(17, 24, 39, 0.8) !important;
  --el-mask-color: rgba(0, 0, 0, 0.7) !important;
}
</style>

<style scoped>
/* 整体页面样式 */
.user-management-page {
  min-height: 100vh;
  background-color: #111827;
  color: #e5e7eb;
  padding: 2rem;
  width: 100%;
  margin: 0;
}

.container {
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
}

.main-card {
  background: rgba(26, 32, 50, 0.8) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  border-radius: 1rem !important;
  overflow: hidden;
  backdrop-filter: blur(5px);
  transition: all 0.3s ease;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

/* 头部样式 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 0;
}

.header-title {
  font-size: 2rem;
  font-weight: 800;
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin: 0;
}

/* 搜索部分样式 */
.search-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
  gap: 1rem;
}

.search-container {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex: 1;
}

.search-input {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #e5e7eb;
}

/* 表格样式 */
.table-container {
  margin-bottom: 1.5rem;
  border-radius: 0.5rem;
  overflow: hidden;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
}

.empty-placeholder {
  padding: 2rem;
  text-align: center;
  color: #9ca3af;
}

.action-buttons {
  display: flex;
  gap: 0.75rem;
  justify-content: center;
}

.tag-style {
  padding: 0.25rem 0.75rem;
  font-weight: 500;
  border-radius: 1rem;
}

:deep(.el-table) {
  background-color: transparent;
  border-color: rgba(255, 255, 255, 0.05);
  font-size: 14px;
}

:deep(.el-table::before),
:deep(.el-table::after) {
  display: none;
}

:deep(.el-table__inner-wrapper::before) {
  display: none;
}

:deep(.el-table th.el-table__cell) {
  background-color: #1a2032;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 12px 10px;
  font-size: 14px;
}

:deep(.el-table .cell) {
  padding: 0 10px;
  line-height: 1.5;
  word-break: break-word;
}

:deep(.el-table tr) {
  height: 60px;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  padding: 10px;
}

:deep(.el-table--striped .table-row.el-table__row--striped td.el-table__cell) {
  background-color: rgba(30, 41, 59, 0.5);
}

:deep(.el-table__body tr:hover td.el-table__cell) {
  background-color: rgba(51, 65, 85, 0.5) !important;
}

:deep(.el-button.el-button--small.is-circle) {
  width: 32px;
  height: 32px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 分页样式 */
.pagination-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 1rem 0;
}

.records-info {
  color: #9ca3af;
  font-size: 0.9rem;
}

/* 深色主题表单控件样式 */
:deep(.el-input__wrapper),
:deep(.el-textarea__wrapper) {
  background-color: rgba(17, 24, 39, 0.8) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  box-shadow: none !important;
  border-radius: 0.5rem !important;
}

:deep(.el-input__inner),
:deep(.el-textarea__inner) {
  color: #e5e7eb !important;
  background-color: transparent !important;
}

:deep(.el-input__inner::placeholder),
:deep(.el-textarea__inner::placeholder) {
  color: #6b7280 !important;
}

:deep(.el-select .el-input__wrapper) {
  background-color: rgba(17, 24, 39, 0.8) !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
}

:deep(.el-select-dropdown) {
  background-color: rgba(26, 32, 50, 0.95) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}

:deep(.el-select-dropdown__item) {
  color: #e5e7eb !important;
}

:deep(.el-select-dropdown__item.hover),
:deep(.el-select-dropdown__item:hover) {
  background-color: rgba(99, 102, 241, 0.1) !important;
}

:deep(.el-select-dropdown__item.selected) {
  color: #6366f1 !important;
}

:deep(.el-checkbox__input .el-checkbox__inner) {
  background-color: rgba(17, 24, 39, 0.8) !important;
  border-color: rgba(255, 255, 255, 0.2) !important;
}

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: #6366f1 !important;
  border-color: #6366f1 !important;
}

:deep(.el-checkbox__label) {
  color: #e5e7eb !important;
}

/* 对话框样式 */
:deep(.el-overlay) {
  background-color: rgba(0, 0, 0, 0.6) !important;
}

:deep(.custom-dialog) {
  background-color: #1a2032 !important;
  border-radius: 1rem !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  overflow: hidden !important;
}

:deep(.el-dialog__header) {
  background-color: #111827 !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06) !important;
  margin-right: 0 !important;
}

:deep(.el-dialog__title) {
  color: #e5e7eb !important;
  font-weight: 600 !important;
}

:deep(.el-dialog__body) {
  background-color: #1a2032 !important;
  color: #e5e7eb !important;
}

:deep(.el-dialog__footer) {
  background-color: #1a2032 !important;
  border-top: 1px solid rgba(255, 255, 255, 0.06) !important;
}

.dialog-footer {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
}

.form-hint {
  color: #9ca3af;
  font-size: 0.85rem;
  margin-top: 0.5rem;
}

.error-message {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  padding: 0.8rem;
  border-radius: 0.5rem;
  margin-top: 1rem;
  border-left: 4px solid #ef4444;
}

.warning-message {
  background: rgba(245, 158, 11, 0.1);
  color: #f59e0b;
  padding: 0.8rem;
  border-radius: 0.5rem;
  margin-bottom: 1rem;
  border-left: 4px solid #f59e0b;
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* 按钮样式统一 */
:deep(.el-button) {
  border-radius: 8px;
  transition: all 0.3s;
  font-weight: 500;
  padding: 12px 28px;
  height: auto;
  font-size: 15px;
}

:deep(.el-button--primary) {
  background-color: #6366f1 !important; /* 使用与页面风格一致的颜色 */
  border-color: #6366f1 !important;
  color: #ffffff !important;
}

:deep(.el-button--primary:hover) {
  background-color: #4f46e5 !important; /* 悬停时颜色稍深 */
  border-color: #4f46e5 !important;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  transform: translateY(-2px);
}

:deep(.el-button--primary:active) {
  background-color: #4338ca !important; /* 点击时颜色更深 */
  border-color: #4338ca !important;
  box-shadow: none;
}

:deep(.el-button--default) {
  background: rgba(255, 255, 255, 0.05);
  border: 2px solid rgba(255, 255, 255, 0.2);
  color: #e5e7eb;
}

:deep(.el-button--default:hover) {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.3);
  color: #ffffff;
  transform: translateY(-2px);
}

:deep(.el-button--default:active) {
  transform: translateY(0);
}

:deep(.el-button--success) {
  background-color: #10b981 !important;
  border-color: #10b981 !important;
  color: #ffffff !important;
}

:deep(.el-button--success:hover) {
  background-color: #059669 !important;
  border-color: #059669 !important;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
  transform: translateY(-2px);
}

:deep(.el-button--danger) {
  background-color: #ef4444 !important;
  border-color: #ef4444 !important;
  color: #ffffff !important;
}

:deep(.el-button--danger:hover) {
  background-color: #dc2626 !important;
  border-color: #dc2626 !important;
  box-shadow: 0 4px 12px rgba(220, 38, 38, 0.3);
  transform: translateY(-2px);
}

:deep(.el-button.el-button--small) {
  padding: 8px 16px;
  font-size: 14px;
}

/* 标签样式覆盖 */
:deep(.el-tag) {
  border: none;
  padding: 0.25rem 0.75rem;
  font-weight: 500;
  border-radius: 1rem;
}

:deep(.el-tag--info) {
  background-color: rgba(51, 65, 85, 0.7) !important;
  color: #cbd5e1 !important;
  border: none !important;
}

:deep(.el-tag--success) {
  background-color: rgba(16, 185, 129, 0.2) !important;
  color: #34d399 !important;
  border: 1px solid rgba(16, 185, 129, 0.3) !important;
}

:deep(.el-tag--danger) {
  background-color: rgba(239, 68, 68, 0.2) !important;
  color: #f87171 !important;
  border: 1px solid rgba(239, 68, 68, 0.3) !important;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .user-management-page {
    padding: 1rem;
  }
  
  .search-section {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-container {
    width: 100%;
    margin-bottom: 0.5rem;
  }
  
  .pagination-section {
    flex-direction: column;
    align-items: flex-start;
    gap: 1rem;
  }
  
  :deep(.el-pagination) {
    width: 100%;
    justify-content: center;
  }
}
</style> 