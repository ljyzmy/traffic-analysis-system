package com.traffic.analysis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

/**
 * GridFS配置类
 * 用于配置MongoDB的GridFS文件存储功能
 */
@Configuration
public class GridFsConfig {
    
    @Bean
    public GridFsTemplate gridFsTemplate(MongoTemplate mongoTemplate) {
        return new GridFsTemplate(mongoTemplate.getMongoDatabaseFactory(), mongoTemplate.getConverter());
    }
} 