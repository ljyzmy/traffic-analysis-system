package com.traffic.analysis.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 组件扫描配置
 * 确保traffic-web目录下的组件也被正确扫描
 */
@Configuration
@ComponentScan({
    "com.traffic.analysis.controller",
    "com.traffic.analysis.service",
    "com.traffic.analysis.config"
})
public class ComponentScanConfig {
    // 此配置类确保Spring能够扫描到traffic-web目录下的各种组件
} 