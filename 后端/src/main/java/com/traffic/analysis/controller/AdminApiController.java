package com.traffic.analysis.controller;

import com.traffic.analysis.model.User;
import com.traffic.analysis.security.TokenInfo;
import com.traffic.analysis.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员API控制器 - 处理管理员对用户的管理功能
 */
@RestController
@CrossOrigin(origins = {"http://localhost:8081", "http://localhost:8080", "http://localhost:5173", "http://localhost:5000", "http://localhost:5001"}, 
            allowCredentials = "true", maxAge = 3600)
@RequestMapping("/api/admin")
public class AdminApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminApiController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;
    
    /**
     * 验证当前用户是否为管理员
     */
    private boolean isAdmin() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) {
                logger.warn("无法获取认证信息");
                return false;
            }
            
            logger.info("验证管理员权限: 认证对象类名={}, 主体类名={}, 主体={}, 认证={}",
                    auth.getClass().getName(),
                    auth.getPrincipal().getClass().getName(),
                    auth.getName(),
                    auth.isAuthenticated());
            
            // 首先检查权限列表
            boolean hasAdminAuthority = auth.getAuthorities().stream()
                    .anyMatch(a -> {
                        String authority = a.getAuthority();
                        boolean isAdminRole = "ROLE_ADMIN".equalsIgnoreCase(authority) || "ADMIN".equalsIgnoreCase(authority);
                        logger.debug("检查权限: {} -> {}", authority, isAdminRole);
                        return isAdminRole;
                    });
                    
            if (hasAdminAuthority) {
                logger.info("通过权限列表验证用户{}为管理员", auth.getName());
                return true;
            } else {
                logger.debug("权限列表中未找到管理员角色，权限列表: {}", 
                        auth.getAuthorities().stream()
                            .map(a -> a.getAuthority())
                            .collect(java.util.stream.Collectors.joining(", ")));
            }

            // 首先尝试从Authentication的details获取TokenInfo
            Object details = auth.getDetails();
            if (details instanceof TokenInfo) {
                TokenInfo tokenInfo = (TokenInfo) details;
                logger.info("从TokenInfo获取角色: {}", tokenInfo.getRole());
                if ("admin".equalsIgnoreCase(tokenInfo.getRole())) {
                    logger.info("通过TokenInfo验证用户{}为管理员", tokenInfo.getUsername());
                    return true;
                }
            } else {
                logger.debug("认证对象details不是TokenInfo类型: detailsClass={}", 
                        details != null ? details.getClass().getName() : "null");
            }
            
            // 如果details中没有TokenInfo，则尝试从用户名查询数据库
            String username = auth.getName();
            logger.info("尝试通过数据库查询验证用户{}的管理员权限", username);
            User user = userService.findByUsername(username);
            
            if (user == null) {
                logger.warn("在数据库中未找到用户: {}", username);
                return false;
            }
            
            boolean isAdmin = "admin".equalsIgnoreCase(user.getRole());
            logger.info("通过数据库验证用户{}的角色为{}, 是否管理员: {}", 
                    username, user.getRole(), isAdmin);
            return isAdmin;
        } catch (Exception e) {
            logger.error("验证管理员权限时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取用户列表（支持分页和搜索）
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false) String query) {
        
        try {
            // 验证管理员权限
            if (!isAdmin()) {
                logger.warn("非管理员尝试访问用户列表");
                return ResponseEntity.status(403).body(createErrorResponse("无权限执行此操作"));
            }
            
            logger.info("获取用户列表: page={}, limit={}, query={}", page, limit, query);
            
            // 计算分页参数
            int skip = (page - 1) * limit;
            
            // 获取用户列表
            List<User> users = userService.getUsers(query, limit, skip);
            int totalUsers = userService.countUsers(query);
            
            // 构建响应数据，不包含敏感信息
            List<Map<String, Object>> usersData = users.stream().map(user -> {
                Map<String, Object> userData = new HashMap<>();
                userData.put("id", user.getId());
                userData.put("username", user.getUsername());
                userData.put("email", user.getEmail());
                userData.put("phone", user.getPhone());
                userData.put("role", user.getRole());
                userData.put("createdAt", user.getCreatedAt());
                userData.put("lastLoginAt", user.getLastLoginAt());
                userData.put("active", user.getActive()); // 使用User对象的active字段
                return userData;
            }).collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("items", usersData);
            result.put("total", totalUsers);
            result.put("page", page);
            result.put("limit", limit);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", result);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取用户列表出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(createErrorResponse("获取用户列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建新用户
     */
    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody User userInput) {
        try {
            // 验证管理员权限
            if (!isAdmin()) {
                logger.warn("非管理员尝试创建用户");
                return ResponseEntity.status(403).body(createErrorResponse("无权限执行此操作"));
            }
            
            logger.info("创建新用户: {}", userInput.getUsername());
            
            // 验证必填字段
            if (userInput.getUsername() == null || userInput.getUsername().trim().isEmpty() ||
                userInput.getPassword() == null || userInput.getPassword().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("用户名和密码不能为空"));
            }
            
            // 检查用户名是否已存在
            if (userService.isUserExists(userInput.getUsername())) {
                return ResponseEntity.badRequest().body(createErrorResponse("用户名已存在"));
            }
            
            // 创建新用户
            User newUser = new User();
            newUser.setUsername(userInput.getUsername());
            newUser.setPassword(passwordEncoder.encode(userInput.getPassword()));
            newUser.setEmail(userInput.getEmail());
            newUser.setPhone(userInput.getPhone());
            newUser.setRole(userInput.getRole() != null ? userInput.getRole() : "user");
            newUser.setCreatedAt(LocalDateTime.now());
            
            User savedUser = userService.createUser(newUser);
            
            // 构建响应数据
            Map<String, Object> userData = new HashMap<>();
            userData.put("id", savedUser.getId());
            userData.put("username", savedUser.getUsername());
            userData.put("email", savedUser.getEmail());
            userData.put("phone", savedUser.getPhone());
            userData.put("role", savedUser.getRole());
            userData.put("createdAt", savedUser.getCreatedAt());
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "用户创建成功");
            response.put("data", userData);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("创建用户出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(createErrorResponse("创建用户失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新用户信息
     */
    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable String id, @RequestBody User userUpdate) {
        try {
            // 验证管理员权限
            if (!isAdmin()) {
                logger.warn("非管理员尝试更新用户");
                return ResponseEntity.status(403).body(createErrorResponse("无权限执行此操作"));
            }
            
            logger.info("更新用户信息: id={}", id);
            
            // 获取现有用户
            User existingUser = userService.findById(id);
            if (existingUser == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("用户不存在"));
            }
            
            // 如果更新用户名，检查新用户名是否已存在
            if (userUpdate.getUsername() != null && !userUpdate.getUsername().equals(existingUser.getUsername())) {
                if (userService.isUserExists(userUpdate.getUsername())) {
                    return ResponseEntity.badRequest().body(createErrorResponse("用户名已存在"));
                }
                existingUser.setUsername(userUpdate.getUsername());
            }
            
            // 更新用户信息
            if (userUpdate.getEmail() != null) {
                existingUser.setEmail(userUpdate.getEmail());
            }
            if (userUpdate.getPhone() != null) {
                existingUser.setPhone(userUpdate.getPhone());
            }
            if (userUpdate.getRole() != null) {
                existingUser.setRole(userUpdate.getRole());
            }
            
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
     * 重置用户密码
     */
    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@PathVariable String id, @RequestBody Map<String, String> resetData) {
        try {
            // 验证管理员权限
            if (!isAdmin()) {
                logger.warn("非管理员尝试重置用户密码");
                return ResponseEntity.status(403).body(createErrorResponse("无权限执行此操作"));
            }
            
            String newPassword = resetData.get("newPassword");
            if (newPassword == null || newPassword.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("新密码不能为空"));
            }
            
            logger.info("重置用户密码: id={}", id);
            
            // 获取用户
            User user = userService.findById(id);
            if (user == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("用户不存在"));
            }
            
            // 重置密码
            boolean success = userService.resetPassword(id, newPassword);
            if (!success) {
                return ResponseEntity.badRequest().body(createErrorResponse("重置密码失败"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "密码重置成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("重置用户密码出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(createErrorResponse("重置用户密码失败: " + e.getMessage()));
        }
    }
    
    /**
     * 切换用户状态（启用/禁用）
     */
    @PatchMapping("/users/{id}/status")
    public ResponseEntity<Map<String, Object>> toggleUserStatus(@PathVariable String id, @RequestBody Map<String, Boolean> statusUpdate) {
        try {
            // 验证管理员权限
            if (!isAdmin()) {
                logger.warn("非管理员尝试修改用户状态");
                return ResponseEntity.status(403).body(createErrorResponse("无权限执行此操作"));
            }
            
            Boolean active = statusUpdate.get("active");
            if (active == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("状态参数不能为空"));
            }
            
            logger.info("切换用户状态: id={}, active={}", id, active);
            
            // 获取用户
            User user = userService.findById(id);
            if (user == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("用户不存在"));
            }
            
            // 设置用户状态
            boolean success = userService.setUserStatus(id, active);
            if (!success) {
                return ResponseEntity.badRequest().body(createErrorResponse("更新用户状态失败"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", active ? "用户已启用" : "用户已禁用");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("切换用户状态出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(createErrorResponse("切换用户状态失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String id) {
        try {
            // 验证管理员权限
            if (!isAdmin()) {
                logger.warn("非管理员尝试删除用户");
                return ResponseEntity.status(403).body(createErrorResponse("无权限执行此操作"));
            }
            
            logger.info("删除用户: id={}", id);
            
            // 获取用户
            User user = userService.findById(id);
            if (user == null) {
                return ResponseEntity.badRequest().body(createErrorResponse("用户不存在"));
            }
            
            // 删除用户
            boolean success = userService.deleteUser(id);
            if (!success) {
                return ResponseEntity.badRequest().body(createErrorResponse("删除用户失败"));
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "用户删除成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除用户出错: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(createErrorResponse("删除用户失败: " + e.getMessage()));
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