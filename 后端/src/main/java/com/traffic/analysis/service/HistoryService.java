package com.traffic.analysis.service;

import com.traffic.analysis.entity.AnalysisHistory;
import com.traffic.analysis.model.DetectionResult;

import java.util.List;
import java.util.Map;

/**
 * 历史记录服务接口
 */
public interface HistoryService {
    
    /**
     * 获取用户历史记录
     * @param userId 用户ID
     * @return 历史记录列表
     */
    List<AnalysisHistory> getUserHistory(String userId);
    
    /**
     * 保存历史记录
     * @param history 历史记录对象
     * @return 保存后的历史记录
     */
    AnalysisHistory saveHistory(AnalysisHistory history);
    
    /**
     * 从检测结果创建历史记录
     * @param result 检测结果
     * @param userId 用户ID
     * @param fileName 文件名
     * @return 创建的历史记录
     */
    AnalysisHistory createFromDetectionResult(DetectionResult result, String userId, String fileName);
    
    /**
     * 通过用户ID查找历史记录
     * @param userId 用户ID
     * @return 历史记录列表
     */
    List<AnalysisHistory> findByUserId(String userId);
    
    /**
     * 通过ID查找历史记录
     * @param id 历史记录ID
     * @return 历史记录
     */
    AnalysisHistory findById(String id);
    
    /**
     * 通过ID删除历史记录
     * @param id 历史记录ID
     */
    void deleteById(String id);
    
    /**
     * 获取最新历史记录
     * @param limit 限制数量
     * @return 历史记录列表
     */
    List<AnalysisHistory> findLatest(int limit);
    
    /**
     * 删除历史记录
     * @param id 历史记录ID
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteHistory(String id, String userId);
    
    /**
     * 批量删除历史记录
     * @param ids 历史记录ID列表
     * @param type 记录类型
     * @param userId 用户ID，用于权限验证
     * @return 已删除的ID列表
     */
    List<String> batchDelete(List<String> ids, String type, String userId);
    
    /**
     * 获取用户历史记录，支持分页
     * @param userId 用户ID
     * @param limit 每页记录数
     * @param skip 跳过记录数
     * @return 历史记录分页结果
     */
    Map<String, Object> getUserHistory(String userId, Integer limit, Integer skip);
    
    /**
     * 获取用户历史记录，支持分页和搜索查询
     * @param userId 用户ID
     * @param limit 每页记录数
     * @param skip 跳过记录数
     * @param query 搜索关键词
     * @return 历史记录分页结果
     */
    Map<String, Object> getUserHistory(String userId, Integer limit, Integer skip, String query);
    
    /**
     * 获取用户的特定类型历史记录
     * @param userId 用户ID
     * @param type 记录类型
     * @return 历史记录列表
     */
    List<AnalysisHistory> getUserHistoryByType(String userId, String type);
    
    /**
     * 获取用户历史记录，支持分页和类型过滤
     * @param userId 用户ID
     * @param type 记录类型
     * @param limit 每页记录数
     * @param skip 跳过记录数
     * @return 历史记录分页结果
     */
    Map<String, Object> getUserHistoryByType(String userId, String type, Integer limit, Integer skip);
    
    /**
     * 获取用户历史记录，支持分页、类型过滤和搜索查询
     * @param userId 用户ID
     * @param type 记录类型
     * @param limit 每页记录数
     * @param skip 跳过记录数
     * @param query 搜索关键词
     * @return 历史记录分页结果
     */
    Map<String, Object> getUserHistoryByType(String userId, String type, Integer limit, Integer skip, String query);
    
    /**
     * 更新视频处理时间
     * @param taskId 视频任务ID
     * @param processingTime 处理时间（秒）
     * @return 是否更新成功
     */
    boolean updateVideoProcessingTime(String taskId, double processingTime);
} 