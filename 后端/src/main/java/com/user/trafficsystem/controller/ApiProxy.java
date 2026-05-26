package com.user.trafficsystem.controller;

import com.user.trafficsystem.service.PythonApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * API代理控制器，将前端API请求转发到Python API
 */
@RestController
public class ApiProxy {

    @Autowired
    private PythonApiService pythonApiService;

    /**
     * 代理GET /traffic/history?limit=x&skip=y 请求
     */
    @GetMapping("/traffic/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        System.out.println("API代理: GET /traffic/history -> Python API");
        
        try {
            // 提取令牌
            String token = extractToken(authHeader);
            
            // 调用Python API获取历史记录
            Map<String, Object> response = pythonApiService.getHistory(limit, skip, token);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取历史记录失败: " + e.getMessage());
            errorResponse.put("results", new Object[0]);
            errorResponse.put("total", 0);
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    /**
     * 代理GET /traffic/history/stats 请求
     */
    @GetMapping("/traffic/history/stats")
    public ResponseEntity<Map<String, Object>> getHistoryStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        System.out.println("API代理: GET /traffic/history/stats -> Python API");
        
        try {
            // 提取令牌
            String token = extractToken(authHeader);
            
            // 调用Python API获取统计数据
            Map<String, Object> response = pythonApiService.getHistoryStats(token);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取统计数据失败: " + e.getMessage());
            errorResponse.put("api_status", "online");
            errorResponse.put("db_status", "unknown");
            errorResponse.put("model_status", "offline");
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    /**
     * 代理POST /traffic/history/save 请求
     */
    @PostMapping("/traffic/history/save")
    public ResponseEntity<Map<String, Object>> saveHistorySave(
            @RequestBody Map<String, Object> analysisData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        System.out.println("API代理: POST /traffic/history/save -> Python API");
        
        try {
            // 提取令牌
            String token = extractToken(authHeader);
            
            // 调用Python API保存分析结果
            Map<String, Object> response = pythonApiService.saveAnalysisResult(analysisData, token);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "保存分析结果失败: " + e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    /**
     * 代理POST /traffic/history 请求
     */
    @PostMapping("/traffic/history")
    public ResponseEntity<Map<String, Object>> saveHistory(
            @RequestBody Map<String, Object> analysisData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        System.out.println("API代理: POST /traffic/history -> Python API");
        
        try {
            // 提取令牌
            String token = extractToken(authHeader);
            
            // 调用Python API保存分析结果
            Map<String, Object> response = pythonApiService.saveAnalysisResult(analysisData, token);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "保存分析结果失败: " + e.getMessage());
            return ResponseEntity.ok(errorResponse);
        }
    }
    
    /**
     * 检查API状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkApiStatus() {
        System.out.println("API代理: GET /status -> Python API");
        
        try {
            // 调用Python API检查状态
            Map<String, Object> response = pythonApiService.checkApiStatus();
            
            // 如果无法连接Python API，返回默认状态
            if (response.get("api_status").equals("offline")) {
                Map<String, Object> defaultStatus = new HashMap<>();
                defaultStatus.put("status", "success");
                defaultStatus.put("api_status", "online");
                defaultStatus.put("db_status", "unknown");
                defaultStatus.put("model_status", "offline");
                return ResponseEntity.ok(defaultStatus);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> defaultStatus = new HashMap<>();
            defaultStatus.put("status", "success");
            defaultStatus.put("api_status", "online");
            defaultStatus.put("db_status", "unknown");
            defaultStatus.put("model_status", "offline");
            defaultStatus.put("error", e.getMessage());
            return ResponseEntity.ok(defaultStatus);
        }
    }
    
    /**
     * 从Authorization头中提取令牌
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
} 