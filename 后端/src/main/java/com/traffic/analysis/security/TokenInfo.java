package com.traffic.analysis.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户令牌信息类
 * 用于Spring Security认证中存储用户信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo {
    
    private String id;
    private String username;
    private String role;
    
    // 用于判断用户是否是管理员
    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(role);
    }
    
    /**
     * 重写toString方法，确保在日志和授权过程中仅使用用户名
     */
    @Override
    public String toString() {
        return username != null ? username : "未知用户";
    }
} 