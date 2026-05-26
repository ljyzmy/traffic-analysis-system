<template>
    <div class="upload-container">
    <h1 class="page-title">上传交通图像进行分析</h1>
      
    <div class="alert alert-danger" v-if="error">{{ error }}</div>
    <div class="alert alert-danger" v-if="!modelStatus && statusErrorCount > 2" style="display: block;">
      <strong>系统状态异常!</strong> 检测到Python分析服务器无法连接，可能导致功能受限
    </div>
    
    <div class="main-content">
      <div class="content-grid">
        <div class="form-section">
          <UploadForm />
        </div>
        
        <div class="info-section">
          <div class="feature-card">
            <div class="card-header">
              <h5 class="card-title">
                <i class="bi bi-info-circle-fill icon-title"></i>
                图像分析说明
              </h5>
            </div>
            <div class="card-body">
              <p>上传交通道路<strong>静态图像</strong>进行车辆识别和交通分析。系统将:</p>
              <ul>
                <li><i class="bi bi-car-front icon-list"></i> 检测图像中的各类车辆</li>
                <li><i class="bi bi-calculator icon-list"></i> 计算车辆数量</li>
                <li><i class="bi bi-pie-chart icon-list"></i> 分析车辆类型分布</li>
                <li><i class="bi bi-graph-up icon-list"></i> 对交通状况进行评估</li>
              </ul>
              <p>分析结果将显示车辆位置标注和详细的统计数据。</p>
              <div class="alert-box info">
                <i class="bi bi-lightbulb icon-alert"></i>
                <div>
                  <strong>提示:</strong> 仅支持静态图像分析，不支持视频。图像质量越高，分析结果越准确。推荐使用白天、光线充足的交通图像。
                </div>
              </div>
            </div>
          </div>
          
          <!-- 分析过程说明 -->
          <div class="feature-card">
            <div class="card-header">
              <h5 class="card-title">
                <i class="bi bi-gear-wide-connected icon-title"></i>
                图像分析过程
              </h5>
            </div>
            <div class="card-body">
              <div class="process-container">
                <div class="process-step">
                  <div class="process-icon">
                    <i class="bi bi-cloud-upload"></i>
                    <div class="step-number">1</div>
                  </div>
                  <div class="process-text">
                    <strong>上传图像</strong> - 将交通图像上传到系统
                  </div>
                </div>
                <div class="process-step">
                  <div class="process-icon">
                    <i class="bi bi-image-alt"></i>
                    <div class="step-number">2</div>
                  </div>
                  <div class="process-text">
                    <strong>预处理</strong> - 系统对图像进行预处理优化
                  </div>
                </div>
                <div class="process-step">
                  <div class="process-icon">
                    <i class="bi bi-eye"></i>
                    <div class="step-number">3</div>
                  </div>
                  <div class="process-text">
                    <strong>对象识别</strong> - 使用YOLOv8模型检测车辆
                  </div>
                </div>
                <div class="process-step">
                  <div class="process-icon">
                    <i class="bi bi-calculator"></i>
                    <div class="step-number">4</div>
                  </div>
                  <div class="process-text">
                    <strong>数据计算</strong> - 统计各类车辆数量
                  </div>
                </div>
                <div class="process-step">
                  <div class="process-icon">
                    <i class="bi bi-file-earmark-bar-graph"></i>
                    <div class="step-number">5</div>
                  </div>
                  <div class="process-text">
                    <strong>结果生成</strong> - 生成带标注的分析图像和报告
                  </div>
                </div>
              </div>
              <div class="alert-box warning">
                <i class="bi bi-exclamation-triangle icon-alert"></i>
                <div>
                  <strong>注意:</strong> 分析过程需要5-30秒，请耐心等待。大型图像可能需要更长时间。
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="features-highlight">
        <h3 class="section-title"><i class="bi bi-stars"></i> 系统优势</h3>
        <div class="highlight-grid">
          <div class="highlight-item">
            <div class="highlight-icon">
              <i class="bi bi-speedometer2"></i>
            </div>
            <div class="highlight-content">
              <h4>高效分析</h4>
              <p>针对交通场景优化的算法，快速准确识别车辆</p>
            </div>
          </div>
          <div class="highlight-item">
            <div class="highlight-icon">
              <i class="bi bi-clipboard-data"></i>
            </div>
            <div class="highlight-content">
              <h4>数据可视化</h4>
              <p>直观展示交通流量和车辆分布情况</p>
            </div>
          </div>
          <div class="highlight-item">
            <div class="highlight-icon">
              <i class="bi bi-shield-check"></i>
            </div>
            <div class="highlight-content">
              <h4>稳定可靠</h4>
              <p>基于深度学习技术，抗干扰能力强</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue';
import UploadForm from '@/components/analysis/UploadForm.vue';
import { getModelStatus } from '@/api/traffic';
import { ElMessage } from 'element-plus';

export default {
  name: 'UploadView',
  components: {
    UploadForm
  },
  setup() {
    const modelStatus = ref(false);
    const checkingStatus = ref(false);
    const statusErrorCount = ref(0);
    const error = ref(null);
    
    // 检查模型状态
    const checkModelStatus = async () => {
      if (checkingStatus.value) return; // 防止重复检查
      
      checkingStatus.value = true;
      console.log('开始检查模型状态...');
      
      try {
        const res = await getModelStatus();
        console.log('模型状态响应:', res);
        
        // 处理可能的认证错误返回的默认离线状态
        if (res.status === 'offline') {
          modelStatus.value = false;
          statusErrorCount.value++;
          
          // 如果是认证错误引起的，不需要增加错误计数
          if (res._authError) {
            console.warn('认证错误导致的模型状态检查失败，不增加错误计数');
            statusErrorCount.value = Math.max(0, statusErrorCount.value - 1);
          }
          
          return;
        }
        
        // 检查是否返回了HTML (可能是重定向到登录页面)
        if (res && typeof res === 'string' && res.includes('<!DOCTYPE html>')) {
          console.error('模型状态请求接收到HTML响应，可能是认证问题导致重定向到登录页面');
          modelStatus.value = false;
          statusErrorCount.value++;
          return;
        }
        
        // 处理多种可能的响应格式
        if (res && typeof res === 'object') {
          if (res.data && typeof res.data === 'object') {
            // 标准响应格式: { data: { status: 'running' } }
            modelStatus.value = res.data.status === 'online' || res.data.status === 'running';
          } else if (res.status) {
            // 简化响应格式: { status: 'running' }
            modelStatus.value = res.status === 'online' || res.status === 'running';
          } else {
            modelStatus.value = false;
          }
        } else {
          modelStatus.value = false;
        }
        
        // 重置错误计数
        statusErrorCount.value = 0;
      } catch (error) {
        console.error('检查模型状态失败:', error);
        
        // 检查是否是认证错误
        const isAuthError = error.response && error.response.status === 401;
        if (isAuthError) {
          console.warn('模型状态检查发生认证错误，但不重定向到登录页');
          
          // 设置为离线状态但不增加错误计数
          modelStatus.value = false;
          return;
        }
        
        modelStatus.value = false;
        statusErrorCount.value++;
        
        // 如果连续三次检查失败，显示警告信息 (但不是认证错误)
        if (statusErrorCount.value >= 3) {
          ElMessage.warning({
            message: '模型服务连接不稳定，可能影响分析功能',
            duration: 5000
          });
        }
      } finally {
        checkingStatus.value = false;
      }
    };
    
    onMounted(() => {
      checkModelStatus();
      
      // 每60秒检查一次模型状态
      const statusInterval = setInterval(checkModelStatus, 60000);
      
      // 组件卸载时清除定时器
      return () => {
        clearInterval(statusInterval);
      };
    });

    return {
      modelStatus,
      checkingStatus,
      statusErrorCount,
      error
    };
  }
};
</script>

<style scoped>
.upload-container {
  min-height: 100vh;
  background-color: #111827;
  color: #e5e7eb;
  padding: 1.5rem;
  width: 100%;
  margin: 0;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.page-title {
  font-size: 2.5rem;
  font-weight: 800;
  margin-bottom: 1.5rem;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: -0.5px;
  text-align: left;
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
  position: relative;
  padding-bottom: 0.5rem;
}

.page-title:after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 80px;
  height: 3px;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  border-radius: 2px;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.5rem;
  margin-bottom: 1.5rem;
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
  width: 100%;
}

.form-section, .info-section {
  display: flex;
  flex-direction: column;
}

.feature-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 0.75rem;
  overflow: hidden;
  backdrop-filter: blur(5px);
  transition: all 0.3s ease;
  margin-bottom: 1rem;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.1);
  height: auto;
}

.feature-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.2);
  border-color: rgba(59, 130, 246, 0.3);
}

.card-header {
  background-color: rgba(26, 32, 50, 0.8);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  padding: 0.8rem 1.2rem;
}

.card-title {
  margin: 0;
  font-weight: 600;
  color: #ffffff;
  font-size: 1.2rem;
  display: flex;
  align-items: center;
}

.icon-title {
  color: #3b82f6;
  margin-right: 8px;
  font-size: 1rem;
}

.card-body {
  padding: 1rem;
}

.icon-list {
  color: #3b82f6;
  margin-right: 8px;
}

.alert-box {
  padding: 0.8rem;
  border-radius: 0.5rem;
  margin-top: 0.8rem;
  border-left: 4px solid;
  display: flex;
  align-items: flex-start;
}

.icon-alert {
  margin-right: 10px;
  font-size: 1rem;
  margin-top: 2px;
}

.alert-box.info {
  background-color: rgba(59, 130, 246, 0.1);
  border-color: #3b82f6;
}

.alert-box.info .icon-alert {
  color: #3b82f6;
}

.alert-box.warning {
  background-color: rgba(245, 158, 11, 0.1);
  border-color: #f59e0b;
}

.alert-box.warning .icon-alert {
  color: #f59e0b;
}

.alert {
  padding: 1rem;
  border-radius: 0.5rem;
  margin-bottom: 1.5rem;
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
}

.alert-danger {
  background-color: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

ul, ol {
  margin-left: 0;
  color: #d1d5db;
  line-height: 1.5;
  margin-bottom: 0.8rem;
  margin-top: 0.8rem;
  list-style-type: none;
  padding-left: 0;
}

li {
  margin-bottom: 0.5rem;
  display: flex;
  align-items: center;
}

strong {
  color: #ffffff;
}

p {
  margin-bottom: 0.8rem;
  margin-top: 0.8rem;
  color: #d1d5db;
  line-height: 1.5;
}

.process-container {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.process-step {
  display: flex;
  align-items: center;
  gap: 1rem;
  background-color: rgba(255, 255, 255, 0.02);
  padding: 0.7rem;
  border-radius: 0.5rem;
}

.process-icon {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
  height: 36px;
  width: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1rem;
  position: relative;
  flex-shrink: 0;
}

.step-number {
  position: absolute;
  top: -5px;
  right: -5px;
  background: #3b82f6;
  color: white;
  height: 16px;
  width: 16px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.6rem;
  font-weight: bold;
}

.process-text {
  flex-grow: 1;
  font-size: 0.95rem;
}

.features-highlight {
  max-width: 1400px;
  margin-left: auto;
  margin-right: auto;
  margin-top: 1rem;
  margin-bottom: 0;
  width: 100%;
}

.section-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 1.2rem;
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-title i {
  color: #3b82f6;
}

.highlight-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.5rem;
}

.highlight-item {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 0.75rem;
  padding: 1.2rem;
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  transition: all 0.2s ease;
}

.highlight-item:hover {
  transform: translateY(-3px);
  background: rgba(255, 255, 255, 0.05);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
}

.highlight-icon {
  background: linear-gradient(135deg, #3b82f6, #8b5cf6);
  height: 40px;
  width: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  color: white;
  flex-shrink: 0;
}

.highlight-content h4 {
  color: #ffffff;
  font-size: 1rem;
  margin-top: 0;
  margin-bottom: 0.5rem;
}

.highlight-content p {
  color: #9ca3af;
  font-size: 0.9rem;
  margin: 0;
  line-height: 1.4;
}

/* 响应式设计 */
@media (max-width: 992px) {
  .content-grid {
    grid-template-columns: 1fr;
    gap: 1.5rem;
  }
  
  .page-title {
    font-size: 2.2rem;
  }

  .highlight-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .highlight-grid {
    grid-template-columns: 1fr;
  }
  
  .page-title {
    font-size: 1.8rem;
  }
  
  .upload-container {
    padding: 1rem;
  }
}

@media (max-width: 576px) {
  .card-body {
    padding: 0.8rem;
  }

  .process-step {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
  }
  
  .highlight-item {
    padding: 1rem;
  }
}
</style> 