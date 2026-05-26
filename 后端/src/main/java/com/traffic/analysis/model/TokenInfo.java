package com.traffic.analysis.model;

import lombok.Data;

/**
 * 令牌信息类，用于存储JWT令牌中的用户信息
 */
@Data
public class TokenInfo {
    
    private String userId;
    private String username;
    private String role;
    private Long expires;
    
    public TokenInfo() {
    }
    
    public TokenInfo(String userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }
    
    public TokenInfo(String userId, String username, String role, Long expires) {
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.expires = expires;
    }
} 