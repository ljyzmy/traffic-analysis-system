package com.user.trafficsystem.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.user.trafficsystem.service.PythonApiService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * API状态控制器，处理/api/status请求
 */
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5000", "http://localhost:5001", "http://localhost:8080"}, allowCredentials = "true", maxAge = 3600)
@RestController
@RequestMapping("/api/status")
public class ApiStatusController {

    @Autowired
    private PythonApiService pythonApiService;

    /**
     * 检查API状态
     */
    @CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5000", "http://localhost:5001", "http://localhost:8080"})
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> checkApiStatus() {
        System.out.println("API状态控制器: GET /api/status");
        
        Map<String, Object> status = new HashMap<>();
        status.put("status", "online");
        status.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
        status.put("version", "1.0.0");
        
        return ResponseEntity.ok(status);
    }
} 