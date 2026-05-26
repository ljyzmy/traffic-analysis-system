<template>
  <div class="video-report-panel">
    <div class="optimization-section">
      <h3>交通拥堵解决方案</h3>
      
      <!-- 拥堵状态卡片 -->
      <el-card class="congestion-card">
        <template #header>
          <div class="card-header">
            <span>当前拥堵状态评估</span>
          </div>
        </template>
        <div class="congestion-content">
          <div class="congestion-level-container">
            <div class="congestion-meter">
              <div class="meter-indicator" :style="{ left: getCongestionPercentage + '%' }"></div>
              <div class="meter-scale">
                <span>不拥挤</span>
                <span>一般</span>
                <span>较拥挤</span>
                <span>拥挤</span>
              </div>
            </div>
            <div class="congestion-label">
              {{ getCongestionLevel }}
            </div>
          </div>
          <div class="congestion-details">
            <p>共检测到 <strong>{{ vehicleCount }}</strong> 辆车</p>
            <p>建议采用: <strong>{{ getSolutionTitle }}</strong></p>
          </div>
        </div>
      </el-card>
      
      <!-- 交通方案建议 -->
      <el-collapse v-model="activeNames" class="mt-4">
        <el-collapse-item title="交通信号灯优化方案" name="1">
          <div class="solution-content">
            <p>基于当前交通流量分析，推荐以下信号灯配置：</p>
            <div class="traffic-light-config">
              <el-row :gutter="20">
                <el-col :span="12">
                  <div class="light-direction">
                    <div class="direction-label">东西方向</div>
                    <div class="light-timings">
                      <div class="light-item">
                        <div class="light green"></div>
                        <div class="light-time">{{ getEastWestGreenTime }}秒</div>
                      </div>
                      <div class="light-item">
                        <div class="light yellow"></div>
                        <div class="light-time">3秒</div>
                      </div>
                      <div class="light-item">
                        <div class="light red"></div>
                        <div class="light-time">{{ getEastWestRedTime }}秒</div>
                      </div>
                    </div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="light-direction">
                    <div class="direction-label">南北方向</div>
                    <div class="light-timings">
                      <div class="light-item">
                        <div class="light green"></div>
                        <div class="light-time">{{ getNorthSouthGreenTime }}秒</div>
                      </div>
                      <div class="light-item">
                        <div class="light yellow"></div>
                        <div class="light-time">3秒</div>
                      </div>
                      <div class="light-item">
                        <div class="light red"></div>
                        <div class="light-time">{{ getNorthSouthRedTime }}秒</div>
                      </div>
                    </div>
                  </div>
                </el-col>
              </el-row>
            </div>
          </div>
        </el-collapse-item>
        
        <el-collapse-item title="具体优化建议" name="2">
          <div class="suggestion-content">
            <p>{{ getSolutionDescription }}</p>
            <div class="suggestion-details">
              <h4>详细建议：</h4>
              <ul class="suggestion-list">
                <li v-for="(suggestion, index) in getDetailedSuggestions" :key="index">
                  {{ suggestion }}
              </li>
            </ul>
            </div>
          </div>
        </el-collapse-item>
        
        <el-collapse-item title="长期改进措施" name="3">
          <div class="suggestion-content">
            <p>除了短期交通信号调整外，还建议考虑以下长期改进措施：</p>
            <ul class="suggestion-list">
              <li>优化道路设计，增加车道数量或调整车道分配</li>
              <li>安装智能交通系统，实时监控和调整交通信号</li>
              <li>实施交通需求管理策略，如错峰出行和公共交通优先</li>
              <li>在交通高峰期增加交通引导人员</li>
              <li>建设更完善的公共交通网络，减少私家车使用</li>
            </ul>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
    
    <div class="report-section">
      <el-row :gutter="20">
        <el-col :span="24">
          <div class="report-header">
            <h3>完整分析报告</h3>
            <div class="report-actions">
              <el-tooltip content="导出PDF报告" placement="top">
                <el-button type="primary" @click="exportPDF" :loading="exporting">
                  <el-icon><document /></el-icon> 导出PDF
                </el-button>
              </el-tooltip>
            </div>
          </div>
        </el-col>
      </el-row>
      
      <div class="report-container" v-loading="iframeLoading">
        <iframe 
          v-if="reportUrl" 
          :src="getFixedReportUrl(reportUrl)" 
          @load="iframeLoaded"
          @error="handleIframeError"
          ref="reportIframe"
          frameborder="0" 
          sandbox="allow-same-origin allow-scripts allow-forms"
          style="width:100%; height:500px;"
        ></iframe>
        <div v-else class="no-report">
          <el-icon><warning /></el-icon>
          <p>报告生成中或无法加载</p>
          <el-button type="primary" size="small" @click="generateReport" :loading="generating">
            生成报告
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import html2pdf from 'html2pdf.js';
import { Document, Warning } from '@element-plus/icons-vue';
import { exportPdfReport } from '@/api/video';

export default {
  name: 'VideoReportPanel',
  components: {
    Document,
    Warning
  },
  props: {
    // 分析结果数据
    result: {
      type: Object,
      default: () => ({})
    },
    // 报告的URL
    reportUrl: {
      type: String,
      default: ''
    },
    // 视频路径（用于报告名称）
    videoPath: {
      type: String,
      default: ''
    }
  },
  setup(props, { emit }) {
    const activeNames = ref(['1', '2']); // 默认展开前两个建议
    const iframeLoading = ref(true);
    const exporting = ref(false);
    const generating = ref(false);
    const actualReportUrl = ref('');
    
    // 获取车辆数量
    const vehicleCount = computed(() => props.result?.vehicle_count || 0);
    
    // 计算拥堵百分比位置
    const getCongestionPercentage = computed(() => {
      const count = vehicleCount.value;
      if (count <= 5) return 12.5; // 不拥挤，位于第一档中间
      if (count <= 10) return 37.5; // 一般，位于第二档中间
      if (count <= 20) return 62.5; // 较拥挤，位于第三档中间
      return 87.5; // 拥挤，位于第四档中间
    });
    
    // 获取拥堵等级
    const getCongestionLevel = computed(() => {
      const count = vehicleCount.value;
      if (count <= 5) return '不拥挤';
      if (count <= 10) return '一般';
      if (count <= 20) return '较拥挤';
      return '拥挤';
    });
    
    // 获取解决方案标题
    const getSolutionTitle = computed(() => {
      const count = vehicleCount.value;
      if (count <= 5) return '方案一：正常红绿灯交换';
      if (count <= 10) return '方案二：延长横向绿灯时间';
      if (count <= 20) return '方案三：延长纵向绿灯时间';
      return '方案四：发出提醒（需人为干预）';
    });
    
    // 获取解决方案描述
    const getSolutionDescription = computed(() => {
      const count = vehicleCount.value;
      if (count <= 5) return '当前车流量正常，可维持默认信号灯配置。各方向交通流量均衡，无需特殊干预。';
      if (count <= 10) return '当前车流量略大，建议延长东西方向绿灯时间，确保车辆顺畅通行。';
      if (count <= 20) return '当前车流量较大，建议延长南北方向绿灯时间，减少车辆积压。';
      return '当前车流量大，可能出现拥堵，建议启动交通应急预案，必要时派遣交警现场指挥。';
    });
    
    // 获取详细建议
    const getDetailedSuggestions = computed(() => {
      const count = vehicleCount.value;
      const direction = props.result?.direction || 'horizontal';
      
      if (count <= 5) {
        return [
          '维持当前信号灯配置，绿灯时间保持在30秒',
          '保持交通监控系统正常运行',
          '定期检查道路标志和标线的清晰度'
        ];
      } else if (count <= 10) {
        const suggestions = [
          '将横向方向绿灯时间延长至45秒',
          '纵向方向绿灯时间可适当缩短至25秒',
          '加强对驾驶员的提醒，避免不必要的车道变换'
        ];
        
        if (direction === 'horizontal') {
          suggestions.push('重点关注东西方向的车流量变化');
        }
        
        return suggestions;
      } else if (count <= 20) {
        const suggestions = [
          '将纵向方向绿灯时间延长至45秒',
          '横向方向绿灯时间可适当缩短至25秒',
          '开启辅助车道，增加通行能力',
          '考虑实施临时交通管制措施'
        ];
        
        if (direction === 'vertical') {
          suggestions.push('重点关注南北方向的车流量变化');
        }
        
        return suggestions;
      } else {
        return [
          '建议派遣交警到现场指挥交通',
          '启动交通应急预案，必要时实施单向通行',
          '向驾驶员发布交通提醒，建议选择替代路线',
          '临时关闭部分入口，减少车辆进入',
          '协调相邻路口信号灯，形成绿波带'
        ];
      }
    });
    
    // 计算东西向绿灯时间
    const getEastWestGreenTime = computed(() => {
      const count = vehicleCount.value;
      const direction = props.result?.direction || 'horizontal';
      
      if (count <= 5) return 30;
      if (count <= 10) return direction === 'horizontal' ? 45 : 25;
      if (count <= 20) return direction === 'horizontal' ? 25 : 20;
      return 20; // 拥挤状态下，适当减少东西向绿灯时间
    });
    
    // 计算南北向绿灯时间
    const getNorthSouthGreenTime = computed(() => {
      const count = vehicleCount.value;
      const direction = props.result?.direction || 'horizontal';
      
      if (count <= 5) return 30;
      if (count <= 10) return direction === 'vertical' ? 40 : 25;
      if (count <= 20) return direction === 'vertical' ? 45 : 30;
      return 25; // 拥挤状态下，适当减少南北向绿灯时间
    });
    
    // 计算东西向红灯时间
    const getEastWestRedTime = computed(() => {
      return getNorthSouthGreenTime.value + 3; // 绿灯时间 + 黄灯时间
    });
    
    // 计算南北向红灯时间
    const getNorthSouthRedTime = computed(() => {
      return getEastWestGreenTime.value + 3; // 绿灯时间 + 黄灯时间
    });
    
    // 尝试修复报告URL
    const getFixedReportUrl = (url) => {
      if (!url) return '';
      
      console.log('修复报告URL:', url);
      
      // 如果已经是完整URL，直接返回
      if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('/api/')) {
        return url;
      }
      
      // 如果是TaskID格式（可能是24位十六进制字符串），构建规范的API URL
      if (/^[0-9a-f]{24}$/i.test(url)) {
        return `/api/video-analysis/${url}/report`;
      }
      
      // 如果是相对路径，添加API前缀
      if (url.startsWith('/')) {
        return `/api${url}`;
      }
      
      // 其他情况，使用标准报告URL格式
      // 如果有taskId属性，优先使用
      if (props.result && props.result.taskId) {
        return `/api/video-analysis/${props.result.taskId}/report`;
      }
      
      // 最后尝试构建一个合理的URL
      return `/api/static/reports/${url}`;
    };
    
    // iframe加载完成
    const iframeLoaded = () => {
      console.log('iframe加载完成，URL:', props.reportUrl);
      try {
        const iframe = document.querySelector('.report-container iframe');
        if (iframe) {
          // 检查iframe的内容是否加载成功
          const iframeDoc = iframe.contentDocument || iframe.contentWindow?.document;
          if (iframeDoc) {
            console.log('iframe文档加载成功，标题:', iframeDoc.title);
            if (iframeDoc.body.innerHTML === '') {
              console.warn('iframe内容为空，可能加载失败');
              // 内容为空时也触发错误处理
              handleIframeError(new Error('iframe内容为空'));
              return;
            }
          } else {
            console.warn('无法访问iframe内容，可能是跨域问题');
          }
        }
      } catch (err) {
        console.error('iframe检查失败:', err);
        handleIframeError(err);
      }
      iframeLoading.value = false;
    };
    
    // 处理iframe加载错误
    const handleIframeError = (error) => {
      console.error('iframe加载失败:', error);
      iframeLoading.value = false;
      ElMessage.warning({
        message: '报告加载失败，是否在新窗口打开？',
        duration: 5000,
        showClose: true,
        type: 'warning',
        onClose: () => {}
      });
      
      // 提供在新窗口打开的选项
      ElMessageBox.confirm(
        '报告在框架中加载失败，是否在新窗口打开查看？',
        '报告加载失败',
        {
          confirmButtonText: '在新窗口打开',
          cancelButtonText: '取消',
          type: 'warning'
        }
      ).then(() => {
        const url = getFixedReportUrl(props.reportUrl);
        window.open(url, '_blank');
      }).catch(() => {
        // 用户取消，不做任何操作
      });
    };
    
    // 从视频路径中提取文件名作为报告名称
    const getReportName = () => {
      if (!props.videoPath) return '交通分析报告';
      
      const pathParts = props.videoPath.split('/');
      const fileName = pathParts[pathParts.length - 1];
      const nameWithoutExt = fileName.split('.')[0];
      
      return `${nameWithoutExt}_交通分析报告`;
    };
    
    // 导出PDF报告
    const exportPDF = async () => {
      if (exporting.value) {
        return;
      }
      
      exporting.value = true;
      
      try {
        // 检查是否有iframe
        const iframe = document.querySelector('.report-container iframe');
        if (!iframe && props.result?.taskId) {
          // 尝试通过API导出
          console.log('尝试通过API导出PDF报告');
          
          try {
            const response = await exportPdfReport(props.result.taskId);
            
            // 处理Blob响应
            const blob = new Blob([response.data], { type: 'application/pdf' });
            const url = URL.createObjectURL(blob);
            
            // 创建下载链接
            const link = document.createElement('a');
            link.href = url;
            link.download = `${getReportName()}.pdf`;
            document.body.appendChild(link);
            link.click();
            
            // 清理资源
            setTimeout(() => {
              URL.revokeObjectURL(url);
              document.body.removeChild(link);
            }, 100);
            
            ElMessage.success('PDF报告已导出');
          } catch (error) {
            console.error('API导出PDF失败:', error);
            
            // 处理HTML响应或认证错误
            if (error.htmlResponse) {
              ElMessage.error('导出失败：服务器返回了HTML而不是PDF (可能是认证问题)');
              
              // 询问用户是否重新登录
              ElMessageBox.confirm(
                '您的登录会话可能已过期。是否重新登录？',
                '认证错误',
                {
                  confirmButtonText: '重新登录',
                  cancelButtonText: '取消',
                  type: 'warning'
                }
              ).then(() => {
                // 清除认证信息并跳转到登录页面
                localStorage.removeItem('auth_token');
                localStorage.removeItem('user');
                window.location.href = '/login';
              }).catch(() => {});
              return;
            }
            
            throw new Error(error.message || '通过API导出PDF失败');
          }
          return;
        }
        
        if (!iframe) {
          throw new Error('找不到报告iframe');
        }
        
        // 尝试访问iframe内容
        let iframeContent;
        try {
          iframeContent = iframe.contentDocument || (iframe.contentWindow && iframe.contentWindow.document);
          
          // 检查iframe是否加载了HTML内容
          if (iframeContent && iframeContent.body.innerHTML === '') {
            throw new Error('iframe内容为空，可能是因为报告未生成或加载失败');
          }
          
          // 检查iframe内容是否为错误页面
          if (iframeContent && iframeContent.title && 
              (iframeContent.title.includes('Error') || 
               iframeContent.title.includes('错误') ||
               iframeContent.title.includes('Not Found'))) {
            throw new Error(`报告加载失败: ${iframeContent.title}`);
          }
        } catch (error) {
          console.error('无法访问iframe内容（可能是跨域问题）:', error);
          throw new Error('无法访问报告内容，可能是由于跨域限制或报告未生成');
        }
        
        if (!iframeContent) {
          throw new Error('无法访问iframe内容');
        }
        
        // 创建一个新的容器，复制iframe内容
        const element = iframeContent.body.cloneNode(true);
        
        // 添加样式以确保正确打印
        const styles = document.createElement('style');
        styles.innerHTML = `
          body {
            font-family: Arial, sans-serif;
            color: #333;
            background-color: white;
          }
          h1, h2, h3 {
            color: #1a1a1a;
          }
          .chart-container {
            page-break-inside: avoid;
            margin-bottom: 20px;
          }
        `;
        element.appendChild(styles);
        
        // 使用html2pdf库进行转换
        const opt = {
          margin: [10, 10, 10, 10],
          filename: `${getReportName()}.pdf`,
          image: { type: 'jpeg', quality: 0.98 },
          html2canvas: { scale: 2, useCORS: true, logging: false },
          jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' }
        };
        
        await html2pdf().set(opt).from(element).save();
        ElMessage.success('PDF报告已导出');
      } catch (err) {
        console.error('导出PDF失败:', err);
        ElMessage.error('导出PDF失败: ' + err.message);
        
        // 如果客户端导出失败，尝试使用服务器端导出
        if (props.result?.taskId) {
          ElMessage.info('正在尝试从服务器导出...');
          try {
            const response = await exportPdfReport(props.result.taskId);
            
            // 处理Blob响应
            const blob = new Blob([response.data], { type: 'application/pdf' });
            const url = URL.createObjectURL(blob);
            
            // 创建下载链接
            const link = document.createElement('a');
            link.href = url;
            link.download = `${getReportName()}.pdf`;
            document.body.appendChild(link);
            link.click();
            
            // 清理资源
            setTimeout(() => {
              URL.revokeObjectURL(url);
              document.body.removeChild(link);
            }, 100);
            
            ElMessage.success('PDF报告已从服务器导出');
          } catch (apiErr) {
            console.error('服务器导出PDF失败:', apiErr);
            
            // 检查是否是认证错误
            if (apiErr.htmlResponse) {
              ElMessage.error('认证失败，请重新登录后再试');
              // 询问是否重新登录
              ElMessageBox.confirm(
                '您的登录会话可能已过期，是否重新登录？',
                '认证错误',
                {
                  confirmButtonText: '重新登录',
                  cancelButtonText: '取消',
                  type: 'warning'
                }
              ).then(() => {
                // 清除认证信息并跳转到登录页面
                localStorage.removeItem('auth_token');
                localStorage.removeItem('user');
                window.location.href = '/login';
              }).catch(() => {});
            } else {
              ElMessage.error('服务器导出PDF也失败: ' + (apiErr.message || '未知错误'));
            }
          }
        }
      } finally {
        exporting.value = false;
      }
    };
    
    // 生成报告
    const generateReport = async () => {
      generating.value = true;
      try {
        // 检查是否有任务ID
        if (!props.result?.taskId) {
          throw new Error('缺少任务ID，无法生成报告');
        }
        
        // 这里应该调用实际的报告生成API
        // 为了演示，我们模拟一个API调用
        await new Promise(resolve => setTimeout(resolve, 2000));
        
        console.log('向服务器发送报告生成请求，任务ID:', props.result.taskId);
        
        // 假设报告生成成功
        ElMessage.success('报告生成请求已发送，请稍后查看');
        
        // 这里应该刷新页面或重新加载报告URL
        // 如果后端有实时通知功能更好
      } catch (err) {
        console.error('生成报告失败:', err);
        ElMessage.error('生成报告失败: ' + err.message);
      } finally {
        generating.value = false;
      }
    };
    
    // 监听属性变化
    onMounted(() => {
      console.log('VideoReportPanel组件已挂载，reportUrl:', props.reportUrl);
      if (props.reportUrl) {
        actualReportUrl.value = getFixedReportUrl(props.reportUrl);
        console.log('处理后的报告URL:', actualReportUrl.value);
      } else {
        console.log('无reportUrl提供');
      }
    });
    
    return {
      activeNames,
      vehicleCount,
      getCongestionPercentage,
      getCongestionLevel,
      getSolutionTitle,
      getSolutionDescription,
      getDetailedSuggestions,
      getEastWestGreenTime,
      getNorthSouthGreenTime,
      getEastWestRedTime,
      getNorthSouthRedTime,
      iframeLoading,
      exporting,
      generating,
      iframeLoaded,
      handleIframeError,
      exportPDF,
      generateReport,
      actualReportUrl,
      getFixedReportUrl
    };
  }
};
</script>

<style scoped>
.video-report-panel {
  width: 100%;
}

.optimization-section,
.report-section {
  margin: 20px 0;
}

h3 {
  color: #ffffff !important;
  font-weight: 600 !important;
  margin-bottom: 15px;
}

h4 {
  color: #e5e7eb !important;
  font-weight: 600 !important;
  margin: 15px 0 10px 0;
}

.congestion-card {
  margin-bottom: 20px;
  background: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
}

:deep(.congestion-card .el-card__header) {
  background-color: rgba(26, 32, 50, 0.8);
}

.card-header {
  font-weight: bold;
  color: #ffffff;
}

.congestion-content {
  padding: 20px 0;
}

.congestion-level-container {
  text-align: center;
  margin-bottom: 20px;
}

.congestion-meter {
  height: 20px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  position: relative;
  margin: 0 auto 15px;
  width: 80%;
}

.meter-indicator {
  width: 20px;
  height: 20px;
  background: #6366f1;
  border-radius: 50%;
  position: absolute;
  top: 0;
  transform: translateX(-50%);
  box-shadow: 0 0 10px rgba(99, 102, 241, 0.7);
}

.meter-scale {
  display: flex;
  justify-content: space-between;
  margin-top: 5px;
  color: #d1d5db;
  font-size: 0.85rem;
}

.congestion-label {
  font-size: 24px;
  font-weight: 700;
  color: #6366f1;
  margin: 15px 0;
}

.congestion-details {
  text-align: center;
  color: #e5e7eb;
}

.congestion-details p {
  margin: 5px 0;
}

.congestion-details strong {
  color: #ffffff;
  font-weight: 600;
}

.mt-4 {
  margin-top: 1rem;
}

.suggestion-content {
  color: #e5e7eb;
  padding: 10px;
}

.suggestion-list {
  margin-top: 10px;
  padding-left: 20px;
}

.suggestion-list li {
  margin-bottom: 8px;
  position: relative;
}

.suggestion-list li::before {
  content: '•';
  color: #6366f1;
  font-weight: bold;
  position: absolute;
  left: -15px;
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.report-container {
  background: rgba(17, 24, 39, 0.5);
  border-radius: 8px;
  padding: 15px;
  height: 530px;
}

.no-report {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
  color: #909399;
}

.no-report .el-icon {
  font-size: 48px;
  margin-bottom: 20px;
}

:deep(.el-collapse) {
  --el-collapse-header-bg-color: rgba(17, 24, 39, 0.5);
  --el-collapse-header-text-color: #e5e7eb;
  --el-collapse-content-bg-color: rgba(31, 41, 55, 0.5);
  --el-collapse-content-text-color: #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  border: none;
}

:deep(.el-collapse-item__header) {
  font-weight: 600;
}

:deep(.el-collapse-item__wrap) {
  border-bottom: none;
}

/* 信号灯配置样式 */
.traffic-light-config {
  margin-top: 15px;
}

.light-direction {
  background: rgba(17, 24, 39, 0.7);
  border-radius: 8px;
  padding: 15px;
  margin-bottom: 10px;
}

.direction-label {
  text-align: center;
  font-weight: 600;
  color: #ffffff;
  margin-bottom: 15px;
  font-size: 16px;
}

.light-timings {
  display: flex;
  justify-content: space-around;
}

.light-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.light {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-bottom: 8px;
}

.light.red {
  background-color: #ef4444;
  box-shadow: 0 0 10px rgba(239, 68, 68, 0.5);
}

.light.yellow {
  background-color: #f59e0b;
  box-shadow: 0 0 10px rgba(245, 158, 11, 0.5);
}

.light.green {
  background-color: #10b981;
  box-shadow: 0 0 10px rgba(16, 185, 129, 0.5);
}

.light-time {
  font-size: 14px;
  color: #d1d5db;
}
</style> 