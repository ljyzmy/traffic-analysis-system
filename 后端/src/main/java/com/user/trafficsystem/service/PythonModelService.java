package com.user.trafficsystem.service;

import java.util.Map;

public interface PythonModelService {
    
    /**
     * 使用Python模型检测图片中的汽车
     * 
     * @param imagePath 图片的完整路径
     * @param direction 图片方向（横向或纵向）
     * @return 检测结果，包含识别信息
     * @throws Exception 如果模型处理过程出错
     */
    Map<String, Object> detectCar(String imagePath, String direction) throws Exception;
} 