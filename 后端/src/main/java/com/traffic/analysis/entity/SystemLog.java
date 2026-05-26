package com.traffic.analysis.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 系统日志实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "system_logs")
public class SystemLog {
    
    @Id
    private String id;
    
    /**
     * 日志级别：INFO, WARNING, ERROR, SUCCESS
     */
    private String level;
    
    /**
     * 日志消息内容
     */
    private String message;
    
    /**
     * 操作类型
     */
    private String operationType;
    
    /**
     * 操作人ID
     */
    private String operatorId;
    
    /**
     * 操作人用户名
     */
    private String operatorName;
    
    /**
     * 操作时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 相关服务名称（如 model, database）
     */
    private String serviceName;
    
    /**
     * 服务操作（如 start, stop, test）
     */
    private String serviceOperation;
    
    /**
     * 操作结果
     */
    private boolean success;
    
    /**
     * 创建信息级别日志
     */
    public static SystemLog info(String message, String operatorId, String operatorName) {
        SystemLog log = new SystemLog();
        log.setLevel("INFO");
        log.setMessage(message);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setTimestamp(LocalDateTime.now());
        log.setSuccess(true);
        return log;
    }
    
    /**
     * 创建警告级别日志
     */
    public static SystemLog warning(String message, String operatorId, String operatorName) {
        SystemLog log = new SystemLog();
        log.setLevel("WARNING");
        log.setMessage(message);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setTimestamp(LocalDateTime.now());
        log.setSuccess(false);
        return log;
    }
    
    /**
     * 创建错误级别日志
     */
    public static SystemLog error(String message, String operatorId, String operatorName) {
        SystemLog log = new SystemLog();
        log.setLevel("ERROR");
        log.setMessage(message);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setTimestamp(LocalDateTime.now());
        log.setSuccess(false);
        return log;
    }
    
    /**
     * 创建成功级别日志
     */
    public static SystemLog success(String message, String operatorId, String operatorName) {
        SystemLog log = new SystemLog();
        log.setLevel("SUCCESS");
        log.setMessage(message);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setTimestamp(LocalDateTime.now());
        log.setSuccess(true);
        return log;
    }
} 