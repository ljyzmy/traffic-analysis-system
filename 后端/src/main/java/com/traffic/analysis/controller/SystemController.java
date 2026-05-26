package com.traffic.analysis.controller;

import com.traffic.analysis.entity.SystemLog;
import com.traffic.analysis.service.SystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统设置控制器
 * 提供系统状态、服务管理和日志查询的API端点
 */
@RestController
@CrossOrigin(origins = {"http://localhost:8081", "http://localhost:8080", "http://localhost:5173", "http://localhost:5000", "http://localhost:5001"}, 
              allowCredentials = "true", maxAge = 3600)
@RequestMapping({"/api/system", "/system"})
@Slf4j
public class SystemController {
    
    @Autowired
    private SystemService systemService;
    
    /**
     * 获取系统状态
     * @return 系统状态信息
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        log.info("接收到获取系统状态请求");
        
        try {
            Map<String, Object> status = systemService.getSystemStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("获取系统状态失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "获取系统状态失败");
            error.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 启动模型服务
     * @return 操作结果
     */
    @PostMapping("/model/start")
    public ResponseEntity<Map<String, Object>> startModelService() {
        log.info("接收到启动模型服务请求");
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            String userId = getUserIdFromAuthentication(auth);
            
            boolean success = systemService.startModelService(userId, username);
            
            response.put("success", success);
            response.put("message", success ? "模型服务启动成功" : "模型服务启动失败");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("启动模型服务失败", e);
            response.put("success", false);
            response.put("message", "启动模型服务失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 停止模型服务
     * @return 操作结果
     */
    @PostMapping("/model/stop")
    public ResponseEntity<Map<String, Object>> stopModelService() {
        log.info("接收到停止模型服务请求");
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            String userId = getUserIdFromAuthentication(auth);
            
            boolean success = systemService.stopModelService(userId, username);
            
            response.put("success", success);
            response.put("message", success ? "模型服务停止成功" : "模型服务停止失败");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("停止模型服务失败", e);
            response.put("success", false);
            response.put("message", "停止模型服务失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 测试模型连接
     * @return 测试结果
     */
    @PostMapping("/model/test")
    public ResponseEntity<Map<String, Object>> testModelConnection() {
        log.info("接收到测试模型连接请求");
        
        try {
            // 获取当前用户信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            String userId = getUserIdFromAuthentication(auth);
            
            Map<String, Object> result = systemService.testModelConnection(userId, username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("测试模型连接失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "测试模型连接失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 启动数据库服务
     * @return 操作结果
     */
    @PostMapping("/database/start")
    public ResponseEntity<Map<String, Object>> startDatabaseService() {
        log.info("接收到启动数据库服务请求");
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            String userId = getUserIdFromAuthentication(auth);
            
            boolean success = systemService.startDatabaseService(userId, username);
            
            response.put("success", success);
            response.put("message", success ? "数据库服务启动成功" : "数据库服务启动失败");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("启动数据库服务失败", e);
            response.put("success", false);
            response.put("message", "启动数据库服务失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 停止数据库服务
     * @return 操作结果
     */
    @PostMapping("/database/stop")
    public ResponseEntity<Map<String, Object>> stopDatabaseService() {
        log.info("接收到停止数据库服务请求");
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            String userId = getUserIdFromAuthentication(auth);
            
            boolean success = systemService.stopDatabaseService(userId, username);
            
            response.put("success", success);
            response.put("message", success ? "数据库服务停止成功" : "数据库服务停止失败");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("停止数据库服务失败", e);
            response.put("success", false);
            response.put("message", "停止数据库服务失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 测试数据库连接
     * @return 测试结果
     */
    @PostMapping("/database/test")
    public ResponseEntity<Map<String, Object>> testDatabaseConnection() {
        log.info("接收到测试数据库连接请求");
        
        try {
            // 获取当前用户信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            String userId = getUserIdFromAuthentication(auth);
            
            Map<String, Object> result = systemService.testDatabaseConnection(userId, username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("测试数据库连接失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "测试数据库连接失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 获取系统日志
     * @param limit 日志数量限制
     * @return 系统日志列表
     */
    @GetMapping("/logs")
    public ResponseEntity<List<SystemLog>> getSystemLogs(
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        log.info("接收到获取系统日志请求，限制{}条", limit);
        
        try {
            List<SystemLog> logs = systemService.getSystemLogs(limit);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("获取系统日志失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    
    /**
     * 清除系统日志
     * @return 操作结果
     */
    @DeleteMapping("/logs")
    public ResponseEntity<Map<String, Object>> clearSystemLogs() {
        log.info("接收到清除系统日志请求");
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户信息
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            String userId = getUserIdFromAuthentication(auth);
            
            boolean success = systemService.clearSystemLogs(userId, username);
            
            response.put("success", success);
            response.put("message", success ? "系统日志清除成功" : "系统日志清除失败");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("清除系统日志失败", e);
            response.put("success", false);
            response.put("message", "清除系统日志失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 从身份验证对象中提取用户ID
     */
    private String getUserIdFromAuthentication(Authentication auth) {
        // 根据实际的用户对象实现获取用户ID的逻辑
        // 这里是一个简单的实现，实际项目中可能需要根据具体的用户对象类型来获取
        if (auth == null) {
            return "unknown";
        }
        
        Object principal = auth.getPrincipal();
        if (principal == null) {
            return auth.getName();
        }
        
        // 如果principal是字符串，直接返回
        if (principal instanceof String) {
            return (String) principal;
        }
        
        // 尝试通过反射获取id字段
        try {
            java.lang.reflect.Method getIdMethod = principal.getClass().getMethod("getId");
            if (getIdMethod != null) {
                Object id = getIdMethod.invoke(principal);
                if (id != null) {
                    return id.toString();
                }
            }
        } catch (Exception e) {
            log.debug("通过getId方法获取用户ID失败: {}", e.getMessage());
        }
        
        // 如果无法获取ID，使用用户名作为ID
        return auth.getName();
    }
} 