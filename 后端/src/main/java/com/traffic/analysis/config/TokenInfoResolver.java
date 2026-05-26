package com.traffic.analysis.config;

import com.traffic.analysis.model.TokenInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * TokenInfo参数解析器
 * 用于从HTTP请求中解析出TokenInfo对象并注入到Controller方法的@ModelAttribute TokenInfo参数中
 */
@Component
public class TokenInfoResolver implements HandlerMethodArgumentResolver {

    private static final Logger logger = LoggerFactory.getLogger(TokenInfoResolver.class);

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(TokenInfo.class) &&
                parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.ModelAttribute.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                 NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            logger.warn("请求对象为空");
            return new TokenInfo();
        }

        // 从Authorization头中获取令牌
        String authHeader = request.getHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            logger.debug("未找到有效的Authorization头");
            return new TokenInfo();
        }

        String token = authHeader.substring(7);
        logger.debug("解析JWT令牌: {}", token);

        try {
            // 解析令牌部分
            String[] parts = token.split("_");
            
            // 输出调试信息
            logger.debug("令牌部分数量: {}", parts.length);
            for (int i = 0; i < parts.length; i++) {
                logger.debug("令牌部分[{}]: {}", i, parts[i]);
            }
            
            if (parts.length >= 5) {
                // 标准格式: [hash]_[username]_[userId]_[role]_[timestamp]
                String username = parts[1];
                String userId = parts[2];
                String role = parts[3];
                
                logger.debug("从完整令牌中提取用户信息: username={}, userId={}, role={}", 
                        username, userId, role);
                
                return new TokenInfo(userId, username, role);
            } else if (parts.length >= 3) {
                // 前端简化格式: [hash]_[username]_[timestamp]
                String username = parts[1];
                String userId = username; // 使用用户名作为用户ID的默认值
                String role = "user";     // 默认角色为普通用户
                
                logger.debug("从简化令牌中提取用户信息: username={}, userId={}, role={}", 
                        username, userId, role);
                
                return new TokenInfo(userId, username, role);
            } else {
                logger.warn("令牌格式不正确，部分数量={}", parts.length);
            }
        } catch (Exception e) {
            logger.error("解析令牌失败", e);
        }
        
        logger.warn("返回空的TokenInfo对象");
        return new TokenInfo();
    }
} 