<template>
  <div class="video-player-panel">
    <div v-if="videoUrl" class="video-player-container">
      <h3>分析结果视频</h3>
      <div class="video-controls">
        <el-button size="small" type="primary" @click="takeScreenshot">
          <el-icon><camera /></el-icon> 截图
        </el-button>
      </div>
      <div class="video-player" :class="{ 'video-loading': loading }">
        <div v-if="loading" class="video-loading-overlay">
          <el-icon class="loading-icon"><loading /></el-icon>
          <p>视频加载中...</p>
        </div>
        <video 
          ref="videoPlayer"
          controls 
          width="100%" 
          :src="videoUrl"
          @loadstart="handleVideoLoadStart"
          @canplay="handleVideoLoad"
          @error="handleVideoError"
        >
          您的浏览器不支持视频播放
        </video>
        <div v-if="error" class="video-error-message">
          <el-alert
            title="视频加载失败"
            type="error"
            description="请检查网络连接或视频格式"
            show-icon
            :closable="false"
          ></el-alert>
        </div>
      </div>
    </div>
    <div v-else-if="processingStatus" class="processing-message">
      <el-alert
        title="视频正在处理中"
        type="info"
        description="请稍后查看分析完成的视频"
        show-icon
        :closable="false"
      ></el-alert>
      <el-progress 
        style="margin-top: 20px;"
        :percentage="processingProgress"
        :stroke-width="12"
      ></el-progress>
    </div>
    <div v-else class="no-video-message">
      <el-empty description="没有可用的视频" :image-size="100"></el-empty>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Camera, Loading } from '@element-plus/icons-vue';
import { createVideoBlobUrl } from '@/api/media';
import config from '@/config/env';

export default {
  name: 'VideoPlayerPanel',
  components: {
    Camera,
    Loading
  },
  props: {
    // 视频路径或URL
    videoPath: {
      type: String,
      default: ''
    },
    // 视频处理状态
    status: {
      type: String,
      default: ''
    },
    // 处理进度百分比
    progress: {
      type: Number,
      default: 0
    }
  },
  emits: ['screenshot'],
  setup(props, { emit }) {
    const videoPlayer = ref(null);
    const loading = ref(false);
    const error = ref(false);
    const videoReady = ref(false);
    
    // 处理视频路径，返回可用的URL
    const videoUrl = ref('');
    
    // 计算属性，判断是否在处理中
    const processingStatus = ref(false);
    const processingProgress = ref(0);
    
    // 始终使用代理模式
    const useProxy = true;
    
    // 监听视频加载状态
    const handleVideoLoad = () => {
      console.log('视频加载成功，准备就绪');
      videoReady.value = true;
      loading.value = false;
    };
    
    const handleVideoError = (e) => {
      console.error('视频加载失败:', e);
      console.error('错误详情:', e.target?.error);
      error.value = true;
      loading.value = false;
      // 尝试获取详细错误信息
      const videoElement = e.target;
      if (videoElement && videoElement.error) {
        const errorCode = videoElement.error.code;
        let errorMessage = '未知错误';
        switch (errorCode) {
          case 1:
            errorMessage = '加载中断 - 可能是网络问题';
            break;
          case 2:
            errorMessage = '网络错误 - 无法下载视频';
            break;
          case 3:
            errorMessage = '解码错误 - 视频格式不支持';
            break;
          case 4:
            errorMessage = '视频源不可用或格式不支持';
            break;
        }
        ElMessage.error(`视频加载失败: ${errorMessage} (错误代码: ${errorCode})`);
      } else {
        ElMessage.error('视频加载失败，请检查网络连接或视频格式');
      }
    };
    
    const handleVideoLoadStart = () => {
      console.log('视频开始加载...');
      loading.value = true;
      videoReady.value = false;
      error.value = false;
    };
    
    // 视频截图功能
    const takeScreenshot = () => {
      if (!videoPlayer.value) {
        ElMessage.warning('视频播放器不可用');
        return;
      }
      
      try {
        // 暂停视频
        videoPlayer.value.pause();
        
        // 创建canvas
        const canvas = document.createElement('canvas');
        canvas.width = videoPlayer.value.videoWidth;
        canvas.height = videoPlayer.value.videoHeight;
        
        // 在canvas上绘制当前视频帧
        const ctx = canvas.getContext('2d');
        ctx.drawImage(videoPlayer.value, 0, 0, canvas.width, canvas.height);
        
        // 转换为图片并下载
        const dataURL = canvas.toDataURL('image/png');
        
        // 发出截图事件并传递截图数据
        emit('screenshot', dataURL);
        
        // 下载截图
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
      } catch (err) {
        ElMessage.error('截图失败: ' + err.message);
        console.error('截图错误:', err);
      }
    };
    
    // 检查URL是否为Base64数据
    const isBase64 = (url) => {
      return url && (url.startsWith('data:image') || url.startsWith('data:video'));
    };
    
    // 处理视频路径，确保返回有效的URL
    const getVideoUrl = async (path) => {
      if (!path) return '';
      
      // 检查是否为Base64数据或Blob URL
      if (isBase64(path) || path.startsWith('blob:')) {
        return path;
      }
      
      try {
        console.log('使用代理模式加载视频');
        // 如果是视频ID，使用代理API获取视频内容并创建Blob URL
        if (/^[0-9a-f]{24}$/i.test(path)) {
          console.log('检测到视频ID格式，使用代理API加载:', path);
          const blobUrl = await createVideoBlobUrl(path);
          console.log('成功创建视频Blob URL:', blobUrl);
          return blobUrl;
        }
        
        // 如果是路径，也使用代理API
        if (path.startsWith('/api/media/video/')) {
          const videoId = path.split('/').pop();
          console.log('提取视频ID:', videoId);
          const blobUrl = await createVideoBlobUrl(videoId);
          console.log('成功创建视频Blob URL:', blobUrl);
          return blobUrl;
        }
        
        // 其他类型的URL
        return path;
      } catch (err) {
        console.error('获取视频URL失败:', err);
        error.value = true;
        ElMessage.error('视频加载失败: ' + err.message);
        return '';
      }
    };
    
    onMounted(async () => {
      // 设置视频URL
      if (props.videoPath) {
        console.log('视频播放器组件 - 原始路径:', props.videoPath);
        loading.value = true;
        try {
          videoUrl.value = await getVideoUrl(props.videoPath);
          console.log('视频播放器组件 - 修正后URL:', videoUrl.value);
        } catch (err) {
          console.error('加载视频失败:', err);
          error.value = true;
        } finally {
          loading.value = false;
        }
      }
      
      // 设置处理状态
      processingStatus.value = props.status === 'processing';
      processingProgress.value = props.progress || 0;
    });
    
    // 监听props变化
    const updateProps = async () => {
      if (props.videoPath) {
        console.log('视频播放器组件(更新) - 原始路径:', props.videoPath);
        loading.value = true;
        try {
          videoUrl.value = await getVideoUrl(props.videoPath);
          console.log('视频播放器组件(更新) - 修正后URL:', videoUrl.value);
        } catch (err) {
          console.error('更新视频URL失败:', err);
          error.value = true;
        } finally {
          loading.value = false;
        }
      } else {
        videoUrl.value = '';
      }
      
      processingStatus.value = props.status === 'processing';
      processingProgress.value = props.progress || 0;
    };
    
    return {
      videoPlayer,
      loading,
      error,
      videoReady,
      videoUrl,
      processingStatus,
      processingProgress,
      handleVideoLoad,
      handleVideoError,
      handleVideoLoadStart,
      takeScreenshot,
      isBase64
    };
  },
  watch: {
    videoPath() {
      this.updateProps();
    },
    status() {
      this.updateProps();
    },
    progress() {
      this.updateProps();
    }
  }
};
</script>

<style scoped>
.video-player-panel {
  width: 100%;
}

.video-player-container {
  margin-top: 20px;
}

.video-controls {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.video-player {
  position: relative;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.3);
  background: rgba(0, 0, 0, 0.2);
}

.video-loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.7);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;
  z-index: 10;
}

.loading-icon {
  font-size: 30px;
  animation: rotating 2s linear infinite;
  color: #6366f1;
}

@keyframes rotating {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.video-error-message {
  margin-top: 15px;
}

.processing-message {
  padding: 30px 0;
}

.no-video-message {
  padding: 50px 0;
  text-align: center;
}

h3 {
  color: #ffffff !important;
  font-weight: 600 !important;
  margin-bottom: 15px;
}
</style> 