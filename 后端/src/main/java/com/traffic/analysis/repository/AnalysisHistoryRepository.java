package com.traffic.analysis.repository;

import com.traffic.analysis.entity.AnalysisHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 分析历史记录数据访问接口
 */
@Repository
public interface AnalysisHistoryRepository extends MongoRepository<AnalysisHistory, String> {
    
    /**
     * 根据用户ID查询历史记录（按创建时间降序排序）
     * @param userId 用户ID
     * @return 历史记录列表
     */
    List<AnalysisHistory> findByUserIdOrderByCreateTimeDesc(String userId);
    
    /**
     * 根据用户ID查询历史记录（按时间戳降序排序）
     * @param userId 用户ID
     * @return 历史记录列表
     */
    List<AnalysisHistory> findByUserIdOrderByTimestampDesc(String userId);
    
    /**
     * 获取最近的10条历史记录（按创建时间降序排序）
     * @return 历史记录列表
     */
    List<AnalysisHistory> findTop10ByOrderByCreateTimeDesc();

    /**
     * 添加查询方法，按照不同ID类型查找记录
     * @param historyId 历史记录ID字符串
     * @return 找到的历史记录
     */
    List<AnalysisHistory> findByHistoryId(String historyId);

    /**
     * 按照关联ID查找记录
     * @param relatedId 关联的ID
     * @return 找到的历史记录
     */
    List<AnalysisHistory> findByRelatedId(String relatedId);
} 