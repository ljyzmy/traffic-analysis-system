package com.traffic.analysis.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 响应头过滤器，确保不会同时设置Transfer-Encoding和Content-Length头
 * 解决ERR_INCOMPLETE_CHUNKED_ENCODING错误
 */
@Component
public class ResponseHeaderFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ResponseHeaderFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            SafeResponseWrapper responseWrapper = new SafeResponseWrapper(httpResponse);
            
            try {
                // 继续过滤链，使用包装的响应
                chain.doFilter(request, responseWrapper);
            } catch (Exception e) {
                // 记录异常但允许正常处理
                log.error("过滤器处理时发生异常", e);
                throw e;
            }
        } else {
            // 如果不是HTTP响应，直接继续
            chain.doFilter(request, response);
        }
    }
    
    /**
     * 安全的响应包装器，处理响应头冲突
     */
    private static class SafeResponseWrapper extends HttpServletResponseWrapper {
        
        private final Logger log = LoggerFactory.getLogger(SafeResponseWrapper.class);
        
        public SafeResponseWrapper(HttpServletResponse response) {
            super(response);
        }
        
        @Override
        public void setHeader(String name, String value) {
            // 如果要设置Content-Length，但已经有Transfer-Encoding头，则跳过
            if ("Content-Length".equalsIgnoreCase(name) && 
                "chunked".equalsIgnoreCase(getHeader("Transfer-Encoding"))) {
                log.debug("跳过设置Content-Length，因为已经设置了Transfer-Encoding: chunked");
                return;
            }
            
            // 如果要设置Transfer-Encoding为chunked，则确保没有Content-Length头
            if ("Transfer-Encoding".equalsIgnoreCase(name) && 
                "chunked".equalsIgnoreCase(value) && 
                getHeader("Content-Length") != null) {
                super.setHeader("Content-Length", null);
                log.debug("设置Transfer-Encoding: chunked，并移除Content-Length头");
            }
            
            super.setHeader(name, value);
        }
        
        @Override
        public void setContentLength(int len) {
            // 如果已经设置了Transfer-Encoding: chunked，则不设置Content-Length
            if ("chunked".equalsIgnoreCase(getHeader("Transfer-Encoding"))) {
                log.debug("跳过设置Content-Length: {}，因为已经设置了Transfer-Encoding: chunked", len);
                return;
            }
            super.setContentLength(len);
        }
        
        @Override
        public void setContentLengthLong(long len) {
            // 如果已经设置了Transfer-Encoding: chunked，则不设置Content-Length
            if ("chunked".equalsIgnoreCase(getHeader("Transfer-Encoding"))) {
                log.debug("跳过设置Content-Length: {}，因为已经设置了Transfer-Encoding: chunked", len);
                return;
            }
            super.setContentLengthLong(len);
        }
        
        @Override
        public void addHeader(String name, String value) {
            // 如果要添加Content-Length，但已经有Transfer-Encoding头，则跳过
            if ("Content-Length".equalsIgnoreCase(name) && 
                "chunked".equalsIgnoreCase(getHeader("Transfer-Encoding"))) {
                log.debug("跳过添加Content-Length，因为已经设置了Transfer-Encoding: chunked");
                return;
            }
            
            // 如果要添加Transfer-Encoding为chunked，则确保没有Content-Length头
            if ("Transfer-Encoding".equalsIgnoreCase(name) && 
                "chunked".equalsIgnoreCase(value) && 
                getHeader("Content-Length") != null) {
                super.setHeader("Content-Length", null);
                log.debug("添加Transfer-Encoding: chunked，并移除Content-Length头");
            }
            
            super.addHeader(name, value);
        }
    }
} 