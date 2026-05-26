package com.traffic.analysis.controller;

import com.traffic.analysis.model.VideoAnalysis;
import com.traffic.analysis.service.VideoAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 视频报告控制器
 * 用于处理视频分析报告的访问
 */
@Controller
@RequestMapping("/api/video-analysis")
public class VideoReportController {
    
    private static final Logger log = LoggerFactory.getLogger(VideoReportController.class);
    
    @Autowired
    private VideoAnalysisService videoAnalysisService;

    /**
     * 获取视频分析报告
     * 返回HTML格式的报告，设置允许嵌入iframe的选项
     */
    @GetMapping("/{taskId}/report")
    public ResponseEntity<String> getVideoAnalysisReport(@PathVariable String taskId) {
        try {
            log.info("请求视频分析报告: {}", taskId);
            
            // 获取分析结果
            Optional<VideoAnalysis> taskOpt = videoAnalysisService.findByTaskId(taskId);
            
            if (!taskOpt.isPresent()) {
                log.warn("未找到视频分析任务: {}", taskId);
                return ResponseEntity.notFound().build();
            }
            
            VideoAnalysis task = taskOpt.get();
            
            // 获取分析结果数据
            Map<String, Object> resultData = videoAnalysisService.getVideoAnalysisResult(taskId);
            
            if (resultData == null || resultData.isEmpty() || resultData.containsKey("error")) {
                log.warn("获取视频分析结果失败: {}", resultData != null && resultData.containsKey("error") ? resultData.get("error") : "结果为空");
                return ResponseEntity.badRequest().body("获取分析结果失败");
            }
            
            // 生成HTML报告
            String htmlReport = generateHtmlReport(task, resultData);
            
            // 设置HTTP头，允许在iframe中嵌入
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_HTML);
            headers.set("X-Frame-Options", "SAMEORIGIN");
            headers.set("Content-Security-Policy", "frame-ancestors 'self' http://localhost:* http://127.0.0.1:*");
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(htmlReport);
                
        } catch (Exception e) {
            log.error("获取视频分析报告时出错: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("处理分析报告时出错");
        }
    }
    
    /**
     * 生成HTML格式的分析报告
     */
    private String generateHtmlReport(VideoAnalysis task, Map<String, Object> resultData) {
        
        // 获取车辆类型统计
        Map<String, Integer> vehicleTypeStats = task.getVehicleTypeStats();
        if (vehicleTypeStats == null) {
            vehicleTypeStats = new HashMap<>();
        }
        
        int carCount = vehicleTypeStats.getOrDefault("car", 0);
        int truckCount = vehicleTypeStats.getOrDefault("truck", 0);
        int busCount = vehicleTypeStats.getOrDefault("bus", 0);
        int motorcycleCount = vehicleTypeStats.getOrDefault("motorcycle", 0);
        int totalVehicles = task.getVehicleCount();
        
        // 创建HTML报告
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html lang='zh-CN'>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>视频分析报告</title>");
        html.append("<style>");
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 20px; color: #333; }");
        html.append(".container { max-width: 800px; margin: 0 auto; }");
        html.append(".header { padding: 20px 0; border-bottom: 1px solid #eee; }");
        html.append(".header h1 { margin: 0; color: #2c3e50; }");
        html.append(".meta-info { color: #7f8c8d; font-size: 14px; margin-top: 5px; }");
        html.append(".section { margin: 30px 0; }");
        html.append(".section h2 { color: #3498db; border-bottom: 2px solid #f2f2f2; padding-bottom: 10px; }");
        html.append(".stats-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 15px; }");
        html.append(".stat-card { background: #f8f9fa; border-radius: 8px; padding: 15px; text-align: center; border-left: 4px solid #3498db; }");
        html.append(".stat-value { font-size: 32px; font-weight: bold; color: #2c3e50; margin: 10px 0; }");
        html.append(".stat-label { color: #7f8c8d; text-transform: uppercase; font-size: 12px; letter-spacing: 1px; }");
        html.append(".chart-container { margin: 20px 0; height: 300px; }");
        html.append(".footer { margin-top: 40px; text-align: center; color: #95a5a6; font-size: 12px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class='container'>");
        
        // 标题和元信息
        html.append("<div class='header'>");
        html.append("<h1>视频分析报告</h1>");
        html.append("<div class='meta-info'>");
        html.append("任务ID: ").append(task.getTaskId()).append("<br>");
        html.append("创建时间: ").append(task.getCreatedAt()).append("<br>");
        html.append("完成时间: ").append(task.getCompletedAt()).append("<br>");
        html.append("处理时间: ").append(task.getProcessingTime()).append(" 秒");
        html.append("</div>"); // meta-info end
        html.append("</div>"); // header end
        
        // 分析摘要
        html.append("<div class='section'>");
        html.append("<h2>分析摘要</h2>");
        html.append("<div class='stats-grid'>");
        html.append("<div class='stat-card'>");
        html.append("<div class='stat-label'>总车辆数</div>");
        html.append("<div class='stat-value'>").append(totalVehicles).append("</div>");
        html.append("</div>");
        html.append("<div class='stat-card'>");
        html.append("<div class='stat-label'>小汽车</div>");
        html.append("<div class='stat-value'>").append(carCount).append("</div>");
        html.append("</div>");
        html.append("<div class='stat-card'>");
        html.append("<div class='stat-label'>卡车</div>");
        html.append("<div class='stat-value'>").append(truckCount).append("</div>");
        html.append("</div>");
        html.append("<div class='stat-card'>");
        html.append("<div class='stat-label'>公交车</div>");
        html.append("<div class='stat-value'>").append(busCount).append("</div>");
        html.append("</div>");
        html.append("<div class='stat-card'>");
        html.append("<div class='stat-label'>摩托车</div>");
        html.append("<div class='stat-value'>").append(motorcycleCount).append("</div>");
        html.append("</div>");
        html.append("</div>"); // stats-grid end
        html.append("</div>"); // section end
        
        // 结果图
        if (task.getThumbnailUrl() != null && !task.getThumbnailUrl().isEmpty()) {
            String thumbnailUrl = task.getThumbnailUrl();
            if (thumbnailUrl.matches("[0-9a-f]{24}")) {
                thumbnailUrl = "/api/media/image/" + thumbnailUrl;
            }
            
            html.append("<div class='section'>");
            html.append("<h2>结果截图</h2>");
            html.append("<img src='").append(thumbnailUrl).append("' style='max-width:100%; border-radius:8px;'>");
            html.append("</div>");
        }
        
        // 分析结果视频
        if (task.getResultPath() != null && !task.getResultPath().isEmpty()) {
            String videoUrl = task.getResultPath();
            if (videoUrl.matches("[0-9a-f]{24}")) {
                videoUrl = "/api/media/video/" + videoUrl;
            }
            
            html.append("<div class='section'>");
            html.append("<h2>结果视频</h2>");
            html.append("<video controls style='max-width:100%; border-radius:8px;'>");
            html.append("<source src='").append(videoUrl).append("' type='video/mp4'>");
            html.append("您的浏览器不支持视频标签");
            html.append("</video>");
            html.append("</div>");
        }
        
        // 页脚
        html.append("<div class='footer'>");
        html.append("本报告由交通分析系统自动生成");
        html.append("</div>");
        
        html.append("</div>"); // container end
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }
} 