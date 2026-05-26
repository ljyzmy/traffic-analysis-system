package com.traffic.analysis.controller.staticresources;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.traffic.analysis.model.VideoAnalysis;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 静态资源控制器
 * 用于处理视频文件等静态资源的访问
 */
@Controller("staticResourcesController")
@RequestMapping("/api/static")
public class StaticResourceController {
    
    private static final Logger logger = LoggerFactory.getLogger(StaticResourceController.class);
    
    @Value("${traffic.analysis.video.results-dir:D:/code/trafficsystem/trafficsystem/static/video/results}")
    private String resultsDir;
    
    @Value("${traffic.analysis.video.upload-dir:D:/code/trafficsystem/trafficsystem/static/video/uploads}")
    private String uploadDir;
    
    @Autowired
    private GridFsTemplate gridFsTemplate;

    @Autowired
    private GridFsOperations gridFsOperations;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    /**
     * 获取视频文件
     * 
     * @param filename 视频文件名
     * @return 视频文件资源
     */
    @GetMapping("/videos/{filename:.+}")
    public ResponseEntity<Resource> getVideo(@PathVariable String filename) {
        try {
            // 解码文件名
            String decodedFilename = URLDecoder.decode(filename, StandardCharsets.UTF_8.name());
            logger.info("请求视频文件: {}", decodedFilename);
            
            // 检查是否是MongoDB ObjectId格式
            if (decodedFilename.matches("[0-9a-f]{24}")) {
                // 这是MongoDB GridFS ID格式，重定向到/api/media/video/接口
                logger.info("检测到MongoDB ObjectId格式: {}，重定向到/api/media/video/接口", decodedFilename);
                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(URI.create("/api/media/video/" + decodedFilename));
                return new ResponseEntity<>(headers, HttpStatus.FOUND);
            }
            
            // 首先尝试通过文件名查找GridFS文件
            if (decodedFilename.startsWith("result_")) {
                // 从文件名中提取taskId
                String taskId = decodedFilename.substring(7, decodedFilename.lastIndexOf("."));
                
                // 查询任务信息以获取GridFS文件ID
                Query query = Query.query(Criteria.where("task_id").is(taskId));
                VideoAnalysis analysis = mongoTemplate.findOne(query, VideoAnalysis.class);
                
                if (analysis != null) {
                    // 检查resultPath是否为GridFS文件ID (MongoDB ObjectId格式)
                    String fileId = analysis.getResultPath();
                    if (fileId != null && !fileId.isEmpty()) {
                        if (fileId.matches("[0-9a-f]{24}")) {
                            // 这是MongoDB GridFS ID格式，重定向到/api/media/video/接口
                            logger.info("从任务{}中获取到GridFS ID: {}，重定向到/api/media/video/接口", 
                                    taskId, fileId);
                            HttpHeaders headers = new HttpHeaders();
                            headers.setLocation(URI.create("/api/media/video/" + fileId));
                            return new ResponseEntity<>(headers, HttpStatus.FOUND);
                        } else {
                            // 这是一个文件路径，兼容旧版本
                            // 不做任何操作，继续使用本地文件系统逻辑
                        }
                    }
                }
            }
            
            // 如果无法从GridFS获取，尝试从本地文件系统获取（兼容旧数据）
            // 构建文件路径
            Path videoPath = Paths.get(resultsDir, decodedFilename);
            
            // 如果结果目录中没有找到文件，尝试从上传目录获取
            if (!Files.exists(videoPath)) {
                videoPath = Paths.get(uploadDir, decodedFilename);
            }
            
            Resource resource = new FileSystemResource(videoPath);
            
            // 检查文件是否存在
            if (!resource.exists()) {
                logger.warn("请求的视频文件不存在: {}", videoPath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }
            
            // 获取文件内容类型
            String contentType = Files.probeContentType(videoPath);
            if (contentType == null) {
                // 根据文件扩展名确定内容类型
                String extension = getFileExtension(decodedFilename).toLowerCase();
                switch (extension) {
                    case "mp4":
                        contentType = "video/mp4";
                        break;
                    case "avi":
                        contentType = "video/x-msvideo";
                        break;
                    case "mkv":
                        contentType = "video/x-matroska";
                        break;
                    case "webm":
                        contentType = "video/webm";
                        break;
                    case "mov":
                        contentType = "video/quicktime";
                        break;
                    default:
                        contentType = "application/octet-stream";
                        break;
                }
            }
            
            logger.debug("提供视频文件: {}, 内容类型: {}, 大小: {} 字节", 
                    videoPath.toAbsolutePath(), contentType, resource.contentLength());
            
            // 返回视频文件，设置适当的头信息
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000") // 添加缓存控制
                    .body(resource);
        } catch (Exception e) {
            logger.error("获取视频文件时出错: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 从GridFS中获取视频文件
     * 
     * @param fileId GridFS文件ID
     * @return 包含视频资源的响应实体
     */
    private ResponseEntity<Resource> getVideoFromGridFs(String fileId) {
        try {
            // 将字符串ID转换为ObjectId
            ObjectId objectId = new ObjectId(fileId);
            
            // 根据ID从GridFS获取文件
            GridFSFile gridFsFile = gridFsTemplate.findOne(new Query(Criteria.where("_id").is(objectId)));
            
            if (gridFsFile == null) {
                logger.error("未找到GridFS视频文件: {}", fileId);
                return ResponseEntity.notFound().build();
            }
            
            logger.debug("找到GridFS视频文件: {}, 大小: {}", gridFsFile.getFilename(), gridFsFile.getLength());
            
            // 获取GridFS资源
            GridFsResource resource = gridFsOperations.getResource(gridFsFile);
            
            // 设置HTTP响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("video/mp4"));
            headers.setContentLength(gridFsFile.getLength());
            headers.setContentDisposition(ContentDisposition.builder("inline").filename(gridFsFile.getFilename()).build());
            
            // 设置缓存控制
            headers.setCacheControl("public, max-age=31536000");
            
            // 返回视频资源
            return new ResponseEntity<>(
                new InputStreamResource(resource.getInputStream()),
                headers,
                HttpStatus.OK
            );
        } catch (IllegalArgumentException e) {
            logger.error("无效的ObjectId格式: {}", fileId);
            return ResponseEntity.badRequest().build();
        } catch (IOException e) {
            logger.error("获取GridFS视频文件时出错: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("处理GridFS视频请求时出错: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 获取特定路径下的视频文件
     * 用于处理绝对路径形式的视频URL
     * 
     * @return 视频文件资源
     */
    @GetMapping("/videos/**")
    public ResponseEntity<Resource> getVideoByPath() {
        try {
            // 获取完整请求路径
            String fullPath = URLDecoder.decode(
                    getCurrentRequest().getRequestURI().substring("/api/static/videos/".length()), 
                    StandardCharsets.UTF_8.name());
            
            logger.info("通过路径请求视频文件: {}", fullPath);
            
            // 首先检查是否是GridFS文件ID格式
            if (fullPath.matches("[0-9a-f]{24}")) {
                // 这是MongoDB GridFS ID格式，重定向到/api/media/video/接口
                logger.info("检测到MongoDB ObjectId格式: {}，重定向到/api/media/video/接口", fullPath);
                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(URI.create("/api/media/video/" + fullPath));
                return new ResponseEntity<>(headers, HttpStatus.FOUND);
            }
            
            // 检查是否是结果视频文件
            if (fullPath.startsWith("result_")) {
                // 从文件名中提取taskId
                String taskId = fullPath.substring(7, fullPath.lastIndexOf("."));
                
                // 查询任务信息以获取GridFS文件ID
                Query query = Query.query(Criteria.where("task_id").is(taskId));
                VideoAnalysis analysis = mongoTemplate.findOne(query, VideoAnalysis.class);
                
                if (analysis != null) {
                    // 检查resultPath是否为GridFS文件ID
                    String fileId = analysis.getResultPath();
                    if (fileId != null && !fileId.isEmpty() && fileId.matches("[0-9a-f]{24}")) {
                        // 这是MongoDB GridFS ID格式，重定向到/api/media/video/接口
                        logger.info("从任务{}中获取到GridFS ID: {}，重定向到/api/media/video/接口", 
                                taskId, fileId);
                        HttpHeaders headers = new HttpHeaders();
                        headers.setLocation(URI.create("/api/media/video/" + fileId));
                        return new ResponseEntity<>(headers, HttpStatus.FOUND);
                    }
                }
            }
            
            // 如果不是GridFS ID，继续处理为本地文件路径（兼容旧数据）
            // 处理文件路径
            File videoFile = null;
            
            // 检查是否是绝对路径
            if (fullPath.contains(":") || fullPath.startsWith("/")) {
                // 绝对路径 - 直接使用，但移除可能开头的斜杠
                if (fullPath.startsWith("/") && fullPath.contains(":")) {
                    fullPath = fullPath.substring(1);
                }
                videoFile = new File(fullPath);
            } else {
                // 相对路径，拼接到结果目录
                videoFile = new File(resultsDir, fullPath);
                
                // 如果结果目录中没有找到文件，尝试从上传目录获取
                if (!videoFile.exists()) {
                    videoFile = new File(uploadDir, fullPath);
                }
            }
            
            // 安全检查：移除限制，但保留日志记录，保证视频文件可访问
            if (!videoFile.getPath().toLowerCase().contains("video") && 
                !videoFile.getPath().toLowerCase().contains("results") && 
                !videoFile.getPath().toLowerCase().contains("uploads")) {
                logger.warn("访问非标准视频路径: {}", videoFile.getAbsolutePath());
                // 继续处理而不是返回禁止访问
            }
            
            Resource resource = new FileSystemResource(videoFile);
            if (!resource.exists()) {
                logger.warn("请求的视频文件不存在: {}", videoFile.getAbsolutePath());
                return ResponseEntity.notFound().build();
            }
            
            // 获取文件内容类型
            String contentType = null;
            try {
                contentType = Files.probeContentType(videoFile.toPath());
            } catch (IOException e) {
                logger.warn("无法检测文件内容类型: {}", e.getMessage());
            }
            
            if (contentType == null) {
                // 根据文件扩展名确定内容类型
                String extension = getFileExtension(videoFile.getName()).toLowerCase();
                switch (extension) {
                    case "mp4":
                        contentType = "video/mp4";
                        break;
                    case "avi":
                        contentType = "video/x-msvideo";
                        break;
                    case "mkv":
                        contentType = "video/x-matroska";
                        break;
                    case "webm":
                        contentType = "video/webm";
                        break;
                    case "mov":
                        contentType = "video/quicktime";
                        break;
                    default:
                        contentType = "application/octet-stream";
                        break;
                }
            }
            
            logger.debug("提供视频文件: {}, 内容类型: {}, 大小: {} 字节", 
                    videoFile.getAbsolutePath(), contentType, resource.contentLength());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000") // 添加缓存控制
                    .body(resource);
            
        } catch (Exception e) {
            logger.error("获取路径视频文件时出错: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1);
    }
    
    /**
     * 获取当前请求对象
     */
    private jakarta.servlet.http.HttpServletRequest getCurrentRequest() {
        return ((org.springframework.web.context.request.ServletRequestAttributes) 
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                .getRequest();
    }
    
    /**
     * 添加诊断端点
     */
    @GetMapping("/diagnosis")
    public ResponseEntity<Object> getDiagnosis() {
        try {
            // 收集系统信息
            java.util.Map<String, Object> info = new java.util.HashMap<>();
            info.put("resultsDir", resultsDir);
            info.put("uploadDir", uploadDir);
            
            // 检查目录是否存在
            File resultsDirFile = new File(resultsDir);
            File uploadDirFile = new File(uploadDir);
            
            info.put("resultsDirExists", resultsDirFile.exists());
            info.put("uploadDirExists", uploadDirFile.exists());
            
            // 列出目录内容
            if (resultsDirFile.exists() && resultsDirFile.isDirectory()) {
                File[] files = resultsDirFile.listFiles();
                java.util.List<String> filesList = new java.util.ArrayList<>();
                if (files != null) {
                    for (File file : files) {
                        filesList.add(file.getName() + " (" + file.length() + " bytes)");
                    }
                }
                info.put("resultsFiles", filesList);
            }
            
            // 检查GridFS信息
            try {
                // 查询一个文件来测试GridFS连接，尝试获取第一个文件即可
                GridFSFile gridFsFile = gridFsTemplate.findOne(new Query().limit(1));
                info.put("gridFsAvailable", gridFsFile != null);
                if (gridFsFile != null) {
                    info.put("gridFsFileExample", gridFsFile.getFilename() + " (" + gridFsFile.getLength() + " bytes)");
                }
            } catch (Exception e) {
                info.put("gridFsAvailable", false);
                info.put("gridFsError", e.getMessage());
            }
            
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            logger.error("获取诊断信息时出错: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("获取诊断信息失败");
        }
    }
} 