package com.traffic.analysis.service;

import com.traffic.analysis.entity.SystemLog;

import java.util.List;
import java.util.Map;

/**
 * 系统服务接口
 */
public interface SystemService {
    
    /**
     * 获取系统状态
     * @return 系统状态信息
     */
    Map<String, Object> getSystemStatus();
    
    /**
     * 启动模型服务
     * @param userId 用户ID
     * @param username 用户名
     * @return 操作结果
     */
    boolean startModelService(String userId, String username);
    
    /**
     * 停止模型服务
     * @param userId 用户ID
     * @param username 用户名
     * @return 操作结果
     */
    boolean stopModelService(String userId, String username);
    
    /**
     * 启动数据库服务
     * @param userId 用户ID
     * @param username 用户名
     * @return 操作结果
     */
    boolean startDatabaseService(String userId, String username);
    
    /**
     * 停止数据库服务
     * @param userId 用户ID
     * @param username 用户名
     * @return 操作结果
     */
    boolean stopDatabaseService(String userId, String username);
    
    /**
     * 测试模型连接
     * @param userId 用户ID
     * @param username 用户名
     * @return 测试结果
     */
    Map<String, Object> testModelConnection(String userId, String username);
    
    /**
     * 测试数据库连接
     * @param userId 用户ID
     * @param username 用户名
     * @return 测试结果
     */
    Map<String, Object> testDatabaseConnection(String userId, String username);
    
    /**
     * 获取系统日志
     * @param limit 限制条数
     * @return 日志列表
     */
    List<SystemLog> getSystemLogs(int limit);
    
    /**
     * 添加系统日志
     * @param log 日志对象
     * @return 保存的日志对象
     */
    SystemLog addSystemLog(SystemLog log);
    
    /**
     * 清除系统日志
     * @param userId 用户ID
     * @param username 用户名
     * @return 清除结果，true表示成功
     */
    boolean clearSystemLogs(String userId, String username);
} 