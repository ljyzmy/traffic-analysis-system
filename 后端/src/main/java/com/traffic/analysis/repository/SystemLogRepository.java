package com.traffic.analysis.repository;

import com.traffic.analysis.entity.SystemLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统日志数据仓库
 */
@Repository
public interface SystemLogRepository extends MongoRepository<SystemLog, String> {
    
    /**
     * 根据日志级别查询
     * @param level 日志级别
     * @return 日志列表
     */
    List<SystemLog> findByLevel(String level);
    
    /**
     * 根据操作类型查询
     * @param operationType 操作类型
     * @return 日志列表
     */
    List<SystemLog> findByOperationType(String operationType);
    
    /**
     * 查询特定时间范围内的日志
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 日志列表
     */
    List<SystemLog> findByTimestampBetween(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 查询最近的日志记录
     * @return 最近的日志记录列表
     */
    List<SystemLog> findTop20ByOrderByTimestampDesc();
    
    /**
     * 查询特定服务的日志
     * @param serviceName 服务名称
     * @return 日志列表
     */
    List<SystemLog> findByServiceName(String serviceName);
    
    /**
     * 查询特定操作者的日志
     * @param operatorId 操作者ID
     * @return 日志列表
     */
    List<SystemLog> findByOperatorId(String operatorId);
} 