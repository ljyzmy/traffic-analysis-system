package com.traffic.analysis.config;

import com.traffic.analysis.model.User;
import com.traffic.analysis.security.TokenInfo;
import com.traffic.analysis.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JWT 认证提供者 - 处理JWT令牌的认证
 */
@Component
public class JwtAuthenticationProvider implements AuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationProvider.class);

    @Autowired
    private UserService userService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        logger.debug("JwtAuthenticationProvider处理认证请求: {}", 
                authentication.getPrincipal());
        
        // 如果不是我们期望的认证类型或者已经认证过，直接返回
        if (!(authentication instanceof UsernamePasswordAuthenticationToken) || authentication.isAuthenticated()) {
            logger.debug("非目标认证类型或已认证，跳过");
            return authentication;
        }
        
        // 提取认证信息
        String username = authentication.getName();
        Object details = authentication.getDetails();
        TokenInfo tokenInfo = null;
        
        if (details instanceof TokenInfo) {
            tokenInfo = (TokenInfo) details;
            logger.info("从认证对象中提取TokenInfo: username={}, role={}", 
                    tokenInfo.getUsername(), tokenInfo.getRole());
        } else {
            logger.warn("认证对象中没有TokenInfo");
            return authentication; // 无法进一步处理
        }
        
        try {
            // 根据TokenInfo中的ID查询用户
            User user = null;
            if (tokenInfo.getId() != null) {
                user = userService.findById(tokenInfo.getId());
                if (user != null) {
                    logger.info("根据TokenInfo中的用户ID查询到用户: {}", user.getUsername());
                }
            }
            
            // 如果根据ID未找到用户，尝试根据用户名查询
            if (user == null && tokenInfo.getUsername() != null) {
                user = userService.findByUsername(tokenInfo.getUsername());
                if (user != null) {
                    logger.info("根据TokenInfo中的用户名查询到用户: {}", user.getUsername());
                }
            }
            
            // 最后尝试使用认证对象的用户名查询
            if (user == null && username != null) {
                user = userService.findByUsername(username);
                if (user != null) {
                    logger.info("根据认证对象的用户名查询到用户: {}", user.getUsername());
                }
            }
            
            // 如果找不到用户，维持原有认证
            if (user == null) {
                logger.warn("无法在数据库中找到对应用户");
                return authentication;
            }
            
            // 如果TokenInfo中没有角色信息，从用户中获取
            if (tokenInfo.getRole() == null || tokenInfo.getRole().isEmpty()) {
                tokenInfo.setRole(user.getRole());
                logger.info("从用户对象中获取并设置角色: {}", user.getRole());
            }
            
            // 创建权限列表
            List<GrantedAuthority> authorities = new ArrayList<>();
            String role = tokenInfo.getRole();
            
            // 确保角色名称前缀为ROLE_
            String roleAuthority = role;
            if (!roleAuthority.startsWith("ROLE_")) {
                roleAuthority = "ROLE_" + roleAuthority.toUpperCase();
            }
            authorities.add(new SimpleGrantedAuthority(roleAuthority));
            
            // 添加原始角色名称作为权限
            if (!Objects.equals(roleAuthority, role.toUpperCase())) {
                authorities.add(new SimpleGrantedAuthority(role.toUpperCase()));
            }
            
            // 添加基本用户权限
            authorities.add(new SimpleGrantedAuthority("USER"));
            
            logger.info("为用户{}创建认证对象，权限: {}", username, 
                    authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(java.util.stream.Collectors.joining(", ")));
            
            // 创建已认证的认证对象
            UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            
            // 保留原始TokenInfo
            authToken.setDetails(tokenInfo);
            
            return authToken;
        } catch (Exception e) {
            logger.error("处理JWT认证时出错: {}", e.getMessage(), e);
            return authentication; // 出错时保持原认证状态
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
} 