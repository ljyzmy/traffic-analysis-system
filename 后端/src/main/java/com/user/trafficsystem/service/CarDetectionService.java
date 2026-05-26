package com.user.trafficsystem.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

public interface CarDetectionService {
    
    /**
     * 检测图像中的车辆
     * @param image 上传的图像文件
     * @return 包含检测结果的Map
     * @throws Exception 如果检测过程中出错
     */
    Map<String, Object> detectCars(MultipartFile image) throws Exception;
} 