package com.traffic.analysis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WebSocket配置
 * 提供视频分析进度的实时推送功能
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        logger.info("注册WebSocket端点");
        // 注册WebSocket端点
        registry.addEndpoint("/api/ws")
                .setAllowedOrigins("http://localhost:5173")
                .withSockJS();
        
        // 特别添加视频进度WebSocket端点
        registry.addEndpoint("/api/ws/video-progress/{taskId}")
                .setAllowedOrigins("http://localhost:5173")
                .withSockJS();
    }
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        logger.info("配置WebSocket消息代理");
        // 启用简单代理，客户端可以订阅这些目标
        config.enableSimpleBroker("/topic");
        
        // 设置应用程序目标前缀
        config.setApplicationDestinationPrefixes("/app");
    }
} 