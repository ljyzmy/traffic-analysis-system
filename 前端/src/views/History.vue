                                                                                                                                        <template>
  <div class="history-page">
    <el-card class="history-card">
      <template #header>
        <div class="card-header">
          <h2>分析历史记录</h2>
          <div class="header-actions">
            <div class="selection-controls" v-if="totalRecords > 0">
              <el-checkbox v-model="selectAll" @change="handleSelectAllChange" class="wider-checkbox">全选</el-checkbox>
            </div>
            <div class="filter-user" v-if="userInfo && userInfo.role === 'admin'">
              <span style="white-space:nowrap;">查看用户:</span>
              <el-select 
                v-model="selectedUser" 
                placeholder="选择用户" 
                clearable
                @change="handleUserChange"
                style="min-width: 120px; margin-right: 10px;"
                size="small"
              >
                <el-option label="全部用户" value=""></el-option>
                <el-option 
                  v-for="user in availableUsers" 
                  :key="user.id || user._id" 
                  :label="user.username" 
                  :value="user.username">
                </el-option>
              </el-select>
            </div>
            <el-button class="custom-btn-outline" :disabled="selectedRecords.length === 0" @click="showDeleteConfirm">
              <el-icon><delete /></el-icon> 批量删除
            </el-button>
            <router-link to="/upload">
              <el-button class="custom-btn-primary">
                <el-icon><plus /></el-icon> 新的分析
              </el-button>
            </router-link>
          </div>
        </div>
      </template>
      
      <!-- 筛选面板 -->
      <div class="filter-panel">
        <div class="filter-row">
          <div class="filter-item">
            <span class="filter-label">日期：</span>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY/MM/DD"
              value-format="YYYY-MM-DD"
              :shortcuts="dateShortcuts"
              @change="handleFilterChange"
              class="date-picker"
              size="small"
            />
          </div>
          <div class="filter-item">
            <span class="filter-label">时间：</span>
            <el-time-picker
              v-model="startTime"
              placeholder="开始时间"
              format="HH:mm"
              value-format="HH:mm:ss"
              @change="handleFilterChange"
              class="time-picker"
              size="small"
            />
            <span class="time-separator">至</span>
            <el-time-picker
              v-model="endTime"
              placeholder="结束时间"
              format="HH:mm"
              value-format="HH:mm:ss"
              @change="handleFilterChange"
              class="time-picker"
              size="small"
            />
          </div>
          <div class="filter-actions">
            <el-button type="primary" size="small" @click="applyFilters" class="filter-btn">应用</el-button>
            <el-button size="small" @click="resetFilters" class="reset-btn">重置</el-button>
          </div>
        </div>
      </div>
      
      <HistoryList 
        ref="historyList" 
        :filters="filters" 
        :page-size="8"
        :selection-mode="true"
        type="image"
        @selection-change="handleSelectionChange" 
      />
      
      <!-- 分页 -->
      <div class="pagination-container">
        <div class="batch-actions">
          <el-checkbox v-if="totalRecords > 0" v-model="selectAll" @change="handleSelectAllChange" class="me-2">全选</el-checkbox>
          <span v-if="selectedRecords.length > 0" class="selected-info">
            已选择 {{ selectedRecords.length }} 项
          </span>
          <el-button 
            v-if="selectedRecords.length > 0" 
            class="custom-btn-outline"
            size="small" 
            @click="showDeleteConfirm"
          >
            批量删除
          </el-button>
        </div>
        <div class="pagination-wrapper">
          <div class="pagination-info">共 {{ totalRecords }} 条记录，本页共 {{ currentPageRecords }} 条</div>
          <el-pagination
            background
            layout="prev, pager, next"
            :total="totalRecords"
            :page-size="8"
            :current-page="currentPage"
            @current-change="handlePageChange"
            prev-text="上一页"
            next-text="下一页"
          />
        </div>
      </div>
    </el-card>
    
    <!-- 删除确认对话框 -->
    <el-dialog
      v-model="deleteDialogVisible"
      title="确认删除"
      width="30%"
      :close-on-click-modal="false"
      class="dark-theme-dialog"
    >
      <span>确定要删除选中的 {{ selectedRecords.length }} 条历史记录吗？此操作无法恢复。</span>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="deleteDialogVisible = false">取消</el-button>
          <el-button class="custom-btn-outline" @click="confirmBatchDelete" :loading="deleting">确定删除</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { Plus, Delete } from '@element-plus/icons-vue';
import HistoryList from '@/components/history/HistoryList.vue';
import { ref, reactive, watch, onMounted, nextTick, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { apiService } from '@/api';

export default {
  name: 'HistoryView',
  components: {
    HistoryList,
    Plus,
    Delete
  },
  setup() {
    // 日期和时间筛选相关的数据
    const dateRange = ref(null);
    const startTime = ref(null);
    const endTime = ref(null);
    const filters = reactive({
      startDate: null,
      endDate: null,
      startTime: null,
      endTime: null
    });
    const historyList = ref(null);
    
    // 用户信息
    const userInfo = ref(null);
    
    // 用户筛选相关
    const selectedUser = ref('');
    const availableUsers = ref([]);
    
    // 分页相关
    const currentPage = ref(1);
    const totalRecords = ref(0);
    const currentPageRecords = ref(0);

    // 批量删除相关
    const selectedRecords = ref([]);
    const deleteDialogVisible = ref(false);
    const deleting = ref(false);
    
    // 全选状态
    const selectAll = ref(false);

    // 日期快捷选项
    const dateShortcuts = [
      {
        text: '最近一周',
        value: () => {
          const end = new Date();
          const start = new Date();
          start.setTime(start.getTime() - 3600 * 1000 * 24 * 7);
          return [start, end];
        },
      },
      {
        text: '最近一个月',
        value: () => {
          const end = new Date();
          const start = new Date();
          start.setTime(start.getTime() - 3600 * 1000 * 24 * 30);
          return [start, end];
        },
      },
      {
        text: '最近三个月',
        value: () => {
          const end = new Date();
          const start = new Date();
          start.setTime(start.getTime() - 3600 * 1000 * 24 * 90);
          return [start, end];
        },
      }
    ];

    // 处理筛选变化
    const handleFilterChange = () => {
      console.log('筛选条件变化:', { dateRange: dateRange.value, startTime: startTime.value, endTime: endTime.value });
    };

    // 应用筛选
    const applyFilters = () => {
      if (dateRange.value && dateRange.value.length === 2) {
        filters.startDate = dateRange.value[0];
        filters.endDate = dateRange.value[1];
      } else {
        filters.startDate = null;
        filters.endDate = null;
      }

      filters.startTime = startTime.value;
      filters.endTime = endTime.value;

      console.log('应用筛选条件:', filters);
      
      // 重置页码为第一页
      currentPage.value = 1;
      
      // 重置选中的记录
      selectedRecords.value = [];
      
      // 如果HistoryList组件提供了筛选方法，则调用它
      if (historyList.value && typeof historyList.value.applyFilters === 'function') {
        historyList.value.applyFilters(filters);
        
        // 更新总记录数
        if (historyList.value.totalFilteredRecords) {
          totalRecords.value = historyList.value.totalFilteredRecords();
        }
      } else {
        console.log('HistoryList组件未提供筛选方法');
      }
    };

    // 重置筛选
    const resetFilters = () => {
      dateRange.value = null;
      startTime.value = null;
      endTime.value = null;
      
      filters.startDate = null;
      filters.endDate = null;
      filters.startTime = null;
      filters.endTime = null;
      
      console.log('重置筛选条件');
      
      // 重置页码为第一页
      currentPage.value = 1;
      
      // 重置选中的记录
      selectedRecords.value = [];
      
      // 如果HistoryList组件提供了重置方法，则调用它
      if (historyList.value && typeof historyList.value.resetFilters === 'function') {
        historyList.value.resetFilters();
        
        // 更新总记录数
        if (historyList.value.totalFilteredRecords) {
          totalRecords.value = historyList.value.totalFilteredRecords();
        }
      }
    };
    
    // 处理页码变化
    const handlePageChange = (page) => {
      currentPage.value = page;
      
      // 更新选中记录
      selectedRecords.value = [];
      
      // 通知HistoryList组件页码变化
      if (historyList.value && typeof historyList.value.setPage === 'function') {
        historyList.value.setPage(page);
        
        // 确保切换页面后更新总记录数和当前页记录数
        nextTick(() => {
          updateTotalRecords();
        });
      }
    };
    
    // 处理全选/取消全选
    const handleSelectAllChange = (val) => {
      if (historyList.value && typeof historyList.value.selectAll === 'function') {
        historyList.value.selectAll(val);
      }
    };
    
    // 处理记录选择变化
    const handleSelectionChange = (selection) => {
      selectedRecords.value = selection;
      // 更新全选状态
      if (totalRecords.value > 0) {
        selectAll.value = selection.length === totalRecords.value;
      } else {
        selectAll.value = false;
      }
    };
    
    // 显示删除确认对话框
    const showDeleteConfirm = () => {
      if (selectedRecords.value.length === 0) {
        ElMessage.warning('请至少选择一条记录');
        return;
      }
      deleteDialogVisible.value = true;
    };
    
    // 确认批量删除
    const confirmBatchDelete = async () => {
      if (selectedRecords.value.length === 0) {
        ElMessage.warning('请至少选择一条记录');
        deleteDialogVisible.value = false;
        return;
      }
      
      deleting.value = true;
      try {
        // 直接传递选中的记录对象，而不是仅传递ID
        if (historyList.value && typeof historyList.value.batchDelete === 'function') {
          await historyList.value.batchDelete(selectedRecords.value);
          
          // 更新总记录数
          if (historyList.value.totalFilteredRecords) {
            totalRecords.value = historyList.value.totalFilteredRecords();
          }
          
          // 重置选中记录
          selectedRecords.value = [];
          
          ElMessage.success('批量删除成功');
        } else {
          throw new Error('HistoryList组件未提供批量删除方法');
        }
      } catch (error) {
        console.error('批量删除失败:', error);
        ElMessage.error('批量删除失败: ' + error.message);
      } finally {
        deleting.value = false;
        deleteDialogVisible.value = false;
      }
    };
    
    // 监听HistoryList组件的记录总数变化
    watch(() => historyList.value, (newVal) => {
      if (newVal && typeof newVal.totalFilteredRecords === 'function') {
        totalRecords.value = newVal.totalFilteredRecords();
      }
    }, { deep: true });

    // 更新总记录数的函数
    const updateTotalRecords = () => {
      if (historyList.value && typeof historyList.value.totalFilteredRecords === 'function') {
        totalRecords.value = historyList.value.totalFilteredRecords();
        
        // 更新当前页记录数
        if (historyList.value.displayedHistory && historyList.value.displayedHistory.length !== undefined) {
          currentPageRecords.value = historyList.value.displayedHistory.length;
        }
        
        console.log('更新总记录数:', totalRecords.value, '当前页记录数:', currentPageRecords.value);
      }
    };
    
    // 监听自定义事件
    const handleHistoryUpdated = (event) => {
      console.log('收到历史记录更新事件:', event.detail);
      updateTotalRecords();
      
      // 重置选中的记录
      selectedRecords.value = [];
    };

    // 组件挂载后更新总记录数
    onMounted(() => {
      // 获取用户信息
      const storedUser = localStorage.getItem('user');
      if (storedUser) {
        try {
          userInfo.value = JSON.parse(storedUser);
          console.log('已获取用户信息:', userInfo.value.username);
          
          // 如果是管理员，加载用户列表
          if (userInfo.value.role === 'admin') {
            loadUserList();
          }
        } catch (err) {
          console.error('解析用户信息失败:', err);
        }
      }
      
      // 使用nextTick确保HistoryList组件已经完成挂载和数据加载
      nextTick(() => {
        setTimeout(() => {
          updateTotalRecords();
          
          // 确保历史列表组件能正确处理分页
          if (historyList.value && typeof historyList.value.ensurePagination === 'function') {
            historyList.value.ensurePagination();
          }
        }, 500); // 延迟500ms，确保历史记录已加载
      });
      
      // 添加事件监听
      window.addEventListener('historyUpdated', handleHistoryUpdated);
    });
    
    // 组件卸载时清理事件监听
    onUnmounted(() => {
      window.removeEventListener('historyUpdated', handleHistoryUpdated);
    });

    // 处理用户筛选变更
    const handleUserChange = (username) => {
      console.log(`选择查看用户: ${username || '全部用户'}`);
      
      // 如果历史列表组件存在并且提供了筛选用户的方法
      if (historyList.value && typeof historyList.value.onUserFilterChange === 'function') {
        historyList.value.onUserFilterChange(username);
      }
    };
    
    // 加载用户列表
    const loadUserList = async () => {
      if (!userInfo.value || userInfo.value.role !== 'admin') return;
      
      try {
        console.log('加载用户列表');
        
        // 使用相同的API接口获取用户列表
        const response = await apiService.getUsers(1, 100);
        console.log('获取用户列表响应:', response);
        
        if (response.data && response.data.data) {
          // 获取正确的嵌套数据结构
          const responseData = response.data.data;
          availableUsers.value = responseData.items || [];
        } else if (response.data) {
          // 兼容非嵌套结构
          availableUsers.value = response.data.items || [];
        }
        
        console.log(`已加载${availableUsers.value.length}个用户`);
      } catch (error) {
        console.error('加载用户列表失败:', error);
        ElMessage.warning('无法加载用户列表');
      }
    };

    return {
      dateRange,
      startTime,
      endTime,
      dateShortcuts,
      filters,
      historyList,
      currentPage,
      totalRecords,
      selectedRecords,
      deleteDialogVisible,
      deleting,
      selectAll,
      handleFilterChange,
      applyFilters,
      resetFilters,
      handlePageChange,
      handleSelectionChange,
      handleSelectAllChange,
      showDeleteConfirm,
      confirmBatchDelete,
      userInfo,
      selectedUser,
      availableUsers,
      handleUserChange,
      loadUserList,
      currentPageRecords
    };
  }
};
</script>

<style scoped>
.history-page {
  min-height: calc(100vh - 60px);
  padding: 0;
  background-color: #0e1525;
  color: #e5e7eb;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.history-card {
  max-width: 1100px;
  margin: 0 auto;
  width: 100%;
  flex: 1;
  background: rgba(13, 18, 32, 0.97);
  border: none;
  border-radius: 0;
  overflow: hidden;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.2);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(22, 30, 49, 0.95);
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  padding: 0.8rem 1rem;
}

.card-header h2 {
  font-weight: 700;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin: 0;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  letter-spacing: 0.5px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.users-container {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.user-section {
  display: flex;
  align-items: center;
  margin-right: 10px;
}

.user-label {
  margin-right: 5px;
  font-weight: bold;
  color: #e5e7eb;
  white-space: nowrap;
}

.selection-controls {
  display: flex;
  align-items: center;
  margin-right: 5px;
}

/* 筛选面板样式 */
.filter-panel {
  margin-bottom: 15px;
  padding: 10px 15px;
  background: rgba(22, 30, 49, 0.95);
  border-radius: 0;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.15);
}

.filter-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-wrap: nowrap;
  gap: 10px;
}

.filter-item {
  display: flex;
  align-items: center;
  white-space: nowrap;
}

.filter-label {
  margin-right: 4px;
  font-weight: 600;
  color: rgba(229, 231, 235, 0.95);
  white-space: nowrap;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
  font-size: 0.85rem;
}

.time-separator {
  margin: 0 2px;
  color: rgba(255, 255, 255, 0.95);
  font-weight: 600;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

/* 控制日期选择器宽度 */
.date-picker {
  width: 210px !important;
}

/* 控制时间选择器宽度 */
.time-picker {
  width: 90px !important;
}

/* 修改过的filter-actions样式 */
.filter-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto; /* 将按钮推到右侧 */
}

/* 应用筛选按钮样式 */
.filter-btn {
  height: 28px;
  padding: 0 12px;
  font-size: 0.85rem;
}

/* 重置按钮样式 */
.reset-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.9);
  height: 28px;
  padding: 0 12px;
  font-size: 0.85rem;
}

.reset-btn:hover {
  background: rgba(255, 255, 255, 0.15);
}

/* 优化Element Plus日期时间选择器样式 */
:deep(.el-input__wrapper) {
  padding: 0 8px !important;
}

:deep(.el-input__inner) {
  font-size: 0.85rem !important;
}

:deep(.el-range-separator) {
  padding: 0 2px !important;
}

:deep(.el-range-input) {
  width: 30% !important;
}

/* 响应式优化 */
@media (max-width: 1200px) {
  .filter-row {
    flex-wrap: wrap;
  }
  
  .filter-actions {
    margin-left: 0;
  }
}

/* 分页容器样式 */
.pagination-container {
  margin-top: 0;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding: 12px 18px;
  flex-wrap: wrap;
  gap: 10px;
  background: rgba(22, 30, 49, 0.95);
  border-top: none;
  border-radius: 0;
  box-shadow: 0 -2px 5px rgba(0, 0, 0, 0.15);
}

.pagination-wrapper {
  display: flex;
  align-items: center;
  gap: 15px;
}

.pagination-info {
  color: rgba(209, 213, 219, 0.95);
  font-size: 14px;
  font-weight: 600;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.batch-actions {
  display: none;
}

.selected-info {
  color: rgba(209, 213, 219, 0.95);
  font-size: 14px;
  font-weight: 600;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  width: 100%;
  gap: 10px;
}

.me-2 {
  margin-right: 8px;
}

/* 自定义删除按钮样式 */
.custom-btn-outline {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid #ef4444;
  color: #ffffff;
  border-radius: 0.25rem;
  padding: 0.3rem 0.6rem;
  font-weight: 600;
  font-size: 0.85rem;
  transition: all 0.2s ease;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0.95;
  width: 48%;
}

.custom-btn-outline:hover {
  background: rgba(239, 68, 68, 0.25);
  border-color: #ef4444;
  color: #ffffff;
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(239, 68, 68, 0.3);
}

.custom-btn-outline i {
  color: #ef4444;
  margin-right: 4px;
}

/* 自定义主要按钮样式 */
:deep(.custom-btn-primary) {
  border-radius: 0.25rem;
  transition: all 0.2s;
  font-weight: 600;
  padding: 0.3rem 0.6rem;
  height: 32px;
  font-size: 0.85rem;
  background-color: rgba(79, 70, 229, 0.9) !important;
  border-color: rgba(79, 70, 229, 0.9) !important;
  color: #ffffff !important;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1.05;
  width: 52%;
}

:deep(.custom-btn-primary):hover {
  background-color: rgba(99, 90, 249, 0.9) !important;
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(79, 70, 229, 0.3) !important;
}

/* 修改应用筛选按钮 */
.filter-actions .el-button {
  padding: 0.3rem 0.6rem;
  height: auto;
  font-size: 0.8rem;
  border-radius: 0.25rem;
}

:deep(.el-date-editor),
:deep(.el-time-picker) {
  background-color: rgba(17, 24, 39, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 0.25rem;
}

:deep(.el-input__wrapper) {
  background-color: rgba(17, 24, 39, 0.2);
  box-shadow: none !important;
  border: 1px solid rgba(255, 255, 255, 0.05);
  border-radius: 0.25rem;
}

/* 增强日期范围分隔符的样式 */
:deep(.el-range-separator) {
  color: rgba(255, 255, 255, 0.95) !important;
  font-weight: 600 !important;
  box-shadow: none !important;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
  padding: 0 5px !important;
}

/* 分页样式 */
:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-button-bg-color: rgba(79, 70, 229, 0.1);
  --el-pagination-button-color: #fff;
  --el-pagination-button-disabled-color: #606266;
  --el-pagination-button-disabled-bg-color: rgba(255, 255, 255, 0.1);
  --el-pagination-hover-color: #4f46e5;
}

:deep(.el-pagination .el-pager li) {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

/* 装饰元素 */
.history-page::before {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(#3b82f6, transparent);
  opacity: 0.15;
  top: 10%;
  right: 5%;
  animation: float 8s ease-in-out infinite;
}

.history-page::after {
  content: '';
  position: absolute;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: radial-gradient(#8b5cf6, transparent);
  opacity: 0.15;
  bottom: 15%;
  left: 10%;
  animation: float 6s ease-in-out infinite;
  animation-delay: 1s;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-20px); }
}

@media (max-width: 768px) {
  .filter-row {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .time-picker-container {
    margin-top: 8px;
    width: 100%;
  }
  
  .filter-label {
    margin-bottom: 5px;
  }
  
  .time-separator {
    margin: 10px;
  }
  
  .filter-actions {
    margin-top: 10px;
  }
  
  .pagination-container {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .batch-actions {
    margin-bottom: 10px;
    width: 100%;
  }
}

.filter-user {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wider-checkbox {
  margin-right: 10px;
}

/* 修复卡片内容区域背景色 */
:deep(.el-card) {
  background-color: rgba(25, 32, 50, 0.95);
  border: 1px solid rgba(45, 55, 80, 0.6);
  border-radius: 0.25rem;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

:deep(.el-card__header) {
  background-color: rgba(30, 38, 65, 0.95);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 10px 15px;
}

:deep(.el-card__body) {
  background-color: rgba(25, 32, 50, 0.95);
  color: #e5e7eb;
  padding: 15px;
}

/* 确保头部操作区按钮大小一致 */
.header-actions .el-button,
.header-actions a {
  flex: 1;
  width: auto;
  min-width: 120px;
}

/* 确保卡片中的操作按钮大小一致 */
.item-actions {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.item-actions a,
.item-actions button {
  flex: 1;
  width: 50%;
}

/* 标签样式，与UserManagement.vue一致 */
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

:deep(.el-tag--warning) {
  background-color: rgba(245, 158, 11, 0.2) !important;
  color: #fbbf24 !important;
  border: 1px solid rgba(245, 158, 11, 0.3) !important;
}

:deep(.el-tag--primary) {
  background-color: rgba(99, 102, 241, 0.2) !important;
  color: #818cf8 !important;
  border: 1px solid rgba(99, 102, 241, 0.3) !important;
}

/* 下拉框控件本身样式 */
:deep(.el-select) {
  width: auto !important;
  min-width: 130px !important;
}

:deep(.el-select .el-input__wrapper) {
  background-color: rgba(17, 24, 39, 0.3) !important;
  box-shadow: none !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  border-radius: 0.25rem !important;
  padding: 0 10px !important;
}

:deep(.el-select .el-select__input) {
  color: rgba(255, 255, 255, 0.9) !important;
  margin-left: 0 !important;
}

:deep(.el-select .el-select__placeholder) {
  color: rgba(255, 255, 255, 0.5) !important;
  padding: 0 4px !important;
}

:deep(.el-select__selected-item) {
  color: rgba(255, 255, 255, 0.95) !important;
  background-color: transparent !important;
  font-weight: 500 !important;
}

/* 下拉菜单容器样式 */
:deep(.el-select-dropdown) {
  background-color: rgba(25, 32, 50, 0.98) !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  margin-top: 6px !important;
  margin-bottom: 6px !important;
  border-radius: 4px !important;
  max-height: 340px !important;
  overflow: visible !important;
  padding: 6px 0 !important;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.4) !important;
}

:deep(.el-select-dropdown__wrap) {
  max-height: 300px !important;
}

:deep(.el-select-dropdown__list) {
  padding: 0 !important;
  margin: 0 !important;
  width: 100% !important;
}

/* 下拉菜单选项样式 - 强制垂直居中 */
:deep(.el-select-dropdown__item) {
  position: relative !important;
  color: rgba(255, 255, 255, 0.95) !important;
  font-size: 0.9rem !important;
  padding: 0 16px !important;
  height: 36px !important;
  line-height: 36px !important;
  white-space: nowrap !important;
  margin: 0 !important;
  border-radius: 0 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: flex-start !important;
}

/* 确保文本本身居中 */
:deep(.el-select-dropdown__item span) {
  display: inline-block !important;
  line-height: normal !important;
  vertical-align: middle !important;
}

/* 更强制的文本样式 */
:deep(.el-select-dropdown__list li) {
  margin: 0 !important;
  position: relative !important;
  display: flex !important;
  align-items: center !important;
  height: 36px !important;
}

/* 单独设置选中项的样式 */
:deep(.el-select-dropdown__item.selected) {
  background-color: rgba(79, 70, 229, 0.35) !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  height: 36px !important;
  line-height: 36px !important;
  display: flex !important;
  align-items: center !important;
}

/* 单独设置hover项的样式 */
:deep(.el-select-dropdown__item.hover),
:deep(.el-select-dropdown__item:hover) {
  background-color: rgba(99, 102, 241, 0.25) !important;
  color: #ffffff !important;
  height: 36px !important;
  line-height: 36px !important;
  display: flex !important;
  align-items: center !important;
}

/* Popper定位和样式 */
:deep(.el-popper) {
  min-width: 150px !important;
  padding-bottom: 10px !important;
  margin-bottom: 8px !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4) !important;
}

:deep(.el-select__popper.el-popper) {
  z-index: 2100 !important;
}

:deep(.el-popper__arrow) {
  display: none !important; /* 隐藏箭头，避免定位问题 */
}

/* 滚动条样式 */
:deep(.el-scrollbar) {
  max-height: 300px !important;
  overflow: hidden !important;
  padding-bottom: 12px !important;
}

:deep(.el-scrollbar__view) {
  padding: 0 !important;
  margin: 0 !important;
}

:deep(.el-scrollbar__bar) {
  width: 6px !important;
  opacity: 0.8 !important;
  background: rgba(255, 255, 255, 0.05) !important;
  right: 2px !important;
}

:deep(.el-scrollbar__thumb) {
  background-color: rgba(255, 255, 255, 0.3) !important;
  border-radius: 3px !important;
}

/* 修正定位问题 */
:deep(.el-popper.is-pure) {
  transform: translateY(8px) !important;
}

:deep(.el-select__popper) {
  margin-top: 2px !important;
}

/* 全局修复弹出层问题 */
:deep(.el-overlay) {
  z-index: 2000 !important;
}

/* 自定义对话框样式 */
:deep(.dark-theme-dialog) {
  background: rgba(13, 18, 32, 0.97) !important;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3) !important;
  border-radius: 0.5rem !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}

:deep(.dark-theme-dialog .el-dialog__header) {
  background: rgba(22, 30, 49, 0.95) !important;
  color: #e5e7eb !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
  padding: 12px 20px !important;
}

:deep(.dark-theme-dialog .el-dialog__title) {
  color: #e5e7eb !important;
  font-weight: 600 !important;
  font-size: 1.1rem !important;
}

:deep(.dark-theme-dialog .el-dialog__body) {
  background: rgba(13, 18, 32, 0.97) !important;
  color: #e5e7eb !important;
  padding: 20px !important;
}

:deep(.dark-theme-dialog .el-dialog__footer) {
  background: rgba(22, 30, 49, 0.95) !important;
  border-top: 1px solid rgba(255, 255, 255, 0.1) !important;
  padding: 12px 20px !important;
}

:deep(.dark-theme-dialog .el-dialog__headerbtn:hover .el-dialog__close) {
  color: #6366f1 !important;
}

:deep(.dark-theme-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: #e5e7eb !important;
}

:deep(.dark-theme-dialog .el-button) {
  background: rgba(31, 41, 55, 0.8) !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  color: #e5e7eb !important;
}

:deep(.dark-theme-dialog .el-button:hover) {
  background: rgba(55, 65, 81, 0.8) !important;
  border-color: rgba(255, 255, 255, 0.2) !important;
}
</style> 