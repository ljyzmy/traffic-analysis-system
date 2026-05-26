<template>
  <div class="vehicle-stats-card">
    <el-card class="stats-card">
      <template #header>
        <div class="card-header">
          <span>车辆类型统计</span>
          <el-button type="text" @click="exportStats">
            <el-icon><download /></el-icon>
          </el-button>
        </div>
      </template>
      
      <div class="stats-content">
        <div class="vehicle-icons">
          <div class="vehicle-type" v-for="(count, type) in vehicleTypeStats" :key="type">
            <div class="vehicle-icon-container">
              <div class="vehicle-icon">{{ getVehicleIcon(type) }}</div>
              <div class="vehicle-count">{{ count }}</div>
            </div>
            <div class="vehicle-label">{{ getVehicleTypeName(type) }}</div>
            <div class="vehicle-percentage">
              {{ getPercentage(count) }}%
            </div>
            <el-progress 
              :percentage="getPercentage(count)" 
              :color="getVehicleColor(type)"
              :show-text="false"
              :stroke-width="8"
            />
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { computed } from 'vue';
import { ElMessage } from 'element-plus';
import { Download } from '@element-plus/icons-vue';

export default {
  name: 'VehicleStatsCard',
  components: {
    Download
  },
  props: {
    // 车辆类型统计数据
    vehicleTypeStats: {
      type: Object,
      default: () => ({})
    }
  },
  setup(props) {
    // 计算总车辆数
    const totalVehicles = computed(() => {
      let total = 0;
      Object.values(props.vehicleTypeStats).forEach(count => {
        total += count;
      });
      return total;
    });
    
    // 获取车辆类型名称
    const getVehicleTypeName = (type) => {
      const typeNames = {
        car: '小汽车',
        motorcycle: '摩托车',
        truck: '卡车',
        bus: '公交车',
        bicycle: '自行车'
      };
      
      return typeNames[type] || type;
    };
    
    // 获取车辆图标
    const getVehicleIcon = (type) => {
      const icons = {
        car: '🚗',
        motorcycle: '🏍️',
        truck: '🚚',
        bus: '🚌',
        bicycle: '🚲'
      };
      
      return icons[type] || '🚗';
    };
    
    // 获取车辆颜色
    const getVehicleColor = (type) => {
      const colors = {
        car: '#4f46e5',
        motorcycle: '#ef4444',
        truck: '#f59e0b',
        bus: '#10b981',
        bicycle: '#3b82f6'
      };
      
      return colors[type] || '#4f46e5';
    };
    
    // 计算百分比
    const getPercentage = (count) => {
      if (totalVehicles.value === 0) return 0;
      return Math.round((count / totalVehicles.value) * 100);
    };
    
    // 导出统计数据
    const exportStats = () => {
      try {
        const data = [];
        
        // 添加表头
        data.push(['车辆类型', '数量', '百分比']);
        
        // 添加数据行
        Object.entries(props.vehicleTypeStats).forEach(([type, count]) => {
          data.push([
            getVehicleTypeName(type),
            count,
            `${getPercentage(count)}%`
          ]);
        });
        
        // 添加总计行
        data.push(['总计', totalVehicles.value, '100%']);
        
        // 生成CSV内容
        const csvContent = data.map(row => row.join(',')).join('\n');
        
        // 创建Blob对象
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        
        // 创建下载链接
        const link = document.createElement('a');
        const url = URL.createObjectURL(blob);
        
        link.setAttribute('href', url);
        link.setAttribute('download', '车辆类型统计.csv');
        link.style.visibility = 'hidden';
        
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        
        ElMessage.success('统计数据已导出');
      } catch (err) {
        console.error('导出统计数据失败:', err);
        ElMessage.error('导出统计数据失败');
      }
    };
    
    return {
      totalVehicles,
      getVehicleTypeName,
      getVehicleIcon,
      getVehicleColor,
      getPercentage,
      exportStats
    };
  }
};
</script>

<style scoped>
.vehicle-stats-card {
  margin: 20px 0;
}

.stats-card {
  background: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.06) !important;
}

:deep(.stats-card .el-card__header) {
  background-color: rgba(26, 32, 50, 0.8);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.card-header span {
  font-weight: bold;
  color: #ffffff;
}

.stats-content {
  padding: 15px 0;
}

.vehicle-icons {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 20px;
}

.vehicle-type {
  margin-bottom: 15px;
}

.vehicle-icon-container {
  display: flex;
  align-items: center;
  margin-bottom: 5px;
}

.vehicle-icon {
  font-size: 24px;
  margin-right: 10px;
}

.vehicle-count {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
}

.vehicle-label {
  font-size: 14px;
  color: #d1d5db;
  margin-bottom: 5px;
}

.vehicle-percentage {
  font-size: 12px;
  color: #9ca3af;
  margin-bottom: 5px;
  text-align: right;
}
</style> 