package com.traffic.analysis.controller;

import com.traffic.analysis.exception.UserDisabledException;
import com.traffic.analysis.model.User;
import com.traffic.analysis.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@CrossOrigin(origins = {"http://localhost:8081", "http://localhost:8080", "http://localhost:5173", "http://localhost:5000", "http://localhost:5001"}, 
            allowCredentials = "true", maxAge = 3600)
@RequestMapping("/api/auth")
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");
        
        // 记录登录尝试，帮助调试
        logger.info("登录尝试 - 用户名: {}, 请求体: {}", username, loginRequest);
        
        try {
        // 使用UserService验证用户
        User user = userService.authenticate(username, password);
        
        if (user != null) {
            // 创建JWT令牌
            String accessToken = generateToken(username);
            
            // 创建用户对象，包含前端所需的信息
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("username", user.getUsername());
            userInfo.put("id", user.getId());
            userInfo.put("role", user.getRole());
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", accessToken);
            response.put("user", userInfo);
            response.put("status", "success");
            response.put("message", "登录成功");
            
            logger.info("用户[{}]登录成功", username);
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "用户名或密码错误");
                response.put("errorCode", "INVALID_CREDENTIALS");
            
                logger.warn("用户[{}]登录失败：用户名或密码错误", username);
            return ResponseEntity.status(401).body(response);
            }
        } catch (UserDisabledException e) {
            // 用户被禁用的特殊处理
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "您的账户已被禁用，请联系管理员");
            response.put("errorCode", "USER_DISABLED");
            
            logger.warn("用户[{}]登录失败：账户已被禁用", username);
            return ResponseEntity.status(403).body(response);
        } catch (Exception e) {
            // 其他异常处理
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "登录失败: " + e.getMessage());
            response.put("errorCode", "SERVER_ERROR");
            
            logger.error("用户[{}]登录失败：服务器错误 - {}", username, e.getMessage(), e);
            return ResponseEntity.status(500).body(response);
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
        String email = registerRequest.get("email");
        
        logger.info("注册请求 - 用户名: {}, 邮箱: {}", username, email);
        
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "用户名和密码不能为空");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            // 检查用户是否已存在
            if (userService.isUserExists(username)) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "用户名已存在");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 注册新用户，传递email参数
            User newUser = userService.registerUser(username, password, email);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "注册成功");
            response.put("user_id", newUser.getId());
            
            logger.info("用户[{}]注册成功，ID: {}", username, newUser.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("注册用户时出错: {}", e.getMessage(), e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "注册失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    // 添加OPTIONS请求的处理
    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        logger.debug("处理认证API的OPTIONS请求");
        return ResponseEntity.ok().build();
    }
    
    // 添加状态检查端点
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "active");
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * 刷新令牌 - 支持POST和GET请求
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshTokenPost(@RequestBody(required = false) Map<String, String> refreshRequest,
                                                           @RequestHeader(value = "Authorization", required = false) String authHeader) {
        return refreshToken(refreshRequest, authHeader);
    }
    
    /**
     * 刷新令牌 - 支持GET请求
     */
    @GetMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshTokenGet(@RequestParam(required = false) String token,
                                                          @RequestParam(required = false) String username,
                                                          @RequestHeader(value = "Authorization", required = false) String authHeader) {
        Map<String, String> refreshRequest = new HashMap<>();
        if (token != null) {
            refreshRequest.put("token", token);
        }
        if (username != null) {
            refreshRequest.put("username", username);
        }
        return refreshToken(refreshRequest, authHeader);
    }
    
    /**
     * 刷新令牌的通用处理方法
     */
    private ResponseEntity<Map<String, Object>> refreshToken(Map<String, String> refreshRequest, String authHeader) {
        try {
            logger.info("收到刷新令牌请求");
            
            // 尝试从请求体获取令牌
            String token = refreshRequest != null ? refreshRequest.get("token") : null;
            
            // 如果请求体中没有token，尝试从Authorization头获取
            if ((token == null || token.isEmpty()) && authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
            
            // 如果无法获取令牌，返回错误
            if (token == null || token.isEmpty()) {
                logger.warn("未提供要刷新的令牌");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "未提供要刷新的令牌");
                return ResponseEntity.status(401).body(errorResponse);
            }
            
            // 从令牌中提取用户名
            String username = null;
            try {
                String[] parts = token.split("_");
                if (parts.length >= 2) {
                    username = parts[1]; // 假设用户名在第二部分
                }
            } catch (Exception e) {
                logger.error("解析令牌失败: {}", e.getMessage());
            }
            
            // 如果无法从令牌获取用户名，尝试从请求中获取
            if (username == null && refreshRequest != null) {
                username = refreshRequest.get("username");
            }
            
            // 如果仍然无法获取用户名，返回错误
            if (username == null || username.isEmpty()) {
                logger.warn("无法获取用户名");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "无法获取用户名");
                return ResponseEntity.status(401).body(errorResponse);
            }
            
            // 查找用户
            User user = userService.findByUsername(username);
            if (user == null) {
                logger.warn("未找到用户: {}", username);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "用户不存在");
                return ResponseEntity.status(401).body(errorResponse);
            }
            
            // 创建新的令牌
            String newToken = generateToken(username);
            
            // 构建响应
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("username", user.getUsername());
            userInfo.put("id", user.getId());
            userInfo.put("role", user.getRole());
            
            Map<String, Object> response = new HashMap<>();
            response.put("token", newToken);
            response.put("user", userInfo);
            response.put("status", "success");
            response.put("message", "令牌刷新成功");
            
            logger.info("用户[{}]令牌刷新成功", username);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("刷新令牌失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "刷新令牌失败: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
} 