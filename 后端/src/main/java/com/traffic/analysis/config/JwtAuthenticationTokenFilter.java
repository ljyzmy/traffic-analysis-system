package com.traffic.analysis.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.traffic.analysis.security.TokenInfo;
import com.traffic.analysis.service.UserService;
import com.traffic.analysis.model.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.Collections;

import java.io.IOException;

/**
 * JWT认证过滤器
 */
@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationTokenFilter.class);
    
    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // 记录所有请求，帮助调试
        logger.debug("请求：{} {}", method, path);
        
        // 输出认证状态前
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        logger.debug("过滤器处理前认证状态: {}", existingAuth != null ? 
                existingAuth.getName() + ", 认证=" + existingAuth.isAuthenticated() : "null");
        
        // 跳过不需要认证的路径（更细粒度地检查）
        if (path.startsWith("/api/auth/") || 
            path.equals("/login") || 
            path.equals("/register") ||
            path.startsWith("/static/") ||
            path.startsWith("/css/") ||
            path.startsWith("/js/") ||
            path.startsWith("/images/") ||
            path.startsWith("/favicon.ico") ||
            "OPTIONS".equalsIgnoreCase(method)) {
            logger.debug("跳过JWT认证 (公开端点): {} {}", method, path);
            chain.doFilter(request, response);
            return;
        }
        
        // 获取Authorization头
        String authHeader = request.getHeader("Authorization");
        logger.debug("检查Authorization头: {}", authHeader);
        
        // 从URL参数中获取认证令牌
        String authTokenParam = request.getParameter("auth_token");
        if (StringUtils.hasText(authTokenParam) && !StringUtils.hasText(authHeader)) {
            authHeader = "Bearer " + authTokenParam;
            logger.debug("从URL参数中获取认证令牌: {}", authHeader);
        }
        
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                
                // 从令牌中提取用户名
                String username = extractUsernameFromToken(token);
                logger.info("从令牌中提取用户名: {}", username);
                
                if (username != null) {
                    // 创建一个简单的未认证令牌，让认证提供者后续进行完整认证
                    TokenInfo tokenInfo = extractTokenInfo(token);
                    
                    // 只创建一个基本令牌，不包含权限，让认证提供者负责添加权限
                    UsernamePasswordAuthenticationToken authToken = 
                            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
                    authToken.setDetails(tokenInfo);
                    
                    // 设置认证令牌到上下文
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.info("已创建初始认证令牌: username={}, userId={}, role={}",
                            username, tokenInfo.getId(), tokenInfo.getRole());
                } else {
                    logger.warn("无法从令牌提取用户名");
                }
            } catch (Exception e) {
                logger.error("JWT令牌处理失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
                
                // 对于API请求，直接返回401错误
                if (isApiRequest(path) && !response.isCommitted()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"未授权访问\",\"message\":\"JWT令牌验证失败\"}");
                    return; // 不继续过滤链
                }
            }
        } else if (isApiRequest(path) && 
                  !path.startsWith("/api/status/") && 
                  !path.startsWith("/api/health")) {
            // 记录需要认证但没有提供令牌的API请求
            logger.warn("API请求没有提供认证令牌: {} {}", method, path);
        }
        
        // 在继续过滤链之前记录最终认证状态
        Authentication finalAuth = SecurityContextHolder.getContext().getAuthentication();
        logger.info("继续过滤链前认证状态: {}", finalAuth != null ? 
                finalAuth.getName() + ", 认证=" + finalAuth.isAuthenticated() : "null");
        
        chain.doFilter(request, response);
    }
    
    // 判断是否为API请求
    private boolean isApiRequest(String path) {
        return path.startsWith("/api/");
    }
    
    private String extractUsernameFromToken(String token) {
        try {
            // 支持多种令牌格式
            // 1. [hash]_[username]_[timestamp] - 前端发送的简化格式
            // 2. [hash]_[username]_[userId]_[role]_[timestamp] - 标准格式
            String[] parts = token.split("_");
            logger.debug("令牌解析: 包含{}部分", parts.length);
            
            if (parts.length >= 2) {
                String username = parts[1]; // 无论哪种格式，用户名都在第二部分
                logger.debug("从令牌中提取用户名: {}", username);
                return username;
            }
        } catch (Exception e) {
            logger.error("解析令牌失败: {}", e.getMessage());
        }
        return null;
    }
    
    private TokenInfo extractTokenInfo(String token) {
        // 实现从令牌中提取更多信息的逻辑
        TokenInfo tokenInfo = new TokenInfo();
        String username = extractUsernameFromToken(token);
        tokenInfo.setUsername(username);
        
        try {
            String[] parts = token.split("_");
            logger.debug("令牌包含{}部分: {}", parts.length, String.join(", ", parts));
            
            if (parts.length >= 3) {
                // 标准格式: [hash]_[username]_[userId]_[role]_[timestamp]
                if (parts.length >= 5) {
                    String userId = parts[2];
                    String role = parts[3];
                    tokenInfo.setId(userId);
                    tokenInfo.setRole(role);
                    logger.debug("从JWT令牌提取完整信息: userId={}, role={}", userId, role);
                } else if (username != null) {
                    // 尝试从用户服务获取信息
                    User user = userService.findByUsername(username);
                    if (user != null) {
                        tokenInfo.setId(user.getId());
                        tokenInfo.setRole(user.getRole());
                        logger.debug("从用户服务获取信息: id={}, role={}", user.getId(), user.getRole());
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("解析令牌信息时出现错误: {}", e.getMessage());
        }
        
        return tokenInfo;
    }
    
    private String extractUserIdFromToken(String token) {
        try {
            String[] parts = token.split("_");
            if (parts.length >= 5) {
                // 标准格式：[hash]_[username]_[userId]_[role]_[timestamp]
                return parts[2]; 
            } else {
                // 尝试通过用户名查找
                String username = extractUsernameFromToken(token);
                if (username != null) {
                    User user = userService.findByUsername(username);
                    if (user != null) {
                        return user.getId();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("提取用户ID失败: {}", e.getMessage());
        }
        return null;
    }
    
    private String extractRoleFromToken(String token) {
        try {
            String[] parts = token.split("_");
            if (parts.length >= 5) {
                // 标准格式：[hash]_[username]_[userId]_[role]_[timestamp]
                return parts[3];
            } else {
                // 尝试通过用户名查找
                String username = extractUsernameFromToken(token);
                if (username != null) {
                    User user = userService.findByUsername(username);
                    if (user != null) {
                        return user.getRole();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("提取角色失败: {}", e.getMessage());
        }
        return "user"; // 默认角色
    }
} 