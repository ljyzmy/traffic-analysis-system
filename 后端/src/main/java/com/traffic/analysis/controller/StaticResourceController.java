package com.traffic.analysis.controller;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * 静态资源控制器
 * 提供从GridFS获取静态资源的接口
 */
@RestController
@RequestMapping("/api/static")
@Slf4j
public class StaticResourceController {

    @Autowired
    private GridFsTemplate gridFsTemplate;
    
    @Autowired
    private GridFsOperations gridFsOperations;
    
    /**
     * 从GridFS获取视频文件
     * 支持范围请求，用于视频播放的快进和拖动功能
     * 
     * @param id GridFS文件ID
     * @param request HTTP请求，包含Range头信息
     * @return 视频流响应
     */
    @GetMapping("/videos/{id}")
    public ResponseEntity<?> getVideo(@PathVariable String id, HttpServletRequest request) {
        log.info("接收静态视频请求：ID={}", id);
        
        try {
            // 尝试将ID转换为ObjectId
            ObjectId objectId;
            try {
                objectId = new ObjectId(id);
            } catch (IllegalArgumentException e) {
                log.warn("无效的ObjectId格式: {}, 尝试使用原始字符串", id);
                objectId = null;
            }
            
            // 查找GridFS文件
            GridFSFile file = null;
            
            // 先尝试用ObjectId查找
            if (objectId != null) {
                file = gridFsTemplate.findOne(new Query(Criteria.where("_id").is(objectId)));
            }
            
            // 如果找不到，尝试用原始字符串查找
            if (file == null) {
                file = gridFsTemplate.findOne(new Query(Criteria.where("_id").is(id)));
            }
            
            // 如果都找不到，返回404
            if (file == null) {
                log.warn("未找到视频文件：ID={}", id);
                return ResponseEntity.notFound().build();
            }
            
            log.debug("找到视频文件: ID={}, 名称={}, 大小={}", 
                    file.getObjectId() != null ? file.getObjectId().toString() : id,
                    file.getFilename(), 
                    file.getLength());
            
            // 获取文件内容和元数据
            GridFsResource resource = gridFsOperations.getResource(file);
            if (resource == null || !resource.exists()) {
                log.error("视频文件资源不存在: ID={}", id);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("文件资源不存在");
            }
            
            // 确定内容类型 - 从元数据或文件名中提取
            String contentType = Optional.ofNullable(file.getMetadata())
                    .map(meta -> meta.getString("contentType"))
                    .orElse(null);
                    
            if (contentType == null) {
                // 尝试从文件名判断内容类型
                String filename = file.getFilename();
                if (filename != null) {
                    if (filename.toLowerCase().endsWith(".mp4")) {
                        contentType = "video/mp4";
                    } else if (filename.toLowerCase().endsWith(".webm")) {
                        contentType = "video/webm";
                    } else if (filename.toLowerCase().endsWith(".mov")) {
                        contentType = "video/quicktime";
                    } else if (filename.toLowerCase().endsWith(".avi")) {
                        contentType = "video/x-msvideo";
                    } else {
                        // 默认MP4
                        contentType = "video/mp4";
                    }
                } else {
                    // 默认MP4
                    contentType = "video/mp4";
                }
            }
            
            // 获取Range请求头
            String rangeHeader = request.getHeader(HttpHeaders.RANGE);
            
            if (rangeHeader != null && !rangeHeader.isEmpty()) {
                try {
                    // 处理Range请求
                    long fileSize = file.getLength();
                    
                    // 解析Range头
                    // 格式为: "bytes=start-end" 或 "bytes=start-"
                    String[] ranges = rangeHeader.substring("bytes=".length()).split("-");
                    long rangeStart = Long.parseLong(ranges[0]);
                    long rangeEnd = ranges.length > 1 && !ranges[1].isEmpty() 
                            ? Long.parseLong(ranges[1]) 
                            : fileSize - 1;
                            
                    // 验证范围有效性
                    if (rangeStart > rangeEnd || rangeStart < 0 || rangeEnd >= fileSize) {
                        // 如果范围无效，返回整个文件
                        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                                .build();
                    }
                    
                    // 计算内容长度
                    long contentLength = rangeEnd - rangeStart + 1;
                    
                    // 记录范围请求日志
                    log.debug("处理视频范围请求: ID={}, Range={}-{}, Size={}", 
                            id, rangeStart, rangeEnd, contentLength);
                    
                    // 准备范围响应
                    InputStream inputStream = resource.getInputStream();
                    
                    // 跳到开始位置
                    long skipped = inputStream.skip(rangeStart);
                    if (skipped != rangeStart) {
                        log.warn("跳过字节数不匹配: 期望={}, 实际={}", rangeStart, skipped);
                    }
                    
                    // 创建InputStreamResource，以便读取指定范围的数据
                    final long finalRangeStart = rangeStart;
                    final long finalRangeEnd = rangeEnd;
                    final long finalContentLength = contentLength;
                    
                    // 使用标准InputStreamResource返回
                    try {
                        // 读取指定范围的数据到字节数组
                        byte[] rangeBytes = new byte[(int)contentLength];
                        int bytesRead = 0;
                        int offset = 0;
                        int len = (int)contentLength;

                        while (offset < contentLength && (bytesRead = inputStream.read(rangeBytes, offset, len - offset)) != -1) {
                            offset += bytesRead;
                        }
                        
                        // 创建InputStreamResource用于响应
                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(rangeBytes);
                        InputStreamResource rangeResource = new InputStreamResource(byteArrayInputStream);
                                
                        // 返回206 Partial Content响应
                        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                                .contentType(MediaType.parseMediaType(contentType))
                                .contentLength(finalContentLength)
                                .header(HttpHeaders.CONTENT_RANGE, "bytes " + finalRangeStart + "-" + finalRangeEnd + "/" + fileSize)
                                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(finalContentLength))
                                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                                .body(rangeResource);
                    } catch (Exception e) {
                        log.error("创建范围响应资源失败: {}", e.getMessage(), e);
                        // 关闭流，避免资源泄露
                        try {
                            inputStream.close();
                        } catch (IOException closeEx) {
                            log.warn("关闭输入流失败", closeEx);
                        }
                        
                        // 降级到非范围请求处理
                        log.info("降级为完整视频传输");
                    }
                } catch (Exception e) {
                    log.error("处理视频范围请求失败: ID={}, 错误={}", id, e.getMessage(), e);
                    // 降级到非范围请求处理
                }
            }
            
            // 如果没有Range请求或处理Range请求失败，返回整个视频文件
            log.info("返回完整视频文件: ID={}, 文件名={}, 大小={} bytes", 
                    id, file.getFilename(), file.getLength());
            
            // 使用InputStreamResource而不是StreamingResponseBody
            InputStreamResource videoResource = new InputStreamResource(resource.getInputStream());
            
            return ResponseEntity.status(HttpStatus.OK)
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(file.getLength())
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getFilename() + "\"")
                    // 添加缓存控制头
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                    .body(videoResource);
            
        } catch (Exception e) {
            log.error("获取视频文件失败: ID={}, 错误={}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("获取视频失败: " + e.getMessage());
        }
    }
} 