package com.traffic.analysis.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 视频分析结果数据模型
 * 对应MongoDB的analysis_videos集合
 */
@Data
@Document(collection = "analysis_videos")
public class VideoAnalysis {
    @Id
    private String id;

    @Field("task_id")
    private String taskId;

    @Field("user_id")
    private String userId;
    
    @Field("username")
    private String username;
    
    @Field("role")
    private String role;  // 用户角色：admin或user
    
    @Field("video_filename")
    private String videoFilename;
    
    @Field("video_path")
    private String videoPath;
    
    @Field("video_name")
    private String videoName;  // 视频自定义名称
    
    @Field("video_duration")
    private double videoDuration;  // 视频时长（秒）
    
    @Field("direction")
    private String direction;  // horizontal(横向)、vertical(纵向)、intersection(十字路口)
    
    @Field("status")
    private String status;  // queued, processing, completed, failed
    
    @Field("message")
    private String message;
    
    @Field("progress")
    private int progress; // 0-100
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Field("created_at")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Field("updated_at")
    private LocalDateTime updatedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Field("completed_at")
    private LocalDateTime completedAt;
    
    @Field("result_path")
    private String resultPath;
    
    @Field("result_video_filename")
    private String resultVideoFilename;  // 结果视频文件名
    
    @Field("thumbnail_url")
    private String thumbnailUrl;
    
    @Field("vehicle_count")
    private int vehicleCount;
    
    @Field("processing_time")
    private double processingTime;
    
    @Field("vehicle_type_stats")
    private Map<String, Integer> vehicleTypeStats = new HashMap<>();
    
    // 十字路口分析的特定字段
    @Field("horizontal_data")
    private Map<String, Object> horizontalData;
    
    @Field("vertical_data")
    private Map<String, Object> verticalData;
    
    @Field("mode")
    private String mode;  // single(单向), intersection(十字路口)

    // 结果ID - 用于关联分析结果
    @Field("result_id")
    private String resultId;
} 