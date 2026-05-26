package com.traffic.analysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.context.annotation.Primary;

/**
 * 交通视频分析系统主程序入口
 */
@SpringBootApplication
@ServletComponentScan
@ComponentScan(
    basePackages = {"com.traffic.analysis"},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.user\\.trafficsystem\\..*")
    }
)
public class TrafficAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrafficAnalysisApplication.class, args);
    }

    @Bean
    public WebMvcConfigurer appCorsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                       .allowedOrigins("http://10.100.178.94:5173", "http://localhost:8080", "http://localhost:5173", "http://localhost:5000", "http://localhost:5001")
                       .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                       .allowedHeaders("*")
                       .exposedHeaders("Authorization")
                       .allowCredentials(true);
            }
        };
    }
    
    /**
     * 确保CSRF被禁用的主过滤链
     */
    @Bean
    @Primary
    public SecurityFilterChain primarySecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        
        System.out.println("应用级别的CSRF保护已明确禁用!");
        return http.build();
    }
} 