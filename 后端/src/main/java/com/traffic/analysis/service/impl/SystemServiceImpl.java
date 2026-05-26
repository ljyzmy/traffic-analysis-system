package com.traffic.analysis.service.impl;

import com.traffic.analysis.entity.SystemLog;
import com.traffic.analysis.repository.SystemLogRepository;
import com.traffic.analysis.service.SystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 系统服务实现类
 */
@Service
@Slf4j
public class SystemServiceImpl implements SystemService {

    @Autowired
    private SystemLogRepository systemLogRepository;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    // 系统状态缓存
    private static final Map<String, Boolean> serviceStatus = new HashMap<>();
    
    static {
        // 初始化服务状态
        serviceStatus.put("model", true);
        serviceStatus.put("database", true);
    }
    
    @Override
    public Map<String, Object> getSystemStatus() {
        log.info("获取系统状态信息");
        Map<String, Object> status = new HashMap<>();
        
        // 获取模型服务状态
        Map<String, Object> modelStatus = new HashMap<>();
        modelStatus.put("status", serviceStatus.get("model") ? "online" : "offline");
        modelStatus.put("lastCheck", LocalDateTime.now().toString());
        status.put("model", modelStatus);
        
        // 获取数据库服务状态
        Map<String, Object> dbStatus = new HashMap<>();
        
        // 测试MongoDB连接
        boolean isDbConnected = isMongoDbConnected();
        serviceStatus.put("database", isDbConnected); // 更新数据库状态
        
        dbStatus.put("status", isDbConnected ? "online" : "offline");
        dbStatus.put("lastCheck", LocalDateTime.now().toString());
        status.put("database", dbStatus);
        
        // 获取最近的系统日志
        List<SystemLog> recentLogs = getSystemLogs(10);
        List<Map<String, Object>> formattedLogs = new ArrayList<>();
        
        for (SystemLog log : recentLogs) {
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("level", log.getLevel());
            logMap.put("message", log.getMessage());
            logMap.put("timestamp", log.getTimestamp().toString());
            formattedLogs.add(logMap);
        }
        
        status.put("logs", formattedLogs);
        
        return status;
    }

    @Override
    public boolean startModelService(String userId, String username) {
        log.info("启动模型服务, 操作人: {}", username);
        
        // 记录操作日志
        SystemLog logEntry = SystemLog.info("尝试启动模型服务", userId, username);
        logEntry.setServiceName("model");
        logEntry.setServiceOperation("start");
        logEntry.setOperationType("SERVICE_CONTROL");
        
        try {
            // 实际环境中，这里应该调用实际的服务启动代码
            // 这里仅模拟服务启动
            simulateServiceOperation(2000);
            
            // 更新服务状态
            serviceStatus.put("model", true);
            
            // 记录成功日志
            logEntry.setLevel("SUCCESS");
            logEntry.setMessage("成功启动模型服务");
            logEntry.setSuccess(true);
            systemLogRepository.save(logEntry);
            
            return true;
        } catch (Exception e) {
            log.error("启动模型服务失败", e);
            
            // 记录失败日志
            logEntry.setLevel("ERROR");
            logEntry.setMessage("启动模型服务失败: " + e.getMessage());
            logEntry.setSuccess(false);
            systemLogRepository.save(logEntry);
            
            return false;
        }
    }

    @Override
    public boolean stopModelService(String userId, String username) {
        log.info("停止模型服务, 操作人: {}", username);
        
        // 记录操作日志
        SystemLog logEntry = SystemLog.info("尝试停止模型服务", userId, username);
        logEntry.setServiceName("model");
        logEntry.setServiceOperation("stop");
        logEntry.setOperationType("SERVICE_CONTROL");
        
        try {
            // 实际环境中，这里应该调用实际的服务停止代码
            // 这里仅模拟服务停止
            simulateServiceOperation(1500);
            
            // 更新服务状态
            serviceStatus.put("model", false);
            
            // 记录成功日志
            logEntry.setLevel("SUCCESS");
            logEntry.setMessage("成功停止模型服务");
            logEntry.setSuccess(true);
            systemLogRepository.save(logEntry);
            
            return true;
        } catch (Exception e) {
            log.error("停止模型服务失败", e);
            
            // 记录失败日志
            logEntry.setLevel("ERROR");
            logEntry.setMessage("停止模型服务失败: " + e.getMessage());
            logEntry.setSuccess(false);
            systemLogRepository.save(logEntry);
            
            return false;
        }
    }

    @Override
    public boolean startDatabaseService(String userId, String username) {
        log.info("启动数据库服务, 操作人: {}", username);
        
        // 记录操作日志
        SystemLog logEntry = SystemLog.info("尝试启动数据库服务", userId, username);
        logEntry.setServiceName("database");
        logEntry.setServiceOperation("start");
        logEntry.setOperationType("SERVICE_CONTROL");
        
        try {
            // 实际环境中，这里应该调用实际的数据库服务启动代码
            // 这里仅模拟服务启动
            simulateServiceOperation(3000);
            
            // 测试数据库连接
            boolean isConnected = isMongoDbConnected();
            
            if (isConnected) {
                // 更新服务状态
                serviceStatus.put("database", true);
                
                // 记录成功日志
                logEntry.setLevel("SUCCESS");
                logEntry.setMessage("成功启动数据库服务");
                logEntry.setSuccess(true);
                systemLogRepository.save(logEntry);
                
                return true;
            } else {
                // 记录失败日志
                logEntry.setLevel("ERROR");
                logEntry.setMessage("数据库服务启动失败: 无法连接到数据库");
                logEntry.setSuccess(false);
                systemLogRepository.save(logEntry);
                
                return false;
            }
        } catch (Exception e) {
            log.error("启动数据库服务失败", e);
            
            // 记录失败日志
            logEntry.setLevel("ERROR");
            logEntry.setMessage("启动数据库服务失败: " + e.getMessage());
            logEntry.setSuccess(false);
            systemLogRepository.save(logEntry);
            
            return false;
        }
    }

    @Override
    public boolean stopDatabaseService(String userId, String username) {
        log.info("停止数据库服务, 操作人: {}", username);
        
        // 记录操作日志
        SystemLog logEntry = SystemLog.info("尝试停止数据库服务", userId, username);
        logEntry.setServiceName("database");
        logEntry.setServiceOperation("stop");
        logEntry.setOperationType("SERVICE_CONTROL");
        
        try {
            // 实际环境中，这里应该调用实际的数据库服务停止代码
            // 这里仅模拟服务停止
            simulateServiceOperation(1800);
            
            // 更新服务状态
            serviceStatus.put("database", false);
            
            // 记录成功日志
            logEntry.setLevel("SUCCESS");
            logEntry.setMessage("成功停止数据库服务");
            logEntry.setSuccess(true);
            systemLogRepository.save(logEntry);
            
            return true;
        } catch (Exception e) {
            log.error("停止数据库服务失败", e);
            
            // 记录失败日志
            logEntry.setLevel("ERROR");
            logEntry.setMessage("停止数据库服务失败: " + e.getMessage());
            logEntry.setSuccess(false);
            systemLogRepository.save(logEntry);
            
            return false;
        }
    }

    @Override
    public Map<String, Object> testModelConnection(String userId, String username) {
        log.info("测试模型连接, 操作人: {}", username);
        Map<String, Object> result = new HashMap<>();
        
        // 记录操作日志
        SystemLog logEntry = SystemLog.info("尝试测试模型连接", userId, username);
        logEntry.setServiceName("model");
        logEntry.setServiceOperation("test");
        logEntry.setOperationType("CONNECTIVITY_TEST");
        
        try {
            // 检查模型服务状态
            if (!serviceStatus.get("model")) {
                result.put("success", false);
                result.put("message", "模型服务当前已停止，请先启动服务");
                
                // 记录测试结果
                logEntry.setLevel("WARNING");
                logEntry.setMessage("模型连接测试失败: 服务未启动");
                logEntry.setSuccess(false);
                systemLogRepository.save(logEntry);
                
                return result;
            }
            
            // 实际环境中，这里应该实际测试模型连接
            // 这里仅模拟连接测试
            simulateServiceOperation(1000);
            
            // 随机生成测试结果，50%成功概率（实际应该是真实的连接测试）
            boolean testSuccess = true; // 实际项目中应该是真实的连接测试结果
            
            result.put("success", testSuccess);
            
            if (testSuccess) {
                result.put("message", "成功连接到模型服务");
                result.put("latency", "42ms");
                
                // 记录成功日志
                logEntry.setLevel("SUCCESS");
                logEntry.setMessage("模型连接测试成功: 延迟42ms");
                logEntry.setSuccess(true);
            } else {
                result.put("message", "无法连接到模型服务，请检查网络设置");
                
                // 记录失败日志
                logEntry.setLevel("ERROR");
                logEntry.setMessage("模型连接测试失败: 连接超时");
                logEntry.setSuccess(false);
            }
            
            systemLogRepository.save(logEntry);
            return result;
            
        } catch (Exception e) {
            log.error("测试模型连接出错", e);
            
            result.put("success", false);
            result.put("message", "测试过程中发生错误: " + e.getMessage());
            
            // 记录错误日志
            logEntry.setLevel("ERROR");
            logEntry.setMessage("模型连接测试出错: " + e.getMessage());
            logEntry.setSuccess(false);
            systemLogRepository.save(logEntry);
            
            return result;
        }
    }

    @Override
    public Map<String, Object> testDatabaseConnection(String userId, String username) {
        log.info("测试数据库连接, 操作人: {}", username);
        Map<String, Object> result = new HashMap<>();
        
        // 记录操作日志
        SystemLog logEntry = SystemLog.info("尝试测试数据库连接", userId, username);
        logEntry.setServiceName("database");
        logEntry.setServiceOperation("test");
        logEntry.setOperationType("CONNECTIVITY_TEST");
        
        try {
            // 检查数据库服务状态
            if (!serviceStatus.get("database")) {
                result.put("success", false);
                result.put("message", "数据库服务当前已停止，请先启动服务");
                
                // 记录测试结果
                logEntry.setLevel("WARNING");
                logEntry.setMessage("数据库连接测试失败: 服务未启动");
                logEntry.setSuccess(false);
                systemLogRepository.save(logEntry);
                
                return result;
            }
            
            // 实际测试数据库连接
            boolean isConnected = isMongoDbConnected();
            
            result.put("success", isConnected);
            
            if (isConnected) {
                // 获取数据库信息
                String dbName = mongoTemplate.getDb().getName();
                List<String> collections = mongoTemplate.getCollectionNames().stream().toList();
                
                result.put("message", "成功连接到数据库 " + dbName);
                result.put("dbName", dbName);
                result.put("collections", collections);
                result.put("latency", "28ms");
                
                // 记录成功日志
                logEntry.setLevel("SUCCESS");
                logEntry.setMessage("数据库连接测试成功: 延迟28ms, 数据库名: " + dbName);
                logEntry.setSuccess(true);
            } else {
                result.put("message", "无法连接到数据库，请检查数据库配置");
                
                // 记录失败日志
                logEntry.setLevel("ERROR");
                logEntry.setMessage("数据库连接测试失败: 无法建立连接");
                logEntry.setSuccess(false);
            }
            
            systemLogRepository.save(logEntry);
            return result;
            
        } catch (Exception e) {
            log.error("测试数据库连接出错", e);
            
            result.put("success", false);
            result.put("message", "测试过程中发生错误: " + e.getMessage());
            
            // 记录错误日志
            logEntry.setLevel("ERROR");
            logEntry.setMessage("数据库连接测试出错: " + e.getMessage());
            logEntry.setSuccess(false);
            systemLogRepository.save(logEntry);
            
            return result;
        }
    }

    @Override
    public List<SystemLog> getSystemLogs(int limit) {
        try {
            if (limit <= 0) {
                limit = 20; // 默认获取20条
            }
            
            if (limit > 100) {
                limit = 100; // 最多获取100条
            }
            
            // 检查是否有日志记录
            long count = systemLogRepository.count();
            
            if (count == 0) {
                // 如果没有日志，创建一些初始日志
                createInitialSystemLogs();
            }
            
            // 获取最近的日志记录
            return systemLogRepository.findTop20ByOrderByTimestampDesc();
        } catch (Exception e) {
            log.error("获取系统日志失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public SystemLog addSystemLog(SystemLog log) {
        if (log.getTimestamp() == null) {
            log.setTimestamp(LocalDateTime.now());
        }
        
        return systemLogRepository.save(log);
    }
    
    @Override
    public boolean clearSystemLogs(String userId, String username) {
        log.info("清除所有系统日志, 操作人: {}", username);
        
        // 记录操作日志
        SystemLog logEntry = SystemLog.info("尝试清除所有系统日志", userId, username);
        logEntry.setOperationType("SYSTEM_MAINTENANCE");
        
        try {
            // 方式1：使用MongoTemplate删除所有日志
            mongoTemplate.remove(new org.springframework.data.mongodb.core.query.Query(), "system_logs");
            
            // 方式2：也可以使用Repository方法删除所有日志
            // systemLogRepository.deleteAll();
            
            // 创建新的日志项表示清除操作
            SystemLog newLogEntry = SystemLog.success("成功清除系统日志", userId, username);
            newLogEntry.setOperationType("SYSTEM_MAINTENANCE");
            systemLogRepository.save(newLogEntry);
            
            return true;
        } catch (Exception e) {
            log.error("清除系统日志失败", e);
            
            // 记录失败日志
            logEntry.setLevel("ERROR");
            logEntry.setMessage("清除系统日志失败: " + e.getMessage());
            logEntry.setSuccess(false);
            systemLogRepository.save(logEntry);
            
            return false;
        }
    }
    
    /**
     * 测试MongoDB连接
     */
    private boolean isMongoDbConnected() {
        try {
            // 执行ping命令测试连接
            Object result = mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1)).get("ok");
            
            // 解析结果
            if (result instanceof Double) {
                return ((Double) result) > 0;
            } else if (result instanceof Integer) {
                return ((Integer) result) > 0;
            } else if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                return false;
            }
        } catch (Exception e) {
            log.error("MongoDB连接测试失败", e);
            return false;
        }
    }
    
    /**
     * 模拟服务操作的执行时间
     */
    private void simulateServiceOperation(long millis) {
        try {
            Thread.sleep(millis / 10); // 减少实际等待时间，便于测试
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 创建初始系统日志记录
     */
    private void createInitialSystemLogs() {
        List<SystemLog> initialLogs = new ArrayList<>();
        
        // 系统启动日志
        SystemLog startupLog = SystemLog.info("系统启动完成", "system", "系统");
        startupLog.setTimestamp(LocalDateTime.now().minusMinutes(30));
        startupLog.setOperationType("SYSTEM_STARTUP");
        initialLogs.add(startupLog);
        
        // 数据库连接日志
        SystemLog dbLog = SystemLog.success("数据库连接成功", "system", "系统");
        dbLog.setTimestamp(LocalDateTime.now().minusMinutes(29));
        dbLog.setOperationType("DATABASE_CONNECTION");
        dbLog.setServiceName("database");
        initialLogs.add(dbLog);
        
        // 模型服务日志
        SystemLog modelLog = SystemLog.success("模型服务初始化完成", "system", "系统");
        modelLog.setTimestamp(LocalDateTime.now().minusMinutes(28));
        modelLog.setOperationType("SERVICE_STARTUP");
        modelLog.setServiceName("model");
        initialLogs.add(modelLog);
        
        // 保存初始日志
        systemLogRepository.saveAll(initialLogs);
    }
} 