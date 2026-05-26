package com.traffic.analysis.controller;

import com.traffic.analysis.model.AnalysisResult;
import com.traffic.analysis.model.DetectionResult;
import com.traffic.analysis.model.User;
import com.traffic.analysis.service.AnalysisService;
import com.traffic.analysis.service.ModelService;
import com.traffic.analysis.service.HistoryService;
import com.traffic.analysis.entity.AnalysisHistory;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

@Controller
public class AnalysisController {
    
    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    
    @Autowired
    private ModelService modelService;
    
    @Autowired
    private AnalysisService analysisService;
    
    @Autowired
    private HistoryService historyService;
    
    @GetMapping("/upload")
    public String redirectToUpload(HttpSession session) {
        if (session.getAttribute("user") == null) {
            // 用户未登录时，需要先登录
            return "redirect:/login";
        }
        return "upload";
    }
    
    @GetMapping("/analysis/upload")
    public String uploadPage(Model model, HttpSession session) {
        if (session.getAttribute("user") == null) {
            // 用户未登录时，需要先登录
            return "redirect:/login";
        }
        return "upload";
    }
    
    /**
     * 检查分析任务的状态
     * @param id 分析任务ID
     * @return 分析任务状态
     */
    @GetMapping("/api/analysis/status/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkAnalysisStatus(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        log.info("检查分析状态: id={}", id);
        
        try {
            AnalysisResult result = analysisService.findById(id);
            
            if (result == null) {
                log.warn("未找到分析结果: id={}", id);
                response.put("status", "error");
                response.put("message", "未找到分析结果");
                return ResponseEntity.ok(response);
            }
            
            // 返回分析状态
            response.put("id", result.getId());
            response.put("status", result.getStatus());
            response.put("message", result.getMessage());
            
            // 检查车辆计数是否正确
            int vehicleCount = result.getVehicleCount();
            if (vehicleCount == 0 && result.getVehicleTypeStats() != null && !result.getVehicleTypeStats().isEmpty()) {
                // 如果vehicleCount为0但有车辆类型统计，重新计算
                int calculatedCount = result.getVehicleTypeStats().values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
                if (calculatedCount > 0) {
                    log.info("状态检查: 更正车辆计数从0到{}", calculatedCount);
                    vehicleCount = calculatedCount;
                    // 更新数据库中的记录
                    result.setVehicleCount(calculatedCount);
                    analysisService.saveResult(result);
                }
            }
            response.put("vehicleCount", vehicleCount);
            
            // 添加车辆类型统计信息
            response.put("vehicleTypeStats", result.getVehicleTypeStats());
            
            // 添加分析时间
            if (result.getAnalysisStartTime() != null) {
                response.put("analysisStartTime", result.getAnalysisStartTime().toString());
            }
            
            // 获取结果图像URL
            String imageUrl = result.getImageUrl();
            
            // 格式化图像URL
            if (imageUrl != null && !imageUrl.isEmpty()) {
                // 确保URL格式正确
                if (!imageUrl.startsWith("http") && !imageUrl.startsWith("/api/images/")) {
                    imageUrl = "/api/images/" + imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
                }
                response.put("imageUrl", imageUrl);
            } else {
                response.put("imageUrl", "");
                log.warn("分析结果中没有有效的图像URL");
            }
            
            // 添加推理时间（毫秒）
            response.put("inferenceTime", result.getInferenceTime());
            
            // 添加分析人员信息
            // 优先使用数据库中已保存的分析人员信息
            if (result.getUsername() != null) {
                response.put("analyst", result.getUsername());
                response.put("username", result.getUsername());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("检查分析状态时出错: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", "检查分析状态出错: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/api/analysis/analyze")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> analyzeImage(
            @RequestParam("image") MultipartFile imageFile,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
                                                          HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        log.info("收到图像分析请求: 文件大小={}", imageFile.getSize());
        log.info("认证头: {}", authHeader != null ? (authHeader.length() > 15 ? authHeader.substring(0, 15) + "..." : authHeader) : "null");
        
        // 验证用户 - 先尝试会话认证
        User user = (User) session.getAttribute("user");
        
        // 如果会话中没有用户，尝试从JWT令牌中获取
        if (user == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                log.info("尝试从令牌验证用户: {}", token.substring(0, Math.min(token.length(), 10)) + "...");
                
                // 从令牌中解析用户信息
                String[] parts = token.split("_");
                if (parts.length >= 2) {
                    String userId = parts[0];
                    String username = parts[1];
                    
                    // 创建临时用户对象
                    user = new User();
                    user.setId(userId);
                    user.setUsername(username);
                    log.info("从令牌中提取用户信息成功: id={}, username={}", userId, username);
                }
            } catch (Exception e) {
                log.error("令牌解析失败: {}", e.getMessage());
            }
        }
        
        // 检查用户是否已登录
        if (user == null) {
            log.warn("未登录用户尝试分析图片");
            response.put("status", "error");
            response.put("message", "请先登录再进行分析");
            return ResponseEntity.status(401).body(response);
        }
        
        log.info("当前用户: {}", user.getUsername());
        
        try {
            if (imageFile.isEmpty()) {
                log.warn("上传的图像文件为空");
                response.put("status", "error");
                response.put("message", "请选择要上传的图片");
                return ResponseEntity.badRequest().body(response);
            }
            
            // 调用模型服务进行分析
            log.info("开始分析图像: 文件名={}, 大小={}字节", imageFile.getOriginalFilename(), imageFile.getSize());
            DetectionResult detectionResult = modelService.analyzeImage(imageFile);
            log.info("图像分析完成: status={}, 检测到{}个车辆", detectionResult.getStatus(), detectionResult.getVehicleCount());
            
            // 转换检测结果为分析结果对象
            AnalysisResult analysisResult = new AnalysisResult();
            analysisResult.setUserId(user.getId());
            analysisResult.setUsername(user.getUsername());
            // 添加分析人员信息
            analysisResult.setAnalyst(user.getUsername());
            analysisResult.setStatus(detectionResult.getStatus());
            analysisResult.setMessage(detectionResult.getMessage());
            analysisResult.setVehicleCount(detectionResult.getVehicleCount());
            analysisResult.setInferenceTime(detectionResult.getInferenceTime());
            analysisResult.setImageUrl(detectionResult.getImageUrl());
            analysisResult.setResultImageBase64(detectionResult.getResultImageBase64());
            
            // 设置分析开始时间和时间戳
            analysisResult.setAnalysisStartTime(detectionResult.getAnalysisStartTime());
            analysisResult.setTimestamp(detectionResult.getTimestamp() != null ? 
                    detectionResult.getTimestamp() : LocalDateTime.now());
            
            // 转换检测对象列表
            if (detectionResult.getDetections() != null) {
                List<AnalysisResult.Detection> resultDetections = new ArrayList<>();
                
                for (DetectionResult.Detection detection : detectionResult.getDetections()) {
                    AnalysisResult.Detection resultDetection = new AnalysisResult.Detection();
                    resultDetection.setClassId(detection.getClassId());
                    resultDetection.setClassName(detection.getClassName());
                    resultDetection.setConfidence(detection.getConfidence());
                    
                    // 转换边界框
                    if (detection.getBbox() != null) {
                        double[] bbox = new double[detection.getBbox().size()];
                        for (int i = 0; i < detection.getBbox().size(); i++) {
                            bbox[i] = detection.getBbox().get(i);
                        }
                        resultDetection.setBbox(bbox);
                    }
                    
                    resultDetections.add(resultDetection);
                }
                
                analysisResult.setDetections(resultDetections);
            }
            
            // 更新车辆类型统计
            if (detectionResult.getVehicleTypeStats() != null && !detectionResult.getVehicleTypeStats().isEmpty()) {
                analysisResult.setVehicleTypeStats(detectionResult.getVehicleTypeStats());
            } else {
                // 如果检测结果中没有，手动计算
                Map<String, Integer> vehicleTypeStats = new HashMap<>();
                if (analysisResult.getDetections() != null) {
                    for (AnalysisResult.Detection detection : analysisResult.getDetections()) {
                        String vehicleType = detection.getClassNameSafe();
                        vehicleTypeStats.put(vehicleType, vehicleTypeStats.getOrDefault(vehicleType, 0) + 1);
            }
                }
                analysisResult.setVehicleTypeStats(vehicleTypeStats);
            }
            
            // 检查vehicleCount与vehicleTypeStats是否一致
            if (analysisResult.getVehicleCount() == 0 && !analysisResult.getVehicleTypeStats().isEmpty()) {
                int calculatedCount = analysisResult.getVehicleTypeStats().values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
                if (calculatedCount > 0) {
                    log.info("分析结果vehicleCount为0但有车辆类型统计，更新计数为: {}", calculatedCount);
                    analysisResult.setVehicleCount(calculatedCount);
                }
            }
            
            // 设置请求来源
            analysisResult.setRequestSource("web");
            
            // 保存分析结果到数据库
            log.info("保存分析结果到数据库");
            AnalysisResult savedResult = analysisService.saveResult(analysisResult);
            log.info("分析结果已保存，ID: {}", savedResult.getId());
            
            // 同时保存到历史记录
            try {
                AnalysisHistory history = new AnalysisHistory();
                history.setUserId(user.getId());
                history.setUsername(user.getUsername());
                // 设置分析人员为当前用户名
                history.setAnalyst(user.getUsername());
                history.setFileName(imageFile.getOriginalFilename());
                history.setImageUrl(savedResult.getImageUrl());
                history.setVehicleCount(savedResult.getVehicleCount());
                history.setInferenceTime(savedResult.getInferenceTime());
                history.setStatus(savedResult.getStatus());
                history.setMessage(savedResult.getMessage());
                history.setVehicleTypeStats(savedResult.getVehicleTypeStats());
                
                // 转换时间
                history.setCreateTime(LocalDateTime.now());
                history.setUpdateTime(LocalDateTime.now());
                history.setAnalysisStartTime(savedResult.getAnalysisStartTime());
                
                if (savedResult.getTimestamp() != null) {
                    history.setTimestamp(savedResult.getTimestamp().toString());
                } else {
                    history.setTimestamp(LocalDateTime.now().toString());
                }
                
                // 保存结果数据
                Map<String, Object> resultData = new HashMap<>();
                resultData.put("id", savedResult.getId());
                resultData.put("vehicleCount", savedResult.getVehicleCount());
                resultData.put("status", savedResult.getStatus());
                resultData.put("timestamp", savedResult.getTimestamp());
                resultData.put("inferenceTime", savedResult.getInferenceTime());
                resultData.put("imageUrl", savedResult.getImageUrl());
                resultData.put("detections", savedResult.getDetections());
                resultData.put("vehicleTypeStats", savedResult.getVehicleTypeStats());
                resultData.put("analyst", user.getUsername());
                
                history.setAnalysisResult(resultData);
                history.setRequestSource("web");
                
                historyService.saveHistory(history);
                log.info("分析历史记录已保存");
            } catch (Exception e) {
                log.error("保存历史记录失败: {}", e.getMessage(), e);
            }
            
            // 返回结果
            response.put("status", "success");
            response.put("id", savedResult.getId());
            log.info("返回分析结果: id={}", savedResult.getId());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("处理图片时出错", e);
            response.put("status", "error");
            response.put("message", "处理图片时出错: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @GetMapping("/result/{id}")
    public String showResult(@PathVariable String id, Model model, HttpSession session, HttpServletResponse response) {
        log.info("查看分析结果: id={}", id);
        
        // 检查用户是否已登录
        User user = (User) session.getAttribute("user");
        if (user == null) {
            log.warn("未登录用户尝试查看分析结果");
            return "redirect:/login";
        }
        
        try {
            AnalysisResult result = analysisService.findById(id);
            
            if (result == null) {
                log.warn("未找到分析结果: id={}", id);
                model.addAttribute("error", "未找到分析结果");
                return "error";
            }
            
            // 检查是否有权限查看
            if (result.getUserId() != null && !result.getUserId().equals(user.getId())) {
                if (!"admin".equals(user.getRole())) {
                    log.warn("用户 {} 尝试查看不属于他的分析结果 {}", user.getUsername(), id);
                    model.addAttribute("error", "您没有权限查看此分析结果");
                    return "error";
                }
            }
            
            // 添加分析结果到模型
            model.addAttribute("result", result);
            model.addAttribute("id", result.getId());
            model.addAttribute("status", result.getStatus());
            model.addAttribute("message", result.getMessage());
            
            // 检查车辆计数是否正确
            int vehicleCount = result.getVehicleCount();
            if (vehicleCount == 0 && result.getVehicleTypeStats() != null && !result.getVehicleTypeStats().isEmpty()) {
                // 如果vehicleCount为0但有车辆类型统计，重新计算
                int calculatedCount = result.getVehicleTypeStats().values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
                if (calculatedCount > 0) {
                    log.info("显示结果: 更正车辆计数从0到{}", calculatedCount);
                    vehicleCount = calculatedCount;
                    // 更新数据库中的记录
                    result.setVehicleCount(calculatedCount);
                    analysisService.saveResult(result);
                }
            }
            model.addAttribute("vehicleCount", vehicleCount);
            
            model.addAttribute("inferenceTime", result.getInferenceTime());
            
            // 添加车辆类型统计
            if (result.getVehicleTypeStats() != null && !result.getVehicleTypeStats().isEmpty()) {
                model.addAttribute("vehicleTypeStats", result.getVehicleTypeStats());
                log.info("显示车辆类型统计: {}", result.getVehicleTypeStats());
            } else {
                // 如果没有预先统计好的数据，尝试根据检测结果计算
                Map<String, Integer> vehicleTypeStats = new HashMap<>();
                if (result.getDetections() != null) {
                    for (AnalysisResult.Detection detection : result.getDetections()) {
                        String vehicleType = detection.getClassNameSafe();
                        vehicleTypeStats.put(vehicleType, vehicleTypeStats.getOrDefault(vehicleType, 0) + 1);
            }
            
                    if (!vehicleTypeStats.isEmpty()) {
                        // 更新数据库中的记录
                        result.setVehicleTypeStats(vehicleTypeStats);
                        analysisService.saveResult(result);
                        model.addAttribute("vehicleTypeStats", vehicleTypeStats);
                        log.info("计算并显示车辆类型统计: {}", vehicleTypeStats);
                    }
                }
            }
            
            // 添加分析时间信息
            if (result.getAnalysisStartTime() != null) {
                model.addAttribute("analysisStartTime", result.getAnalysisStartTime());
            }
            
            if (result.getTimestamp() != null) {
                model.addAttribute("timestamp", result.getTimestamp());
            }
            
            // 检查图像URL
            if (result.getImageUrl() != null && !result.getImageUrl().isEmpty()) {
                // 检查图像URL是否有效并格式化URL
                String imageUrl = result.getImageUrl();
                if (imageUrl.startsWith("/images/") || imageUrl.startsWith("/static/images/")) {
                    // 提取实际文件名
                    String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                    imageUrl = "/api/images/" + filename;
                }
                model.addAttribute("imageUrl", imageUrl);
                model.addAttribute("hasImage", true);
                log.info("返回图片URL: {}", imageUrl);
            } else if (result.getResultImageBase64() != null && !result.getResultImageBase64().isEmpty()) {
                // 如果没有URL但有base64数据，可以保存为文件并返回URL
                try {
                    String imageUrl = modelService.saveImageFromBase64(result.getResultImageBase64());
                    if (imageUrl != null) {
                        // 调整URL格式供前端使用
                        String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
                        imageUrl = "/api/images/" + filename;
                        
                        // 更新结果并保存回数据库
                        result.setImageUrl(imageUrl);
                        analysisService.saveResult(result);
                        
                        model.addAttribute("imageUrl", imageUrl);
                        model.addAttribute("hasImage", true);
                        log.info("从Base64创建图片URL: {}", imageUrl);
                    } else {
                        model.addAttribute("hasImage", false);
                        log.warn("无法从Base64数据创建图片");
                    }
                } catch (Exception e) {
                    log.error("保存图片失败: {}", e.getMessage());
                    model.addAttribute("hasImage", false);
                }
            } else {
                log.warn("分析结果缺少图像URL: id={}", id);
                model.addAttribute("hasImage", false);
            }
            
            // 添加调试信息
            Map<String, Object> debug = new HashMap<>();
            debug.put("id", result.getId());
            debug.put("hasDetections", result.getDetections() != null && !result.getDetections().isEmpty());
            debug.put("detectionsCount", result.getDetections() != null ? result.getDetections().size() : 0);
            debug.put("hasImage", result.getResultImageBase64() != null && !result.getResultImageBase64().isEmpty());
            debug.put("hasImageUrl", result.getImageUrl() != null && !result.getImageUrl().isEmpty());
            debug.put("hasTimestamp", result.getTimestamp() != null);
            debug.put("hasVehicleCount", result.getVehicleCount() > 0);
            debug.put("inferenceTimeValid", result.getInferenceTime() > 0);
            model.addAttribute("debug", debug);
            
            log.info("成功设置结果数据到模型中，将渲染结果页面");
            return "result";
        } catch (Exception e) {
            log.error("加载结果数据出错: id={}, 错误类型={}, 错误消息={}", 
                    id, e.getClass().getName(), e.getMessage());
            e.printStackTrace(); // 打印详细堆栈
            
            // 创建一个空的结果对象，避免模板中的空指针异常
            AnalysisResult emptyResult = new AnalysisResult();
            emptyResult.setId(id);
            emptyResult.setMessage("加载数据失败: " + e.getMessage());
            emptyResult.setStatus("error");
            emptyResult.setTimestamp(java.time.LocalDateTime.now());
            emptyResult.setDetections(new ArrayList<>());
            model.addAttribute("result", emptyResult);
            
            model.addAttribute("error", "加载结果数据出错: " + e.getMessage());
            // 添加详细错误信息
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("exceptionType", e.getClass().getName());
            errorDetails.put("message", e.getMessage());
            model.addAttribute("errorDetails", errorDetails);
            
            return "result";
        }
    }
    
    @GetMapping("/analysis/history")
    public String showHistory(Model model, HttpSession session) {
        return "redirect:/history/list";
    }
    
    @DeleteMapping("/api/analysis/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteAnalysis(@PathVariable String id,
                                                            HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (session.getAttribute("user") == null) {
                response.put("status", "error");
                response.put("message", "请先登录");
                return ResponseEntity.badRequest().body(response);
            }
            
            AnalysisResult result = analysisService.findById(id);
            if (result == null) {
                response.put("status", "error");
                response.put("message", "分析结果不存在");
                return ResponseEntity.badRequest().body(response);
            }
            
            User user = (User) session.getAttribute("user");
            if (!result.getUserId().equals(user.getId())) {
                response.put("status", "error");
                response.put("message", "无权删除此分析结果");
                return ResponseEntity.badRequest().body(response);
            }
            
            analysisService.deleteById(id);
            
            response.put("status", "success");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 提供分析结果图片访问的API端点
     * 前端可以通过/api/images/{filename}访问
     */
    @GetMapping("/api/images/{filename:.+}")
    @ResponseBody
    public ResponseEntity<?> getAnalysisImage(@PathVariable String filename, HttpServletResponse response) {
        log.info("请求分析图片: {}", filename);
        
        try {
            // 图片可能的存储位置
            List<String> possiblePaths = new ArrayList<>();
            possiblePaths.add("src/main/resources/static/images/" + filename);  // 主路径
            possiblePaths.add("src/main/resources/static/images/uploads/" + filename);  // 上传目录
            possiblePaths.add("traffic-web/src/main/resources/static/images/" + filename);  // 针对不同工作目录
            possiblePaths.add("traffic-web/src/main/resources/static/images/uploads/" + filename);
            
            // 尝试从文件系统查找图片
            Path imagePath = null;
            for (String path : possiblePaths) {
                Path checkPath = Paths.get(path);
                if (Files.exists(checkPath)) {
                    imagePath = checkPath;
                    log.info("在文件系统中找到图片: {}", path);
                    break;
                }
            }
            
            // 如果在文件系统中找到图片
            if (imagePath != null) {
                // 读取图片字节
                byte[] imageBytes = Files.readAllBytes(imagePath);
                
                // 设置MIME类型
                String mimeType = "image/jpeg";
                if (filename.toLowerCase().endsWith(".png")) {
                    mimeType = "image/png";
                }
                
                // 返回图片
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(mimeType))
                        .body(imageBytes);
            }
            
            // 如果文件系统中没有找到，尝试从数据库查找结果
            log.info("文件系统中未找到图片，尝试从数据库查找: {}", filename);
            
            // 尝试多种URL格式查找
            List<String> possibleUrls = new ArrayList<>();
            possibleUrls.add("/images/" + filename);
            possibleUrls.add("/static/images/" + filename);
            possibleUrls.add("/images/uploads/" + filename);
            possibleUrls.add("/static/images/uploads/" + filename);
            possibleUrls.add(filename);
            
            for (String url : possibleUrls) {
                try {
                    AnalysisResult result = analysisService.findByImageUrl(url);
                    if (result != null && result.getResultImageBase64() != null) {
                        log.info("通过URL在数据库中找到图片: {}", url);
                        String base64 = result.getResultImageBase64();
                        if (base64.contains(",")) {
                            base64 = base64.split(",")[1];
                        }
                        byte[] imageBytes = Base64.getDecoder().decode(base64);
                        return ResponseEntity.ok()
                                .contentType(MediaType.IMAGE_JPEG)
                                .body(imageBytes);
                    }
                } catch (Exception e) {
                    log.debug("尝试查找URL失败: {}", e.getMessage());
                }
            }
            
            // 找不到图片，返回404
            log.warn("无法找到图片: {}", filename);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("获取分析图片出错: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("图片读取错误: " + e.getMessage());
        }
    }
} 