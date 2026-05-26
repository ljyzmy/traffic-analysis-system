package com.traffic.analysis.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Controller
public class MyErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(MyErrorController.class);

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        // 获取错误信息
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object exception = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object requestURI = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        
        log.error("发生错误: 状态码={}, 异常={}, 消息={}, URI={}", 
                status, exception, message, requestURI);
        
        // 记录所有属性，帮助诊断问题
        Enumeration<String> attributeNames = request.getAttributeNames();
        Map<String, Object> attributes = new HashMap<>();
        while (attributeNames.hasMoreElements()) {
            String name = attributeNames.nextElement();
            Object value = request.getAttribute(name);
            attributes.put(name, value);
            log.info("请求属性: {}={}", name, value);
        }
        
        // 在模型中添加错误信息
        model.addAttribute("status", status);
        model.addAttribute("error", exception != null ? exception.toString() : "未知错误");
        model.addAttribute("message", message);
        model.addAttribute("path", requestURI);
        model.addAttribute("attributes", attributes);
        
        // 返回错误页面
        return "error";
    }
} 