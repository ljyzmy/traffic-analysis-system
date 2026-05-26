import apiClient, { createApiUrl } from '@/utils/http-common';

// 获取MongoDB文档的正确ID
function getDocumentId(item) {
  // 检查是否有MongoDB ObjectId
  if (item && item._id) {
    // 如果_id是对象且包含$oid属性（标准MongoDB扩展JSON格式）
    if (typeof item._id === 'object' && item._id.$oid) {
      return item._id.$oid;
    }
    
    // 如果_id直接是字符串形式的ObjectId
    if (typeof item._id === 'string' && item._id.match(/^[0-9a-f]{24}$/)) {
      return item._id;
    }
    
    // 如果_id是ObjectId对象（可能包含timestamp等属性）
    if (typeof item._id === 'object' && item._id.toString) {
      // 尝试获取ObjectId的字符串表示
      return item._id.toString().replace(/^ObjectId\(['"](.+)['"]\)$/, '$1');
    }
  }
  
  // 尝试获取analysis_result中的id
  if (item && item.analysis_result && item.analysis_result.id) {
    return item.analysis_result.id;
  }
  
  // 最后尝试其他可能的ID字段
  return (item && item.id) ? item.id : null;
}

class AnalysisService {
  // 上传图片进行分析
  uploadImage(formData) {
    return apiClient.post(createApiUrl('analysis/analyze'), formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
  }

  // 获取分析结果
  getAnalysisResult(id) {
    return apiClient.get(createApiUrl(`analysis/status/${id}`));
  }

  // 获取历史记录
  getHistory(params = {}) {
    console.log(`请求历史记录:`, params);
    
    // 构建URL和查询参数
    const baseUrl = 'history/list';
    
    // 构建查询参数
    const queryParams = new URLSearchParams();
    
    // 如果params是数字，视为limit参数
    if (typeof params === 'number') {
      queryParams.append('limit', params);
    } 
    // 如果params是对象，处理各种可能的参数
    else if (typeof params === 'object') {
      // 分页参数
      if (params.limit) queryParams.append('limit', params.limit);
      if (params.skip !== undefined) queryParams.append('skip', params.skip);
      
      // 用户筛选参数
      if (params.userId) queryParams.append('userId', params.userId);
      if (params.user_id) queryParams.append('user_id', params.user_id);
      if (params.username) queryParams.append('username', params.username);
      
      // 日期筛选参数
      if (params.startDate) queryParams.append('startDate', params.startDate);
      if (params.endDate) queryParams.append('endDate', params.endDate);
      
      // 记录类型
      if (params.type) queryParams.append('type', params.type);
    }
    
    // 添加随机时间戳防止缓存
    queryParams.append('t', new Date().getTime());
    
    // 构建完整URL
    const url = `${baseUrl}?${queryParams.toString()}`;
    
    // 调试完整URL
    const apiUrl = createApiUrl(url);
    console.log(`完整API请求路径: ${apiUrl}`);
    
    return apiClient.get(apiUrl)
      .then(response => {
        // 对响应进行数据格式处理
        console.log('历史记录原始响应状态:', response.status);
        
        // 标准化响应格式
        if (response.data) {
          // 如果响应是标准axios格式，返回data部分
          return response.data;
        }
        return response; // 如果直接就是数据，则返回数据本身
      });
  }
  
  // 获取图像分析历史记录
  getImageHistory(params = {}) {
    console.log('请求图像分析历史记录');
    return this.getHistory({
      ...params,
      type: 'image'
    });
  }
  
  // 获取视频分析历史记录
  getVideoHistory(params = {}) {
    console.log('请求视频分析历史记录');
    return this.getHistory({
      ...params,
      type: 'video'
    });
  }

  // 删除历史记录
  deleteHistory(item, type = 'image') {
    // 获取正确格式的ID
    let recordId = null;
    
    // 如果直接传入字符串ID
    if (typeof item === 'string') {
      recordId = item;
    } 
    // 如果传入对象（记录或ID对象）
    else if (typeof item === 'object' && item !== null) {
      // 优先使用analysis_result.id (这是正确的MongoDB ObjectId)
      if (item.analysis_result && item.analysis_result.id) {
        recordId = item.analysis_result.id;
      }
      // 其次使用analysisResult.id
      else if (item.analysisResult && item.analysisResult.id) {
        recordId = item.analysisResult.id;
      }
      // 直接使用记录中的_id
      else if (item._id) {
        if (typeof item._id === 'string') {
          recordId = item._id;
        } 
        // 处理MongoDB扩展格式
        else if (typeof item._id === 'object' && item._id.$oid) {
          recordId = item._id.$oid;
        }
        // 处理当前格式：包含timestamp的对象
        else if (typeof item._id === 'object' && item._id.timestamp) {
          recordId = item._id.timestamp.toString();
        }
      }
      // 尝试使用其他ID字段
      else if (item.id) {
        recordId = item.id;
      } else if (item.result_id) {
        recordId = item.result_id;
      }
    }
    
    if (!recordId) {
      console.error('无法获取有效的记录ID');
      return Promise.reject(new Error('无法获取有效的记录ID'));
    }
    
    console.log('删除历史记录: id=' + recordId + ', type=' + type);
    
    // 尝试获取用户信息
    let userId;
    try {
      const userInfo = JSON.parse(localStorage.getItem('user'));
      userId = userInfo?.id;
    } catch (error) {
      console.warn('获取用户ID失败:', error);
    }
    
    // 使用createApiUrl确保路径正确
    const url = createApiUrl(`history/${recordId}`);
    
    console.log(`完整删除API路径: ${url}`);
    
    // 构建参数对象
    const params = { type };
    if (userId) params.userId = userId;
    
    return apiClient.delete(url, { params })
      .then(response => {
        console.log('删除历史记录响应状态:', response.status);
        
        // 标准化响应格式
        if (response.data) {
          return response.data;
        }
        return response;
      })
      .catch(error => {
        console.error('删除历史记录请求失败:', error);
        throw error;
      });
  }
  
  // 删除图像分析历史记录
  deleteImageHistory(id) {
    console.log('删除图像分析历史记录');
    return this.deleteHistory(id, 'image');
  }
  
  // 删除视频分析历史记录
  deleteVideoHistory(id) {
    console.log('删除视频分析历史记录');
    return this.deleteHistory(id, 'video');
  }
}

export default new AnalysisService(); 