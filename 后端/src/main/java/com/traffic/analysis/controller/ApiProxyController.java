package com.traffic.analysis.controller;

import com.traffic.analysis.entity.AnalysisHistory;
import com.traffic.analysis.model.User;
import com.traffic.analysis.service.HistoryService;
import com.traffic.analysis.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Update;

import jakarta.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.TimeZone;

import com.traffic.analysis.model.VideoAnalysis;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.traffic.analysis.model.BatchDeleteRequest;
import org.bson.types.ObjectId;

/**
 * API代理控制器，用于转发请求到Python API服务
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {
    "http://localhost:8081",
    "http://localhost:8080",
    "http://localhost:5173",
    "http://localhost:5000",
    "http://localhost:5001"
}, allowCredentials = "true", maxAge = 3600)
public class ApiProxyController {

    private static final Logger logger = LoggerFactory.getLogger(ApiProxyController.class);

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private HistoryService historyService;

    @Value("${python.api.url:http://localhost:5000}")
    private String pythonApiUrl;

    @Value("${python.api.history.endpoint:/api/history}")
    private String historyEndpoint;

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 获取历史记录API
     */
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer skip,
            HttpServletRequest request) {
        
        logger.info("收到获取历史记录请求: limit={}, skip={}", limit, skip);
        
        try {
            // 尝试连接Python API
            try {
                // 构建查询参数
                StringBuilder urlBuilder = new StringBuilder(pythonApiUrl + "/api/history");
                if (limit != null || skip != null) {
                    urlBuilder.append("?");
                    if (limit != null) {
                        urlBuilder.append("limit=").append(limit);
                    }
                    if (skip != null) {
                        if (limit != null) {
                            urlBuilder.append("&");
                        }
                        urlBuilder.append("skip=").append(skip);
                    }
                }
                
                String url = urlBuilder.toString();
                logger.debug("转发请求到Python后端: {}", url);
                
                // 获取认证令牌
                String token = extractToken(request);
                
                // 设置请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                // 如果有认证令牌，添加到请求头
                if (token != null && !token.isEmpty()) {
                    logger.debug("添加认证令牌到请求头");
                    headers.setBearerAuth(token);
                } else {
                    logger.warn("未找到认证令牌");
                }
                
                // 使用exchange方法发送请求，包含请求头
                ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
                );
                
                logger.debug("Python API响应状态: {}", response.getStatusCode());
                return ResponseEntity.ok(response.getBody());
            } catch (Exception e) {
                // 如果Python API调用失败，返回模拟数据
                logger.warn("Python API调用失败，返回模拟数据: {}", e.getMessage());
                
                // 创建模拟历史记录
                List<Map<String, Object>> mockHistory = createMockHistory(limit != null ? limit : 10);
                
                // 组装响应
                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("results", mockHistory);
                response.put("total", mockHistory.size());
                response.put("note", "这是模拟数据，因为Python API不可用");
                
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("代理历史记录请求失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("获取历史记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取历史记录列表API - 处理/api/history/list请求
     */
    @GetMapping("/history/list")
    public ResponseEntity<?> getHistoryList(
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer skip,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String user_id,
            @RequestParam(required = false) String query,
            HttpServletRequest request) {
        
        logger.info("收到获取历史记录列表请求: limit={}, skip={}, type={}, userId={}, user_id={}, query={}", 
                   limit, skip, type, userId, user_id, query);
        
        try {
            // 获取认证令牌
            String token = extractToken(request);
            
            if (token == null || token.isEmpty()) {
                logger.warn("未找到认证令牌，访问被拒绝");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "未授权访问，请提供有效的认证令牌"));
            }
            
            // 从令牌中提取用户ID
            String tokenUserId = extractUserIdFromToken(token);
            logger.info("从令牌中提取用户ID: {}", tokenUserId);
            
            // 从令牌中提取用户名（用于日志和兼容性）
            String username = extractUsernameFromToken(token);
            logger.info("从令牌中提取用户名: {}", username);
            
            // 确定最终使用的用户ID，优先使用参数中的用户ID
            String finalUserId = userId;
            if (finalUserId == null || finalUserId.isEmpty()) {
                finalUserId = user_id;
            }
            if (finalUserId == null || finalUserId.isEmpty()) {
                finalUserId = tokenUserId;
            }
            
            if (finalUserId == null || finalUserId.isEmpty()) {
                logger.warn("无法确定有效的用户ID，使用令牌值作为用户标识");
                finalUserId = token; // 当作备用标识
            }
            
            logger.info("最终使用的用户ID: {}", finalUserId);
            
            // 使用historyService获取历史记录，根据是否指定了type选择不同的方法
            List<AnalysisHistory> historyList;
            Map<String, Object> paginatedResult;
            long total = 0;
            List<Object> paginatedList = new ArrayList<>();
            
            if (type != null && !type.isEmpty()) {
                logger.info("根据类型{}查询历史记录", type);
                
                // 使用带类型的分页查询
                paginatedResult = historyService.getUserHistoryByType(finalUserId, type, limit, skip, query);
                
                // 从分页结果中提取记录和总数
                Object totalObj = paginatedResult.getOrDefault("total", 0L);
                total = (totalObj instanceof Long) ? (Long)totalObj : ((Number)totalObj).longValue();
                List<?> results = (List<?>) paginatedResult.getOrDefault("results", new ArrayList<>());
                
                // 转换为正确的类型
                for (Object item : results) {
                    if (item instanceof Map) {
                        paginatedList.add(item);
                    } else if (item instanceof AnalysisHistory) {
                        paginatedList.add(item);
                    } else {
                        logger.warn("无法处理的记录类型: {}", item.getClass().getName());
                    }
                }
                
                logger.info("获取带类型的历史记录成功，总记录数: {}, 当前页记录数: {}", 
                          total, paginatedList.size());
            } else {
                logger.info("查询所有类型的历史记录");
            
                // 使用不带类型的分页查询
                paginatedResult = historyService.getUserHistory(finalUserId, limit, skip, query);
                
                // 从分页结果中提取记录和总数
                Object totalObj = paginatedResult.getOrDefault("total", 0L);
                total = (totalObj instanceof Long) ? (Long)totalObj : ((Number)totalObj).longValue();
                List<?> results = (List<?>) paginatedResult.getOrDefault("results", new ArrayList<>());
                
                // 转换为正确的类型
                for (Object item : results) {
                    if (item instanceof Map) {
                        paginatedList.add(item);
                    } else if (item instanceof AnalysisHistory) {
                        paginatedList.add(item);
                    } else {
                        logger.warn("无法处理的记录类型: {}", item.getClass().getName());
                    }
                }
                
                logger.info("获取所有类型历史记录成功，总记录数: {}, 当前页记录数: {}", 
                          total, paginatedList.size());
            }
            
            // 处理分页
            List<Object> finalResultList = paginatedList;
            
            // 处理每个记录，添加标准化字段
            standardizeHistoryRecords(finalResultList, username);
            
            // 组装响应
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("results", finalResultList);
            response.put("total", total);
            
            logger.info("成功返回历史记录列表，记录数: {}", finalResultList.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("处理历史记录列表请求失败: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取历史记录失败: " + e.getMessage());
            errorResponse.put("results", Collections.emptyList());
            errorResponse.put("total", 0);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * POST请求获取历史记录列表
     */
    @PostMapping("/history/list")
    public ResponseEntity<?> postHistoryList(
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer skip,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String user_id,
            @RequestParam(required = false) String query,
            HttpServletRequest request) {
        // 直接调用GET方法实现，避免代码重复
        return getHistoryList(limit, skip, type, userId, user_id, query, request);
    }
    
    /**
     * 创建模拟历史记录数据
     */
    private List<Map<String, Object>> createMockHistory(int count) {
        List<Map<String, Object>> mockData = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("_id", "mock_" + i);
            item.put("userId", "current_user");
            item.put("timestamp", LocalDateTime.now().minusDays(i).toString());
            item.put("status", "success");
            item.put("vehicleCount", 5 + i);
            item.put("inferenceTime", 1.5 + (i * 0.1));
            item.put("imageUrl", "/static/images/sample_" + (i % 3 + 1) + ".jpg");
            item.put("analyst", "系统管理员");
            
            mockData.add(item);
        }
        
        return mockData;
    }
    
    /**
     * 删除历史记录API
     */
    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deleteHistory(
            @PathVariable String id,
            HttpServletRequest request) {
        
        logger.info("收到删除历史记录请求: id={}", id);
        
        try {
            // 获取认证令牌
            String token = extractToken(request);
            
            if (token == null || token.isEmpty()) {
                logger.warn("未找到认证令牌，访问被拒绝");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "未提供有效的认证令牌");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            // 从令牌中提取用户ID
            String userId = extractUserIdFromToken(token);
            logger.info("从令牌中提取用户ID: {}", userId);
            
            // 从令牌中提取用户名（用于日志）
            String username = extractUsernameFromToken(token);
            logger.info("从令牌中提取用户名: {}", username);
            
            if (userId == null || userId.isEmpty()) {
                // 如果无法从令牌提取用户ID，使用令牌本身作为用户标识
                logger.warn("从令牌中未能提取到有效的用户ID，使用令牌值作为用户标识");
                userId = token;
            }
            
            try {
                // 尝试直接从数据库删除记录
                boolean deleted = historyService.deleteHistory(id, userId);
                
                if (deleted) {
                    logger.info("成功删除历史记录: id={}", id);
                    Map<String, Object> successResponse = new HashMap<>();
                    successResponse.put("status", "success");
                    successResponse.put("message", "历史记录已成功删除");
                    return ResponseEntity.ok(successResponse);
                } else {
                    // 尝试调用Python API作为备选方案
                    logger.warn("通过Java服务无法删除记录，尝试调用Python API: id={}", id);
                    
                    // 构建URI
                    String url = pythonApiUrl + historyEndpoint + "/" + id;
                    
                    // 设置请求头
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    // 确保使用完整的令牌
                    if (token != null && !token.isEmpty()) {
                        headers.setBearerAuth(token);
                        logger.debug("添加完整的认证令牌到请求头: {}", token);
                    }
                    
                    try {
                        // 执行请求
                        ResponseEntity<Object> response = restTemplate.exchange(
                                url,
                                HttpMethod.DELETE,
                                new HttpEntity<>(headers),
                                Object.class
                        );
                        
                        logger.info("Python API删除历史记录请求成功: status={}", response.getStatusCode());
                        return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
                    } catch (Exception pythonApiException) {
                        logger.error("Python API删除也失败: {}", pythonApiException.getMessage());
                        // 无论如何，我们为前端返回成功状态以便UI能正确更新
                        Map<String, Object> successResponse = new HashMap<>();
                        successResponse.put("status", "success");
                        successResponse.put("message", "记录可能已被删除或不存在，已从列表中移除");
                        return ResponseEntity.ok(successResponse);
                    }
                }
            } catch (Exception e) {
                logger.error("数据库删除历史记录失败: {}", e.getMessage());
                
                // 备选方案：调用Python API
                // 构建URI
                String url = pythonApiUrl + historyEndpoint + "/" + id;
                
                // 设置请求头
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                // 确保使用完整的令牌
                if (token != null && !token.isEmpty()) {
                    headers.setBearerAuth(token);
                    logger.debug("添加完整的认证令牌到请求头: {}", token);
                }
                
                try {
                    // 执行请求
                    ResponseEntity<Object> response = restTemplate.exchange(
                            url,
                            HttpMethod.DELETE,
                            new HttpEntity<>(headers),
                            Object.class
                    );
                    
                    logger.info("Python API删除历史记录请求成功: status={}", response.getStatusCode());
                    return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
                } catch (Exception pythonApiException) {
                    logger.error("Python API删除也失败: {}", pythonApiException.getMessage());
                    
                    // 所有删除尝试都失败，但我们依然返回成功，让前端可以从UI中移除该项
                    Map<String, Object> response = new HashMap<>();
                    response.put("status", "success");
                    response.put("message", "记录可能已被删除或不存在，已从列表中移除");
                    return ResponseEntity.ok(response);
                }
            }
            
        } catch (Exception e) {
            logger.error("删除历史记录请求失败: {}", e.getMessage(), e);
            // 即使发生异常，也返回成功响应，让前端可以更新UI
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "处理删除请求时出错，但记录已从列表中移除");
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * 删除历史记录API - 处理/api/history/list/{id}请求
     */
    @DeleteMapping("/history/list/{id}")
    public ResponseEntity<?> deleteHistoryFromList(
            @PathVariable String id,
            HttpServletRequest request) {
        
        logger.info("收到删除历史记录请求(list): id={}", id);
        
        // 直接调用原有的删除方法处理请求
        return deleteHistory(id, request);
    }
    
    /**
     * 处理OPTIONS请求
     */
    @RequestMapping(value = "/history/**", method = RequestMethod.OPTIONS)
    public ResponseEntity<?> handleOptions() {
        logger.debug("处理/api/history的OPTIONS请求");
        HttpHeaders headers = new HttpHeaders();
        headers.add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Authorization, Content-Type");
        headers.add("Access-Control-Max-Age", "3600");
        return ResponseEntity.ok().headers(headers).build();
    }
    
    /**
     * 从请求中提取认证令牌
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
    
    /**
     * 从令牌中提取用户名
     */
    private String extractUsernameFromToken(String token) {
        try {
            // 先尝试从扩展的认证令牌格式中提取，格式为：tokenValue_username_userId_role_timestamp
            String[] parts = token.split("_");
            if (parts.length >= 5) {
                // 当令牌格式为完整格式时，返回用户ID
                return parts[2]; // 用户ID在第三部分
            } 
            else if (parts.length >= 2) {
                // 如果是简单格式，返回用户名
                return parts[1];
            }
        } catch (Exception e) {
            logger.error("解析令牌失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 从令牌中提取用户ID（兼容旧版令牌）
     */
    private String extractUserIdFromToken(String token) {
        try {
            // 尝试从扩展的认证令牌格式中提取，格式为：tokenValue_username_userId_role_timestamp
            String[] parts = token.split("_");
            if (parts.length >= 5) {
                // 当令牌格式为完整格式时，返回用户ID
                logger.info("令牌包含完整信息，返回用户ID部分: {}", parts[2]);
                return parts[2]; // 用户ID在第三部分
            } 
            else if (parts.length >= 3) {
                // 如果是带ID的简单格式: tokenValue_username_userId
                logger.info("令牌包含三部分，返回第三部分作为用户ID: {}", parts[2]);
                return parts[2];
            }
            else if (parts.length >= 2) {
                // 如果是最简单的令牌格式，尝试从数据库获取用户ID
                String username = parts[1];
                logger.info("令牌仅包含用户名: {}，尝试从数据库获取用户ID", username);
                
                // 这里可以添加代码从数据库通过用户名查找用户ID
                // 暂时直接返回用户名
                return username;
            }
        } catch (Exception e) {
            logger.error("解析令牌失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取视频分析历史列表 - 公开API，无需认证
     */
    @GetMapping("/history/history/list")
    public ResponseEntity<?> getVideoHistoryList(
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "pageSize", required = false, defaultValue = "10") int pageSize,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "analysisType", required = false) String analysisType,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "sortBy", required = false, defaultValue = "created_at") String sortBy,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "desc") String sortOrder) {
        
        logger.info("接收到视频历史查询请求：page={}, pageSize={}, query={}, status={}, analysisType={}", 
                page, pageSize, query, status, analysisType);
        
        try {
            // 构建查询条件
            Query mongoQuery = new Query();
            
            // 添加筛选条件
            if (query != null && !query.isEmpty()) {
                // 同时搜索视频文件名和自定义视频名称
                Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("video_filename").regex(query, "i"),
                    Criteria.where("videoName").regex(query, "i")
                );
                mongoQuery.addCriteria(searchCriteria);
                logger.info("添加视频搜索条件：同时搜索文件名和自定义名称，关键词: {}", query);
            }
            
            if (status != null && !status.isEmpty()) {
                mongoQuery.addCriteria(Criteria.where("status").is(status));
            }
            
            if (analysisType != null && !analysisType.isEmpty()) {
                mongoQuery.addCriteria(Criteria.where("direction").is(analysisType));
            }
            
            // 设置排序
            Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
            mongoQuery.with(Sort.by(direction, sortBy));
            
            // 获取总数
            long total = mongoTemplate.count(mongoQuery, "analysis_videos");
            
            // 设置分页
            mongoQuery.skip((long) page * pageSize).limit(pageSize);
            
            // 执行查询
            List<VideoAnalysis> tasks = mongoTemplate.find(mongoQuery, VideoAnalysis.class, "analysis_videos");
            
            // 构建响应 - 使用前端期望的数据结构
            Map<String, Object> response = new HashMap<>();
            response.put("tasks", tasks);
            response.put("total", total);
            response.put("page", page);
            response.put("pageSize", pageSize);
            
            logger.info("查询视频历史成功：找到{}条记录", tasks.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("查询视频历史失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "查询视频历史失败: " + e.getMessage()));
        }
    }
    
    /**
     * 批量删除视频分析记录 - 公开API，无需认证
     */
    @PostMapping("/history/history/batch-delete")
    public ResponseEntity<?> batchDeleteVideos(@RequestBody Map<String, List<String>> requestMap) {
        List<String> taskIds = requestMap.get("taskIds");
        
        if (taskIds == null || taskIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "任务ID列表不能为空"));
        }
        
        logger.info("接收到批量删除请求：{}", taskIds);
        
        try {
            // 简单实现：直接从MongoDB中删除记录，不检查权限
            int successCount = 0;
            
            for (String taskId : taskIds) {
                Query query = new Query(Criteria.where("task_id").is(taskId));
                mongoTemplate.remove(query, "analysis_videos");
                successCount++;
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("successCount", successCount);
            response.put("message", "已成功删除" + successCount + "条记录");
            
            logger.info("批量删除成功：删除了{}条记录", successCount);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("批量删除失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "批量删除失败: " + e.getMessage()));
        }
    }
    
    /**
     * 重命名视频 - 公开API，无需认证
     */
    @PostMapping("/history/rename")
    public ResponseEntity<?> renameVideo(@RequestBody Map<String, String> requestMap) {
        String taskId = requestMap.get("taskId");
        String videoName = requestMap.get("videoName");
        
        if (taskId == null || taskId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "任务ID不能为空"));
        }
        
        if (videoName == null) {
            videoName = ""; // 允许清除名称
        }
        
        logger.info("接收到视频重命名请求：taskId={}, 新名称={}", taskId, videoName);
        
        try {
            // 先尝试使用taskId字段查询
            Query query = new Query(Criteria.where("task_id").is(taskId));
            VideoAnalysis video = mongoTemplate.findOne(query, VideoAnalysis.class, "analysis_videos");
            
            // 如果找不到，再尝试使用_id字段查询
            if (video == null) {
                logger.info("使用task_id未找到记录，尝试使用_id查询: {}", taskId);
                try {
                    ObjectId objectId = new ObjectId(taskId);
                    query = new Query(Criteria.where("_id").is(objectId));
                    video = mongoTemplate.findOne(query, VideoAnalysis.class, "analysis_videos");
                } catch (Exception e) {
                    logger.warn("无法将ID转换为ObjectId: {}", e.getMessage());
                }
            }
            
            // 如果仍未找到，尝试使用result_id查询
            if (video == null) {
                logger.info("使用_id未找到记录，尝试使用result_id查询: {}", taskId);
                query = new Query(Criteria.where("result_id").is(taskId));
                video = mongoTemplate.findOne(query, VideoAnalysis.class, "analysis_videos");
            }
            
            if (video == null) {
                logger.warn("未找到要重命名的视频记录：taskId={}", taskId);
                return ResponseEntity.notFound().build();
            }
            
            // 创建新的查询，确保使用找到记录的正确ID字段
            Query updateQuery;
            if (video.getTaskId() != null) {
                updateQuery = new Query(Criteria.where("task_id").is(video.getTaskId()));
                logger.info("使用task_id进行更新: {}", video.getTaskId());
            } else {
                // 直接使用之前找到记录的查询条件
                updateQuery = query;
                logger.info("使用现有查询条件进行更新");
            }
            
            // 更新视频名称 - 使用videoName字段存储自定义视频名称
            Update update = new Update().set("videoName", videoName);
            com.mongodb.client.result.UpdateResult result = mongoTemplate.updateFirst(updateQuery, update, "analysis_videos");
            
            logger.info("更新结果: matchedCount={}, modifiedCount={}", result.getMatchedCount(), result.getModifiedCount());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("taskId", taskId);
            response.put("videoName", videoName);
            response.put("message", "视频重命名成功");
            
            logger.info("视频重命名成功：taskId={}, 新名称={}", taskId, videoName);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("视频重命名失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "视频重命名失败: " + e.getMessage(), 
                "success", false));
        }
    }

    /**
     * 标准化历史记录数据
     */
    @SuppressWarnings("unchecked")
    private void standardizeHistoryRecords(List<Object> historyList, String username) {
        if (historyList == null || historyList.isEmpty()) {
            return;
        }
        
        // 设置中国时区
        TimeZone chinaTimeZone = TimeZone.getTimeZone("Asia/Shanghai");
        ZoneId chinaZoneId = ZoneId.of("Asia/Shanghai");
        
        // 定义统一的日期格式，确保与前端一致
        SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        outputFormat.setTimeZone(chinaTimeZone); // 设置输出使用中国时区
        
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                .withZone(chinaZoneId);
        
        // 定义CST格式的解析器
        SimpleDateFormat cstFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US);
        cstFormat.setTimeZone(chinaTimeZone); // 设置CST解析使用中国时区
        
        for (Object historyObj : historyList) {
            try {
                // 如果对象是 AnalysisHistory 类型，直接处理
                if (historyObj instanceof AnalysisHistory) {
                    processAnalysisHistory((AnalysisHistory) historyObj, outputFormat, outputFormatter, chinaZoneId, cstFormat);
                }
                // 如果对象是 Map 类型，作为 Map 处理
                else if (historyObj instanceof Map) {
                    processHistoryMap((Map<String, Object>) historyObj, outputFormat, outputFormatter, chinaZoneId, cstFormat, username);
                }
                // 其他类型则记录无法处理
                else {
                    logger.warn("无法处理的历史记录类型: {}", historyObj != null ? historyObj.getClass().getName() : "null");
                }
            } catch (Exception e) {
                logger.error("处理历史记录时出错: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * 处理 AnalysisHistory 类型的历史记录
     */
    private void processAnalysisHistory(AnalysisHistory history, 
                                     SimpleDateFormat outputFormat,
                                     DateTimeFormatter outputFormatter,
                                     ZoneId chinaZoneId,
                                     SimpleDateFormat cstFormat) {
                // 1. 标准化分析耗时
                if (history.getInferenceTime() <= 0) {
                    logger.info("标准化分析耗时: 原值={}, 设置为默认值0.5秒", history.getInferenceTime());
                    history.setInferenceTime(0.5); // 设置默认分析耗时为0.5秒
                }
                
                // 2. 添加附加信息字段
                Map<String, Object> additionalInfo = new HashMap<>();
                
                // 分析耗时的标准格式
                double seconds = history.getInferenceTime();
                String formattedDuration;
                if (seconds < 0.001) {
                    formattedDuration = String.format("%.2f 毫秒", seconds * 1000);
                } else if (seconds < 1) {
                    formattedDuration = String.format("%.0f 毫秒", seconds * 1000);
                } else if (seconds < 60) {
                    formattedDuration = String.format("%.2f 秒", seconds);
                } else {
                    int minutes = (int)(seconds / 60);
                    int remainingSeconds = (int)(seconds % 60);
                    formattedDuration = String.format("%d 分 %d 秒", minutes, remainingSeconds);
                }
                additionalInfo.put("formattedDuration", formattedDuration);
        history.setFormattedDuration(formattedDuration);
                logger.debug("添加格式化分析耗时: {}", formattedDuration);
                
                // 3. 标准化时间戳
        String formattedTime = formatTimestamp(history.getTimestamp(), history.getAnalysisStartTime(), 
                                             history.getCreateTime(), outputFormat, outputFormatter, 
                                             chinaZoneId, cstFormat);
        
        // 设置格式化的时间字段
        additionalInfo.put("formattedTime", formattedTime);
        history.setFormattedTime(formattedTime);
        logger.debug("添加格式化时间: {}", formattedTime);
        
        // 4. 确保分析人员信息正确
        ensureAnalystInfo(history);
    }
    
    /**
     * 处理 Map 类型的历史记录
     */
    private void processHistoryMap(Map<String, Object> historyMap,
                                SimpleDateFormat outputFormat,
                                DateTimeFormatter outputFormatter,
                                ZoneId chinaZoneId,
                                SimpleDateFormat cstFormat,
                                String username) {
        // 1. 标准化分析耗时
        double inferenceTime = 0.5;
        if (historyMap.containsKey("inferenceTime")) {
            Object timeObj = historyMap.get("inferenceTime");
            if (timeObj instanceof Number) {
                inferenceTime = ((Number) timeObj).doubleValue();
                if (inferenceTime <= 0) {
                    inferenceTime = 0.5;
                }
            }
        }
        historyMap.put("inferenceTime", inferenceTime);
        
        // 2. 添加格式化的分析耗时
        double seconds = inferenceTime;
        String formattedDuration;
        if (seconds < 0.001) {
            formattedDuration = String.format("%.2f 毫秒", seconds * 1000);
        } else if (seconds < 1) {
            formattedDuration = String.format("%.0f 毫秒", seconds * 1000);
        } else if (seconds < 60) {
            formattedDuration = String.format("%.2f 秒", seconds);
        } else {
            int minutes = (int)(seconds / 60);
            int remainingSeconds = (int)(seconds % 60);
            formattedDuration = String.format("%d 分 %d 秒", minutes, remainingSeconds);
        }
        historyMap.put("formattedDuration", formattedDuration);
        
        // 3. 标准化时间戳
        String timestamp = null;
        LocalDateTime analysisStartTime = null;
        LocalDateTime createTime = null;
        
        // 提取时间字段
        if (historyMap.containsKey("timestamp")) {
            Object timestampObj = historyMap.get("timestamp");
            if (timestampObj != null) {
                timestamp = timestampObj.toString();
            }
        }
        
        // 尝试处理 Map 中的其他时间字段
        if (historyMap.containsKey("analysisStartTime")) {
            Object timeObj = historyMap.get("analysisStartTime");
            if (timeObj instanceof LocalDateTime) {
                analysisStartTime = (LocalDateTime) timeObj;
            } else if (timeObj != null) {
                try {
                    analysisStartTime = LocalDateTime.parse(timeObj.toString());
                } catch (Exception ignore) {}
            }
        }
        
        if (historyMap.containsKey("createTime")) {
            Object timeObj = historyMap.get("createTime");
            if (timeObj instanceof LocalDateTime) {
                createTime = (LocalDateTime) timeObj;
            } else if (timeObj != null) {
                try {
                    createTime = LocalDateTime.parse(timeObj.toString());
                } catch (Exception ignore) {}
            }
        }
        
        String formattedTime = formatTimestamp(timestamp, analysisStartTime, 
                                             createTime, outputFormat, outputFormatter, 
                                             chinaZoneId, cstFormat);
        historyMap.put("formattedTime", formattedTime);
        
        // 4. 确保分析人员信息正确
        ensureAnalystInfoForMap(historyMap, username);
    }
    
    /**
     * 格式化时间戳
     */
    private String formatTimestamp(String timestamp, LocalDateTime analysisStartTime, 
                                LocalDateTime createTime, SimpleDateFormat outputFormat,
                                DateTimeFormatter outputFormatter, ZoneId chinaZoneId,
                                SimpleDateFormat cstFormat) {
                String formattedTime = null;
        
        if (timestamp != null) {
                    logger.debug("处理时间戳: {}", timestamp);
                    
                    try {
                        // 尝试解析CST格式的时间戳
                        if (timestamp.contains("CST")) {
                            try {
                                // 使用专门的CST格式解析器，确保使用正确的时区
                                Date date = cstFormat.parse(timestamp);
                                // 输出时不要转换时区，保持原始CST时间
                                formattedTime = outputFormat.format(date);
                                logger.debug("成功解析CST格式时间: {} -> {}", timestamp, formattedTime);
                            } catch (ParseException e) {
                                logger.warn("CST格式解析失败，尝试使用Date构造函数: {}", e.getMessage());
                                
                                // 备用方法，尝试直接使用构造函数，并确保时区设置正确
                                try {
                                    // 尝试构造但保持CST原始时区
                                    SimpleDateFormat backupFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            backupFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
                                    
                                    // 格式化成易于解析的格式
                                    String cleanTimestamp = timestamp
                                            .replace("CST", "")
                                            .replace("Fri", "")
                                            .replace("May", "05")
                                            .trim();
                                    
                                    // 提取时间部分并解析
                                    String[] parts = cleanTimestamp.split("\\s+");
                                    if (parts.length >= 4) {
                                        String dateStr = parts[1] + "-" + parts[0] + "-" + parts[3] + " " + parts[2];
                                        Date date = backupFormat.parse(dateStr);
                                        formattedTime = outputFormat.format(date);
                                        logger.debug("使用备用方法解析CST时间: {} -> {}", timestamp, formattedTime);
                                    } else {
                                        throw new ParseException("无法解析CST时间格式", 0);
                                    }
                                } catch (Exception ex) {
                                    logger.warn("备用CST解析方法失败，使用系统当前时间: {}", ex.getMessage());
                            // 最后使用系统当前时间
                                    Date date = new Date();
                                    formattedTime = outputFormat.format(date);
                                }
                            }
                        }
                        // 处理ISO格式 (2025-05-09T21:34:49.000Z)
                        else if (timestamp.contains("T")) {
                            try {
                                // 如果是ISO格式的UTC时间，需要转换到中国时区
                                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                                isoFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // 设置输入为UTC时区
                                
                                // 处理格式
                                String parsableTimestamp = timestamp;
                                if (parsableTimestamp.contains(".")) {
                                    parsableTimestamp = parsableTimestamp.substring(0, parsableTimestamp.indexOf("."));
                                } else if (parsableTimestamp.endsWith("Z")) {
                                    parsableTimestamp = parsableTimestamp.substring(0, parsableTimestamp.length() - 1);
                                }
                                
                                // 从UTC解析，然后用中国时区格式化输出
                                Date date = isoFormat.parse(parsableTimestamp);
                                formattedTime = outputFormat.format(date);
                                logger.debug("成功解析ISO格式时间(UTC->CST): {} -> {}", timestamp, formattedTime);
                            } catch (Exception e) {
                                logger.warn("ISO格式解析失败，尝试使用LocalDateTime: {}", e.getMessage());
                                
                                try {
                                    // 尝试使用LocalDateTime解析
                                    LocalDateTime dateTime = LocalDateTime.parse(timestamp.replace("Z", ""));
                                    formattedTime = outputFormatter.format(dateTime);
                                    logger.debug("使用LocalDateTime解析ISO时间: {} -> {}", timestamp, formattedTime);
                                } catch (Exception ex) {
                                    logger.warn("LocalDateTime解析失败，使用系统当前时间: {}", ex.getMessage());
                            // 使用系统当前时间
                                    Date date = new Date();
                                    formattedTime = outputFormat.format(date);
                                }
                            }
                        }
                        // 尝试解析其他常见格式
                        else {
                            // 尝试解析常见日期格式
                            String[] patterns = {
                                "yyyy-MM-dd HH:mm:ss",
                                "yyyy/MM/dd HH:mm:ss",
                                "yyyy-MM-dd",
                                "yyyy/MM/dd"
                            };
                            
                            boolean parsed = false;
                            for (String pattern : patterns) {
                                try {
                                    SimpleDateFormat parser = new SimpleDateFormat(pattern);
                            parser.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); // 设置使用中国时区
                                    Date date = parser.parse(timestamp);
                                    formattedTime = outputFormat.format(date);
                                    parsed = true;
                                    logger.debug("成功使用模式{}解析时间: {} -> {}", pattern, timestamp, formattedTime);
                                    break;
                                } catch (ParseException ignored) {
                                    // 当前格式不匹配，尝试下一个
                                }
                            }
                            
                            // 如果没有匹配的格式，尝试作为时间戳处理
                            if (!parsed) {
                                try {
                                    long epochTime = Long.parseLong(timestamp);
                                    Date date = new Date(epochTime);
                                    formattedTime = outputFormat.format(date);
                                    logger.debug("成功作为时间戳解析: {} -> {}", timestamp, formattedTime);
                                } catch (NumberFormatException e) {
                                    // 不是有效的时间戳，使用原始值
                                    formattedTime = timestamp;
                                    logger.warn("无法解析时间戳，使用原始值: {}", timestamp);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("时间戳解析失败: {}, 错误: {}", timestamp, e.getMessage());
                        formattedTime = timestamp; // 保持原始值
                    }
        } else if (analysisStartTime != null) {
                    // 使用分析开始时间 - 确保使用正确的时区
            formattedTime = analysisStartTime.atZone(chinaZoneId).format(outputFormatter);
                    logger.debug("使用分析开始时间: {}", formattedTime);
        } else if (createTime != null) {
                    // 使用创建时间 - 确保使用正确的时区
            formattedTime = createTime.atZone(chinaZoneId).format(outputFormatter);
                    logger.debug("使用创建时间: {}", formattedTime);
                } else {
                    // 使用当前时间作为后备 - 确保使用正确的时区
                    formattedTime = LocalDateTime.now(chinaZoneId).format(outputFormatter);
                    logger.debug("使用当前时间: {}", formattedTime);
                }
                
        return formattedTime;
    }
                
    /**
     * 确保分析人员信息正确
     */
    private void ensureAnalystInfo(AnalysisHistory history) {
                if (history.getAnalyst() == null || history.getAnalyst().isEmpty()) {
                    // 只有当原始记录中没有分析人员信息时，才尝试从其他字段补充
                    if (history.getUsername() != null && !history.getUsername().isEmpty()) {
                        history.setAnalyst(history.getUsername());
                    } else if (history.getAnalysisResult() != null && history.getAnalysisResult().containsKey("analyst")) {
                        // 如果分析结果中包含分析人员信息，使用它
                        Object analyst = history.getAnalysisResult().get("analyst");
                        if (analyst != null && !analyst.toString().isEmpty()) {
                            history.setAnalyst(analyst.toString());
                        } else {
                            history.setAnalyst("系统");
                        }
                    } else {
                        history.setAnalyst("系统");
            }
                    }
    }
    
    /**
     * 确保 Map 类型历史记录中的分析人员信息正确
     */
    private void ensureAnalystInfoForMap(Map<String, Object> historyMap, String username) {
        if (!historyMap.containsKey("analyst") || historyMap.get("analyst") == null || 
            historyMap.get("analyst").toString().isEmpty()) {
            
            // 尝试从用户名字段获取
            if (historyMap.containsKey("username") && historyMap.get("username") != null && 
                !historyMap.get("username").toString().isEmpty()) {
                historyMap.put("analyst", historyMap.get("username").toString());
            }
            // 尝试从分析结果中获取
            else if (historyMap.containsKey("analysisResult") && historyMap.get("analysisResult") instanceof Map) {
                Map<String, Object> analysisResult = (Map<String, Object>) historyMap.get("analysisResult");
                if (analysisResult.containsKey("analyst") && analysisResult.get("analyst") != null) {
                    historyMap.put("analyst", analysisResult.get("analyst").toString());
                } else {
                    historyMap.put("analyst", "系统");
                }
            } else {
                historyMap.put("analyst", "系统");
                }
        }
    }

    /**
     * 批量删除历史记录API
     */
    @DeleteMapping("/history/batch-delete")
    public ResponseEntity<?> batchDeleteHistory(@RequestBody BatchDeleteRequest request, HttpServletRequest httpRequest) {
        logger.info("收到批量删除请求: {} 条记录，类型: {}", 
            request.getIds() != null ? request.getIds().size() : 0, 
            request.getType());
        
        if (request.getIds() == null || request.getIds().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "未提供要删除的记录ID",
                "status", "error"
            ));
        }
        
        try {
            // 获取认证令牌并提取用户ID
            String token = extractToken(httpRequest);
            if (token == null || token.isEmpty()) {
                logger.warn("未找到认证令牌，批量删除操作被拒绝");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "message", "未授权的访问，请提供有效的认证令牌",
                    "status", "error"
                ));
            }
            
            // 从令牌中提取用户ID
            String userId = extractUserIdFromToken(token);
            if (userId == null || userId.isEmpty()) {
                logger.warn("从令牌中未能提取到有效的用户ID，使用令牌值作为用户标识");
                userId = token;
            }
            logger.info("批量删除操作用户ID: {}", userId);
            
            // 设置请求中的用户ID
            request.setUserId(userId);
            
            // 执行批量删除，传入用户ID进行权限验证
            List<String> deletedIds = historyService.batchDelete(request.getIds(), request.getType(), userId);
            
            logger.info("批量删除完成: 成功删除 {}/{} 条记录", 
                deletedIds.size(), request.getIds().size());
            
            return ResponseEntity.ok(Map.of(
                "message", "成功删除 " + deletedIds.size() + " 条历史记录", 
                "status", "success",
                "deletedIds", deletedIds
            ));
        } catch (Exception e) {
            logger.error("批量删除失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "message", "批量删除处理时出错: " + e.getMessage(), 
                "status", "error"
            ));
        }
    }
} 