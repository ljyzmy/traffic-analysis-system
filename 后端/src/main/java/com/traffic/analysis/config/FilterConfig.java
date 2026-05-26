package com.traffic.analysis.config;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class FilterConfig {

    /**
     * 已禁用以避免与WebSecurityConfig中的CORS配置冲突
     */
    /*
    @Bean
    public FilterRegistrationBean<Filter> corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // 明确指定允许的源
        config.addAllowedOrigin("http://localhost:8081");
        config.addAllowedOrigin("http://localhost:8080");
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedOrigin("http://localhost:5000");
        config.addAllowedOrigin("http://localhost:5001");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        source.registerCorsConfiguration("/**", config);
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
    */

    @Bean
    public FilterRegistrationBean<ResponseHeaderFilter> responseHeaderFilter() {
        FilterRegistrationBean<ResponseHeaderFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ResponseHeaderFilter());
        registrationBean.addUrlPatterns("/result/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registrationBean.setName("responseHeaderFilter");
        return registrationBean;
    }
} 