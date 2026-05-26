<template>
    <div class="container my-5">
    <h1 class="mb-4">交通图像分析结果</h1>
    
    <!-- 分析结果图片显示 -->
    <div class="card mb-4">
      <div class="card-header bg-primary text-white">
        <h5 class="mb-0">分析结果图像</h5>
      </div>
      <div class="card-body text-center">
        <div class="image-container">
          <div id="loadingIndicator" class="loading-indicator" v-if="isLoading">
        <div class="spinner-border text-primary" role="status">
          <span class="visually-hidden">加载中...</span>
        </div>
          </div>
          
          <!-- 如果有分析图像就显示 -->
          <div v-if="analysisData && getAnalysisImage()" class="result-image-container">
            <img 
              :src="getAnalysisImage()" 
              alt="分析结果图像" 
              class="result-image img-fluid"
              @load="isImageLoading = false"
              @error="onImageError"
            />
      </div>
      
          <!-- 图像加载失败或无分析数据时的显示 -->
          <div v-if="imageError || (!getAnalysisImage() && !isLoading)" class="alert alert-warning">
            <i class="bi bi-exclamation-triangle me-2"></i>无法加载分析图像
          </div>
      </div>
      
        <div id="imageError" class="alert alert-warning mt-3" v-if="imageError" style="display: block;">
          <i class="bi bi-exclamation-triangle me-2"></i>图像加载失败，可能的原因:
          <ul class="mb-0 text-start">
            <li>图像处理尚未完成</li>
            <li>图像URL路径不正确</li>
            <li>分析结果图像未正确保存</li>
          </ul>
        </div>
      </div>
      </div>
      
    <!-- 分析结果信息 -->
            <div class="card mb-4">
              <div class="card-header bg-primary text-white">
        <h5 class="mb-0">分析结果信息</h5>
      </div>
      <div class="card-body">
        <!-- 错误提示 -->
        <div v-if="hasError" class="alert alert-danger mb-3">
          <i class="bi bi-exclamation-triangle-fill me-2"></i>
          <strong>分析错误: </strong> {{ errorMessage }}
        </div>
        
        <div v-if="!analysisData && !isLoading && !hasError" class="alert alert-warning">
          <i class="bi bi-exclamation-triangle me-2"></i>无法加载分析结果数据
        </div>
        
        <div v-if="analysisData || simulatedData">
          <div class="row mb-3">
            <div class="col-md-3 fw-bold">分析ID：</div>
            <div class="col-md-9">{{ resultId || 'DEMO123456' }}</div>
          </div>
          
          <div class="row mb-3">
            <div class="col-md-3 fw-bold">识别数：</div>
            <div class="col-md-9">
              <span>{{ getVehicleCount() }}</span>
              <small class="text-danger" v-if="getVehicleCount() === 0 && !hasVehicleTypeStats()">（未检测到车辆，可能分析有误）</small>
            </div>
          </div>
          
          <div class="row mb-3">
            <div class="col-md-3 fw-bold">分析时间：</div>
            <div class="col-md-9">
              {{ analysisData && (analysisData.analysisStartTime || analysisData.timestamp) ? 
                   formatAnalysisTime(analysisData.analysisStartTime || analysisData.timestamp) : '未知' }}
            </div>
          </div>
          
          <div class="row mb-3">
            <div class="col-md-3 fw-bold">分析耗时：</div>
            <div class="col-md-9">
              <span v-if="analysisData && analysisData.inferenceTime !== undefined">
                {{ formatInferenceTime(analysisData.inferenceTime) }}
              </span>
              <span v-else>未知</span>
            </div>
              </div>
          
          <div class="row mb-3">
            <div class="col-md-3 fw-bold">分析人员：</div>
            <div class="col-md-9">
              {{ getAnalyst() }}
                </div>
              </div>
          
          <div class="row mb-3">
            <div class="col-md-3 fw-bold">处理状态：</div>
            <div class="col-md-9">
              <span class="badge bg-success" v-if="!isLoading && !hasError">成功</span>
              <span class="badge bg-warning" v-if="isLoading">处理中</span>
              <span class="badge bg-danger" v-if="hasError">失败</span>
            </div>
          </div>
          
          <div class="row mb-3">
            <div class="col-md-3 fw-bold">交通状况评估：</div>
            <div class="col-md-9">
              <span class="badge" :class="getTrafficStatusClass()">{{ getTrafficStatus() }}</span>
              <small class="text-muted ms-2">{{ getTrafficDescription() }}</small>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 图表数据展示 -->
            <div class="card mb-4">
              <div class="card-header bg-primary text-white">
        <h5 class="mb-0">分析统计</h5>
      </div>
      <div class="card-body">
        <ul class="nav nav-tabs" id="myTab" role="tablist">
          <li class="nav-item" role="presentation">
            <button class="nav-link active" id="vehicles-tab" data-bs-toggle="tab" data-bs-target="#vehicles" type="button" role="tab" aria-controls="vehicles" aria-selected="true" @click="activeTab = 'vehicles'">类型分布</button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="flow-tab" data-bs-toggle="tab" data-bs-target="#flow" type="button" role="tab" aria-controls="flow" aria-selected="false" @click="activeTab = 'flow'">交通流量</button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link" id="density-tab" data-bs-toggle="tab" data-bs-target="#density" type="button" role="tab" aria-controls="density" aria-selected="false" @click="activeTab = 'density'">交通密度</button>
          </li>
        </ul>
        <div class="tab-content pt-3" id="myTabContent">
          <div class="tab-pane fade show active" id="vehicles" role="tabpanel" aria-labelledby="vehicles-tab">
            <div class="row">
              <div class="col-md-6">
                <div class="chart-container">
                  <vehicle-chart 
                    v-if="(analysisData || simulatedData) && (activeTab === 'vehicles' || activeTab === null)" 
                    :data="getVehicleData()" 
                    :loading="isLoading"
                    :key="'vehicle-chart-' + chartRefreshKey"
                    chart-type="bar"
                  />
                  <div class="empty-data" v-else>暂无车辆数据</div>
                </div>
              </div>
              <div class="col-md-6">
                <div class="chart-container">
                  <vehicle-chart 
                    v-if="(analysisData || simulatedData) && (activeTab === 'vehicles' || activeTab === null)" 
                    :data="getVehicleData()" 
                    :loading="isLoading"
                    :key="'vehicle-pie-chart-' + chartRefreshKey"
                    chart-type="pie"
                  />
                  <div class="empty-data" v-else>暂无车辆数据</div>
                </div>
              </div>
            </div>
            
            <!-- 添加车辆类型统计表格 -->
            <div class="mt-4" v-if="hasVehicleTypeStats()">
              <h6 class="fw-bold mb-3">类型详情</h6>
              <div class="table-responsive">
                <table class="table table-bordered table-hover">
                  <thead class="table-light">
                    <tr>
                      <th>类型</th>
                      <th>数量</th>
                      <th>占比</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(count, type) in getVehicleTypeStats()" :key="type">
                      <td>{{ formatVehicleType(type) }}</td>
                      <td>{{ count }}</td>
                      <td>{{ calculatePercentage(count, getVehicleCount()) }}%</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>
          <div class="tab-pane fade" id="flow" role="tabpanel" aria-labelledby="flow-tab">
            <div class="chart-container">
              <flow-chart 
                v-if="(analysisData || simulatedData) && activeTab === 'flow'" 
                :data="getFlowData()" 
                :loading="isLoading"
                :key="'flow-chart-' + chartRefreshKey"
              />
              <div class="empty-data" v-else>暂无流量数据</div>
            </div>
          </div>
          <div class="tab-pane fade" id="density" role="tabpanel" aria-labelledby="density-tab">
            <div class="chart-container">
              <density-chart 
                v-if="(analysisData || simulatedData) && activeTab === 'density'" 
                :data="getDensityData()" 
                :loading="isLoading"
                :key="'density-chart-' + chartRefreshKey"
              />
              <div class="empty-data" v-else>暂无密度数据</div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 操作按钮 -->
    <div class="d-flex gap-2 mb-4">
      <router-link to="/upload" class="btn btn-primary">
        <i class="bi bi-arrow-up-circle me-1"></i>新上传
      </router-link>
      <router-link to="/history" class="btn btn-info text-white">
        <i class="bi bi-clock-history me-1"></i>历史记录
      </router-link>
      <button id="refreshButton" class="btn btn-outline-secondary" @click="refreshData">
        <i class="bi bi-arrow-clockwise me-1"></i>刷新结果
      </button>
    </div>
  </div>
</template>

<script>
import VehicleChart from '@/components/analysis/charts/VehicleChart.vue';
import FlowChart from '@/components/analysis/charts/FlowChart.vue';
import DensityChart from '@/components/analysis/charts/DensityChart.vue';
import { getAnalysisResult } from '@/api/traffic';

export default {
  name: 'ResultView',
  components: {
    VehicleChart,
    FlowChart,
    DensityChart
  },
  data() {
    return {
      resultId: this.$route.params.id,
      isLoading: true,
      analysisData: null,
      imageError: false,
      isImageLoading: true,
      useSimulatedData: false,
      activeTab: 'vehicles',
      chartRefreshKey: 0,
      hasError: false,
      errorMessage: '',
      // 模拟数据（当API数据不可用时使用）
      simulatedData: {
        vehicles: {
          categories: ['轿车', '卡车', '公交车', '摩托车', '自行车', '行人', '交通信号灯'],
          counts: [15, 5, 2, 3, 1, 4, 2]
        },
        flow: {
          times: ['08:00', '09:00', '10:00', '11:00', '12:00', '13:00'],
          flows: [35, 42, 25, 30, 22, 28]
        },
        density: {
          times: ['08:00', '09:00', '10:00', '11:00', '12:00', '13:00'],
          densities: [18, 22, 10, 15, 8, 12]
        }
      }
    };
  },
  mounted() {
    // Add this to ensure the body background is also dark
    document.body.style.backgroundColor = '#111827';
    document.body.style.margin = '0';
    document.body.style.padding = '0';
    document.body.style.minHeight = '100vh';
    
    this.loadAnalysisData();
    
    // 设置初始活动标签页
    this.activeTab = 'vehicles';
    
    // 添加标签页切换事件监听
    const tabEls = document.querySelectorAll('button[data-bs-toggle="tab"]');
    tabEls.forEach(tabEl => {
      tabEl.addEventListener('shown.bs.tab', event => {
        // 获取激活的标签页ID
        const targetId = event.target.getAttribute('data-bs-target').substring(1);
        
        // 更新活动标签页
        if (targetId === 'vehicles') this.activeTab = 'vehicles';
        else if (targetId === 'flow') this.activeTab = 'flow';
        else if (targetId === 'density') this.activeTab = 'density';
        
        // 增加图表刷新计数器，强制重新渲染
        this.chartRefreshKey++;
        
        console.log(`标签页切换到：${this.activeTab}，刷新计数：${this.chartRefreshKey}`);
      });
    });
  },
  methods: {
    // 获取分析图像URL
    getAnalysisImage() {
      if (!this.analysisData) return null;
      
      // 如果有状态错误，不尝试加载图片
      if (this.analysisData.status === 'error') {
        return null;
      }
      
      // 处理图片URL
      let imageUrl = null;
      
      if (this.analysisData.imageUrl) {
        imageUrl = this.analysisData.imageUrl;
      } else if (this.analysisData.resultImage) {
        imageUrl = this.analysisData.resultImage;
      } else if (this.analysisData.resultImageBase64) {
        // 如果有Base64数据，直接返回使用data URI
        return `data:image/jpeg;base64,${this.analysisData.resultImageBase64}`;
      } else if (this.analysisData.vehicles && this.analysisData.vehicles.image) {
        imageUrl = this.analysisData.vehicles.image;
      }
      
      // 如果找到了图片URL
      if (imageUrl) {
        // 检查是否为Base64数据
        if (imageUrl.startsWith('data:image')) {
          return imageUrl;
        }
        
        // 检查是否为GridFS ID（24位十六进制字符串）
        if (/^[0-9a-f]{24}$/i.test(imageUrl)) {
          return `/api/media/image/${imageUrl}`;
        }
        
        // 检查URL是否以http开头，如果不是则添加后端服务器地址
        if (!imageUrl.startsWith('http') && !imageUrl.startsWith('/api/')) {
          // 如果是以/开头的相对路径，则添加后端服务器地址
          if (imageUrl.startsWith('/')) {
            // 在开发环境中使用代理的实际路径
            return `/api${imageUrl}`;
          } else {
            // 如果不是以/开头，添加/api/
            return `/api/${imageUrl}`;
          }
        }
        return imageUrl;
      }
      
      return null;
    },
    
    // 获取车辆数量
    getVehicleCount() {
      if (this.analysisData) {
        // 如果vehicleCount为0但有车辆类型统计，则重新计算
        if (this.analysisData.vehicleCount === 0 && this.analysisData.vehicleTypeStats) {
          const calculatedCount = Object.values(this.analysisData.vehicleTypeStats).reduce((sum, count) => sum + Number(count), 0);
          if (calculatedCount > 0) {
            console.log(`修正车辆计数: 从0到${calculatedCount}`);
            this.analysisData.vehicleCount = calculatedCount;
          }
        }
        
        if (this.analysisData.vehicles && this.analysisData.vehicles.count) {
          return this.analysisData.vehicles.count;
        }
        
        if (this.analysisData.vehicleCount !== undefined) {
          return this.analysisData.vehicleCount;
        }
        
        if (this.analysisData.detections && Array.isArray(this.analysisData.detections)) {
          return this.analysisData.detections.length;
        }
      }
      
      // 使用模拟数据
      if (this.useSimulatedData) {
        return this.simulatedData.vehicles.counts.reduce((sum, count) => sum + count, 0);
      }
      
      return 0;
    },
    
    // 处理图像加载错误
    onImageError() {
      console.error('图像加载失败');
      this.imageError = true;
      this.isImageLoading = false;
    },
    
    // 获取车辆数据（用于图表）
    getVehicleData() {
      if (this.analysisData && (
          (this.analysisData.vehicles && this.analysisData.vehicles.categories) || 
          this.analysisData.vehicleCount || 
          (this.analysisData.detections && this.analysisData.detections.length) ||
          this.analysisData.vehicleTypeStats
      )) {
        // 如果有vehicleTypeStats，优先使用
        if (this.analysisData.vehicleTypeStats) {
          const stats = this.analysisData.vehicleTypeStats;
          const categories = Object.keys(stats);
          const counts = categories.map(category => stats[category]);
          return { categories, counts };
        }
        
        return this.analysisData.vehicles || {count: this.analysisData.vehicleCount};
      }
      
      // 使用模拟数据
      return this.simulatedData.vehicles;
    },
    
    // 获取车辆类型统计数据
    getVehicleTypeStats() {
      if (this.analysisData && this.analysisData.vehicleTypeStats) {
        return this.analysisData.vehicleTypeStats;
      }
      
      // 如果没有vehicleTypeStats但有detections，生成统计数据
      if (this.analysisData && this.analysisData.detections && 
          Array.isArray(this.analysisData.detections)) {
        const stats = {};
        this.analysisData.detections.forEach(detection => {
          const type = detection.className || detection.class_name || '未知';
          stats[type] = (stats[type] || 0) + 1;
        });
        return stats;
      }
      
      return this.simulatedData.vehicles.categories.reduce((obj, cat, index) => {
        obj[cat] = this.simulatedData.vehicles.counts[index];
        return obj;
      }, {});
    },
    
    // 检查是否有车辆类型统计数据
    hasVehicleTypeStats() {
      const stats = this.getVehicleTypeStats();
      return stats && Object.keys(stats).length > 0;
    },
    
    // 格式化车辆类型名称
    formatVehicleType(type) {
      return this.getVehicleTypeName(type);
    },
    
    // 获取车辆类型中文名称
    getVehicleTypeName(type) {
      const typeNames = {
        'car': '小汽车',
        'truck': '卡车',
        'bus': '公交车',
        'motorcycle': '摩托车',
        'bicycle': '自行车',
        'person': '行人',
        'traffic light': '交通信号灯',
        'unknown': '未知'
      };
      return typeNames[type.toLowerCase()] || type;
    },
    
    // 计算百分比
    calculatePercentage(value, total) {
      if (!total) return 0;
      return Math.round((value / total) * 100);
    },
    
    // 获取分析时间
    getAnalysisTime() {
      if (this.analysisData) {
        if (this.analysisData.timestamp) {
          return this.formatAnalysisTime(this.analysisData.timestamp);
        }
        if (this.analysisData.analysisStartTime) {
          return this.formatAnalysisTime(this.analysisData.analysisStartTime);
        }
      }
      return this.formatAnalysisTime(new Date());
    },
    
    // 格式化分析时间显示
    formatAnalysisTime(timestamp) {
      if (!timestamp) return '未知';
      try {
        const date = new Date(timestamp);
        return date.toLocaleString();
      } catch (e) {
        return timestamp;
      }
    },
    
    // 获取推理耗时
    getInferenceTime() {
      if (this.analysisData && this.analysisData.inferenceTime !== undefined) {
        return this.formatInferenceTime(this.analysisData.inferenceTime);
      }
      return '未知';
    },
    
    // 格式化推理时间
    formatInferenceTime(milliseconds) {
      if (!milliseconds && milliseconds !== 0) return '未知';
      
      const ms = Number(milliseconds);
      if (isNaN(ms)) return milliseconds;
      
      if (ms < 1000) {
        return ms.toFixed(0) + ' 毫秒';
      } else {
        return (ms / 1000).toFixed(2) + ' 秒';
      }
    },
    
    // 获取流量数据
    getFlowData() {
      if (this.analysisData && (
          this.analysisData.flow || 
          (this.analysisData.detections && this.analysisData.detections.length)
      )) {
        return this.analysisData.flow || this.analysisData.detections;
      }
      
      // 使用模拟数据
      return this.simulatedData.flow;
    },
    
    // 获取密度数据
    getDensityData() {
      if (this.analysisData && (
          this.analysisData.density || 
          (this.analysisData.detections && this.analysisData.detections.length)
      )) {
        return this.analysisData.density || this.analysisData.detections;
      }
      
      // 使用模拟数据
      return this.simulatedData.density;
    },
    
    // 获取交通状况
    getTrafficStatus() {
      const count = this.getVehicleCount();
      
      if (count <= 5) return '畅通';
      if (count <= 15) return '正常';
      if (count <= 30) return '较拥挤';
      return '拥挤';
    },
    
    // 获取交通状况描述
    getTrafficDescription() {
      const status = this.getTrafficStatus();
      
      switch (status) {
        case '畅通': return '车辆稀少，道路通行顺畅';
        case '正常': return '车流量适中，通行正常';
        case '较拥挤': return '车流量较大，通行速度减慢';
        case '拥挤': return '车流量大，道路拥堵';
        default: return '';
      }
    },
    
    // 获取交通状况样式类
    getTrafficStatusClass() {
      const status = this.getTrafficStatus();
      
      switch (status) {
        case '畅通': return 'bg-success';
        case '正常': return 'bg-info';
        case '较拥挤': return 'bg-warning';
        case '拥挤': return 'bg-danger';
        default: return 'bg-secondary';
      }
    },
    
    // 加载分析数据
    async loadAnalysisData() {
      if (!this.resultId) {
        this.useSimulatedData = true;
        this.isLoading = false;
        return;
      }
      
      this.isLoading = true;
      this.hasError = false;
      this.errorMessage = '';
      
      try {
        const response = await getAnalysisResult(this.resultId);
        console.log('分析结果响应:', response);
        
        // 检查是否返回了HTML
        if (typeof response === 'string' && response.includes('<!DOCTYPE html>')) {
          console.error('响应为HTML，可能是认证问题');
          this.useSimulatedData = true;
          this.hasError = true;
          this.errorMessage = '服务器认证失败';
          return;
        }
        
        // 处理响应数据
        if (response.data) {
          this.analysisData = response.data;
        } else if (typeof response === 'object') {
          this.analysisData = response;
        }
        
        // 验证和修复车辆计数
        if (this.analysisData) {
          // 如果vehicleCount为0但有车辆类型统计，则重新计算
          if (this.analysisData.vehicleCount === 0 && this.analysisData.vehicleTypeStats) {
            const calculatedCount = Object.values(this.analysisData.vehicleTypeStats).reduce((sum, count) => sum + Number(count), 0);
            if (calculatedCount > 0) {
              console.log(`修正车辆计数: 从0到${calculatedCount}`);
              this.analysisData.vehicleCount = calculatedCount;
            }
          }
          
          // 确保inferenceTime字段有值
          if (this.analysisData.inferenceTime === undefined || this.analysisData.inferenceTime <= 0) {
            console.log('修正分析耗时为默认值0.5秒');
            this.analysisData.inferenceTime = 500; // 500毫秒
          }
        }
        
        // 检查是否存在错误状态
        if (this.analysisData && this.analysisData.status === 'error') {
          console.warn('分析结果包含错误:', this.analysisData.message);
          this.errorMessage = this.analysisData.message || '分析失败';
          this.hasError = true;
        }
        
        // 处理车辆类型统计 - 如果API响应中没有但有检测数据，则生成统计
        if (this.analysisData && !this.analysisData.vehicleTypeStats && 
            this.analysisData.detections && Array.isArray(this.analysisData.detections)) {
          const stats = {};
          this.analysisData.detections.forEach(detection => {
            const type = detection.className || detection.class_name || '未知';
            stats[type] = (stats[type] || 0) + 1;
          });
          this.analysisData.vehicleTypeStats = stats;
        }
        
        // 如果没有足够的数据，使用模拟数据
        if (!this.analysisData || 
            (!this.analysisData.vehicles && !this.analysisData.vehicleCount && 
             !this.analysisData.vehicleTypeStats && 
             !(this.analysisData.detections && this.analysisData.detections.length))) {
          this.useSimulatedData = true;
          if (!this.hasError) {
            this.hasError = true;
            this.errorMessage = '分析结果数据不完整，使用模拟数据';
          }
        }
        
      } catch (error) {
        console.error('加载分析数据失败:', error);
        
        // 处理特定的错误情况
        if (error.response) {
          if (error.response.status === 404) {
            console.error('分析结果不存在或API路径错误');
            this.errorMessage = '分析结果不存在或API路径错误';
          } else if (error.response.status === 401) {
            console.error('未授权访问，可能需要登录');
            this.errorMessage = '未授权访问，请先登录';
            // 可以选择重定向到登录页面
            // window.location.href = '/login';
          } else {
            this.errorMessage = `服务器错误: ${error.response.status}`;
          }
        } else if (error.request) {
          this.errorMessage = '无法连接到服务器';
        } else {
          this.errorMessage = error.message || '加载分析数据失败';
        }
        
        this.hasError = true;
        this.useSimulatedData = true;
      } finally {
        this.isLoading = false;
      }
    },
    
    // 刷新数据
    refreshData() {
      this.loadAnalysisData();
      this.chartRefreshKey++;
    },
    
    // 获取分析人员信息
    getAnalyst() {
      if (this.analysisData) {
        // 优先使用analyst字段
        if (this.analysisData.analyst) {
          return this.analysisData.analyst;
        }
        // 其次使用username字段
        if (this.analysisData.username) {
          return this.analysisData.username;
        }
        // 再次检查结果数据中是否包含分析人员信息
        if (this.analysisData.analysisResult && this.analysisData.analysisResult.analyst) {
          return this.analysisData.analysisResult.analyst;
        }
      }
      return "未知";
    }
  },
  beforeUnmount() {
    // Clean up styles when component is destroyed
    document.body.style.backgroundColor = '';
    document.body.style.margin = '';
    document.body.style.padding = '';
    document.body.style.minHeight = '';
  }
};
</script> 

<style scoped>
.container {
  min-height: 100vh;
  background-color: #111827;
  color: #e5e7eb;
  padding: 2.5rem;
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
}

body {
  background-color: #111827;
  margin: 0;
  padding: 0;
}

h1 {
  font-weight: 800;
  background: linear-gradient(90deg, #6366f1, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin-bottom: 1.5rem;
}

.card {
  background: rgba(255, 255, 255, 0.03) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  border-radius: 1rem !important;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1) !important;
  margin-bottom: 1.5rem;
}

.card-header {
  background-color: rgba(26, 32, 50, 0.8) !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
  padding: 1rem 1.5rem !important;
}

.card-header.bg-primary {
  background: rgba(26, 32, 50, 0.8) !important;
}

.card-header h5 {
  color: #ffffff !important;
  font-weight: 600 !important;
  font-size: 18px !important;
  margin-bottom: 0 !important;
}

.card-body {
  background-color: #111827 !important;
  color: #d1d5db !important;
  padding: 1.5rem !important;
}

.fw-bold {
  color: #ffffff !important;
}

.result-image {
  max-width: 100%;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.25) !important;
}

.image-container {
  position: relative;
  max-width: 100%;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading-indicator {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.chart-container {
  height: 400px;
  margin-top: 20px;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(17, 24, 39, 0.5) !important;
  border-radius: 8px !important;
  padding: 15px !important;
}

.badge {
  font-size: 0.9em;
  padding: 0.5em 0.8em;
}

.empty-data {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 300px;
  color: #d1d5db !important;
  font-style: italic;
}

/* 表格样式 */
.table {
  background-color: #111827 !important;
  color: #ffffff !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
}

.table-bordered {
  border-color: rgba(255, 255, 255, 0.1) !important;
}

.table th, 
.table td {
  background-color: #111827 !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
  color: #ffffff !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
}

.table thead th {
  background-color: rgba(26, 32, 50, 0.8) !important;
  color: #ffffff !important;
  font-weight: 600 !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15) !important;
}

.table-light {
  background-color: rgba(26, 32, 50, 0.8) !important;
}

/* 标签页样式 */
.nav-tabs {
  border-bottom: 1px solid rgba(255, 255, 255, 0.1) !important;
}

.nav-tabs .nav-link {
  color: #d1d5db !important;
  background-color: transparent !important;
  border: none !important;
  border-bottom: 2px solid transparent !important;
  border-radius: 0 !important;
  padding: 0.5rem 1rem !important;
  transition: all 0.3s !important;
}

.nav-tabs .nav-link:hover {
  color: #ffffff !important;
  border-color: transparent !important;
}

.nav-tabs .nav-link.active {
  color: #6366f1 !important;
  background-color: transparent !important;
  border-bottom: 2px solid #6366f1 !important;
  font-weight: 500 !important;
}

/* 按钮样式 */
.btn-primary {
  background-color: rgba(79, 70, 229, 0.9) !important;
  border-color: rgba(79, 70, 229, 0.9) !important;
  color: #ffffff !important;
  border-radius: 0.25rem !important;
  transition: all 0.2s !important;
  font-weight: 600 !important;
}

.btn-primary:hover {
  background-color: rgba(99, 90, 249, 0.9) !important;
  transform: translateY(-1px) !important;
  box-shadow: 0 2px 5px rgba(79, 70, 229, 0.3) !important;
}

.btn-info {
  background-color: rgba(59, 130, 246, 0.9) !important;
  border-color: rgba(59, 130, 246, 0.9) !important;
  color: #ffffff !important;
  border-radius: 0.25rem !important;
  transition: all 0.2s !important;
  font-weight: 600 !important;
}

.btn-info:hover {
  background-color: rgba(96, 165, 250, 0.9) !important;
  transform: translateY(-1px) !important;
  box-shadow: 0 2px 5px rgba(59, 130, 246, 0.3) !important;
}

.btn-outline-secondary {
  background: rgba(45, 55, 72, 0.5) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  color: rgba(255, 255, 255, 0.9) !important;
  border-radius: 0.25rem !important;
  transition: all 0.2s !important;
  font-weight: 600 !important;
}

.btn-outline-secondary:hover {
  background: rgba(55, 65, 82, 0.5) !important;
  color: white !important;
  transform: translateY(-1px) !important;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2) !important;
}

/* 警告框样式 */
.alert {
  border-radius: 0.75rem !important;
  border: 1px solid transparent !important;
}

.alert-warning {
  background-color: rgba(245, 158, 11, 0.2) !important;
  color: #fbbf24 !important;
  border-color: rgba(245, 158, 11, 0.3) !important;
}

.alert-danger {
  background-color: rgba(239, 68, 68, 0.2) !important;
  color: #f87171 !important;
  border-color: rgba(239, 68, 68, 0.3) !important;
}

/* 进度条样式 */
.spinner-border {
  border-color: rgba(99, 102, 241, 0.2) !important;
  border-right-color: #6366f1 !important;
}

.result-image-container {
  max-width: 100%;
  max-height: 500px;
  overflow: hidden;
}

/* 修复空白问题 - 添加全局样式 */
:deep(body), 
:deep(html) {
  background-color: #111827 !important;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  overflow-x: hidden;
}

/* 改善小文本对比度 */
.text-muted {
  color: #9ca3af !important;
  font-weight: 500 !important;
}

/* 确保图表内文字可见 */
:deep(.vehicle-chart),
:deep(.echarts) {
  color: #ffffff !important;
}

:deep(.echarts text) {
  fill: #ffffff !important;
  font-weight: 500 !important;
}

:deep(.echarts .axis-label),
:deep(.echarts .item-label) {
  fill: #ffffff !important;
  text-shadow: 0 0 2px rgba(0, 0, 0, 0.8) !important;
}

:deep(.echarts path) {
  stroke: rgba(255, 255, 255, 0.25) !important;
}

:deep(.echarts line) {
  stroke: rgba(255, 255, 255, 0.25) !important;
}

/* 修复图表tooltip样式 */
:deep(.echarts .tooltip-item) {
  color: #ffffff !important;
}

:deep(div[class][style*="background-color: rgb(255, 255, 255)"][style*="border-width: 1px"][style*="pointer-events: none"]) {
  background-color: rgba(26, 32, 50, 0.95) !important;
  border-color: rgba(255, 255, 255, 0.2) !important;
  color: #ffffff !important;
  font-weight: 500 !important;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5) !important;
}

:deep(.echarts-tooltip) {
  background-color: rgba(26, 32, 50, 0.95) !important;
  border-color: rgba(255, 255, 255, 0.2) !important;
  color: #ffffff !important;
}

/* 增强图表标题和图例文字可见性 */
:deep(.echarts .chart-title),
:deep(.echarts text[style*="font-weight:bold"]),
:deep(.echarts text[style*="font-size:18px"]),
:deep(.echarts text[text-anchor="middle"][dominant-baseline="central"]) {
  fill: #ffffff !important;
  font-weight: 700 !important;
  font-size: 18px !important;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.5) !important;
}

/* 增强图例文字可见性 */
:deep(.echarts .legend),
:deep(.echarts text[text-anchor="start"]) {
  fill: #ffffff !important;
  font-weight: 600 !important;
  font-size: 14px !important;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.5) !important;
}

/* 更改渲染模式，提高文字锐利度 */
:deep(.echarts canvas) {
  image-rendering: -webkit-optimize-contrast !important;
  image-rendering: crisp-edges !important;
}
</style> 

