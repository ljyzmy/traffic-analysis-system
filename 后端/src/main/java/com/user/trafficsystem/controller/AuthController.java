package com.user.trafficsystem.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5000", "http://localhost:5001", "http://localhost:8080"}, 
            allowCredentials = "true", maxAge = 3600)
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");
        
        // 记录登录尝试
        System.out.println("登录尝试 - 用户名: " + username);
        
        // 演示账号（生产环境请禁用；密码通过环境变量 DEMO_ADMIN_PASSWORD / DEMO_USER_PASSWORD 配置）
        String demoAdminPass = System.getenv().getOrDefault("DEMO_ADMIN_PASSWORD", "changeme");
        String demoUserPass = System.getenv().getOrDefault("DEMO_USER_PASSWORD", "changeme");
        if (("admin".equals(username) && demoAdminPass.equals(password)) ||
            ("user".equals(username) && demoUserPass.equals(password))) {
            
            // 创建JWT令牌 - 简单实现，实际应用应使用更安全的JWT库
            String accessToken = generateToken(username);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "登录成功");
            response.put("access_token", accessToken);
            response.put("username", username);
            response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
            
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "用户名或密码错误");
            return ResponseEntity.status(401).body(response);
        }
    }
    
    // 简单的模拟令牌生成
    private String generateToken(String username) {
        return UUID.randomUUID().toString().replace("-", "") + 
               "_" + username + 
               "_" + System.currentTimeMillis();
    }
    
    // 添加注册端点
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> registerRequest) {
        String username = registerRequest.get("username");
        String password = registerRequest.get("password");
        
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "用户名和密码不能为空");
            return ResponseEntity.badRequest().body(response);
        }
        
        // 模拟用户注册成功
        String userId = UUID.randomUUID().toString();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "注册成功");
        response.put("user_id", userId);
        
        return ResponseEntity.ok(response);
    }
} 