<template>
  <div class="result-display">
    <el-tabs v-model="activeTab" type="card">
      <el-tab-pane label="车辆统计" name="vehicles">
        <div class="chart-container">
          <vehicle-chart 
            v-if="analysisData && (analysisData.vehicles || analysisData.vehicleCount)" 
            :data="analysisData.vehicles || {count: analysisData.vehicleCount}" 
            :loading="loading"
          />
          <el-empty v-else description="暂无数据" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="交通流量" name="flow">
        <div class="chart-container">
          <flow-chart 
            v-if="analysisData && (analysisData.flow || analysisData.detections)" 
            :data="analysisData.flow || analysisData.detections || []" 
            :loading="loading"
          />
          <el-empty v-else description="暂无数据" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="交通密度" name="density">
        <div class="chart-container">
          <density-chart 
            v-if="analysisData && (analysisData.density || analysisData.detections)" 
            :data="analysisData.density || analysisData.detections || []" 
            :loading="loading"
          />
          <el-empty v-else description="暂无数据" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="原始数据" name="raw">
        <div class="data-table">
          <el-table
            v-loading="loading"
            :data="rawTableData"
            style="width: 100%"
            border
            stripe
            height="400"
          >
            <el-table-column
              v-for="(col, index) in tableColumns"
              :key="index"
              :prop="col.prop"
              :label="col.label"
              :width="col.width"
            />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>
    
    <div class="actions">
      <el-button type="primary" @click="refreshData">刷新数据</el-button>
      <el-button type="success" @click="exportData">导出数据</el-button>
      <el-button @click="goBack">返回</el-button>
    </div>
  </div>
</template>

<script>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAnalysisResult, getTrafficData } from '@/api/traffic'
import VehicleChart from './charts/VehicleChart.vue'
import FlowChart from './charts/FlowChart.vue'
import DensityChart from './charts/DensityChart.vue'

export default {
  name: 'ResultDisplay',
  components: {
    VehicleChart,
    FlowChart,
    DensityChart
  },
  setup() {
    const route = useRoute()
    const router = useRouter()
    const loading = ref(false)
    const activeTab = ref('vehicles')
    const analysisData = ref(null)
    const analysisId = computed(() => route.params.id)
    
    // 表格列定义
    const tableColumns = ref([
      { prop: 'timestamp', label: '时间点', width: '180' },
      { prop: 'cars', label: '小型车', width: '100' },
      { prop: 'trucks', label: '卡车', width: '100' },
      { prop: 'buses', label: '公交车', width: '100' },
      { prop: 'motorcycles', label: '摩托车', width: '100' },
      { prop: 'bicycles', label: '自行车', width: '100' },
      { prop: 'pedestrians', label: '行人', width: '100' },
      { prop: 'flow', label: '流量(辆/小时)', width: '140' },
      { prop: 'density', label: '密度(辆/公里)', width: '140' },
      { prop: 'avgSpeed', label: '平均速度(公里/小时)', width: '160' }
    ])
    
    // 原始表格数据
    const rawTableData = computed(() => {
      if (!analysisData.value) {
        return []
      }
      
      // 处理不同的数据格式
      if (analysisData.value.raw) {
        return analysisData.value.raw
      }
      
      // 如果有detections但没有raw
      if (analysisData.value.detections && Array.isArray(analysisData.value.detections)) {
        // 将detections转换为表格数据格式
        return analysisData.value.detections.map(detection => ({
          timestamp: new Date().toLocaleString(),
          cars: detection.class === 'car' ? 1 : 0,
          trucks: detection.class === 'truck' ? 1 : 0,
          buses: detection.class === 'bus' ? 1 : 0,
          motorcycles: detection.class === 'motorcycle' ? 1 : 0,
          bicycles: detection.class === 'bicycle' ? 1 : 0,
          pedestrians: detection.class === 'person' ? 1 : 0,
          flow: 0,
          density: 0,
          avgSpeed: 0
        }))
      }
      
      // 如果只有vehicleCount
      if (analysisData.value.vehicleCount !== undefined || 
          (analysisData.value.vehicles && analysisData.value.vehicles.count !== undefined)) {
        const count = analysisData.value.vehicleCount || 
                      (analysisData.value.vehicles ? analysisData.value.vehicles.count : 0)
        
        // 创建一个简单的表格行
        return [{
          timestamp: new Date().toLocaleString(),
          cars: count,
          trucks: 0,
          buses: 0,
          motorcycles: 0,
          bicycles: 0,
          pedestrians: 0,
          flow: 0,
          density: 0,
          avgSpeed: 0
        }]
      }
      
      return []
    })
    
    // 加载分析数据
    const loadAnalysisData = async () => {
      if (!analysisId.value) {
        ElMessage.error('分析ID不存在')
        return
      }
      
      loading.value = true
      try {
        // 获取分析状态
        const statusRes = await getAnalysisResult(analysisId.value)
        console.log('分析结果状态:', statusRes)
        
        // 处理不同的响应格式
        let status = 'unknown'
        let hasBasicData = false
        
        // 检查是否返回了HTML (可能是重定向到登录页面)
        if (typeof statusRes === 'string' && statusRes.includes('<!DOCTYPE html>')) {
          console.error('状态请求接收到HTML响应，可能是认证问题导致重定向到登录页面')
          ElMessage.error('获取分析状态失败，认证问题')
          loading.value = false
          router.push('/login')
          return
        }
        
        // 直接判断状态响应中是否已经包含足够的数据来渲染页面
        if (typeof statusRes === 'object') {
          // 如果状态响应包含vehicleCount或detections，说明已经有了足够的分析数据
          // 可以直接使用它，无需再次请求详细数据
          if (statusRes.vehicleCount !== undefined || 
              (statusRes.detections && statusRes.detections.length > 0) ||
              statusRes.imageUrl) {
            
            console.log('状态响应已包含足够分析数据，直接使用')
            analysisData.value = {
              vehicles: {
                count: statusRes.vehicleCount || 0,
                image: statusRes.imageUrl || statusRes.resultImage || ''
              },
              detections: statusRes.detections || []
            }
            loading.value = false
            return
          }
          
          // 从嵌套的data结构中检查
          if (statusRes.data && (
              statusRes.data.vehicleCount !== undefined || 
              (statusRes.data.detections && statusRes.data.detections.length > 0) ||
              statusRes.data.imageUrl
          )) {
            console.log('状态响应data中包含足够分析数据，直接使用')
            analysisData.value = {
              vehicles: {
                count: statusRes.data.vehicleCount || 0,
                image: statusRes.data.imageUrl || statusRes.data.resultImage || ''
              },
              detections: statusRes.data.detections || []
            }
            loading.value = false
            return
          }
        }
        
        if (statusRes.data && statusRes.data.status) {
          status = statusRes.data.status
        } else if (statusRes.status) {
          status = statusRes.status
        } else if (typeof statusRes === 'object' && 'success' in statusRes) {
          // 处理直接返回成功状态的情况
          status = statusRes.success ? 'success' : 'failed'
        } else if (typeof statusRes === 'object' && statusRes.vehicleCount !== undefined) {
          // 有车辆数据，认为分析已完成
          status = 'success'
          // 可以直接使用此数据
          analysisData.value = {
            vehicles: {
              count: statusRes.vehicleCount,
              image: statusRes.imageUrl || statusRes.resultImage
            },
            detections: statusRes.detections || []
          }
          hasBasicData = true
        }
        
        if (status !== 'success' && !analysisData.value) {
          ElMessage.warning(`分析任务状态: ${status}`)
          // 如果不是成功状态，5秒后重试
          if (status === 'processing' || status === 'queued') {
            setTimeout(loadAnalysisData, 5000)
            return
          }
        }
        
        // 如果已经有数据了（从状态请求中获取），不需要再请求
        if (hasBasicData) {
          loading.value = false
          return
        }
        
        // 以下代码尝试获取更详细的分析数据
        // 由于我们可能会收到登录页面重定向，所以这部分功能可能不可靠
        // 建议尽可能使用上面从状态响应中获取的数据
        try {
          // 在请求数据前确保有可用的token
          const token = localStorage.getItem('auth_token')
          if (!token) {
            ElMessage.error('未检测到认证令牌，无法获取分析数据')
            router.push('/login')
            return
          }
          
          // 获取分析数据
          const dataRes = await getTrafficData({ id: analysisId.value })
          console.log('分析数据响应:', dataRes)
          
          // 检查是否返回了HTML (可能是重定向到登录页面)
          if (typeof dataRes === 'string' && dataRes.includes('<!DOCTYPE html>')) {
            console.error('接收到HTML响应，可能是认证问题导致重定向到登录页面')
            
            // 我们已经从状态请求中获取了基本数据，所以可以直接使用它
            if (statusRes && typeof statusRes === 'object') {
              analysisData.value = {
                vehicles: {
                  count: statusRes.vehicleCount || 0,
                  image: statusRes.imageUrl || statusRes.resultImage || ''
                },
                detections: statusRes.detections || []
              }
              ElMessage.warning('使用基本分析结果，详细数据获取失败')
              loading.value = false
              return
            } else {
              ElMessage.error('认证失败，请重新登录')
              // 不要清除认证令牌，只提示重新登录
              // localStorage.removeItem('auth_token')
              router.push('/login')
              return
            }
          }
          
          // 处理不同的响应格式
          if (dataRes.data) {
            analysisData.value = dataRes.data
          } else if (typeof dataRes === 'object' && (dataRes.vehicles || dataRes.detections)) {
            // 如果直接返回数据对象
            analysisData.value = dataRes
          } else if (Array.isArray(dataRes)) {
            // 如果直接返回数组数据
            analysisData.value = {
              raw: dataRes,
              vehicles: dataRes,
              flow: dataRes,
              density: dataRes
            }
          } else {
            // 尝试从状态响应中构建基本数据
            if (statusRes && (typeof statusRes === 'object')) {
              analysisData.value = {
                vehicles: {
                  count: statusRes.vehicleCount || 0,
                  image: statusRes.imageUrl || statusRes.resultImage || ''
                },
                detections: statusRes.detections || []
              }
              ElMessage.warning('使用基本分析结果，完整数据不可用')
            } else {
              ElMessage.warning('响应中没有识别的数据格式')
            }
          }
        } catch (dataError) {
          console.error('获取分析数据失败:', dataError)
          
          // 尝试使用状态响应中的数据（如果有）
          if (statusRes && (typeof statusRes === 'object')) {
            analysisData.value = {
              vehicles: {
                count: statusRes.vehicleCount || 0,
                image: statusRes.imageUrl || statusRes.resultImage || ''
              },
              detections: statusRes.detections || []
            }
            ElMessage.warning('使用基本分析结果，详细数据获取失败')
          } else {
            ElMessage.error('无法获取分析数据: ' + (dataError.message || '未知错误'))
          }
        }
      } catch (error) {
        console.error('加载分析数据失败:', error)
        ElMessage.error(`加载分析数据失败: ${error.message || '未知错误'}`)
      } finally {
        loading.value = false
      }
    }
    
    // 刷新数据
    const refreshData = () => {
      loadAnalysisData()
    }
    
    // 导出数据
    const exportData = () => {
      if (!analysisData.value) {
        ElMessage.warning('暂无数据可导出')
        return
      }
      
      // 创建CSV内容
      let csv = 'data:text/csv;charset=utf-8,\uFEFF'
      
      // 添加表头
      const headerRow = tableColumns.value.map(col => col.label).join(',')
      csv += headerRow + '\r\n'
      
      // 添加数据行
      rawTableData.value.forEach(row => {
        const dataRow = tableColumns.value.map(col => row[col.prop]).join(',')
        csv += dataRow + '\r\n'
      })
      
      // 触发下载
      const encodedUri = encodeURI(csv)
      const link = document.createElement('a')
      link.setAttribute('href', encodedUri)
      link.setAttribute('download', `交通分析结果_${analysisId.value}.csv`)
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      
      ElMessage.success('数据导出成功')
    }
    
    // 返回上一页
    const goBack = () => {
      router.push('/upload')
    }
    
    // 组件挂载时加载数据
    onMounted(() => {
      loadAnalysisData()
    })
    
    // 监听分析ID变化
    watch(analysisId, () => {
      if (analysisId.value) {
        loadAnalysisData()
      }
    })
    
    return {
      activeTab,
      analysisData,
      loading,
      tableColumns,
      rawTableData,
      refreshData,
      exportData,
      goBack
    }
  }
}
</script>

<style scoped>
.result-display {
  width: 100%;
  padding: 20px;
}

.chart-container {
  height: 400px;
  margin-top: 20px;
}

.data-table {
  margin-top: 20px;
}

.actions {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style> 