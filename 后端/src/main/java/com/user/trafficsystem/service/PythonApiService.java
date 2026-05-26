package com.user.trafficsystem.service;

import java.util.Map;

/**
 * Python API服务接口，用于与Python API通信
 */
public interface PythonApiService {
    
    /**
     * 从Python API获取历史记录
     * @param limit 每页记录数
     * @param skip 跳过的记录数
     * @param token 用户认证令牌
     * @return 历史记录数据
     */
    Map<String, Object> getHistory(int limit, int skip, String token);
    
    /**
     * 从Python API获取历史统计数据
     * @param token 用户认证令牌
     * @return 统计数据
     */
    Map<String, Object> getHistoryStats(String token);
    
    /**
     * 将分析结果保存到Python API
     * @param analysisData 分析结果数据
     * @param token 用户认证令牌
     * @return 保存结果
     */
    Map<String, Object> saveAnalysisResult(Map<String, Object> analysisData, String token);
    
    /**
     * 检查Python API状态
     * @return API状态信息
     */
    Map<String, Object> checkApiStatus();
} 