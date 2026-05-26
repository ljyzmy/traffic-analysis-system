package com.traffic.analysis.config;

import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 服务器配置类，自定义Tomcat服务器参数
 */
@Configuration
public class ServerConfig {

    /**
     * 自定义Tomcat服务器，以处理大文件上传和下载
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return (factory) -> {
            factory.addConnectorCustomizers(connector -> {
                if (connector.getProtocolHandler() instanceof AbstractHttp11Protocol) {
                    // 设置最大连接数
                    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setMaxConnections(200);
                    
                    // 设置连接超时（毫秒）
                    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setConnectionTimeout(30000);
                    
                    // 启用保持活动连接
                    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setKeepAliveTimeout(60000);
                    
                    // 设置压缩，有助于减少响应大小
                    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setCompression("on");
                    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setCompressionMinSize(2048);
                    
                    // 设置缓冲区大小
                    ((AbstractHttp11Protocol<?>) connector.getProtocolHandler()).setMaxSwallowSize(-1);
                    
                    // 禁用Nagle算法，提高实时响应
                    connector.setProperty("socket.txBufSize", "8192");
                    connector.setProperty("socket.rxBufSize", "8192");
                    connector.setProperty("socket.tcpNoDelay", "true");
                }
            });
        };
    }
} 