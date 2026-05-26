package com.traffic.analysis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

/**
 * 自定义安全配置，解决分号导致的URL请求被拒绝问题
 */
@Configuration
@Slf4j
public class CustomSecurityConfig {

    /**
     * 自定义HttpFirewall，允许URL中包含分号
     * 解决jsessionid带来的重定向循环问题
     */
    @Bean
    public HttpFirewall allowSemicolonHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        // 允许分号和jsessionid
        firewall.setAllowSemicolon(true);
        // 允许斜杠
        firewall.setAllowUrlEncodedSlash(true);
        // 允许反斜杠
        firewall.setAllowBackSlash(true);
        // 允许百分号
        firewall.setAllowUrlEncodedPercent(true);
        log.info("配置自定义HttpFirewall，允许URL中包含分号");
        return firewall;
    }
    
    /**
     * 配置Web安全，使用自定义的HttpFirewall
     */
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> {
            web.httpFirewall(allowSemicolonHttpFirewall());
            log.info("已应用自定义HttpFirewall配置");
        };
    }
} 