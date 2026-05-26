package com.traffic.analysis.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;

/**
 * WebSocket视频进度控制器
 * 用于处理视频分析进度的实时推送
 */
@Controller
public class VideoProgressWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(VideoProgressWebSocketController.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * 处理视频进度消息
     * @param taskId 任务ID
     * @param message 进度消息
     * @return 进度消息
     */
    @MessageMapping("/video-progress/{taskId}")
    @SendTo("/topic/video-progress/{taskId}")
    public Map<String, Object> sendProgress(@DestinationVariable String taskId, Map<String, Object> message) {
        logger.debug("收到WebSocket进度消息: taskId={}, message={}", taskId, message);
        return message;
    }
    
    /**
     * 推送视频进度更新
     * @param taskId 任务ID
     * @param progress 进度
     * @param status 状态
     * @param message 消息
     */
    public void sendProgressUpdate(String taskId, int progress, String status, String message) {
        try {
            // 构造进度消息
            Map<String, Object> progressData = Map.of(
                "taskId", taskId,
                "progress", progress,
                "status", status,
                "message", message
            );
            
            // 推送到特定任务的主题
            String destination = "/topic/video-progress/" + taskId;
            logger.debug("推送视频进度更新: destination={}, data={}", destination, progressData);
            messagingTemplate.convertAndSend(destination, progressData);
        } catch (Exception e) {
            logger.error("推送视频进度更新失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送包含结果ID的进度更新
     * @param taskId 任务ID
     * @param message 包含进度、状态等信息的消息对象
     */
    public void sendProgressUpdate(String taskId, Map<String, Object> message) {
        try {
            // 如果是完成状态消息且没有resultId，添加taskId作为resultId
            if ("completed".equals(message.get("status")) && !message.containsKey("resultId")) {
                // 创建可修改的Map
                Map<String, Object> updatedMessage = new HashMap<>(message);
                updatedMessage.put("resultId", taskId);
                message = updatedMessage;
            }
            
            // 添加统一的消息类型
            if (!message.containsKey("type")) {
                // 创建可修改的Map
                Map<String, Object> updatedMessage = new HashMap<>(message);
                updatedMessage.put("type", "processing_progress");
                message = updatedMessage;
            }
            
            // 推送到特定任务的主题
            String destination = "/topic/video-progress/" + taskId;
            logger.info("已发送进度更新到任务 {}: {}", taskId, message);
            messagingTemplate.convertAndSend(destination, message);
        } catch (Exception e) {
            logger.error("推送视频进度更新失败: {}", e.getMessage(), e);
        }
    }
} 