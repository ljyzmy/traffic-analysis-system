package com.traffic.analysis.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.nio.charset.StandardCharsets;

/**
 * RestTemplate配置类，设置连接超时和读取超时
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建自定义的RestTemplate Bean
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        // 创建自定义的HTTP请求工厂
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 设置连接超时（毫秒）- 10秒
        factory.setConnectTimeout(10000);
        
        // 设置读取超时（毫秒）- 30秒
        factory.setReadTimeout(30000);
        
        // 创建RestTemplate并使用自定义的工厂
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // 配置消息转换器
        configureMessageConverters(restTemplate);
        
        return restTemplate;
    }
    
    /**
     * 配置消息转换器，确保可以正确处理JSON响应
     */
    private void configureMessageConverters(RestTemplate restTemplate) {
        // 获取现有转换器
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        
        // 添加支持multipart/form-data的转换器
        FormHttpMessageConverter formHttpMessageConverter = new FormHttpMessageConverter();
        formHttpMessageConverter.addPartConverter(new ResourceHttpMessageConverter());
        formHttpMessageConverter.addPartConverter(new ByteArrayHttpMessageConverter());
        formHttpMessageConverter.addPartConverter(new StringHttpMessageConverter());
        
        // 设置表单转换器支持的媒体类型
        List<MediaType> formMediaTypes = new ArrayList<>();
        formMediaTypes.add(MediaType.MULTIPART_FORM_DATA);
        formMediaTypes.add(MediaType.APPLICATION_FORM_URLENCODED);
        formMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        formHttpMessageConverter.setSupportedMediaTypes(formMediaTypes);
        
        converters.add(formHttpMessageConverter);
        
        // 添加二进制数据转换器
        ByteArrayHttpMessageConverter byteArrayConverter = new ByteArrayHttpMessageConverter();
        List<MediaType> byteMediaTypes = new ArrayList<>();
        byteMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        byteMediaTypes.add(MediaType.IMAGE_JPEG);
        byteMediaTypes.add(MediaType.IMAGE_PNG);
        byteMediaTypes.add(MediaType.ALL);
        byteArrayConverter.setSupportedMediaTypes(byteMediaTypes);
        converters.add(byteArrayConverter);
        
        // 添加资源转换器
        converters.add(new ResourceHttpMessageConverter());
        
        // 设置UTF-8编码的字符串转换器
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false);
        List<MediaType> textMediaTypes = new ArrayList<>();
        textMediaTypes.add(MediaType.TEXT_PLAIN);
        textMediaTypes.add(MediaType.TEXT_HTML);
        textMediaTypes.add(MediaType.TEXT_XML);
        textMediaTypes.add(MediaType.ALL);
        stringConverter.setSupportedMediaTypes(textMediaTypes);
        converters.add(stringConverter);
        
        // 创建JSON转换器
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        
        // 设置支持的媒体类型
        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(MediaType.APPLICATION_JSON);
        supportedMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        supportedMediaTypes.add(new MediaType("application", "*+json"));
        supportedMediaTypes.add(MediaType.TEXT_PLAIN); // 有些API可能返回text/plain但内容是JSON
        supportedMediaTypes.add(MediaType.ALL); // 支持任何媒体类型，确保最大兼容性
        jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
        converters.add(jsonConverter);
        
        // 设置所有转换器
        restTemplate.setMessageConverters(converters);
    }
} 