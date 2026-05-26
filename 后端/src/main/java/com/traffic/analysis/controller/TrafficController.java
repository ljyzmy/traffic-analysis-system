package com.traffic.analysis.controller;

import com.traffic.analysis.model.DetectionResult;
import com.traffic.analysis.service.ModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 交通分析控制器
 */
@Controller
@Slf4j
public class TrafficController {

    private final ModelService modelService;

    @Autowired
    public TrafficController(ModelService modelService) {
        this.modelService = modelService;
    }

    /**
     * 首页重定向到登录页
     */
    @GetMapping("/")
    public String root(HttpSession session) {
        // 如果用户已登录，直接进入首页
        if (session.getAttribute("user") != null) {
            return "redirect:/index";
        }
        return "redirect:/login";
    }
    
    /**
     * 主页面 - 需要先登录
     */
    @GetMapping("/index")
    public String index(Model model, HttpSession session) {
        // 检查用户是否已登录
        Object userObj = session.getAttribute("user");
        if (userObj == null) {
            log.info("用户未登录，重定向到登录页面");
            return "redirect:/login";
        }
        
        log.info("用户已登录，正在加载首页...");
        try {
            // 检查模型API是否在线 - 用try-catch包装，防止阻断页面加载
            boolean isModelOnline = false;
            try {
                isModelOnline = modelService.isModelApiOnline();
                log.debug("模型API状态: {}", isModelOnline ? "在线" : "离线");
            } catch (Exception e) {
                log.error("检查模型API状态时出错", e);
            }
            model.addAttribute("isModelOnline", isModelOnline);
            return "index";
        } catch (Exception e) {
            // 发生任何错误都确保页面能够加载
            log.error("加载首页时出错", e);
            model.addAttribute("isModelOnline", false);
            model.addAttribute("error", "加载页面时发生错误，但您仍然可以使用基本功能");
            return "index";
        }
    }

    /**
     * 公共上传图片页面
     */
    @GetMapping("/public/upload")
    public String publicUploadPage() {
        return "public_upload";
    }

    /**
     * 处理公共图片上传请求
     */
    @PostMapping("/public/analyze")
    public String analyzePublicImage(@RequestParam("image") MultipartFile imageFile, Model model) {
        try {
            if (imageFile.isEmpty()) {
                model.addAttribute("error", "请选择要上传的图片");
                return "public_upload";
            }

            // 调用服务进行分析
            DetectionResult result = modelService.analyzeImage(imageFile);
            
            if ("success".equals(result.getStatus())) {
                model.addAttribute("result", result);
                return "public_result";
            } else {
                model.addAttribute("error", "分析失败: " + result.getMessage());
                return "public_upload";
            }
        } catch (Exception e) {
            log.error("分析图像时出错", e);
            model.addAttribute("error", "处理图片时出错: " + e.getMessage());
            return "public_upload";
        }
    }

    /**
     * 公共图片分析API (JSON接口)
     */
    @PostMapping("/api/public/analyze")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> analyzePublicImageApi(@RequestParam("image") MultipartFile imageFile) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (imageFile.isEmpty()) {
                response.put("status", "error");
                response.put("message", "请选择要上传的图片");
                return ResponseEntity.badRequest().body(response);
            }

            // 调用服务进行分析
            DetectionResult result = modelService.analyzeImage(imageFile);
            
            if ("success".equals(result.getStatus())) {
                // 将DetectionResult转换为Map
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("status", result.getStatus());
                resultMap.put("vehicleCount", result.getVehicleCount());
                resultMap.put("detections", result.getDetections());
                resultMap.put("inferenceTime", result.getInferenceTime());
                resultMap.put("resultImageBase64", result.getResultImageBase64());
                resultMap.put("timestamp", result.getTimestamp());
                return ResponseEntity.ok(resultMap);
            } else {
                response.put("status", "error");
                response.put("message", "分析失败: " + result.getMessage());
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            log.error("API分析图像时出错", e);
            response.put("status", "error");
            response.put("message", "处理图片时出错: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 模型状态检查API
     */
    @GetMapping("/api/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkModelStatus() {
        Map<String, Object> response = new HashMap<>();
        boolean isOnline = modelService.isModelApiOnline();
        response.put("status", isOnline ? "online" : "offline");
        response.put("model_status", isOnline ? "online" : "offline");
        return ResponseEntity.ok(response);
    }
    
    /**
     * 模型状态检查API - 新路径
     */
    @GetMapping("/api/model/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkModelStatusNew() {
        Map<String, Object> response = new HashMap<>();
        boolean isOnline = modelService.isModelApiOnline();
        response.put("status", isOnline ? "running" : "offline");
        return ResponseEntity.ok(response);
    }
} 