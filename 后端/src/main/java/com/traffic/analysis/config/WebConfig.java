package com.traffic.analysis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.core.Ordered;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.Collections;

/**
 * Web配置类
 * 用于配置CORS、安全头等全局Web设置
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    /**
     * 配置HTTP防火墙
     */
    @Bean
    public StrictHttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowSemicolon(true);
        firewall.setAllowBackSlash(true);
        firewall.setAllowUrlEncodedPercent(true);
        return firewall;
    }
    
    /**
     * 配置HTTP安全头
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/**").permitAll()
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .addHeaderWriter((request, response) -> {
                    response.setHeader("X-Frame-Options", "SAMEORIGIN");
                    response.setHeader("Content-Security-Policy", 
                        "default-src 'self'; " +
                        "img-src 'self' data: blob: *; " +
                        "media-src 'self' blob: *; " +
                        "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                        "style-src 'self' 'unsafe-inline';"
                    );
                })
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加API路径修复拦截器
        registry.addInterceptor(new ApiPathFixInterceptor())
                .addPathPatterns("/api/api/**"); // 拦截重复API前缀的路径
    }

    /**
     * API路径修复拦截器，处理重复的API前缀
     */
    public static class ApiPathFixInterceptor implements HandlerInterceptor {
        
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String originalUri = request.getRequestURI();
            
            // 检测并修复重复的API前缀
            if (originalUri.startsWith("/api/api/")) {
                // 创建新的URI，移除一个api前缀
                String newUri = originalUri.replace("/api/api/", "/api/");
                String queryString = request.getQueryString();
                
                // 构建完整的重定向URL
                String redirectUrl = newUri + (queryString != null ? "?" + queryString : "");
                logger.info("检测到重复API前缀，重定向: {} -> {}", originalUri, redirectUrl);
                
                try {
                    // 重定向到修正后的URL
                    response.sendRedirect(redirectUrl);
                } catch (Exception e) {
                    logger.error("重定向失败: {}", e.getMessage());
                }
                return false; // 不继续处理请求
            }
            
            return true; // 继续正常处理请求
        }
    }
} 