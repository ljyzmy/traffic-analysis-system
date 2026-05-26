package com.traffic.analysis.service;

import com.traffic.analysis.config.AppConfig;
import com.traffic.analysis.model.DetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Image;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 模型服务类，负责与Python API交互
 */
@Service
@Slf4j
public class ModelService {

    private final RestTemplate restTemplate;
    private final AppConfig appConfig;

    @Autowired
    public ModelService(RestTemplate restTemplate, AppConfig appConfig) {
        this.restTemplate = restTemplate;
        this.appConfig = appConfig;
    }

    /**
     * 检查模型API是否在线
     */
    public boolean isModelApiOnline() {
        try {
            String url = appConfig.getModelApiUrl() + "/status";
            log.debug("检查模型API状态: {}", url);
            
            // 短超时时间，避免阻塞用户界面
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Object status = response.getBody().get("model_status");
                log.debug("模型API状态: {}", status);
                return "online".equals(status);
            } else {
                log.warn("模型API返回未预期的状态码: {}", response.getStatusCode());
                return false;
            }
        } catch (RestClientException e) {
            log.warn("模型API不可用: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("检查模型API状态时发生未知错误: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 压缩图像文件以减小大小
     */
    private byte[] compressImage(byte[] imageData, String formatName) throws IOException {
        // 将字节数组转换为BufferedImage
        ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
        BufferedImage originalImage = ImageIO.read(bis);
        bis.close();
        
        if (originalImage == null) {
            log.error("无法读取图像数据");
            return imageData; // 如果无法读取，返回原始数据
        }
        
        // 计算新的尺寸（保持宽高比）
        int maxWidth = 800;
        int maxHeight = 600;
        
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();
        
        // 计算缩放比例
        double scale = Math.min(
            (double) maxWidth / originalWidth,
            (double) maxHeight / originalHeight
        );
        
        // 如果图像已经足够小，不需要缩放
        if (scale >= 1.0) {
            log.info("图像尺寸已经符合要求，无需压缩");
            return imageData;
        }
        
        // 计算新尺寸
        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);
        
        log.info("压缩图像：从 {}x{} 到 {}x{}", originalWidth, originalHeight, newWidth, newHeight);
        
        // 创建新的BufferedImage
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        
        // 绘制缩放后的图像
        g.drawImage(originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        
        // 将BufferedImage转换回字节数组
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, formatName, bos);
        byte[] compressedImageData = bos.toByteArray();
        bos.close();
        
        log.info("原始图像大小: {}KB, 压缩后大小: {}KB", imageData.length / 1024, compressedImageData.length / 1024);
        
        return compressedImageData;
    }

    /**
     * 分析图像
     */
    public DetectionResult analyzeImage(MultipartFile imageFile) throws IOException {
        if (imageFile.isEmpty()) {
            throw new IllegalArgumentException("图像文件不能为空");
        }

        // 记录分析开始时间
        LocalDateTime startTime = LocalDateTime.now();

        // 检查模型API是否在线
        boolean isModelOnline = isModelApiOnline();
        log.info("模型API状态: {}", isModelOnline ? "在线" : "离线");
        
        // 如果模型API离线，使用模拟数据
        if (!isModelOnline) {
            log.info("模型API离线，使用模拟数据");
            DetectionResult mockResult = getMockDetectionResult(imageFile);
            // 设置分析开始时间
            mockResult.setAnalysisStartTime(startTime);
            // 统计车辆类型
            mockResult.updateVehicleTypeStats();
            return mockResult;
        }
        
        // 获取原始图像数据
        byte[] imageData = imageFile.getBytes();
        log.info("原始图像大小: {}KB", imageData.length / 1024);
        
        // 压缩图像 - 从文件名获取格式
        String formatName = "jpg"; // 默认格式
        String fileName = imageFile.getOriginalFilename();
        if (fileName != null && fileName.lastIndexOf(".") > 0) {
            String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            if (extension.equals("png") || extension.equals("jpeg")) {
                formatName = extension;
            }
        }
        
        // 压缩图像
        byte[] compressedImageData = compressImage(imageData, formatName);
        
        // 模型API在线，尝试发送请求
        try {
            String url = appConfig.getAnalyzeUrl();
            
            // 创建请求头，设置JSON内容类型
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // 将图像数据转换为Base64
            String base64Image = Base64.getEncoder().encodeToString(compressedImageData);
            
            // 构建请求JSON对象
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("image_base64", base64Image);
            
            // 发送请求
            log.info("发送分析请求到 {}", url);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        requestEntity,
                Map.class
            );
            
            // 处理响应
            if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                log.info("分析请求成功，处理结果");
                Map<String, Object> responseBody = responseEntity.getBody();
                
                // 将响应转换为DetectionResult对象
                DetectionResult result = new DetectionResult();
                
                // 设置状态
                result.setStatus(responseBody.getOrDefault("status", "success").toString());
                
                // 设置错误信息（如果有）
                if (responseBody.containsKey("message")) {
                    result.setMessage(responseBody.get("message").toString());
                }
                
                // 设置分析开始时间和结果时间
                result.setAnalysisStartTime(startTime);
                result.setTimestamp(LocalDateTime.now());
                
                // 设置车辆计数
                Object vehicleCount = responseBody.get("vehicle_count");
                if (vehicleCount != null) {
                    if (vehicleCount instanceof Number) {
                        result.setVehicleCount(((Number) vehicleCount).intValue());
                    } else {
                        try {
                            result.setVehicleCount(Integer.parseInt(vehicleCount.toString()));
                        } catch (NumberFormatException e) {
                            log.warn("无法解析车辆数量: {}", vehicleCount);
                            result.setVehicleCount(0);
                        }
                    }
                } else {
                    result.setVehicleCount(0);
                }
                
                // 设置推理时间
                Object inferenceTime = responseBody.get("inference_time");
                if (inferenceTime != null) {
                    if (inferenceTime instanceof Number) {
                        result.setInferenceTime(((Number) inferenceTime).doubleValue());
                    } else {
                        try {
                            result.setInferenceTime(Double.parseDouble(inferenceTime.toString()));
                        } catch (NumberFormatException e) {
                            log.warn("无法解析推理时间: {}", inferenceTime);
                            result.setInferenceTime(0.0);
                        }
                    }
                } else {
                    result.setInferenceTime(0.0);
                }
                
                // 设置结果图像Base64
                if (responseBody.containsKey("result_image_base64")) {
                    result.setResultImageBase64(responseBody.get("result_image_base64").toString());
                }
                
                // 处理检测结果
                if (responseBody.containsKey("detections") && responseBody.get("detections") instanceof List) {
                    List<Map<String, Object>> detectionsMap = (List<Map<String, Object>>) responseBody.get("detections");
                    List<DetectionResult.Detection> detections = new ArrayList<>();
                    
                    for (Map<String, Object> detectionMap : detectionsMap) {
                        DetectionResult.Detection detection = new DetectionResult.Detection();
                        
                        // 设置类ID
                        Object classId = detectionMap.get("class_id");
                        if (classId != null) {
                            if (classId instanceof Number) {
                                detection.setClassId(((Number) classId).intValue());
                            } else {
                                try {
                                    detection.setClassId(Integer.parseInt(classId.toString()));
                                } catch (NumberFormatException e) {
                                    detection.setClassId(0);
                                }
                            }
                        }
                        
                        // 设置类名
                        Object className = detectionMap.get("class_name");
                        if (className != null) {
                            detection.setClassName(className.toString());
                        } else {
                            detection.setClassName("未知");
                        }
                        
                        // 设置置信度
                        Object confidence = detectionMap.get("confidence");
                        if (confidence != null) {
                            if (confidence instanceof Number) {
                                detection.setConfidence(((Number) confidence).doubleValue());
                            } else {
                                try {
                                    detection.setConfidence(Double.parseDouble(confidence.toString()));
                                } catch (NumberFormatException e) {
                                    detection.setConfidence(0.0);
                                }
                            }
                        }
                        
                        // 设置边界框
                        if (detectionMap.containsKey("bbox") && detectionMap.get("bbox") instanceof List) {
                            List<Object> bboxList = (List<Object>) detectionMap.get("bbox");
                            List<Double> bbox = new ArrayList<>();
                            
                            for (Object coord : bboxList) {
                                if (coord instanceof Number) {
                                    bbox.add(((Number) coord).doubleValue());
                        } else {
                                    try {
                                        bbox.add(Double.parseDouble(coord.toString()));
                                    } catch (NumberFormatException e) {
                                        bbox.add(0.0);
                                    }
                                }
                            }
                            
                            detection.setBbox(bbox);
                        }
                        
                        detections.add(detection);
                    }
                    
                    result.setDetections(detections);
                }
                
                // 处理图像URL
                if (responseBody.containsKey("image_url")) {
                    result.setImageUrl(responseBody.get("image_url").toString());
                }
                
                // 如果响应包含Base64图像，保存为文件并设置URL
                if (result.getResultImageBase64() != null && !result.getResultImageBase64().isEmpty()) {
                    String imageUrl = saveImageFromBase64(result.getResultImageBase64());
                    if (imageUrl != null) {
                        result.setImageUrl(imageUrl);
                    }
                }
                
                // 更新车辆类型统计
                result.updateVehicleTypeStats();
                
                // 检查车辆计数是否与车辆类型统计一致
                if (result.getVehicleCount() == 0 && !result.getVehicleTypeStats().isEmpty()) {
                    // 如果vehicleCount是0但实际上有车辆统计，重新计算vehicleCount
                    int calculatedCount = result.getVehicleTypeStats().values().stream()
                        .mapToInt(Integer::intValue)
                        .sum();
                    if (calculatedCount > 0) {
                        log.info("vehicleCount为0但检测到{}种车辆类型共{}辆，更新计数", 
                            result.getVehicleTypeStats().size(), calculatedCount);
                        result.setVehicleCount(calculatedCount);
                    }
                }
                
                // 记录结果信息
                log.info("分析完成: 检测到{}个车辆, 车辆类型: {}", result.getVehicleCount(), result.getVehicleTypeStats());
                    
                    return result;
                } else {
                // 处理异常响应
                log.error("分析请求失败: HTTP状态码 {}", responseEntity.getStatusCode());
            DetectionResult errorResult = new DetectionResult();
            errorResult.setStatus("error");
                errorResult.setMessage("模型API返回错误: " + responseEntity.getStatusCode());
            return errorResult;
            }
        } catch (Exception e) {
            // 处理异常
            log.error("分析请求发生异常: {}", e.getMessage(), e);
            DetectionResult errorResult = new DetectionResult();
            errorResult.setStatus("error");
            errorResult.setMessage("分析请求失败: " + e.getMessage());
            return errorResult;
        }
    }
    
    /**
     * 创建模拟检测结果（用于离线模式）
     */
    private DetectionResult getMockDetectionResult(MultipartFile file) throws IOException {
        log.info("生成模拟检测结果");
        DetectionResult result = new DetectionResult();
        
        // 设置基本信息
        result.setStatus("success");
        result.setMessage("模拟分析完成");
        result.setTimestamp(LocalDateTime.now());
        
        // 生成随机车辆数量
        Random random = new Random();
        int vehicleCount = random.nextInt(5) + 3; // 3-7辆车
        result.setVehicleCount(vehicleCount);
        
        // 转换图片为Base64以便前端显示
        byte[] imageBytes = file.getBytes();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        result.setResultImageBase64(base64Image);
        
        // 设置处理时间
        result.setInferenceTime(random.nextInt(500) + 300); // 300-800ms
        
        // 创建检测列表
        List<DetectionResult.Detection> detections = new ArrayList<>();
        String[] vehicleClasses = {"car", "truck", "bus", "motorcycle"};
        
        for (int i = 0; i < vehicleCount; i++) {
            DetectionResult.Detection detection = new DetectionResult.Detection();
            String className = vehicleClasses[random.nextInt(vehicleClasses.length)];
            detection.setClassName(className);
            detection.setClassId(i + 1);
            detection.setConfidence(0.7 + random.nextDouble() * 0.3); // 70%-100%
            
            // 创建边界框 [x1, y1, x2, y2]
            List<Double> bbox = new ArrayList<>();
            double x = random.nextInt(800);
            double y = random.nextInt(600);
            double width = 50 + random.nextInt(150);
            double height = 50 + random.nextInt(100);
            
            bbox.add(x); // x1
            bbox.add(y); // y1
            bbox.add(x + width); // x2
            bbox.add(y + height); // y2
            
            detection.setBbox(bbox);
            detections.add(detection);
        }
        
        result.setDetections(detections);
        
        return result;
    }

    /**
     * 从Base64数据保存图像并返回URL
     */
    public String saveImageFromBase64(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            log.warn("Base64数据为空，无法保存图像");
            return null;
        }
        
        try {
            // 如果Base64字符串包含头部，则移除
            if (base64Data.contains(",")) {
                base64Data = base64Data.split(",")[1];
            }
            
            // 解码Base64数据
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            
            // 生成唯一文件名
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            String fileName = "result_" + timestamp + ".jpg";
            
            // 确定存储路径 - 使用静态资源目录
            String uploadDir = "src/main/resources/static/images";
            
            // 确保目录存在
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                log.info("创建上传目录: {}", uploadDir);
                directory.mkdirs();
            }
            
            // 保存文件
            Path path = Paths.get(uploadDir, fileName);
            Files.write(path, imageBytes);
            
            log.info("图像已保存到: {}", path.toAbsolutePath());
            
            // 返回相对URL路径（前端通过/api/images/xxx访问）
            return "/images/" + fileName;
        } catch (Exception e) {
            log.error("保存Base64图像时出错: {}", e.getMessage(), e);
            return null;
        }
    }
} 