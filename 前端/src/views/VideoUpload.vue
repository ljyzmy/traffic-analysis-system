<template>
  <div class="video-upload-container">
    <div class="page-header">
      <h2>视频分析</h2>
      <p class="sub-title">上传交通视频进行流量分析</p>
    </div>

    <el-card class="upload-card">
      <template #header>
        <div class="card-header">
          <span>上传交通视频</span>
        </div>
      </template>

      <!-- 使用自定义class修饰VideoUploadForm组件 -->
      <div class="upload-form-wrapper">
        <VideoUploadForm 
          @analysis-complete="handleAnalysisComplete" 
          :initial-task-id="taskIdFromRoute"
          @analysis-status-change="handleAnalysisStatusChange"
        />
      </div>

      <!-- 添加分析中的状态提示 -->
      <div v-if="isAnalyzing" class="analysis-status-wrapper">
        <div class="el-alert el-alert--info is-light analysis-alert" role="alert">
          <i class="el-icon el-alert__icon is-big">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">
              <path fill="currentColor" d="M512 64a448 448 0 1 1 0 896.064A448 448 0 0 1 512 64m67.2 275.072c33.28 0 60.288-23.104 60.288-57.344s-27.072-57.344-60.288-57.344c-33.28 0-60.16 23.104-60.16 57.344s26.88 57.344 60.16 57.344M590.912 699.2c0-6.848 2.368-24.64 1.024-34.752l-52.608 60.544c-10.88 11.456-24.512 19.392-30.912 17.28a12.992 12.992 0 0 1-8.256-14.72l87.68-276.992c7.168-35.136-12.544-67.2-54.336-71.296-44.096 0-108.992 44.736-148.48 101.504 0 6.784-1.28 23.68.064 33.792l52.544-60.608c10.88-11.328 23.552-19.328 29.952-17.152a12.8 12.8 0 0 1 7.808 16.128L388.48 728.576c-10.048 32.256 8.96 63.872 55.04 71.04 67.84 0 107.904-43.648 147.456-100.416z"></path>
            </svg>
          </i>
          <div class="el-alert__content">
            <span class="el-alert__title with-description"><strong>分析中</strong> - 您的视频正在处理 </span>
            <div class="el-alert__description">
              <p>任务ID: {{ taskId }}</p>
              <p v-if="analysisProgress > 0">进度: {{ Math.round(analysisProgress) }}%</p>
              <p>{{ analysisMessage || '正在处理视频' }}</p>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-card class="info-card">
      <template #header>
        <div class="card-header">
          <span>视频分析功能说明</span>
        </div>
      </template>
      <div class="feature-list">
        <div class="feature-item">
          <el-icon><data-analysis /></el-icon>
          <div class="feature-info">
            <h4>车流量统计</h4>
            <p>计算视频中的车辆总数和不同车型的分布情况</p>
          </div>
        </div>
        <div class="feature-item">
          <el-icon><trend-charts /></el-icon>
          <div class="feature-info">
            <h4>时间序列数据</h4>
            <p>车流量随时间变化的趋势图</p>
          </div>
        </div>
        <div class="feature-item">
          <el-icon><position /></el-icon>
          <div class="feature-info">
            <h4>交通方向分布</h4>
            <p>东西/南北方向交通流量的比例分析</p>
          </div>
        </div>
        <div class="feature-item">
          <el-icon><timer /></el-icon>
          <div class="feature-info">
            <h4>高峰期识别</h4>
            <p>自动识别交通高峰时段</p>
          </div>
        </div>
        <div class="feature-item">
          <el-icon><magic-stick /></el-icon>
          <div class="feature-info">
            <h4>优化建议</h4>
            <p>基于分析结果提供交通优化方案</p>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { 
  DataAnalysis, 
  TrendCharts, 
  Position, 
  Timer, 
  MagicStick
} from '@element-plus/icons-vue'
import VideoUploadForm from '@/components/analysis/VideoUploadForm.vue'
import stompService from '@/utils/stomp-service'

export default {
  name: 'VideoUpload',
  components: {
    VideoUploadForm,
    DataAnalysis,
    TrendCharts,
    Position,
    Timer,
    MagicStick
  },
  setup() {
    const router = useRouter()
    const route = useRoute()
    
    // 从路由参数中获取任务ID（如果有）
    const taskIdFromRoute = route.params.taskId
    
    // 添加分析状态
    const isAnalyzing = ref(false)
    const taskId = ref('')
    const analysisProgress = ref(0)
    const analysisMessage = ref('')
    
    // 处理分析完成的回调
    const handleAnalysisComplete = (resultId) => {
      if (resultId) {
        router.push(`/video-result/${resultId}`)
      }
    }
    
    // 处理分析状态变化
    const handleAnalysisStatusChange = (status) => {
      isAnalyzing.value = status.isAnalyzing
      if (status.taskId) {
        taskId.value = status.taskId
      }
      if (status.progress !== undefined) {
        analysisProgress.value = status.progress
      }
      if (status.message) {
        analysisMessage.value = status.message
      }
    }
    
    // 监听任务ID的变化，初始化WebSocket连接
    watch(() => taskId.value, (newTaskId) => {
      if (newTaskId && isAnalyzing.value) {
        initWebSocketConnection(newTaskId)
      }
    })
    
    // 初始化WebSocket连接接收实时进度
    const initWebSocketConnection = (currentTaskId) => {
      try {
        console.log(`初始化进度更新WebSocket订阅: progress/${currentTaskId}`)
        
        // 使用STOMP服务订阅进度更新主题
        stompService.subscribe(`progress/${currentTaskId}`, (data) => {
          try {
            if (data.type === 'progress_update') {
              analysisProgress.value = data.progress || 0
              if (data.message) {
                analysisMessage.value = data.message
              }
            } else if (data.type === 'result_update' && data.status === 'completed') {
              // 当分析完成时跳转到结果页
              if (data.resultId) {
                router.push(`/video-result/${data.resultId}`)
              }
            }
          } catch (err) {
            console.error('处理WebSocket消息失败:', err)
          }
        }).catch(error => {
          console.error('订阅进度更新失败:', error)
        })
      } catch (error) {
        console.error('初始化WebSocket订阅失败:', error)
      }
    }
    
    // 页面挂载后执行
    onMounted(() => {
      console.log('视频上传页面已加载')
      // 如果有taskId，说明正在分析中
      if (taskIdFromRoute) {
        isAnalyzing.value = true
        taskId.value = taskIdFromRoute
        
        // 检查当前路径是否包含video-status
        if (route.path.includes('/video-status/')) {
          // 初始化WebSocket连接获取实时进度
          initWebSocketConnection(taskIdFromRoute)
        }
      }
    })
    
    return {
      taskIdFromRoute,
      handleAnalysisComplete,
      handleAnalysisStatusChange,
      isAnalyzing,
      taskId,
      analysisProgress,
      analysisMessage
    }
  }
}
</script>

<style scoped>
.video-upload-container {
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

.upload-card, .info-card {
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
  margin-bottom: 1.5rem;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 1rem;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.card-header {
  background-color: rgba(26, 32, 50, 0.8);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 1rem 1.5rem;
}

.card-header span {
  color: #ffffff;
  font-weight: 600;
}

/* 添加一个包装器，防止样式冲突 */
.upload-form-wrapper {
  display: block;
  width: 100%;
}

/* 确保上传组件的文件不会重复显示 */
:deep(.preview-container) {
  border-radius: 8px;
  padding: 1.5rem;
  height: auto;
  min-height: 100px;
}

:deep(.file-name) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 90%;
  margin: 8px 0 4px 0 !important;
  color: #e5e7eb !important;
}

/* 调整内嵌视频播放器的样式 */
:deep(.preview-video-inline) {
  width: 100%;
  max-height: 250px;
  border-radius: 8px;
  margin-top: 15px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background-color: rgba(0, 0, 0, 0.2);
}

/* 不再需要单独的视频预览区域，可以隐藏或移除 */
:deep(.video-preview) {
  display: none;
}

/* 隐藏重复显示的文件名 */
:deep(.el-form-item__content > .file-name:not(.preview-container .file-name)) {
  display: none !important;
}

:deep(.video-preview ~ .file-name),
:deep(.video-preview + .file-name) {
  display: none !important;
}

/* 确保文件名只在预览容器内显示 */
:deep(.el-form-item__content) {
  overflow: hidden;
}

/* 调整上传区域的尺寸，确保足够空间显示内嵌视频 */
:deep(.drop-area.has-file) {
  min-height: 350px;
  padding: 1.5rem;
}

/* 改善视频预览区域的样式 */
:deep(.video-preview h4) {
  color: #ffffff;
  margin-top: 0;
  margin-bottom: 10px;
  font-size: 16px;
  font-weight: 600;
}

:deep(.el-button--text) {
  color: #6366f1;
}

.feature-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  grid-gap: 20px;
  margin-top: 10px;
}

.feature-item {
  display: flex;
  align-items: flex-start;
  padding: 15px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 0.75rem;
  transition: all 0.3s;
}

.feature-item:hover {
  background: rgba(99, 102, 241, 0.08);
  transform: translateY(-2px);
}

.feature-item :deep(.el-icon) {
  font-size: 24px;
  color: #6366f1;
  margin-right: 15px;
  margin-top: 3px;
}

.feature-info h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #ffffff;
}

.feature-info p {
  margin: 0;
  color: #d1d5db;
  font-size: 14px;
}

/* 防止导航栏字体变色 - 局部样式 */
:deep(.el-menu-item),
:deep(.el-submenu__title),
:deep(.el-dropdown-menu__item) {
  color: #e5e7eb !important;
}

:deep(.el-menu-item.is-active),
:deep(.el-menu-item:hover),
:deep(.el-submenu__title:hover),
:deep(.el-dropdown-menu__item:hover) {
  color: #6366f1 !important;
  background-color: rgba(99, 102, 241, 0.1) !important;
}

/* 分析状态提示样式 */
.analysis-status-wrapper {
  margin-top: 1.5rem;
  width: 100%;
}

/* 状态警告框样式 */
:deep(.analysis-alert) {
  background-color: rgba(99, 102, 241, 0.08);
  border: 1px solid rgba(99, 102, 241, 0.1);
  border-radius: 0.75rem;
  color: #e5e7eb;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  padding: 16px;
}

:deep(.el-alert__icon.is-big) {
  font-size: 24px;
  color: #6366f1;
  margin-right: 16px;
}

:deep(.el-alert__title) {
  color: #e5e7eb;
  font-size: 16px;
  font-weight: 600;
}

:deep(.el-alert__title strong) {
  color: #6366f1;
  font-weight: 700;
}

:deep(.el-alert__description) {
  color: #d1d5db;
  margin: 8px 0 0 0;
  line-height: 1.5;
}

:deep(.el-alert__description p) {
  margin: 4px 0;
  font-size: 14px;
}

/* 响应式设计 */
@media (max-width: 992px) {
  .feature-list {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .feature-list {
    grid-template-columns: 1fr;
  }
  
  .page-header h2 {
    font-size: 2rem;
  }
  
  .video-upload-container {
    padding: 1.5rem;
  }
}
</style> 