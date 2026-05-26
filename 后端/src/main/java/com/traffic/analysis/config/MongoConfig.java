package com.traffic.analysis.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

@Configuration
public class MongoConfig {
    
    @Value("${spring.data.mongodb.host}")
    private String host;
    
    @Value("${spring.data.mongodb.port}")
    private int port;
    
    @Value("${spring.data.mongodb.database}")
    private String database;
    
    @Value("${spring.data.mongodb.username}")
    private String username;
    
    @Value("${spring.data.mongodb.password}")
    private String password;
    
    @Value("${spring.data.mongodb.authentication-database}")
    private String authenticationDatabase;
    
    @Bean
    public MongoClient mongoClient() {
        MongoCredential credential = MongoCredential.createCredential(
            username, 
            authenticationDatabase, 
            password.toCharArray()
        );
        
        MongoClientSettings settings = MongoClientSettings.builder()
            .credential(credential)
            .applyToClusterSettings(builder -> 
                builder.hosts(java.util.Arrays.asList(new ServerAddress(host, port))))
            .build();
            
        return MongoClients.create(settings);
    }
    
    @Bean
    public MongoTemplate mongoTemplate(MongoClient mongoClient) {
        return new MongoTemplate(mongoClient, database);
    }
} 