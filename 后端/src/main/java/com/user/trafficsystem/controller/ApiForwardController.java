package com.user.trafficsystem.controller;

import com.user.trafficsystem.service.PythonApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * API转发控制器，处理/api前缀的请求并转发到Python API
 */
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5000", "http://localhost:5001", "http://localhost:8080"}, allowCredentials = "true", maxAge = 3600)
@RestController
@RequestMapping("/api")
public class ApiForwardController {

    @Autowired
    private PythonApiService pythonApiService;

    /**
     * 代理 GET /api/traffic/history 请求
     */
    @GetMapping("/traffic/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "skip", defaultValue = "0") int skip,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        System.out.println("API转发控制器: GET /api/traffic/history -> Python API");
        
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
     * 代理 GET /api/traffic/history/stats 请求
     */
    @GetMapping("/traffic/history/stats")
    public ResponseEntity<Map<String, Object>> getHistoryStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        System.out.println("API转发控制器: GET /api/traffic/history/stats -> Python API");
        
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
     * 代理 POST /api/traffic/history/save 请求
     */
    @PostMapping("/traffic/history/save")
    public ResponseEntity<Map<String, Object>> saveHistorySave(
            @RequestBody Map<String, Object> analysisData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        System.out.println("API转发控制器: POST /api/traffic/history/save -> Python API");
        
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
     * 代理 POST /api/traffic/history 请求
     */
    @PostMapping("/traffic/history")
    public ResponseEntity<Map<String, Object>> saveHistory(
            @RequestBody Map<String, Object> analysisData,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        System.out.println("API转发控制器: POST /api/traffic/history -> Python API");
        
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
     * 从Authorization头中提取令牌
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
} 