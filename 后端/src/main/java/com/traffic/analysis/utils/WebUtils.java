package com.traffic.analysis.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;

/**
 * Web工具类，用于处理HTTP请求相关的常用操作
 */
public class WebUtils {
    
    /**
     * 从请求中获取用户ID
     * 首先尝试从安全上下文中获取认证信息，如果可用则返回用户名作为用户ID
     * 如果没有认证信息，则尝试从请求头或会话中获取用户ID
     *
     * @param request HTTP请求
     * @return 用户ID，如果无法获取则返回"anonymous"
     */
    public static String getUserId(HttpServletRequest request) {
        // 导入日志记录器
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WebUtils.class);
        
        // 首先尝试从Spring Security上下文中获取
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && 
            !authentication.getPrincipal().equals("anonymousUser")) {
            String userId = authentication.getName();
            logger.debug("从Security Context获取到用户ID: {}", userId);
            return userId;
        } else if (authentication != null) {
            logger.debug("Security Context存在但不满足条件: authenticated={}, principal={}", 
                    authentication.isAuthenticated(), authentication.getPrincipal());
        } else {
            logger.debug("Security Context认证信息为空");
        }
        
        // 尝试从请求头中获取
        String userId = request.getHeader("X-User-Id");
        if (StringUtils.hasText(userId)) {
            logger.debug("从请求头X-User-Id获取到用户ID: {}", userId);
            return userId;
        }
        
        // 尝试从会话中获取
        if (request.getSession() != null) {
            // 尝试从session.user对象中获取
            Object userObj = request.getSession().getAttribute("user");
            if (userObj != null) {
                try {
                    // 反射获取ID属性
                    java.lang.reflect.Method getIdMethod = userObj.getClass().getMethod("getId");
                    Object idObj = getIdMethod.invoke(userObj);
                    if (idObj != null) {
                        logger.debug("从会话user对象获取到用户ID: {}", idObj);
                        return idObj.toString();
                    }
                } catch (Exception e) {
                    logger.debug("尝试从user对象提取ID失败: {}", e.getMessage());
                }
                
                // 如果reflection失败，尝试将整个对象的toString作为ID
                logger.debug("使用用户对象的字符串表示: {}", userObj);
                return userObj.toString();
            }
            
            // 直接获取userId属性
            if (request.getSession().getAttribute("userId") != null) {
                userId = request.getSession().getAttribute("userId").toString();
                logger.debug("从会话userId属性获取到: {}", userId);
                return userId;
            }
            
            logger.debug("会话中没有找到用户信息");
        } else {
            logger.debug("请求会话为空");
        }
        
        // 默认返回匿名用户
        logger.debug("无法获取用户ID，返回anonymous");
        return "anonymous";
    }
} 