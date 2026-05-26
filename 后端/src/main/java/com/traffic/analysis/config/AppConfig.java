package com.traffic.analysis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${model.api.url:http://localhost:5000}")
    private String modelApiUrl;
    
    @Value("${model.api.analyze.endpoint:/analyze}")
    private String modelAnalyzeEndpoint;
    
    @Value("${model.api.health.endpoint:/health}")
    private String modelHealthEndpoint;
    
    @Value("${upload.dir:${java.io.tmpdir}/traffic-analysis/uploads}")
    private String uploadDirectory;
    
    /**
     * 获取模型API的基础URL
     */
    public String getModelApiUrl() {
        return modelApiUrl;
    }
    
    /**
     * 获取分析接口的完整URL
     */
    public String getAnalyzeUrl() {
        return modelApiUrl + modelAnalyzeEndpoint;
    }
    
    /**
     * 获取健康检查接口的完整URL
     */
    public String getHealthCheckUrl() {
        return modelApiUrl + modelHealthEndpoint;
    }
    
    /**
     * 获取上传目录
     */
    public String getUploadDirectory() {
        return uploadDirectory;
    }
} 