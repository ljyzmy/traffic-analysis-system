<template>
  <div class="upload-container">
    <div class="alert alert-danger" v-if="error" style="display: block;">{{ error }}</div>
    
    <div class="alert alert-warning" v-if="offlineMode" style="display: block;">
      <strong>服务器连接不稳定!</strong> 检测到与分析服务器的连接不稳定，可能影响分析功能。
    </div>
    
    <form @submit.prevent="submitForm">
      <div class="mb-3">
        <div 
          class="drop-area" 
          :class="{ 'drag-over': isDragOver, 'has-image': hasFilePreview }"
          @dragenter="onDragEnter"
          @dragleave="onDragLeave"
          @dragover.prevent
          @drop="onDrop"
          @click="triggerFileInput"
        >
          <div id="dropText" v-if="!hasFilePreview">
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
            <p class="upload-title">拖放图片到这里或点击选择文件</p>
            <p class="upload-hint">仅支持图像格式: JPG, PNG, JPEG (最大10MB)</p>
          </div>
          <div v-else class="preview-container">
            <img :src="filePreview" class="preview-image">
          </div>
        </div>
      </div>
      
      <input 
        type="file" 
        ref="fileInput" 
        class="form-control"
        style="display: none;"
        accept="image/jpeg,image/jpg,image/png"
        @change="onFileChange"
      >
      
      <div class="button-container">
        <button type="submit" class="el-button el-button--primary" :disabled="!selectedFile || isLoading">
          <span v-if="isLoading" class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>
          {{ isLoading ? '分析中...' : '开始分析' }}
        </button>
        <button type="button" class="el-button" @click="resetForm">重置</button>
      </div>
      
      <!-- 进度显示 -->
      <div id="progress-container" class="mt-4" v-if="isLoading">
        <div class="d-flex justify-content-between mb-1">
          <span>分析进度</span>
          <span id="progress-text">{{ progress }}%</span>
        </div>
        <div class="progress" style="height: 20px;">
          <div class="progress-bar progress-bar-striped progress-bar-animated" 
               role="progressbar" 
               :style="{width: progress + '%'}" 
               :aria-valuenow="progress" 
               aria-valuemin="0" 
               aria-valuemax="100"></div>
        </div>
        <div class="small text-muted mt-2" id="status-message">{{ statusMessage }}</div>
      </div>
    </form>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { uploadVideo, getModelStatus } from '@/api/traffic';

export default {
  name: 'UploadForm',
  setup() {
    const router = useRouter();
    const store = useStore();
    
    const selectedFile = ref(null);
    const filePreview = ref(null);
    const fileType = ref(null);
    const error = ref(null);
    const isLoading = ref(false);
    const isDragOver = ref(false);
    const progress = ref(0);
    const statusMessage = ref('准备分析中...');
    const fileInput = ref(null);
    const offlineMode = ref(false);
    let progressInterval = null;
    
    const hasFilePreview = computed(() => !!filePreview.value);
    
    const triggerFileInput = () => {
      fileInput.value.click();
    };
    
    const onFileChange = (event) => {
      const file = event.target.files[0];
      if (file) {
        if (validateFile(file)) {
          selectedFile.value = file;
          createPreview(file);
        }
      }
    };
    
    const onDragEnter = (event) => {
      event.preventDefault();
      isDragOver.value = true;
    };
    
    const onDragLeave = (event) => {
      event.preventDefault();
      isDragOver.value = false;
    };
    
    const onDrop = (event) => {
      event.preventDefault();
      isDragOver.value = false;
      
      const file = event.dataTransfer.files[0];
      if (file && validateFile(file)) {
        selectedFile.value = file;
        createPreview(file);
      }
    };
    
    const validateFile = (file) => {
      // 验证文件类型
      const validImageTypes = ['image/jpeg', 'image/jpg', 'image/png'];
      
      if (!validImageTypes.includes(file.type)) {
        error.value = '请上传有效的图片文件 (JPG, PNG, JPEG)';
        return false;
      }
      
      // 设置文件类型为图片
        fileType.value = 'image';
        
        // 验证图片文件大小 (10MB)
        const maxImageSize = 10 * 1024 * 1024;
        if (file.size > maxImageSize) {
          error.value = '图片文件大小超过限制 (最大10MB)';
          return false;
      }
      
      error.value = null;
      return true;
    };
    
    const createPreview = (file) => {
      const reader = new FileReader();
      reader.onload = (e) => {
        filePreview.value = e.target.result;
      };
      reader.readAsDataURL(file);
    };
    
    const resetForm = () => {
      selectedFile.value = null;
      filePreview.value = null;
      fileType.value = null;
      error.value = null;
      isLoading.value = false;
      progress.value = 0;
      statusMessage.value = '准备分析中...';
      
      if (progressInterval) {
        clearInterval(progressInterval);
        progressInterval = null;
      }
      
      if (fileInput.value) {
        fileInput.value.value = '';
      }
    };
    
    const startProgressSimulation = () => {
      progress.value = 0;
      const maxProgress = 95; // 最大模拟进度
      let progressStep = 2; // 图片处理速度较快
      
      progressInterval = setInterval(() => {
        if (progress.value >= maxProgress) {
          clearInterval(progressInterval);
          return;
        }
        
        // 根据进度阶段更新状态消息
        if (progress.value < 20) {
          statusMessage.value = "正在上传图像...";
        } else if (progress.value < 40) {
          statusMessage.value = "图像预处理中...";
        } else if (progress.value < 60) {
          statusMessage.value = "检测车辆中...";
        } else if (progress.value < 80) {
          statusMessage.value = "计算统计数据中...";
        } else {
          statusMessage.value = "生成分析结果中...";
        }
        
        progress.value += Math.random() * progressStep + 0.1;
        progress.value = Math.min(progress.value, maxProgress);
        progress.value = Math.round(progress.value); // 取整数
      }, 300);
    };
    
    const submitForm = async () => {
      if (!selectedFile.value) {
        error.value = '请先选择图片文件';
        return;
      }
      
      // 检查是否登录
      const authToken = localStorage.getItem('auth_token');
      if (!authToken) {
        error.value = '请先登录后再上传文件';
        router.push('/login');
        return;
      }
      
      isLoading.value = true;
      error.value = null;
      
      try {
        // 检查服务器状态
        await checkServerStatus();
        
        if (offlineMode.value) {
          error.value = '服务器连接不稳定，无法上传文件';
          return;
        }
        
        // 开始进度模拟
        startProgressSimulation();
        
        // 创建表单数据
        const formData = new FormData();
        // 根据当前的API路径格式要求，确保文件字段名正确
        formData.append('image', selectedFile.value);
        
        // 发送请求
        console.log('开始上传文件:', selectedFile.value.name);
        
        // 使用修改后的上传函数
        const response = await uploadVideo(formData);
        console.log('上传响应:', response);
        
        // 检查是否返回了HTML (可能是重定向到登录页面)
        if (typeof response === 'string' && response.includes('<!DOCTYPE html>')) {
          console.error('上传请求接收到HTML响应，可能是认证问题导致重定向到登录页面');
          throw new Error('认证失败，请重新登录');
        }
        
        // 停止进度模拟
        if (progressInterval) {
          clearInterval(progressInterval);
        }
        
        // 处理响应
        let analysisId = null;
        
        // 兼容多种响应格式
        if (response.data && response.data.analysisId) {
          analysisId = response.data.analysisId;
        } else if (response.analysisId) {
          analysisId = response.analysisId;
        } else if (response.id) {
          // 处理直接返回id的情况
          analysisId = response.id;
        } else if (response.data && response.data.id) {
          // 处理data中包含id的情况
          analysisId = response.data.id;
        }
        
        if (analysisId) {
          // 设置100%进度
          progress.value = 100;
          statusMessage.value = "分析完成，正在跳转到结果页面...";
          
          // 保存当前分析ID到Vuex
          store.commit('SET_CURRENT_ANALYSIS', analysisId);
          
          // 跳转到结果页面
          setTimeout(() => {
            router.push(`/result/${analysisId}`);
          }, 1000);
        } else {
          throw new Error(response.message || '分析失败，未收到有效结果');
        }
      } catch (error) {
        console.error('上传分析出错:', error);
        
        // 检查是否是认证错误
        if (error.response && error.response.status === 401) {
          error.value = '认证失败，请重新登录';
          // 不要清除认证令牌，而是直接跳转到登录页
          // localStorage.removeItem('auth_token');
          // localStorage.removeItem('user');
          
          // 跳转到登录页面
          setTimeout(() => {
            router.push('/login');
          }, 1500);
        } else {
          error.value = `处理请求时出错: ${error.message || '未知错误'}`;
        }
        
        // 清除进度模拟
        if (progressInterval) {
          clearInterval(progressInterval);
          progressInterval = null;
        }
      } finally {
        isLoading.value = false;
      }
    };
    
    // 检查服务器状态
    const checkServerStatus = async () => {
      try {
        await getModelStatus();
        offlineMode.value = false;
      } catch (error) {
        console.warn('服务器连接不稳定:', error);
        offlineMode.value = true;
      }
    };
    
    onMounted(() => {
      checkServerStatus();
    });
    
    return {
      selectedFile,
      filePreview,
      fileType,
      hasFilePreview,
      error,
      isLoading,
      isDragOver,
      progress,
      statusMessage,
      fileInput,
      triggerFileInput,
      onFileChange,
      onDragEnter,
      onDragLeave,
      onDrop,
      resetForm,
      submitForm,
      offlineMode
    };
  }
};
</script>

<style scoped>
.upload-container {
  width: 100%;
}

.drop-area {
  border: 2px dashed rgba(255, 255, 255, 0.3);
  border-radius: 8px;
  padding: 3rem 2rem;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 1.5rem;
  background-color: rgba(255, 255, 255, 0.02);
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.drop-area.drag-over {
  border-color: #3b82f6;
  background-color: rgba(59, 130, 246, 0.05);
}

.drop-area.has-image {
  padding: 0;
  border: none;
  min-height: auto;
}

.preview-container {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.preview-image {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
  object-fit: contain;
}

.preview-video {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
}

.progress {
  height: 20px;
}

/* 按钮容器 */
.button-container {
  display: flex;
  flex-direction: row;
  justify-content: center;
  margin-top: 16px;
  gap: 16px;
}

/* 美化按钮 - 复制自VideoUploadForm.vue */
.el-button {
  border-radius: 8px;
  transition: all 0.3s;
  border: 2px solid rgba(255, 255, 255, 0.2);
  background-color: rgba(255, 255, 255, 0.05);
  color: #e5e7eb;
  padding: 12px 28px;
  height: 48px;
  font-weight: 500;
  font-size: 15px;
  min-width: 140px;
  margin: 0 8px;
  cursor: pointer;
}

.el-button:hover {
  background-color: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.3);
  color: #ffffff;
  transform: translateY(-2px);
}

.el-button:active {
  transform: translateY(0);
}

.el-button--primary {
  background-color: #6366f1 !important;
  border-color: #6366f1 !important;
  color: #ffffff !important;
}

.el-button--primary:hover {
  background-color: #4f46e5 !important;
  border-color: #4f46e5 !important;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

.el-button--primary:active {
  background-color: #4338ca !important;
  border-color: #4338ca !important;
  box-shadow: none;
}

.el-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
}

.alert {
  border-radius: 4px;
}

/* 调整文字颜色和样式 */
.upload-title {
  color: #ffffff;
  font-size: 1.1rem;
  font-weight: 500;
  margin-bottom: 0.5rem;
  margin-top: 1rem;
}

.upload-hint {
  color: #e5e7eb !important;
  font-size: 0.9rem;
}

/* 调整图标样式 */
.icon-container {
  position: relative;
  display: inline-block;
  margin-bottom: 0.5rem;
}

.upload-icon {
  color: #3b82f6;
  filter: drop-shadow(0 0 8px rgba(59, 130, 246, 0.3));
}

.upload-arrow {
  position: absolute;
  bottom: -6px;
  right: -6px;
  background-color: #3b82f6;
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

/* 媒体查询 */
@media (max-width: 768px) {
  .button-container {
    flex-direction: column;
  }
  
  .el-button {
    margin: 8px 0;
    width: 100%;
  }
}
</style> 