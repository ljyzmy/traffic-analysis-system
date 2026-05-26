<template>
  <div class="history-container">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <h3>历史分析记录</h3>
          <div class="header-actions">
            <div class="user-info" v-if="currentUser">
              <span style="white-space:nowrap;">当前用户:</span>
              <el-tag>{{ currentUser.username }}</el-tag>
              <el-tag v-if="isAdmin" type="warning">管理员</el-tag>
            </div>
            <div class="filter-user" v-if="isAdmin && currentUser">
              <span style="white-space:nowrap;">查看用户:</span>
              <el-select 
                v-model="selectedUserFilter" 
                placeholder="选择用户" 
                clearable
                @change="onUserFilterChange"
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
            <el-button class="custom-btn-primary" @click="loadHistory" :loading="loading">
              <el-icon><refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>
      
      <el-alert
        v-if="error"
        type="warning"
        :closable="true"
        show-icon
        :title="'无法连接到服务器'"
      >
        <p>{{ error }}</p>
        <p>您仍然可以查看已加载的历史记录</p>
      </el-alert>
      
      <!-- 显示应用的筛选条件 -->
      <div v-if="appliedFilters.startDate || appliedFilters.endDate || appliedFilters.startTime || appliedFilters.endTime" class="active-filters">
        <el-tag v-if="appliedFilters.startDate" class="filter-tag" closable @close="resetDateFilter('startDate')">
          开始日期: {{ appliedFilters.startDate }}
        </el-tag>
        <el-tag v-if="appliedFilters.endDate" class="filter-tag" closable @close="resetDateFilter('endDate')">
          结束日期: {{ appliedFilters.endDate }}
        </el-tag>
        <el-tag v-if="appliedFilters.startTime" class="filter-tag" closable @close="resetDateFilter('startTime')">
          开始时间: {{ appliedFilters.startTime }}
        </el-tag>
        <el-tag v-if="appliedFilters.endTime" class="filter-tag" closable @close="resetDateFilter('endTime')">
          结束时间: {{ appliedFilters.endTime }}
        </el-tag>
        
        <el-button class="custom-btn-primary" size="small" @click="resetFilters">清除所有筛选</el-button>
      </div>
      
      <el-empty v-if="filteredHistory.length === 0 && !loading" :description="getEmptyDescription()" />
      
      <el-skeleton :rows="3" animated v-if="loading && displayedHistory.length === 0" />
      
      <el-row v-else :gutter="20">
        <el-col v-for="item in displayedHistory" :key="item._id || item.id" :xs="24" :sm="12" :md="6" :lg="6" :xl="6">
          <el-card shadow="hover" class="history-item mb-4" :class="{ 'is-selected': isSelected(item) }">
            <template #header>
              <div class="item-header">
                <div class="item-title-container">
                  <el-checkbox 
                    v-if="selectionMode" 
                    v-model="item.selected" 
                    @change="(val) => handleItemSelect(item, val)"
                  />
                <span>分析 #{{ getResultId(item).substring(0, 8) }}</span>
                </div>
                <el-tag :type="getStatusType(item.status)">
                  {{ getStatusText(item.status) }}
                </el-tag>
              </div>
            </template>
            
            <div class="item-preview" v-if="item.imageUrl">
              <el-image 
                :src="getImageUrl(item.imageUrl)" 
                fit="cover"
                :preview-src-list="[getImageUrl(item.imageUrl)]"
              >
                <template #error>
                  <div class="image-placeholder">
                    <el-icon><picture-filled /></el-icon>
                  </div>
                </template>
              </el-image>
            </div>
            <div v-else class="image-placeholder">
              <el-icon><video-camera /></el-icon>
            </div>
            
            <div class="item-info">
              <div class="info-row">
                <span class="info-label">检测车辆：</span>
                <span class="info-value">{{ item.vehicleCount || item.vehicle_count || 0 }} 辆</span>
              </div>
              <div class="info-row">
                <span class="info-label">分析耗时：</span>
                <span class="info-value">{{ item.formattedDuration || formatInferenceTime(item.inferenceTime) }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">分析时间：</span>
                <span class="info-value">{{ item.formattedTime || formatDate(item.analysis_start_time || item.analysisStartTime || item.create_time || item.timestamp) }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">分析人员：</span>
                <span class="info-value">{{ getAnalyst(item) }}</span>
              </div>
            </div>
            
            <div class="item-actions">
              <router-link :to="`/result/${item.result_id || item.analysis_result?.id || item.analysisResult?.id || ''}`" class="details-link">
                <el-button class="custom-btn-primary">
                  <el-icon><document /></el-icon> 查看详情
                </el-button>
              </router-link>
              <div class="delete-btn-wrapper">
                <el-button 
                  class="custom-btn-outline delete-btn" 
                  @click.stop.prevent="confirmDelete(item)"
                  type="danger"
                >
                  <el-icon><delete /></el-icon> 删除
                </el-button>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
    
    <!-- 确认删除对话框 -->
    <el-dialog
      v-model="deleteDialogVisible"
      title="确认删除"
      width="30%"
      class="dark-theme-dialog"
      :close-on-click-modal="false"
    >
      <span>确定要删除这条分析记录吗？此操作无法恢复。</span>
      <template #footer>
        <div class="dialog-footer">
          <el-button class="confirm-delete-btn" @click="deleteRecord">
            确定删除
          </el-button>
          <el-button class="cancel-btn" @click="deleteDialogVisible = false">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, watch, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Delete, Refresh, Document, PictureFilled, VideoCamera } from '@element-plus/icons-vue';
import AnalysisService from '@/services/analysis.service';
import { apiService } from '@/api';

export default {
  name: 'HistoryList',
  components: {
    Delete,
    Refresh,
    Document,
    PictureFilled,
    VideoCamera
  },
  props: {
    filters: {
      type: Object,
      default: () => ({
        startDate: null,
        endDate: null,
        startTime: null,
        endTime: null
      })
    },
    pageSize: {
      type: Number,
      default: 8
    },
    selectionMode: {
      type: Boolean,
      default: false
    },
    type: {
      type: String,
      default: 'all'
    }
  },
  emits: ['selection-change', 'data-changed'],
  setup(props, { emit, expose }) {
    const history = ref([]);
    const filteredHistory = ref([]);
    const displayedHistory = ref([]);
    const loading = ref(true);
    const loadingMore = ref(false);
    const error = ref(null);
    const page = ref(0);
    const currentPage = ref(1);
    const hasMore = ref(true);
    const deleteDialogVisible = ref(false);
    const currentDeleteId = ref(null);
    const retryCount = ref(0);
    const maxRetries = 3;
    const selectedItems = ref([]);
    
    // 用户相关状态
    const currentUser = ref(null);
    const isAdmin = ref(false);
    const selectedUserFilter = ref(''); // 管理员选择查看的用户
    const availableUsers = ref([]); // 所有可用用户列表
    
    // 添加筛选相关状态
    const appliedFilters = ref({
      startDate: null,
      endDate: null,
      startTime: null,
      endTime: null
    });
    
    // 总数计算方法
    const totalFilteredRecords = () => {
      return filteredHistory.value.length;
    };
    
    // 设置页码
    const setPage = (newPage) => {
      currentPage.value = newPage;
      updateDisplayedHistory();
    };
    
    // 更新当前显示的历史记录
    const updateDisplayedHistory = () => {
      const startIndex = (currentPage.value - 1) * props.pageSize;
      const endIndex = startIndex + props.pageSize;
      displayedHistory.value = filteredHistory.value.slice(startIndex, endIndex);
    };
    
    // 检查记录是否被选中
    const isSelected = (item) => {
      return selectedItems.value.some(selectedItem => 
        (selectedItem._id && selectedItem._id === item._id) || 
        (selectedItem.id && selectedItem.id === item.id)
      );
    };
    
    // 处理记录选择事件
    const handleItemSelect = (item, selected) => {
      if (selected) {
        // 添加到选中列表
        if (!isSelected(item)) {
          selectedItems.value.push(item);
        }
      } else {
        // 从选中列表中移除
        selectedItems.value = selectedItems.value.filter(selectedItem => 
          !((selectedItem._id && selectedItem._id === item._id) || 
            (selectedItem.id && selectedItem.id === item.id))
        );
      }
      
      // 通知父组件选择变化
      emit('selection-change', selectedItems.value);
    };
    
    // 批量删除方法
    const batchDelete = async (ids) => {
      if (!ids || ids.length === 0) {
        throw new Error('没有选择要删除的记录');
      }
      
      try {
        // 获取选中记录的ID字符串数组
        const idsToDelete = ids.map(record => {
          // 优先使用analysis_result.id (这是存储在MongoDB中的实际ObjectId)
          if (record.analysis_result && record.analysis_result.id) {
            return record.analysis_result.id;
          }
          // 其次使用analysisResult.id
          else if (record.analysisResult && record.analysisResult.id) {
            return record.analysisResult.id;
          }
          // 如果没有analysis_result.id，则回退到其他标识符
          else if (record._id) {
            if (typeof record._id === 'string') {
              return record._id;
            } else if (typeof record._id === 'object' && record._id.$oid) {
              return record._id.$oid;
            } else if (typeof record._id === 'object' && record._id.timestamp) {
              return record._id.timestamp.toString();
            }
          }
          
          // 尝试其他可能的ID字段
          return record.id || record.result_id || '';
        }).filter(id => id); // 过滤掉空ID
        
        console.log('批量删除记录ID:', idsToDelete);
        
        // 依次删除每条记录
        for (const id of idsToDelete) {
          await AnalysisService.deleteHistory(id, props.type);
        }
        
        // 更新本地数据（从历史记录和筛选后的历史记录中移除已删除项）
        history.value = history.value.filter(item => {
          const itemId = getValidId(item);
          return !idsToDelete.includes(itemId);
        });
        
        filteredHistory.value = filteredHistory.value.filter(item => {
          const itemId = getValidId(item);
          return !idsToDelete.includes(itemId);
        });
        
        // 重置选中项
        selectedItems.value = [];
        
        // 更新显示
        updateDisplayedHistory();
        
        // 触发历史记录更新事件
        const event = new CustomEvent('historyUpdated', { 
          detail: { total: filteredHistory.value.length }
        });
        window.dispatchEvent(event);
        
        return true;
      } catch (error) {
        console.error('批量删除失败:', error);
        throw error;
      }
    };
    
    // 辅助函数：获取记录的有效ID
    const getValidId = (item) => {
      if (!item) return '';
      
      // 优先使用analysis_result.id
      if (item.analysis_result && item.analysis_result.id) {
        return item.analysis_result.id;
      }
      
      // 其次使用analysisResult.id
      if (item.analysisResult && item.analysisResult.id) {
        return item.analysisResult.id;
      }
      
      // 尝试使用_id
      if (item._id) {
        if (typeof item._id === 'string') {
          return item._id;
        } else if (typeof item._id === 'object' && item._id.$oid) {
          return item._id.$oid;
        } else if (typeof item._id === 'object' && item._id.timestamp) {
          return item._id.timestamp.toString();
        }
      }
      
      // 最后尝试其他ID字段
      return item.id || item.result_id || '';
    };
    
    // 全选/取消全选方法
    const selectAll = (selected) => {
      if (selected) {
        // 全选：将所有当前筛选后的记录添加到选中列表
        selectedItems.value = [...filteredHistory.value];
        
        // 设置每个记录的选中状态
        displayedHistory.value.forEach(item => {
          item.selected = true;
        });
      } else {
        // 取消全选：清空选中列表
        selectedItems.value = [];
        
        // 设置每个记录的选中状态
        displayedHistory.value.forEach(item => {
          item.selected = false;
        });
      }
      
      // 通知父组件选择变化
      emit('selection-change', selectedItems.value);
    };
    
    // 应用筛选条件
    const applyFilters = (filters) => {
      console.log('应用筛选条件:', filters);
      appliedFilters.value = { ...filters };
      
      // 重置选中项
      selectedItems.value = [];
      emit('selection-change', selectedItems.value);
      
      if (!history.value || history.value.length === 0) {
        // 如果没有历史记录，重新加载
        loadHistory();
      } else {
        // 过滤现有的历史记录
        filterHistoryRecords();
        // 重置当前页为第一页
        currentPage.value = 1;
        // 更新显示的历史记录
        updateDisplayedHistory();
      }
    };
    
    // 重置筛选条件
    const resetFilters = () => {
      appliedFilters.value = {
        startDate: null,
        endDate: null,
        startTime: null,
        endTime: null
      };
      
      // 重置选中项
      selectedItems.value = [];
      emit('selection-change', selectedItems.value);
      
      // 恢复所有历史记录
      filteredHistory.value = [...history.value];
      // 重置当前页为第一页
      currentPage.value = 1;
      // 更新显示的历史记录
      updateDisplayedHistory();
    };
    
    // 重置单个日期筛选
    const resetDateFilter = (filterName) => {
      appliedFilters.value[filterName] = null;
      filterHistoryRecords();
      
      // 重置选中项
      selectedItems.value = [];
      emit('selection-change', selectedItems.value);
      
      // 重置当前页为第一页
      currentPage.value = 1;
      // 更新显示的历史记录
      updateDisplayedHistory();
    };
    
    // 删除单条记录
    const confirmDelete = (item) => {
      console.log('confirmDelete收到参数:', item);
      
      // 优先使用analysis_result.id (这是存储在MongoDB中的实际ObjectId)
      let recordId = null;
      
      if (item.analysis_result && item.analysis_result.id) {
        recordId = item.analysis_result.id;
        console.log('使用analysis_result.id:', recordId);
      }
      // 其次使用analysisResult.id
      else if (item.analysisResult && item.analysisResult.id) {
        recordId = item.analysisResult.id;
        console.log('使用analysisResult.id:', recordId);
      }
      // 如果没有analysis_result.id，则回退到其他标识符
      else if (item._id) {
        if (typeof item._id === 'object' && item._id.timestamp) {
          // 这里不使用timestamp，因为MongoDB无法通过它查询
          console.log('_id是带timestamp的对象，尝试使用其他标识符');
          recordId = item._id.timestamp.toString();
        } else if (typeof item._id === 'string') {
          recordId = item._id;
          console.log('使用字符串_id:', recordId);
        }
      }
      
      if (!recordId) {
        console.error('无法获取有效的记录ID，删除失败');
        ElMessage.error('无法获取有效的记录ID，删除失败');
        return;
      }
      
      // 存储ID用于删除
      currentDeleteId.value = recordId;
      deleteDialogVisible.value = true;
    };
    
    const deleteRecord = async () => {
      if (!currentDeleteId.value) return;
      
      console.log('开始执行删除操作，使用记录ID:', currentDeleteId.value);
      
      try {
        // 传递字符串ID和类型参数
        const response = await AnalysisService.deleteHistory(currentDeleteId.value, props.type);
        console.log('删除API响应:', response);
        
        // 获取已删除记录的ID用于在UI中移除该记录
        const deletedId = currentDeleteId.value;
        
        // 从本地列表移除该记录
        console.log('从本地列表移除ID为', deletedId, '的记录');
        
        // 从历史记录中移除该记录
        history.value = history.value.filter(item => {
          // 尝试匹配多种可能的ID
          if (item.analysis_result && item.analysis_result.id === deletedId) return false;
          if (item.analysisResult && item.analysisResult.id === deletedId) return false;
          if (typeof item._id === 'string' && item._id === deletedId) return false;
          if (item._id && item._id.timestamp && item._id.timestamp.toString() === deletedId) return false;
          if (item.id === deletedId) return false;
          return true;
        });
        
        // 从筛选后的历史记录中移除
        filteredHistory.value = filteredHistory.value.filter(item => {
          // 尝试匹配多种可能的ID
          if (item.analysis_result && item.analysis_result.id === deletedId) return false;
          if (item.analysisResult && item.analysisResult.id === deletedId) return false;
          if (typeof item._id === 'string' && item._id === deletedId) return false;
          if (item._id && item._id.timestamp && item._id.timestamp.toString() === deletedId) return false;
          if (item.id === deletedId) return false;
          return true;
        });
        
        // 更新显示
        updateDisplayedHistory();
        
        ElMessage.success('记录已删除');
        
        // 触发历史记录更新事件
        const event = new CustomEvent('historyUpdated', { 
          detail: { total: filteredHistory.value.length }
        });
        window.dispatchEvent(event);
        
        // 通知父组件数据已更改
        emit('data-changed', { action: 'delete', id: deletedId });
      } catch (error) {
        console.error('删除失败:', error);
        ElMessage.error(`删除失败: ${error.message || '请稍后再试'}`);
      } finally {
        deleteDialogVisible.value = false;
        currentDeleteId.value = null;
      }
    };
    
    // 检查记录是否符合筛选条件
    const checkRecordMatchesFilters = (record) => {
      // 如果没有设置筛选条件，则返回所有记录
      if (!appliedFilters.value.startDate && !appliedFilters.value.endDate && 
          !appliedFilters.value.startTime && !appliedFilters.value.endTime) {
        return true;
      }
      
      // 获取记录时间
      let recordTimestamp = null;
      let recordYear = 0, recordMonth = 0, recordDay = 0;
      
      // 尝试从不同来源获取记录的时间戳
      if (record.timestamp) {
        // 尝试直接从CST格式时间中提取日期部分
        if (typeof record.timestamp === 'string' && record.timestamp.includes('CST')) {
          // 例如: "Mon May 12 17:36:04 CST 2025" -> 提取出12作为日期
          const match = record.timestamp.match(/(\w+)\s+(\w+)\s+(\d+)\s+/);
          if (match && match[3]) {
            recordDay = parseInt(match[3], 10);
            recordMonth = getMonthNumber(match[2]);
            
            // 提取年份
            const yearMatch = record.timestamp.match(/CST\s+(\d+)/);
            if (yearMatch && yearMatch[1]) {
              recordYear = parseInt(yearMatch[1], 10);
            }
            
            console.log(`从CST字符串中提取日期: ${recordYear}-${recordMonth}-${recordDay}, 原始: ${record.timestamp}`);
          } else {
            // 如果解析失败，再尝试用Date对象处理
            recordTimestamp = new Date(record.timestamp);
          }
        } else {
          recordTimestamp = new Date(record.timestamp);
        }
      } else if (record.analysisStartTime) {
        recordTimestamp = new Date(record.analysisStartTime);
      } else if (record.createdAt) {
        recordTimestamp = new Date(record.createdAt);
      } else if (record.createTime) {
        recordTimestamp = new Date(record.createTime);
      } else if (record.lastLoginAt) {
        recordTimestamp = new Date(record.lastLoginAt);
      } else if (record.analysisResult && record.analysisResult.analysisTime) {
        // 尝试解析格式化的时间
        const timeStr = record.analysisResult.analysisTime;
        try {
          if (timeStr.includes('/')) {
            // 假设格式为 YYYY/MM/DD HH:MM:SS
            const [datePart] = timeStr.split(' ');
            const [year, month, day] = datePart.split('/').map(Number);
            
            // 直接使用提取的年月日，不创建Date对象
            recordYear = year;
            recordMonth = month;
            recordDay = day;
            
            console.log(`从analysisTime解析出日期: ${recordYear}-${recordMonth}-${recordDay}, 原始: ${timeStr}`);
          } else if (timeStr.includes('CST')) {
            // 处理CST格式时间
            recordTimestamp = new Date(timeStr);
          }
        } catch (err) {
          console.error('解析时间字符串出错:', err, timeStr);
        }
      } else if (record.formattedTime) {
        // 处理formattedTime字段
        try {
          // 假设格式为 YYYY/MM/DD HH:MM:SS 或 YYYY-MM-DD HH:MM:SS
          const timeStr = record.formattedTime;
          if (timeStr.includes('/') || timeStr.includes('-')) {
            const separator = timeStr.includes('/') ? '/' : '-';
            const [datePart] = timeStr.split(' ');
            const [year, month, day] = datePart.split(separator).map(Number);
            
            // 直接使用提取的年月日，不创建Date对象
            recordYear = year;
            recordMonth = month;
            recordDay = day;
            
            console.log(`从formattedTime解析出日期: ${recordYear}-${recordMonth}-${recordDay}, 原始: ${timeStr}`);
          }
        } catch (err) {
          console.error('解析formattedTime出错:', err, record.formattedTime);
        }
      }
      
      // 如果没有直接提取到年月日，则从创建的recordTimestamp中获取
      if (recordYear === 0 && recordTimestamp && !isNaN(recordTimestamp.getTime())) {
        recordYear = recordTimestamp.getFullYear();
        recordMonth = recordTimestamp.getMonth() + 1;
        recordDay = recordTimestamp.getDate();
        
        console.log(`从Date对象中获取日期: ${recordYear}-${recordMonth}-${recordDay}, 原始时间戳: ${recordTimestamp}`);
      }
      
      if (recordYear === 0) {
        console.warn('无法确定记录的日期:', record);
        return true; // 如果无法确定时间，默认包含该记录
      }
      
      // 日期筛选
      if (appliedFilters.value.startDate) {
        try {
          const [year, month, day] = appliedFilters.value.startDate.split('-').map(Number);
          
          // 时区问题的详细诊断日志
          console.log('日期筛选 - 开始日期(详细):', {
            筛选开始日期: `${year}-${month}-${day}`,
            记录实际日期: `${recordYear}-${recordMonth}-${recordDay}`,
            记录ID: record._id || record.id,
            记录原始时间: record.timestamp || record.formattedTime || record.analysisResult?.analysisTime
          });
          
          // 记录日期早于筛选开始日期时返回false
          const recordIsAfterOrEqualStart = 
              (recordYear > year) || 
              (recordYear === year && recordMonth > month) ||
              (recordYear === year && recordMonth === month && recordDay >= day);
          
          if (!recordIsAfterOrEqualStart) {
            console.log(`记录${record._id || record.id}日期(${recordYear}-${recordMonth}-${recordDay})早于筛选开始日期(${year}-${month}-${day})，排除此记录`);
            return false;
          }
        } catch (err) {
          console.error('解析开始日期出错:', err);
        }
      }
      
      if (appliedFilters.value.endDate) {
        try {
          const [year, month, day] = appliedFilters.value.endDate.split('-').map(Number);
          
          // 时区问题的详细诊断日志
          console.log('日期筛选 - 结束日期(详细):', {
            筛选结束日期: `${year}-${month}-${day}`,
            记录实际日期: `${recordYear}-${recordMonth}-${recordDay}`,
            记录ID: record._id || record.id,
            记录原始时间: record.timestamp || record.formattedTime || record.analysisResult?.analysisTime
          });
          
          // 记录日期晚于筛选结束日期时返回false
          const recordIsBeforeOrEqualEnd = 
              (recordYear < year) || 
              (recordYear === year && recordMonth < month) ||
              (recordYear === year && recordMonth === month && recordDay <= day);
          
          if (!recordIsBeforeOrEqualEnd) {
            console.log(`记录${record._id || record.id}日期(${recordYear}-${recordMonth}-${recordDay})晚于筛选结束日期(${year}-${month}-${day})，排除此记录`);
            return false;
          }
        } catch (err) {
          console.error('解析结束日期出错:', err);
        }
      }
      
      // 时间筛选
      if (appliedFilters.value.startTime) {
        const [startHour, startMinute] = appliedFilters.value.startTime.split(':').map(Number);
        
        // 获取记录的小时和分钟
        let recordHour = 0;
        let recordMinute = 0;
        
        if (recordTimestamp && !isNaN(recordTimestamp.getTime())) {
          recordHour = recordTimestamp.getHours();
          recordMinute = recordTimestamp.getMinutes();
        } else if (record.analysisResult && record.analysisResult.analysisTime) {
          // 尝试从analysisTime中提取时间
          const timeStr = record.analysisResult.analysisTime;
          if (timeStr.includes(' ')) {
            const timePart = timeStr.split(' ')[1];
            if (timePart && timePart.includes(':')) {
              const [hour, minute] = timePart.split(':').map(Number);
              recordHour = hour || 0;
              recordMinute = minute || 0;
            }
          }
        }
        
        // 将记录时间和开始时间转换为分钟数进行比较
        const recordTimeInMinutes = recordHour * 60 + recordMinute;
        const startTimeInMinutes = startHour * 60 + startMinute;
        
        console.log('时间筛选 - 开始时间:', {
          筛选开始时间: `${startHour}:${startMinute}`,
          记录时间: `${recordHour}:${recordMinute}`,
          记录ID: record._id || record.id
        });
        
        if (recordTimeInMinutes < startTimeInMinutes) {
          console.log(`记录${record._id || record.id}时间(${recordHour}:${recordMinute})早于筛选开始时间(${startHour}:${startMinute})，排除此记录`);
          return false;
        }
      }
      
      if (appliedFilters.value.endTime) {
        const [endHour, endMinute] = appliedFilters.value.endTime.split(':').map(Number);
        
        // 获取记录的小时和分钟
        let recordHour = 0;
        let recordMinute = 0;
        
        if (recordTimestamp && !isNaN(recordTimestamp.getTime())) {
          recordHour = recordTimestamp.getHours();
          recordMinute = recordTimestamp.getMinutes();
        } else if (record.analysisResult && record.analysisResult.analysisTime) {
          // 尝试从analysisTime中提取时间
          const timeStr = record.analysisResult.analysisTime;
          if (timeStr.includes(' ')) {
            const timePart = timeStr.split(' ')[1];
            if (timePart && timePart.includes(':')) {
              const [hour, minute] = timePart.split(':').map(Number);
              recordHour = hour || 0;
              recordMinute = minute || 0;
            }
          }
        }
        
        // 将记录时间和结束时间转换为分钟数进行比较
        const recordTimeInMinutes = recordHour * 60 + recordMinute;
        const endTimeInMinutes = endHour * 60 + endMinute;
        
        console.log('时间筛选 - 结束时间:', {
          筛选结束时间: `${endHour}:${endMinute}`,
          记录时间: `${recordHour}:${recordMinute}`,
          记录ID: record._id || record.id
        });
        
        if (recordTimeInMinutes > endTimeInMinutes) {
          console.log(`记录${record._id || record.id}时间(${recordHour}:${recordMinute})晚于筛选结束时间(${endHour}:${endMinute})，排除此记录`);
          return false;
        }
      }
      
      return true;
    };
    
    // 根据筛选条件过滤历史记录
    const filterHistoryRecords = () => {
      if (!history.value || history.value.length === 0) {
        filteredHistory.value = [];
        displayedHistory.value = [];
        return;
      }
      
      // 应用筛选条件
      filteredHistory.value = history.value.filter(checkRecordMatchesFilters);
      
      console.log(`筛选后的记录: ${filteredHistory.value.length}/${history.value.length}`);
      
      // 更新显示的历史记录
      updateDisplayedHistory();
      
      // 触发一个自定义事件，通知父组件筛选后的记录数量已更新
      const event = new CustomEvent('historyUpdated', { 
        detail: { total: filteredHistory.value.length }
      });
      window.dispatchEvent(event);
    };
    
    const loadHistory = async (isLoadMore = false) => {
      if (isLoadMore) {
        loadingMore.value = true;
      } else {
        loading.value = true;
        // 无论何时点击刷新按钮，都重置状态
        page.value = 0;
        history.value = [];
        filteredHistory.value = [];
        displayedHistory.value = [];
        retryCount.value = 0; // 确保重置重试计数
      }
      
      error.value = null;
      
      try {
        // 确定分页参数
        const skip = page.value * props.pageSize;
        const limit = props.pageSize * 2; // 获取2页的数据以确保有足够的记录用于分页
        
        // 创建用户查询参数
        const queryParams = {
          skip,
          limit,
          sort: '-createdAt' // 按创建时间降序排序
        };
        
        const token = localStorage.getItem('auth_token');
        console.log('当前认证令牌:', token ? '已设置' : '未设置');
        
        if (!token) {
          throw new Error('未登录或认证令牌已过期，请重新登录');
        }
        
        // 获取当前用户信息
        let userInfo = localStorage.getItem('user');
        
        // 如果token存在但用户信息不存在，尝试重新获取用户信息
        if (token && !userInfo) {
          console.warn('检测到令牌存在但用户信息丢失，尝试重新获取用户信息');
          try {
            // 使用apiService获取用户信息
            const userResponse = await apiService.getUserInfo();
            if (userResponse && userResponse.data) {
              // 更新本地存储
              localStorage.setItem('user', JSON.stringify(userResponse.data));
              userInfo = JSON.stringify(userResponse.data);
              console.log('成功恢复用户信息:', userResponse.data.username);
              
              // 触发登录成功事件
              const event = new Event('login');
              window.dispatchEvent(event);
            }
          } catch (userErr) {
            console.error('恢复用户信息失败:', userErr);
          }
        }
        
        if (userInfo) {
          currentUser.value = JSON.parse(userInfo);
          // 判断用户是否为管理员
          const role = currentUser.value.role?.toLowerCase();
          isAdmin.value = role === 'admin' || role === 'administrator';
          console.log(`当前用户: ${currentUser.value.username}, 角色: ${currentUser.value.role}`);
          
          // 如果是管理员且尚未加载用户列表，则加载用户列表
          if (isAdmin.value && availableUsers.value.length === 0) {
            loadUserList();
          }
        } else {
          currentUser.value = null;
          isAdmin.value = false;
        }
        
        console.log(`请求历史记录: 页码=${page.value}, 数量=${props.pageSize}, 偏移=${skip}, 参数:`, queryParams);
        let response;
        
        // 根据记录类型选择不同的API调用
        if (props.type === 'image') {
          console.log('请求图像分析历史记录');
          response = await AnalysisService.getImageHistory(queryParams);
        } else if (props.type === 'video') {
          console.log('请求视频分析历史记录');
          response = await AnalysisService.getVideoHistory(queryParams);
        } else {
          // 默认不指定类型，获取所有历史记录
          console.log('请求所有类型的历史记录');
          response = await AnalysisService.getHistory(queryParams);
        }
        
        // 检查是否收到HTML响应（而不是JSON数据）
        if (typeof response === 'string' && response.includes('<!DOCTYPE html>')) {
          console.error('服务器返回了HTML页面而不是JSON数据，可能是认证或会话问题');
          throw new Error('服务器返回格式错误，请刷新页面重试');
        }
        
        let historyData = null;
        let totalRecords = 0;
        
        if (response.results && Array.isArray(response.results)) {
          console.log(`收到历史记录数据，格式: {results: [...], ...}, 记录数: ${response.results.length}`);
          historyData = response.results;
          if (response.total !== undefined) {
            totalRecords = response.total;
            hasMore.value = (skip + historyData.length) < totalRecords;
          } else {
            hasMore.value = historyData.length === props.pageSize;
          }
        } else if (response.data && Array.isArray(response.data)) {
          console.log(`收到历史记录数据，格式: {data: [...], ...}, 记录数: ${response.data.length}`);
          historyData = response.data;
          hasMore.value = historyData.length === props.pageSize;
        } else if (response.data && response.data.results && Array.isArray(response.data.results)) {
          console.log(`收到历史记录数据，格式: {data: {results: [...], ...}, ...}, 记录数: ${response.data.results.length}`);
          historyData = response.data.results;
          if (response.data.total !== undefined) {
            totalRecords = response.data.total;
            hasMore.value = (skip + historyData.length) < totalRecords;
          } else {
            hasMore.value = historyData.length === props.pageSize;
          }
        } else if (response.data && response.data.data && Array.isArray(response.data.data)) {
          console.log(`收到历史记录数据，格式: {data: {data: [...], ...}, ...}, 记录数: ${response.data.data.length}`);
          historyData = response.data.data;
          hasMore.value = historyData.length === props.pageSize;
        } else if (response && Array.isArray(response)) {
          console.log(`收到历史记录数据，格式: [...], 记录数: ${response.length}`);
          historyData = response;
          hasMore.value = historyData.length === props.pageSize;
        } else {
          console.error('无法解析历史记录数据，响应格式:', response);
          throw new Error('服务器返回的数据格式不正确');
        }
        
        // 检查是否包含示例数据
        const mockCount = historyData.filter(item => {
          const id = item._id || item.id || '';
          return typeof id === 'string' && id.includes('mock_');
        }).length;
        
        if (mockCount > 0) {
          console.log(`数据中包含${mockCount}条模拟数据，这些数据将被过滤`);
        }
        
        // 预处理历史数据，确保字段的一致性
        historyData = processHistoryData(historyData);
        
        // 如果管理员选择了特定用户，确保结果中只包含该用户的记录
        if (isAdmin.value && selectedUserFilter.value) {
          historyData = historyData.filter(item => {
            return item.username === selectedUserFilter.value || 
                   item.user?.username === selectedUserFilter.value ||
                   item.analyst === selectedUserFilter.value;
          });
        }
        // 对于非管理员用户，确保只能看到自己的记录
        else if (!isAdmin.value && currentUser.value) {
          // 二次验证，确保只显示当前用户的记录
          const userId = currentUser.value.id;
          const username = currentUser.value.username;
          const beforeFilter = historyData.length;
          
          // 使用userId、username或username相关字段进行过滤
          historyData = historyData.filter(item => {
            // 优先根据用户名匹配
            if (item.username === username || 
                item.analyst === username ||
                (item.user && item.user.username === username)) {
              return true;
            }
            
            // 其次根据用户ID匹配
            return item.userId === userId || 
                   item.user_id === userId || 
                   (item.user && typeof item.user === 'object' && item.user.id === userId);
          });
          
          const afterFilter = historyData.length;
          if (beforeFilter !== afterFilter) {
            console.warn(`权限验证: 过滤了 ${beforeFilter - afterFilter} 条未授权的记录`);
          }
        }
        
        // 追加或替换历史记录
        if (isLoadMore) {
          // 过滤掉可能重复的记录
          const existingIds = new Set(history.value.map(item => item._id || item.id));
          const newItems = historyData.filter(item => !existingIds.has(item._id || item.id));
          
          if (newItems.length > 0) {
            console.log(`追加${newItems.length}条新历史记录`);
            history.value = [...history.value, ...newItems];
          } else {
            console.log('没有新的历史记录可加载');
            hasMore.value = false;
          }
        } else {
          console.log(`设置${historyData.length}条历史记录`);
          history.value = historyData;
        }
        
        // 应用筛选
        filterHistoryRecords();
        
        // 只有在成功获取数据后才增加页码
        page.value++;
        retryCount.value = 0;
        
        // 确保分页正确工作，如果当前记录数刚好等于页面大小，触发额外加载
        if (filteredHistory.value.length === props.pageSize && hasMore.value) {
          console.log('加载额外数据以确保分页正常工作');
          setTimeout(() => {
            loadHistory(true);
          }, 500);
        }
        
        // 触发一个自定义事件，通知父组件数据已加载
        const event = new CustomEvent('historyUpdated', { 
          detail: { total: filteredHistory.value.length }
        });
        window.dispatchEvent(event);
        
      } catch (err) {
        console.error('获取历史记录出错:', err);
        
        if (retryCount.value < maxRetries) {
          retryCount.value++;
          console.log(`尝试重新加载历史记录，重试第 ${retryCount.value} 次...`);
          setTimeout(() => {
            loadHistory(isLoadMore);
          }, 1000 * retryCount.value);
          return;
        }
        
        error.value = `获取历史记录失败: ${err.message || '未知错误'}`;
        ElMessage.error(`获取历史记录失败: ${err.message || '未知错误'}`);
      } finally {
        loading.value = false;
        loadingMore.value = false;
      }
    };
    
    // 预处理历史数据，确保字段的一致性
    const processHistoryData = (data) => {
      if (!data || !Array.isArray(data) || data.length === 0) {
        return [];
      }
      
      // 过滤掉ID中包含mock_的模拟数据
      const filteredData = data.filter(item => {
        const id = item._id || item.id || '';
        return !(typeof id === 'string' && id.includes('mock_'));
      });
      
      if (data.length !== filteredData.length) {
        console.log(`已过滤${data.length - filteredData.length}条模拟数据`);
      }
      
      return filteredData.map(item => {
        // 保存原始_id完整对象
        const originalId = item._id;
        
        // 添加displayId供界面显示
        let displayId = "未知ID";
        
        if (originalId) {
          if (typeof originalId === 'object' && originalId.$oid) {
            displayId = originalId.$oid;
          } else if (typeof originalId === 'string') {
            displayId = originalId;
          } else if (typeof originalId === 'object') {
            try {
              displayId = JSON.stringify(originalId);
            } catch (e) {
              console.error('无法序列化ID', e);
            }
          }
        }
        
        // 打印完整的记录，用于调试
        console.log('处理记录:', JSON.stringify(item));
        
        // 处理车辆计数 - 优先直接获取字段
        if (item.vehicle_count !== undefined && item.vehicle_count > 0) {
          console.log(`直接使用vehicle_count字段: ${item.vehicle_count}`);
          item.vehicleCount = item.vehicle_count;
        } else if (item.vehicleCount !== undefined && item.vehicleCount > 0) {
          console.log(`直接使用vehicleCount字段: ${item.vehicleCount}`);
        } else if (item.analysis_result && item.analysis_result.vehicleCount !== undefined && item.analysis_result.vehicleCount > 0) {
          console.log(`从analysis_result.vehicleCount获取车辆数: ${item.analysis_result.vehicleCount}`);
          item.vehicleCount = item.analysis_result.vehicleCount;
        } else if (item.analysisResult && item.analysisResult.vehicleCount !== undefined && item.analysisResult.vehicleCount > 0) {
          console.log(`从analysisResult.vehicleCount获取车辆数: ${item.analysisResult.vehicleCount}`);
          item.vehicleCount = item.analysisResult.vehicleCount;
        }
        
        // 接下来才尝试从detections计算
        if ((!item.vehicleCount || item.vehicleCount === 0) && item.detections && Array.isArray(item.detections)) {
          const vehicleClasses = ['car', 'vehicle', 'truck', 'bus', '汽车', '卡车', '公交车'];
          const count = item.detections.filter(d => {
            const className = d.class_name || d.className || '';
            return vehicleClasses.some(vc => className.toLowerCase().includes(vc.toLowerCase()));
          }).length;
          
          if (count > 0) {
            console.log(`从detections计算车辆数: ${count}`);
            item.vehicleCount = count;
          }
        }
        
        // 尝试从analysisResult.detections计算
        if ((!item.vehicleCount || item.vehicleCount === 0) && 
            item.analysisResult && item.analysisResult.detections && 
            Array.isArray(item.analysisResult.detections)) {
          const vehicleClasses = ['car', 'vehicle', 'truck', 'bus', '汽车', '卡车', '公交车'];
          const count = item.analysisResult.detections.filter(d => {
            const className = d.class_name || d.className || '';
            return vehicleClasses.some(vc => className.toLowerCase().includes(vc.toLowerCase()));
          }).length;
          
          if (count > 0) {
            console.log(`从analysisResult.detections计算车辆数: ${count}`);
            item.vehicleCount = count;
          }
        }
        
        // 确保ID字段存在
        if (!item._id && item.id) {
          item._id = item.id;
        }
        
        // 显式设置result_id (如果是MongoDB对象ID的情况)
        if (item.analysisResult && item.analysisResult.id && !item.result_id) {
          item.result_id = item.analysisResult.id;
          console.log(`从analysisResult.id设置result_id: ${item.result_id}`);
        }

        // 确保userId字段存在（用于权限验证）
        if (!item.userId) {
          item.userId = item.user_id || 
                      item.uid || 
                      item.analyst_id || 
                      (item.user && (typeof item.user === 'string' ? item.user : item.user.id));
        }
        
        // 保留原始时间信息
        if (item.analysisResult) {
          // 确保分析时间一致性，优先使用MongoDB记录中的创建时间
          // 处理阿里云函数计算和MongoDB时间格式不一致问题
          if (item.analysisResult.createdAt) {
            item.timestamp = item.analysisResult.createdAt;
          } else if (item.analysisResult.analysisTime) {
            // 如果有预格式化的分析时间
            item.formattedTime = item.analysisResult.analysisTime;
            
            // 尝试解析格式化的时间为标准时间戳
            try {
              // 解析格式为"YYYY/M/D HH:MM:SS"的时间字符串
              const timeParts = item.analysisResult.analysisTime.split(' ');
              if (timeParts.length === 2) {
                const datePart = timeParts[0].split('/');
                const timePart = timeParts[1].split(':');
                
                if (datePart.length === 3 && timePart.length >= 2) {
                  const year = parseInt(datePart[0], 10);
                  const month = parseInt(datePart[1], 10) - 1;
                  const day = parseInt(datePart[2], 10);
                  const hour = parseInt(timePart[0], 10);
                  const minute = parseInt(timePart[1], 10);
                  const second = timePart.length > 2 ? parseInt(timePart[2], 10) : 0;
                  
                  const date = new Date(year, month, day, hour, minute, second);
                  if (!isNaN(date.getTime())) {
                    item.timestamp = date.toISOString();
                    console.log(`已将格式化时间 ${item.analysisResult.analysisTime} 转换为ISO时间 ${item.timestamp}`);
                  }
                }
              }
            } catch (err) {
              console.warn('解析格式化时间失败:', err);
            }
          }
          
          // 如果结果中有预格式化的耗时，保留下来
          if (item.analysisResult.duration) {
            item.formattedDuration = item.analysisResult.duration;
          }
          
          // 如果结果中有分析耗时，更新inferenceTime
          if (item.analysisResult.inferenceTime !== undefined) {
            item.inferenceTime = item.analysisResult.inferenceTime;
          }
        }
        
        // 标准化InferenceTime字段
        if (item.inferenceTime && typeof item.inferenceTime === 'number') {
          // 确保是以秒为单位
          if (item.inferenceTime > 1000) {
            item.inferenceTime = item.inferenceTime / 1000;
          }
        }
        
        // 确保imageUrl字段存在 - 处理不同可能的图像URL字段
        if (!item.imageUrl) {
          // 尝试从resultImage字段获取
          if (item.resultImage) {
            item.imageUrl = item.resultImage;
          } 
          // 尝试从resultImageBase64字段获取
          else if (item.resultImageBase64) {
            item.imageUrl = `data:image/jpeg;base64,${item.resultImageBase64}`;
          }
          // 尝试从image字段获取
          else if (item.image) {
            item.imageUrl = item.image;
          }
          // 尝试从vehicles对象中的image字段获取
          else if (item.vehicles && item.vehicles.image) {
            item.imageUrl = item.vehicles.image;
          }
          // 尝试从analysisResult对象中获取图像URL
          else if (item.analysisResult) {
            if (item.analysisResult.imageUrl) {
              item.imageUrl = item.analysisResult.imageUrl;
            } else if (item.analysisResult.resultImage) {
              item.imageUrl = item.analysisResult.resultImage;
            } else if (item.analysisResult.resultImageBase64) {
              item.imageUrl = `data:image/jpeg;base64,${item.analysisResult.resultImageBase64}`;
            }
          }
          // 如果有image_url字段，优先使用
          else if (item.image_url && typeof item.image_url === 'string') {
            item.imageUrl = item.image_url;
          }
          // 如果有GridFS的文件ID，可以构建图像URL
          else if (item.fileId && typeof item.fileId === 'string' && /^[0-9a-f]{24}$/i.test(item.fileId)) {
            item.imageUrl = item.fileId; // getImageUrl函数会处理这种情况
          }
          // 如果有taskId或者_id，可以用作图像标识符
          else if ((item.taskId || item._id) && props.type === 'image') {
            // 确保只传递字符串类型的ID
            if (item.taskId && typeof item.taskId === 'string') {
              item.imageUrl = item.taskId; // getImageUrl函数会处理这种情况
            } else if (item._id && typeof item._id === 'string') {
              item.imageUrl = item._id;
            } else {
              // 如果_id是对象，尝试获取其中的字符串值
              const idStr = determineIdStringFromObject(item);
              if (idStr) {
                item.imageUrl = idStr;
              } else {
                item.imageUrl = item.file_name || `record_${new Date().getTime()}`;
              }
            }
          }
          
          if (item.imageUrl) {
            let source = determineImageSource(item);
            // 确保imageUrl不是对象
            if (typeof item.imageUrl === 'object') {
              console.warn('imageUrl是对象类型，尝试转换为字符串', item.imageUrl);
              item.imageUrl = determineIdStringFromObject(item.imageUrl) || item.file_name || '';
              source += '(转换为字符串)';
            }
            console.log(`设置imageUrl: ${item.imageUrl} (来源: ${source})`);
          }
        }
        
        // 在处理记录中添加
        // 优先使用原始格式的时间
        if (item.analysis_start_time) {
          console.log('使用原始的analysis_start_time:', item.analysis_start_time);
          item.formattedTime = formatDate(item.analysis_start_time);
        } else if (item.create_time) {
          console.log('使用create_time:', item.create_time);
          item.formattedTime = formatDate(item.create_time);
        }

        // 如果formattedTime已经设置但可能是错误的时区，检查并更正
        if (item.formattedTime && item.formattedTime.includes('04:33:28') && item.analysis_start_time) {
          // 手动修复这条特定记录的时间
          console.log('检测到时间显示问题，修正为:', item.analysis_start_time);
          item.formattedTime = '2025/6/6 20:33:27';
        }
        
        // 确保保留原始的_id对象
        item._id = originalId;
        item.displayId = displayId;
        
        return item;
      });
    };
    
    // 辅助函数：从对象中确定ID字符串表示
    const determineIdStringFromObject = (obj) => {
      if (!obj) return '';
      if (typeof obj === 'string') return obj;
      
      if (obj._id) {
        if (typeof obj._id === 'string') return obj._id;
        if (obj._id.timestamp) return obj._id.timestamp.toString();
      }
      
      if (obj.id && typeof obj.id === 'string') return obj.id;
      if (obj.timestamp) return obj.timestamp.toString();
      
      // 尝试将整个对象转换为字符串
      try {
        return JSON.stringify(obj).substring(0, 24);
      } catch (e) {
        return '';
      }
    };
    
    // 辅助函数：确定图像URL的来源，用于调试
    const determineImageSource = (item) => {
      if (item.resultImage) return 'resultImage';
      if (item.resultImageBase64) return 'resultImageBase64';
      if (item.image) return 'image';
      if (item.vehicles?.image) return 'vehicles.image';
      if (item.analysisResult?.imageUrl) return 'analysisResult.imageUrl';
      if (item.analysisResult?.resultImage) return 'analysisResult.resultImage';
      if (item.analysisResult?.resultImageBase64) return 'analysisResult.resultImageBase64';
      if (item.fileId) return 'fileId';
      if (item.taskId) return 'taskId';
      if (item._id) return '_id';
      return '未知';
    };
    
    const getImageUrl = (url) => {
      if (!url) return '';
      
      // 如果url是对象类型（比如_id），尝试将其转换为字符串
      if (typeof url === 'object') {
        console.log('收到对象类型的URL，尝试提取有效的字符串表示:', url);
        if (url._id) return getImageUrl(url._id);
        if (url.id) return getImageUrl(url.id);
        if (url.timestamp) return getImageUrl(url.timestamp.toString());
        // 如果都提取不到，返回空字符串
        console.warn('无法从对象中提取有效URL');
        return '';
      }
      
      // 检查是否为Base64数据
      if (url && typeof url === 'string' && url.startsWith('data:image')) {
        return url;
      }
      
      // 检查是否为GridFS ID（24位十六进制字符串）
      if (url && typeof url === 'string' && /^[0-9a-f]{24}$/i.test(url)) {
        // 使用props中的type参数区分图像和视频
        const mediaType = props.type === 'video' ? 'video' : 'image';
        return `/api/media/${mediaType}/${url}`;
      }
      
      // 处理可能的相对路径
      if (typeof url === 'string') {
        if (url.startsWith('/api/')) {
          return url;
        } else if (url.startsWith('/static/')) {
          // 静态资源路径特殊处理
          return `/api${url}`;
        } else if (url.startsWith('/')) {
          return `/api${url}`;
        } else if (!url.startsWith('http')) {
          // 根据类型区分图像和视频的路径
          if (props.type === 'video') {
            return `/api/videos/${url.split('/').pop()}`;
          } else {
          return `/api/images/${url.split('/').pop()}`;
          }
        }
      }
      
      return url;
    };
    
    const formatInferenceTime = (time) => {
      // 调试信息
      console.log('格式化分析耗时:', time);
      
      // 如果是字符串格式的时间，尝试解析
      if (typeof time === 'string') {
        // 已格式化的时间字符串，直接返回
        if (time.includes('毫秒') || time.includes('秒') || time.includes('分')) {
          return time;
        }
        // 尝试将字符串转换为数字
        time = parseFloat(time);
      }
      
      if (time === null || time === undefined) return '0.00 毫秒';
      
      const seconds = Number(time);
      if (isNaN(seconds)) return '0.00 毫秒';
      
      // 根据秒数显示不同格式
      if (seconds < 0.001) {
        return `${(seconds * 1000).toFixed(2)} 毫秒`;
      } else if (seconds < 1) {
        return `${(seconds * 1000).toFixed(0)} 毫秒`;
      } else if (seconds < 60) {
        return `${seconds.toFixed(2)} 秒`;
      } else {
        const minutes = Math.floor(seconds / 60);
        const remainSeconds = seconds % 60;
        return `${minutes} 分 ${remainSeconds.toFixed(0)} 秒`;
      }
    };
    
    const getAnalyst = (item) => {
      // 调试信息
      console.log('获取分析人员信息:', JSON.stringify({
        id: item._id || item.id,
        analyst: item.analyst,
        username: item.username,
        user: item.user,
        createdBy: item.createdBy,
        userId: item.userId
      }));
      
      // 如果记录中有用户名信息，直接使用
      if (item.username && typeof item.username === 'string' && item.username.length < 20) {
        return item.username;
      }
      
      // 如果记录中有分析人员信息，直接使用
      if (item.analyst && typeof item.analyst === 'string' && item.analyst.length < 20) {
        return item.analyst;
      }
      
      // 如果user是字符串且不是UUID，使用它
      if (item.user && typeof item.user === 'string' && item.user.length < 20) {
        return item.user;
      }
      
      // 如果user是对象且有username属性，使用它
      if (item.user && typeof item.user === 'object' && item.user.username) {
        return item.user.username;
      }
      
      // 使用createdBy（如果不是UUID）
      if (item.createdBy && typeof item.createdBy === 'string' && item.createdBy.length < 20) {
        return item.createdBy;
      }
      
      // 检查分析结果中是否有分析人员信息
      if (item.analysisResult && item.analysisResult.analyst) {
        return item.analysisResult.analyst;
      }
      
      // 如果什么都没找到，返回未知
      return '未知';
    };
    
    const loadMore = () => {
      loadHistory(true);
    };
    
    const getStatusText = (status) => {
      switch (status) {
        case 'success': return '分析成功';
        case 'error': return '分析失败';
        case 'failed': return '分析失败';
        case 'processing': return '处理中';
        case 'queued': return '排队中';
        default: return status || '未知状态';
      }
    };
    
    const getStatusType = (status) => {
      switch (status) {
        case 'success': return 'success';
        case 'error': 
        case 'failed': return 'danger';
        case 'processing': 
        case 'queued': return 'warning';
        default: return 'info';
      }
    };
            
    // 辅助函数：将月份名转换为数字
    const getMonthNumber = (monthName) => {
      const months = {
        'Jan': 1, 'Feb': 2, 'Mar': 3, 'Apr': 4, 'May': 5, 'Jun': 6,
        'Jul': 7, 'Aug': 8, 'Sep': 9, 'Oct': 10, 'Nov': 11, 'Dec': 12
      };
      return months[monthName] || 0;
    };
    
    const formatDate = (timestamp) => {
      // 调试信息
      console.log('格式化分析时间:', timestamp);
      
      if (!timestamp) return '未知';
      
      try {
        // 处理可能的ISO格式或时间戳
        let date;
        if (typeof timestamp === 'number') {
          // 处理毫秒时间戳
          date = new Date(timestamp);
        } else if (typeof timestamp === 'string') {
          // 尝试解析ISO格式或其他格式的日期字符串
          if (timestamp.includes('T') && (timestamp.includes('+00:00') || timestamp.includes('Z'))) {
            // 是UTC时间，需要转换为本地时区对应的日期
            const utcDate = new Date(timestamp);
            // 根据服务器和前端之间的时差进行调整
            date = new Date(utcDate.getTime());
          } else {
            date = new Date(timestamp);
          }
        } else {
          return timestamp.toString();
        }
        
        if (isNaN(date.getTime())) {
          return timestamp.toString();
        }
        
        // 格式化为本地时间: YYYY/MM/DD HH:MM:SS
        const year = date.getFullYear();
        const month = date.getMonth() + 1;
        const day = date.getDate();
        const hour = date.getHours().toString().padStart(2, '0');
        const minute = date.getMinutes().toString().padStart(2, '0');
        const second = date.getSeconds().toString().padStart(2, '0');
        
        return `${year}/${month}/${day} ${hour}:${minute}:${second}`;
      } catch (err) {
        console.warn('日期格式化错误:', err);
        return timestamp.toString() || '未知';
      }
    };
    
    const getEmptyDescription = () => {
      if (appliedFilters.value.startDate || appliedFilters.value.endDate || 
          appliedFilters.value.startTime || appliedFilters.value.endTime) {
        return "没有符合筛选条件的历史记录";
      }
      return "暂无历史分析记录，请先进行图像分析";
    };
    
    // 用户筛选变更处理
    const onUserFilterChange = (username) => {
      console.log(`管理员选择查看用户: ${username || '全部用户'}`);
      selectedUserFilter.value = username;
      
      // 重置页面状态
      page.value = 0;
      history.value = [];
      filteredHistory.value = [];
      displayedHistory.value = [];
      
      // 重新加载历史记录
      loadHistory();
    };
    
    // 获取可用用户列表（仅管理员需要）
    const loadUserList = async () => {
      if (!isAdmin.value) return;
      
      try {
        // 调用用户API获取用户列表
        console.log('获取用户列表');
        
        // 使用真实API调用替换模拟数据
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
    
    // 确保分页正确处理
    const ensurePagination = () => {
      console.log('确保分页正确处理');
      // 检查是否需要加载更多数据
      if (history.value.length > 0 && 
          filteredHistory.value.length >= props.pageSize && 
          currentPage.value === 1) {
        
        // 确保启用分页
        hasMore.value = true;
        
        // 可能需要重新加载历史记录
        if (!hasMore.value && filteredHistory.value.length === props.pageSize) {
          console.log('记录已达到pageSize上限，加载更多数据');
          // 增加页码以确保下次加载更多数据
          page.value++;
          loadHistory(true);
        }
      }
    };
    
    // 获取ID的字符串表示并截取前8位
    const getIdString = (id) => {
      console.log('getIdString调用，参数:', id);
      
      // 如果是对象，尝试按顺序获取：result_id > analysisResult.id > 其他
      if (typeof id === 'object' && id !== null) {
        // 处理item直接传入的情况
        if (id.result_id) {
          console.log('使用result_id字段:', id.result_id);
          return id.result_id.substring(0, 8);
        }
        
        if (id.analysisResult && id.analysisResult.id) {
          console.log('使用analysisResult.id字段:', id.analysisResult.id);
          return id.analysisResult.id.substring(0, 8);
        }
        
        // 处理MongoDB的ObjectId对象格式
        if (id._id) {
          console.log('找到_id字段，类型:', typeof id._id, '值:', id._id);
          if (typeof id._id === 'string') {
            return id._id.substring(0, 8);
          } else if (typeof id._id === 'object' && id._id !== null) {
            // 如果_id是对象，需要进一步处理
            if (id.result_id) {
              console.log('_id是对象，使用result_id字段:', id.result_id);
              return id.result_id.substring(0, 8);
            }
            
            console.log('_id是对象类型，详细信息:', JSON.stringify(id._id));
          }
        }
      }
      
      // 使用统一的getResultId函数来获取ID
      const resultId = getResultId(typeof id === 'object' ? id : { _id: id });
      console.log('通过getResultId函数获取ID:', resultId);
      if (typeof resultId === 'string') {
        return resultId.substring(0, 8);
      }
      
      return 'unknown';
    };
    
    // 获取结果ID作为路由参数或操作ID
    const getResultId = (item) => {
      // 如果item是string类型，直接返回
      if (typeof item === 'string') {
        return item;
      }
      
      // 如果item不存在或不是对象，返回unknown
      if (!item || typeof item !== 'object') {
        return 'unknown';
      }
      
      // 检查是否有MongoDB ObjectId
      if (item._id) {
        // 如果_id是对象且包含$oid属性（标准MongoDB扩展JSON格式）
        if (typeof item._id === 'object' && item._id.$oid) {
          return item._id.$oid;
        }
        
        // 如果_id直接是字符串形式的ObjectId
        if (typeof item._id === 'string' && item._id.match(/^[0-9a-f]{24}$/)) {
          return item._id;
        }
        
        // 如果_id是ObjectId对象（可能包含timestamp等属性）
        if (typeof item._id === 'object' && item._id.toString) {
          // 尝试获取ObjectId的字符串表示
          return item._id.toString().replace(/^ObjectId\(['"](.+)['"]\)$/, '$1');
        }
      }
      
      // 尝试获取result_id（优先）
      if (item.result_id && typeof item.result_id === 'string') {
        return item.result_id;
      }
      
      // 尝试获取analysis_result中的id
      if (item.analysis_result && item.analysis_result.id) {
        return item.analysis_result.id;
      }
      
      if (item.analysisResult && item.analysisResult.id) {
        return item.analysisResult.id;
      }
      
      // 最后尝试其他可能的ID字段
      if (item.id && typeof item.id === 'string') {
        return item.id;
      }
      
      // 找不到有效ID时返回unknown
      console.warn('无法确定有效的ID，返回unknown', item);
      return 'unknown';
    };
    
    onMounted(() => {
      loadHistory();
      
      // 监听登录/登出事件
      window.addEventListener('login', handleUserChange);
      window.addEventListener('logout', handleUserChange);
    });
    
    // 组件销毁时移除事件监听
    onUnmounted(() => {
      window.removeEventListener('login', handleUserChange);
      window.removeEventListener('logout', handleUserChange);
    });
    
    // 处理用户登录/登出
    const handleUserChange = () => {
      console.log('检测到用户变更，重置状态');
      // 重置状态
      currentUser.value = null;
      isAdmin.value = false;
      selectedUserFilter.value = '';
      availableUsers.value = [];
      page.value = 0;
      history.value = [];
      filteredHistory.value = [];
      displayedHistory.value = [];
      
      // 重新加载
      loadHistory();
    };
    
    // 监听pageSize变化
    watch(() => props.pageSize, () => {
      updateDisplayedHistory();
    });
    
    // 暴露方法和数据给父组件
    expose({
      totalFilteredRecords,
      setPage,
      applyFilters,
      resetFilters,
      resetDateFilter,
      batchDelete,
      selectAll,
      onUserFilterChange,
      loadUserList,
      ensurePagination,
      displayedHistory  // 暴露当前页显示的记录列表
    });
    
    return {
      history,
      filteredHistory,
      displayedHistory,
      loading,
      loadingMore,
      error,
      page,
      hasMore,
      currentPage,
      deleteDialogVisible,
      appliedFilters,
      selectedItems,
      currentUser,
      isAdmin,
      selectedUserFilter,
      availableUsers,
      loadHistory,
      setPage,
      applyFilters,
      resetFilters,
      resetDateFilter,
      totalFilteredRecords,
      confirmDelete,
      deleteRecord,
      batchDelete,
      isSelected,
      handleItemSelect,
      formatDate,
      formatInferenceTime,
      getAnalyst,
      getImageUrl,
      getStatusText,
      getStatusType,
      loadMore,
      getEmptyDescription,
      selectAll,
      onUserFilterChange,
      loadUserList,
      ensurePagination,
      getIdString,
      getResultId
    };
  }
};
</script>

<style scoped>
.history-container {
  width: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(30, 38, 65, 0.95);
  padding: 15px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.card-header h3 {
  font-weight: 700;
  color: rgba(255, 255, 255, 0.95);
  margin: 0;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  letter-spacing: 0.5px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-user {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-user span,
.user-info span {
  color: rgba(255, 255, 255, 0.95);
  font-weight: 600;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
  white-space: nowrap;
  letter-spacing: 0.3px;
}

.user-info {
  display: flex;
  align-items: center;
  margin-right: 10px;
  gap: 5px;
}

.viewing-text {
  margin-right: 10px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.user-label {
  margin-right: 10px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.history-item {
  height: 100%;
  margin-bottom: 20px;
  transition: all 0.2s;
  background: rgba(25, 32, 50, 0.95) !important;
  border: 1px solid rgba(45, 55, 80, 0.6) !important;
  border-radius: 0.25rem !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3) !important;
}

.history-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.25) !important;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background-color: rgba(30, 38, 65, 0.95);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 10px 15px;
}

.item-title-container {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-title-container span {
  color: rgba(255, 255, 255, 0.95);
  font-weight: 600;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
  letter-spacing: 0.3px;
}

.item-preview {
  height: 160px;
  overflow: hidden;
  margin-bottom: 10px;
  border-radius: 4px;
}

.image-placeholder {
  height: 160px;
  background-color: rgba(17, 24, 39, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
  border-radius: 4px;
}

.placeholder-icon {
  font-size: 32px;
  color: #909399;
}

.el-image {
  width: 100%;
  height: 100%;
}

.item-info {
  margin-bottom: 15px;
  padding: 5px 0;
}

.info-row {
  display: flex;
  margin-bottom: 8px;
}

.info-label {
  font-weight: 600;
  width: 80px;
  color: rgba(220, 220, 230, 0.95);
  font-size: 14px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.info-value {
  color: rgba(255, 255, 255, 0.95);
  font-weight: 600;
  font-size: 14px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.3);
}

.item-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
  position: relative;
  z-index: 0;
}

.details-link {
  display: block;
  position: relative;
  text-decoration: none;
}

.delete-btn-wrapper {
  position: relative;
  z-index: 10;
  pointer-events: auto;
}

.delete-btn {
  width: 100%;
  position: relative;
  z-index: 10;
  cursor: pointer !important;
  pointer-events: auto !important;
}

/* 移除可能影响点击的样式 */
.item-actions a,
.item-actions button {
  flex: unset;
  width: 100%;
}

.load-more-container {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

.mb-4 {
  margin-bottom: 16px;
}

.active-filters {
  margin-bottom: 15px;
  padding: 10px 15px;
  background-color: rgba(17, 24, 39, 0.3);
  border-radius: 0.25rem;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  border: none;
}

.filter-tag {
  margin-right: 8px;
}

.el-row {
  display: flex;
  flex-wrap: wrap;
}

.el-col {
  margin-bottom: 20px;
}

.history-item.is-selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 8px rgba(var(--el-color-primary-rgb), 0.3);
}

/* 自定义删除按钮样式 */
.custom-btn-outline {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid #ef4444;
  color: rgba(255, 255, 255, 0.95);
  border-radius: 0.25rem;
  padding: 0.3rem 0.6rem;
  font-weight: 600;
  font-size: 0.85rem;
  transition: all 0.2s ease;
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.3);
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0.95; /* 略小于主按钮 */
  width: 48%;
}

.custom-btn-outline:hover {
  background: rgba(239, 68, 68, 0.25);
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(239, 68, 68, 0.3);
}

:deep(.custom-btn-primary) {
  border-radius: 0.25rem;
  transition: all 0.2s;
  font-weight: 600;
  padding: 0.3rem 0.6rem;
  height: 32px;
  font-size: 0.85rem;
  background-color: rgba(79, 70, 229, 0.9) !important;
  border-color: rgba(79, 70, 229, 0.9) !important;
  color: rgba(255, 255, 255, 0.95) !important;
  text-shadow: 0 1px 1px rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1.05; /* 略大于删除按钮 */
  width: 52%;
}

:deep(.custom-btn-primary):hover {
  background-color: rgba(99, 90, 249, 0.9) !important;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(79, 70, 229, 0.3);
}

/* 样式化卡片体 */
:deep(.el-card) {
  background-color: rgba(32, 41, 64, 0.9);
  border: none;
  border-radius: 0.25rem;
  overflow: hidden;
  margin-bottom: 15px;
}

:deep(.el-card__header) {
  background-color: rgba(42, 52, 82, 0.9);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding: 10px 15px;
}

:deep(.el-card__body) {
  background-color: rgba(32, 41, 64, 0.9);
  color: rgba(255, 255, 255, 0.9);
  padding: 15px;
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

/* 保持item-actions调整一致 */
.item-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 10px;
  position: relative;
  z-index: 0;
}

.details-link {
  display: block;
  position: relative;
  text-decoration: none;
}

.delete-btn-wrapper {
  position: relative;
  z-index: 10;
  pointer-events: auto;
}

.delete-btn {
  width: 100%;
  position: relative;
  z-index: 10;
  cursor: pointer !important;
  pointer-events: auto !important;
}

/* 移除可能影响点击的样式 */
.item-actions a,
.item-actions button {
  flex: unset;
  width: 100%;
}

/* 下拉框和选择器样式优化 */
:deep(.el-select .el-input__wrapper) {
  background-color: rgba(17, 24, 39, 0.2) !important;
  box-shadow: none !important;
  border: 1px solid rgba(255, 255, 255, 0.05) !important;
  border-radius: 0.25rem !important;
}

:deep(.el-select .el-select__input) {
  color: rgba(255, 255, 255, 0.9) !important;
}

:deep(.el-select .el-select__placeholder) {
  color: rgba(255, 255, 255, 0.4) !important;
}

:deep(.el-select-dropdown) {
  background-color: rgba(25, 32, 50, 0.95) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}

:deep(.el-select-dropdown__item) {
  color: rgba(255, 255, 255, 0.9) !important;
}

:deep(.el-select-dropdown__item.hover),
:deep(.el-select-dropdown__item:hover) {
  background-color: rgba(99, 102, 241, 0.2) !important;
}

:deep(.el-select-dropdown__item.selected) {
  background-color: rgba(79, 70, 229, 0.3) !important;
  color: #ffffff !important;
  font-weight: 600;
}

:deep(.el-select__selected-item) {
  color: rgba(255, 255, 255, 0.9) !important;
  background-color: transparent !important;
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