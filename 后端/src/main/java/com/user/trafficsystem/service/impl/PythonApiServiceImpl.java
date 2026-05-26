package com.user.trafficsystem.service.impl;

import com.user.trafficsystem.service.PythonApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Python API服务实现类
 */
@Service
public class PythonApiServiceImpl implements PythonApiService {

    private static final Logger logger = LoggerFactory.getLogger(PythonApiServiceImpl.class);
    
    private final RestTemplate restTemplate;
    
    @Value("${python.api.url}")
    private String pythonApiUrl;
    
    @Value("${python.api.history.endpoint}")
    private String historyEndpoint;
    
    @Value("${python.api.history-stats.endpoint}")
    private String historyStatsEndpoint;
    
    @Value("${python.api.history-save.endpoint}")
    private String historySaveEndpoint;
    
    public PythonApiServiceImpl() {
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public Map<String, Object> getHistory(int limit, int skip, String token) {
        try {
            // 构建URL并添加查询参数
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(pythonApiUrl + historyEndpoint)
                    .queryParam("limit", limit)
                    .queryParam("skip", skip);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", "Bearer " + token);
            }
            
            // 发送请求
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            // 处理响应
            logger.info("从Python API获取历史记录成功: {}", response.getStatusCode());
            return response.getBody();
            
        } catch (Exception e) {
            logger.error("从Python API获取历史记录失败", e);
            
            // 返回错误响应
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取历史记录失败: " + e.getMessage());
            errorResponse.put("results", new Object[0]);
            errorResponse.put("total", 0);
            return errorResponse;
        }
    }
    
    @Override
    public Map<String, Object> getHistoryStats(String token) {
        try {
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", "Bearer " + token);
            }
            
            // 发送请求
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    pythonApiUrl + historyStatsEndpoint,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            // 处理响应
            logger.info("从Python API获取统计数据成功: {}", response.getStatusCode());
            return response.getBody();
            
        } catch (Exception e) {
            logger.error("从Python API获取统计数据失败", e);
            
            // 返回错误响应
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取统计数据失败: " + e.getMessage());
            errorResponse.put("api_status", "online");
            errorResponse.put("db_status", "unknown");
            errorResponse.put("model_status", "offline");
            return errorResponse;
        }
    }
    
    @Override
    public Map<String, Object> saveAnalysisResult(Map<String, Object> analysisData, String token) {
        try {
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", "Bearer " + token);
            }
            
            // 发送请求
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(analysisData, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    pythonApiUrl + historySaveEndpoint,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );
            
            // 处理响应
            logger.info("保存分析结果到Python API成功: {}", response.getStatusCode());
            return response.getBody();
            
        } catch (Exception e) {
            logger.error("保存分析结果到Python API失败", e);
            
            // 返回错误响应
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "保存分析结果失败: " + e.getMessage());
            return errorResponse;
        }
    }
    
    @Override
    public Map<String, Object> checkApiStatus() {
        try {
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 发送请求
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    pythonApiUrl + "/status",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            // 处理响应
            logger.info("检查Python API状态成功: {}", response.getStatusCode());
            return response.getBody();
            
        } catch (Exception e) {
            logger.error("检查Python API状态失败", e);
            
            // 返回错误响应
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "success");
            errorResponse.put("api_status", "offline");
            errorResponse.put("db_status", "unknown");
            errorResponse.put("model_status", "unknown");
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }
} 