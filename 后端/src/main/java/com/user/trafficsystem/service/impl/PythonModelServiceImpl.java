package com.user.trafficsystem.service.impl;

import com.user.trafficsystem.service.PythonModelService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;

@Service
public class PythonModelServiceImpl implements PythonModelService {
    
    private static final Logger logger = LoggerFactory.getLogger(PythonModelServiceImpl.class);
    
    @Value("${python.script.path:./python_model/car_detection.py}")
    private String pythonScriptPath;
    
    @Value("${python.executable:python}")
    private String pythonExecutable;
    
    @Value("${model.timeout:60}")
    private int modelTimeout;

    @Override
    public Map<String, Object> detectCar(String imagePath, String direction) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                pythonExecutable,
                pythonScriptPath,
                "--image_path", imagePath,
                "--direction", direction
        );
        
        pb.redirectErrorStream(true);
        
        logger.info("执行Python脚本: {}", pb.command());
        
        Process process = pb.start();
        
        // 读取脚本输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // 等待脚本执行完成
        boolean completed = process.waitFor(modelTimeout, TimeUnit.SECONDS);
        
        if (!completed) {
            process.destroyForcibly();
            throw new IOException("模型执行超时，超过 " + modelTimeout + " 秒");
        }
        
        int exitCode = process.exitValue();
        
        if (exitCode != 0) {
            logger.error("模型执行失败，退出码: {}, 输出: {}", exitCode, output);
            throw new IOException("模型执行失败，退出码: " + exitCode + "\n" + output);
        }
        
        try {
            // 解析JSON输出
            String jsonString = output.toString().trim();
            logger.debug("模型输出: {}", jsonString);
            
            JSONObject jsonOutput = new JSONObject(jsonString);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            
            // 从JSON中提取结果
            for (String key : jsonOutput.keySet()) {
                result.put(key, jsonOutput.get(key));
            }
            
            return result;
        } catch (Exception e) {
            logger.error("无法解析模型输出: {}", output, e);
            throw new IOException("无法解析模型输出: " + e.getMessage() + "\n" + output);
        }
    }
} 