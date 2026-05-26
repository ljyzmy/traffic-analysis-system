package com.traffic.analysis.controller;

import com.traffic.analysis.model.User;
import com.traffic.analysis.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = {"http://localhost:8081", "http://localhost:8080", "http://localhost:5173", "http://localhost:5000", "http://localhost:5001"}, 
            allowCredentials = "true", maxAge = 3600)
@RequestMapping("/api/user")
public class UserApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(UserApiController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getUserInfo() {
        try {
            // 获取当前认证的用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            logger.info("获取用户信息: {}", username);
            
            // 根据用户名获取用户详情
            User user = userService.findByUsername(username);
            if (user == null) {
                logger.warn("未找到用户: {}", username);
                return ResponseEntity.badRequest().body(createErrorResponse("用户不存在"));
            }
            
            // 构建响应数据，不包含敏感信息如密码
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("username", user.getUsername());
            userData.put("email", user.getEmail());
            userData.put("phone", user.getPhone());
            userData.put("role", user.getRole());
            userData.put("createdAt", user.getCreatedAt());
            userData.put("lastLoginAt", user.getLastLoginAt());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", userData);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取用户信息出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(createErrorResponse("获取用户信息失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新用户信息
     */
    @PutMapping("/info")
    public ResponseEntity<Map<String, Object>> updateUserInfo(@RequestBody User userUpdate) {
        try {
            // 获取当前认证的用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            logger.info("更新用户信息: {}", username);
            
            // 根据用户名获取现有用户
            User existingUser = userService.findByUsername(username);
            if (existingUser == null) {
                logger.warn("未找到用户: {}", username);
                return ResponseEntity.badRequest().body(createErrorResponse("用户不存在"));
            }
            
            // 添加用户名更新支持
            if (userUpdate.getUsername() != null && !userUpdate.getUsername().equals(existingUser.getUsername())) {
                // 检查新用户名是否已存在
                if (userService.isUserExists(userUpdate.getUsername())) {
                    logger.warn("用户名已存在: {}", userUpdate.getUsername());
                    return ResponseEntity.badRequest().body(createErrorResponse("用户名已存在，请选择其他用户名"));
                }
                logger.info("用户正在更改用户名: {} -> {}", existingUser.getUsername(), userUpdate.getUsername());
                existingUser.setUsername(userUpdate.getUsername());
            }
            
            // 只更新允许的字段，不允许更新敏感字段如密码
            if (userUpdate.getEmail() != null) {
                existingUser.setEmail(userUpdate.getEmail());
            }
            
            if (userUpdate.getPhone() != null) {
                existingUser.setPhone(userUpdate.getPhone());
            }
            
            // 更新用户
            User updatedUser = userService.updateUser(existingUser);
            
            // 构建响应数据
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", updatedUser.getId());
            userData.put("username", updatedUser.getUsername());
            userData.put("email", updatedUser.getEmail());
            userData.put("phone", updatedUser.getPhone());
            userData.put("role", updatedUser.getRole());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "用户信息更新成功");
            response.put("data", userData);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("更新用户信息出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(createErrorResponse("更新用户信息失败: " + e.getMessage()));
        }
    }
    
    /**
     * 修改用户密码
     */
    @PutMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> passwordData) {
        try {
            // 获取当前认证的用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            logger.info("修改密码请求: {}", username);
            
            // 获取请求数据
            String currentPassword = passwordData.get("currentPassword");
            String newPassword = passwordData.get("newPassword");
            String confirmPassword = passwordData.get("confirmPassword");
            
            // 验证请求数据完整性
            if (currentPassword == null || newPassword == null || confirmPassword == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("请提供当前密码、新密码和确认新密码"));
            }
            
            // 验证两次输入的新密码是否一致
            if (!newPassword.equals(confirmPassword)) {
                return ResponseEntity.badRequest().body(createErrorResponse("新密码和确认密码不一致"));
            }
            
            // 根据用户名获取用户
            User user = userService.findByUsername(username);
            if (user == null) {
                logger.warn("未找到用户: {}", username);
                return ResponseEntity.badRequest().body(createErrorResponse("用户不存在"));
            }
            
            // 验证当前密码是否正确
            if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                logger.warn("当前密码验证失败: {}", username);
                return ResponseEntity.badRequest().body(createErrorResponse("当前密码不正确"));
            }
            
            // 更新密码
            user.setPassword(passwordEncoder.encode(newPassword));
            userService.updateUser(user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "密码修改成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("修改密码出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(createErrorResponse("修改密码失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除用户账户
     */
    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteUserAccount(@RequestBody Map<String, String> deleteRequest) {
        try {
            // 获取当前认证的用户
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            
            logger.info("删除账户请求: {}", username);
            
            // 获取请求数据
            String password = deleteRequest.get("password");
            
            if (password == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("请提供密码以确认删除账户"));
            }
            
            // 根据用户名获取用户
            User user = userService.findByUsername(username);
            if (user == null) {
                logger.warn("未找到用户: {}", username);
                return ResponseEntity.badRequest().body(createErrorResponse("用户不存在"));
            }
            
            // 验证密码是否正确
            if (!passwordEncoder.matches(password, user.getPassword())) {
                logger.warn("密码验证失败，删除账户操作被拒绝: {}", username);
                return ResponseEntity.badRequest().body(createErrorResponse("密码不正确，无法删除账户"));
            }
            
            // 执行删除操作
            String userId = user.getId();
            boolean success = userService.deleteUser(userId);
            
            if (!success) {
                logger.warn("删除账户失败: {}", username);
                return ResponseEntity.badRequest().body(createErrorResponse("删除账户失败，请稍后重试"));
            }
            
            logger.info("用户账户已成功删除: {}, ID: {}", username, userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "账户已成功删除");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除账户出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(createErrorResponse("删除账户失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建错误响应
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }
} 