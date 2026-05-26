package com.traffic.analysis.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Data
@Document(collection = "analysis_results")
public class AnalysisResult {
    @Id
    private String id;
    
    @Field("userId")
    private String userId;
    
    @Field("username")
    private String username;
    
    @Field("status")
    private String status;
    
    @Field("message")
    private String message;
    
    @Field("vehicleCount")
    private int vehicleCount;
    
    @Field("detections")
    private List<Detection> detections;
    
    @Field("inferenceTime")
    private double inferenceTime;
    
    @JsonIgnore
    @Field("resultImageBase64")
    private String resultImageBase64; // 使用JsonIgnore避免大型Base64数据被序列化到JSON
    
    @Field("imageUrl")
    private String imageUrl;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Field("timestamp")
    private LocalDateTime timestamp;
    
    // 添加新字段：车辆类型统计
    @Field("vehicleTypeStats")
    private Map<String, Integer> vehicleTypeStats = new HashMap<>();
    
    // 添加新字段：分析开始时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Field("analysisStartTime")
    private LocalDateTime analysisStartTime;
    
    // 添加新字段：分析请求来源
    @Field("requestSource")
    private String requestSource;
    
    // 添加新字段：分析人员
    @Field("analyst")
    private String analyst;
    
    // 分析人员的getter和setter方法
    public String getAnalyst() {
        return analyst;
    }
    
    public void setAnalyst(String analyst) {
        this.analyst = analyst;
    }
    
    @Data
    public static class Detection {
        @Field("classId")
        private int classId;
        
        @Field("className")
        private String className;
        
        // 提供两种访问方式支持不同的命名风格
        @JsonProperty("class_id")
        public int getClass_id() {
            return classId;
        }
        
        @JsonProperty("class_id")
        public void setClass_id(int classId) {
            this.classId = classId;
        }
        
        @JsonProperty("class_name")
        public String getClass_name() {
            return className;
        }
        
        @JsonProperty("class_name")
        public void setClass_name(String className) {
            this.className = className;
        }
        
        @Field("confidence")
        private double confidence;
        
        @Field("bbox")
        private double[] bbox;
        
        // 安全的getter，避免空指针异常
        public double[] getBboxSafe() {
            return bbox != null ? bbox : new double[]{0, 0, 0, 0};
        }
        
        public String getClassNameSafe() {
            return className != null ? className : "未知车型";
        }
        
        public double getConfidenceSafe() {
            return confidence >= 0 ? confidence : 0;
        }
    }
    
    // 安全的getter方法，避免空指针异常
    public List<Detection> getDetectionsSafe() {
        return detections != null ? detections : java.util.Collections.emptyList();
    }
    
    public LocalDateTime getTimestampSafe() {
        return timestamp != null ? timestamp : LocalDateTime.now();
    }
    
    public String getImageUrlSafe() {
        return imageUrl != null ? imageUrl : "";
    }
    
    public String getResultImageBase64Safe() {
        return resultImageBase64 != null ? resultImageBase64 : "";
    }
    
    // 添加车辆类型统计的辅助方法
    public void updateVehicleTypeStats() {
        if (detections == null) return;
        
        vehicleTypeStats = new HashMap<>();
        
        for (Detection detection : detections) {
            String vehicleType = detection.getClassNameSafe();
            vehicleTypeStats.put(vehicleType, vehicleTypeStats.getOrDefault(vehicleType, 0) + 1);
        }
    }
}