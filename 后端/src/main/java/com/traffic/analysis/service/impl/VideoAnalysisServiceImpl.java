package com.traffic.analysis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.client.result.UpdateResult;
import com.traffic.analysis.controller.VideoProgressWebSocketController;
import com.traffic.analysis.model.VideoAnalysis;
import com.traffic.analysis.service.VideoAnalysisService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// 导入JavaCV相关类
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.bson.Document;
import com.mongodb.client.gridfs.model.GridFSFile;

/**
 * 视频分析服务实现类
 */
@Service
public class VideoAnalysisServiceImpl implements VideoAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(VideoAnalysisServiceImpl.class);
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Value("${traffic.analysis.video.upload-dir:D:/code/trafficsystem/trafficsystem/static/video/uploads}")
    private String uploadDir;
    
    @Value("${traffic.analysis.video.results-dir:D:/code/trafficsystem/trafficsystem/static/video/results}")
    private String resultsDir;
    
    @Value("${traffic.analysis.video.thumbnails-dir:D:/code/trafficsystem/trafficsystem/src/main/resources/static/images/thumbnails}")
    private String thumbnailsDir;
    
    // 线程池用于异步处理视频
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    
    // 添加WebSocket控制器
    @Autowired(required = false)
    private VideoProgressWebSocketController webSocketController;
    
    // 添加GridFS相关依赖
    @Autowired
    private GridFsTemplate gridFsTemplate;
    
    @Autowired
    private GridFsOperations gridFsOperations;
    
    @Override
    public VideoAnalysis saveVideoAnalysis(VideoAnalysis videoAnalysis) {
        if (videoAnalysis.getCreatedAt() == null) {
            videoAnalysis.setCreatedAt(LocalDateTime.now());
        }
        videoAnalysis.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(videoAnalysis);
    }

    @Override
    public Optional<VideoAnalysis> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, VideoAnalysis.class));
    }

    @Override
    public Optional<VideoAnalysis> findByTaskId(String taskId) {
        Query query = Query.query(Criteria.where("task_id").is(taskId));
        VideoAnalysis result = mongoTemplate.findOne(query, VideoAnalysis.class);
        return Optional.ofNullable(result);
    }

    @Override
    public Page<VideoAnalysis> findByUserIdAndRole(String userId, String role, Pageable pageable) {
        Query query;
        
        // 管理员可以查看所有视频分析任务
        if ("admin".equalsIgnoreCase(role)) {
            query = new Query();
        } else {
            // 普通用户只能查看自己的视频分析任务
            query = Query.query(Criteria.where("user_id").is(userId));
        }
        
        // 总数
        long total = mongoTemplate.count(query, VideoAnalysis.class);
        
        // 分页查询
        query.with(pageable);
        List<VideoAnalysis> content = mongoTemplate.find(query, VideoAnalysis.class);
        
        return PageableExecutionUtils.getPage(content, pageable, () -> total);
    }

    @Override
    public Page<VideoAnalysis> findByDirectionAndRole(String direction, String role, Pageable pageable) {
        Query query;
        
        if (role != null) {
            // 查询指定方向和角色的任务
            query = Query.query(
                Criteria.where("direction").is(direction)
                    .and("role").is(role)
            );
        } else {
            // 仅查询指定方向的任务
            query = Query.query(Criteria.where("direction").is(direction));
        }
        
        // 总数
        long total = mongoTemplate.count(query, VideoAnalysis.class);
        
        // 分页查询
        query.with(pageable);
        List<VideoAnalysis> content = mongoTemplate.find(query, VideoAnalysis.class);
        
        return PageableExecutionUtils.getPage(content, pageable, () -> total);
    }
    
    @Override
    public Page<VideoAnalysis> findByUserIdDirectionAndRole(String userId, String direction, String role, Pageable pageable) {
        Query query;
        
        // 管理员可以查看所有指定方向的任务
        if ("admin".equalsIgnoreCase(role)) {
            query = Query.query(Criteria.where("direction").is(direction));
        } else {
            // 普通用户只能查看自己创建的指定方向的任务
            query = Query.query(
                Criteria.where("direction").is(direction)
                    .and("user_id").is(userId)
            );
        }
        
        // 总数
        long total = mongoTemplate.count(query, VideoAnalysis.class);
        
        // 分页查询
        query.with(pageable);
        List<VideoAnalysis> content = mongoTemplate.find(query, VideoAnalysis.class);
        
        return PageableExecutionUtils.getPage(content, pageable, () -> total);
    }

    @Override
    public VideoAnalysis uploadAndAnalyzeVideo(
            MultipartFile videoFile, 
            String userId, 
            String username, 
            String role,
            String direction) throws IOException {
        // 生成UUID格式的任务ID
        String taskId = UUID.randomUUID().toString();
        
        // 检查上传目录是否存在，不存在则创建
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // 创建文件名
        String timestamp = String.valueOf(System.currentTimeMillis()/1000);
        String filename = "video_" + timestamp + "_" + taskId.substring(0, 8) + ".mp4";
        
        String savedFileId;
        double videoDuration = 0.0;
        
        // 使用GridFS存储视频文件，但先尝试获取视频时长
        File tempFile = null;
        try {
            // 创建临时文件
            tempFile = File.createTempFile("video_duration_", ".mp4");
            videoFile.transferTo(tempFile);
            
            // 获取视频时长
            videoDuration = getVideoDuration(tempFile);
            log.info("成功获取视频时长: {}秒", videoDuration);
            
            // 使用GridFS存储视频文件
            try (InputStream inputStream = new FileInputStream(tempFile)) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("contentType", videoFile.getContentType());
                metadata.put("filename", filename);
                metadata.put("userId", userId);
                metadata.put("username", username);
                metadata.put("uploadTime", new Date());
                metadata.put("taskId", taskId);
                if (videoDuration > 0) {
                    metadata.put("duration", videoDuration);
                }
                
                ObjectId objectId = gridFsTemplate.store(
                    inputStream, filename, videoFile.getContentType(), 
                    new Document(metadata)
                );
                savedFileId = objectId.toString();
                log.info("视频文件保存到GridFS: {}, ID: {}, 时长: {}秒", filename, savedFileId, videoDuration);
            }
        } finally {
            // 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                try {
                    tempFile.delete();
                } catch (Exception e) {
                    log.warn("清理临时文件失败: {}", e.getMessage());
                }
            }
        }
        
        // 创建视频分析任务
        VideoAnalysis videoAnalysis = new VideoAnalysis();
        videoAnalysis.setTaskId(taskId);
        videoAnalysis.setResultId(taskId); // 默认结果ID与任务ID相同
        videoAnalysis.setUserId(userId);
        videoAnalysis.setUsername(username);
        videoAnalysis.setRole(role);
        videoAnalysis.setVideoFilename(filename);
        videoAnalysis.setVideoName(""); // 确保videoName默认为空字符串
        videoAnalysis.setVideoPath(savedFileId);
        videoAnalysis.setDirection(direction);
        videoAnalysis.setStatus("queued");
        videoAnalysis.setProgress(0);
        videoAnalysis.setMessage("视频上传成功，等待分析...");
        videoAnalysis.setCreatedAt(LocalDateTime.now());
        videoAnalysis.setUpdatedAt(LocalDateTime.now());
        
        // 设置视频时长 - 确保使用setter方法
        if (videoDuration > 0) {
            videoAnalysis.setVideoDuration(videoDuration);
            log.info("设置视频分析任务的视频时长: {}秒", videoDuration);
        }
        
        // 保存分析任务
        final VideoAnalysis savedAnalysis = saveVideoAnalysis(videoAnalysis);
        
        // 验证保存是否成功
        log.info("保存视频分析任务到数据库，任务ID: {}, 视频时长: {}秒", 
                savedAnalysis.getTaskId(), savedAnalysis.getVideoDuration());
        
        // 异步处理视频分析
        executorService.submit(() -> processVideo(savedAnalysis));
        
        return savedAnalysis;
    }

    @Override
    public VideoAnalysis processIntersectionVideos(
            MultipartFile horizontalFile, 
            MultipartFile verticalFile, 
            String userId, 
            String username,
            String role) throws IOException {
        // 生成UUID格式的任务ID
        String taskId = UUID.randomUUID().toString();
        
        // 检查上传目录是否存在，不存在则创建
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // 创建水平方向文件名
        String timestamp = String.valueOf(System.currentTimeMillis()/1000);
        String horizontalFilename = "horizontal_" + timestamp + "_" + taskId.substring(0, 8) + ".mp4";
        
        // 创建垂直方向文件名
        String verticalFilename = "vertical_" + timestamp + "_" + taskId.substring(0, 8) + ".mp4";
        
        String horizontalFileId;
        String verticalFileId;
        double horizontalVideoDuration = 0.0;
        double verticalVideoDuration = 0.0;
        double totalVideoDuration = 0.0;
        
        // 处理水平方向视频
        File horizontalTempFile = null;
        try {
            // 创建临时文件以获取时长
            horizontalTempFile = File.createTempFile("horizontal_video_", ".mp4");
            horizontalFile.transferTo(horizontalTempFile);
            
            // 获取视频时长
            horizontalVideoDuration = getVideoDuration(horizontalTempFile);
            log.info("成功获取水平方向视频时长: {}秒", horizontalVideoDuration);
            
            // 使用GridFS存储水平方向视频
            try (InputStream inputStream = new FileInputStream(horizontalTempFile)) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("contentType", horizontalFile.getContentType());
                metadata.put("filename", horizontalFilename);
                metadata.put("userId", userId);
                metadata.put("username", username);
                metadata.put("uploadTime", new Date());
                metadata.put("taskId", taskId);
                metadata.put("direction", "horizontal");
                if (horizontalVideoDuration > 0) {
                    metadata.put("duration", horizontalVideoDuration);
                }
                
                ObjectId objectId = gridFsTemplate.store(
                    inputStream, horizontalFilename, horizontalFile.getContentType(), 
                    new Document(metadata)
                );
                horizontalFileId = objectId.toString();
                log.info("水平方向视频文件保存到GridFS: {}, ID: {}, 时长: {}秒", 
                        horizontalFilename, horizontalFileId, horizontalVideoDuration);
            }
        } finally {
            // 清理临时文件
            if (horizontalTempFile != null && horizontalTempFile.exists()) {
                try {
                    horizontalTempFile.delete();
                } catch (Exception e) {
                    log.warn("清理水平方向视频临时文件失败: {}", e.getMessage());
                }
            }
        }
        
        // 处理垂直方向视频
        File verticalTempFile = null;
        try {
            // 创建临时文件以获取时长
            verticalTempFile = File.createTempFile("vertical_video_", ".mp4");
            verticalFile.transferTo(verticalTempFile);
            
            // 获取视频时长
            verticalVideoDuration = getVideoDuration(verticalTempFile);
            log.info("成功获取垂直方向视频时长: {}秒", verticalVideoDuration);
        
            // 使用GridFS存储垂直方向视频
            try (InputStream inputStream = new FileInputStream(verticalTempFile)) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("contentType", verticalFile.getContentType());
                metadata.put("filename", verticalFilename);
                metadata.put("userId", userId);
                metadata.put("username", username);
                metadata.put("uploadTime", new Date());
                metadata.put("taskId", taskId);
                metadata.put("direction", "vertical");
                if (verticalVideoDuration > 0) {
                    metadata.put("duration", verticalVideoDuration);
                }
                
                ObjectId objectId = gridFsTemplate.store(
                    inputStream, verticalFilename, verticalFile.getContentType(), 
                    new Document(metadata)
                );
                verticalFileId = objectId.toString();
                log.info("垂直方向视频文件保存到GridFS: {}, ID: {}, 时长: {}秒", 
                        verticalFilename, verticalFileId, verticalVideoDuration);
            }
        } finally {
            // 清理临时文件
            if (verticalTempFile != null && verticalTempFile.exists()) {
                try {
                    verticalTempFile.delete();
                } catch (Exception e) {
                    log.warn("清理垂直方向视频临时文件失败: {}", e.getMessage());
                }
            }
        }
        
        // 总视频时长取两个视频的最大值
        totalVideoDuration = Math.max(horizontalVideoDuration, verticalVideoDuration);
        log.info("十字路口总视频时长(取最大值): {}秒", totalVideoDuration);
        
        // 创建视频分析任务
        VideoAnalysis videoAnalysis = new VideoAnalysis();
        videoAnalysis.setTaskId(taskId);
        videoAnalysis.setResultId(taskId); // 默认结果ID与任务ID相同
        videoAnalysis.setUserId(userId);
        videoAnalysis.setUsername(username);
        videoAnalysis.setRole(role);
        videoAnalysis.setVideoFilename(horizontalFilename + " + " + verticalFilename);
        videoAnalysis.setVideoName(""); // 确保videoName默认为空字符串
        videoAnalysis.setDirection("intersection");
        videoAnalysis.setStatus("queued");
        videoAnalysis.setProgress(0);
        videoAnalysis.setMessage("十字路口视频上传成功，等待分析...");
        videoAnalysis.setCreatedAt(LocalDateTime.now());
        videoAnalysis.setUpdatedAt(LocalDateTime.now());
        videoAnalysis.setMode("intersection");
        
        // 设置视频时长 - 直接使用setter方法
        if (totalVideoDuration > 0) {
            videoAnalysis.setVideoDuration(totalVideoDuration);
            log.info("设置十字路口分析任务的视频时长: {}秒", totalVideoDuration);
        }
        
        // 使用自定义字段存储两个视频的路径
        Map<String, Object> horizontalData = new HashMap<>();
        horizontalData.put("video_path", horizontalFileId);
        horizontalData.put("filename", horizontalFilename);
        horizontalData.put("duration", horizontalVideoDuration);
        videoAnalysis.setHorizontalData(horizontalData);
        
        Map<String, Object> verticalData = new HashMap<>();
        verticalData.put("video_path", verticalFileId);
        verticalData.put("filename", verticalFilename);
        verticalData.put("duration", verticalVideoDuration);
        videoAnalysis.setVerticalData(verticalData);
        
        // 保存分析任务
        final VideoAnalysis savedAnalysis = saveVideoAnalysis(videoAnalysis);
        
        // 验证保存是否成功
        log.info("保存十字路口视频分析任务到数据库，任务ID: {}, 视频时长: {}秒", 
                savedAnalysis.getTaskId(), savedAnalysis.getVideoDuration());
        
        // 异步处理视频分析
        executorService.submit(() -> processIntersection(savedAnalysis));
        
        return savedAnalysis;
    }

    @Override
    public VideoAnalysis updateProgress(String taskId, int progress) {
        Query query = Query.query(Criteria.where("task_id").is(taskId));
        Update update = new Update()
                .set("progress", progress)
                .set("updated_at", LocalDateTime.now());
        
        // 任务完成时，设置状态为已完成，并添加resultId
        if (progress >= 100) {
            update.set("status", "completed")
                  .set("completed_at", LocalDateTime.now())
                  .set("result_id", taskId); // 使用taskId作为resultId
        }
        
        mongoTemplate.updateFirst(query, update, VideoAnalysis.class);
        
        // 获取更新后的视频分析对象
        VideoAnalysis task = findByTaskId(taskId).orElse(null);
        
        // 通过WebSocket发送进度更新
        if (task != null && webSocketController != null) {
            try {
                if (progress >= 100) {
                    // 任务完成时，发送包含resultId的消息
                    Map<String, Object> message = new HashMap<>();
                    message.put("type", "processing_progress");
                    message.put("status", "completed");
                    message.put("progress", progress);
                    message.put("resultId", taskId); // 重要：添加resultId
                    message.put("message", task.getMessage());
                    webSocketController.sendProgressUpdate(taskId, message);
                } else {
                    // 常规进度更新使用旧方法
                    webSocketController.sendProgressUpdate(
                        taskId, 
                        progress, 
                        task.getStatus(), 
                        task.getMessage()
                    );
                }
            } catch (Exception e) {
                log.error("通过WebSocket发送进度更新失败: {}", e.getMessage(), e);
            }
        }
        
        return task;
    }

    @Override
    public VideoAnalysis updateStatus(String taskId, String status, String message) {
        Query query = Query.query(Criteria.where("task_id").is(taskId));
        Update update = new Update()
                .set("status", status)
                .set("updated_at", LocalDateTime.now());
        
        if (message != null && !message.isEmpty()) {
            update.set("message", message);
        }
        
        // 如果状态为已完成，设置完成时间
        if ("completed".equals(status)) {
            update.set("completed_at", LocalDateTime.now());
        }
                
        mongoTemplate.updateFirst(query, update, VideoAnalysis.class);
        
        VideoAnalysis task = findByTaskId(taskId).orElse(null);
        
        // 通过WebSocket发送状态更新
        if (task != null && webSocketController != null) {
            try {
                webSocketController.sendProgressUpdate(
                    taskId, 
                    task.getProgress(), 
                    status, 
                    message != null ? message : task.getMessage()
                );
            } catch (Exception e) {
                log.error("通过WebSocket发送状态更新失败: {}", e.getMessage(), e);
            }
        }
        
        return task;
    }

    @Override
    public VideoAnalysis completeAnalysis(String taskId, Map<String, Object> resultData) {
        Query query = Query.query(Criteria.where("task_id").is(taskId));
        VideoAnalysis task = mongoTemplate.findOne(query, VideoAnalysis.class);
        
        if (task == null) {
            log.warn("无法完成分析，任务不存在: {}", taskId);
            return null;
        }
        
        // 更新任务状态
        task.setStatus("completed");
        task.setProgress(100);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setResultId(taskId); // 设置resultId为taskId
        
        // 更新结果数据
        if (resultData != null) {
            // 车辆计数
            if (resultData.containsKey("vehicleCount")) {
                task.setVehicleCount((Integer) resultData.get("vehicleCount"));
            }
            
            // 车辆类型统计
            if (resultData.containsKey("vehicleTypeStats") && resultData.get("vehicleTypeStats") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Integer> stats = (Map<String, Integer>) resultData.get("vehicleTypeStats");
                task.setVehicleTypeStats(stats);
            }
            
            // 处理时间 - 仅在当前值为0或null时才设置
            if (resultData.containsKey("processingTime")) {
                // 检查当前处理时间是否已设置
                Double currentProcessingTime = task.getProcessingTime();
                if (currentProcessingTime == null || currentProcessingTime == 0) {
                    Double processingTime = (Double) resultData.get("processingTime");
                    task.setProcessingTime(processingTime);
                    log.info("设置初始处理时间: {}秒 (来自resultData)", processingTime);
                } else {
                    // 保留现有值，可能是前端传递的实际测量值
                    log.info("保留现有处理时间: {}秒 (忽略resultData中的值)", task.getProcessingTime());
                }
            }
            
            // 结果路径
            if (resultData.containsKey("resultPath")) {
                task.setResultPath((String) resultData.get("resultPath"));
            }
            
            // 结果视频文件名
            if (resultData.containsKey("resultVideoFilename")) {
                task.setResultVideoFilename((String) resultData.get("resultVideoFilename"));
            } else if (task.getResultPath() != null) {
                // 如果没有提供结果视频文件名，但有结果路径，创建一个默认的文件名
                task.setResultVideoFilename("analyzed_video_" + task.getTaskId() + ".mp4");
            }
            
            // 缩略图URL
            if (resultData.containsKey("thumbnailUrl")) {
                task.setThumbnailUrl((String) resultData.get("thumbnailUrl"));
            }
            
            // 如果是十字路口分析
            if ("intersection".equals(task.getMode())) {
                // 更新横向数据
                if (resultData.containsKey("horizontal_data") && resultData.get("horizontal_data") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> hData = (Map<String, Object>) resultData.get("horizontal_data");
                    task.setHorizontalData(hData);
                }
                
                // 更新纵向数据
                if (resultData.containsKey("vertical_data") && resultData.get("vertical_data") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> vData = (Map<String, Object>) resultData.get("vertical_data");
                    task.setVerticalData(vData);
                }
            }
            
            // 保存结果数据中的mode字段
            if (resultData.containsKey("mode")) {
                task.setMode((String) resultData.get("mode"));
            }
        }
        
        // 保存更新后的任务
        VideoAnalysis updatedTask = saveVideoAnalysis(task);
        
        // 通过WebSocket发送任务完成消息
        if (webSocketController != null) {
            try {
                Map<String, Object> message = new HashMap<>();
                message.put("type", "processing_progress");
                message.put("status", "completed");
                message.put("progress", 100);
                message.put("resultId", taskId); // 关键：添加resultId
                message.put("message", "视频分析已完成");
                webSocketController.sendProgressUpdate(taskId, message);
            } catch (Exception e) {
                log.error("通过WebSocket发送任务完成消息失败: {}", e.getMessage(), e);
            }
        }
        
        return updatedTask;
    }

    @Override
    public Map<String, Object> getVideoAnalysisResult(String resultId) {
        Optional<VideoAnalysis> taskOpt = findByTaskId(resultId);
        
        if (!taskOpt.isPresent()) {
            return Collections.singletonMap("error", "视频分析结果不存在");
        }
        
        VideoAnalysis task = taskOpt.get();
        Map<String, Object> result = new HashMap<>();
        
        // 基本信息
        result.put("id", task.getId());
        result.put("taskId", task.getTaskId());
        result.put("status", task.getStatus());
        result.put("progress", task.getProgress());
        result.put("vehicleCount", task.getVehicleCount());
        result.put("vehicleTypeStats", task.getVehicleTypeStats());
        result.put("createdAt", task.getCreatedAt());
        result.put("completedAt", task.getCompletedAt());
        result.put("processingTime", task.getProcessingTime());
        result.put("direction", task.getDirection());
        result.put("mode", task.getMode());
        
        // 添加视频时长字段到直接结果数据
        result.put("videoDuration", task.getVideoDuration());
        log.info("视频结果查询: 任务ID={}, 视频时长={}秒", resultId, task.getVideoDuration());
        
        // 处理GridFS中的视频和图像路径
        String resultPath = task.getResultPath();
        String resultVideoFilename = task.getResultVideoFilename();
        String thumbnailUrl = task.getThumbnailUrl();
        
        // 如果路径是ObjectId格式，则添加API路径前缀
        if (resultPath != null) {
            result.put("resultPath", resultPath); // 保存原始ID用于数据库查询
            result.put("resultVideoFilename", resultVideoFilename); // 添加结果视频文件名
            
            if (resultPath.matches("[0-9a-f]{24}")) {
                // GridFS ID
                result.put("videoUrl", "/api/media/video/" + resultPath); // 直接使用MediaController访问
            } else if (resultPath.startsWith("/")) {
                // 已经是相对URL路径
                result.put("videoUrl", resultPath);
            } else {
                // 本地路径，转换为URL
                result.put("videoUrl", "/api/static/videos/" + Paths.get(resultPath).getFileName().toString());
            }
        }
        
        // 如果缩略图是ObjectId格式，则添加API路径前缀
        if (thumbnailUrl != null) {
            if (thumbnailUrl.matches("[0-9a-f]{24}")) {
                result.put("thumbnailUrl", thumbnailUrl); // 保存原始ID用于数据库查询
                result.put("thumbnailImageUrl", "/api/media/image/" + thumbnailUrl); // 添加API路径用于前端访问
            } else if (thumbnailUrl.startsWith("/")) {
                // 已经是相对URL路径
                result.put("thumbnailUrl", thumbnailUrl);
                result.put("thumbnailImageUrl", thumbnailUrl);
            } else {
                // 本地路径，保持原样（兼容现有代码）
                result.put("thumbnailUrl", thumbnailUrl);
            }
        }
        
        // === 添加前端期望的数据结构 ===
        
        // 1. 视频信息
        // 如果没有视频信息，创建默认值
        Map<String, Object> videoInfo = new HashMap<>();
        videoInfo.put("filename", task.getVideoFilename());
        
        // 根据方向设置方向字段
        String displayDirection = "horizontal"; // 默认为横向
        if (task.getDirection() != null) {
            if ("intersection".equals(task.getDirection())) {
                displayDirection = "intersection";
            } else {
                switch (task.getDirection().toLowerCase()) {
                    case "north":
                    case "south":
                    case "vertical":
                        displayDirection = "vertical";
                        break;
                    default:
                        displayDirection = "horizontal";
                }
            }
        }
        videoInfo.put("direction", displayDirection);
        
        // 使用实际获取的视频时长，如果有
        if (task.getVideoDuration() > 0) {
            videoInfo.put("duration_seconds", task.getVideoDuration());
        } else {
            // 如果没有视频时长，提供默认值
        videoInfo.put("duration_seconds", 60); // 默认60秒
        }
        
        videoInfo.put("width", 1280);
        videoInfo.put("height", 720);
        
        // 添加存储类型信息，便于前端区分处理方式
        boolean isGridFs = resultPath != null && resultPath.matches("[0-9a-f]{24}");
        videoInfo.put("storage_type", isGridFs ? "gridfs" : "local");
        videoInfo.put("gridfs_id", isGridFs ? resultPath : null);
        
        // 添加结果视频文件名
        if (resultVideoFilename != null && !resultVideoFilename.isEmpty()) {
            videoInfo.put("result_video_filename", resultVideoFilename);
        } else if (task.getTaskId() != null) {
            // 如果没有设置结果视频文件名，生成一个默认的
            videoInfo.put("result_video_filename", "analyzed_video_" + task.getTaskId() + ".mp4");
        }
        
        result.put("video_info", videoInfo);
        
        // 2. 分析摘要
        Map<String, Object> analysisSummary = new HashMap<>();
        int totalVehicles = 30; // 默认值
        
        // 如果有车辆数量数据，使用它
        Integer vehicleCount = task.getVehicleCount();
        if (vehicleCount != null) {
            totalVehicles = vehicleCount;
        }
        
        analysisSummary.put("total_vehicles", totalVehicles);
        analysisSummary.put("traffic_density", 0.5); // 默认值
        
        // 默认高峰期
        List<Integer> peakTimes = new ArrayList<>();
        peakTimes.add(30); // 默认30秒为高峰
        analysisSummary.put("peak_times", peakTimes);
        
        // 默认方向分布
        Map<String, Integer> directionDistribution = new HashMap<>();
        directionDistribution.put("eastbound", totalVehicles / 3);
        directionDistribution.put("westbound", totalVehicles / 3);
        directionDistribution.put("northbound", totalVehicles / 6);
        directionDistribution.put("southbound", totalVehicles / 6);
        
        analysisSummary.put("direction_distribution", directionDistribution);
        result.put("analysis_summary", analysisSummary);
        
        // 3. 时间序列数据
        Map<String, Object> timeSeriesData = new HashMap<>();
        List<Integer> timestamps = new ArrayList<>();
        List<Integer> vehicleCounts = new ArrayList<>();
        Random random = new Random();
        
        for (int i = 0; i < 10; i++) {
            timestamps.add(i * 10); // 0, 10, 20...秒
            vehicleCounts.add(random.nextInt(15) + 5); // 随机车辆数
        }
        timeSeriesData.put("timestamps", timestamps);
        timeSeriesData.put("vehicle_counts", vehicleCounts);
        result.put("time_series_data", timeSeriesData);
        
        // 4. 车型分布 (使用vehicleTypeStats或创建默认值)
        if (task.getVehicleTypeStats() != null && !task.getVehicleTypeStats().isEmpty()) {
            result.put("vehicle_types", task.getVehicleTypeStats());
        } else {
            // 创建默认车型分布
            Map<String, Integer> vehicleTypes = new HashMap<>();
            vehicleTypes.put("car", totalVehicles * 3 / 5);
            vehicleTypes.put("truck", totalVehicles / 10);
            vehicleTypes.put("bus", totalVehicles / 20);
            vehicleTypes.put("motorcycle", totalVehicles / 10);
            vehicleTypes.put("bicycle", totalVehicles / 20);
            result.put("vehicle_types", vehicleTypes);
        }
        
        // 5. 优化建议
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        Map<String, Object> suggestion = new HashMap<>();
        suggestion.put("type", "traffic_flow");
        suggestion.put("message", "基于视频分析，建议优化交通信号灯配时");
        suggestion.put("severity", "medium");
        suggestions.add(suggestion);
        
        result.put("optimization_suggestions", suggestions);
        
        // 十字路口特定数据
        if ("intersection".equals(task.getMode())) {
            // 如果有存储的横向和纵向数据，使用它
            if (task.getHorizontalData() != null) {
                result.put("horizontal_data", task.getHorizontalData());
            } else {
                // 创建默认横向数据
                Map<String, Object> horizontalData = new HashMap<>();
                horizontalData.put("vehicleCount", totalVehicles / 2);
                horizontalData.put("trafficDensity", 0.6);
                
                // 创建时间序列数据
                Map<String, Object> hTimeSeriesData = new HashMap<>();
                List<Integer> hTimestamps = new ArrayList<>();
                List<Integer> hVehicleCounts = new ArrayList<>();
                
                for (int i = 0; i < 10; i++) {
                    hTimestamps.add(i * 10); // 0, 10, 20...秒
                    hVehicleCounts.add(random.nextInt(20) + 10); // 随机车辆数
                }
                
                hTimeSeriesData.put("timestamps", hTimestamps);
                hTimeSeriesData.put("vehicle_counts", hVehicleCounts);
                horizontalData.put("time_series_data", hTimeSeriesData);
                
                result.put("horizontal_data", horizontalData);
            }
            
            if (task.getVerticalData() != null) {
                result.put("vertical_data", task.getVerticalData());
            } else {
                // 创建默认纵向数据
                Map<String, Object> verticalData = new HashMap<>();
                verticalData.put("vehicleCount", totalVehicles / 2);
                verticalData.put("trafficDensity", 0.4);
                
                // 创建时间序列数据
                Map<String, Object> vTimeSeriesData = new HashMap<>();
                List<Integer> vTimestamps = new ArrayList<>();
                List<Integer> vVehicleCounts = new ArrayList<>();
                
                for (int i = 0; i < 10; i++) {
                    vTimestamps.add(i * 10); // 0, 10, 20...秒
                    vVehicleCounts.add(random.nextInt(15) + 5); // 随机车辆数
                }
                
                vTimeSeriesData.put("timestamps", vTimestamps);
                vTimeSeriesData.put("vehicle_counts", vVehicleCounts);
                verticalData.put("time_series_data", vTimeSeriesData);
                
                result.put("vertical_data", verticalData);
            }
            
            // 补充横向数据中的time_series_data
            if (result.containsKey("horizontal_data") && 
                result.get("horizontal_data") instanceof Map && 
                !((Map<?,?>)result.get("horizontal_data")).containsKey("time_series_data")) {
                
                @SuppressWarnings("unchecked")
                Map<String, Object> horizontalData = (Map<String, Object>) result.get("horizontal_data");
                
                Map<String, Object> hTimeSeriesData = new HashMap<>();
                List<Integer> hTimestamps = new ArrayList<>();
                List<Integer> hVehicleCounts = new ArrayList<>();
                
                for (int i = 0; i < 10; i++) {
                    hTimestamps.add(i * 10); // 0, 10, 20...秒
                    hVehicleCounts.add(random.nextInt(20) + 10); // 随机车辆数
                }
                
                hTimeSeriesData.put("timestamps", hTimestamps);
                hTimeSeriesData.put("vehicle_counts", hVehicleCounts);
                horizontalData.put("time_series_data", hTimeSeriesData);
            }
            
            // 补充纵向数据中的time_series_data
            if (result.containsKey("vertical_data") && 
                result.get("vertical_data") instanceof Map && 
                !((Map<?,?>)result.get("vertical_data")).containsKey("time_series_data")) {
                
                @SuppressWarnings("unchecked")
                Map<String, Object> verticalData = (Map<String, Object>) result.get("vertical_data");
                
                Map<String, Object> vTimeSeriesData = new HashMap<>();
                List<Integer> vTimestamps = new ArrayList<>();
                List<Integer> vVehicleCounts = new ArrayList<>();
                
                for (int i = 0; i < 10; i++) {
                    vTimestamps.add(i * 10); // 0, 10, 20...秒
                    vVehicleCounts.add(random.nextInt(15) + 5); // 随机车辆数
                }
                
                vTimeSeriesData.put("timestamps", vTimestamps);
                vTimeSeriesData.put("vehicle_counts", vVehicleCounts);
                verticalData.put("time_series_data", vTimeSeriesData);
            }
        }
        
        return result;
    }

    @Override
    public VideoAnalysis retryAnalysis(String taskId) {
        Optional<VideoAnalysis> taskOpt = findByTaskId(taskId);
        
        if (!taskOpt.isPresent()) {
            log.warn("无法重新分析，任务不存在: {}", taskId);
            return null;
        }
        
        VideoAnalysis task = taskOpt.get();
        
        // 重置任务状态
        task.setStatus("queued");
        task.setProgress(0);
        task.setUpdatedAt(LocalDateTime.now());
        task.setCompletedAt(null);
        task.setMessage(null);
        
        // 保存更新的任务
        VideoAnalysis updatedTask = saveVideoAnalysis(task);
        
        // 重新启动分析任务
        if ("intersection".equals(task.getMode())) {
            CompletableFuture.runAsync(() -> processIntersection(updatedTask), executorService);
        } else {
            CompletableFuture.runAsync(() -> processVideo(updatedTask), executorService);
        }
        
        return updatedTask;
    }

    @Override
    public boolean deleteAnalysis(String taskId, String userId, String role) {
        Query query;
        
        // 管理员可以删除任何任务，普通用户只能删除自己的任务
        if ("admin".equalsIgnoreCase(role)) {
            query = Query.query(Criteria.where("task_id").is(taskId));
        } else {
            query = Query.query(
                Criteria.where("task_id").is(taskId)
                    .and("user_id").is(userId)
            );
        }
        
        VideoAnalysis task = mongoTemplate.findAndRemove(query, VideoAnalysis.class);
        
        if (task != null) {
            // 删除任务相关的文件
            try {
                if (task.getVideoPath() != null && !task.getVideoPath().isEmpty()) {
                    Files.deleteIfExists(Paths.get(task.getVideoPath()));
                }
                
                if (task.getResultPath() != null && !task.getResultPath().isEmpty()) {
                    Files.deleteIfExists(Paths.get(task.getResultPath()));
                }
                
                return true;
            } catch (IOException e) {
                log.error("删除视频分析任务文件失败", e);
                return false;
            }
        }
        
        return false;
    }
    
    @Override
    public boolean renameVideo(String taskId, String videoName) {
        try {
            // 先尝试使用task_id字段查询
            Query query = Query.query(Criteria.where("task_id").is(taskId));
            VideoAnalysis video = mongoTemplate.findOne(query, VideoAnalysis.class);
            
            // 如果找不到，再尝试使用_id字段查询
            if (video == null) {
                log.info("使用task_id未找到记录，尝试使用_id查询: {}", taskId);
                try {
                    ObjectId objectId = new ObjectId(taskId);
                    query = Query.query(Criteria.where("_id").is(objectId));
                    video = mongoTemplate.findOne(query, VideoAnalysis.class);
                } catch (Exception e) {
                    log.warn("无法将ID转换为ObjectId: {}", e.getMessage());
                }
            }
            
            // 如果仍未找到，尝试使用result_id查询
            if (video == null) {
                log.info("使用_id未找到记录，尝试使用result_id查询: {}", taskId);
                query = Query.query(Criteria.where("result_id").is(taskId));
                video = mongoTemplate.findOne(query, VideoAnalysis.class);
            }
            
            if (video == null) {
                log.warn("未找到要重命名的视频：taskId={}", taskId);
                return false;
            }
            
            Update update = new Update().set("videoName", videoName);
            // 使用当前查询条件 - 此时query变量已经设置为成功找到记录的查询条件
            UpdateResult result = mongoTemplate.updateFirst(query, update, VideoAnalysis.class);
            
            if (result.getModifiedCount() > 0) {
                log.info("视频重命名成功：taskId={}, 新名称={}", taskId, videoName);
                return true;
            } else {
                log.warn("视频重命名未修改任何记录：taskId={}", taskId);
                return false;
            }
        } catch (Exception e) {
            log.error("视频重命名失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 处理单个视频（异步）
     * @param task 视频分析任务
     */
    private void processVideo(VideoAnalysis task) {
        log.info("开始处理视频任务: {}", task.getTaskId());
        
        try {
            // 更新状态为处理中
            task.setStatus("processing");
            task.setMessage("正在处理视频...");
            task.setProgress(5);
            saveVideoAnalysis(task);
            updateTaskProgress(task);
            
            // 检查路径是否为GridFS ID
            String videoPath = task.getVideoPath();
            
            if (videoPath == null || videoPath.isEmpty()) {
                log.error("视频路径为空，无法处理视频");
                task.setStatus("failed");
                task.setMessage("视频路径为空，无法处理视频");
                saveVideoAnalysis(task);
                updateTaskProgress(task);
                return;
            }
            
            // 判断是否为GridFS ID
            boolean isGridFsVideo = videoPath.matches("[0-9a-f]{24}");
            
            log.info("准备调用Python视频分析API，视频ID: {}, 是否GridFS: {}", videoPath, isGridFsVideo);
            
            // 更新进度
            task.setProgress(10);
            task.setMessage("视频准备完成，开始分析...");
            saveVideoAnalysis(task);
            updateTaskProgress(task);
                        
            // 调用Python API执行视频分析
            String apiUrl = "http://localhost:5001/analyze_video_from_gridfs";
            
            // 创建HTTP客户端
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
            
            // 准备请求数据
            String requestBody = String.format(
                "{\"video_id\": \"%s\", \"task_id\": \"%s\"}",
                videoPath, task.getTaskId()
            );
            
            // 创建HTTP请求
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
            
            log.info("发送视频分析请求到Python API: {}", apiUrl);
            
            // 更新状态为分析中
            task.setProgress(20);
            task.setMessage("Python模型正在分析视频...");
                    saveVideoAnalysis(task);
                    updateTaskProgress(task);
            
            // 发送请求
            java.net.http.HttpResponse<String> response = client.send(
                request, java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            
            // 检查响应状态
            if (response.statusCode() != 200) {
                log.error("Python API返回错误状态码: {}, 响应内容: {}", 
                         response.statusCode(), response.body());
                throw new IOException("Python API返回错误: " + response.body());
            }
            
            // 解析响应JSON
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> result = mapper.readValue(response.body(), Map.class);
            
            // 检查分析状态
            if (!"success".equals(result.get("status"))) {
                String errorMessage = (String) result.getOrDefault("message", "未知错误");
                log.error("视频分析失败: {}", errorMessage);
                    task.setStatus("failed");
                task.setMessage("视频分析失败: " + errorMessage);
                    saveVideoAnalysis(task);
                    updateTaskProgress(task);
                    return;
            }
            
            log.info("视频分析成功，结果: {}", response.body());
            
            // 更新进度
            task.setProgress(80);
            task.setMessage("视频分析完成，保存结果...");
            saveVideoAnalysis(task);
            updateTaskProgress(task);
            
            // 从结果中提取数据
            String resultFileId = (String) result.get("result_file_id");
            Integer vehicleCount = (Integer) result.get("vehicle_count");
            Double processingTime = (Double) result.get("processing_time");
            Map<String, Integer> vehicleTypes = (Map<String, Integer>) result.get("vehicle_types");
            
            // 设置结果视频文件名
            String resultVideoFilename = "analyzed_video_" + task.getTaskId() + ".mp4";
            
            // 生成结果数据
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("vehicleCount", vehicleCount);
            resultData.put("vehicleTypeStats", vehicleTypes);
            resultData.put("processingTime", processingTime);
            resultData.put("resultPath", resultFileId);
            resultData.put("resultVideoFilename", resultVideoFilename);
            
            // 如果有缩略图，更新缩略图URL
            if (task.getThumbnailUrl() == null || task.getThumbnailUrl().isEmpty()) {
                // 保存第一帧作为缩略图
                String thumbnailUrl = saveVideoThumbnail(videoPath);
                if (thumbnailUrl != null) {
                    resultData.put("thumbnailUrl", thumbnailUrl);
                    task.setThumbnailUrl(thumbnailUrl);
                }
            } else {
                resultData.put("thumbnailUrl", task.getThumbnailUrl());
            }
            
            // 更新进度为100%
            task.setProgress(100);
            task.setMessage("视频分析已完成");
            saveVideoAnalysis(task);
            updateTaskProgress(task);
            
            // 完成分析
            completeAnalysis(task.getTaskId(), resultData);
            
            log.info("视频分析任务完成: taskId={}, 结果视频ID={}, 车辆数={}", 
                    task.getTaskId(), resultFileId, vehicleCount);
            
        } catch (Exception e) {
            log.error("处理视频时出错: {}", e.getMessage(), e);
            task.setStatus("failed");
            task.setMessage("处理视频时出错: " + e.getMessage());
            saveVideoAnalysis(task);
            updateTaskProgress(task);
        }
    }
    
    /**
     * 创建结果视频文件
     * 支持将原始视频另存为结果视频，并保存到GridFS
     */
    private String createResultVideo(VideoAnalysis task) {
        try {
            // 创建结果文件名
            String resultFileName = "result_" + task.getTaskId() + ".mp4";
            
            // 检查视频路径
            if (task.getVideoPath() == null || task.getVideoPath().isEmpty()) {
                log.error("视频路径为空，无法创建结果视频");
                return null;
            }
            
            String videoPath = task.getVideoPath();
            File tempVideoFile = null;
            boolean isGridFsVideo = false;
            
            // 检查视频是否存储在GridFS
            if (videoPath.matches("[0-9a-f]{24}")) {
                isGridFsVideo = true;
                log.info("原始视频存储在GridFS: {}", videoPath);
                
                // 获取视频的GridFS资源
                GridFsResource gridFsResource = getVideoFromGridFs(videoPath);
                if (gridFsResource == null || !gridFsResource.exists()) {
                    log.error("无法从GridFS获取原始视频: {}", videoPath);
                    return null;
                }
                
                // 为了处理视频，需要先创建临时文件
                tempVideoFile = File.createTempFile("source_video_", ".mp4");
                tempVideoFile.deleteOnExit();
                
                // 将GridFS视频保存到临时文件
                try (InputStream inputStream = gridFsResource.getInputStream();
                     FileOutputStream outputStream = new FileOutputStream(tempVideoFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                
                log.info("已将GridFS视频保存为临时文件: {}", tempVideoFile.getAbsolutePath());
                videoPath = tempVideoFile.getAbsolutePath();
            }
            
            try {
                // 确认视频文件存在
                File sourceVideoFile = new File(videoPath);
                if (!sourceVideoFile.exists()) {
                    log.error("源视频文件不存在: {}", videoPath);
                    return null;
                }
                
                // 准备元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("type", "result_video");
                metadata.put("taskId", task.getTaskId());
                metadata.put("userId", task.getUserId());
                metadata.put("originalFilename", task.getVideoFilename());
                metadata.put("direction", task.getDirection());
                metadata.put("analysisDate", new Date());
                
                // 将视频复制并保存到GridFS
                String fileId = saveVideoToGridFs(sourceVideoFile.getAbsolutePath(), resultFileName, metadata);
                
                if (fileId != null) {
                    log.info("已创建结果视频并保存到GridFS，文件ID: {}", fileId);
                    
                    // 更新数据库中的视频路径
                    Query query = Query.query(Criteria.where("task_id").is(task.getTaskId()));
                    Update update = new Update().set("result_path", fileId);
                    mongoTemplate.updateFirst(query, update, VideoAnalysis.class);
                    
                    return fileId;
                } else {
                    log.error("保存视频到GridFS失败");
                }
            } finally {
                // 清理临时文件
                if (tempVideoFile != null && tempVideoFile.exists()) {
                    tempVideoFile.delete();
                    log.info("已删除临时视频文件: {}", tempVideoFile.getAbsolutePath());
                }
            }
            
            log.warn("无法创建结果视频");
            return null;
        } catch (Exception e) {
            log.error("创建结果视频失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成模拟结果数据（用于测试）
     */
    private Map<String, Object> generateMockResultData(VideoAnalysis task, String resultPath) {
        Map<String, Object> resultData = new HashMap<>();
        
        Random random = new Random();
        
        // 车辆计数
        int vehicleCount = random.nextInt(100) + 50;
        resultData.put("vehicleCount", vehicleCount);
        
        // 车辆类型统计
        Map<String, Integer> vehicleTypeStats = new HashMap<>();
        vehicleTypeStats.put("car", random.nextInt(50) + 30);
        vehicleTypeStats.put("truck", random.nextInt(20) + 5);
        vehicleTypeStats.put("bus", random.nextInt(10) + 3);
        vehicleTypeStats.put("motorcycle", random.nextInt(15) + 5);
        vehicleTypeStats.put("bicycle", random.nextInt(10) + 2);
        resultData.put("vehicleTypeStats", vehicleTypeStats);
        
        // 处理时间 - 使用合理的默认值而非随机值，优先使用前端传递的实际测量值
        // 不再生成随机的处理时间，而是使用初始值0，等待前端更新
        resultData.put("processingTime", 0.0);
        
        // 结果路径
        if (resultPath != null) {
            resultData.put("resultPath", resultPath);
        } else {
            // 如果没有创建结果文件，使用默认路径
            resultData.put("resultPath", Paths.get(resultsDir, "result_" + task.getTaskId() + ".mp4").toString());
        }
        
        // 缩略图URL
        resultData.put("thumbnailUrl", task.getThumbnailUrl() != null ? 
                    task.getThumbnailUrl() : 
                    "/static/thumbnails/default_thumb.jpg");
        
        // === 添加前端期望的数据结构 ===
        
        // 1. 视频信息
        Map<String, Object> videoInfo = new HashMap<>();
        videoInfo.put("filename", task.getVideoFilename());
        
        // 根据方向设置方向字段
        String displayDirection = "horizontal"; // 默认为横向
        if (task.getDirection() != null) {
            switch (task.getDirection().toLowerCase()) {
                case "north":
                case "south":
                case "vertical":
                    displayDirection = "vertical";
                    break;
                default:
                    displayDirection = "horizontal";
            }
        }
        videoInfo.put("direction", displayDirection);
        
        // 添加其他视频元数据
        videoInfo.put("duration_seconds", random.nextInt(120) + 60);
        videoInfo.put("width", 1280);
        videoInfo.put("height", 720);
        resultData.put("video_info", videoInfo);
        
        // 2. 分析摘要
        Map<String, Object> analysisSummary = new HashMap<>();
        analysisSummary.put("total_vehicles", vehicleCount);
        analysisSummary.put("traffic_density", random.nextDouble() * 0.8 + 0.2);
        
        // 添加高峰期时间
        List<Integer> peakTimes = new ArrayList<>();
        peakTimes.add(random.nextInt(60) + 30); // 30-90秒为高峰期
        analysisSummary.put("peak_times", peakTimes);
        
        // 添加方向分布
        Map<String, Integer> directionDistribution = new HashMap<>();
        directionDistribution.put("eastbound", random.nextInt(30) + 20);
        directionDistribution.put("westbound", random.nextInt(30) + 20);
        analysisSummary.put("direction_distribution", directionDistribution);
        resultData.put("analysis_summary", analysisSummary);
        
        // 3. 时间序列数据
        Map<String, Object> timeSeriesData = new HashMap<>();
        List<Integer> timestamps = new ArrayList<>();
        List<Integer> vehicleCounts = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            timestamps.add(i * 10); // 0, 10, 20...秒
            vehicleCounts.add(random.nextInt(15) + 5); // 车辆数
        }
        timeSeriesData.put("timestamps", timestamps);
        timeSeriesData.put("vehicle_counts", vehicleCounts);
        resultData.put("time_series_data", timeSeriesData);
        
        // 4. 车型分布 (保持与vehicleTypeStats一致)
        resultData.put("vehicle_types", vehicleTypeStats);
        
        // 5. 添加优化建议
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        Map<String, Object> suggestion1 = new HashMap<>();
        suggestion1.put("type", "traffic_flow");
        suggestion1.put("message", "车流量集中在早晚高峰期，建议错峰出行");
        suggestion1.put("severity", "medium");
        suggestions.add(suggestion1);
        
        Map<String, Object> suggestion2 = new HashMap<>();
        suggestion2.put("type", "direction");
        suggestion2.put("message", "东西方向车流量大于南北方向，建议优化信号灯配时");
        suggestion2.put("severity", "low");
        suggestions.add(suggestion2);
        
        resultData.put("optimization_suggestions", suggestions);
        
        // 设置分析模式
        resultData.put("mode", "single");
        
        return resultData;
    }

    /**
     * 处理十字路口视频（异步）
     * @param task 视频分析任务
     */
    private void processIntersection(VideoAnalysis task) {
        try {
            // 更新任务状态为处理中
            updateStatus(task.getTaskId(), "processing", "正在处理十字路口视频");
            
            // 获取视频路径数据
            Map<String, Object> horizontalData = task.getHorizontalData();
            Map<String, Object> verticalData = task.getVerticalData();
            
            if (horizontalData == null || verticalData == null) {
                log.error("缺少水平或垂直方向视频数据");
                updateStatus(task.getTaskId(), "failed", "缺少水平或垂直方向视频数据");
                return;
            }
            
            String horizontalVideoId = (String) horizontalData.get("video_path");
            String verticalVideoId = (String) verticalData.get("video_path");
            
            if (horizontalVideoId == null || verticalVideoId == null) {
                log.error("无法获取十字路口视频路径");
                updateStatus(task.getTaskId(), "failed", "无法获取十字路口视频路径");
                return;
            }
            
            // 生成缩略图（使用水平方向视频）
            if (task.getThumbnailUrl() == null || task.getThumbnailUrl().isEmpty()) {
                String thumbnailUrl = saveVideoThumbnail(horizontalVideoId);
                    if (thumbnailUrl != null) {
                        log.info("为十字路口视频生成缩略图: {}", thumbnailUrl);
                        // 更新任务状态
                        Query query = Query.query(Criteria.where("task_id").is(task.getTaskId()));
                        Update update = new Update().set("thumbnail_url", thumbnailUrl);
                        mongoTemplate.updateFirst(query, update, VideoAnalysis.class);
                        task.setThumbnailUrl(thumbnailUrl);
                    }
                }
            
            // 更新进度
            task.setProgress(10);
            task.setMessage("准备分析水平方向视频...");
            saveVideoAnalysis(task);
            updateTaskProgress(task);
            
            // 处理水平方向视频
            ObjectMapper mapper = new ObjectMapper();
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
            
            // 准备水平方向视频分析请求
            String apiUrl = "http://localhost:5001/analyze_video_from_gridfs";
            String horizontalRequestBody = String.format(
                "{\"video_id\": \"%s\", \"task_id\": \"h_%s\"}",
                horizontalVideoId, task.getTaskId()
            );
            
            log.info("发送水平方向视频分析请求...");
            
            // 更新进度
            task.setProgress(20);
            task.setMessage("分析水平方向视频...");
            saveVideoAnalysis(task);
            updateTaskProgress(task);
            
            // 发送水平方向分析请求
            java.net.http.HttpRequest horizontalRequest = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(horizontalRequestBody))
                .build();
            
            java.net.http.HttpResponse<String> horizontalResponse = client.send(
                horizontalRequest, java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            
            // 检查响应状态
            if (horizontalResponse.statusCode() != 200) {
                log.error("Python API返回错误状态码: {}, 响应内容: {}", 
                         horizontalResponse.statusCode(), horizontalResponse.body());
                throw new IOException("水平方向视频分析失败: " + horizontalResponse.body());
            }
            
            // 解析水平方向分析结果
            Map<String, Object> horizontalResult = mapper.readValue(horizontalResponse.body(), Map.class);
            
            if (!"success".equals(horizontalResult.get("status"))) {
                String errorMessage = (String) horizontalResult.getOrDefault("message", "未知错误");
                log.error("水平方向视频分析失败: {}", errorMessage);
                throw new IOException("水平方向视频分析失败: " + errorMessage);
            }
            
            // 提取水平方向分析结果
            String horizontalResultFileId = (String) horizontalResult.get("result_file_id");
            Integer horizontalVehicleCount = (Integer) horizontalResult.get("vehicle_count");
            Map<String, Integer> horizontalVehicleTypes = (Map<String, Integer>) horizontalResult.get("vehicle_types");
            
            log.info("水平方向视频分析完成: 车辆数={}, 结果ID={}", 
                    horizontalVehicleCount, horizontalResultFileId);
            
            // 更新水平方向数据
            horizontalData.put("result_file_id", horizontalResultFileId);
            horizontalData.put("vehicle_count", horizontalVehicleCount);
            horizontalData.put("vehicle_types", horizontalVehicleTypes);
            
            // 更新进度
            task.setProgress(50);
            task.setMessage("准备分析垂直方向视频...");
            saveVideoAnalysis(task);
            updateTaskProgress(task);
            
            // 处理垂直方向视频
            String verticalRequestBody = String.format(
                "{\"video_id\": \"%s\", \"task_id\": \"v_%s\"}",
                verticalVideoId, task.getTaskId()
            );
            
            log.info("发送垂直方向视频分析请求...");
            
            // 更新进度
            task.setProgress(60);
            task.setMessage("分析垂直方向视频...");
            saveVideoAnalysis(task);
            updateTaskProgress(task);
            
            // 发送垂直方向分析请求
            java.net.http.HttpRequest verticalRequest = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(verticalRequestBody))
                .build();
            
            java.net.http.HttpResponse<String> verticalResponse = client.send(
                verticalRequest, java.net.http.HttpResponse.BodyHandlers.ofString()
            );
            
            // 检查响应状态
            if (verticalResponse.statusCode() != 200) {
                log.error("Python API返回错误状态码: {}, 响应内容: {}", 
                         verticalResponse.statusCode(), verticalResponse.body());
                throw new IOException("垂直方向视频分析失败: " + verticalResponse.body());
            }
            
            // 解析垂直方向分析结果
            Map<String, Object> verticalResult = mapper.readValue(verticalResponse.body(), Map.class);
            
            if (!"success".equals(verticalResult.get("status"))) {
                String errorMessage = (String) verticalResult.getOrDefault("message", "未知错误");
                log.error("垂直方向视频分析失败: {}", errorMessage);
                throw new IOException("垂直方向视频分析失败: " + errorMessage);
            }
            
            // 提取垂直方向分析结果
            String verticalResultFileId = (String) verticalResult.get("result_file_id");
            Integer verticalVehicleCount = (Integer) verticalResult.get("vehicle_count");
            Map<String, Integer> verticalVehicleTypes = (Map<String, Integer>) verticalResult.get("vehicle_types");
            
            log.info("垂直方向视频分析完成: 车辆数={}, 结果ID={}", 
                    verticalVehicleCount, verticalResultFileId);
            
            // 更新垂直方向数据
            verticalData.put("result_file_id", verticalResultFileId);
            verticalData.put("vehicle_count", verticalVehicleCount);
            verticalData.put("vehicle_types", verticalVehicleTypes);
            
            // 更新任务进度
            task.setProgress(90);
            task.setMessage("合并分析结果...");
            saveVideoAnalysis(task);
            updateTaskProgress(task);
            
            // 合并结果数据
            Map<String, Object> resultData = new HashMap<>();
            
            // 总体车辆计数（水平+垂直）
            int totalVehicles = horizontalVehicleCount + verticalVehicleCount;
            resultData.put("vehicleCount", totalVehicles);
            
            // 合并车辆类型统计
            Map<String, Integer> totalVehicleTypes = new HashMap<>();
            
            // 添加水平方向数据
            for (Map.Entry<String, Integer> entry : horizontalVehicleTypes.entrySet()) {
                totalVehicleTypes.put(entry.getKey(), entry.getValue());
            }
            
            // 添加垂直方向数据（累加相同类型）
            for (Map.Entry<String, Integer> entry : verticalVehicleTypes.entrySet()) {
                String key = entry.getKey();
                Integer value = entry.getValue();
                
                if (totalVehicleTypes.containsKey(key)) {
                    totalVehicleTypes.put(key, totalVehicleTypes.get(key) + value);
                } else {
                    totalVehicleTypes.put(key, value);
                }
            }
            
            resultData.put("vehicleTypeStats", totalVehicleTypes);
            
            // 计算总处理时间
            Double horizontalProcessingTime = (Double) horizontalResult.get("processing_time");
            Double verticalProcessingTime = (Double) verticalResult.get("processing_time");
            Double totalProcessingTime = horizontalProcessingTime + verticalProcessingTime;
            resultData.put("processingTime", totalProcessingTime);
            
            // 使用水平方向视频作为主要结果视频
            resultData.put("resultPath", horizontalResultFileId);
            
            // 设置缩略图
            resultData.put("thumbnailUrl", task.getThumbnailUrl());
            
            // 保存单独的方向数据
            resultData.put("horizontal_data", horizontalData);
            resultData.put("vertical_data", verticalData);
            
            // 设置分析模式
            resultData.put("mode", "intersection");
            
            // 完成分析
            task.setProgress(100);
            task.setMessage("十字路口视频分析已完成");
            saveVideoAnalysis(task);
            updateTaskProgress(task);
            
            completeAnalysis(task.getTaskId(), resultData);
            
            log.info("十字路口视频分析完成: 任务ID={}, 总车辆数={}", task.getTaskId(), totalVehicles);
            
        } catch (Exception e) {
            log.error("处理十字路口视频失败: {}", e.getMessage(), e);
            updateStatus(task.getTaskId(), "failed", "处理失败: " + e.getMessage());
        }
    }

    /**
     * 创建十字路口结果视频（复制原始视频作为结果）
     */
    private String createIntersectionResultVideo(VideoAnalysis task) {
        try {
            // 确保结果目录存在
            File resultDirFile = new File(resultsDir);
            if (!resultDirFile.exists()) {
                resultDirFile.mkdirs();
            }
            
            // 创建结果文件路径
            String resultFileName = "intersection_" + task.getTaskId() + ".mp4";
            Path resultPath = Paths.get(resultsDir, resultFileName);
            
            // 获取水平视频路径（作为主要视频）
            String videoPathString = task.getVideoPath();
            if (videoPathString != null && videoPathString.contains(";")) {
                String[] paths = videoPathString.split(";");
                if (paths.length >= 1) {
                    Path sourcePath = Paths.get(paths[0]); // 使用第一个视频（水平视频）
                    if (Files.exists(sourcePath)) {
                        Files.copy(sourcePath, resultPath);
                        log.info("已创建十字路口结果视频: {}", resultPath);
                        return resultPath.toString();
                    }
                }
            }
            
            log.warn("无法创建十字路口结果视频");
            return null;
        } catch (Exception e) {
            log.error("创建十字路口结果视频失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成模拟十字路口结果数据（用于测试）
     */
    private Map<String, Object> generateMockIntersectionResultData(VideoAnalysis task, String resultPath) {
        Map<String, Object> resultData = new HashMap<>();
        
        Random random = new Random();
        
        // 总体车辆计数
        int totalVehicles = random.nextInt(200) + 100;
        resultData.put("vehicleCount", totalVehicles);
        
        // 车辆类型统计
        Map<String, Integer> vehicleTypeStats = new HashMap<>();
        vehicleTypeStats.put("car", random.nextInt(100) + 60);
        vehicleTypeStats.put("truck", random.nextInt(30) + 10);
        vehicleTypeStats.put("bus", random.nextInt(20) + 5);
        vehicleTypeStats.put("motorcycle", random.nextInt(25) + 10);
        vehicleTypeStats.put("bicycle", random.nextInt(15) + 5);
        resultData.put("vehicleTypeStats", vehicleTypeStats);
        
        // 处理时间
        resultData.put("processingTime", random.nextDouble() * 200 + 100);
        
        // 结果路径
        if (resultPath != null) {
            resultData.put("resultPath", resultPath);
        } else {
            // 如果没有创建结果文件，使用默认路径
            resultData.put("resultPath", Paths.get(resultsDir, "intersection_" + task.getTaskId() + ".mp4").toString());
        }
        
        // 缩略图URL
        resultData.put("thumbnailUrl", task.getThumbnailUrl() != null ? 
                    task.getThumbnailUrl() : 
                    "/static/thumbnails/intersection_default.jpg");
        
        // 横向视频数据
        Map<String, Object> horizontalData = new HashMap<>();
        horizontalData.put("vehicleCount", random.nextInt(100) + 50);
        Map<String, Integer> hVehicleTypeStats = new HashMap<>();
        hVehicleTypeStats.put("car", random.nextInt(60) + 30);
        hVehicleTypeStats.put("truck", random.nextInt(20) + 5);
        hVehicleTypeStats.put("bus", random.nextInt(10) + 2);
        hVehicleTypeStats.put("motorcycle", random.nextInt(15) + 5);
        horizontalData.put("vehicleTypeStats", hVehicleTypeStats);
        horizontalData.put("trafficDensity", random.nextDouble() * 0.8 + 0.2);
        
        // === 添加横向视频前端期望的数据格式 ===
        
        // 横向视频时间序列数据
        Map<String, Object> hTimeSeriesData = new HashMap<>();
        List<Integer> hTimestamps = new ArrayList<>();
        List<Integer> hVehicleCounts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            hTimestamps.add(i * 10); // 0, 10, 20...秒
            hVehicleCounts.add(random.nextInt(20) + 10); // 更多车辆
        }
        hTimeSeriesData.put("timestamps", hTimestamps);
        hTimeSeriesData.put("vehicle_counts", hVehicleCounts);
        horizontalData.put("time_series_data", hTimeSeriesData);
        
        resultData.put("horizontal_data", horizontalData);
        
        // 纵向视频数据
        Map<String, Object> verticalData = new HashMap<>();
        verticalData.put("vehicleCount", random.nextInt(100) + 50);
        Map<String, Integer> vVehicleTypeStats = new HashMap<>();
        vVehicleTypeStats.put("car", random.nextInt(60) + 30);
        vVehicleTypeStats.put("truck", random.nextInt(15) + 5);
        vVehicleTypeStats.put("bus", random.nextInt(10) + 3);
        vVehicleTypeStats.put("motorcycle", random.nextInt(10) + 5);
        verticalData.put("vehicleTypeStats", vVehicleTypeStats);
        verticalData.put("trafficDensity", random.nextDouble() * 0.8 + 0.2);
        
        // === 添加纵向视频前端期望的数据格式 ===
        
        // 纵向视频时间序列数据
        Map<String, Object> vTimeSeriesData = new HashMap<>();
        List<Integer> vTimestamps = new ArrayList<>();
        List<Integer> vVehicleCounts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            vTimestamps.add(i * 10); // 0, 10, 20...秒
            vVehicleCounts.add(random.nextInt(15) + 5); // 车辆数
        }
        vTimeSeriesData.put("timestamps", vTimestamps);
        vTimeSeriesData.put("vehicle_counts", vVehicleCounts);
        verticalData.put("time_series_data", vTimeSeriesData);
        
        resultData.put("vertical_data", verticalData);
        
        // === 添加与单视频分析相同的前端期望数据 ===
        
        // 1. 视频信息 (使用合并的视频信息)
        Map<String, Object> videoInfo = new HashMap<>();
        videoInfo.put("filename", task.getVideoFilename());
        videoInfo.put("direction", "intersection"); // 十字路口没有方向
        videoInfo.put("duration_seconds", random.nextInt(120) + 90); // 稍长的视频
        videoInfo.put("width", 1280);
        videoInfo.put("height", 720);
        resultData.put("video_info", videoInfo);
        
        // 2. 分析摘要
        Map<String, Object> analysisSummary = new HashMap<>();
        analysisSummary.put("total_vehicles", totalVehicles);
        analysisSummary.put("traffic_density", random.nextDouble() * 0.8 + 0.2);
        
        // 添加高峰期时间
        List<Integer> peakTimes = new ArrayList<>();
        peakTimes.add(random.nextInt(60) + 30); // 30-90秒为高峰期
        peakTimes.add(random.nextInt(60) + 100); // 100-160秒为另一个高峰期
        analysisSummary.put("peak_times", peakTimes);
        
        // 添加方向分布
        Map<String, Integer> directionDistribution = new HashMap<>();
        directionDistribution.put("eastbound", random.nextInt(40) + 30);
        directionDistribution.put("westbound", random.nextInt(40) + 30);
        directionDistribution.put("northbound", random.nextInt(35) + 25);
        directionDistribution.put("southbound", random.nextInt(35) + 25);
        analysisSummary.put("direction_distribution", directionDistribution);
        resultData.put("analysis_summary", analysisSummary);
        
        // 3. 时间序列数据 (结合两个方向的数据)
        Map<String, Object> combinedTimeSeriesData = new HashMap<>();
        List<Integer> timestamps = new ArrayList<>();
        List<Integer> vehicleCounts = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            timestamps.add(i * 10);
            // 组合横向和纵向的车辆数
            int combinedCount = hVehicleCounts.get(i) + vVehicleCounts.get(i);
            vehicleCounts.add(combinedCount);
        }
        combinedTimeSeriesData.put("timestamps", timestamps);
        combinedTimeSeriesData.put("vehicle_counts", vehicleCounts);
        resultData.put("time_series_data", combinedTimeSeriesData);
        
        // 4. 车型分布
        resultData.put("vehicle_types", vehicleTypeStats);
        
        // 5. 优化建议
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        Map<String, Object> suggestion1 = new HashMap<>();
        suggestion1.put("type", "traffic_flow");
        suggestion1.put("message", "横向车流量较大，建议延长东西方向绿灯时间");
        suggestion1.put("severity", "medium");
        suggestions.add(suggestion1);
        
        Map<String, Object> suggestion2 = new HashMap<>();
        suggestion2.put("type", "peak_time");
        suggestion2.put("message", "预计高峰期在8:00-9:00和17:00-18:00，建议增加交通指挥");
        suggestion2.put("severity", "high");
        suggestions.add(suggestion2);
        
        Map<String, Object> suggestion3 = new HashMap<>();
        suggestion3.put("type", "density");
        suggestion3.put("message", "十字路口东北角车流密度较高，建议优化通行效率");
        suggestion3.put("severity", "medium");
        suggestions.add(suggestion3);
        
        resultData.put("optimization_suggestions", suggestions);
        
        // 设置分析模式
        resultData.put("mode", "intersection");
        
        return resultData;
    }

    /**
     * 从视频中提取缩略图并保存到GridFS
     * @param videoPath 视频文件路径或GridFS文件ID
     * @return 缩略图文件ID
     */
    private String saveVideoThumbnail(String videoPath) {
        if (videoPath == null || videoPath.isEmpty()) {
            log.warn("视频路径为空，无法生成缩略图");
            return null;
        }
        
        try {
            // 生成唯一文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String fileName = "thumbnail_" + timestamp + ".jpg";
            
            // 确定视频源 - 可能是文件路径或GridFS ID
            String actualVideoPath = videoPath;
            File tempVideoFile = null;
            
            // 检查是否为GridFS ID (24位十六进制字符串)
            if (videoPath.matches("[0-9a-f]{24}")) {
                log.info("视频源是GridFS ID，需要获取临时文件: {}", videoPath);
                
                try {
                    // 从GridFS获取视频文件
                    GridFsResource gridFsResource = getVideoFromGridFs(videoPath);
                    
                    if (gridFsResource != null && gridFsResource.exists()) {
                        // 创建临时文件
                        tempVideoFile = File.createTempFile("video_", ".mp4");
                        tempVideoFile.deleteOnExit();
                        
                        // 将GridFS文件保存为临时文件
                        try (InputStream inputStream = gridFsResource.getInputStream();
                             java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempVideoFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }
                        
                        log.info("已从GridFS获取视频并保存为临时文件: {}", tempVideoFile.getAbsolutePath());
                        actualVideoPath = tempVideoFile.getAbsolutePath();
                    } else {
                        log.error("在GridFS中未找到视频: {}", videoPath);
                        return null;
                    }
                } catch (Exception e) {
                    log.error("从GridFS获取视频时出错: {}", e.getMessage(), e);
                    return null;
                }
            } else {
                // 检查本地文件是否存在
                File videoFile = new File(videoPath);
                if (!videoFile.exists()) {
                    log.error("本地视频文件不存在: {}", videoPath);
                    return null;
                }
            }
            
            // 使用JavaCV提取缩略图
            log.info("使用JavaCV从视频提取缩略图: {}", actualVideoPath);
            FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(actualVideoPath);
            grabber.start();
            
            // 跳到视频的第一秒
            grabber.setTimestamp(1000000); // 微秒单位，1秒 = 1000000微秒
            Frame frame = grabber.grabImage();
            
            try {
                if (frame != null) {
                    Java2DFrameConverter converter = new Java2DFrameConverter();
                    BufferedImage bufferedImage = converter.convert(frame);
                    grabber.stop();
                    
                    if (bufferedImage != null) {
                        // 准备元数据
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("type", "thumbnail");
                        metadata.put("sourceVideo", videoPath);
                        metadata.put("timestamp", timestamp);
                        
                        // 保存到GridFS
                        String fileId = saveThumbnailToGridFs(bufferedImage, fileName, metadata);
                        if (fileId != null) {
                            log.info("缩略图已生成并保存到GridFS，ID: {}", fileId);
                            
                            // 清理临时文件
                            if (tempVideoFile != null && tempVideoFile.exists()) {
                                tempVideoFile.delete();
                                log.info("已删除临时视频文件: {}", tempVideoFile.getAbsolutePath());
                            }
                            
                            return fileId;  // 返回文件ID
                        }
                    }
                } else {
                    log.warn("无法从视频提取帧");
                }
            } finally {
                // 确保关闭grabber
                if (grabber != null) {
                    try {
                        grabber.stop();
                        grabber.release();
                    } catch (Exception e) {
                        log.warn("关闭视频grabber时出错", e);
                    }
                }
                
                // 清理临时文件
                if (tempVideoFile != null && tempVideoFile.exists()) {
                    tempVideoFile.delete();
                    log.info("已删除临时视频文件: {}", tempVideoFile.getAbsolutePath());
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("生成视频缩略图时出错: {}", e.getMessage(), e);
            return null;
        }
    }

    // 添加保存视频到GridFS的方法
    private String saveVideoToGridFs(String filePath, String filename, Map<String, Object> metadata) {
        try {
            log.info("开始将视频保存到GridFS: {}", filePath);
            
            File videoFile = new File(filePath);
            if (!videoFile.exists()) {
                log.error("视频文件不存在: {}", filePath);
                return null;
            }
            
            // 创建元数据对象
            DBObject metaData = new BasicDBObject();
            if (metadata != null) {
                metadata.forEach(metaData::put);
            }
            metaData.put("contentType", "video/mp4");
            metaData.put("uploadDate", new Date());
            
            // 保存文件到GridFS
            try (InputStream inputStream = new FileInputStream(videoFile)) {
                ObjectId id = gridFsTemplate.store(
                    inputStream,
                    filename,
                    "video/mp4",
                    metaData
                );
                
                log.info("视频已保存到GridFS，文件ID: {}", id.toString());
                return id.toString();
            }
        } catch (Exception e) {
            log.error("保存视频到GridFS失败", e);
            return null;
        }
    }
    
    // 添加保存图像到GridFS的方法
    private String saveThumbnailToGridFs(BufferedImage image, String filename, Map<String, Object> metadata) {
        try {
            log.info("开始将缩略图保存到GridFS: {}", filename);
            
            // 将BufferedImage转换为字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            
            // 创建元数据对象
            DBObject metaData = new BasicDBObject();
            if (metadata != null) {
                metadata.forEach(metaData::put);
            }
            metaData.put("contentType", "image/jpeg");
            metaData.put("uploadDate", new Date());
            
            // 保存图像到GridFS
            ObjectId id = gridFsTemplate.store(
                new ByteArrayInputStream(imageBytes),
                filename,
                "image/jpeg",
                metaData
            );
            
            log.info("图像已保存到GridFS，文件ID: {}", id.toString());
            return id.toString();
        } catch (Exception e) {
            log.error("保存图像到GridFS失败", e);
            return null;
        }
    }
    
    // 从GridFS中获取视频
    private GridFsResource getVideoFromGridFs(String fileId) {
        try {
            ObjectId objectId = new ObjectId(fileId);
            return gridFsOperations.getResource(
                gridFsTemplate.findOne(new Query(Criteria.where("_id").is(objectId)))
            );
        } catch (Exception e) {
            log.error("从GridFS获取视频失败", e);
            return null;
        }
    }
    
    // 从GridFS中获取图像
    private GridFsResource getImageFromGridFs(String fileId) {
        try {
            ObjectId objectId = new ObjectId(fileId);
            return gridFsOperations.getResource(
                gridFsTemplate.findOne(new Query(Criteria.where("_id").is(objectId)))
            );
        } catch (Exception e) {
            log.error("从GridFS获取图像失败", e);
            return null;
        }
    }

    /**
     * 更新任务进度并通过WebSocket发送通知
     * @param task 视频分析任务
     */
    private void updateTaskProgress(VideoAnalysis task) {
        // 如果WebSocket控制器存在，发送进度更新
        if (webSocketController != null) {
            try {
                // 创建进度消息
                Map<String, Object> progressMessage = new HashMap<>();
                progressMessage.put("taskId", task.getTaskId());
                progressMessage.put("status", task.getStatus());
                progressMessage.put("progress", task.getProgress());
                progressMessage.put("message", task.getMessage());
                
                // 发送进度更新
                webSocketController.sendProgressUpdate(task.getTaskId(), progressMessage);
            } catch (Exception e) {
                log.error("通过WebSocket发送任务进度失败", e);
            }
        }
    }

    /**
     * 使用FFmpeg获取视频时长的工具方法
     * @param videoFile 视频文件
     * @return 视频时长（秒）
     */
    private double getVideoDuration(File videoFile) {
        double duration = 0;
        
        if (videoFile == null || !videoFile.exists()) {
            log.error("视频文件不存在或为空");
            return duration;
        }
        
        log.info("开始获取视频时长，文件路径: {}, 文件大小: {}字节", 
                videoFile.getAbsolutePath(), videoFile.length());
        
        try {
            // 首先尝试使用JavaCV获取时长（更可靠）
            try {
                FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoFile);
                grabber.start();
                duration = grabber.getLengthInTime() / 1000000.0; // 微秒转秒
                grabber.stop();
                grabber.release();
                
                if (duration > 0) {
                    log.info("使用JavaCV成功获取视频时长: {}秒", duration);
                    return duration;
                } else {
                    log.warn("JavaCV获取视频时长为0，尝试使用FFmpeg命令行");
                }
            } catch (Exception e) {
                log.warn("使用JavaCV获取视频时长失败: {}, 尝试使用FFmpeg命令行", e.getMessage());
            }
            
            // 备选方案：使用FFmpeg命令行
            List<String> command = new ArrayList<>();
            command.add("ffmpeg");
            command.add("-i");
            command.add(videoFile.getAbsolutePath());
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (line.contains("Duration:")) {
                        // 提取时长，格式通常是 "Duration: 00:00:30.57"
                        log.info("找到时长行: {}", line);
                        int durationIndex = line.indexOf("Duration:") + 10;
                        String durationStr = line.substring(durationIndex, durationIndex + 11).trim();
                        log.info("提取的时长字符串: {}", durationStr);
                        
                        String[] parts = durationStr.split(":");
                        if (parts.length >= 3) {
                            try {
                                double hours = Double.parseDouble(parts[0]);
                                double minutes = Double.parseDouble(parts[1]);
                                double seconds = Double.parseDouble(parts[2]);
                                duration = hours * 3600 + minutes * 60 + seconds;
                                log.info("成功解析视频时长: {}秒 ({}:{}:{})", 
                                        duration, parts[0], parts[1], parts[2]);
                                break;
                            } catch (NumberFormatException e) {
                                log.error("解析时长字符串失败: {} - {}", durationStr, e.getMessage());
                            }
                        }
                    }
                }
            }
            
            // 如果没有找到时长信息，记录完整输出以便调试
            if (duration <= 0) {
                log.warn("未能从FFmpeg输出中提取时长，完整输出: {}", output.toString());
            }
            
            // 等待进程完成
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("FFmpeg进程在10秒内未完成，强制终止");
                process.destroyForcibly();
            }
            
        } catch (Exception e) {
            log.error("获取视频时长时发生异常: {}", e.getMessage(), e);
        }
        
        log.info("最终获取的视频时长: {}秒", duration);
        return duration;
    }
} 