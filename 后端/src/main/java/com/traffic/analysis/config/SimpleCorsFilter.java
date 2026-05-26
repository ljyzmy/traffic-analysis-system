package com.traffic.analysis.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// 暂时禁用此过滤器，避免与其他CORS配置冲突
// @Component
// @Order(Ordered.HIGHEST_PRECEDENCE)
public class SimpleCorsFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(SimpleCorsFilter.class);
    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
        "http://localhost:8081",
        "http://localhost:8080",
        "http://localhost:5173",
        "http://localhost:5000",
        "http://localhost:5001"
    );

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;
        
        // 记录请求信息，帮助调试
        logger.debug("处理CORS请求: {} {}", request.getMethod(), request.getRequestURI());
        logger.debug("Origin: {}", request.getHeader("Origin"));
        
        // 设置允许的源
        String origin = request.getHeader("Origin");
        if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        } else if (origin != null) {
            logger.warn("不允许的来源: {}", origin);
        }
        
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "x-requested-with, authorization, Content-Type, Authorization, credential, X-XSRF-TOKEN");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Expose-Headers", "Authorization");

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            logger.debug("处理预检(OPTIONS)请求");
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            chain.doFilter(req, res);
        }
    }

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) {
        // 初始化方法
    }

    @Override
    public void destroy() {
        // 销毁方法
    }
} 