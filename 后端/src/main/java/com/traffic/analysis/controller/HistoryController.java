package com.traffic.analysis.controller;

import com.traffic.analysis.entity.AnalysisHistory;
import com.traffic.analysis.service.HistoryService;
import com.traffic.analysis.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史记录控制器
 */
@Controller
@RequestMapping("/history")
public class HistoryController {

    private static final Logger logger = LoggerFactory.getLogger(HistoryController.class);

    @Autowired
    private HistoryService historyService;

    @GetMapping
    public String showHistoryPage(Model model, HttpServletRequest request) {
        String userId = WebUtils.getUserId(request);
        logger.info("显示历史页面，用户ID: {}", userId);
        
        // 添加会话信息到模型中供调试
        if (request.getSession() != null) {
            Object userObj = request.getSession().getAttribute("user");
            model.addAttribute("debug_session_user", userObj != null ? userObj.toString() : "null");
            model.addAttribute("debug_session_id", request.getSession().getId());
            logger.info("会话信息：ID={}, user={}", request.getSession().getId(), userObj);
        } else {
            model.addAttribute("debug_session_user", "会话为空");
            logger.warn("请求会话为空");
        }
        
        boolean mongoConnected = false;
        String mongoErrorMessage = "";
        
        try {
            // 检查MongoDB连接状态
            try {
                mongoConnected = checkMongoDBConnection();
                model.addAttribute("mongoConnected", mongoConnected);
                logger.info("MongoDB连接状态: {}", mongoConnected ? "正常" : "异常");
            } catch (Exception e) {
                mongoErrorMessage = e.getMessage();
                logger.error("检查MongoDB连接出错: {}", e.getMessage(), e);
                model.addAttribute("mongoConnected", false);
                model.addAttribute("mongoErrorMessage", mongoErrorMessage);
            }
            
            // 直接获取用户历史记录并添加到模型中
            if (userId != null && !userId.isEmpty()) {
                List<AnalysisHistory> historyList = historyService.getUserHistory(userId);
                
                // 防御性编程：确保历史记录列表不为null
                if (historyList == null) {
                    logger.warn("获取到空的历史记录列表");
                    historyList = Collections.emptyList();
                }
                
                // 过滤掉null元素，避免前端渲染问题
                historyList.removeIf(item -> item == null);
                
                model.addAttribute("historyList", historyList);
                model.addAttribute("debug_userId", userId);
                model.addAttribute("debug_recordCount", historyList.size());
                logger.info("成功获取历史记录，记录数: {}", historyList.size());
            } else {
                model.addAttribute("historyList", Collections.emptyList());
                model.addAttribute("error", "用户未登录，无法获取历史记录");
                logger.warn("用户未登录，无法获取历史记录");
            }
        } catch (Exception e) {
            model.addAttribute("historyList", Collections.emptyList());
            model.addAttribute("error", "获取历史记录失败: " + e.getMessage());
            logger.error("获取历史记录时发生异常", e);
        }
        
        return "history";
    }

    /**
     * 检查MongoDB连接状态
     */
    @GetMapping("/check-db")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkDatabaseStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean ping = checkMongoDBConnection();
            response.put("ping", ping);
            response.put("timestamp", System.currentTimeMillis());
            
            if (!ping) {
                response.put("error", "无法连接到MongoDB数据库");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("检查数据库连接时出错", e);
            response.put("ping", false);
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 检查MongoDB连接
     */
    private boolean checkMongoDBConnection() {
        try {
            // 使用historyService检查连接
            List<AnalysisHistory> testQuery = historyService.getUserHistory("test-connection");
            return true;
        } catch (Exception e) {
            logger.error("MongoDB连接测试异常", e);
            return false;
        }
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<?> getUserHistory(HttpServletRequest request, 
                                          @RequestParam(required = false, defaultValue = "10") Integer limit, 
                                          @RequestParam(required = false, defaultValue = "0") Integer skip,
                                          @RequestParam(required = false) String type) {
        logger.info("收到GET请求获取用户历史记录，limit={}, skip={}, type={}", limit, skip, type);
        return processHistoryRequest(request, limit, skip, type);
    }
    
    // 添加POST请求支持，确保前端可以通过POST请求获取历史记录
    @PostMapping("/list")
    @ResponseBody
    public ResponseEntity<?> postUserHistory(HttpServletRequest request, 
                                           @RequestParam(required = false, defaultValue = "10") Integer limit, 
                                           @RequestParam(required = false, defaultValue = "0") Integer skip,
                                           @RequestParam(required = false) String type) {
        logger.info("收到POST请求获取用户历史记录，limit={}, skip={}, type={}", limit, skip, type);
        return processHistoryRequest(request, limit, skip, type);
    }
    
    // 添加通用处理方法，避免代码重复
    private ResponseEntity<?> processHistoryRequest(HttpServletRequest request, Integer limit, Integer skip, String type) {
        try {
            String userId = WebUtils.getUserId(request);
            logger.info("获取用户历史记录，用户ID: {}, 类型: {}", userId, type);
            
            if (userId == null || userId.isEmpty()) {
                logger.error("用户ID为空");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "用户ID为空");
                errorResponse.put("results", Collections.emptyList());
                errorResponse.put("total", 0);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
            }
            
            Map<String, Object> response;
            List<AnalysisHistory> historyList;
            
            // 根据是否指定类型使用不同的查询方法
            if (type != null && !type.isEmpty()) {
                if (limit != null && skip != null) {
                    // 使用支持分页和类型过滤的接口
                    response = historyService.getUserHistoryByType(userId, type, limit, skip);
                    // 结果已经是标准格式，直接添加状态标志
                    response.put("status", "success");
                    logger.info("成功获取历史记录，类型: {}, 记录数: {}", type, 
                        response.get("results") instanceof List<?> ? ((List<?>)response.get("results")).size() : 0);
                    return ResponseEntity.ok(response);
                } else {
                    // 使用按类型过滤但不分页的接口
                    historyList = historyService.getUserHistoryByType(userId, type);
                }
            } else {
                // 不过滤类型，使用原有接口
                historyList = historyService.getUserHistory(userId);
            }
            
            if (historyList == null) {
                logger.error("获取历史记录返回null");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "获取历史记录失败");
                errorResponse.put("results", Collections.emptyList());
                errorResponse.put("total", 0);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }
            
            // 移除空元素
            historyList.removeIf(item -> item == null);
            
            // 确保每条记录的inferenceTime和analyst字段有值，并且id是字符串类型
            for (AnalysisHistory item : historyList) {
                // 确保ID是字符串格式
                if (item.getId() != null && !(item.getId() instanceof String)) {
                    item.setId(String.valueOf(item.getId()));
                }
                
                // 确保resultId是字符串格式
                if (item.getResultId() != null && !(item.getResultId() instanceof String)) {
                    item.setResultId(String.valueOf(item.getResultId()));
                }
                
                if (item.getInferenceTime() <= 0) {
                    item.setInferenceTime(0.5);
                }
                if (item.getAnalyst() == null || item.getAnalyst().isEmpty()) {
                    if (item.getUsername() != null && !item.getUsername().isEmpty()) {
                        item.setAnalyst(item.getUsername());
                    } else {
                        item.setAnalyst("系统");
                    }
                }
            }
            
            // 构建符合前端期望的标准格式响应
            response = new HashMap<>();
            response.put("status", "success");
            response.put("results", historyList);
            response.put("total", historyList.size());
            
            logger.info("成功获取历史记录，记录数: {}", historyList.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取历史记录时发生异常", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取历史记录时发生异常: " + e.getMessage());
            errorResponse.put("results", Collections.emptyList());
            errorResponse.put("total", 0);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * 创建测试记录
     */
    @GetMapping("/create-test-record") 
    public String createTestRecord(HttpServletRequest request) {
        try {
            String userId = WebUtils.getUserId(request);
            if (userId == null || userId.isEmpty()) {
                return "redirect:/login";
            }
            
            AnalysisHistory history = new AnalysisHistory();
            history.setUserId(userId);
            history.setMessage("测试记录 - " + System.currentTimeMillis());
            history.setVehicleCount(5);
            history.setInferenceTime(0.5);
            history.setTimestamp(java.time.LocalDateTime.now().toString());
            history.setStatus("success");
            
            historyService.saveHistory(history);
            logger.info("成功创建测试记录");
            
            return "redirect:/history";
        } catch (Exception e) {
            logger.error("创建测试记录失败", e);
            return "redirect:/history?error=创建测试记录失败: " + e.getMessage();
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteHistory(@PathVariable String id, HttpServletRequest request) {
        try {
            // 清理ID可能包含的特殊格式
            String cleanId = id;
            if (id != null) {
                // 清理ObjectId格式
                if (id.startsWith("ObjectId(") && id.endsWith(")")) {
                    cleanId = id.substring(9, id.length() - 1);
                    // 如果有引号，继续清理
                    if (cleanId.startsWith("\"") && cleanId.endsWith("\"")) {
                        cleanId = cleanId.substring(1, cleanId.length() - 1);
                    }
                    logger.info("清理ObjectId格式: {} -> {}", id, cleanId);
                }
            }
            
            String userId = WebUtils.getUserId(request);
            logger.info("删除历史记录，ID: {} (原始ID: {}), 用户ID: {}", cleanId, id, userId);
            
            if (userId == null || userId.isEmpty()) {
                logger.warn("删除历史记录失败: 用户未登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Collections.singletonMap("error", "用户未登录，无法删除记录"));
            }
            
            // 从用户对象中获取ID，如果是User对象格式
            if (userId.contains("id=")) {
                String extractedId = extractIdFromString(userId);
                if (!extractedId.isEmpty()) {
                    userId = extractedId;
                    logger.info("从复杂格式中提取用户ID: {}", userId);
                }
            }
            
            // 执行删除操作
            boolean result = historyService.deleteHistory(cleanId, userId);
            
            if (result) {
                logger.info("成功删除历史记录，ID: {}", cleanId);
                return ResponseEntity.ok(Collections.singletonMap("message", "删除成功"));
            } else {
                logger.warn("删除历史记录失败: 未找到记录或用户无权删除，ID: {}", cleanId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Collections.singletonMap("error", "未找到记录或无权删除"));
            }
        } catch (Exception e) {
            logger.error("删除历史记录时发生异常: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "删除历史记录时发生异常: " + e.getMessage()));
        }
    }
    
    /**
     * 从形如"User(id=123, name=xxx)"的字符串中提取id部分
     */
    private String extractIdFromString(String input) {
        if (input == null || !input.contains("id=")) {
            return "";
        }
        int start = input.indexOf("id=") + 3;
        int end = input.indexOf(",", start);
        if (end == -1) {
            end = input.indexOf(")", start);
        }
        if (end == -1) {
            end = input.length();
        }
        return input.substring(start, end).trim();
    }
} 