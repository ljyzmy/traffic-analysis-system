<template>
  <div class="vehicle-chart">
    <div class="chart-container" ref="chartRef"></div>
  </div>
</template>

<script setup>
/* eslint-disable no-undef */
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import * as echarts from 'echarts/core';
import { BarChart, PieChart } from 'echarts/charts';
import { TitleComponent, TooltipComponent, GridComponent, LegendComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

// 注册必要的组件
echarts.use([TitleComponent, TooltipComponent, GridComponent, LegendComponent, BarChart, PieChart, CanvasRenderer]);

const props = defineProps({
  data: {
    type: Object,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  },
  chartType: {
    type: String,
    default: 'bar', // 'bar' 或 'pie'
    validator: (value) => ['bar', 'pie'].includes(value)
  }
});

const chartRef = ref(null);
let chart = null;

// 处理数据格式
const chartData = computed(() => {
  if (!props.data) return { categories: [], counts: [] };
  
  // 处理 vehicleTypeStats 格式的数据
  if (props.data.vehicleTypeStats) {
    const categories = Object.keys(props.data.vehicleTypeStats);
    const counts = categories.map(cat => props.data.vehicleTypeStats[cat]);
    return { categories, counts };
  }
  
  // 处理传统格式数据
  if (props.data.categories && props.data.counts) {
    return props.data;
  }
  
  // 如果是单纯的对象 {car: 5, truck: 3} 格式
  if (typeof props.data === 'object' && !Array.isArray(props.data)) {
    const categories = [];
    const counts = [];
    
    for (const [key, value] of Object.entries(props.data)) {
      if (key !== 'count' && key !== 'vehicles' && key !== 'imageUrl' && 
          typeof value === 'number') {
        categories.push(key);
        counts.push(value);
      }
    }
    
    if (categories.length > 0) {
      return { categories, counts };
    }
  }
  
  // 处理 detections 格式数据
  if (props.data.detections && Array.isArray(props.data.detections)) {
    const typeCounter = {};
    props.data.detections.forEach(det => {
      const type = det.className || det.class_name || '未知';
      typeCounter[type] = (typeCounter[type] || 0) + 1;
    });
    
    const categories = Object.keys(typeCounter);
    const counts = categories.map(cat => typeCounter[cat]);
    return { categories, counts };
  }
  
  return { categories: ['无数据'], counts: [0] };
});

// 格式化车辆类型显示名称
const formatVehicleType = (type) => {
  // 英文类型名称转换为中文
  const typeMap = {
    'car': '小汽车',
    'truck': '卡车',
    'bus': '公交车',
    'motorcycle': '摩托车',
    'bicycle': '自行车',
    'person': '行人',
    'traffic light': '交通信号灯',
    'unknown': '未知'
  };
  
  return typeMap[type.toLowerCase()] || type;
};

// 初始化图表
const initChart = () => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value);
    updateChart();
  }
};

// 更新图表
const updateChart = () => {
  if (!chart) return;
  
  const { categories, counts } = chartData.value;
  
  // 格式化车辆类型名称
  const formattedCategories = categories.map(cat => formatVehicleType(cat));
  
  // 颜色配置
  const colorMap = {
    '小汽车': '#5470C6',
    '卡车': '#91CC75',
    '公交车': '#FAC858',
    '摩托车': '#EE6666',
    '自行车': '#73C0DE',
    '行人': '#3BA272',
    '交通信号灯': '#FC8452',
    '未知': '#9A60B4'
  };
  
  // 根据类别名称获取颜色，如果没有预定义则使用默认颜色
  const colors = formattedCategories.map(category => colorMap[category] || null);
  
  // 根据图表类型构建不同的配置
  let option;
  
  if (props.chartType === 'pie') {
    option = {
      title: {
        text: '类型分布',
        left: 'center'
      },
      tooltip: {
        trigger: 'item',
        formatter: '{a} <br/>{b}: {c} ({d}%)'
      },
      legend: {
        orient: 'vertical',
        left: 'left',
        data: formattedCategories
      },
      series: [
        {
          name: '类型分布',
          type: 'pie',
          radius: ['40%', '70%'],
          avoidLabelOverlap: false,
          itemStyle: {
            borderRadius: 10,
            borderColor: '#fff',
            borderWidth: 2
          },
          label: {
            show: true,
            formatter: '{b}: {c} ({d}%)'
          },
          emphasis: {
            label: {
              show: true,
              fontSize: '18',
              fontWeight: 'bold'
            }
          },
          labelLine: {
            show: true
          },
          data: formattedCategories.map((category, index) => ({
            value: counts[index] || 0,
            name: category,
            itemStyle: {
              color: colors[index]
            }
          }))
        }
      ]
    };
  } else {
    // 柱状图配置
    option = {
      title: {
        text: '类型分布',
        left: 'center'
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'shadow'
        },
        formatter: function(params) {
          const data = params[0];
          return `${data.name}: ${data.value} 辆`;
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '15%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: formattedCategories,
        axisLabel: {
          interval: 0,
          rotate: 30
        }
      },
      yAxis: {
        type: 'value',
        name: '数量',
        minInterval: 1
      },
      series: [{
        name: '车辆数量',
        type: 'bar',
        data: counts.map((value, index) => ({
          value: value,
          itemStyle: {
            color: colors[index]
          }
        })),
        label: {
          show: true,
          position: 'top',
          formatter: '{c}'
        }
      }]
    };
  }
  
  chart.setOption(option);
};

// 监听窗口大小变化
window.addEventListener('resize', () => {
  if (chart) {
    chart.resize();
  }
});

// 监听数据变化
watch(() => props.data, () => {
  if (chart) {
    updateChart();
  }
}, { deep: true });

// 监听图表类型变化
watch(() => props.chartType, () => {
  if (chart) {
    updateChart();
  }
});

// 监听加载状态
watch(() => props.loading, (newVal) => {
  if (!newVal && chart) {
    updateChart();
  }
});

onMounted(() => {
  initChart();
});

// 在组件卸载时销毁图表
onUnmounted(() => {
  if (chart) {
    chart.dispose();
    chart = null;
  }
  window.removeEventListener('resize', () => {
    if (chart) {
      chart.resize();
    }
  });
});
</script>

<style scoped>
.vehicle-chart {
  width: 100%;
  height: 100%;
}

.chart-container {
  width: 100%;
  height: 100%;
}
</style> 