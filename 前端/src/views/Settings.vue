<template>
  <div class="settings-page">
    <div class="container">
      <el-card class="main-card">
        <template #header>
          <div class="card-header">
            <h2 class="header-title">系统设置</h2>
          </div>
        </template>
        
        <div class="row">
          <div class="col-md-6 mb-4">
            <el-card class="service-card">
              <template #header>
                <div class="d-flex justify-content-between align-items-center">
                  <h5 class="mb-0">模型服务状态</h5>
                  <el-tag :type="modelStatus ? 'success' : 'danger'" size="small">
                    {{ modelStatus ? '在线' : '离线' }}
                  </el-tag>
                </div>
              </template>
              <div class="card-content">
                <div class="action-row">
                  <el-button 
                    type="primary"
                    @click="testModelConnection"
                    :disabled="isModelTesting || !modelStatus"
                    :loading="isModelTesting">
                    测试模型连接
                  </el-button>
                </div>
                <div v-if="isAdmin" class="action-row">
                  <el-button 
                    :type="modelStatus ? 'danger' : 'success'"
                    @click="toggleModelService"
                    :loading="isModelLoading">
                    {{ modelStatus ? '停止服务' : '启动服务' }}
                  </el-button>
                </div>
              </div>
            </el-card>
          </div>

          <div class="col-md-6 mb-4">
            <el-card class="service-card">
              <template #header>
                <div class="d-flex justify-content-between align-items-center">
                  <h5 class="mb-0">数据库服务状态</h5>
                  <el-tag :type="dbStatus ? 'success' : 'danger'" size="small">
                    {{ dbStatus ? '在线' : '离线' }}
                  </el-tag>
                </div>
              </template>
              <div class="card-content">
                <div class="action-row">
                  <el-button 
                    type="primary"
                    @click="testDatabaseConnection"
                    :disabled="isDbTesting || !dbStatus"
                    :loading="isDbTesting">
                    测试数据库连接
                  </el-button>
                </div>
                <div v-if="isAdmin" class="action-row">
                  <el-button 
                    :type="dbStatus ? 'danger' : 'success'" 
                    @click="toggleDbService"
                    :loading="isDbLoading">
                    {{ dbStatus ? '停止服务' : '启动服务' }}
                  </el-button>
                </div>
              </div>
            </el-card>
          </div>
        </div>

        <!-- 系统日志区域，仅管理员可见 -->
        <div class="mt-4" v-if="isAdmin">
          <el-card class="logs-card">
            <template #header>
              <div class="d-flex justify-content-between align-items-center">
                <h5 class="mb-0">系统日志</h5>
                <div>
                  <el-button 
                    type="default" 
                    size="small" 
                    @click="refreshSystemStatus">
                    刷新
                  </el-button>
                  <el-button 
                    type="danger" 
                    size="small" 
                    @click="showClearLogsConfirm" 
                    class="ms-2">
                    清除日志
                  </el-button>
                </div>
              </div>
            </template>
            <div class="log-container">
              <div v-if="systemLogs.length === 0" class="empty-logs">
                暂无系统日志记录
              </div>
              <div v-else>
                <div v-for="(log, index) in systemLogs" :key="index" 
                     class="log-entry" 
                     :class="{'border-bottom': index < systemLogs.length - 1}">
                  <el-tag size="small" :type="getLogLevelType(log.level)" class="log-level me-2">
                    {{ log.level }}
                  </el-tag>
                  <span class="log-time text-muted me-2">{{ log.timestamp }}</span>
                  <span class="log-message">{{ log.message }}</span>
                </div>
              </div>
            </div>
          </el-card>
        </div>
      </el-card>
    </div>
    
    <!-- 清除日志确认对话框 -->
    <el-dialog
      v-model="clearLogsDialogVisible"
      title="确认清除"
      width="500px"
      class="dark-theme stable-dialog"
      custom-class="custom-dialog"
      :append-to-body="true"
      :destroy-on-close="true"
    >
      <p>确定要清除所有系统日志吗？此操作无法恢复。</p>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="clearLogsDialogVisible = false">取消</el-button>
          <el-button type="danger" @click="clearSystemLogs" :loading="isClearingLogs">
            确认清除
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue';
import { useStore } from 'vuex';
import SystemService from '@/api/system';

export default {
  name: 'SettingsView',
  setup() {
    const store = useStore();
    
    // 状态变量
    const modelStatus = ref(false);
    const dbStatus = ref(false);
    const isModelLoading = ref(false);
    const isDbLoading = ref(false);
    const isModelTesting = ref(false);
    const isDbTesting = ref(false);
    const systemLogs = ref([]);
    const isClearingLogs = ref(false);
    const clearLogsDialogVisible = ref(false);
    
    // 计算属性：检查用户是否为管理员
    const isAdmin = computed(() => {
      const user = JSON.parse(localStorage.getItem('user') || '{}');
      const role = user.role?.toLowerCase() || '';
      return role === 'admin' || role === 'administrator';
    });
    
    // 获取系统状态
    const fetchSystemStatus = async () => {
      try {
        const response = await SystemService.getSystemStatus();
        const data = response.data;
        
        // 更新模型状态
        modelStatus.value = data.model.status === 'online';
        
        // 更新数据库状态
        dbStatus.value = data.database.status === 'online';
        
        // 更新日志信息
        systemLogs.value = data.logs || [];
      } catch (error) {
        console.error('获取系统状态失败:', error);
        store.commit('SET_NOTIFICATION', {
          type: 'error',
          message: '获取系统状态失败，请稍后再试'
        });
      }
    };
    
    // 刷新系统状态
    const refreshSystemStatus = () => {
      fetchSystemStatus();
    };
    
    // 切换模型服务状态
    const toggleModelService = async () => {
      if (!isAdmin.value) return;
      
      isModelLoading.value = true;
      try {
        const action = modelStatus.value ? 'stop' : 'start';
        await SystemService.toggleModelService(action);
        
        // 更新状态（切换当前状态）
        modelStatus.value = !modelStatus.value;
        
        store.commit('SET_NOTIFICATION', {
          type: 'success',
          message: `模型服务${modelStatus.value ? '启动' : '停止'}成功`
        });
        
        // 刷新获取最新状态
        setTimeout(fetchSystemStatus, 1000);
      } catch (error) {
        console.error('操作模型服务失败:', error);
        store.commit('SET_NOTIFICATION', {
          type: 'error',
          message: `模型服务${modelStatus.value ? '停止' : '启动'}失败，请稍后再试`
        });
      } finally {
        isModelLoading.value = false;
      }
    };
    
    // 切换数据库服务状态
    const toggleDbService = async () => {
      if (!isAdmin.value) return;
      
      isDbLoading.value = true;
      try {
        const action = dbStatus.value ? 'stop' : 'start';
        await SystemService.toggleDatabaseService(action);
        
        // 更新状态（切换当前状态）
        dbStatus.value = !dbStatus.value;
        
        store.commit('SET_NOTIFICATION', {
          type: 'success',
          message: `数据库服务${dbStatus.value ? '启动' : '停止'}成功`
        });
        
        // 刷新获取最新状态
        setTimeout(fetchSystemStatus, 1000);
      } catch (error) {
        console.error('操作数据库服务失败:', error);
        store.commit('SET_NOTIFICATION', {
          type: 'error',
          message: `数据库服务${dbStatus.value ? '停止' : '启动'}失败，请稍后再试`
        });
      } finally {
        isDbLoading.value = false;
      }
    };
    
    // 测试模型连接
    const testModelConnection = async () => {
      isModelTesting.value = true;
      try {
        const response = await SystemService.testModelConnection();
        store.commit('SET_NOTIFICATION', {
          type: 'success',
          message: `模型连接测试${response.data.success ? '成功' : '失败'}: ${response.data.message}`
        });
        // 刷新获取最新日志
        setTimeout(fetchSystemStatus, 500);
      } catch (error) {
        console.error('测试模型连接失败:', error);
        store.commit('SET_NOTIFICATION', {
          type: 'error',
          message: '测试模型连接失败，请稍后再试'
        });
      } finally {
        isModelTesting.value = false;
      }
    };
    
    // 测试数据库连接
    const testDatabaseConnection = async () => {
      isDbTesting.value = true;
      try {
        const response = await SystemService.testDatabaseConnection();
        store.commit('SET_NOTIFICATION', {
          type: 'success',
          message: `数据库连接测试${response.data.success ? '成功' : '失败'}: ${response.data.message}`
        });
        // 刷新获取最新日志
        setTimeout(fetchSystemStatus, 500);
      } catch (error) {
        console.error('测试数据库连接失败:', error);
        store.commit('SET_NOTIFICATION', {
          type: 'error',
          message: '测试数据库连接失败，请稍后再试'
        });
      } finally {
        isDbTesting.value = false;
      }
    };
    
    // 显示清除日志确认对话框
    const showClearLogsConfirm = () => {
      clearLogsDialogVisible.value = true;
    };
    
    // 清除系统日志
    const clearSystemLogs = async () => {
      if (!isAdmin.value) return;
      
      isClearingLogs.value = true;
      try {
        await SystemService.clearSystemLogs();
        
        // 清空本地日志数据
        systemLogs.value = [];
        
        store.commit('SET_NOTIFICATION', {
          type: 'success',
          message: '系统日志已清除'
        });
        
        // 关闭确认对话框
        clearLogsDialogVisible.value = false;
      } catch (error) {
        console.error('清除系统日志失败:', error);
        store.commit('SET_NOTIFICATION', {
          type: 'error',
          message: '清除系统日志失败，请稍后再试'
        });
      } finally {
        isClearingLogs.value = false;
      }
    };
    
    // 获取日志级别对应的样式类型
    const getLogLevelType = (level) => {
      switch(level && level.toUpperCase()) {
        case 'ERROR':
          return 'danger';
        case 'WARNING':
          return 'warning';
        case 'INFO':
          return 'info';
        case 'SUCCESS':
          return 'success';
        default:
          return 'info';
      }
    };
    
    // 组件挂载时获取系统状态
    onMounted(() => {
      // 初始化时获取一次真实状态
      fetchSystemStatus();
      
      // 每30秒自动刷新一次状态
      const interval = setInterval(fetchSystemStatus, 30000);
      
      // 组件卸载时清除定时器
      return () => clearInterval(interval);
    });
    
    return {
      modelStatus,
      dbStatus,
      isAdmin,
      isModelLoading,
      isDbLoading,
      isModelTesting,
      isDbTesting,
      isClearingLogs,
      systemLogs,
      clearLogsDialogVisible,
      refreshSystemStatus,
      toggleModelService,
      toggleDbService,
      testModelConnection,
      testDatabaseConnection,
      showClearLogsConfirm,
      clearSystemLogs,
      getLogLevelType
    };
  }
};
</script>

<style scoped>
/* 整体页面样式 */
.settings-page {
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

/* 服务卡片样式 */
.service-card, 
.logs-card {
  background: rgba(17, 24, 39, 0.6) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  border-radius: 0.75rem !important;
  margin-bottom: 1rem;
}

:deep(.service-card .el-card__header),
:deep(.logs-card .el-card__header) {
  background-color: rgba(30, 41, 59, 0.8);
  padding: 0.75rem 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

:deep(.service-card .el-card__header h5),
:deep(.logs-card .el-card__header h5) {
  color: #e5e7eb;
  font-weight: 600;
  margin-bottom: 0;
}

:deep(.service-card .el-card__body),
:deep(.logs-card .el-card__body) {
  padding: 1rem;
  background-color: rgba(17, 24, 39, 0.6);
}

.card-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.action-row {
  display: flex;
  justify-content: center;
}

/* 日志容器样式 */
.log-container {
  background: rgba(17, 24, 39, 0.4);
  border-radius: 0.5rem;
  padding: 1rem;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.empty-logs {
  text-align: center;
  color: #9ca3af;
  padding: 2rem 0;
}

.log-entry {
  font-family: monospace;
  font-size: 0.9rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  color: #d1d5db;
}

.log-entry:last-child {
  border-bottom: none;
}

.log-time,
.log-time.text-muted,
:deep(.log-time),
:deep(.log-time.text-muted) {
  color: #ffffff !important;
  font-weight: 500;
  opacity: 1 !important;
}

.log-level {
  min-width: 65px;
  text-align: center;
}

/* 对话框样式 */
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

/* 响应式设计 */
@media (max-width: 768px) {
  .settings-page {
    padding: 1rem;
  }
  
  .row {
    flex-direction: column;
  }
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

/* 图标按钮样式 */
:deep(.el-button [class*="el-icon-"]+span) {
  margin-left: 6px;
}

:deep(.el-button .el-icon) {
  color: inherit;
}

/* 按钮样式统一 - 小按钮调整 */
:deep(.el-button.el-button--small) {
  padding: 8px 16px;
  font-size: 14px;
  background-color: #6366f1 !important; /* 与主要按钮保持一致 */
  border-color: #6366f1 !important;
  color: #ffffff !important;
}

:deep(.el-button.el-button--small:hover) {
  background-color: #4f46e5 !important;
  border-color: #4f46e5 !important;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
  transform: translateY(-2px);
}

:deep(.el-button.el-button--small .el-icon) {
  margin-right: 4px;
  color: #ffffff !important;
}

:deep(.el-button.el-button--small.el-button--danger) {
  background-color: #ef4444 !important;
  border-color: #ef4444 !important;
}

:deep(.el-button.el-button--small.el-button--danger:hover) {
  background-color: #dc2626 !important;
  border-color: #dc2626 !important;
}

/* 标签样式调整 */
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

:deep(.el-tag--info) {
  background-color: rgba(51, 65, 85, 0.7) !important;
  color: #cbd5e1 !important;
  border: none !important;
}
</style> 