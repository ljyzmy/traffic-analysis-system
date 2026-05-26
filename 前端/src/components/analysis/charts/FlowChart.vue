<template>
  <div class="flow-chart">
    <div class="chart-container" ref="chartRef"></div>
  </div>
</template>

<script>
import { ref, onMounted, onUnmounted, watch } from 'vue';
import * as echarts from 'echarts';

export default {
  name: 'FlowChart',
  props: {
    data: {
      type: [Object, Array],
      required: true
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  setup(props) {
    const chartRef = ref(null);
    let chart = null;
    
    // 初始化图表
    const initChart = () => {
      if (chartRef.value) {
        // 添加延迟，确保DOM已经渲染完成
        setTimeout(() => {
          if (chartRef.value && chartRef.value.clientWidth > 0 && chartRef.value.clientHeight > 0) {
            // 如果已经有图表实例，先销毁
            if (chart) {
              chart.dispose();
            }
            
            chart = echarts.init(chartRef.value);
            updateChart();
            window.addEventListener('resize', handleResize);
            
            // 添加自动重绘功能
            const resizeObserver = new ResizeObserver(() => {
              chart && chart.resize();
            });
            resizeObserver.observe(chartRef.value);
            
            console.log('FlowChart初始化成功，容器尺寸:', chartRef.value.clientWidth, 'x', chartRef.value.clientHeight);
          } else {
            console.warn('图表容器尺寸为0，无法初始化图表');
            // 再次尝试初始化，可能是标签页刚切换导致的尺寸问题
            setTimeout(() => {
              if (chartRef.value && chartRef.value.clientWidth > 0 && chartRef.value.clientHeight > 0) {
                // 如果已经有图表实例，先销毁
                if (chart) {
                  chart.dispose();
                }
                
                chart = echarts.init(chartRef.value);
                updateChart();
                window.addEventListener('resize', handleResize);
                
                // 添加自动重绘功能
                const resizeObserver = new ResizeObserver(() => {
                  chart && chart.resize();
                });
                resizeObserver.observe(chartRef.value);
                
                console.log('FlowChart延迟初始化成功，容器尺寸:', chartRef.value.clientWidth, 'x', chartRef.value.clientHeight);
              } else {
                console.error('图表容器尺寸仍为0，放弃初始化');
              }
            }, 800); // 从500ms增加到800ms，给更多时间让标签页完成切换
          }
        }, 500); // 从300ms增加到500ms
      }
    };
    
    // 处理窗口大小变化
    const handleResize = () => {
      chart && chart.resize();
    };
    
    // 获取流量状态
    const getFlowStatus = (value) => {
      if (value < 10) return '畅通';
      if (value < 20) return '正常';
      if (value < 30) return '较拥挤';
      return '拥挤';
    };
    
    // 更新图表数据
    const updateChart = () => {
      if (!chart || !props.data) return;
      
      // 处理不同的数据格式
      let xAxisData = [];
      let seriesData = [];
      
      if (Array.isArray(props.data)) {
        // 如果是数组格式
        if (props.data.length > 0) {
          if (props.data[0].timestamp) {
            // 时间戳数组
            xAxisData = props.data.map(item => item.timestamp);
            seriesData = props.data.map(item => item.flow || 0);
          } else if (props.data[0].class) {
            // 检测结果数组
            // 将检测数据转换为流量数据
            const now = new Date();
            xAxisData = [now.toLocaleTimeString()];
            seriesData = [props.data.length]; // 使用检测数量作为流量
          }
        }
      } else if (props.data.times && props.data.flows) {
        // 标准格式
        xAxisData = props.data.times;
        seriesData = props.data.flows;
      }
      
      // 如果没有数据，添加默认值
      if (xAxisData.length === 0) {
        const now = new Date();
        xAxisData = [now.toLocaleTimeString()];
        seriesData = [0];
      }
      
      // 确定标准线及颜色区域
      const markLines = [
        {
          name: '畅通/正常临界值',
          yAxis: 10,
          lineStyle: {
            color: '#67C23A',
            type: 'dashed'
          },
          label: {
            formatter: '畅通/正常',
            position: 'end'
          }
        },
        {
          name: '正常/较拥挤临界值',
          yAxis: 20,
          lineStyle: {
            color: '#E6A23C',
            type: 'dashed'
          },
          label: {
            formatter: '正常/较拥挤',
            position: 'end'
          }
        },
        {
          name: '较拥挤/拥挤临界值',
          yAxis: 30,
          lineStyle: {
            color: '#F56C6C',
            type: 'dashed'
          },
          label: {
            formatter: '较拥挤/拥挤',
            position: 'end'
          }
        }
      ];
      
      // 生成图表配置
      const option = {
        title: {
          text: '交通流量变化',
          left: 'center'
        },
        tooltip: {
          trigger: 'axis',
          axisPointer: {
            type: 'shadow'
          },
          formatter: function(params) {
            const value = params[0].value;
            const status = getFlowStatus(value);
            return `${params[0].name}<br/>${params[0].seriesName}: ${value} 辆/小时<br/>状态: ${status}`;
          }
        },
        xAxis: {
          type: 'category',
          data: xAxisData,
          axisLabel: {
            rotate: 45
          }
        },
        yAxis: {
          type: 'value',
          name: '流量(辆/小时)',
          axisLine: {
            show: true
          },
          splitLine: {
            show: true
          }
        },
        visualMap: {
          show: false,
          dimension: 1,
          pieces: [
            {min: 0, max: 10, color: '#67C23A'},  // 畅通 - 绿色
            {min: 10, max: 20, color: '#409EFF'}, // 正常 - 蓝色
            {min: 20, max: 30, color: '#E6A23C'}, // 较拥挤 - 黄色
            {min: 30, max: Infinity, color: '#F56C6C'} // 拥挤 - 红色
          ]
        },
        series: [
          {
            name: '交通流量',
            type: 'bar',
            data: seriesData,
            itemStyle: {
              // 使用visualMap配置的颜色
            },
            markLine: {
              silent: true,
              data: markLines
            }
          }
        ]
      };
      
      chart.setOption(option);
    };
    
    // 监听数据变化
    watch(() => props.data, () => {
      updateChart();
    }, { deep: true });
    
    // 组件挂载时初始化图表
    onMounted(() => {
      initChart();
      updateChart();
    });
    
    // 组件卸载时清理
    onUnmounted(() => {
      if (chart) {
        chart.dispose();
        window.removeEventListener('resize', handleResize);
      }
    });
    
    return {
      chartRef
    };
  }
};
</script>

<style scoped>
.flow-chart {
  width: 100%;
  height: 100%;
}

.chart-container {
  width: 100%;
  height: 100%;
}
</style> 