<template>
  <div class="video-analytics-panel">
    <div class="summary-cards" v-if="analysisSummary">
      <el-row :gutter="20">
        <el-col :span="8">
          <el-card class="stat-card">
            <template #header>
              <div class="card-header">
                <span>总车辆数</span>
              </div>
            </template>
            <div class="card-value">
              {{ vehicleCount || 0 }}
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="stat-card">
            <template #header>
              <div class="card-header">
                <span>处理时间</span>
              </div>
            </template>
            <div class="card-value">
              {{ processingTime ? processingTime.toFixed(2) + '秒' : '数据不可用' }}
            </div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card class="stat-card">
            <template #header>
              <div class="card-header">
                <span>拥堵等级</span>
              </div>
            </template>
            <div class="card-value">
              {{ getCongestionLevel(vehicleCount) }}
            </div>
          </el-card>
        </el-col>
      </el-row>
      
      <!-- 添加车辆类型统计卡片 -->
      <h3 class="section-title">车辆类型统计</h3>
      <el-row :gutter="20" class="vehicle-type-cards">
        <el-col :span="4" v-if="vehicleTypeStats && vehicleTypeStats.car !== undefined">
          <el-card class="vehicle-type-card">
            <div class="vehicle-icon">🚗</div>
            <div class="vehicle-count">{{ vehicleTypeStats.car }}</div>
            <div class="vehicle-label">小汽车</div>
          </el-card>
        </el-col>
        <el-col :span="4" v-if="vehicleTypeStats && vehicleTypeStats.motorcycle !== undefined">
          <el-card class="vehicle-type-card">
            <div class="vehicle-icon">🏍️</div>
            <div class="vehicle-count">{{ vehicleTypeStats.motorcycle }}</div>
            <div class="vehicle-label">摩托车</div>
          </el-card>
        </el-col>
        <el-col :span="4" v-if="vehicleTypeStats && vehicleTypeStats.truck !== undefined">
          <el-card class="vehicle-type-card">
            <div class="vehicle-icon">🚚</div>
            <div class="vehicle-count">{{ vehicleTypeStats.truck }}</div>
            <div class="vehicle-label">卡车</div>
          </el-card>
        </el-col>
        <el-col :span="4" v-if="vehicleTypeStats && vehicleTypeStats.bus !== undefined">
          <el-card class="vehicle-type-card">
            <div class="vehicle-icon">🚌</div>
            <div class="vehicle-count">{{ vehicleTypeStats.bus }}</div>
            <div class="vehicle-label">公交车</div>
          </el-card>
        </el-col>
        <el-col :span="4" v-if="vehicleTypeStats && vehicleTypeStats.bicycle !== undefined">
          <el-card class="vehicle-type-card">
            <div class="vehicle-icon">🚲</div>
            <div class="vehicle-count">{{ vehicleTypeStats.bicycle }}</div>
            <div class="vehicle-label">自行车</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <template v-if="isIntersection">
      <div class="charts-actions">
        <el-button size="small" @click="exportAllCharts" type="success">
          <el-icon><download /></el-icon> 导出所有图表
        </el-button>
      </div>
      <h3>十字路口综合分析</h3>
      <div class="intersection-view">
        <el-row :gutter="20">
          <el-col :span="12">
            <div class="traffic-flow-card">
              <h4>东西方向车流量</h4>
              <div class="chart-mini-container">
                <div ref="horizontalFlowChart" style="width: 100%; height: 280px;"></div>
              </div>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="traffic-flow-card">
              <h4>南北方向车流量</h4>
              <div class="chart-mini-container">
                <div ref="verticalFlowChart" style="width: 100%; height: 280px;"></div>
              </div>
            </div>
          </el-col>
        </el-row>
        
        <el-row style="margin-top: 30px">
          <el-col :span="24">
            <div class="traffic-comparison-card">
              <h4>方向车流量对比</h4>
              <div class="chart-container">
                <div ref="combinedFlowChart" style="width: 100%; height: 350px;"></div>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
      
      <el-divider></el-divider>
    </template>
  
    <h3 class="traffic-trend-title">车流量趋势</h3>
    <div class="chart-container">
      <div ref="lineChart" style="width: 100%; height: 350px;"></div>
    </div>
    
    <el-divider></el-divider>
    
    <el-row :gutter="20">
      <el-col :md="12">
        <h3>车型分布</h3>
        <div class="chart-container">
          <div ref="pieChart" style="width: 100%; height: 350px;"></div>
        </div>
      </el-col>
      <el-col :md="12">
        <h3>方向分布</h3>
        <div class="chart-container">
          <div ref="barChart" style="width: 100%; height: 350px;"></div>
        </div>
      </el-col>
    </el-row>
    
    <!-- 添加交通解决方案建议 -->
    <div class="solution-section" v-if="vehicleCount > 0">
      <h3>交通解决方案</h3>
      <el-alert
        :type="getSolutionAlertType(vehicleCount)"
        :title="getSolutionTitle(vehicleCount)"
        :description="getSolutionDescription(vehicleCount)"
        show-icon
        :closable="false"
        class="solution-alert"
      ></el-alert>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, nextTick, watch } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { Download } from '@element-plus/icons-vue';

export default {
  name: 'VideoAnalyticsPanel',
  components: {
    Download
  },
  props: {
    // 分析结果数据
    result: {
      type: Object,
      default: () => ({})
    }
  },
  setup(props) {
    // 图表引用
    const horizontalFlowChart = ref(null);
    const verticalFlowChart = ref(null);
    const combinedFlowChart = ref(null);
    const lineChart = ref(null);
    const pieChart = ref(null);
    const barChart = ref(null);
    
    // 图表实例
    let horizontalFlowChartInstance = null;
    let verticalFlowChartInstance = null;
    let combinedFlowChartInstance = null;
    let lineChartInstance = null;
    let pieChartInstance = null;
    let barChartInstance = null;
    
    // 计算属性
    const analysisSummary = computed(() => props.result?.analysis_summary || null);
    const isIntersection = computed(() => props.result?.mode === 'intersection');
    
    // 新增计算属性
    const vehicleCount = computed(() => {
      if (!props.result || !props.result.vehicle_type_stats) {
        return props.result?.vehicle_count || 0;
      }
      
      // 计算所有车辆类型数量之和
      let total = 0;
      Object.values(props.result.vehicle_type_stats).forEach(count => {
        total += count;
      });
      
      return total;
    });
    const processingTime = computed(() => props.result?.processing_time || 0);
    const vehicleTypeStats = computed(() => props.result?.vehicle_type_stats || {});
    
    // 格式化时间
    const formatTime = (seconds) => {
      const mins = Math.floor(seconds / 60);
      const secs = Math.floor(seconds % 60);
      return `${mins}分${secs.toString().padStart(2, '0')}秒`;
    };
    
    // 根据车辆数量获取拥堵等级
    const getCongestionLevel = (count) => {
      if (count <= 5) return '不拥挤';
      if (count <= 10) return '一般';
      if (count <= 20) return '较拥挤';
      return '拥挤';
    };
    
    // 获取解决方案的标题
    const getSolutionTitle = (count) => {
      if (count <= 5) return '方案一：正常红绿灯交换';
      if (count <= 10) return '方案二：延长横向绿灯时间';
      if (count <= 20) return '方案三：延长纵向绿灯时间';
      return '方案四：发出提醒（需人为干预）';
    };
    
    // 获取解决方案的描述
    const getSolutionDescription = (count) => {
      if (count <= 5) return '当前车流量正常，可维持默认信号灯配置。';
      if (count <= 10) return '当前车流量略大，建议延长东西方向绿灯时间，确保车辆顺畅通行。';
      if (count <= 20) return '当前车流量较大，建议延长南北方向绿灯时间，减少车辆积压。';
      return '当前车流量大，可能出现拥堵，建议启动交通应急预案，必要时派遣交警现场指挥。';
    };
    
    // 获取解决方案的警告类型
    const getSolutionAlertType = (count) => {
      if (count <= 5) return 'success';
      if (count <= 10) return 'info';
      if (count <= 20) return 'warning';
      return 'error';
    };
    
    // 图表配置
    const lineChartOption = computed(() => {
      if (!props.result || !props.result.time_series_data) {
        return {};
      }
      
      const timeData = props.result.time_series_data.timestamps.map(formatTime);
      const countData = props.result.time_series_data.vehicle_counts;
      
      return {
        title: {
          text: '车流量时间趋势'
        },
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          data: timeData,
          axisLabel: {
            rotate: 45
          },
          name: '时间'
        },
        yAxis: {
          type: 'value',
          name: '车辆数'
        },
        series: [
          {
            name: '车辆数',
            type: 'line',
            data: countData,
            smooth: true
          }
        ]
      };
    });
    
    const pieChartOption = computed(() => {
      if (!props.result || !props.result.vehicle_type_stats) {
        return {};
      }
      
      const vehicleTypeMapping = {
        car: '小汽车',
        motorcycle: '摩托车',
        truck: '卡车',
        bus: '公交车',
        bicycle: '自行车'
      };
      
      // 将车辆类型数据转换为饼图所需格式
      const seriesData = Object.entries(props.result.vehicle_type_stats).map(([type, count]) => ({
        name: vehicleTypeMapping[type] || type,
        value: count
      }));
      
      return {
        title: {
          text: '车型分布',
          textStyle: {
            fontSize: 16,
            fontWeight: 'bold',
            color: '#ffffff',
            fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
          }
        },
        tooltip: {
          trigger: 'item',
          formatter: '{a} <br/>{b}: {c} ({d}%)'
        },
        legend: {
          orient: 'vertical',
          right: 10,
          top: 'center',
          textStyle: {
            color: '#e5e7eb',
            fontSize: 12,
            fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
          }
        },
        series: [
          {
            name: '车型',
            type: 'pie',
            radius: ['40%', '70%'],
            avoidLabelOverlap: false,
            label: {
              show: false,
              position: 'center'
            },
            emphasis: {
              label: {
                show: true,
                fontSize: 14,
                fontWeight: 'bold',
                color: '#ffffff'
              }
            },
            labelLine: {
              show: false
            },
            data: seriesData
          }
        ],
        color: ['#4f46e5', '#10b981', '#ef4444', '#f59e0b', '#3b82f6']
      };
    });
    
    const barChartOption = computed(() => {
      if (!props.result || !props.result.analysis_summary || !props.result.analysis_summary.direction_distribution) {
        return {};
      }
      
      const directionMap = {
        'eastbound': '东向西',
        'westbound': '西向东',
        'northbound': '北向南',
        'southbound': '南向北'
      };
      
      const categories = [];
      const data = [];
      
      Object.entries(props.result.analysis_summary.direction_distribution).forEach(([dir, count]) => {
        categories.push(directionMap[dir] || dir);
        data.push(count);
      });
      
      return {
        title: {
          text: '方向分布',
          textStyle: {
            fontSize: 16,
            fontWeight: 'bold',
            color: '#ffffff',
            fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
          }
        },
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          data: categories,
          name: '行驶方向',
          nameTextStyle: {
            fontSize: 14,
            fontWeight: 'bold',
            color: '#e5e7eb',
            padding: [5, 0, 0, 0],
            fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
          },
          axisLabel: {
            fontSize: 13,
            fontWeight: 'bold',
            color: '#e5e7eb',
            fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
          }
        },
        yAxis: {
          type: 'value',
          name: '车辆数',
          nameTextStyle: {
            fontSize: 14,
            fontWeight: 'bold',
            color: '#e5e7eb',
            padding: [0, 0, 5, 0],
            fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
          },
          axisLabel: {
            fontSize: 13,
            fontWeight: 'bold',
            color: '#e5e7eb',
            fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
          }
        },
        series: [
          {
            name: '车辆数',
            type: 'bar',
            data: data,
            barWidth: '40%',
            itemStyle: {
              color: '#5470c6'
            },
            label: {
              show: true,
              position: 'top',
              fontSize: 14,
              fontWeight: 'bold',
              color: '#ffffff',
              fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
            }
          }
        ]
      };
    });
    
    // 添加十字路口分析相关的图表选项
    const horizontalFlowOption = computed(() => {
      if (!props.result || !props.result.horizontal_data || !props.result.horizontal_data.time_series_data) {
        return {};
      }
      
      const timeData = props.result.horizontal_data.time_series_data.timestamps.map(formatTime);
      const countData = props.result.horizontal_data.time_series_data.vehicle_counts;
      
      return {
        title: {
          text: '东西方向车流量'
        },
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          data: timeData,
          axisLabel: {
            rotate: 45
          }
        },
        yAxis: {
          type: 'value',
          name: '车辆数'
        },
        series: [
          {
            name: '车辆数',
            type: 'line',
            data: countData,
            smooth: true,
            itemStyle: {
              color: '#409EFF'
            }
          }
        ]
      };
    });
    
    const verticalFlowOption = computed(() => {
      if (!props.result || !props.result.vertical_data || !props.result.vertical_data.time_series_data) {
        return {};
      }
      
      const timeData = props.result.vertical_data.time_series_data.timestamps.map(formatTime);
      const countData = props.result.vertical_data.time_series_data.vehicle_counts;
      
      return {
        title: {
          text: '南北方向车流量'
        },
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          data: timeData,
          axisLabel: {
            rotate: 45
          }
        },
        yAxis: {
          type: 'value',
          name: '车辆数'
        },
        series: [
          {
            name: '车辆数',
            type: 'line',
            data: countData,
            smooth: true,
            itemStyle: {
              color: '#67C23A'
            }
          }
        ]
      };
    });
    
    const combinedFlowOption = computed(() => {
      if (!props.result || !props.result.horizontal_data || !props.result.vertical_data) {
        return {};
      }
      
      // 获取横向数据
      const horizontalTimeData = props.result.horizontal_data.time_series_data?.timestamps || [];
      const horizontalCountData = props.result.horizontal_data.time_series_data?.vehicle_counts || [];
      
      // 获取纵向数据
      const verticalTimeData = props.result.vertical_data.time_series_data?.timestamps || [];
      const verticalCountData = props.result.vertical_data.time_series_data?.vehicle_counts || [];
      
      // 合并时间数据，确保时间点一致
      const timePoints = [...new Set([...horizontalTimeData, ...verticalTimeData])].sort((a, b) => a - b);
      const formattedTimePoints = timePoints.map(formatTime);
      
      // 为时间点找到对应的数据，如果没有则为0
      const getCountAtTime = (times, counts, timePoint) => {
        const index = times.indexOf(timePoint);
        return index !== -1 ? counts[index] : 0;
      };
      
      // 为每个时间点构建横向和纵向数据
      const horizontalData = timePoints.map(t => getCountAtTime(horizontalTimeData, horizontalCountData, t));
      const verticalData = timePoints.map(t => getCountAtTime(verticalTimeData, verticalCountData, t));
      
      return {
        title: {
          text: '方向车流量对比'
        },
        tooltip: {
          trigger: 'axis'
        },
        legend: {
          data: ['东西方向', '南北方向']
        },
        xAxis: {
          type: 'category',
          data: formattedTimePoints,
          axisLabel: {
            rotate: 45
          }
        },
        yAxis: {
          type: 'value',
          name: '车辆数'
        },
        series: [
          {
            name: '东西方向',
            type: 'line',
            data: horizontalData,
            smooth: true,
            itemStyle: {
              color: '#409EFF'
            }
          },
          {
            name: '南北方向',
            type: 'line',
            data: verticalData,
            smooth: true,
            itemStyle: {
              color: '#67C23A'
            }
          }
        ]
      };
    });
    
    // 初始化图表
    const initCharts = async () => {
      // 清除旧的图表实例
      disposeCharts();
      
      // 如果结果为空或页面不可见，取消初始化
      if (!props.result || document.hidden) {
        return;
      }
      
      // 使用Promise.all等待所有图表初始化完成
      await new Promise(resolve => setTimeout(resolve, 200)); // 等待200ms确保DOM布局完成
      
      // 定义全局字体设置 - 修改字体族
      const textStyle = {
        fontSize: 14,
        fontWeight: 'bold',
        fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
        color: '#ffffff'
      };

      // 坐标轴标签样式 - 使用更好的中文字体
      const axisLabelStyle = {
        fontSize: 13,
        fontWeight: 'bold',
        color: '#e5e7eb',
        fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
      };

      // 轴线样式
      const axisLineStyle = {
        lineStyle: {
          color: 'rgba(255, 255, 255, 0.2)'
        }
      };
      
      // 更安全的图表初始化函数
      const safeInitChart = (element, option, name) => {
        return new Promise((resolve) => {
          if (!element) {
            console.log(`图表DOM引用不存在: ${name}`);
            resolve(null);
            return;
          }
          
          // 验证DOM元素已渲染且可见
          if (element.offsetWidth <= 0 || element.offsetHeight <= 0) {
            console.log(`图表DOM元素尺寸无效: ${name} (${element.offsetWidth}x${element.offsetHeight})`);
            resolve(null);
            return;
          }
          
          try {
            console.log(`初始化图表: ${name} (${element.offsetWidth}x${element.offsetHeight})`);
            
            // 设置设备像素比，提高清晰度
            const devicePixelRatio = window.devicePixelRatio || 2;
            
            // 应用全局字体设置
            option.textStyle = textStyle;
            
            // 增强轴标签清晰度
            if (option.xAxis) {
              if (Array.isArray(option.xAxis)) {
                option.xAxis.forEach(axis => {
                  axis.axisLabel = { 
                    ...axis.axisLabel, 
                    ...axisLabelStyle,
                    fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                    fontSize: 13,
                    fontWeight: 'bold',
                    textShadow: '0 0 2px rgba(0,0,0,0.3)',
                    rich: {
                      a: {
                        fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                        fontWeight: 'bold'
                      }
                    }
                  };
                  axis.axisLine = { ...axis.axisLine, ...axisLineStyle };
                  // 添加轴名称样式
                  if (axis.name) {
                    axis.nameTextStyle = {
                      fontSize: 14,
                      fontWeight: 'bold',
                      color: '#e5e7eb',
                      padding: [5, 0, 0, 0],
                      fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                      textShadow: '0 0 2px rgba(0,0,0,0.3)'
                    };
                  }
                });
              } else {
                option.xAxis.axisLabel = { 
                  ...option.xAxis.axisLabel, 
                  ...axisLabelStyle,
                  fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                  fontSize: 13, 
                  fontWeight: 'bold',
                  textShadow: '0 0 2px rgba(0,0,0,0.3)',
                  rich: {
                    a: {
                      fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                      fontWeight: 'bold'
                    }
                  }
                };
                option.xAxis.axisLine = { ...option.xAxis.axisLine, ...axisLineStyle };
                // 添加轴名称样式
                if (option.xAxis.name) {
                  option.xAxis.nameTextStyle = {
                    fontSize: 14,
                    fontWeight: 'bold',
                    color: '#e5e7eb',
                    padding: [5, 0, 0, 0],
                    fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                    textShadow: '0 0 2px rgba(0,0,0,0.3)'
                  };
                }
              }
            }
            
            if (option.yAxis) {
              if (Array.isArray(option.yAxis)) {
                option.yAxis.forEach(axis => {
                  axis.axisLabel = { 
                    ...axis.axisLabel, 
                    ...axisLabelStyle,
                    fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                    fontSize: 13,
                    fontWeight: 'bold',
                    textShadow: '0 0 2px rgba(0,0,0,0.3)',
                    rich: {
                      a: {
                        fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                        fontWeight: 'bold'
                      }
                    }
                  };
                  axis.axisLine = { ...axis.axisLine, ...axisLineStyle };
                  // 添加轴名称样式
                  if (axis.name) {
                    axis.nameTextStyle = {
                      fontSize: 14,
                      fontWeight: 'bold',
                      color: '#e5e7eb',
                      padding: [0, 0, 5, 0],
                      fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                      textShadow: '0 0 2px rgba(0,0,0,0.3)'
                    };
                  }
                });
              } else {
                option.yAxis.axisLabel = { 
                  ...option.yAxis.axisLabel, 
                  ...axisLabelStyle,
                  fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                  fontSize: 13,
                  fontWeight: 'bold',
                  textShadow: '0 0 2px rgba(0,0,0,0.3)',
                  rich: {
                    a: {
                      fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                      fontWeight: 'bold'
                    }
                  }
                };
                option.yAxis.axisLine = { ...option.yAxis.axisLine, ...axisLineStyle };
                // 添加轴名称样式
                if (option.yAxis.name) {
                  option.yAxis.nameTextStyle = {
                    fontSize: 14,
                    fontWeight: 'bold',
                    color: '#e5e7eb',
                    padding: [0, 0, 5, 0],
                    fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                    textShadow: '0 0 2px rgba(0,0,0,0.3)'
                  };
                }
              }
            }
            
            // 增强标题清晰度
            if (option.title) {
              option.title.textStyle = {
                fontSize: 16,
                fontWeight: 'bold',
                color: '#ffffff',
                fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                textShadow: '0 0 2px rgba(0,0,0,0.3)'
              };
            }
            
            // 增强提示框清晰度
            if (option.tooltip) {
              option.tooltip.textStyle = {
                fontSize: 14,
                fontWeight: 'normal',
                fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif'
              };
            }
            
            // 增强图例清晰度
            if (option.legend) {
              option.legend.textStyle = {
                fontSize: 13,
                fontWeight: 'bold',
                color: '#e5e7eb',
                fontFamily: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
                textShadow: '0 0 2px rgba(0,0,0,0.3)'
              };
            }
            
            const instance = echarts.init(element, null, {
              devicePixelRatio: devicePixelRatio,
              renderer: 'canvas'  // 显式指定渲染器
            });
            instance.setOption(option);
            resolve(instance);
          } catch (err) {
            console.error(`初始化图表失败 ${name}:`, err);
            resolve(null);
          }
        });
      };
      
      const chartsToInit = [];
      
      // 初始化东西方向车流量图
      if (horizontalFlowChart.value && props.result?.horizontal_data) {
        chartsToInit.push(
          safeInitChart(horizontalFlowChart.value, horizontalFlowOption.value, "东西方向车流量")
            .then(instance => { if (instance) horizontalFlowChartInstance = instance; })
        );
      }
      
      // 初始化南北方向车流量图
      if (verticalFlowChart.value && props.result?.vertical_data) {
        chartsToInit.push(
          safeInitChart(verticalFlowChart.value, verticalFlowOption.value, "南北方向车流量")
            .then(instance => { if (instance) verticalFlowChartInstance = instance; })
        );
      }
      
      // 初始化方向车流量对比图
      if (combinedFlowChart.value && props.result?.horizontal_data && props.result?.vertical_data) {
        chartsToInit.push(
          safeInitChart(combinedFlowChart.value, combinedFlowOption.value, "方向车流量对比")
            .then(instance => { if (instance) combinedFlowChartInstance = instance; })
        );
      }
      
      // 初始化车流量时间分布图
      if (lineChart.value && props.result?.time_series_data) {
        chartsToInit.push(
          safeInitChart(lineChart.value, lineChartOption.value, "车流量时间趋势")
            .then(instance => { if (instance) lineChartInstance = instance; })
        );
      }
      
      // 初始化车型分布图
      if (pieChart.value && props.result?.vehicle_type_stats) {
        chartsToInit.push(
          safeInitChart(pieChart.value, pieChartOption.value, "车型分布")
            .then(instance => { if (instance) pieChartInstance = instance; })
        );
      }
      
      // 初始化方向分布图
      if (barChart.value && props.result?.analysis_summary?.direction_distribution) {
        chartsToInit.push(
          safeInitChart(barChart.value, barChartOption.value, "方向分布")
            .then(instance => { if (instance) barChartInstance = instance; })
        );
      }
      
      // 等待所有图表初始化完成
      try {
        await Promise.all(chartsToInit);
        console.log('所有图表初始化完成');
      } catch (err) {
        console.error('图表初始化过程中出错:', err);
      }
    };
    
    // 清除图表实例
    const disposeCharts = () => {
      horizontalFlowChartInstance && horizontalFlowChartInstance.dispose();
      verticalFlowChartInstance && verticalFlowChartInstance.dispose();
      combinedFlowChartInstance && combinedFlowChartInstance.dispose();
      lineChartInstance && lineChartInstance.dispose();
      pieChartInstance && pieChartInstance.dispose();
      barChartInstance && barChartInstance.dispose();
      
      horizontalFlowChartInstance = null;
      verticalFlowChartInstance = null;
      combinedFlowChartInstance = null;
      lineChartInstance = null;
      pieChartInstance = null;
      barChartInstance = null;
    };
    
    // 导出图表为图片
    const exportChart = (chartInstance, fileName) => {
      if (!chartInstance) return;
      
      try {
        const dataURL = chartInstance.getDataURL({
          type: 'png',
          pixelRatio: 2,
          backgroundColor: '#fff'
        });
        
        const link = document.createElement('a');
        link.download = fileName;
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
      } catch (err) {
        ElMessage.error('导出图表失败: ' + err.message);
      }
    };
    
    // 导出所有图表
    const exportAllCharts = () => {
      if (lineChartInstance) {
        exportChart(lineChartInstance, '车流量时间趋势.png');
      }
      
      if (pieChartInstance) {
        exportChart(pieChartInstance, '车型分布.png');
      }
      
      if (barChartInstance) {
        exportChart(barChartInstance, '方向分布.png');
      }
      
      if (props.result?.mode === 'intersection') {
        if (horizontalFlowChartInstance) {
          exportChart(horizontalFlowChartInstance, '东西方向车流量.png');
        }
        
        if (verticalFlowChartInstance) {
          exportChart(verticalFlowChartInstance, '南北方向车流量.png');
        }
        
        if (combinedFlowChartInstance) {
          exportChart(combinedFlowChartInstance, '方向车流量对比.png');
        }
      }
      
      ElMessage.success('图表导出完成');
    };
    
    // 监听窗口大小变化，重新调整图表大小
    const handleResize = () => {
      // 对每个图表实例进行空检查并重绘
      if (horizontalFlowChartInstance) {
        try {
          horizontalFlowChartInstance.resize();
        } catch (err) {}
      }
      
      if (verticalFlowChartInstance) {
        try {
          verticalFlowChartInstance.resize();
        } catch (err) {}
      }
      
      if (combinedFlowChartInstance) {
        try {
          combinedFlowChartInstance.resize();
        } catch (err) {}
      }
      
      if (lineChartInstance) {
        try {
          lineChartInstance.resize();
        } catch (err) {}
      }
      
      if (pieChartInstance) {
        try {
          pieChartInstance.resize();
        } catch (err) {}
      }
      
      if (barChartInstance) {
        try {
          barChartInstance.resize();
        } catch (err) {}
      }
    };
    
    // 监听结果变化
    watch(() => props.result, () => {
      // 当结果发生变化时，重新初始化图表
      nextTick(() => {
        setTimeout(() => {
          initCharts();
        }, 300);
      });
    }, { deep: true });
    
    onMounted(() => {
      // 延迟初始化图表，确保DOM已完全渲染
      nextTick(() => {
        setTimeout(() => {
          initCharts();
        }, 300);
      });
      
      // 添加窗口大小变化监听
      window.addEventListener('resize', handleResize);
    });
    
    return {
      analysisSummary,
      isIntersection,
      vehicleCount,
      processingTime,
      vehicleTypeStats,
      horizontalFlowChart,
      verticalFlowChart,
      combinedFlowChart,
      lineChart,
      pieChart,
      barChart,
      formatTime,
      getCongestionLevel,
      getSolutionTitle,
      getSolutionDescription,
      getSolutionAlertType,
      exportAllCharts,
      initCharts,
      disposeCharts
    };
  },
  beforeUnmount() {
    // 卸载组件前清理资源
    this.disposeCharts();
    window.removeEventListener('resize', this.handleResize);
  }
};
</script>

<style scoped>
.video-analytics-panel {
  width: 100%;
}

.summary-cards {
  margin-top: 20px;
}

.stat-card {
  text-align: center;
  background: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
}

:deep(.stat-card .el-card__header) {
  background-color: rgba(26, 32, 50, 0.8);
}

.card-header {
  font-weight: bold;
  color: #ffffff;
}

.card-value {
  font-size: 28px !important;
  font-weight: 700 !important;
  color: #6366f1 !important;
  padding: 20px 0 !important;
}

.section-title {
  margin-top: 30px;
  margin-bottom: 15px;
  color: #ffffff;
  font-weight: 600;
}

.vehicle-type-cards {
  margin-bottom: 30px;
}

.vehicle-type-card {
  text-align: center;
  background: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  padding: 15px 0;
  transition: all 0.3s ease;
  height: 100%;
}

.vehicle-type-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.1);
}

.vehicle-icon {
  font-size: 30px;
  margin-bottom: 10px;
}

.vehicle-count {
  font-size: 24px;
  font-weight: 700;
  color: #6366f1;
  margin-bottom: 5px;
}

.vehicle-label {
  color: #d1d5db;
  font-size: 14px;
}

.chart-container {
  height: 350px;
  margin-top: 20px;
  margin-bottom: 30px;
  background: rgba(17, 24, 39, 0.5);
  border-radius: 8px;
  padding: 15px;
}

.chart-mini-container {
  height: 280px;
  margin-top: 15px;
  margin-bottom: 15px;
  background: rgba(17, 24, 39, 0.5);
  border-radius: 8px;
  padding: 10px;
}

.traffic-flow-card,
.traffic-comparison-card {
  padding: 15px;
  background-color: rgba(17, 24, 39, 0.5);
  border-radius: 8px;
}

.traffic-comparison-card {
  padding: 20px;
}

.traffic-flow-card h4,
.traffic-comparison-card h4 {
  color: #e5e7eb;
  margin-bottom: 10px;
}

h3 {
  color: #ffffff !important;
  font-weight: 600 !important;
  margin-bottom: 15px;
}

/* 增大车流量趋势标题 */
h3.traffic-trend-title {
  font-size: 24px !important;
  margin-top: 30px !important;
  margin-bottom: 20px !important;
  font-weight: 700 !important;
}

.charts-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 15px;
}

.solution-section {
  margin-top: 30px;
  margin-bottom: 30px;
}

.solution-alert {
  background: rgba(17, 24, 39, 0.5) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
  margin-top: 15px;
}

:deep(.solution-alert .el-alert__title) {
  font-size: 16px !important;
  font-weight: 600 !important;
}

:deep(.solution-alert .el-alert__description) {
  font-size: 14px !important;
  margin-top: 8px !important;
}
</style> 