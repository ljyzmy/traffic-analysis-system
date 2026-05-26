<template>
  <!-- 上传表单部分 - 当没有任务ID时显示 -->
  <el-form v-if="!taskId" :model="form" label-position="top" class="upload-form" :rules="rules" ref="uploadForm">
    <!-- 道路类型选择 -->
    <el-form-item label="道路类型" prop="roadType">
      <el-radio-group v-model="form.roadType" size="large">
        <el-radio-button value="normal" label="普通道路"></el-radio-button>
        <el-radio-button value="intersection" label="十字路口"></el-radio-button>
      </el-radio-group>
    </el-form-item>
    
    <!-- 普通道路的单视频上传 -->
    <template v-if="form.roadType === 'normal'">
      <el-form-item label="上传视频" prop="singleVideo">
        <div class="upload-area">
          <div class="drop-area" 
               :class="{ 'drag-over': isDragOver, 'has-file': form.singleVideoFile }"
               @click="triggerFileInput" 
               @drop.prevent="handleFileDrop" 
               @dragover.prevent
               @dragenter.prevent="onDragEnter"
               @dragleave.prevent="onDragLeave">
            <div v-if="!form.singleVideoFile" id="dropText">
              <div class="icon-container">
                <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" fill="currentColor" class="upload-icon" viewBox="0 0 16 16">
                  <path d="M6.002 5.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0"/>
                  <path d="M2.002 1a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V3a2 2 0 0 0-2-2zm12 1a1 1 0 0 1 1 1v6.5l-3.777-1.947a.5.5 0 0 0-.577.093l-3.71 3.71-2.66-1.772a.5.5 0 0 0-.63.062L1.002 12V3a1 1 0 0 1 1-1z"/>
                </svg>
                <div class="upload-arrow">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" class="arrow-icon" viewBox="0 0 16 16">
                    <path fill-rule="evenodd" d="M8 1a.5.5 0 0 1 .5.5v11.793l3.146-3.147a.5.5 0 0 1 .708.708l-4 4a.5.5 0 0 1-.708 0l-4-4a.5.5 0 0 1 .708-.708L7.5 13.293V1.5A.5.5 0 0 1 8 1z"/>
                  </svg>
                </div>
              </div>
              <p class="upload-title">拖拽视频文件到此处，或 <span class="click-upload">点击上传</span></p>
              <p class="upload-hint">支持 mp4, avi, mov, quicktime 格式视频，文件大小不超过500MB</p>
            </div>
            <div v-else class="preview-container">
              <el-icon class="file-icon"><document /></el-icon>
              <p class="file-name">{{ form.singleVideoFile.name }}</p>
              <span class="file-size">{{ formatFileSize(form.singleVideoFile.size) }}</span>
              <el-button class="remove-file-btn" size="small" @click.stop="handleSingleVideoRemove">移除</el-button>
              <video 
                v-if="singleVideoPreviewUrl" 
                :src="singleVideoPreviewUrl" 
                controls 
                class="preview-video-inline"
              ></video>
            </div>
          </div>
          
          <input
            type="file"
            ref="fileInput"
            style="display: none;"
            @change="handleFileChange"
            accept="video/mp4,video/avi,video/mov,video/quicktime"
          />
        </div>
      </el-form-item>
      
      <el-form-item label="视频方向">
        <el-select v-model="form.direction" placeholder="请选择视频方向">
          <el-option label="横向" value="horizontal"></el-option>
          <el-option label="纵向" value="vertical"></el-option>
        </el-select>
      </el-form-item>
    </template>
    
    <!-- 十字路口的双视频上传 -->
    <template v-else>
      <el-form-item label="横向视频" prop="horizontalVideo">
        <div class="upload-area">
          <div class="drop-area" 
               :class="{ 'drag-over': isDragOverHorizontal, 'has-file': form.horizontalVideoFile }" 
               @click="triggerHorizontalFileInput" 
               @drop.prevent="handleHorizontalFileDrop" 
               @dragover.prevent
               @dragenter.prevent="onDragEnterHorizontal"
               @dragleave.prevent="onDragLeaveHorizontal">
            <div v-if="!form.horizontalVideoFile" id="dropText">
              <div class="icon-container">
                <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" fill="currentColor" class="upload-icon" viewBox="0 0 16 16">
                  <path d="M6.002 5.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0"/>
                  <path d="M2.002 1a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V3a2 2 0 0 0-2-2zm12 1a1 1 0 0 1 1 1v6.5l-3.777-1.947a.5.5 0 0 0-.577.093l-3.71 3.71-2.66-1.772a.5.5 0 0 0-.63.062L1.002 12V3a1 1 0 0 1 1-1z"/>
                </svg>
                <div class="upload-arrow">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" class="arrow-icon" viewBox="0 0 16 16">
                    <path fill-rule="evenodd" d="M8 1a.5.5 0 0 1 .5.5v11.793l3.146-3.147a.5.5 0 0 1 .708.708l-4 4a.5.5 0 0 1-.708 0l-4-4a.5.5 0 0 1 .708-.708L7.5 13.293V1.5A.5.5 0 0 1 8 1z"/>
                  </svg>
                </div>
              </div>
              <p class="upload-title">拖拽横向视频文件到此处，或 <span class="click-upload">点击上传</span></p>
              <p class="upload-hint">横向视频应拍摄东西方向的交通</p>
            </div>
            <div v-else class="preview-container">
              <el-icon class="file-icon"><document /></el-icon>
              <p class="file-name">{{ form.horizontalVideoFile.name }}</p>
              <span class="file-size">{{ formatFileSize(form.horizontalVideoFile.size) }}</span>
              <el-button class="remove-file-btn" size="small" @click.stop="handleHorizontalVideoRemove">移除</el-button>
              <video 
                v-if="horizontalVideoPreviewUrl" 
                :src="horizontalVideoPreviewUrl" 
                controls 
                class="preview-video-inline"
              ></video>
            </div>
          </div>
          
          <input
            type="file"
            ref="horizontalFileInput"
            style="display: none;"
            @change="handleHorizontalFileChange"
            accept="video/mp4,video/avi,video/mov,video/quicktime"
          />
        </div>
      </el-form-item>

      <el-form-item label="纵向视频" prop="verticalVideo">
        <div class="upload-area">
          <div class="drop-area" 
               :class="{ 'drag-over': isDragOverVertical, 'has-file': form.verticalVideoFile }" 
               @click="triggerVerticalFileInput" 
               @drop.prevent="handleVerticalFileDrop" 
               @dragover.prevent
               @dragenter.prevent="onDragEnterVertical"
               @dragleave.prevent="onDragLeaveVertical">
            <div v-if="!form.verticalVideoFile" id="dropText">
              <div class="icon-container">
                <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" fill="currentColor" class="upload-icon" viewBox="0 0 16 16">
                  <path d="M6.002 5.5a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0"/>
                  <path d="M2.002 1a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V3a2 2 0 0 0-2-2zm12 1a1 1 0 0 1 1 1v6.5l-3.777-1.947a.5.5 0 0 0-.577.093l-3.71 3.71-2.66-1.772a.5.5 0 0 0-.63.062L1.002 12V3a1 1 0 0 1 1-1z"/>
                </svg>
                <div class="upload-arrow">
                  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" class="arrow-icon" viewBox="0 0 16 16">
                    <path fill-rule="evenodd" d="M8 1a.5.5 0 0 1 .5.5v11.793l3.146-3.147a.5.5 0 0 1 .708.708l-4 4a.5.5 0 0 1-.708 0l-4-4a.5.5 0 0 1 .708-.708L7.5 13.293V1.5A.5.5 0 0 1 8 1z"/>
                  </svg>
                </div>
              </div>
              <p class="upload-title">拖拽纵向视频文件到此处，或 <span class="click-upload">点击上传</span></p>
              <p class="upload-hint">纵向视频应拍摄南北方向的交通</p>
            </div>
            <div v-else class="preview-container">
              <el-icon class="file-icon"><document /></el-icon>
              <p class="file-name">{{ form.verticalVideoFile.name }}</p>
              <span class="file-size">{{ formatFileSize(form.verticalVideoFile.size) }}</span>
              <el-button class="remove-file-btn" size="small" @click.stop="handleVerticalVideoRemove">移除</el-button>
              <video 
                v-if="verticalVideoPreviewUrl" 
                :src="verticalVideoPreviewUrl" 
                controls 
                class="preview-video-inline"
              ></video>
            </div>
          </div>
          
          <input
            type="file"
            ref="verticalFileInput"
            style="display: none;"
            @change="handleVerticalFileChange"
            accept="video/mp4,video/avi,video/mov,video/quicktime"
          />
        </div>
      </el-form-item>
    </template>
    
    <el-form-item>
      <el-button type="primary" @click="submitUpload" :loading="uploading">开始分析</el-button>
      <el-button @click="resetForm">重置</el-button>
    </el-form-item>
  </el-form>

  <!-- 处理状态部分 - 当有任务ID时显示 -->
  <div v-else class="status-section">
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="3" animated />
    </div>
    
    <div v-else-if="statusError" class="error-container">
      <el-alert
        title="获取任务状态失败"
        type="error"
        :description="statusError"
        show-icon
      />
      <div class="error-actions">
        <el-button type="primary" @click="fetchStatus">重试</el-button>
        <el-button @click="resetTask">重新上传</el-button>
      </div>
    </div>
    
    <div v-else class="status-info">
      <el-alert
        v-if="taskInfo.status !== 'completed'"
        type="info"
        :closable="false"
        show-icon
        class="custom-alert"
      >
        <template #title>
          <strong>分析中</strong> - 您的视频正在处理
        </template>
        <p>任务ID: {{ taskId }}</p>
        <p v-if="taskInfo.videoName">视频名称: {{ taskInfo.videoName || '未命名视频' }}</p>
        <p v-if="taskInfo.message">{{ taskInfo.message }}</p>
        <p v-if="processingElapsedTime > 0">处理时间: {{ processingElapsedTime.toFixed(2) }} 秒</p>
      </el-alert>
      
      <el-alert
        v-else
        type="success"
        :closable="false"
        show-icon
        class="custom-alert"
      >
        <template #title>
          <strong>分析完成</strong> - 您的视频已处理完毕
        </template>
        <p>任务ID: {{ taskId }}</p>
        <p v-if="taskInfo.videoName">视频名称: {{ taskInfo.videoName || '未命名视频' }}</p>
        <p v-if="processingElapsedTime > 0">处理总时间: {{ processingElapsedTime.toFixed(2) }} 秒</p>
      </el-alert>
      
      <div class="status-progress">
        <p>处理进度:</p>
        <el-progress 
          :percentage="taskInfo.progress || 0" 
          :status="progressStatus"
          :stroke-width="18"
          text-inside
          :show-text="true"
          class="custom-progress"
        />
      </div>
      
      <!-- 处理时间显示 -->
      <div v-if="processingElapsedTime > 0" class="processing-time">
        <div class="time-header">
          <el-icon><Timer /></el-icon> 
          <span>处理时间</span>
        </div>
        <div class="time-value-container">
          <span class="time-value">{{ processingElapsedTime.toFixed(2) }}</span> 
          <span class="time-unit">秒</span>
          <div v-if="taskInfo.status === 'processing'" class="timer-animation">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
        </div>
        <div v-if="taskInfo.status === 'completed'" class="processing-complete">
          <el-icon color="#10b981"><Check /></el-icon> 处理完成
        </div>
      </div>
      
      <div class="status-actions">
        <div v-if="taskInfo.status === 'completed' && taskInfo.resultId" class="completed-actions">
          <el-button 
            type="primary" 
            class="view-result-btn"
            @click="viewResult"
            size="large"
          >
            <el-icon class="view-icon"><Document /></el-icon> 查看详情
          </el-button>
          
          <el-button 
            @click="resetTask"
            class="reset-btn"
            size="large"
          >
            重新上传
          </el-button>
        </div>
        
        <div v-else-if="taskInfo.status === 'failed'" class="failed-actions">
          <el-button 
            type="warning" 
            @click="retryAnalysis"
            :loading="retrying"
            size="large"
          >
            重新分析
          </el-button>
          
          <el-button 
            @click="resetTask"
            size="large"
          >
            重新上传
          </el-button>
        </div>
        
        <div v-else class="processing-actions">
          <el-button 
            @click="resetTask"
            size="large"
          >
            重新上传
          </el-button>
        </div>
      </div>
    </div>
  </div>

  <!-- 上传进度条 - 仅在上传过程中显示 -->
  <div v-if="uploadProgress > 0 && uploadProgress < 100 && !taskId" class="upload-progress">
    <p>上传进度:</p>
    <el-progress 
      :percentage="uploadProgress" 
      :status="uploadProgress < 100 ? '' : 'success'"
      :stroke-width="18"
      :show-text="true"
      text-inside
      class="custom-progress"
    ></el-progress>
  </div>

  <!-- 视频预览对话框 -->
  <el-dialog
    title="视频预览"
    v-model="previewDialogVisible"
    width="70%"
    class="preview-dialog"
  >
    <video 
      v-if="currentPreviewUrl" 
      :src="currentPreviewUrl" 
      controls 
      style="width: 100%;"
    ></video>
  </el-dialog>
</template>

<script>
import { ref, reactive, computed, onMounted, onUnmounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  uploadAndAnalyzeVideo, 
  uploadVideoDirectApi, 
  uploadIntersectionDirectApi,
  getVideoTaskStatus,
  retryVideoAnalysis,
  saveVideoProcessingTime
} from '@/api/video'
import { STOMP_VIDEO_PROGRESS } from '@/config'
import stompService from '@/utils/stomp-service'
import apiClient from '@/utils/http-common'
import { 
  UploadFilled, 
  Document,
  Timer,
  Check
} from '@element-plus/icons-vue'

export default {
  name: 'VideoUploadForm',
  components: {
    UploadFilled,
    Document,
    Timer,
    Check
  },
  emits: ['analysisComplete', 'analysisStatusChange'],
  props: {
    initialTaskId: {
      type: String,
      default: null
    }
  },
  setup(props, { emit }) {
    const router = useRouter()
    
    // 基本状态初始化
    const uploadForm = ref(null)
    const fileInput = ref(null)
    const taskId = ref(props.initialTaskId || null)
    const uploading = ref(false)
    const uploadProgress = ref(0)
    const maxSize = 500 * 1024 * 1024 // 500MB
    
    // 添加处理时间计时器
    const processingStartTime = ref(null)
    const processingElapsedTime = ref(0)
    const processingTimer = ref(null)
    const processingTimeInterval = ref(null)
    
    // 拖拽状态
    const isDragOver = ref(false);
    const isDragOverHorizontal = ref(false);
    const isDragOverVertical = ref(false);
    
    // 表单数据
    const form = reactive({
      roadType: 'normal', // 默认为普通道路
      direction: 'horizontal', // 默认为横向
      singleVideoFile: null,
      horizontalVideoFile: null,
      verticalVideoFile: null
    })
    
    // 表单验证规则
    const rules = {
      roadType: [
        { required: true, message: '请选择道路类型', trigger: 'change' }
      ],
      singleVideo: [
        { required: true, message: '请上传视频文件', trigger: 'change', validator: (rule, value, callback) => {
          if (form.roadType === 'normal' && !form.singleVideoFile) {
            callback(new Error('请上传视频文件'));
          } else {
            callback();
          }
        }}
      ],
      horizontalVideo: [
        { required: true, message: '请上传横向视频', trigger: 'change', validator: (rule, value, callback) => {
          if (form.roadType === 'intersection' && !form.horizontalVideoFile) {
            callback(new Error('请上传横向视频'));
          } else {
            callback();
          }
        }}
      ],
      verticalVideo: [
        { required: true, message: '请上传纵向视频', trigger: 'change', validator: (rule, value, callback) => {
          if (form.roadType === 'intersection' && !form.verticalVideoFile) {
            callback(new Error('请上传纵向视频'));
          } else {
            callback();
          }
        }}
      ]
    }
    
    // 视频预览URL - 使用base64 Data URL
    const singleVideoPreviewUrl = ref('')
    const horizontalVideoPreviewUrl = ref('')
    const verticalVideoPreviewUrl = ref('')
    const currentPreviewUrl = ref('')
    const previewDialogVisible = ref(false)
    
    // 任务状态相关
    const loading = ref(false)
    const statusError = ref('')
    const taskInfo = ref({})
    const retrying = ref(false)
    const statusInterval = ref(null)
    let stompSubscription = null
    
    // 计算属性：进度条状态
    const progressStatus = computed(() => {
      const status = taskInfo.value.status;
      if (status === 'completed') return 'success';
      if (status === 'failed') return 'exception';
      return '';
    })
    
    // 监听道路类型变化，重置表单
    watch(() => form.roadType, (newValue) => {
      // 清除已上传的文件
      if (newValue === 'normal') {
        if (form.horizontalVideoFile) handleHorizontalVideoRemove()
        if (form.verticalVideoFile) handleVerticalVideoRemove()
      } else {
        if (form.singleVideoFile) handleSingleVideoRemove()
      }
    })
    
    // 拖拽处理方法 - 单视频
    const onDragEnter = () => {
      isDragOver.value = true;
    };
    
    const onDragLeave = () => {
      isDragOver.value = false;
    };
    
    // 拖拽处理方法 - 横向视频
    const onDragEnterHorizontal = () => {
      isDragOverHorizontal.value = true;
    };
    
    const onDragLeaveHorizontal = () => {
      isDragOverHorizontal.value = false;
    };
    
    // 拖拽处理方法 - 纵向视频
    const onDragEnterVertical = () => {
      isDragOverVertical.value = true;
    };
    
    const onDragLeaveVertical = () => {
      isDragOverVertical.value = false;
    };
    
    // 格式化文件大小显示
    const formatFileSize = (bytes) => {
      if (bytes === 0) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
    
    // 验证文件类型
    const validateFileType = (file) => {
      const allowedTypes = ['video/mp4', 'video/avi', 'video/mov', 'video/quicktime'];
      if (!allowedTypes.includes(file.type)) {
        ElMessage.error('不支持的文件类型，请上传mp4、avi、mov或quicktime格式的视频');
        return false;
      }
      return true;
    }
    
    // 检查文件大小
    const checkFileSize = (file) => {
      if (file.size > maxSize) {
        ElMessage.error(`文件大小超过限制，请上传小于 ${formatFileSize(maxSize)} 的文件`);
        return false;
      }
      return true;
    }
    
    // 创建视频预览
    const createVideoPreview = (file, previewUrl) => {
      return new Promise((resolve, reject) => {
        const videoURL = URL.createObjectURL(file);
        previewUrl.value = videoURL;
        resolve(videoURL);
      });
    }
    
    // 单视频上传处理 - 文件输入和拖拽
    const triggerFileInput = () => {
      fileInput.value.click();
    };
    
    const handleFileDrop = (event) => {
      isDragOver.value = false;
      const files = event.dataTransfer.files;
      if (files.length > 0) {
        handleFileChange({ target: { files: Array.from(files) } });
      }
    };
    
    const handleFileChange = (event) => {
      const files = event.target.files;
      if (files.length > 0) {
        handleUpload({ file: files[0] });
      }
    };
    
    const handleUpload = (options) => {
      const file = options.file;
      
      if (!file) {
        console.error('没有接收到文件对象');
        return;
      }
      
      // 验证文件类型和大小
      if (!validateFileType(file) || !checkFileSize(file)) {
        return;
      }
      
      // 设置表单数据
      form.singleVideoFile = file;
      console.log('设置单个视频文件:', file.name, file);
      
      // 创建预览
      createVideoPreview(file, singleVideoPreviewUrl)
        .catch(err => {
          console.error('创建视频预览失败:', err);
          ElMessage.warning('视频预览生成失败，但您仍可以上传');
        });
        
      // 如果是input change事件，清空它以允许选择相同的文件
      if (fileInput.value) {
        fileInput.value.value = '';
      }
    };
    
    const handleSingleVideoRemove = (e) => {
      if (e) {
        e.stopPropagation(); // 防止冒泡到上传区域
      }
      
      form.singleVideoFile = null;
      // 清除预览URL
      singleVideoPreviewUrl.value = '';
      
      // 清空文件输入
      if (fileInput.value) {
        fileInput.value.value = '';
      }
    };

    // 横向视频上传处理
    const horizontalFileInput = ref(null);
    const verticalFileInput = ref(null);
    
    // 触发横向视频文件选择
    const triggerHorizontalFileInput = () => {
      horizontalFileInput.value.click();
    };
    
    // 处理横向视频拖拽
    const handleHorizontalFileDrop = (event) => {
      const files = event.dataTransfer.files;
      if (files.length > 0) {
        handleHorizontalFileChange({ target: { files: Array.from(files) } });
      }
    };
    
    // 处理横向视频文件选择
    const handleHorizontalFileChange = (event) => {
      const files = event.target.files;
      if (files.length > 0) {
        handleHorizontalVideoUpload(files[0]);
      }
    };
    
    // 处理横向视频上传
    const handleHorizontalVideoUpload = (file) => {
      if (!validateFileType(file) || !checkFileSize(file)) {
        return;
      }
      
      // 清除之前的预览URL
      horizontalVideoPreviewUrl.value = '';
      
      // 设置表单数据
      form.horizontalVideoFile = file;
      console.log('设置横向视频文件:', file.name, file);
      
      // 创建预览
      createVideoPreview(file, horizontalVideoPreviewUrl)
        .catch(err => {
          console.error('创建横向视频预览失败:', err);
          ElMessage.warning('视频预览生成失败，但您仍可以上传');
        });
      
      // 清空输入以允许选择相同的文件
      if (horizontalFileInput.value) {
        horizontalFileInput.value.value = '';
      }
    };
    
    // 横向视频删除
    const handleHorizontalVideoRemove = (e) => {
      if (e) {
        e.stopPropagation(); // 防止冒泡到上传区域
      }
      
      form.horizontalVideoFile = null;
      // 清除预览URL
      horizontalVideoPreviewUrl.value = '';
      
      // 清空文件输入
      if (horizontalFileInput.value) {
        horizontalFileInput.value.value = '';
      }
    };
    
    // 触发纵向视频文件选择
    const triggerVerticalFileInput = () => {
      verticalFileInput.value.click();
    };
    
    // 处理纵向视频拖拽
    const handleVerticalFileDrop = (event) => {
      const files = event.dataTransfer.files;
      if (files.length > 0) {
        handleVerticalFileChange({ target: { files: Array.from(files) } });
      }
    };
    
    // 处理纵向视频文件选择
    const handleVerticalFileChange = (event) => {
      const files = event.target.files;
      if (files.length > 0) {
        handleVerticalVideoUpload(files[0]);
      }
    };
    
    // 处理纵向视频上传
    const handleVerticalVideoUpload = (file) => {
      if (!validateFileType(file) || !checkFileSize(file)) {
        return;
      }
      
      // 清除之前的预览URL
      verticalVideoPreviewUrl.value = '';
      
      // 设置表单数据
      form.verticalVideoFile = file;
      console.log('设置纵向视频文件:', file.name, file);
      
      // 创建预览
      createVideoPreview(file, verticalVideoPreviewUrl)
        .catch(err => {
          console.error('创建纵向视频预览失败:', err);
          ElMessage.warning('视频预览生成失败，但您仍可以上传');
        });
      
      // 清空输入以允许选择相同的文件
      if (verticalFileInput.value) {
        verticalFileInput.value.value = '';
      }
    };
    
    // 纵向视频删除
    const handleVerticalVideoRemove = (e) => {
      if (e) {
        e.stopPropagation(); // 防止冒泡到上传区域
      }
      
      form.verticalVideoFile = null;
      // 清除预览URL
      verticalVideoPreviewUrl.value = '';
      
      // 清空文件输入
      if (verticalFileInput.value) {
        verticalFileInput.value.value = '';
      }
    };

    // 使用WebSocket连接来接收上传和处理进度
    const initWebSocket = (taskId) => {
      try {
        console.log(`初始化视频进度WebSocket订阅: ${STOMP_VIDEO_PROGRESS}/${taskId}`);
        
        // 使用STOMP服务订阅视频进度主题
        stompService.subscribe(`${STOMP_VIDEO_PROGRESS}/${taskId}`, (data) => {
          try {
            if (data.type === 'upload_progress') {
              uploadProgress.value = data.progress;
            } else if (data.type === 'processing_progress') {
              // 处理进度更新
              if (taskInfo.value) {
                taskInfo.value.progress = data.progress || taskInfo.value.progress;
                taskInfo.value.status = data.status || taskInfo.value.status;
                taskInfo.value.message = data.message || taskInfo.value.message;
                
                // 当收到第一个处理状态时启动计时器
                if (data.status === 'processing' && !processingStartTime.value) {
                  startProcessingTimer();
                }
                
                if (data.status === 'completed' && data.resultId) {
                  taskInfo.value.resultId = data.resultId;
                  
                  // 停止计时器并记录处理时间
                  stopProcessingTimer();
                  
                  // 自动跳转到结果页面
                  setTimeout(() => {
                    viewResult();
                  }, 2000);
                }
              }
            }
          } catch (err) {
            console.error('处理WebSocket消息失败:', err);
          }
        }).then(subscription => {
          stompSubscription = subscription;
        }).catch(error => {
          console.error('订阅视频进度失败:', error);
        });
      } catch (error) {
        console.error('初始化WebSocket订阅失败:', error);
      }
    }
    
    // 模拟上传进度（当后端不支持实时进度更新时使用）
    const simulateUploadProgress = () => {
      let progress = 0
      const interval = setInterval(() => {
        if (progress < 98) {
          progress += (98 - progress) / 10
          uploadProgress.value = Math.floor(progress)
        } else {
          clearInterval(interval)
        }
      }, 200)
      
      return interval
    }
    
    // 获取任务状态
    const fetchStatus = async () => {
      if (!taskId.value) return;
      
      loading.value = true;
      statusError.value = '';
      
      try {
        const response = await getVideoTaskStatus(taskId.value);
        
        if (response && response.data) {
          taskInfo.value = response.data;
          
          // 如果任务状态为处理中且计时器未启动，则启动计时器
          if (taskInfo.value.status === 'processing' && !processingStartTime.value) {
            startProcessingTimer();
          }
          
          // 如果任务已完成或失败，停止轮询和计时器
          if (['completed', 'failed'].includes(taskInfo.value.status)) {
            clearInterval(statusInterval.value);
            stopProcessingTimer();
          }
          
          // 如果任务完成，自动跳转到结果页
          if (taskInfo.value.status === 'completed' && taskInfo.value.resultId) {
            setTimeout(() => {
              viewResult();
            }, 2000);
          }
        } else {
          throw new Error('获取任务状态失败');
        }
      } catch (err) {
        statusError.value = err.message || '获取任务状态失败';
        console.error('获取任务状态错误:', err);
      } finally {
        loading.value = false;
      }
    };
    
    // 查看结果
    const viewResult = () => {
      if (taskInfo.value.resultId) {
        emit('analysisComplete', taskInfo.value.resultId);
      } else {
        ElMessage.warning('结果ID不存在，无法查看结果');
      }
    };
    
    // 重新分析
    const retryAnalysis = async () => {
      retrying.value = true;
      
      try {
        const response = await retryVideoAnalysis(taskId.value);
        
        if (response && response.data && response.data.success) {
          ElMessage.success('已重新提交分析任务');
          // 重置状态并开始轮询
          taskInfo.value.status = 'queued';
          taskInfo.value.progress = 0;
          startPolling();
        } else {
          throw new Error('重新分析失败');
        }
      } catch (err) {
        ElMessage.error(err.message || '重新分析失败');
      } finally {
        retrying.value = false;
      }
    };
    
    // 重置任务（清除taskId，返回到上传状态）
    const resetTask = () => {
      taskId.value = null;
      taskInfo.value = {};
      clearInterval(statusInterval.value);
      
      // 停止处理时间计时器
      stopProcessingTimer();
      
      // 取消STOMP订阅
      if (stompSubscription) {
        stompService.unsubscribe(`${STOMP_VIDEO_PROGRESS}/${taskId.value}`);
        stompSubscription = null;
      }
      
      // 清除会话存储的任务ID
      try {
        sessionStorage.removeItem('uploadState');
      } catch (err) {
        console.warn('无法清除会话存储中的上传状态:', err);
      }
    };
    
    // 开始轮询任务状态
    const startPolling = () => {
      // 先清除可能存在的轮询
      clearInterval(statusInterval.value);
      
      // 立即获取一次状态
      fetchStatus();
      
      // 初始化WebSocket连接
      initWebSocket(taskId.value);
      
      // 作为备份，设置轮询间隔（每5秒）
      statusInterval.value = setInterval(() => {
        fetchStatus();
      }, 5000);
    };

    // 启动处理时间计时器
    const startProcessingTimer = () => {
      // 如果已经启动过，则不重复启动
      if (processingStartTime.value) return;
      
      console.log('开始视频处理计时...');
      processingStartTime.value = Date.now();
      processingElapsedTime.value = 0;
      
      // 设置定时更新处理时间
      processingTimeInterval.value = setInterval(() => {
        if (processingStartTime.value) {
          processingElapsedTime.value = (Date.now() - processingStartTime.value) / 1000;
        }
      }, 1000);
    };
    
    // 停止处理时间计时器
    const stopProcessingTimer = async () => {
      if (!processingStartTime.value) return;
      
      const endTime = Date.now();
      const processingTimeSeconds = (endTime - processingStartTime.value) / 1000;
      
      console.log(`视频处理完成，耗时: ${processingTimeSeconds.toFixed(2)}秒`);
      
      // 清除计时器
      clearInterval(processingTimeInterval.value);
      
      // 尝试将处理时间记录到数据库
      try {
        if (taskId.value) {
          // 使用API函数发送处理时间
          await saveVideoProcessingTime(taskId.value, parseFloat(processingTimeSeconds.toFixed(2)));
          console.log('处理时间已记录到数据库');
        }
      } catch (err) {
        console.error('记录处理时间失败:', err);
      }
      
      // 重置计时状态
      processingStartTime.value = null;
      processingElapsedTime.value = processingTimeSeconds;
    };

    // 提交上传
    const submitUpload = async () => {
      // 表单验证
      if (!uploadForm.value) return
      
      // 添加调试日志
      console.log('开始上传视频，表单数据:', form)
      
      // 检查是否上传了文件
      if (form.roadType === 'normal') {
        if (!form.singleVideoFile) {
          ElMessage.warning('请先选择视频文件!')
          return
        }
      } else {
        if (!form.horizontalVideoFile || !form.verticalVideoFile) {
          ElMessage.warning('请上传横向和纵向视频文件!')
          return
        }
      }
      
      // 检查令牌有效性和格式
      const token = localStorage.getItem('auth_token')
      if (!token) {
        ElMessage.error('未找到认证令牌，请先登录')
        setTimeout(() => {
          router.push('/login')
        }, 1500)
        return
      }
      
      // 注册全局进度回调函数
      window.onVideoUploadProgress = (percent) => {
        uploadProgress.value = Math.floor(percent)
      }
      
      uploading.value = true
      uploadProgress.value = 0
      
      // 重置处理时间计时器
      if (processingTimeInterval.value) {
        clearInterval(processingTimeInterval.value);
      }
      processingStartTime.value = null;
      processingElapsedTime.value = 0;
      
      // 使用模拟进度条
      const progressInterval = simulateUploadProgress()
      
      try {
        const formData = new FormData()
        
        // 使用新的直接上传API
        let response
        
        if (form.roadType === 'normal') {
          // 单视频上传
          console.log('使用直接API上传单视频:', form.singleVideoFile.name, '大小:', form.singleVideoFile.size)
          
          try {
            // 调用新的直接上传API
            response = await uploadVideoDirectApi(
              form.singleVideoFile,
              form.direction,
              'normal'
            )
            console.log('直接API上传成功，响应:', response)
          } catch (directApiError) {
            console.error('直接API上传失败，尝试使用传统方式:', directApiError)
            
            // 检查是否是认证错误
            if (directApiError.authError || (directApiError.response && directApiError.response.status === 401)) {
              ElMessage.error('认证失败，请重新登录')
              clearInterval(progressInterval)
              uploading.value = false
              setTimeout(() => {
                router.push('/login')
              }, 1500)
              return
            }
            
            // 如果直接API失败，回退到传统方式
            formData.append('video', form.singleVideoFile)
            formData.append('roadType', 'normal')
            formData.append('direction', form.direction)
            
            // 添加当前用户信息到FormData
            try {
              const userStr = localStorage.getItem('user');
              if (userStr) {
                const user = JSON.parse(userStr);
                formData.append('userId', user.id);
                formData.append('username', user.username);
                formData.append('role', user.role || 'user');
                
                // 从令牌中提取信息作为备用
                const token = localStorage.getItem('auth_token');
                if (token) {
                  const parts = token.split('_');
                  if (parts.length >= 5) {
                    formData.append('tokenHash', parts[0]);
                    formData.append('tokenUsername', parts[1]);
                    formData.append('tokenUserId', parts[2]);
                    formData.append('tokenRole', parts[3]);
                    formData.append('tokenTimestamp', parts[4]);
                  }
                  formData.append('auth_token', token); // 作为备用
                }
              } else {
                console.warn('未找到用户信息');
              }
            } catch (e) {
              console.error('添加用户信息失败:', e);
            }
            
            // 传统API上传
            response = await uploadAndAnalyzeVideo(formData)
          }
        } else {
          // 十字路口双视频上传
          console.log('使用直接API上传十字路口视频')
          
          try {
            // 调用新的十字路口直接上传API
            response = await uploadIntersectionDirectApi(
              form.horizontalVideoFile,
              form.verticalVideoFile
            )
            console.log('直接API上传成功，响应:', response)
          } catch (directApiError) {
            console.error('直接API上传失败，尝试使用传统方式:', directApiError)
            
            // 检查是否是认证错误
            if (directApiError.authError || (directApiError.response && directApiError.response.status === 401)) {
              ElMessage.error('认证失败，请重新登录')
              clearInterval(progressInterval)
              uploading.value = false
              setTimeout(() => {
                router.push('/login')
              }, 1500)
              return
            }
            
            // 如果直接API失败，回退到传统方式
            formData.append('horizontalVideo', form.horizontalVideoFile)
            formData.append('verticalVideo', form.verticalVideoFile)
            formData.append('roadType', 'intersection')
            
            // 添加当前用户信息到FormData
            try {
              const userStr = localStorage.getItem('user');
              if (userStr) {
                const user = JSON.parse(userStr);
                formData.append('userId', user.id);
                formData.append('username', user.username);
                formData.append('role', user.role || 'user');
              }
            } catch (e) {
              console.error('添加用户信息失败:', e);
            }
            
            // 传统API上传
            response = await uploadAndAnalyzeVideo(formData)
          }
        }
        
        // 处理响应结果
        console.log('收到响应:', response)
        
        // 处理响应，兼容多种返回格式
        let resultTaskId = null
        
        if (response && response.data) {
          if (typeof response.data === 'string') {
            try {
              // 尝试解析可能的JSON字符串
              const parsedData = JSON.parse(response.data)
              resultTaskId = parsedData.taskId || parsedData.id
            } catch (e) {
              console.error('无法解析响应数据:', e)
            }
          } else if (response.data.taskId) {
            resultTaskId = response.data.taskId
          } else if (response.data.id) {
            resultTaskId = response.data.id
          }
        } else if (response && (response.taskId || response.id)) {
          resultTaskId = response.taskId || response.id
        }
        
        if (resultTaskId) {
          uploadProgress.value = 100
          taskId.value = resultTaskId
          
          ElMessage.success('视频上传成功，正在分析中')
          console.log('上传成功，任务ID:', resultTaskId)
          
          // 保存任务ID到会话存储
          try {
            sessionStorage.setItem('uploadState', JSON.stringify({ taskId: resultTaskId }))
          } catch (err) {
            console.warn('无法保存上传状态到会话存储:', err)
          }
          
          // 开始轮询任务状态
          startPolling();
          
          // 初始化WebSocket
          initWebSocket(resultTaskId)
          
          // 更新浏览器URL但不导航（用于刷新页面时能恢复状态）
          window.history.replaceState(
            history.state, 
            '', 
            `/video-status/${resultTaskId}`
          );
        } else {
          console.error('无法从响应中提取任务ID:', response)
          throw new Error('上传失败，未收到有效的任务ID')
        }
      } catch (error) {
        console.error('视频上传分析失败:', error)
          
        // 提供更友好的错误信息
        let errorMessage = '视频上传分析失败: '
        
        if (error.authError || (error.response && error.response.status === 401)) {
          errorMessage = '认证失败，请重新登录'
          
          setTimeout(() => {
            router.push('/login')
          }, 1500)
        } else if (error.response) {
          // 服务器返回的错误
          if (error.response.status === 401) {
            errorMessage += '认证失败，请重新登录'
            
            setTimeout(() => {
              router.push('/login')
            }, 1500)
          } else if (error.response.status === 413) {
            errorMessage += '文件太大，超出服务器限制'
          } else if (error.response.status === 500) {
            errorMessage += '服务器内部错误，请稍后重试'
          } else if (error.response.data && error.response.data.message) {
            errorMessage += error.response.data.message
          } else {
            errorMessage += `服务器错误 (${error.response.status})`
          }
        } else if (error.message) {
          // 客户端网络错误
          if (error.message.includes('Network Error')) {
            errorMessage += '网络连接失败，请检查网络'
          } else {
            errorMessage += error.message
          }
        } else {
          errorMessage += '未知错误'
        }
        
        ElMessage.error(errorMessage)
      } finally {
        clearInterval(progressInterval)
        uploading.value = false
        
        // 清除全局进度回调
        window.onVideoUploadProgress = null
      }
    }
    
    // 重置表单
    const resetForm = () => {
      if (uploadForm.value) {
        uploadForm.value.resetFields()
      }
      
      form.roadType = 'normal'
      form.direction = 'horizontal'
      
      // 清除视频文件和预览
      if (form.singleVideoFile) handleSingleVideoRemove()
      if (form.horizontalVideoFile) handleHorizontalVideoRemove()
      if (form.verticalVideoFile) handleVerticalVideoRemove()
      
      // 清空文件
      form.singleVideoFile = null
      form.horizontalVideoFile = null
      form.verticalVideoFile = null
      
      // 清空预览URL
      singleVideoPreviewUrl.value = ''
      horizontalVideoPreviewUrl.value = ''
      verticalVideoPreviewUrl.value = ''
      
      uploadProgress.value = 0
    }
    
    // 页面挂载前执行清理操作
    const clearPreviousState = () => {
      // 重置表单
      resetForm();
      
      // 清除任务ID
      taskId.value = null;
      taskInfo.value = {};
      
      // 清除上传进度
      uploadProgress.value = 0;
      
      // 清除错误信息
      statusError.value = '';
      
      // 清空预览URL
      singleVideoPreviewUrl.value = '';
      horizontalVideoPreviewUrl.value = '';
      verticalVideoPreviewUrl.value = '';
      currentPreviewUrl.value = '';
      
      // 重置加载状态
      loading.value = false;
      uploading.value = false;
      
      // 清除计时器
      stopProcessingTimer();
      
      // 清除可能存在的定时器
      clearInterval(statusInterval.value);
      
      // 取消STOMP订阅
      if (stompSubscription) {
        try {
          stompService.unsubscribe(`${STOMP_VIDEO_PROGRESS}/${taskId.value}`);
          stompSubscription = null;
        } catch (err) {
          console.warn('取消WebSocket订阅失败:', err);
        }
      }
      
      // 清除会话存储的任务ID
      try {
        sessionStorage.removeItem('uploadState');
          } catch (err) {
        console.warn('无法清除会话存储中的上传状态:', err);
          }
      
      console.log('视频上传表单已重置到初始状态');
    };
    
    // 页面挂载后执行
    onMounted(() => {
      // 首先清除先前的状态
      clearPreviousState();
      
      // 如果有initialTaskId，则开始轮询
      if (taskId.value) {
        startPolling();
      }
    });
    
    // 在组件销毁前执行
    onBeforeUnmount(() => {
      clearInterval(statusInterval.value);
    });
    
    // 页面卸载时清理资源
    onUnmounted(() => {
      // 取消STOMP订阅
      if (taskId.value && stompSubscription) {
        stompService.unsubscribe(`${STOMP_VIDEO_PROGRESS}/${taskId.value}`);
      }
      
      // 停止处理时间计时器
      stopProcessingTimer();
      
      // 清空预览URL引用
      singleVideoPreviewUrl.value = ''
      horizontalVideoPreviewUrl.value = ''
      verticalVideoPreviewUrl.value = ''
      currentPreviewUrl.value = ''
    });

    return {
      form,
      rules,
      uploadForm,
      fileInput,
      horizontalFileInput,
      verticalFileInput,
      uploading,
      uploadProgress,
      taskId,
      taskInfo,
      loading,
      statusError,
      retrying,
      progressStatus,
      singleVideoPreviewUrl,
      horizontalVideoPreviewUrl,
      verticalVideoPreviewUrl,
      currentPreviewUrl,
      previewDialogVisible,
      processingElapsedTime,
      formatFileSize,
      handleSingleVideoRemove,
      handleHorizontalVideoRemove,
      handleVerticalVideoRemove,
      submitUpload,
      resetForm,
      resetTask,
      fetchStatus,
      viewResult,
      retryAnalysis,
      triggerFileInput,
      handleFileDrop,
      handleFileChange,
      triggerHorizontalFileInput,
      handleHorizontalFileDrop,
      handleHorizontalFileChange,
      triggerVerticalFileInput,
      handleVerticalFileDrop,
      handleVerticalFileChange,
      clearPreviousState,
      isDragOver,
      isDragOverHorizontal,
      isDragOverVertical,
      onDragEnter,
      onDragLeave,
      onDragEnterHorizontal,
      onDragLeaveHorizontal,
      onDragEnterVertical,
      onDragLeaveVertical
    }
  }
}
</script>

<style scoped>
.upload-form {
  margin-bottom: 30px;
  max-width: 700px; /* 控制表单整体宽度 */
  margin-left: auto;
  margin-right: auto;
  display: flex;
  flex-direction: column;
  align-items: stretch; /* 让子元素水平撑满 */
}

.drop-area {
  border: 2px dashed rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  padding: 3rem 2rem;
  text-align: center;
  cursor: pointer;
  min-height: 250px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  transition: all 0.3s;
  background-color: rgba(17, 24, 39, 0.8);
  width: 100%;
}

.drop-area.drag-over {
  border-color: #6366f1;
  background-color: rgba(99, 102, 241, 0.05);
}

.drop-area.has-file {
  padding: 2rem;
  border-style: solid;
  min-height: 350px; /* 增加高度以适应视频播放器 */
}

.preview-container {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

/* 图标容器 */
.icon-container {
  position: relative;
  display: inline-block;
  margin-bottom: 0.5rem;
}

.upload-icon {
  font-size: 48px;
  color: #6366f1;
  filter: drop-shadow(0 0 8px rgba(99, 102, 241, 0.3));
}

.upload-arrow {
  position: absolute;
  bottom: -6px;
  right: -6px;
  background-color: #6366f1;
  border-radius: 50%;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: bounce 2s infinite;
}

.arrow-icon {
  color: white;
  width: 16px;
  height: 16px;
}

@keyframes bounce {
  0%, 20%, 50%, 80%, 100% {
    transform: translateY(0);
  }
  40% {
    transform: translateY(-8px);
  }
  60% {
    transform: translateY(-4px);
  }
}

/* 文字样式 */
.upload-title {
  color: #ffffff;
  font-size: 1.1rem;
  font-weight: 500;
  margin-bottom: 0.5rem;
  margin-top: 1rem;
}

.click-upload {
  color: #6366f1;
  font-weight: 500;
}

.upload-hint {
  color: #e5e7eb !important;
  font-size: 0.9rem;
}

/* 内嵌视频预览样式 */
.preview-video-inline {
  width: 100%;
  max-height: 200px; /* 控制内嵌视频的最大高度 */
  border-radius: 8px;
  object-fit: contain;
  margin-top: 15px; /* 与其他元素的间距 */
  border: 1px solid rgba(255, 255, 255, 0.1);
  background-color: rgba(0, 0, 0, 0.2);
}

/* 文件信息样式 */
.file-name {
  margin: 8px 0 4px;
  font-size: 14px;
  color: #e5e7eb; /* 亮色以增加可读性 */
  text-align: center;
  word-break: break-all;
  max-width: 90%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-size {
  color: #d1d5db;
  margin: 4px 0;
  font-size: 12px;
}

.file-icon {
  font-size: 42px;
  color: #909399;
  margin-bottom: 10px;
}

.video-preview {
  margin-top: 20px;
}

.upload-progress {
  margin-top: 20px;
  max-width: 700px;
  margin-left: auto;
  margin-right: auto;
}

.upload-progress p {
  color: #e5e7eb;
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 500;
}

.status-section {
  padding: 20px 0;
  max-width: 700px;
  margin: 0 auto;
  background: rgba(17, 24, 39, 0.4);
  border-radius: 10px;
  padding: 30px;
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.status-progress {
  margin: 20px 0;
}

.status-progress p {
  color: #e5e7eb;
  margin-bottom: 10px;
  font-size: 15px;
  font-weight: 500;
}

.status-actions {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.completed-actions, .failed-actions, .processing-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.view-result-btn {
  min-width: 160px;
  background-color: #4f46e5;
  border-color: #4f46e5;
  font-weight: 600;
  font-size: 16px;
  padding: 12px 24px;
  border-radius: 8px;
  transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.3);
}

.view-result-btn:hover {
  background-color: #6366f1;
  border-color: #6366f1;
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(79, 70, 229, 0.4);
}

.view-icon {
  margin-right: 8px;
  font-size: 18px;
  vertical-align: middle;
}

.reset-btn {
  min-width: 120px;
}

:deep(.custom-alert) {
  background-color: rgba(17, 24, 39, 0.6) !important;
  color: #e5e7eb !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  padding: 15px !important;
  border-radius: 8px !important;
}

:deep(.custom-alert .el-alert__title) {
  color: #ffffff !important;
  font-size: 16px !important;
}

:deep(.custom-alert .el-alert__description) {
  color: #d1d5db !important;
  margin-top: 8px !important;
}

:deep(.custom-alert.el-alert--info .el-alert__icon) {
  color: #6366f1 !important;
}

:deep(.custom-alert.el-alert--success .el-alert__icon) {
  color: #10b981 !important;
}

:deep(.custom-alert.el-alert--error .el-alert__icon) {
  color: #ef4444 !important;
}

:deep(.el-skeleton) {
  --el-skeleton-color: rgba(255, 255, 255, 0.05);
  --el-skeleton-to-color: rgba(255, 255, 255, 0.15);
}

/* 表单项间距和布局 */
:deep(.el-form-item) {
  margin-bottom: 24px; /* 增加表单项之间的间距 */
  width: 100%; /* 确保表单项宽度一致 */
}

/* 表单内容区域 */
:deep(.el-form-item__content) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

/* 标签样式 */
:deep(.el-form-item__label) {
  color: #e5e7eb !important;
  font-weight: 600;
  font-size: 16px;
  padding-bottom: 8px;
  text-align: left;
  margin-left: 4px;
}

/* 强化单选按钮选中效果 */
:deep(.el-radio-group) {
  display: flex;
  margin-bottom: 16px;
  gap: 16px; /* 增加按钮间距 */
  justify-content: center; /* 让按钮居中排列 */
  width: 100%;
}

:deep(.el-radio-button) {
  margin-right: 0;
  flex: 1; /* 让按钮平均分配空间 */
  max-width: 200px; /* 限制最大宽度 */
}

:deep(.el-radio-button__inner) {
  background-color: #1f2937 !important;
  border: 2px solid #374151 !important;
  color: #e5e7eb !important;
  padding: 12px 24px !important;
  font-weight: 500 !important;
  border-radius: 8px !important;
  height: auto !important;
  line-height: 1.5 !important;
  box-shadow: none !important;
  position: relative;
  overflow: visible;
  width: 100%; /* 使按钮宽度充满容器 */
  text-align: center;
  transition: all 0.3s ease;
}

:deep(.el-radio-button:first-child .el-radio-button__inner),
:deep(.el-radio-button:last-child .el-radio-button__inner) {
  border-radius: 8px !important;
}

:deep(.el-radio-button__original) {
  opacity: 0;
}

:deep(.el-radio-button.is-active .el-radio-button__inner) {
  background-color: #6366f1 !important;
  border-color: #6366f1 !important;
  color: white !important;
  box-shadow: 0 0 0 2px rgba(99, 102, 241, 0.4) !important;
  z-index: 1;
}

:deep(.el-radio-button.is-active .el-radio-button__inner::after) {
  content: "✓";
  position: absolute;
  top: -8px;
  right: -8px;
  background-color: #6366f1;
  color: white;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

/* 下拉框容器 */
:deep(.el-select-wrapper) {
  width: 100%;
  display: flex;
  justify-content: center;
}

/* 修复下拉框背景色和宽度 */
:deep(.el-select) {
  width: 300px !important; /* 将下拉框宽度限制为300px */
  max-width: 100%; /* 最大宽度限制，使其与其他控件宽度一致 */
}

:deep(.el-select .el-input__wrapper) {
  background-color: #1f2937 !important;
  border: 2px solid #374151 !important;
  box-shadow: none !important;
  color: #e5e7eb !important;
  width: 100% !important; /* 宽度占满 */
  min-width: unset; /* 移除最小宽度限制 */
  padding: 0 16px !important; /* 调整内边距 */
  height: 48px !important; /* 固定高度 */
}

:deep(.el-select .el-input__inner) {
  color: #e5e7eb !important;
  background-color: transparent !important;
  font-size: 15px; /* 增加字体大小 */
}

/* 控制下拉菜单的颜色和宽度 */
:global(.el-select__popper) {
  width: auto !important;
  max-width: 300px !important; /* 与选择框宽度一致 */
}

:global(.el-popper.is-light) {
  background-color: #1f2937 !important;
  border: 1px solid #374151 !important;
  color: #e5e7eb !important;
}

:global(.el-select-dropdown__item) {
  color: #e5e7eb !important;
  padding: 12px 20px !important; /* 增加选项内边距 */
  font-size: 15px; /* 增加字体大小 */
  display: flex !important; /* 确保垂直居中 */
  align-items: center !important; /* 确保垂直居中 */
  height: 36px !important; /* 固定高度 */
  line-height: normal !important; /* 重置行高 */
}

:global(.el-select-dropdown__item span) {
  display: inline-block !important;
  line-height: normal !important;
  vertical-align: middle !important;
}

:global(.el-select-dropdown__item.hover),
:global(.el-select-dropdown__item:hover) {
  background-color: rgba(99, 102, 241, 0.1) !important;
}

:global(.el-select-dropdown__item.selected) {
  color: #6366f1 !important;
  font-weight: bold !important;
}

/* 确保下拉框元素样式正确 */
:global(.el-select__wrapper) {
  background-color: #1f2937 !important;
  border-color: #374151 !important;
  width: 100% !important; /* 宽度占满 */
}

:global(.el-select__input) {
  color: #e5e7eb !important;
  background-color: transparent !important;
}

:global(.el-select-dropdown) {
  background-color: #1f2937 !important;
  border: 1px solid #374151 !important;
  width: auto !important; /* 匹配选择框宽度 */
}

:global(.el-select .el-select__selection) {
  color: #e5e7eb !important;
  width: 100%; /* 确保内容区域宽度一致 */
}

:global(.el-select__selected-item) {
  font-size: 15px; /* 增加字体大小 */
}

/* 按钮容器 */
:deep(.el-form-item:last-child .el-form-item__content) {
  display: flex;
  flex-direction: row;
  justify-content: center;
  margin-top: 16px;
}

/* 美化按钮 */
:deep(.el-button) {
  border-radius: 8px;
  transition: all 0.3s;
  border: 2px solid rgba(255, 255, 255, 0.2);
  background-color: rgba(255, 255, 255, 0.05);
  color: #e5e7eb;
  padding: 12px 28px;
  height: 48px;
  font-weight: 500;
  font-size: 15px;
  min-width: 140px; /* 按钮最小宽度增加 */
  margin: 0 8px; /* 调整按钮间距，使用margin代替margin-right */
}

:deep(.el-button:hover) {
  background-color: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.3);
  color: #ffffff;
  transform: translateY(-2px);
}

:deep(.el-button:active) {
  transform: translateY(0);
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
}

:deep(.el-button--primary:active) {
  background-color: #4338ca !important; /* 点击时颜色更深 */
  border-color: #4338ca !important;
  box-shadow: none;
}

/* 媒体查询 - 适配小屏幕 */
@media (max-width: 768px) {
  .upload-form {
    padding: 0 10px;
  }
  
  :deep(.el-radio-group) {
    flex-direction: column;
    align-items: center;
  }
  
  :deep(.el-radio-button) {
    max-width: 100%;
    width: 100%;
    margin-bottom: 10px;
  }
  
  :deep(.el-form-item:last-child .el-form-item__content) {
    flex-direction: column;
    align-items: center;
  }
  
  :deep(.el-button) {
    margin: 8px 0;
    width: 100%;
  }
}

.file-upload-area {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: border-color 0.3s;
  height: 180px;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #fafafa;
}

.file-upload-area:hover {
  border-color: #409eff;
}

.preview-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  position: relative;
  padding: 20px;
}

.file-icon {
  font-size: 42px;
  color: #909399;
  margin-bottom: 10px;
}

.file-name {
  margin: 8px 0 4px;
  font-size: 14px;
  color: #606266;
  text-align: center;
  word-break: break-all;
  max-width: 90%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-size {
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}

.remove-file-btn {
  margin-top: 8px;
}

.video-preview {
  margin-top: 20px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 15px;
  background-color: #f9f9f9;
}

.video-preview h4 {
  margin-top: 0;
  margin-bottom: 15px;
  color: #303133;
  font-weight: 500;
  font-size: 16px;
}

.preview-video {
  width: 100%;
  max-height: 300px;
  object-fit: contain;
  border-radius: 4px;
}

/* 确保文件名不会重复显示 */
.el-form-item__content .file-name {
  display: block;
}

/* 隐藏视频预览外部可能显示的文件名 */
.video-preview ~ .file-name,
.video-preview + .file-name,
.el-form-item__content > .file-name:not(.preview-container .file-name) {
  display: none !important;
}

.preview-video-inline {
  width: 100%;
  max-height: 300px;
  object-fit: contain;
  border-radius: 4px;
}

:deep(.custom-progress) {
  margin-bottom: 20px;
}

:deep(.custom-progress .el-progress-bar__outer) {
  background-color: rgba(255, 255, 255, 0.1) !important;
  border-radius: 10px;
}

:deep(.custom-progress .el-progress-bar__inner) {
  background-color: #6366f1 !important;
  border-radius: 10px;
}

:deep(.custom-progress .el-progress-bar__innerText) {
  color: #ffffff;
  font-size: 14px;
  font-weight: 500;
}

:deep(.preview-dialog .el-dialog__header) {
  background-color: rgba(26, 32, 50, 0.8);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 15px 20px;
}

:deep(.preview-dialog .el-dialog__title) {
  color: #e5e7eb;
  font-weight: 600;
}

:deep(.preview-dialog .el-dialog__body) {
  background-color: #111827;
  padding: 20px;
}

:deep(.preview-dialog .el-dialog__close) {
  color: #e5e7eb;
}

:deep(.preview-dialog .el-dialog__headerbtn:hover .el-dialog__close) {
  color: #6366f1;
}

/* 处理时间显示样式 */
.processing-time {
  margin: 20px 0;
  background-color: rgba(17, 24, 39, 0.6);
  border-radius: 8px;
  padding: 10px 15px;
  border: 1px solid rgba(99, 102, 241, 0.2);
}

.processing-time p {
  display: flex;
  align-items: center;
  color: #e5e7eb;
  font-size: 16px;
  margin: 0;
}

.processing-time .el-icon {
  color: #6366f1;
  margin-right: 8px;
  font-size: 18px;
}

.time-value {
  color: #6366f1;
  font-weight: 600;
  margin: 0 5px;
}

.time-header {
  display: flex;
  align-items: center;
  color: #e5e7eb;
  font-size: 16px;
  margin-bottom: 10px;
}

.time-value-container {
  display: flex;
  align-items: baseline;
}

.time-unit {
  color: #d1d5db;
  font-size: 14px;
  margin-left: 5px;
}

.processing-complete {
  color: #10b981;
  font-size: 16px;
  margin-top: 10px;
}

.timer-animation {
  display: flex;
  align-items: center;
  margin-left: 10px;
}

.dot {
  width: 4px;
  height: 4px;
  background-color: #d1d5db;
  border-radius: 50%;
  margin: 0 2px;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style> 