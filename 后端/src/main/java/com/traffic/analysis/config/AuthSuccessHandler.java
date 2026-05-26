package com.traffic.analysis.config;

import com.traffic.analysis.model.User;
import com.traffic.analysis.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 认证成功处理器，用于在Spring Security认证成功后将用户信息存入HttpSession
 */
@Component
public class AuthSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    
    private static final Logger log = LoggerFactory.getLogger(AuthSuccessHandler.class);
    
    @Autowired
    private UserService userService;
    
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, 
                                       Authentication authentication) throws IOException, ServletException {
        
        String username = authentication.getName();
        log.info("用户登录成功，正在设置会话: {}", username);
        
        // 查询完整的用户信息
        User user = userService.findByUsername(username);
        if (user != null) {
            // 存入HttpSession
            HttpSession session = request.getSession();
            session.setAttribute("user", user);
            log.info("用户信息已存入会话: {}, role={}", username, user.getRole());
        } else {
            log.warn("无法获取完整的用户信息: {}", username);
        }
        
        // 继续默认的成功处理
        setDefaultTargetUrl("/index");
        setAlwaysUseDefaultTargetUrl(true);
        super.onAuthenticationSuccess(request, response, authentication);
    }
} 