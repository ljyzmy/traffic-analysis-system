package com.traffic.analysis.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import com.mongodb.client.MongoClient;
import org.bson.Document;

/**
 * 数据库控制器
 */
@RestController
@Slf4j
public class DatabaseController {

    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Value("${spring.data.mongodb.host:localhost}")
    private String mongoHost;
    
    @Value("${spring.data.mongodb.port:27017}")
    private int mongoPort;

    /**
     * 检查数据库状态 - 使用直接的网络连接测试
     */
    @GetMapping("/api/status/database")
    public ResponseEntity<Map<String, Object>> checkDatabaseStatus() {
        Map<String, Object> response = new HashMap<>();
        
        // 1. 使用Socket直接检测端口是否可连接
        boolean networkConnected = isPortOpen(mongoHost, mongoPort, 1000);
        
        // 2. 如果网络连接成功，尝试执行数据库操作
        boolean querySuccessful = false;
        String errorMessage = null;
        
        if (networkConnected) {
            try {
                // 尝试执行一个简单但有效的数据库查询
                Document pingResult = mongoTemplate.getDb()
                    .runCommand(new Document("ping", 1)
                    .append("maxTimeMS", 500)); // 500ms超时
                
                // 确认响应成功
                if (pingResult != null && Double.valueOf(1.0).equals(pingResult.get("ok"))) {
                    // 尝试执行另一个操作以进一步确认
                    mongoTemplate.getDb().listCollectionNames().first();
                    querySuccessful = true;
                }
            } catch (Exception e) {
                log.error("数据库查询失败: {}", e.getMessage());
                errorMessage = e.getMessage();
                querySuccessful = false;
            }
        }
        
        // 结合网络连接和查询结果判断数据库状态
        boolean isOnline = networkConnected && querySuccessful;
        
        // 构建响应
        response.put("status", isOnline ? "online" : "offline");
        response.put("db_status", isOnline ? "online" : "offline");
        response.put("network_connected", networkConnected);
        response.put("query_successful", querySuccessful);
        
        if (!isOnline) {
            String reason = !networkConnected ? 
                "无法连接到数据库服务器" : 
                "数据库服务器可连接但查询失败: " + errorMessage;
            
            log.warn("数据库离线: {}", reason);
            response.put("error", reason);
        } else {
            log.debug("数据库在线");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 使用Socket连接测试端口是否开放
     */
    private boolean isPortOpen(String host, int port, int timeout) {
        Socket socket = null;
        try {
            log.debug("开始检测MongoDB网络连接: {}:{}", host, port);
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeout);
            log.debug("MongoDB网络连接成功");
            return true;
        } catch (SocketTimeoutException e) {
            log.warn("MongoDB网络连接超时: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            log.warn("MongoDB网络连接失败: {}", e.getMessage());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    log.error("关闭Socket异常", e);
                }
            }
        }
    }
} 