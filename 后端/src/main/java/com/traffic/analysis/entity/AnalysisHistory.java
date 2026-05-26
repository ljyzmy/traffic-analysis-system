package com.traffic.analysis.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * 分析历史记录实体类
 * 对应MongoDB的analysis_history集合
 */
@Data
@Document(collection = "analysis_history")
public class AnalysisHistory {

    @Id
    private String id;
    
    @Field("user_id")
    private String userId;
    
    @Field("username")
    private String username;
    
    @Field("file_name")
    private String fileName;
    
    @Field("image_url")
    private String imageUrl;
    
    @Field("vehicle_count")
    private int vehicleCount;
    
    @Field("inference_time")
    private double inferenceTime;
    
    @Field("analysis_start_time")
    private LocalDateTime analysisStartTime;
    
    @Field("analysis_end_time")
    private LocalDateTime analysisEndTime;
    
    @Field("create_time")
    private LocalDateTime createTime;
    
    @Field("update_time")
    private LocalDateTime updateTime;
    
    @Field("result_image_base64")
    private String resultImageBase64;
    
    @Field("vehicle_type_stats")
    private Map<String, Integer> vehicleTypeStats = new HashMap<>();
    
    @Field("analysis_result")
    private Map<String, Object> analysisResult = new HashMap<>();
    
    @Field("message")
    private String message;
    
    @Field("timestamp")
    private String timestamp;
    
    @Field("status")
    private String status;
    
    @Field("type")
    private String type;
    
    @Field("analyst")
    private String analyst;
    
    @Field("result_id")
    private String resultId;
    
    @Field("video_name")
    private String videoName;
    
    @Field("request_source")
    private String requestSource;
    
    @Field("formatted_duration")
    private String formattedDuration;
    
    @Field("formatted_time")
    private String formattedTime;
    
    @Field("history_id")
    private String historyId;
    
    @Field("related_id")
    private String relatedId;
    
    public String getFormattedDuration() {
        return formattedDuration;
    }
    
    public void setFormattedDuration(String formattedDuration) {
        this.formattedDuration = formattedDuration;
    }
    
    public String getFormattedTime() {
        return formattedTime;
    }
    
    public void setFormattedTime(String formattedTime) {
        this.formattedTime = formattedTime;
    }
    
    public String getRequestSource() {
        return requestSource;
    }
    
    public void setRequestSource(String requestSource) {
        this.requestSource = requestSource;
    }
    
    public String getHistoryId() {
        return historyId;
    }
    
    public void setHistoryId(String historyId) {
        this.historyId = historyId;
    }
    
    public String getRelatedId() {
        return relatedId;
    }
    
    public void setRelatedId(String relatedId) {
        this.relatedId = relatedId;
    }
}