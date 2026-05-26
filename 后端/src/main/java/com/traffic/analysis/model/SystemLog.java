package com.traffic.analysis.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 系统日志实体类
 * 用于记录系统操作、状态变更和连接测试等日志信息
 */
@Document(collection = "system_logs")
public class SystemLog {
    
    @Id
    private String id;
    private String level;       // 日志级别: INFO, WARNING, ERROR, SUCCESS
    private String message;     // 日志消息
    private String type;        // 日志类型: MODEL, DATABASE, SYSTEM
    private String action;      // 操作类型: START, STOP, TEST, STATUS
    private String username;    // 操作用户名
    private LocalDateTime timestamp; // 操作时间
    private String result;      // 操作结果或详细信息
    
    public SystemLog() {
        this.timestamp = LocalDateTime.now();
    }
    
    public SystemLog(String level, String message, String type, String action) {
        this();
        this.level = level;
        this.message = message;
        this.type = type;
        this.action = action;
    }
    
    public static SystemLog info(String message, String type, String action) {
        return new SystemLog("INFO", message, type, action);
    }
    
    public static SystemLog error(String message, String type, String action) {
        return new SystemLog("ERROR", message, type, action);
    }
    
    public static SystemLog warning(String message, String type, String action) {
        return new SystemLog("WARNING", message, type, action);
    }
    
    public static SystemLog success(String message, String type, String action) {
        return new SystemLog("SUCCESS", message, type, action);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
} 