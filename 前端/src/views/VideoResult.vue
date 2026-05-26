<template>
  <div class="video-result-container">
    <div class="page-header">
      <h2>视频分析结果</h2>
      <p class="sub-title">查看交通视频分析详情</p>
    </div>

    <el-card v-loading="loading">
      <div v-if="error" class="error-container">
        <el-empty 
          description="获取结果失败" 
          :image-size="120"
        >
          <template #description>
            <p>{{ error }}</p>
          </template>
          <el-button type="primary" @click="$router.push('/video-upload')">返回上传</el-button>
        </el-empty>
      </div>

      <template v-else-if="result">
        <div class="video-info-card">
          <h3>视频信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="文件名">
              <div class="video-name-container">
                <span v-if="!editing">{{ result.video_info?.filename || result.video_filename }}</span>
                <el-input 
                  v-else 
                  v-model="editName" 
                  size="small" 
                  @keyup.enter="confirmRename"
                />
                <el-button 
                  v-if="!editing"
                  type="primary" 
                  size="small" 
                  circle 
                  @click="startRename"
                  class="rename-button"
                >
                  <el-icon><edit /></el-icon>
                </el-button>
                <el-button 
                  v-else 
                  type="success" 
                  size="small" 
                  circle
                  @click="confirmRename"
                  :loading="renaming"
                >
                  <el-icon><check /></el-icon>
                </el-button>
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="方向">
              {{ result.direction === 'horizontal' ? '横向 (东西方向)' : '纵向 (南北方向)' }}
            </el-descriptions-item>
            <el-descriptions-item label="视频时长">
              {{ result.video_info?.duration_seconds ? (Number(result.video_info.duration_seconds.toFixed(1)) % 1 === 0 ? Math.floor(result.video_info.duration_seconds) : result.video_info.duration_seconds.toFixed(1)) + ' 秒' : '计算中...' }}
            </el-descriptions-item>
            <el-descriptions-item label="处理时间">
              {{ result.processing_time ? result.processing_time.toFixed(2) + ' 秒' : '数据不可用' }}
            </el-descriptions-item>
            <el-descriptions-item label="分析模式">
              {{ result.mode === 'intersection' ? '十字路口分析' : '单向分析' }}
            </el-descriptions-item>
            <el-descriptions-item label="车辆统计">
              总计: {{ calculateTotalVehicles() }} 辆
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <el-tabs v-model="activeTab" class="result-tabs">
          <el-tab-pane label="分析摘要" name="summary">
            <div class="summary-tab">
              <!-- 添加车辆类型统计卡片 -->
              <VehicleStatsCard 
                v-if="result && result.vehicle_type_stats" 
                :vehicleTypeStats="result.vehicle_type_stats" 
              />
              
              <!-- 视频分析图表组件 -->
              <VideoAnalyticsPanel :result="result" />
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="分析视频" name="video">
            <div class="video-tab">
              <!-- 视频播放器组件 -->
              <VideoPlayerPanel 
                v-if="result.status === 'completed' && result.resultPath"
                :video-path="getVideoUrl(result.resultPath || result.result_path)"
                :status="result.status"
                :progress="result.progress || 0"
                @screenshot="handleScreenshot"
              />
              <div v-else class="video-not-available">
                <el-empty description="视频分析未完成或视频不可用">
                  <template #description>
                    <p>当前状态: {{ getStatusText(result.status) }}</p>
                  </template>
                </el-empty>
              </div>
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="优化报告" name="report">
            <div class="report-tab">
              <!-- 视频报告组件 -->
              <VideoReportPanel 
                :result="result" 
                :report-url="result.reportUrl || result.report_url || ''" 
                :video-path="result.video_info?.filename || ''"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-card>
  </div>
</template>

<script>
import { ref, onMounted, onBeforeMount, onUnmounted, watch, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getVideoResult, updateVideoName, saveVideoProcessingTime } from '@/api/video'
import { refreshAuthToken } from '@/utils/http-common'
import { ElMessage } from 'element-plus'
import { 
  Edit,
  Check
} from '@element-plus/icons-vue'
import { STOMP_TOPIC_PREFIX } from '@/config'
import stompService from '@/utils/stomp-service'

// 导入组件
import VideoPlayerPanel from '@/components/video/VideoPlayerPanel.vue'
import VideoAnalyticsPanel from '@/components/video/VideoAnalyticsPanel.vue'
import VideoReportPanel from '@/components/video/VideoReportPanel.vue'
import VehicleStatsCard from '@/components/video/VehicleStatsCard.vue'

export default {
  name: 'VideoResult',
  components: {
    VideoPlayerPanel,
    VideoAnalyticsPanel,
    VideoReportPanel,
    VehicleStatsCard,
    Edit,
    Check
  },
  props: {
    idType: {
      type: String,
      default: null
    }
  },
  setup(props) {
    const route = useRoute()
    const resultId = ref(route.params.id)
    const loading = ref(true)
    const error = ref(null)
    const result = ref(null)
    const activeTab = ref('summary')
    const editing = ref(false)
    const editName = ref('')
    const renaming = ref(false)
    
    // 直接修改表格样式的函数
    const forceTableStyles = () => {
      // 给样式一些时间应用
      setTimeout(() => {
        // 获取所有表格标签单元格并应用样式
        const labelCells = document.querySelectorAll('.el-descriptions__cell.el-descriptions__label');
        labelCells.forEach(cell => {
          cell.setAttribute('style', 'background-color: rgba(26, 32, 50, 0.8) !important; color: #e5e7eb !important; font-weight: 600 !important; border-color: rgba(255, 255, 255, 0.1) !important;');
        });
        
        // 获取所有表格内容单元格并应用样式
        const contentCells = document.querySelectorAll('.el-descriptions__cell.el-descriptions__content');
        contentCells.forEach(cell => {
          cell.setAttribute('style', 'background-color: rgba(31, 41, 55, 0.8) !important; color: #e5e7eb !important; border-color: rgba(255, 255, 255, 0.1) !important;');
        });
        
        console.log('强制应用表格样式完成');
      }, 500);
    };
    
    // WebSocket相关
    let reconnectTimer = null
    let wsSubscription = null
    let pollInterval = null // 轮询定时器
    
    // 添加Blob URL管理
    const blobUrls = new Set() // 存储创建的所有Blob URLs
    
    // 添加最大重连次数计数器
    const maxReconnectAttempts = ref(5); // 最大重连次数
    const reconnectAttempts = ref(0); // 当前重连次数
    
    // 状态文本映射
    const getStatusText = (status) => {
      if (status === 'completed') return '已完成';
      if (status === 'processing') return '处理中';
      if (status === 'queued') return '排队中';
      if (status === 'failed') return '失败';
      return '未知';
    };

    // 清除所有状态数据
    const resetState = () => {
      // 清理WebSocket连接
      cleanupWebSocket();
      
      // 清理轮询定时器
      clearPolling();
      
      // 清理Blob URLs
      clearBlobUrls();
      
      // 重置状态
      result.value = null;
      error.value = null;
    };
    
    // 清理WebSocket资源
    const cleanupWebSocket = () => {
      if (wsSubscription) {
        try {
          // 使用stompService取消订阅
          const taskId = result.value?.taskId || resultId.value;
          if (taskId) {
            stompService.unsubscribe(`task/${taskId}`);
        }
        wsSubscription = null;
      } catch (err) {
          console.warn('取消WebSocket订阅失败:', err);
        }
      }
      
      // 清除重连定时器
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
      }
      
      // 重置重连计数
      reconnectAttempts.value = 0;
    };
    
    // 检查STOMP连接状态
    const checkStompConnection = () => {
      // 检查STOMP服务是否已连接
      if (!stompService.connected) {
        console.log('STOMP服务未连接，尝试初始化连接...');
        
        // 尝试初始化STOMP连接
        stompService.init().catch(error => {
          console.error('初始化STOMP连接失败:', error);
          // 启动轮询作为备份
          startPolling();
        });
      }
    };
    
    // 清理轮询定时器
    const clearPolling = () => {
      if (pollInterval) {
        clearInterval(pollInterval);
        pollInterval = null;
      }
    };
    
    // 清理Blob URLs
    const clearBlobUrls = () => {
        blobUrls.forEach(url => {
        try {
          URL.revokeObjectURL(url);
      } catch (err) {
          console.warn('撤销Blob URL失败:', err);
        }
      });
      blobUrls.clear();
    };
    
    // 清理所有资源
    const cleanupResources = () => {
      // 清理WebSocket
      cleanupWebSocket();
      
      // 清理轮询定时器
      clearPolling();
      
      // 清理Blob URLs
      clearBlobUrls();
      
      // 移除事件监听器
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
    
    // 启动轮询机制，作为WebSocket的备份
    const startPolling = () => {
      // 先清除可能存在的轮询
      clearPolling();
      
      // 如果视频已完成处理，不需要轮询
      if (result.value && result.value.status === 'completed') {
        console.log('视频已处理完成，无需轮询');
        return;
      }
      
      console.log('启动状态轮询作为WebSocket的备份机制');
      
      // 设置轮询间隔（每10秒）
      pollInterval = setInterval(() => {
        console.log('轮询检查视频处理状态...');
        fetchResult(false); // 传入false表示这是轮询调用，减少日志输出
      }, 10000);
    };
    
    // 初始化WebSocket连接
    const initWebSocket = async (taskId) => {
      if (!taskId) {
        console.warn('任务ID为空，无法初始化WebSocket');
        return;
      }
      
      // 先清理现有连接
      cleanupWebSocket();
      
      try {
        console.log(`初始化视频结果WebSocket连接，订阅任务: ${taskId}`);
        
        // 检查STOMP连接状态，确保连接已建立
        if (!stompService.connected) {
          console.log('STOMP连接未建立，尝试初始化连接...');
          try {
            await stompService.init();
            console.log('STOMP连接已成功初始化');
          } catch (connError) {
            console.error('STOMP连接初始化失败:', connError);
            // 连接失败，启动轮询作为备份
            startPolling();
            return; // 退出订阅尝试
          }
        }
            
        // 使用STOMP服务初始化并订阅
        const topic = `task/${taskId}`;
        console.log(`订阅主题: ${STOMP_TOPIC_PREFIX}/${topic}`);
            
        // 订阅主题
        stompService.subscribe(topic, (data) => {
          handleWebSocketMessage({ body: JSON.stringify(data) });
        }).then(subscription => {
          console.log('WebSocket订阅成功');
          wsSubscription = subscription;
          reconnectAttempts.value = 0; // 重置重连计数
          
          // WebSocket连接成功后，仍然启动轮询作为备份
          startPolling();
        }).catch(error => {
          console.error('WebSocket订阅失败:', error);
          // 失败后尝试重连，但要检查重连次数
          reconnectWebSocket(taskId);
          
          // 无论如何都启动轮询作为备份
          startPolling();
        });
      } catch (err) {
        console.error('初始化WebSocket失败:', err);
        reconnectWebSocket(taskId);
        
        // 启动轮询作为备份
        startPolling();
      }
    };
    
    // 重新连接WebSocket
    const reconnectWebSocket = (taskId) => {
      // 检查重连次数
      if (reconnectAttempts.value >= maxReconnectAttempts.value) {
        console.warn(`已达到最大重连次数(${maxReconnectAttempts.value})，停止重连WebSocket`);
        return;
      }
      
      // 增加重连计数
      reconnectAttempts.value++;
      
      // 清除现有定时器
      if (reconnectTimer) {
        clearTimeout(reconnectTimer);
      }
      
      console.log(`WebSocket重连尝试 ${reconnectAttempts.value}/${maxReconnectAttempts.value}`);
      
      // 设置重连定时器
      reconnectTimer = setTimeout(() => {
        console.log('尝试重新连接WebSocket...');
        initWebSocket(taskId);
      }, 5000); // 5秒后重试
    };
    
    // 处理WebSocket消息
    const handleWebSocketMessage = (message) => {
      try {
        const data = JSON.parse(message.body);
        console.log('收到WebSocket消息:', data);
        
        if (data.status) {
          // 更新状态信息
          if (result.value) {
            result.value.status = data.status;
            result.value.progress = data.progress || result.value.progress;
            
            // 如果任务完成，刷新完整数据
            if (data.status === 'completed') {
              ElMessage.success('视频分析已完成');
              fetchResult();
            } else if (data.status === 'failed') {
              ElMessage.error('视频分析失败: ' + (data.error || '未知错误'));
              result.value.error = data.error;
            }
          }
        }
      } catch (err) {
        console.error('处理WebSocket消息失败:', err);
      }
    };
    
    // 页面可见性变化处理
    const handleVisibilityChange = () => {
      if (!document.hidden && activeTab.value === 'summary') {
        // 当页面变为可见且当前在summary标签页时
        if (result.value) {
          console.log('页面变为可见，刷新数据');
          fetchResult();
        }
      }
    };
    
    // 截图处理函数
    const handleScreenshot = (dataURL) => {
      // 创建下载链接
      const link = document.createElement('a');
      link.download = `视频截图_${new Date().getTime()}.png`;
      link.href = dataURL;
      
      // 添加到DOM并触发下载
      document.body.appendChild(link);
      link.click();
      
      // 清理DOM元素
      setTimeout(() => {
        if (document.body.contains(link)) {
          document.body.removeChild(link);
        }
      }, 100);
      
      ElMessage.success('截图已保存');
    };
    
    // 获取结果数据
    const fetchResult = async (showLoading = true) => {
      if (!resultId.value) {
        error.value = '未提供结果ID，无法获取分析结果';
        loading.value = false;
        return;
      }
      
      error.value = null;
      
      // 仅当showLoading为true时显示加载状态
      if (showLoading) {
      loading.value = true;
      }
      
      try {
        console.log('获取视频分析结果，ID:', resultId.value);
        
        // 检测ID类型
        const isMongoObjectId = resultId.value && /^[0-9a-f]{24}$/i.test(resultId.value);
        const isUuid = resultId.value && resultId.value.includes('-');
        
        if (isMongoObjectId) {
          console.log('检测到MongoDB ObjectId格式，使用智能API转换路径');
        } else if (isUuid) {
          console.log('检测到UUID格式，使用标准API路径');
        } else {
          console.log('未知ID格式，使用通用API路径');
        }
        
        // 添加用户信息到请求URL参数
        let userInfo = null;
        try {
          const userStr = localStorage.getItem('user');
          if (userStr) {
            userInfo = JSON.parse(userStr);
          }
        } catch (e) {
          console.warn('获取用户信息失败:', e);
        }
        
        // 构造带有用户信息的请求选项
        const requestOptions = {
          retryWithUuid: isMongoObjectId // 如果是MongoDB ID，允许API尝试UUID查询
        };
        
        if (userInfo) {
          requestOptions.params = {
            userId: userInfo.id || '',
            username: userInfo.username || '',
            role: userInfo.role || ''
          };
        }
        
        // 如果路由参数中有type，添加到请求选项
        if (route.params.type) {
          requestOptions.idType = route.params.type;
        }
        
        // 使用props中的idType参数(来自路由定义中的props)
        if (props.idType) {
          console.log(`使用props中的idType: ${props.idType}`);
          requestOptions.idType = props.idType;
        }
        
        // 添加额外的调试信息
        console.log('发起API请求，选项:', requestOptions);
        
        const data = await getVideoResult(resultId.value, requestOptions);
        
        if (!data) {
          console.error('getVideoResult返回空值');
          error.value = '获取分析结果失败: API返回空值';
          return;
        }
        
        if (data && data.data) {
          console.log('获取分析结果成功:', data.data);
          result.value = data.data;
          
          // 初始化编辑名称
          editName.value = 
            result.value.video_info?.filename || 
            result.value.video_filename || 
            '未命名视频';
            
          // 检查视频是否已完成处理但没有记录处理时间
          if (result.value.status === 'completed' && (!result.value.processing_time || result.value.processing_time <= 0)) {
            console.log('检测到视频已完成但缺少处理时间记录，尝试更新处理时间');
            
            // 使用任务ID或结果ID
            const taskId = result.value.taskId || resultId.value;
            
            try {
              // 如果有完成时间戳但没有开始时间戳，计算估计处理时间
              const estimatedTime = 60; // 默认估计60秒
              await saveVideoProcessingTime(taskId, estimatedTime);
              console.log('已更新缺失的视频处理时间为估计值:', estimatedTime);
              result.value.processing_time = estimatedTime;
              ElMessage.info('已更新视频处理时间');
            } catch (err) {
              console.warn('更新处理时间失败:', err);
            }
          }
            
          // 如果视频正在处理中，初始化WebSocket连接实时接收处理状态
          if (result.value.status === 'processing' || result.value.status === 'queued') {
            const taskId = result.value.taskId || resultId.value;
            if (taskId) {
              console.log('视频正在处理中，初始化WebSocket连接以获取实时状态...');
              initWebSocket(taskId);
            }
          }
            
          // 应用样式
          nextTick(() => {
            forceTableStyles();
          });
        } else {
          console.warn('获取分析结果响应格式不正确:', data);
          error.value = '响应格式不正确，无法显示结果';
        }
      } catch (err) {
        console.error('获取视频分析结果失败：', err);
        
        // 如果是认证错误，尝试刷新令牌
        if (err.authError || err.status === 'auth_error') {
          try {
            if (typeof refreshAuthToken === 'function') {
              const refreshed = await refreshAuthToken();
              if (refreshed) {
                console.log('令牌已刷新，重试获取结果');
                return fetchResult();  // 递归调用自身重试
              }
            } else {
              console.warn('refreshAuthToken函数未定义，无法刷新令牌');
            }
          } catch (refreshErr) {
            console.error('刷新令牌失败:', refreshErr);
          }
          
          // 刷新失败时显示认证错误
          error.value = '认证失败或会话已过期，请重新登录';
          ElMessage({
            message: '认证失败或会话已过期，请重新登录',
            type: 'error',
            duration: 5000
          });
        } else {
          // 如果是轮询请求，错误处理更加宽容
          if (showLoading) {
          error.value = err.message || '获取分析结果失败';
          } else {
            console.warn('轮询获取结果失败，将在下次轮询时重试:', err.message);
          }
        }
      } finally {
        if (showLoading) {
        loading.value = false;
        }
      }
    };
    
    // 获取视频URL
    const getVideoUrl = (path) => {
      if (!path) return ''
      
      // 检查是否为Base64数据
      if (isBase64(path)) {
        return path
      }
      
      // 检查是否为GridFS ID（24位十六进制字符串）
      if (/^[0-9a-f]{24}$/i.test(path)) {
        return `/api/media/video/${path}`;
      }
      
      // 检查是否为Blob URL
      if (path.startsWith('blob:')) {
        // 确保这个Blob URL被跟踪
        blobUrls.add(path) // 只需追踪，不需要重新创建
        return path
      }
      
      // 处理视频URL - 支持绝对路径和相对路径
      if (path.startsWith('/')) {
        // 已经是以/开头的路径，直接返回
        return path;
      } else if (path.includes(':\\') || path.includes(':/')) {
        // 处理Windows绝对路径
        const filename = path.split('\\').pop().split('/').pop();
        // 如果文件名看起来像MongoDB ID，使用media API
        if (/^[0-9a-f]{24}$/i.test(filename)) {
          return `/api/media/video/${filename}`;
        }
        return `/api/static/videos/${filename}`;
      }
      
      // 处理其他格式的路径
      const filename = path.split('/').pop();
      // 如果文件名看起来像MongoDB ID，使用media API
      if (/^[0-9a-f]{24}$/i.test(filename)) {
        return `/api/media/video/${filename}`;
      }
      return `/api/static/videos/${filename}`;
    }
    
    // 获取图像URL
    const getImageUrl = (imageId) => {
      // 检查是否为Base64数据
      if (imageId && imageId.startsWith('data:image')) {
        return imageId;
      }
      
      // 检查是否为GridFS ID（24位十六进制字符串）
      if (imageId && /^[0-9a-f]{24}$/i.test(imageId)) {
        return `/api/media/image/${imageId}`;
      }
      
      // 兼容旧版URL
      return imageId;
    }
    
    // 检查URL是否为Base64数据
    const isBase64 = (url) => {
      return url && (url.startsWith('data:image') || url.startsWith('data:video'));
    }
    
    // 开始重命名
    const startRename = () => {
      editing.value = true;
      editName.value = result.value?.video_info?.filename || '';
    };
    
    // 确认重命名
    const confirmRename = async () => {
      if (!editName.value.trim()) {
        ElMessage.warning('视频名称不能为空');
        return;
      }
      
      renaming.value = true;
      try {
        // 获取任务ID
        const taskId = result.value?.taskId || resultId.value;
        
        if (!taskId) {
          throw new Error('任务ID不存在');
        }
        
        const response = await updateVideoName(taskId, editName.value);
        
        if (response && response.data && response.data.success) {
          // 更新本地数据
          result.value.video_info.filename = editName.value;
          editing.value = false;
          ElMessage.success('视频重命名成功');
        } else {
          throw new Error(response?.data?.message || '重命名失败');
        }
      } catch (err) {
        ElMessage.error('视频重命名失败: ' + (err.message || '未知错误'));
        console.error('重命名错误:', err);
      } finally {
        renaming.value = false;
          }
    };
    
    // 监听result变化，在获取到taskId后初始化WebSocket
    watch(() => result.value?.taskId, (newTaskId) => {
      if (newTaskId) {
        console.log('检测到任务ID变化，初始化WebSocket:', newTaskId);
        initWebSocket(newTaskId);
      }
    });
    
    // 计算车辆总数
    const calculateTotalVehicles = () => {
      if (!result.value || !result.value.vehicle_type_stats) {
        return result.value?.vehicle_count || 0;
      }
      
      // 计算所有车辆类型数量之和
      let total = 0;
      Object.values(result.value.vehicle_type_stats).forEach(count => {
        total += count;
      });
      
      // 如果计算结果与vehicle_count不同，使用计算结果
      return total;
    };
    
    onBeforeMount(() => {
      // 在挂载前就清除先前的状态
      resetState();
      
      // 移除可能存在的会话存储数据
      try {
        sessionStorage.removeItem('videoResultState');
      } catch (err) {
        console.warn('清除视频结果会话存储失败:', err);
      }
    })
    
    onMounted(() => {
      // 重置状态并获取新数据
      resetState();
      fetchResult();
      
      // 监听页面可见性变化
      document.addEventListener('visibilitychange', handleVisibilityChange);
      
      // 应用强制样式
      forceTableStyles();
      
      // 检查STOMP连接状态
      checkStompConnection();
      
      // 如果已经有resultId，尝试获取任务ID并初始化WebSocket
      if (resultId.value) {
        console.log('页面挂载，准备初始化WebSocket，结果ID:', resultId.value);
        // 使用resultId作为初始taskId进行连接
        // 在fetchResult后会通过watch更新为正确的taskId
        setTimeout(() => {
          if (result.value && result.value.taskId) {
            initWebSocket(result.value.taskId);
          } else {
            console.log('尝试使用结果ID作为任务ID进行WebSocket连接');
            initWebSocket(resultId.value);
          }
        }, 1000);
      }
    })
    
    // 监听标签页切换，应用样式
    watch(activeTab, () => {
        nextTick(() => {
          forceTableStyles();
        });
    });
    
    // 监听结果变化，每次更新时重新应用样式
    watch(result, () => {
      if (result.value) {
      nextTick(() => {
        forceTableStyles();
      });
      }
    });
    
    onUnmounted(() => {
      // 使用清理函数释放所有资源
      cleanupResources();
    })
    
    return {
      resultId,
      loading,
      error,
      result,
      activeTab,
      editing,
      editName,
      renaming,
      startRename,
      confirmRename,
      getVideoUrl,
      getImageUrl,
      isBase64,
      getStatusText,
      handleScreenshot,
      forceTableStyles,
      calculateTotalVehicles
    }
  }
}
</script>

<style scoped>
.video-result-container {
  min-height: 100vh;
  background-color: #111827;
  color: #e5e7eb;
  padding: 2.5rem;
  width: 100%;
  max-width: 100%;
  margin: 0;
  overflow-x: hidden;
}

.page-header {
  margin-bottom: 2rem;
  text-align: left;
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
}

.page-header h2 {
  font-size: 2.5rem;
  font-weight: 800;
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin-bottom: 0.5rem;
}

.sub-title {
  color: #d1d5db;
  font-size: 1.1rem;
}

.el-card {
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
  margin-bottom: 1.5rem;
  background: rgba(255, 255, 255, 0.03) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  border-radius: 1rem !important;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1) !important;
}

:deep(.el-card__header) {
  background-color: rgba(26, 32, 50, 0.8);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 1rem 1.5rem;
}

:deep(.el-card__body) {
  padding: 1.5rem;
}

.video-info-card {
  margin-bottom: 2rem;
}

.video-info-card h3 {
  color: #e5e7eb;
  font-weight: 600;
  margin-bottom: 1rem;
}

.video-name-container {
  display: flex;
  align-items: center;
  gap: 10px;
}

.rename-button {
  margin-left: 10px;
}

/* 表格样式强制覆盖 */
:deep(.el-descriptions) {
  --el-descriptions-item-bordered-label-background: rgba(26, 32, 50, 0.8) !important;
  background-color: rgba(31, 41, 55, 0.5) !important;
  border-radius: 8px !important;
  overflow: hidden !important;
}

:deep(.el-descriptions .el-descriptions__label),
:deep(.el-descriptions .el-descriptions__cell.el-descriptions__label),
:deep(.el-descriptions__label.is-bordered-label),
:deep(.el-descriptions__cell.el-descriptions__label.is-bordered-label),
:deep(.el-descriptions__table .el-descriptions__cell.el-descriptions__label),
:deep(td.el-descriptions__cell.el-descriptions__label) {
  color: #e5e7eb !important;
  background-color: rgba(26, 32, 50, 0.8) !important;
  font-weight: 600 !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
}

:deep(.el-descriptions .el-descriptions__content),
:deep(.el-descriptions .el-descriptions__cell.el-descriptions__content),
:deep(.el-descriptions__content.is-bordered-content),
:deep(.el-descriptions__cell.el-descriptions__content.is-bordered-content),
:deep(.el-descriptions__table .el-descriptions__cell.el-descriptions__content),
:deep(td.el-descriptions__cell.el-descriptions__content) {
  color: #e5e7eb !important;
  background-color: rgba(255, 255, 255, 0.03) !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
}

/* 全局覆盖Element Plus的描述列表样式 */
:deep(.el-descriptions__table) {
  background-color: transparent !important;
}

:deep(.el-descriptions__table td) {
  background-color: rgba(255, 255, 255, 0.03) !important;
}

:deep(.el-descriptions__table td.el-descriptions__label) {
  background-color: rgba(26, 32, 50, 0.8) !important;
}

:deep(.el-tabs__item) {
  color: #d1d5db;
  font-size: 1.1rem;
  padding: 0 20px;
}

:deep(.el-tabs__item.is-active) {
  color: #6366f1;
}

:deep(.el-tabs__active-bar) {
  background-color: #6366f1;
}

:deep(.el-tabs__nav-wrap::after) {
  background-color: rgba(255, 255, 255, 0.08);
}

.result-tabs {
  margin-top: 1rem;
}

.summary-tab,
.video-tab,
.report-tab {
  padding: 1rem 0;
}

.video-not-available {
  background: rgba(17, 24, 39, 0.5);
  border-radius: 8px;
  padding: 30px;
  text-align: center;
  margin-top: 20px;
}

.error-container {
  padding: 2rem;
  display: flex;
  flex-direction: column;
  align-items: center;
    justify-content: center;
  min-height: 300px;
}

:deep(.el-empty__description) {
  color: #e5e7eb !important;
}

:deep(.el-empty__image) {
  opacity: 0.6;
}
</style> 

<!-- 全局样式覆盖 -->
<style>
/* 直接对表格单元格应用样式，不使用scoped确保全局应用 */
.el-descriptions__table {
  background-color: transparent !important;
}

.el-descriptions__table .el-descriptions__cell.el-descriptions__label,
td.el-descriptions__cell.el-descriptions__label,
.el-descriptions__label.is-bordered-label {
  background-color: rgba(26, 32, 50, 0.8) !important;
  color: #e5e7eb !important;
  font-weight: 600 !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
}

.el-descriptions__table .el-descriptions__cell.el-descriptions__content,
td.el-descriptions__cell.el-descriptions__content,
.el-descriptions__content.is-bordered-content {
  background-color: rgba(255, 255, 255, 0.03) !important;
  color: #e5e7eb !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
}
</style> 