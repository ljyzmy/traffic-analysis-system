package com.traffic.analysis.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * 检测结果数据模型
 */
@Data
public class DetectionResult {
    private String status;
    
    @JsonProperty("vehicle_count")
    private int vehicleCount;
    
    private List<Detection> detections;
    
    @JsonProperty("inference_time")
    private double inferenceTime;
    
    @JsonProperty("result_image_base64")
    private String resultImageBase64;
    
    private String imageUrl;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;
    
    private String message; // 错误信息
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime analysisStartTime;
    
    @JsonProperty("vehicle_type_stats")
    private Map<String, Integer> vehicleTypeStats = new HashMap<>();
    
    private String requestSource;
    
    private List<Map<String, Object>> detectedObjects = new ArrayList<>();
    
    /**
     * 单个检测对象数据
     */
    @Data
    public static class Detection {
        @JsonProperty("class_id")
        private int classId;
        
        @JsonProperty("class_name")
        private String className;
        
        private double confidence;
        
        private List<Double> bbox;  // [x1, y1, x2, y2]
        
        public String getClassNameSafe() {
            return className != null ? className : "未知车型";
        }
    }
    
    /**
     * 更新车辆类型统计
     */
    public void updateVehicleTypeStats() {
        if (detectedObjects == null || detectedObjects.isEmpty()) {
            vehicleTypeStats = new HashMap<>();
            return;
        }

        Map<String, Integer> stats = new HashMap<>();

        for (Map<String, Object> obj : detectedObjects) {
            if (obj.containsKey("class")) {
                String className = String.valueOf(obj.get("class"));
                stats.put(className, stats.getOrDefault(className, 0) + 1);
            }
        }

        this.vehicleTypeStats = stats;
    }
    
    /**
     * 获取结果图片URL，确保格式一致
     */
    public String getResultImageUrl() {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        
        // 确保URL格式为 /api/images/xxx.jpg
        if (!imageUrl.startsWith("/api/images/") && !imageUrl.startsWith("http")) {
            return "/api/images/" + imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
        }
        
        return imageUrl;
    }
} 