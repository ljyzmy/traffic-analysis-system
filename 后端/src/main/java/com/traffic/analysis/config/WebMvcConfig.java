package com.traffic.analysis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.util.UrlPathHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Web MVC 配置类
 */
@Configuration
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 添加css、js、fonts等资源的直接映射
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));
                
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));
                
        registry.addResourceHandler("/fonts/**")
                .addResourceLocations("classpath:/static/fonts/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));
                
        // 修改images映射，增加对外部文件系统路径的支持
        registry.addResourceHandler("/images/**")
                .addResourceLocations(
                    "classpath:/static/images/",        // 类路径中的图像
                    "file:static/images/",              // 当前目录下的static/images
                    "file:./static/images/",            // 当前目录下的static/images(另一种表达)
                    "file:src/main/resources/static/images/", // 源代码目录下的图像
                    "file:traffic-web/static/images/",  // 项目子目录下的图像
                    "file:traffic-web/src/main/resources/static/images/"  // 源代码子目录下的图像
                )
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));
        
        // 保留原有的static映射
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.maxAge(1, TimeUnit.HOURS));
        
        // 特别添加SVG文件的处理
        registry.addResourceHandler("/static/images/*.svg")
                .addResourceLocations("classpath:/static/images/")
                .setCachePeriod(3600)
                .resourceChain(true);

        // 特别添加SVG文件处理，直接放在根路径
        registry.addResourceHandler("/*.svg")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600)
                .resourceChain(true);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index");
        registry.addViewController("/error").setViewName("error");
    }

    /**
     * 配置路径匹配
     * 解决带前缀API请求的问题
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        UrlPathHelper urlPathHelper = new UrlPathHelper();
        // 不移除分号内容，解决矩阵变量问题
        urlPathHelper.setRemoveSemicolonContent(false);
        configurer.setUrlPathHelper(urlPathHelper);
        
        log.info("配置Web MVC路径匹配器，支持分号和矩阵变量");
    }
} 