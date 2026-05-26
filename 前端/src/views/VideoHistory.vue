<template>
  <div class="video-history-container">
    <el-card class="history-card">
      <template #header>
        <div class="card-header">
          <h2>视频分析历史</h2>
          <div class="header-actions">
            <div class="selection-controls" v-if="videoTasks.length > 0">
              <el-checkbox 
                v-model="selectAll" 
                @change="toggleSelectAll"
                :indeterminate="isIndeterminate"
                class="wider-checkbox"
              >全选</el-checkbox>
            </div>
            <el-button 
              class="custom-btn-outline" 
              :disabled="!hasSelected" 
              @click="handleBatchDelete"
            >
              <el-icon><delete /></el-icon> 批量删除
            </el-button>
            <router-link to="/video-upload">
              <el-button class="custom-btn-primary">
                <el-icon><plus /></el-icon> 上传新视频
              </el-button>
            </router-link>
          </div>
        </div>
      </template>
      
      <!-- 筛选面板 -->
      <div class="filter-panel">
        <div class="filter-row">
          <div class="filter-item">
            <span class="filter-label">搜索：</span>
            <el-input
              v-model="searchQuery"
              placeholder="搜索视频名称"
              clearable
              @clear="handleSearch"
              @keyup.enter="handleSearch"
              size="small"
              class="search-input"
            >
              <template #prefix>
                <el-icon class="el-input__icon"><search /></el-icon>
              </template>
            </el-input>
          </div>
          
          <!-- 管理员用户筛选功能 -->
          <div v-if="isAdmin" class="filter-item">
            <span class="filter-label">用户：</span>
            <el-select
              v-model="userFilter"
              placeholder="用户筛选"
              clearable
              @change="handleSearch"
              size="small"
              :popper-append-to-body="true"
              :reserve-keyword="false"
            >
              <el-option label="全部用户" value=""></el-option>
              <el-option
                v-for="user in userList"
                :key="user.id"
                :label="user.username"
                :value="user.id"
              ></el-option>
            </el-select>
          </div>
          
          <div class="filter-item">
            <span class="filter-label">日期：</span>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :shortcuts="dateShortcuts"
              :editable="false"
              size="small"
              class="date-picker"
            >
            </el-date-picker>
          </div>
          
          <div class="filter-actions">
            <el-button type="primary" size="small" @click="handleSearch" class="filter-btn">应用</el-button>
            <el-button size="small" @click="resetFilters" class="reset-btn">重置</el-button>
            <el-button size="small" @click="fetchVideoTasks" class="refresh-btn">
              <el-icon><refresh /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
      
      <div v-if="loading" class="loading-container">
        <el-skeleton :rows="10" animated />
      </div>
      
      <div v-else-if="error" class="error-container">
        <el-alert
          title="获取历史记录失败"
          type="error"
          :description="error"
          show-icon
        />
        <div class="error-actions">
          <el-button type="primary" @click="fetchVideoTasks">重试</el-button>
        </div>
      </div>
      
      <div v-else-if="videoTasks.length === 0" class="empty-container">
        <el-empty description="暂无视频分析记录">
          <el-button class="custom-btn-primary" @click="$router.push('/video-upload')">上传新视频</el-button>
        </el-empty>
      </div>
      
      <div v-else>
        <el-table
          :data="videoTasks"
          style="width: 100%"
          border
          stripe
          :default-sort="{ prop: sortProp, order: sortOrder }"
          @sort-change="handleSortChange"
          @selection-change="handleSelectionChange"
          v-loading="tableLoading"
          max-height="calc(100vh - 350px)"
          class="custom-table"
        >
          <el-table-column type="selection" width="55">
            <template #header>
              <el-checkbox 
                v-model="selectAll" 
                @change="toggleSelectAll"
                :indeterminate="isIndeterminate"
              />
            </template>
          </el-table-column>
          
          <el-table-column prop="videoName" label="视频名称" min-width="220" sortable>
            <template #default="scope">
              <div v-if="scope.row.editing">
                <el-input 
                  v-model="scope.row.editName" 
                  size="small" 
                  placeholder="输入视频名称"
                  @keyup.enter="confirmRename(scope.row)"
                >
                  <template #append>
                    <el-button @click="confirmRename(scope.row)">确定</el-button>
                  </template>
                </el-input>
              </div>
              <div v-else class="video-name-container">
                <span class="video-name">{{ getVideoDisplayName(scope.row) }}</span>
                <el-button 
                  type="primary" 
                  size="small" 
                  circle 
                  @click="startRename(scope.row)"
                  class="rename-button"
                >
                  <el-icon><Edit /></el-icon>
                </el-button>
              </div>
            </template>
          </el-table-column>
          
          <el-table-column prop="createdAt" label="完成时间" width="180" sortable>
            <template #default="scope">
              {{ formatDate(scope.row.completedAt || scope.row.createdAt) }}
            </template>
          </el-table-column>
          
          <!-- 添加分析人列 -->
          <el-table-column prop="username" label="分析人" width="120">
            <template #default="scope">
              {{ scope.row.username || '未知用户' }}
            </template>
          </el-table-column>
          
          <el-table-column prop="status" label="状态" width="120" sortable>
            <template #default="scope">
              <el-tag :type="getStatusType(scope.row.status)" size="small">
                {{ getStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          
          <el-table-column prop="progress" label="进度" width="150">
            <template #default="scope">
              <el-progress 
                :percentage="scope.row.progress || 0" 
                :status="getProgressStatus(scope.row.status)"
                :stroke-width="10"
              />
            </template>
          </el-table-column>
          
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="scope">
              <div class="table-actions">
                <el-button 
                  size="small" 
                  class="custom-btn-primary" 
                  v-if="scope.row.status === 'completed'"
                  @click="viewResult(scope.row)"
                >
                  查看结果
                </el-button>
                
                <el-button 
                  size="small" 
                  class="action-btn"
                  v-if="['queued', 'processing'].includes(scope.row.status)"
                  @click="checkStatus(scope.row)"
                >
                  查看进度
                </el-button>
                
                <el-button 
                  size="small" 
                  class="action-btn-warning" 
                  v-if="scope.row.status === 'failed'"
                  @click="handleRetryAnalysis(scope.row)"
                >
                  重试
                </el-button>
                
                <el-button 
                  size="small" 
                  class="custom-btn-outline"
                  @click="showDeleteConfirm(scope.row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        
        <div class="pagination-container">
          <div class="batch-actions">
            <el-checkbox v-if="videoTasks.length > 0" v-model="selectAll" @change="toggleSelectAll" class="me-2">全选</el-checkbox>
            <span v-if="selectedRows.length > 0" class="selected-info">
              已选择 {{ selectedRows.length }} 项
            </span>
            <el-button 
              v-if="selectedRows.length > 0" 
              class="custom-btn-outline"
              size="small" 
              @click="handleBatchDelete"
            >
              批量删除
            </el-button>
          </div>
          <div class="pagination-wrapper">
            <div class="pagination-info">共 {{ total }} 个记录</div>
            <el-pagination
              background
              layout="prev, pager, next, sizes"
              :total="total"
              :page-size="pageSize"
              :current-page="currentPage"
              :page-sizes="[10, 20, 50, 100]"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
              prev-text="上一页"
              next-text="下一页"
            />
          </div>
        </div>
      </div>
    </el-card>
    
    <!-- 批量删除确认对话框 -->
    <el-dialog
      title="批量删除"
      v-model="batchDeleteDialogVisible"
      width="400px"
      class="dark-theme-dialog"
      :close-on-click-modal="false"
    >
      <span>确定要删除选中的{{ selectedRows.length }}条记录吗？此操作不可恢复！</span>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="confirm-delete-btn" @click="confirmBatchDelete" :loading="batchOperationLoading">
            确认删除
          </el-button>
          <el-button class="cancel-btn" @click="batchDeleteDialogVisible = false">取消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 单条记录删除确认对话框 -->
    <el-dialog
      title="确认删除"
      v-model="deleteDialogVisible"
      width="400px"
      class="dark-theme-dialog"
      :close-on-click-modal="false"
    >
      <span>确定要删除这条分析记录吗？此操作无法恢复。</span>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="confirm-delete-btn" @click="confirmDelete">
            确定删除
          </el-button>
          <el-button class="cancel-btn" @click="deleteDialogVisible = false">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, computed, watch, watchEffect, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { getVideoTaskList, retryVideoAnalysis, updateVideoName, getVideoResultId } from '@/api/video';
import { ElMessage } from 'element-plus';
import { Search, Edit, Delete, Plus, Refresh } from '@element-plus/icons-vue';
import { batchDeleteHistory } from '@/api/traffic';
import FileSaver from 'file-saver';
import * as XLSX from 'xlsx';
import { useStore } from 'vuex';
import apiClient from '@/utils/http-common'; // 导入apiClient
import { apiService } from '@/api'; // 导入apiService用于获取用户列表

export default {
  name: 'VideoHistory',
  components: {
    Search,
    Edit,
    Delete,
    Plus,
    Refresh
  },
  setup() {
    const router = useRouter();
    const store = useStore();
    const videoTasks = ref([]);
    const loading = ref(true);
    const tableLoading = ref(false);
    const error = ref('');
    
    // 分页参数
    const total = ref(0);
    const pageSize = ref(10);
    const currentPage = ref(1);
    
    // 排序参数
    const sortProp = ref('createdAt');
    const sortOrder = ref('descending');
    
    // 筛选参数
    const searchQuery = ref('');
    const userFilter = ref('');
    const userList = ref([]);
    const dateRange = ref([]);
    
    // 日期快捷选项
    const dateShortcuts = [
      {
        text: '最近一周',
        value: () => {
          const end = new Date();
          const start = new Date();
          start.setTime(start.getTime() - 3600 * 1000 * 24 * 7);
          return [formatToYYYYMMDD(start), formatToYYYYMMDD(end)];
        },
      },
      {
        text: '最近一个月',
        value: () => {
          const end = new Date();
          const start = new Date();
          start.setMonth(start.getMonth() - 1);
          return [formatToYYYYMMDD(start), formatToYYYYMMDD(end)];
        },
      },
      {
        text: '最近三个月',
        value: () => {
          const end = new Date();
          const start = new Date();
          start.setMonth(start.getMonth() - 3);
          return [formatToYYYYMMDD(start), formatToYYYYMMDD(end)];
        },
      }
    ];

    // 格式化日期为YYYY-MM-DD
    const formatToYYYYMMDD = (date) => {
      const year = date.getFullYear();
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const day = String(date.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    };
    
    // 选择相关
    const selectedRows = ref([]);
    const hasSelected = computed(() => selectedRows.value.length > 0);
    const selectAll = ref(false);
    const isIndeterminate = ref(false);
    
    // 批量操作相关
    const batchDeleteDialogVisible = ref(false);
    const batchOperationLoading = ref(false);
    
    // 单条删除相关
    const deleteDialogVisible = ref(false);
    const currentDeleteTask = ref(null);
    
    // 全选/取消全选
    const toggleSelectAll = (val) => {
      if (val) {
        // 获取当前页上的所有记录
        selectedRows.value = [...videoTasks.value];
      } else {
        selectedRows.value = [];
      }
      isIndeterminate.value = false;
    };
    
    // 用户权限相关计算属性
    const isAdmin = computed(() => {
      // 强制从localStorage获取最新用户信息
      try {
        const storedUser = JSON.parse(localStorage.getItem('user') || '{}');
        const role = storedUser?.role?.toLowerCase() || store.state.user?.role?.toLowerCase();
        return role === 'admin' || role === 'administrator';
      } catch (e) {
        console.error('解析用户角色出错:', e);
        const role = store.state.user?.role?.toLowerCase();
        return role === 'admin' || role === 'administrator';
      }
    });
    
    const currentUser = computed(() => {
      try {
        // 优先使用localStorage中的最新用户信息
        const storedUser = JSON.parse(localStorage.getItem('user') || '{}');
        if (storedUser && storedUser.id) {
          return storedUser;
        }
      } catch (e) {
        console.error('解析当前用户信息出错:', e);
      }
      return store.state.user;
    });
    
    // 监听筛选条件变化，重置页码并重新获取数据
    watch([searchQuery, userFilter, dateRange], () => {
      currentPage.value = 1;
      fetchVideoTasks();  // 自动刷新数据以反映筛选结果
    }, { deep: true });
    
    // 监听用户ID变化，立即重新获取数据
    watch(() => store.state.user?.id, (newUserId, oldUserId) => {
      if (newUserId !== oldUserId && newUserId) {
        console.log(`检测到用户变化: ${oldUserId} -> ${newUserId}，立即刷新数据`);
        // 重置分页
        currentPage.value = 1;
        // 重新获取数据
        fetchVideoTasks();
      }
    });
    
    // 视频名称相关
    const getVideoDisplayName = (task) => {
      // 添加调试日志
      console.log('获取显示名称，原始数据:', {
        taskId: task.taskId,
        task_id: task.task_id, 
        videoName: task.videoName,
        _id: task._id,
        hasVideoName: !!task.videoName
      });

      // 优先使用重命名后的videoName字段（如果存在且有内容）
      if (task.videoName && typeof task.videoName === 'string' && task.videoName.trim() !== '') {
        console.log('使用videoName:', task.videoName);
        return String(task.videoName);
      } 
      // 默认使用task_id作为显示名称
      else if (task.task_id) {
        console.log('使用task_id:', task.task_id);
        return task.task_id;
      }
      // 备选方案：如果没有task_id字段，使用其他ID字段
      else if (task.taskId) {
        console.log('使用taskId:', task.taskId);
        return task.taskId;
      }
      // 最后备选：使用其他ID字段
      else {
        const fallback = task._id || '';
        console.log('使用备选ID:', fallback);
        return fallback;
      }
    };
    
    // 开始重命名
    const startRename = (task) => {
      task.editing = true;
      task.editName = task.videoName || '';
    };
    
    // 确认重命名
    const confirmRename = async (task) => {
      try {
        tableLoading.value = true;
        
        // 调用API保存到服务器
        const response = await updateVideoName(task.taskId, task.editName);
        
        // 只要HTTP状态码是2xx，就认为成功
        if (response && response.status >= 200 && response.status < 300) {
          console.log('重命名成功，新名称:', task.editName);
          
          // 更新本地数据 - 只设置videoName字段
          task.videoName = task.editName;
          task.editing = false;
          ElMessage.success('视频重命名成功');
          
          // 强制刷新列表，确保显示最新数据
          setTimeout(() => {
            fetchVideoTasks();  
          }, 300);
        } else {
          throw new Error(response?.data?.message || '重命名失败');
        }
      } catch (err) {
        ElMessage.error('视频重命名失败: ' + (err.message || '未知错误'));
        console.error('重命名错误:', err);
      } finally {
        tableLoading.value = false;
        // 确保无论成功失败，都退出编辑模式
        if (task.editing) {
          task.editing = false;
        }
      }
    };
    
    // 获取视频任务列表
    const fetchVideoTasks = async () => {
      // 使用简化的用户认证检查
      if (!await ensureUserAuthenticated()) {
        error.value = '用户认证失败，请重新登录';
        loading.value = false;
        router.push('/login?redirect=' + encodeURIComponent(router.currentRoute.value.fullPath));
        return;
      }
      
      // 强制更新当前用户信息
      try {
        const storedUser = JSON.parse(localStorage.getItem('user') || '{}');
        if (storedUser && storedUser.id) {
          store.commit('SET_USER', storedUser);
          console.log(`强制更新用户信息: ${storedUser.username}, 角色: ${storedUser.role}`);
        }
      } catch (e) {
        console.error('更新用户信息出错:', e);
      }
      
      loading.value = true;
      error.value = '';
      
      try {
        // 构建查询参数
        const params = {
          page: currentPage.value - 1, // 后端从0开始计算页码
          pageSize: pageSize.value,
          sortBy: sortProp.value,
          sortOrder: sortOrder.value === 'ascending' ? 'asc' : 'desc',
          type: 'video' // 明确指定类型为video
        };
        
        if (searchQuery.value) {
          params.query = searchQuery.value;
        }
        
        // 添加用户筛选参数
        if (userFilter.value && isAdmin.value) {
          params.userId = userFilter.value;
          console.log(`筛选用户ID: ${userFilter.value}`);
        }
        
        // 改进日期范围筛选
        if (dateRange.value && dateRange.value.length === 2) {
          const startDate = dateRange.value[0];
          const endDate = dateRange.value[1];
          
          // 格式化日期并添加时间部分
          params.startDate = `${startDate} 00:00:00`;  // 开始日期的开始时刻
          params.endDate = `${endDate} 23:59:59`;    // 结束日期的结束时刻
          
          console.log(`筛选日期范围: ${params.startDate} 至 ${params.endDate} (字段: createdAt)`);
          ElMessage.info(`正在筛选日期范围: ${startDate} 至 ${endDate}`);
        }
        
        // 获取最新的用户状态
        const isCurrentAdmin = isAdmin.value;
        const currentUserInfo = currentUser.value;
        
        // 严格控制权限：非管理员用户必须添加用户ID过滤
        if (!isCurrentAdmin) {
          if (!currentUserInfo || !currentUserInfo.id) {
            throw new Error('无法获取当前用户信息，权限验证失败');
          }
          // 添加用户ID筛选条件，确保只查看自己的记录
          params.userId = currentUserInfo.id;
          params.user_id = currentUserInfo.id; // 添加这个兼容字段
          console.log(`用户权限: 普通用户 ${currentUserInfo.username}(${currentUserInfo.id})，只显示自己的记录`);
        } else {
          console.log(`用户权限: 管理员 ${currentUserInfo?.username || 'unknown'}，显示所有记录`);
        }
        
        // 添加更详细的日志
        console.log('请求参数:', params);
        console.log('当前用户信息:', currentUser.value);
        console.log('认证令牌前20字符:', localStorage.getItem('auth_token')?.substring(0, 20));
        
        const headers = {};
        const token = localStorage.getItem('auth_token');
        if (token) {
          headers['Authorization'] = `Bearer ${token}`;
        }
        
        const response = await getVideoTaskList(params, headers);
        
        console.log('后端原始响应数据:', response.data); // 添加日志记录原始响应
        
        if (response && response.data) {
          // 兼容多种返回格式
          let rawTasks = response.data.results || response.data.tasks || [];
          
          if (rawTasks.length === 0 && response.data.data) {
            // 处理可能的嵌套data对象
            rawTasks = response.data.data.results || response.data.data.tasks || [];
          }
          
          // 添加更详细的日志，输出第一条记录的完整数据
          if (rawTasks.length > 0) {
            console.log('第一条原始记录数据:', JSON.stringify(rawTasks[0]));
          }
          
          // 应用数据映射处理每一条记录
          videoTasks.value = rawTasks.map(task => mapTaskData(task));
          console.log(`处理后的视频任务: 共${videoTasks.value.length}条记录`, videoTasks.value);
          
          // 客户端权限二次验证，确保数据安全
          if (!isCurrentAdmin && currentUserInfo) {
            const userId = currentUserInfo.id;
            const beforeFilter = videoTasks.value.length;
            
            // 改进过滤逻辑，确保同时检查userId和user_id字段
            videoTasks.value = videoTasks.value.filter(task => {
              return task.userId === userId || task.user_id === userId;
            });
            
            const afterFilter = videoTasks.value.length;
            
            if (beforeFilter !== afterFilter) {
              console.warn(`权限问题: 服务器返回了未经授权的数据，已过滤 ${beforeFilter - afterFilter} 条记录`);
            }
            
            // 使用过滤后的实际记录数作为总数
            total.value = afterFilter;
          } else {
            // 管理员用户使用服务器返回的总数或应用额外的客户端筛选
            if (isCurrentAdmin && userFilter.value) {
              // 确保客户端也应用用户筛选，防止后端筛选未生效
              const beforeFilter = videoTasks.value.length;
              videoTasks.value = videoTasks.value.filter(task => task.userId === userFilter.value);
              const afterFilter = videoTasks.value.length;
              
              if (beforeFilter !== afterFilter) {
                console.log(`客户端额外筛选: 筛选用户ID ${userFilter.value}，从 ${beforeFilter} 条记录过滤到 ${afterFilter} 条`);
              }
              // 更新总数为实际过滤后的记录数
              total.value = afterFilter;
            } else {
              // 没有进行用户筛选或者服务端已正确处理
              total.value = response.data.total || videoTasks.value.length;
            }
          }
          
        } else {
          throw new Error('获取视频任务列表失败');
        }
      } catch (err) {
        error.value = err.message || '获取视频任务列表失败';
        console.error('获取视频任务列表错误:', err);
      } finally {
        loading.value = false;
      }
    };
    
    // 格式化日期
    const formatDate = (dateString) => {
      if (!dateString) return '未知';
      const date = new Date(dateString);
      return new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
      }).format(date);
    };
    
    // 获取状态类型
    const getStatusType = (status) => {
      if (status === 'completed') return 'success';
      if (status === 'processing') return 'primary';
      if (status === 'queued') return 'info';
      if (status === 'failed') return 'danger';
      return 'info';
    };
    
    // 获取状态文本
    const getStatusText = (status) => {
      if (status === 'completed') return '已完成';
      if (status === 'processing') return '处理中';
      if (status === 'queued') return '排队中';
      if (status === 'failed') return '失败';
      return '未知';
    };
    
    // 获取进度条状态
    const getProgressStatus = (status) => {
      if (status === 'completed') return 'success';
      if (status === 'failed') return 'exception';
      return '';
    };
    
    // 查看结果 - 使用后端智能ID转换接口
    const viewResult = async (task) => {
      try {
        console.log('查看结果，原始任务数据:', task);
        
        // 获取任务ID，优先使用UUID格式（taskId, task_id）
        const resultId = task.taskId || task.task_id || task.resultId || task.result_id || task.id || task._id;
        
        if (!resultId) {
          ElMessage.warning('没有找到有效的结果ID，无法查看结果');
          return;
        }
        
        // 记录ID信息用于调试
        console.log(`使用ID查询视频结果: ${resultId}, 格式: ${
          resultId.includes('-') ? 'UUID' : 
          (/^[0-9a-f]{24}$/i.test(resultId) ? 'MongoDB ObjectId' : '其他')
        }`);
        
        // 使用通用ID查询路径，让后端处理ID转换
        router.push(`/video-result/id/${resultId}`);
      } catch (error) {
        console.error('查看结果时出错:', error);
        ElMessage.error('查看结果失败: ' + error.message);
      }
    };

    // 辅助函数：确定ID的来源字段，用于调试
    const determineIdSource = (task) => {
      if (task.task_id) return 'task_id';
      if (task.taskId) return 'taskId';
      if (task.resultId) return 'resultId';
      if (task._id) return '_id';
      if (task.id) return 'id';
      return '未知';
    };
    
    // 查看状态
    const checkStatus = (task) => {
      router.push(`/video-status/${task.taskId}`);
    };
    
    // 重试分析 (函数名已改为handleRetryAnalysis，避免与API函数冲突)
    const handleRetryAnalysis = async (task) => {
      try {
        const response = await retryVideoAnalysis(task.taskId);
        
        if (response && response.data && response.data.success) {
          ElMessage.success('已重新提交分析任务');
          // 跳转到状态页面
          router.push(`/video-status/${task.taskId}`);
        } else {
          throw new Error('重新分析失败');
        }
      } catch (err) {
        ElMessage.error(err.message || '重新分析失败');
      }
    };
    
    // 显示删除确认对话框
    const showDeleteConfirm = (task) => {
      currentDeleteTask.value = task;
      deleteDialogVisible.value = true;
    };
    
    // 确认删除单条记录
    const confirmDelete = async () => {
      if (!currentDeleteTask.value) return;
      
      try {
        tableLoading.value = true;
        
        // 获取任务ID，优先使用MongoDB ObjectId格式
        const taskId = currentDeleteTask.value.taskId || 
                      currentDeleteTask.value.task_id || 
                      currentDeleteTask.value.id || 
                      currentDeleteTask.value._id;
        
        if (!taskId) {
          throw new Error('无法获取记录的ID');
        }
        
        console.log('正在删除任务:', taskId);
        console.log('ID格式分析:', {
          id: taskId, 
          is_uuid: taskId.includes('-'),
          is_objectid: /^[0-9a-f]{24}$/i.test(taskId),
          length: taskId.length
        });
        
        // 使用批量删除API，传递单个ID
        const response = await batchDeleteHistory([taskId], 'video');
        
        if (response && response.status >= 200 && response.status < 300) {
          ElMessage.success('删除成功');
          // 短暂延迟后刷新数据，确保后端处理完成
          setTimeout(() => {
            fetchVideoTasks();
          }, 300);
        } else {
          throw new Error(response?.data?.message || '删除失败');
        }
      } catch (err) {
        console.error('删除单条记录失败:', err);
        ElMessage.error('删除失败: ' + (err.message || '未知错误'));
      } finally {
        tableLoading.value = false;
        deleteDialogVisible.value = false;
        currentDeleteTask.value = null;
      }
    };
    
    // 处理行选择变化
    const handleSelectionChange = (rows) => {
      selectedRows.value = rows;
      
      // 更新全选状态
      const allSelected = rows.length === videoTasks.value.length && rows.length > 0;
      const partiallySelected = rows.length > 0 && rows.length < videoTasks.value.length;
      
      selectAll.value = allSelected;
      isIndeterminate.value = partiallySelected;
    };
    
    // 显示批量删除对话框
    const handleBatchDelete = () => {
      if (selectedRows.value.length === 0) {
        ElMessage.warning('请先选择要删除的记录');
        return;
      }
      batchDeleteDialogVisible.value = true;
    };
    
    // 确认批量删除
    const confirmBatchDelete = async () => {
      try {
        batchOperationLoading.value = true;
        
        // 获取所有选中行的ID，优先使用MongoDB ObjectId格式
        const taskIds = selectedRows.value.map(row => {
          return row.taskId || row.task_id || row.id || row._id;
        }).filter(id => id); // 确保没有undefined或null值
        
        if (taskIds.length === 0) {
          throw new Error('无法获取选中记录的ID');
        }
        
        console.log('正在批量删除任务:', taskIds);
        console.log('ID格式分析:', taskIds.map(id => ({
          id: id, 
          is_uuid: id.includes('-'),
          is_objectid: /^[0-9a-f]{24}$/i.test(id),
          length: id.length
        })));
        
        // 调用批量删除API，使用查询参数方式
        const response = await batchDeleteHistory(taskIds, 'video');
        console.log('批量删除响应:', response);
        
        // 基本的成功响应检查
        if (response && response.status >= 200 && response.status < 300) {
          ElMessage.success(`成功删除${taskIds.length}条记录`);
          batchDeleteDialogVisible.value = false;
          // 短暂延迟后刷新数据，确保后端处理完成
          setTimeout(() => {
            fetchVideoTasks();
          }, 300);
        } else {
          console.warn('批量删除返回值不符合预期:', response);
          throw new Error(response?.data?.message || '批量删除返回状态异常');
        }
      } catch (err) {
        console.error('批量删除失败:', err);
        ElMessage.error('批量删除失败: ' + (err.message || '未知错误'));
      } finally {
        batchOperationLoading.value = false;
        batchDeleteDialogVisible.value = false;
      }
    };
    
    // 导出数据为Excel
    const exportData = async () => {
      try {
        tableLoading.value = true;
        ElMessage.info('正在准备导出数据...');
        
        // 导出前强制刷新一次用户信息
        try {
          const storedUser = localStorage.getItem('user');
          if (storedUser) {
            const userInfo = JSON.parse(storedUser);
            // 每次请求前强制更新store中的用户信息
            store.commit('SET_USER', userInfo);
            console.log('导出前刷新用户信息：', userInfo.username, userInfo.id);
          }
        } catch (err) {
          console.warn('刷新用户信息失败:', err);
        }
        
        // 构建完整的导出数据，不受分页限制
        const exportParams = {
          page: 0,
          pageSize: 1000, // 导出更多数据
          sortBy: sortProp.value,
          sortOrder: sortOrder.value === 'ascending' ? 'asc' : 'desc'
        };
        
        if (searchQuery.value) {
          exportParams.query = searchQuery.value;
        }
        
        if (dateRange.value && dateRange.value.length === 2) {
          exportParams.startDate = dateRange.value[0];
          exportParams.endDate = dateRange.value[1];
          
          // 添加时间部分，确保完整的日期范围
          exportParams.startDate += ' 00:00:00';  // 开始日期的开始时刻
          exportParams.endDate += ' 23:59:59';    // 结束日期的结束时刻
          
          console.log('导出筛选日期范围:', exportParams.startDate, '至', exportParams.endDate);
        }
        
        // 确保导出数据时也应用相同的权限规则
        if (!isAdmin.value) {
          if (!currentUser.value || !currentUser.value.id) {
            throw new Error('无法获取当前用户信息，权限验证失败');
          }
          // 添加用户ID筛选条件，确保只导出自己的记录
          exportParams.userId = currentUser.value.id;
          exportParams.user_id = currentUser.value.id; // 添加兼容字段
          console.log(`导出权限: 普通用户 ${currentUser.value.username}(${currentUser.value.id})，只导出自己的记录`);
        } else {
          console.log('导出权限: 管理员，导出所有记录');
        }
        
        const response = await getVideoTaskList(exportParams);
        
        if (!response || !response.data || (!response.data.tasks && !response.data.results)) {
          throw new Error('获取导出数据失败');
        }
        
        // 修改获取导出数据的方式，适配后端返回的results字段
        let rawExportTasks = response.data.results || response.data.tasks || [];
        
        if (rawExportTasks.length === 0 && response.data.data) {
          // 处理可能的嵌套data对象
          rawExportTasks = response.data.data.results || response.data.data.tasks || [];
        }
        
        // 应用数据映射
        const exportTasks = rawExportTasks.map(task => mapTaskData(task));
        console.log(`处理后的导出任务: 共${exportTasks.length}条记录`);
        
        // 客户端权限二次验证，确保数据安全
        let filteredExportTasks = exportTasks;
        if (!isAdmin.value && currentUser.value) {
          const userId = currentUser.value.id;
          const beforeFilter = filteredExportTasks.length;
          
          // 改进过滤逻辑，同时检查userId和user_id
          filteredExportTasks = filteredExportTasks.filter(task => {
            return task.userId === userId || task.user_id === userId;
          });
          
          const afterFilter = filteredExportTasks.length;
          
          if (beforeFilter !== afterFilter) {
            console.warn(`导出权限问题: 服务器返回了未经授权的数据，已过滤 ${beforeFilter - afterFilter} 条记录`);
          }
          
          // 更新用户界面显示的导出数量
          ElMessage.success(`成功导出${afterFilter}条记录`);
        } else {
          // 管理员导出所有记录
          ElMessage.success(`成功导出${filteredExportTasks.length}条记录`);
        }
        
        // 将数据转换为Excel可用格式
        const exportData = filteredExportTasks.map(task => ({
          '视频名称': task.videoName || '未命名视频',
          '上传时间': formatDate(task.createdAt),
          '分析人': task.username || '未知用户',
          '状态': getStatusText(task.status),
          '进度': `${task.progress || 0}%`,
          '任务ID': task.taskId,
          '结果ID': task.resultId || ''
        }));
        
        // 创建工作表
        const worksheet = XLSX.utils.json_to_sheet(exportData);
        const workbook = XLSX.utils.book_new();
        XLSX.utils.book_append_sheet(workbook, worksheet, '视频分析历史');
        
        // 生成Excel文件并下载
        const excelBuffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' });
        const blob = new Blob([excelBuffer], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
        
        const fileName = `视频分析历史_${new Date().toISOString().slice(0, 10)}.xlsx`;
        FileSaver.saveAs(blob, fileName);
        
        // 这里不再需要通用提示，已在上方添加了个性化提示
      } catch (err) {
        ElMessage.error('导出失败: ' + (err.message || '未知错误'));
        console.error('导出错误:', err);
      } finally {
        tableLoading.value = false;
      }
    };
    
    // 处理搜索
    const handleSearch = () => {
      currentPage.value = 1; // 重置为第一页
      fetchVideoTasks();
    };
    
    // 处理排序变化
    const handleSortChange = (column) => {
      if (column.prop && column.order) {
        sortProp.value = column.prop;
        sortOrder.value = column.order;
        fetchVideoTasks();
      }
    };
    
    // 处理每页数量变化
    const handleSizeChange = (size) => {
      pageSize.value = size;
      fetchVideoTasks();
    };
    
    // 处理页码变化
    const handleCurrentChange = (page) => {
      currentPage.value = page;
      fetchVideoTasks();
    };
    
    // 重置筛选
    const resetFilters = () => {
      searchQuery.value = '';
      userFilter.value = '';
      dateRange.value = [];
      currentPage.value = 1; // 重置为第一页
      
      ElMessage.success('筛选条件已重置');
      fetchVideoTasks();
    };
    
    // 改进用户列表加载逻辑，确保正确获取用户数据
    const loadUserList = async () => {
      // 获取最新的用户角色
      const isCurrentAdmin = isAdmin.value;
      
      // 强化权限检查，只有管理员才尝试加载用户列表
      if (!isCurrentAdmin) {
        console.log('当前用户非管理员，跳过用户列表加载');
        return;
      }
      
      try {
        console.log('开始加载用户列表，当前用户角色:', currentUser.value?.role);
        
        // 防止重复加载
        if (userList.value.length > 0) {
          console.log('用户列表已加载，跳过重复加载');
          return;
        }
        
        // 使用与UserManagement.vue中相同的方法获取用户列表
        const response = await apiService.getUsers(1, 100); // 获取最多100个用户
        
        // 使用nextTick延迟处理数据，避免过快更新DOM
        await nextTick();
        
        if (response.data && response.data.data) {
          // 获取正确的嵌套数据结构
          const responseData = response.data.data;
          userList.value = responseData.items || [];
          
          // 添加用户列表加载验证
          console.log(`用户列表加载完成：${userList.value.length} 个用户`); 
        } else if (response.data) {
          // 兼容非嵌套结构
          userList.value = response.data.items || [];
          console.log(`用户列表加载完成(非嵌套结构)：${userList.value.length} 个用户`);
        }
      } catch (err) {
        // 对于403权限错误，不向用户显示错误提示
        if (err.response && err.response.status === 403) {
          console.warn('用户无权加载用户列表（权限不足）');
          return;
        }
        
        console.error('加载用户列表失败:', err);
        // 仅对管理员显示用户列表加载失败的提示
        if (isCurrentAdmin) {
          ElMessage.warning('无法加载用户列表');
        }
      }
    };
    
    // 添加数据映射处理函数
    const mapTaskData = (task) => {
      // 确保任务数据有正确的字段映射
      if (!task) return task;
      
      // 标准化字段
      const mappedTask = { ...task };
      
      // 记录原始字段，方便调试
      console.log('映射前原始字段:', Object.keys(task).join(', '));
      
      // MongoDB中，_id是ObjectId格式，task_id和result_id是UUID格式
      
      // 确保task_id字段存在 (UUID格式)
      if (!mappedTask.task_id && task.taskId && task.taskId.includes('-')) {
        mappedTask.task_id = task.taskId;
        console.log(`映射task_id: ${mappedTask.task_id}`);
      }
      
      // 确保前端组件可能仍然使用taskId和resultId的字段映射
      // taskId 是指向任务的主键，始终应该是UUID格式
      if (!mappedTask.taskId) {
        mappedTask.taskId = mappedTask.task_id || mappedTask.id || '';
        console.log(`ID映射: taskId=${mappedTask.taskId}`);
      }
      
      // resultId 应该是指向结果的ID，优先使用result_id，如果没有则使用taskId（通常它们是相同的）
      if (!mappedTask.resultId) {
        mappedTask.resultId = mappedTask.result_id || mappedTask.task_id || 
                             mappedTask.taskId || '';
        console.log(`ID映射: resultId=${mappedTask.resultId}`);
      }
      
      // 视频名称映射 - 处理多种可能的名称字段
      // 优先级：videoName > video_name > name > fileName > video_filename
      if (task.videoName && task.videoName.trim() !== '') {
        mappedTask.videoName = task.videoName;
        console.log(`使用videoName字段: ${mappedTask.videoName}`);
      } else if (task.video_name && task.video_name.trim() !== '') {
        mappedTask.videoName = task.video_name;
        console.log(`使用video_name字段: ${mappedTask.videoName}`);
      } else if (task.name && task.name.trim() !== '') {
        mappedTask.videoName = task.name;
        console.log(`使用name字段: ${mappedTask.videoName}`);
      } else if (task.fileName && task.fileName.trim() !== '') {
        mappedTask.videoName = task.fileName;
        console.log(`使用fileName字段: ${mappedTask.videoName}`);
      } else if (task.video_filename && task.video_filename.trim() !== '') {
        // 从文件名中提取有意义的部分
        const filename = task.video_filename;
        // 移除扩展名和前缀，保留可读部分
        const cleanName = filename.replace(/^video_\d+_/, '').replace(/\.\w+$/, '');
        mappedTask.videoName = cleanName;
        console.log(`从video_filename提取名称: ${mappedTask.videoName}`);
      }
      
      // 将所有可能的视频名称相关字段添加到日志中
      console.log('所有可能的名称字段:', {
        videoName: task.videoName,
        video_name: task.video_name,
        name: task.name,
        fileName: task.fileName,
        video_filename: task.video_filename
      });
      
      // 进度映射
      if (mappedTask.progress === undefined || mappedTask.progress === null) {
        mappedTask.progress = mappedTask.analysisProgress !== undefined ? 
                             mappedTask.analysisProgress : 
                             (mappedTask.status === 'completed' ? 100 : 0);
      }
      
      // 日期映射
      if (!mappedTask.createdAt) {
        mappedTask.createdAt = mappedTask.created_at || 
                              mappedTask.createTime || 
                              mappedTask.create_time || 
                              mappedTask.uploadTime || 
                              new Date().toISOString();
      }
      
      // 添加完成时间映射
      if (!mappedTask.completedAt) {
        mappedTask.completedAt = mappedTask.completed_at || 
                                mappedTask.updatedAt || 
                                mappedTask.updated_at || 
                                mappedTask.createdAt || 
                                mappedTask.created_at;
      }
      
      // 状态映射
      if (!mappedTask.status) {
        mappedTask.status = mappedTask.analysisStatus || 
                           mappedTask.state || 
                           mappedTask.task_status || 
                           'unknown';
      }
      
      // 确保用户ID字段 - 优先使用user_id
      if (!mappedTask.userId) {
        mappedTask.userId = mappedTask.user_id || 
                           mappedTask.uid || 
                           mappedTask.analyst_id || 
                           currentUser.value?.id || '';
      }
      
      // 补充用户名
      if (!mappedTask.username) {
        mappedTask.username = mappedTask.analyst || 
                             mappedTask.userName || 
                             mappedTask.user_name || 
                             currentUser.value?.username || 
                             '未知用户';
      }

      // 记录ID映射结果，用于调试
      console.log('ID映射结果:', {
        _id: mappedTask._id,                  // MongoDB ObjectId
        task_id: mappedTask.task_id,          // UUID
        result_id: mappedTask.result_id,      // UUID
        taskId: mappedTask.taskId,            // 前端使用
        resultId: mappedTask.resultId,         // 前端使用
        userId: mappedTask.userId,            // 用户ID
        user_id: mappedTask.user_id           // 原始用户ID
      });
      
      // 记录映射后的字段
      console.log('映射后字段:', Object.keys(mappedTask).join(', '));
      
      // 添加关键字段日志
      console.log('关键字段值:', {
        taskId: mappedTask.taskId,
        task_id: mappedTask.task_id, 
        videoName: mappedTask.videoName,
        fileName: mappedTask.fileName,
        status: mappedTask.status,
        createdAt: mappedTask.createdAt
      });
      
      return mappedTask;
    };
    
    // 简化用户认证管理的函数
    const ensureUserAuthenticated = async () => {
      // 检查用户状态是否已初始化
      if (!currentUser.value || !currentUser.value.id) {
        const storedUser = localStorage.getItem('user');
        const storedToken = localStorage.getItem('auth_token');
        
        if (storedUser && storedToken) {
          try {
            const userInfo = JSON.parse(storedUser);
            // 确保更新store中的用户信息
            store.commit('SET_USER', userInfo);
            console.log('从localStorage恢复用户信息成功:', userInfo.username, '角色:', userInfo.role);
            return true;
          } catch (err) {
            console.error('恢复用户信息出错:', err);
            return false;
          }
        } else {
          return false;
        }
      }
      
      // 即使当前有用户信息，也强制从localStorage刷新一次
      try {
        const storedUser = localStorage.getItem('user');
        if (storedUser) {
          const userInfo = JSON.parse(storedUser);
          // 仅当localStorage中的用户ID与当前不同时才更新
          if (userInfo.id !== currentUser.value.id) {
            console.log(`检测到用户变化: ${currentUser.value.username}(${currentUser.value.id}) -> ${userInfo.username}(${userInfo.id})`);
            store.commit('SET_USER', userInfo);
          }
        }
      } catch (err) {
        console.warn('刷新用户信息时出错:', err);
      }
      
      return true;
    };
    
    onMounted(() => {
      // 强制刷新用户状态
      try {
        const storedUser = JSON.parse(localStorage.getItem('user') || '{}');
        if (storedUser && storedUser.id) {
          store.commit('SET_USER', storedUser);
          console.log(`组件挂载时更新用户信息: ${storedUser.username}, 角色: ${storedUser.role}`);
        }
      } catch (e) {
        console.error('组件挂载时更新用户信息出错:', e);
      }

      // 立即获取视频任务
      fetchVideoTasks();
      
      // 延迟检查加载用户列表，确保用户信息已完全更新
      setTimeout(() => {
        // 获取最新的管理员状态
        const isCurrentAdmin = isAdmin.value;
        console.log(`用户角色检查: ${currentUser.value?.role || '未知'}, 是否管理员: ${isCurrentAdmin}`);
        
        if (isCurrentAdmin) {
          console.log('用户角色: admin，尝试加载用户列表');
          loadUserList();
        } else {
          console.log(`用户角色: ${currentUser.value?.role || '未知'}，无需加载用户列表`);
        }
      }, 100);
    });
    
    return {
      videoTasks,
      loading,
      tableLoading,
      error,
      total,
      pageSize,
      currentPage,
      sortProp,
      sortOrder,
      searchQuery,
      userFilter,
      userList,
      dateRange,
      selectedRows,
      hasSelected,
      selectAll,
      isIndeterminate,
      batchDeleteDialogVisible,
      batchOperationLoading,
      isAdmin,
      currentUser,
      fetchVideoTasks,
      formatDate,
      getStatusType,
      getStatusText,
      getProgressStatus,
      viewResult,
      checkStatus,
      handleRetryAnalysis,
      handleSearch,
      handleSortChange,
      handleSelectionChange,
      handleSizeChange,
      handleCurrentChange,
      handleBatchDelete,
      toggleSelectAll,
      confirmBatchDelete,
      exportData,
      getVideoDisplayName,
      startRename,
      confirmRename,
      loadUserList,
      resetFilters,
      dateShortcuts,
      formatToYYYYMMDD,
      deleteDialogVisible,
      showDeleteConfirm,
      confirmDelete,
      ensureUserAuthenticated
    };
  }
};
</script>

<style scoped>
.video-history-container {
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
  max-width: 1400px;
  margin: 20px auto;
  width: calc(100% - 40px);
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

.search-input {
  width: 200px !important;
}

/* 控制日期选择器宽度 */
.date-picker {
  width: 250px !important;
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

.refresh-btn {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: rgba(255, 255, 255, 0.9);
  height: 28px;
  padding: 0 10px;
  font-size: 0.85rem;
}

.refresh-btn:hover {
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

.loading-container,
.error-container,
.empty-container {
  padding: 20px;
  text-align: center;
  background: rgba(18, 25, 46, 0.95);
  border-radius: 4px;
  margin: 10px 0;
}

.error-actions {
  margin-top: 20px;
}

/* 表格样式 */
.custom-table {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  overflow: hidden;
}

:deep(.el-table) {
  background-color: transparent !important;
  color: #e5e7eb !important;
}

:deep(.el-table__header-wrapper) {
  background-color: rgba(22, 30, 49, 0.95) !important;
}

:deep(.el-table__header) {
  background-color: rgba(22, 30, 49, 0.95) !important;
  color: #ffffff !important;
}

:deep(.el-table__row) {
  background-color: rgba(25, 32, 50, 0.95) !important;
}

:deep(.el-table__row:hover),
:deep(.el-table__row.hover-row),
:deep(.el-table__row.current-row),
:deep(.el-table__row--striped.hover-row),
:deep(.el-table__row--striped.current-row),
:deep(.el-table tr.hover-row > td.el-table__cell) {
  background-color: rgba(25, 32, 50, 0.95) !important;
}

:deep(.el-table__row--striped) {
  background-color: rgba(22, 30, 49, 0.95) !important;
}

:deep(.el-table__row--striped:hover) {
  background-color: rgba(22, 30, 49, 0.95) !important;
}

/* 修复点击行后变白色的问题 */
:deep(.el-table__row:active),
:deep(.el-table__row--striped:active),
:deep(.el-table__row.current-row:active),
:deep(.el-table__row--striped.current-row:active) {
  background-color: rgba(22, 30, 49, 0.95) !important;
}

/* 增强单元格的背景色保持一致性 */
:deep(.el-table__cell) {
  background-color: inherit !important;
}

:deep(.el-table__cell:hover) {
  background-color: inherit !important;
}

/* 表格单元格样式 */
:deep(.el-table th) {
  background-color: rgba(22, 30, 49, 0.95) !important;
  color: rgba(255, 255, 255, 0.95) !important;
  font-weight: 600 !important;
  padding: 8px 0 !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
}

:deep(.el-table td) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.05) !important;
  color: rgba(229, 231, 235, 0.9) !important;
  padding: 10px 0 !important;
}

/* Disable highlight on table cells when row is hovered */
:deep(.el-table__body tr.hover-row > td.el-table__cell) {
  background-color: inherit !important;
}

/* 确保按钮点击后恢复正确的颜色 */
:deep(.custom-btn-outline:active),
:deep(.custom-btn-outline:focus) {
  background: rgba(239, 68, 68, 0.15) !important;
  color: #ffffff !important;
}

:deep(.custom-btn-outline:hover) {
  background: rgba(239, 68, 68, 0.25) !important;
  border-color: #ef4444 !important;
  color: #ffffff !important;
}

.video-name-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.video-name {
  margin-right: 10px;
  flex: 1;
  color: rgba(255, 255, 255, 0.95);
  font-weight: 500;
}

.rename-button {
  opacity: 0.6;
}

.video-name-container:hover .rename-button {
  opacity: 1;
}

.table-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

/* 分页容器样式 */
.pagination-container {
  margin-top: 15px;
  display: flex;
  justify-content: space-between;
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
  display: flex;
  align-items: center;
  gap: 10px;
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
  gap: 12px;
  width: 100%;
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
.custom-btn-primary {
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
}

.custom-btn-primary:hover {
  background-color: rgba(99, 90, 249, 0.9) !important;
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(79, 70, 229, 0.3) !important;
}

.action-btn {
  background: rgba(45, 55, 72, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.9);
  border-radius: 0.25rem;
  padding: 0.3rem 0.6rem;
  font-size: 0.85rem;
  transition: all 0.2s;
  height: 32px;
}

.action-btn:hover {
  background: rgba(55, 65, 82, 0.5);
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2);
}

.action-btn-warning {
  background: rgba(245, 158, 11, 0.2);
  border: 1px solid rgba(245, 158, 11, 0.4);
  color: #fbbf24;
  border-radius: 0.25rem;
  padding: 0.3rem 0.6rem;
  font-size: 0.85rem;
  transition: all 0.2s;
  height: 32px;
}

.action-btn-warning:hover {
  background: rgba(245, 158, 11, 0.3);
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(245, 158, 11, 0.3);
}

/* 标签样式 */
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

/* 输入框样式 */
:deep(.el-input .el-input__wrapper) {
  background-color: rgba(17, 24, 39, 0.3) !important;
  box-shadow: none !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}

:deep(.el-input .el-input__inner) {
  color: rgba(255, 255, 255, 0.9) !important;
}

/* 进度条样式 */
:deep(.el-progress-bar__outer) {
  background-color: rgba(255, 255, 255, 0.1) !important;
}

:deep(.el-progress-bar__inner) {
  background-color: rgba(79, 70, 229, 0.9) !important;
}

:deep(.el-progress--success .el-progress-bar__inner) {
  background-color: rgba(16, 185, 129, 0.9) !important;
}

:deep(.el-progress--exception .el-progress-bar__inner) {
  background-color: rgba(239, 68, 68, 0.9) !important;
}

/* 响应式优化 */
@media (max-width: 1200px) {
  .filter-row {
    flex-wrap: wrap;
  }
  
  .filter-actions {
    margin-left: 0;
    margin-top: 10px;
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .history-card {
    width: calc(100% - 20px);
    margin: 10px auto;
  }
  
  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .header-actions {
    margin-top: 10px;
    width: 100%;
  }
  
  .filter-row {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .filter-item {
    width: 100%;
    margin-bottom: 8px;
  }
  
  .filter-label {
    min-width: 60px;
  }
  
  .date-picker,
  .search-input {
    width: 100% !important;
  }
  
  .pagination-container {
    flex-direction: column;
    align-items: flex-start;
  }
  
  .pagination-wrapper {
    margin-top: 10px;
    width: 100%;
    justify-content: center;
  }
}

/* 装饰元素 */
.video-history-container::before {
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

.video-history-container::after {
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

.wider-checkbox {
  margin-right: 10px;
}

/* 自定义对话框样式 */
:deep(.dark-theme-dialog) {
  background: rgba(13, 18, 38, 0.98) !important;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.5) !important;
  border-radius: 0.75rem !important;
  border: 1px solid rgba(99, 102, 241, 0.2) !important;
  overflow: hidden !important;
}

:deep(.dark-theme-dialog .el-dialog__header) {
  background: rgba(22, 30, 55, 0.98) !important;
  color: #e5e7eb !important;
  border-bottom: 1px solid rgba(99, 102, 241, 0.15) !important;
  padding: 15px 20px !important;
}

:deep(.dark-theme-dialog .el-dialog__title) {
  color: #e5e7eb !important;
  font-weight: 600 !important;
  font-size: 1.1rem !important;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.2) !important;
}

:deep(.dark-theme-dialog .el-dialog__body) {
  background: rgba(13, 18, 38, 0.98) !important;
  color: #e5e7eb !important;
  padding: 24px 20px !important;
  font-size: 1rem !important;
  line-height: 1.5 !important;
}

:deep(.dark-theme-dialog .el-dialog__footer) {
  background: rgba(22, 30, 55, 0.98) !important;
  border-top: 1px solid rgba(99, 102, 241, 0.15) !important;
  padding: 15px 20px !important;
}

:deep(.dark-theme-dialog .el-dialog__headerbtn:hover .el-dialog__close) {
  color: #818cf8 !important;
}

:deep(.dark-theme-dialog .el-dialog__headerbtn .el-dialog__close) {
  color: #e5e7eb !important;
}

/* 修复按钮样式和比例问题 */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  width: 100%;
}

:deep(.cancel-btn) {
  background: rgba(31, 41, 65, 0.8) !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  color: #e5e7eb !important;
  min-width: 80px !important;
  flex: 0 0 auto !important;
}

:deep(.cancel-btn:hover) {
  background: rgba(55, 65, 95, 0.8) !important;
  border-color: rgba(255, 255, 255, 0.25) !important;
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.15) !important;
}

:deep(.confirm-delete-btn) {
  background: rgba(220, 38, 38, 0.15) !important;
  border: 1px solid rgba(239, 68, 68, 0.5) !important;
  color: #f87171 !important;
  min-width: 100px !important;
  flex: 0 0 auto !important;
  font-weight: 600 !important;
}

:deep(.confirm-delete-btn:hover) {
  background: rgba(220, 38, 38, 0.25) !important;
  color: #fca5a5 !important;
  border-color: rgba(239, 68, 68, 0.7) !important;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3) !important;
}
</style> 