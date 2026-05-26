import {
  uploadAndAnalyzeVideo,
  uploadWithAxios,
} from "@/api/video";

/**
 * 上传和分析视频
 */
const uploadAndAnalyze = () => {
  // 检查是否有视频文件
  if ((isIntersection.value && (!horizontalVideo.value || !verticalVideo.value)) ||
      (!isIntersection.value && !videoFile.value)) {
    ElMessage.error('请先选择视频文件');
    return;
  }
  
  // 显示上传进度
  uploading.value = true;
  progress.value = 0;
  
  // 准备FormData
  const formData = new FormData();
  
  if (isIntersection.value) {
    // 十字路口分析（两个视频）
    formData.append('horizontalVideo', horizontalVideo.value);
    formData.append('verticalVideo', verticalVideo.value);
    formData.append('roadType', 'intersection');
  } else {
    // 单方向分析（一个视频）
    formData.append('video', videoFile.value);
    formData.append('direction', direction.value);
    formData.append('roadType', 'single');
  }
  
  // 定义进度回调函数
  window.onVideoUploadProgress = (percent) => {
    progress.value = percent;
  };
  
  // 使用新的axios上传方法上传视频
  uploadWithAxios(formData)
    .then(response => {
      // 上传成功处理
      const data = response.data;
      console.log('视频上传分析成功:', data);
      
      // 显示成功消息
      ElMessage.success('视频上传成功，开始分析处理');
      
      // 存储任务ID
      taskId.value = data.taskId || data.id;
      analysisStatus.value = 'processing';
      
      // 开始轮询任务状态
      pollingTimer = setTimeout(() => {
        checkTaskStatus(taskId.value);
      }, POLLING_INTERVAL);
    })
    .catch(error => {
      console.error('视频上传失败:', error);
      ElMessage.error(error.message || '视频上传失败，请重试');
      
      // 处理认证错误
      if (error.authError) {
        handleAuthenticationError();
      }
    })
    .finally(() => {
      uploading.value = false;
      
      // 清除进度回调函数
      window.onVideoUploadProgress = null;
    });
  
  // 备用上传方法 (使用原生XHR上传)
  /*
  uploadAndAnalyzeVideo(formData)
    .then(...同上...)
    .catch(...同上...)
    .finally(...同上...);
  */
}; 