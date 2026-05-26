package com.traffic.analysis.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 诊断控制器，用于调试认证和CSRF问题
 */
@RestController
@RequestMapping("/api/diagnostics")
@CrossOrigin(
    origins = {
        "http://localhost:8081",
        "http://localhost:8080",
        "http://localhost:5173",
        "http://localhost:5000",
        "http://localhost:5001"
    }, 
    allowedHeaders = {"*"},
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS},
    allowCredentials = "true"
)
public class DiagnosticsController {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsController.class);

    @GetMapping("/auth-info")
    public ResponseEntity<?> getAuthInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> info = new HashMap<>();
        
        if (auth != null) {
            info.put("authenticated", auth.isAuthenticated());
            info.put("principal", auth.getPrincipal().toString());
            info.put("name", auth.getName());
            info.put("authorities", auth.getAuthorities());
            info.put("details", auth.getDetails() != null ? auth.getDetails().toString() : null);
            
            log.info("认证信息: {}", info);
        } else {
            info.put("authenticated", false);
            info.put("message", "No authentication found");
            
            log.warn("未找到认证信息");
        }
        
        return ResponseEntity.ok(info);
    }
    
    @GetMapping("/csrf")
    public ResponseEntity<?> getCsrfToken(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        Map<String, Object> response = new HashMap<>();
        
        if (token != null) {
            response.put("token", token.getToken());
            response.put("headerName", token.getHeaderName());
            response.put("parameterName", token.getParameterName());
            
            log.info("CSRF令牌信息: token={}, headerName={}, paramName={}", 
                   token.getToken(), token.getHeaderName(), token.getParameterName());
        } else {
            response.put("token", null);
            response.put("message", "CSRF protection might be disabled or not properly configured");
            
            log.warn("无法获取CSRF令牌，CSRF保护可能已禁用");
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/request-info")
    public ResponseEntity<?> getRequestInfo(HttpServletRequest request) {
        Map<String, Object> info = new HashMap<>();
        
        // 基本请求信息
        info.put("method", request.getMethod());
        info.put("requestURI", request.getRequestURI());
        info.put("queryString", request.getQueryString());
        info.put("remoteAddr", request.getRemoteAddr());
        
        // 所有请求头
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = request.getHeader(name);
            
            // 敏感头信息可能需要部分隐藏
            if (name.equalsIgnoreCase("authorization") || name.equalsIgnoreCase("cookie")) {
                value = value != null ? value.substring(0, Math.min(value.length(), 10)) + "..." : null;
            }
            
            headers.put(name, value);
        }
        info.put("headers", headers);
        
        // 所有Cookie
        Cookie[] cookies = request.getCookies();
        Map<String, String> cookieMap = new HashMap<>();
        
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                // 不显示敏感Cookie的实际值
                String value = cookie.getValue();
                if (cookie.getName().toLowerCase().contains("token") || 
                    cookie.getName().toLowerCase().contains("session") ||
                    cookie.getName().toLowerCase().contains("csrf")) {
                    value = value.substring(0, Math.min(value.length(), 10)) + "...";
                }
                
                cookieMap.put(cookie.getName(), value);
            }
        }
        info.put("cookies", cookieMap);
        
        // 请求参数
        Map<String, String[]> parameterMap = request.getParameterMap();
        info.put("parameters", parameterMap);
        
        log.info("请求诊断信息: method={}, uri={}, remoteAddr={}", 
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        
        return ResponseEntity.ok(info);
    }
    
    @PostMapping("/test-post")
    public ResponseEntity<?> testPost(@RequestBody(required = false) Map<String, Object> body, 
                                     HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "POST请求成功接收");
        response.put("receivedData", body != null ? body : "No body");
        
        // 获取CSRF令牌信息
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            response.put("csrfHeader", token.getHeaderName());
            response.put("receivedCsrfToken", request.getHeader(token.getHeaderName()));
            response.put("expectedCsrfToken", token.getToken());
            
            boolean tokenValid = token.getToken().equals(request.getHeader(token.getHeaderName()));
            response.put("csrfValid", tokenValid);
            
            log.info("POST请求CSRF验证: {}, 接收到的令牌: {}", 
                    tokenValid ? "有效" : "无效", 
                    request.getHeader(token.getHeaderName()));
        } else {
            response.put("csrfStatus", "CSRF保护已禁用或未正确配置");
        }
        
        log.info("测试POST请求成功接收");
        return ResponseEntity.ok(response);
    }
} 