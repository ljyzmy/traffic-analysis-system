package com.traffic.analysis.controller;

import com.traffic.analysis.model.User;
import com.traffic.analysis.model.VideoAnalysis;
import com.traffic.analysis.security.TokenInfo;
import com.traffic.analysis.service.UserService;
import com.traffic.analysis.service.VideoAnalysisService;
import com.traffic.analysis.service.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/video-analysis")
@CrossOrigin(
    origins = {
        "http://localhost:8081",
        "http://localhost:8080",
        "http://localhost:5173",
        "http://localhost:5000",
        "http://localhost:5001"
    }, 
    allowedHeaders = {
        "Origin", 
        "Content-Type", 
        "Accept", 
        "Authorization", 
        "X-Requested-With", 
        "Access-Control-Request-Method", 
        "Access-Control-Request-Headers"
    },
    exposedHeaders = {
        "Access-Control-Allow-Origin", 
        "Access-Control-Allow-Credentials", 
        "Authorization"
    },
    methods = {
        RequestMethod.GET,
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.DELETE,
        RequestMethod.OPTIONS
    },
    allowCredentials = "true",
    maxAge = 3600
)
public class VideoAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(VideoAnalysisController.class);

    @Autowired
    private VideoAnalysisService videoAnalysisService;

    @Autowired
    private UserService userService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private HistoryService historyService;

    /**
     * 辅助方法：从SecurityContext中获取TokenInfo
     */
    private TokenInfo getCurrentTokenInfo() {
        TokenInfo tokenInfo = null;
        try {
            org.springframework.security.core.Authentication auth = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            
            if (auth != null) {
                // 从Authentication的details中获取TokenInfo
                Object details = auth.getDetails();
                if (details instanceof TokenInfo) {
                    tokenInfo = (TokenInfo) details;
                    log.debug("从Authentication details获取TokenInfo: username={}, role={}", 
                              tokenInfo.getUsername(), tokenInfo.getRole());
                } else {
                    // 如果details中没有TokenInfo，尝试从用户名获取
                    String username = auth.getName();
                    log.debug("通过用户名获取用户信息: {}", username);
                    User user = userService.findByUsername(username);
                    if (user != null) {
                        tokenInfo = new TokenInfo();
                        tokenInfo.setId(user.getId());
                        tokenInfo.setUsername(user.getUsername());
                        tokenInfo.setRole(user.getRole());
                        log.debug("从数据库获取TokenInfo: username={}, role={}", 
                                 tokenInfo.getUsername(), tokenInfo.getRole());
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取认证信息时出错: {}", e.getMessage(), e);
        }
        return tokenInfo;
    }

    /**
     * 上传单个视频并进行分析
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadVideo(
            @RequestParam("video") MultipartFile video,
            @RequestParam("direction") String direction,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "role", required = false) String role,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Origin", required = false) String origin) {

        log.info("接收到视频上传请求: 文件名={}, 大小={}, 方向={}, 来源={}", 
                video.getOriginalFilename(), video.getSize(), direction, origin);
                
        // 记录所有请求头以帮助调试
        log.info("请求头中的Authorization: {}", authHeader != null ? authHeader.substring(0, Math.min(authHeader.length(), 20)) + "..." : "null");
        log.info("请求中的用户信息: userId={}, username={}, role={}", userId, username, role);

        if (video.isEmpty()) {
            log.warn("上传的视频文件为空");
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "请提供视频文件"));
        }

        // 获取认证信息
        TokenInfo tokenInfo = getCurrentTokenInfo();
        
        // 如果从SecurityContext获取不到，就尝试使用请求参数
        if (tokenInfo == null && userId != null && username != null) {
            log.info("从请求参数获取用户信息: userId={}, username={}, role={}", userId, username, role);
            tokenInfo = new TokenInfo();
            tokenInfo.setId(userId);
            tokenInfo.setUsername(username);
            tokenInfo.setRole(role != null ? role : "user");
        }

        // 检查认证信息
        if (tokenInfo == null) {
            log.warn("用户未认证，无法上传视频");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "用户未认证"));
        }

        log.info("处理用户{}的视频上传请求", tokenInfo.getUsername());

        try {
            User user = null;
            
            // 尝试从数据库获取用户信息
            if (tokenInfo.getId() != null) {
                user = userService.findById(tokenInfo.getId());
            }
            
            // 如果找不到用户，也可能是通过其他认证方式（如API密钥）访问
            if (user == null) {
                log.info("数据库中找不到用户ID={}, 使用请求参数中的用户信息", tokenInfo.getId());
                
                // 创建一个临时用户对象来处理请求
                user = new User();
                user.setId(tokenInfo.getId());
                user.setUsername(tokenInfo.getUsername());
                user.setRole(tokenInfo.getRole());
            } else {
                log.info("从数据库获取到用户: id={}, username={}, role={}", 
                        user.getId(), user.getUsername(), user.getRole());
            }

            log.info("开始处理用户{}(角色={})上传的视频", user.getUsername(), user.getRole());

            VideoAnalysis task = videoAnalysisService.uploadAndAnalyzeVideo(
                    video,
                    user.getId(),
                    user.getUsername(),
                    user.getRole(),
                    direction);

            Map<String, Object> response = new HashMap<>();
            response.put("taskId", task.getTaskId());
            response.put("status", "success");
            response.put("message", "视频已上传，正在进行分析");

            log.info("视频上传成功，创建任务: taskId={}", task.getTaskId());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } catch (IOException e) {
            log.error("上传视频失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "视频上传失败: " + e.getMessage()));
        } catch (Exception e) {
            log.error("处理视频上传时发生意外错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "处理视频时出错: " + e.getMessage()));
        }
    }

    /**
     * 上传十字路口的两个视频（横向和纵向）并进行分析
     */
    @PostMapping(value = "/upload/intersection", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadIntersectionVideos(
            @RequestParam("horizontalVideo") MultipartFile horizontalVideo,
            @RequestParam("verticalVideo") MultipartFile verticalVideo) {

        log.info("接收到十字路口视频上传请求: 横向视频={}, 大小={}; 纵向视频={}, 大小={}", 
                horizontalVideo.getOriginalFilename(), horizontalVideo.getSize(),
                verticalVideo.getOriginalFilename(), verticalVideo.getSize());

        if (horizontalVideo.isEmpty() || verticalVideo.isEmpty()) {
            log.warn("上传的横向或纵向视频文件为空");
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "请提供横向和纵向的视频文件"));
        }

        // 获取认证信息
        TokenInfo tokenInfo = getCurrentTokenInfo();

        // 检查认证信息
        if (tokenInfo == null) {
            log.warn("用户未认证，无法上传视频");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "用户未认证"));
        }

        try {
            User user = userService.findById(tokenInfo.getId());
            if (user == null) {
                log.warn("找不到用户 ID={}", tokenInfo.getId());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Collections.singletonMap("error", "用户不存在"));
            }

            log.info("开始处理用户{}(角色={})上传的十字路口视频", user.getUsername(), user.getRole());

            VideoAnalysis task = videoAnalysisService.processIntersectionVideos(
                    horizontalVideo,
                    verticalVideo,
                    user.getId(),
                    user.getUsername(),
                    user.getRole());

            Map<String, Object> response = new HashMap<>();
            response.put("taskId", task.getTaskId());
            response.put("status", "success");
            response.put("message", "十字路口视频已上传，正在进行分析");

            log.info("十字路口视频上传成功，创建任务: taskId={}", task.getTaskId());
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } catch (IOException e) {
            log.error("上传十字路口视频失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "视频上传失败: " + e.getMessage()));
        } catch (Exception e) {
            log.error("处理十字路口视频上传时发生意外错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "处理视频时出错: " + e.getMessage()));
        }
    }

    /**
     * 获取视频分析任务的状态
     */
    @GetMapping("/{taskId}/status")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        Optional<VideoAnalysis> taskOpt = videoAnalysisService.findByTaskId(taskId);

        if (!taskOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        VideoAnalysis task = taskOpt.get();
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", task.getTaskId());
        response.put("status", task.getStatus());
        response.put("progress", task.getProgress());
        response.put("message", task.getMessage());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取视频分析结果
     */
    @GetMapping("/{taskId}/result")
    public ResponseEntity<?> getAnalysisResult(@PathVariable String taskId) {
        // 获取认证信息
        TokenInfo tokenInfo = getCurrentTokenInfo();
        
        // 输出详细的认证信息用于调试
        log.info("获取视频分析结果: taskId={}, 认证信息存在={}", taskId, tokenInfo != null);
        if (tokenInfo != null) {
            log.info("认证用户: id={}, username={}, role={}",
                    tokenInfo.getId(), tokenInfo.getUsername(), tokenInfo.getRole());
        } else {
            // 输出更多认证调试信息
            try {
                org.springframework.security.core.Authentication auth = 
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                
                if (auth != null) {
                    log.info("认证对象存在但TokenInfo为空: auth.principal={}, auth.name={}, auth.authenticated={}", 
                            auth.getPrincipal(), auth.getName(), auth.isAuthenticated());
                } else {
                    log.warn("认证对象不存在");
                }
            } catch (Exception e) {
                log.error("获取认证对象时出错: {}", e.getMessage());
            }
        }

        // 查找视频分析任务
        Optional<VideoAnalysis> taskOpt = videoAnalysisService.findByTaskId(taskId);
        
        if (!taskOpt.isPresent()) {
            log.warn("未找到指定的视频分析任务: taskId={}", taskId);
            return ResponseEntity.notFound().build();
        }
        
        VideoAnalysis task = taskOpt.get();
        
        // 放宽认证要求，允许不需要认证或认证失败的情况下访问公共视频
        if (tokenInfo == null) {
            log.info("未获取有效认证信息，但允许访问公共视频: taskId={}", taskId);
            // 仍然返回视频分析结果，但不进行权限检查
            return ResponseEntity.ok(videoAnalysisService.getVideoAnalysisResult(taskId));
        }
        
        // 如果有认证信息，可以尝试验证权限
        User user = userService.findById(tokenInfo.getId());
        
        // 如果无法获取用户信息，也允许访问公共视频
        if (user == null) {
            log.info("找不到用户信息，但允许访问公共视频: userId={}, taskId={}", 
                    tokenInfo.getId(), taskId);
            return ResponseEntity.ok(videoAnalysisService.getVideoAnalysisResult(taskId));
        }
        
        // 有用户权限时，检查是否为管理员或任务创建者
        boolean isAdmin = "admin".equalsIgnoreCase(user.getRole());
        boolean isOwner = task.getUserId() != null && task.getUserId().equals(user.getId());
        
        log.info("用户权限检查: userId={}, username={}, 是否管理员={}, 是否所有者={}", 
                user.getId(), user.getUsername(), isAdmin, isOwner);
                
        // 即使不是管理员和创建者，也返回结果，但记录日志
        if (!isAdmin && !isOwner) {
            log.warn("用户{}(角色={})访问不属于自己的视频分析结果: taskId={}, 视频所有者={}", 
                    user.getUsername(), user.getRole(), taskId, task.getUserId());
        }
        
        // 返回分析结果
        return ResponseEntity.ok(videoAnalysisService.getVideoAnalysisResult(taskId));
    }

    /**
     * 智能ID转换端点 - 支持多种格式ID查询视频分析结果
     * 可同时处理MongoDB ObjectId和UUID格式
     */
    @GetMapping("/result/{videoId}")
    public ResponseEntity<?> getVideoResultByAnyId(@PathVariable String videoId) {
        log.info("使用通用ID查询视频分析结果: videoId={}", videoId);
        
        // 获取认证信息
        TokenInfo tokenInfo = getCurrentTokenInfo();
        if (tokenInfo != null) {
            log.info("认证用户信息: id={}, username={}, role={}", 
                    tokenInfo.getId(), tokenInfo.getUsername(), tokenInfo.getRole());
        }
        
        VideoAnalysis videoAnalysis = null;
        
        try {
            // 1. 首先尝试作为MongoDB ObjectId格式查询
            if (videoId.matches("[0-9a-f]{24}")) {
                log.info("尝试作为ObjectId格式查询: {}", videoId);
                videoAnalysis = mongoTemplate.findById(videoId, VideoAnalysis.class, "analysis_videos");
                
                if (videoAnalysis != null) {
                    log.info("成功通过ObjectId(_id)找到视频分析记录");
                }
            }
            
            // 2. 如果未找到，尝试通过task_id字段查询(UUID格式)
            if (videoAnalysis == null) {
                log.info("尝试通过task_id/result_id字段查询: {}", videoId);
                
                // 构建OR查询，同时匹配多个可能的字段
                Query query = new Query(
                    new Criteria().orOperator(
                        Criteria.where("task_id").is(videoId),
                        Criteria.where("result_id").is(videoId),
                        Criteria.where("taskId").is(videoId),
                        Criteria.where("resultId").is(videoId)
                    )
                );
                
                videoAnalysis = mongoTemplate.findOne(query, VideoAnalysis.class, "analysis_videos");
                
                if (videoAnalysis != null) {
                    log.info("成功通过task_id/result_id找到视频分析记录: {}", videoAnalysis.getId());
                }
            }
            
            // 3. 如果仍未找到，尝试前8位匹配(针对前端可能截断ID的情况)
            if (videoAnalysis == null && videoId.length() >= 8) {
                log.info("尝试通过ID前缀查询(前8位): {}", videoId.substring(0, 8));
                
                // 构建正则查询以匹配前缀
                String prefix = "^" + videoId.substring(0, 8);
                Query query = new Query(
                    new Criteria().orOperator(
                        Criteria.where("task_id").regex(prefix),
                        Criteria.where("result_id").regex(prefix),
                        Criteria.where("taskId").regex(prefix),
                        Criteria.where("resultId").regex(prefix)
                    )
                );
                
                videoAnalysis = mongoTemplate.findOne(query, VideoAnalysis.class, "analysis_videos");
                
                if (videoAnalysis != null) {
                    log.info("成功通过ID前缀找到视频分析记录: {}", videoAnalysis.getId());
                }
            }
            
            // 如果找到视频分析记录
            if (videoAnalysis != null) {
                // 简化的权限检查
                if (tokenInfo != null) {
                    User user = userService.findById(tokenInfo.getId());
                    if (user != null) {
                        boolean isAdmin = "admin".equalsIgnoreCase(user.getRole());
                        boolean isOwner = videoAnalysis.getUserId() != null && 
                                         videoAnalysis.getUserId().equals(user.getId());
                        
                        if (!isAdmin && !isOwner) {
                            log.warn("非所有者访问: userId={}, videoOwner={}", 
                                    user.getId(), videoAnalysis.getUserId());
                        }
                    }
                }
                
                // 整合视频结果的额外信息
                Map<String, Object> resultMap = videoAnalysisService.getVideoAnalysisResult(videoAnalysis.getTaskId());
                return ResponseEntity.ok(resultMap);
            } else {
                log.warn("未找到视频分析记录: videoId={}", videoId);
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            log.error("查询视频分析记录时出错: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "查询视频结果失败: " + e.getMessage()));
        }
    }
    
    /**
     * 通过任务ID获取结果ID
     * 用于前端将任务ID转换为结果ID
     */
    @GetMapping("/task/{taskId}/result-id")
    public ResponseEntity<?> getResultIdByTaskId(@PathVariable String taskId) {
        log.info("获取任务对应的结果ID: taskId={}", taskId);
        
        try {
            // 尝试查找视频任务
            Query query = new Query(Criteria.where("task_id").is(taskId));
            VideoAnalysis task = mongoTemplate.findOne(query, VideoAnalysis.class);
            
            if (task != null) {
                Map<String, String> response = new HashMap<>();
                
                // 确定使用哪个ID字段作为结果ID
                String resultId = task.getResultId();
                if (resultId == null || resultId.isEmpty()) {
                    resultId = task.getTaskId(); // 回退到任务ID
                }
                
                response.put("resultId", resultId);
                response.put("taskId", task.getTaskId());
                response.put("objectId", task.getId());
                
                return ResponseEntity.ok(response);
            } else {
                log.warn("未找到对应的视频任务: taskId={}", taskId);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取结果ID时出错: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "查询结果ID失败: " + e.getMessage()));
        }
    }

    /**
     * 获取视频分析历史记录
     */
    @GetMapping("/history")
    public ResponseEntity<?> getAnalysisHistory(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        // 获取认证信息
        TokenInfo tokenInfo = getCurrentTokenInfo();

        if (tokenInfo == null) {
            log.warn("获取视频分析历史记录时未获取到有效的认证信息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "未获取到有效的认证信息，请重新登录"));
        }

        User user = userService.findById(tokenInfo.getId());
        
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "created_at"));
        Page<VideoAnalysis> history = videoAnalysisService.findByUserIdAndRole(
                user.getId(), user.getRole(), pageRequest);
        
        return ResponseEntity.ok(history);
    }

    /**
     * 重新分析视频
     */
    @PostMapping("/{taskId}/retry")
    public ResponseEntity<?> retryAnalysis(@PathVariable String taskId) {
        // 获取认证信息
        TokenInfo tokenInfo = getCurrentTokenInfo();

        if (tokenInfo == null) {
            log.warn("重新分析视频时未获取到有效的认证信息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "未获取到有效的认证信息，请重新登录"));
        }

        Optional<VideoAnalysis> taskOpt = videoAnalysisService.findByTaskId(taskId);
        
        if (!taskOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        VideoAnalysis task = taskOpt.get();
        User user = userService.findById(tokenInfo.getId());
        
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // 检查权限 - 只有管理员或者任务创建者可以重试分析
        if (!user.getRole().equals("admin") && !task.getUserId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", "您没有权限重新分析此视频"));
        }
        
        try {
        VideoAnalysis updatedTask = videoAnalysisService.retryAnalysis(taskId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", updatedTask.getTaskId());
        response.put("status", updatedTask.getStatus());
            response.put("message", "视频重新分析已开始");
        
        return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("重新分析视频时出错: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "重新分析视频失败: " + e.getMessage()));
        }
    }

    /**
     * 删除视频分析任务
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<?> deleteAnalysis(@PathVariable String taskId) {
        // 获取认证信息
        TokenInfo tokenInfo = getCurrentTokenInfo();

        if (tokenInfo == null) {
            log.warn("删除分析任务时未获取到有效的认证信息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "未获取到有效的认证信息，请重新登录"));
        }

        Optional<VideoAnalysis> taskOpt = videoAnalysisService.findByTaskId(taskId);
        
        if (!taskOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        VideoAnalysis task = taskOpt.get();
        User user = userService.findById(tokenInfo.getId());
        
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // 检查权限 - 只有管理员或者任务创建者可以删除
        if (!user.getRole().equals("admin") && !task.getUserId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", "您没有权限删除此分析任务"));
        }
        
        boolean deleted = videoAnalysisService.deleteAnalysis(taskId, user.getId(), user.getRole());
        
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "删除分析任务失败"));
        }
        
            return ResponseEntity.ok(Collections.singletonMap("message", "分析任务已删除"));
    }

    /**
     * 上传并分析视频（显式参数）
     * 这是一个替代API，直接接收用户ID和用户名作为参数
     */
    @PostMapping(value = "/upload/direct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadVideoDirect(
            @RequestParam("video") MultipartFile file,
            @RequestParam("direction") String direction,
            @RequestParam("userId") String userId,
            @RequestParam("username") String username,
            @RequestParam("role") String role) {
        
        log.info("接收到视频直接上传请求: 文件名={}, 大小={}, 方向={}, 用户ID={}, 用户名={}, 角色={}", 
                file.getOriginalFilename(), file.getSize(), direction, userId, username, role);
        
        // 验证参数合法性
        if (file.isEmpty()) {
            log.warn("上传的视频文件为空");
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "请提供有效的视频文件"));
        }
        
        // 验证方向类型
        if (!direction.equals("horizontal") && !direction.equals("vertical") && !direction.equals("intersection")) {
            log.warn("无效的视频方向: {}", direction);
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "无效的视频方向类型"));
        }
        
        try {
            log.debug("开始处理视频: userId={}, username={}, role={}", userId, username, role);
            
            // 确认用户存在
            User user = userService.findById(userId);
            if (user == null) {
                log.warn("找不到用户ID={}", userId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Collections.singletonMap("error", "用户不存在"));
            }
            
            VideoAnalysis task = videoAnalysisService.uploadAndAnalyzeVideo(
                    file, 
                    userId, 
                    username, 
                    role, 
                    direction);
            
            log.info("视频直接上传成功: taskId={}", task.getTaskId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("taskId", task.getTaskId());
            response.put("status", "success");
            response.put("message", "视频已上传，正在进行分析");
            
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } catch (IOException e) {
            log.error("视频上传处理失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "视频上传失败: " + e.getMessage()));
        } catch (Exception e) {
            log.error("处理视频时发生意外错误", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", "处理视频时发生错误: " + e.getMessage()));
        }
    }

    /**
     * 获取视频任务列表（支持高级筛选）
     */
    @GetMapping("/tasks")
    public ResponseEntity<?> getVideoTaskList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "analysisType", required = false) String analysisType,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "sortBy", defaultValue = "created_at") String sortBy,
            @RequestParam(value = "sortOrder", defaultValue = "desc") String sortOrder,
            @RequestParam(value = "userId", required = false) String userId) {

        // 获取认证信息
        TokenInfo tokenInfo = getCurrentTokenInfo();
        
        if (tokenInfo == null) {
            log.warn("获取视频任务列表时未获取到有效的认证信息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "未获取到有效的认证信息，请重新登录"));
        }
        
        User user = userService.findById(tokenInfo.getId());
        
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // 构建查询条件
        Query mongoQuery = new Query();
        
        // 权限检查：管理员可以查看所有，普通用户只能查看自己的
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            // 如果非管理员尝试查看其他用户的记录，拒绝访问
            if (userId != null && !userId.equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Collections.singletonMap("error", "您没有权限查看其他用户的记录"));
            }
            mongoQuery.addCriteria(Criteria.where("user_id").is(user.getId()));
        } else if (userId != null) {
            // 管理员按用户ID筛选
            mongoQuery.addCriteria(Criteria.where("user_id").is(userId));
        }
        
        // 添加筛选条件
        if (query != null && !query.isEmpty()) {
            // 同时搜索视频文件名和自定义视频名称
            Criteria searchCriteria = new Criteria().orOperator(
                Criteria.where("video_filename").regex(query, "i"),
                Criteria.where("videoName").regex(query, "i")
            );
            mongoQuery.addCriteria(searchCriteria);
            log.info("添加视频搜索条件：同时搜索文件名和自定义名称，关键词: {}", query);
        }
        
        if (status != null && !status.isEmpty()) {
            mongoQuery.addCriteria(Criteria.where("status").is(status));
        }
        
        if (analysisType != null && !analysisType.isEmpty()) {
            mongoQuery.addCriteria(Criteria.where("direction").is(analysisType));
        }
        
        // 日期范围筛选
        if (startDate != null && !startDate.isEmpty()) {
            try {
                LocalDateTime startDateTime = LocalDate.parse(startDate).atStartOfDay();
                mongoQuery.addCriteria(Criteria.where("created_at").gte(startDateTime));
            } catch (Exception e) {
                log.warn("无效的开始日期格式: {}", startDate);
            }
        }
        
        if (endDate != null && !endDate.isEmpty()) {
            try {
                LocalDateTime endDateTime = LocalDate.parse(endDate).atTime(23, 59, 59);
                mongoQuery.addCriteria(Criteria.where("created_at").lte(endDateTime));
            } catch (Exception e) {
                log.warn("无效的结束日期格式: {}", endDate);
            }
        }
        
        // 设置排序
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        mongoQuery.with(Sort.by(direction, sortBy));
        
        // 设置分页
        long total = mongoTemplate.count(mongoQuery, VideoAnalysis.class, "analysis_videos");
        mongoQuery.skip((long) page * pageSize);
        mongoQuery.limit(pageSize);
        
        // 执行查询
        List<VideoAnalysis> tasks = mongoTemplate.find(mongoQuery, VideoAnalysis.class, "analysis_videos");
        
        // 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("tasks", tasks);
        response.put("total", total);
        response.put("page", page);
        response.put("pageSize", pageSize);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 批量删除视频分析任务
     */
    @PostMapping("/batchDelete")
    public ResponseEntity<?> batchDeleteVideos(@RequestBody List<String> taskIds) {
        // 获取认证信息
        TokenInfo tokenInfo = getCurrentTokenInfo();
        
        if (tokenInfo == null) {
            log.warn("批量删除视频任务时未获取到有效的认证信息");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "未获取到有效的认证信息，请重新登录"));
        }
        
        User user = userService.findById(tokenInfo.getId());
        
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        List<String> successIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        
        for (String taskId : taskIds) {
            boolean deleted = videoAnalysisService.deleteAnalysis(taskId, user.getId(), user.getRole());
            if (deleted) {
                successIds.add(taskId);
            } else {
                failedIds.add(taskId);
            }
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("successCount", successIds.size());
        response.put("failedCount", failedIds.size());
        response.put("successIds", successIds);
        response.put("failedIds", failedIds);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 更新视频处理时间
     * 
     * @param taskId 视频任务ID
     * @param processingTime 处理时间（秒）
     * @return 更新结果
     */
    @PostMapping("/{taskId}/processing-time")
    public ResponseEntity<?> updateProcessingTime(
            @PathVariable String taskId,
            @RequestParam Double processingTime) {
            
        log.info("接收到更新视频处理时间请求: taskId={}, processingTime={}", taskId, processingTime);
        
        if (taskId == null || taskId.trim().isEmpty()) {
            log.warn("无效的taskId参数");
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "任务ID不能为空"));
        }
        
        if (processingTime == null || processingTime < 0) {
            log.warn("无效的processingTime参数: {}", processingTime);
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "处理时间必须是非负数"));
        }
        
        try {
            // 获取认证信息（可选）
            TokenInfo tokenInfo = getCurrentTokenInfo();
            String username = tokenInfo != null ? tokenInfo.getUsername() : "unknown";
            
            log.info("用户{}正在更新视频任务{}的处理时间: {}秒", username, taskId, processingTime);
            
            // 使用HistoryService更新处理时间
            boolean updated = historyService.updateVideoProcessingTime(taskId, processingTime);
            
            if (updated) {
                log.info("视频处理时间更新成功: taskId={}, processingTime={}", taskId, processingTime);
                
                // 更新后立即查询以验证处理时间是否正确保存
                try {
                    Optional<VideoAnalysis> analysis = videoAnalysisService.findByTaskId(taskId);
                    if (analysis.isPresent()) {
                        Double savedTime = analysis.get().getProcessingTime();
                        log.info("验证更新后的处理时间: 期望值={}秒, 实际保存值={}秒", processingTime, savedTime);
                        
                        // 如果保存的值与传入的值差异较大，记录警告
                        if (savedTime != null && Math.abs(savedTime - processingTime) > 0.5) {
                            log.warn("处理时间保存后与期望值不一致，可能存在覆盖问题: 期望值={}秒, 实际值={}秒", 
                                    processingTime, savedTime);
                        }
                    } else {
                        log.warn("无法验证处理时间更新，未找到任务记录: taskId={}", taskId);
                    }
                } catch (Exception e) {
                    log.warn("验证处理时间更新时发生错误: {}", e.getMessage());
                    // 不影响返回结果
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "处理时间已更新");
                response.put("taskId", taskId);
                response.put("processingTime", processingTime);
                return ResponseEntity.ok(response);
            } else {
                log.warn("视频处理时间更新失败: 未找到记录 taskId={}", taskId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Collections.singletonMap("error", "未找到对应的视频任务记录"));
            }
        } catch (Exception e) {
            log.error("更新视频处理时间时发生错误: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("error", "更新处理时间失败: " + e.getMessage()));
        }
    }
} 