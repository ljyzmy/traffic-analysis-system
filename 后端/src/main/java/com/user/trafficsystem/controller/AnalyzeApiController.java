package com.user.trafficsystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.user.trafficsystem.service.CarDetectionService;
import com.user.trafficsystem.service.PythonApiService;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5000", "http://localhost:5001", "http://localhost:8080"}, allowCredentials = "true", maxAge = 3600)
@RestController
public class AnalyzeApiController {

    @Autowired
    private CarDetectionService carDetectionService;
    
    /**
     * 处理含/api前缀的图像分析请求
     */
    @PostMapping(value = "/api/traffic/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> analyzeImageWithApiPrefix(
            @RequestPart("image") MultipartFile image,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        System.out.println("API分析控制器: POST /api/traffic/analyze -> 图像分析");
        System.out.println("认证头: " + (authHeader != null ? 
                (authHeader.length() > 15 ? authHeader.substring(0, 15) + "..." : authHeader) : "null"));
        System.out.println("文件名: " + image.getOriginalFilename() + ", 大小: " + image.getSize() + " 字节");
        
        try {
            // 处理认证令牌，提取用户信息
            String userId = null;
            String username = null;
            
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                System.out.println("处理令牌: " + (token.length() > 10 ? token.substring(0, 10) + "..." : token));
                
                // 尝试解析令牌格式 (id_username_timestamp)
                try {
                    String[] parts = token.split("_");
                    if (parts.length >= 2) {
                        userId = parts[0];
                        username = parts[1];
                        System.out.println("从令牌提取用户信息: id=" + userId + ", username=" + username);
                    } else {
                        System.out.println("令牌格式不符合预期: 部分数量=" + parts.length);
                    }
                } catch (Exception e) {
                    System.out.println("解析令牌时出错: " + e.getMessage());
                }
            } else {
                System.out.println("未提供有效的认证令牌");
            }
            
            // 调用车辆检测服务
            Map<String, Object> result = carDetectionService.detectCars(image);
            
            // 添加用户信息到结果中，如果有
            if (userId != null) {
                result.put("userId", userId);
                result.put("username", username);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            System.out.println("分析图像时出错: " + e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "图像分析失败: " + e.getMessage());
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