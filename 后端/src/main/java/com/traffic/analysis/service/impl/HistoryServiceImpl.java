package com.traffic.analysis.service.impl;

import com.traffic.analysis.config.ApplicationContextProvider;
import com.traffic.analysis.entity.AnalysisHistory;
import com.traffic.analysis.model.DetectionResult;
import com.traffic.analysis.model.VideoAnalysis;
import com.traffic.analysis.repository.AnalysisHistoryRepository;
import com.traffic.analysis.service.HistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * 历史记录服务实现类
 */
@Service
@Slf4j
public class HistoryServiceImpl implements HistoryService {

    @Autowired
    private AnalysisHistoryRepository historyRepository;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    @Override
    public List<AnalysisHistory> getUserHistory(String userId) {
        try {
            log.info("获取用户历史记录, 用户ID: {}", userId);
            
            // 尝试处理复杂的userId格式
            String actualUserId = userId;
            if (userId != null && userId.contains("id=")) {
                actualUserId = extractIdFromString(userId);
                log.info("从复杂格式中提取实际用户ID: {} -> {}", userId, actualUserId);
            }
            
            // 首先尝试使用Repository从analysis_history集合获取记录
            try {
                List<AnalysisHistory> historyList = historyRepository.findByUserIdOrderByTimestampDesc(actualUserId);
                log.info("从analysis_history集合获取到{}条记录", historyList.size());
                
                if (!historyList.isEmpty()) {
                    return historyList;
                }
                
                // 如果analysis_history没有记录，可能是老数据仅存在于analysis_results集合
                // 这种情况下，尝试从analysis_results中获取记录
                log.info("在analysis_history集合中未找到记录，尝试从analysis_results集合获取");
                
                // 测试MongoDB连接
                boolean pingResult = testMongoDBConnection();
                log.info("MongoDB连接测试: {}", pingResult ? "成功" : "失败");
                
                if (pingResult) {
                    List<String> collections = mongoTemplate.getCollectionNames().stream().toList();
                    if (collections.contains("analysis_results")) {
                        // 创建查询
                        Query query = new Query();
                        if (!"test-connection".equals(actualUserId)) {
                            query.addCriteria(Criteria.where("userId").is(actualUserId));
                        }
                        
                        // 执行查询
                        List<Map> results = mongoTemplate.find(query, Map.class, "analysis_results");
                        log.info("从analysis_results集合获取到{}条记录", results.size());
                        
                        if (results.isEmpty() && !"test-connection".equals(actualUserId)) {
                            log.info("未找到指定用户的记录，尝试获取所有记录");
                            results = mongoTemplate.findAll(Map.class, "analysis_results");
                            log.info("获取所有记录，共{}条", results.size());
                        }
                        
                        if (!results.isEmpty()) {
                            List<AnalysisHistory> convertedResults = convertToHistoryList(results);
                            log.info("从analysis_results集合转换获取到{}条记录", convertedResults.size());
                            return convertedResults;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("获取历史记录时出错: {}", e.getMessage(), e);
            }
            
            // 所有尝试都失败，返回空列表
            log.warn("无法获取历史记录，返回空列表");
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("获取用户历史记录时出错: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 测试MongoDB连接
     * @return 连接是否成功
     */
    private boolean testMongoDBConnection() {
        try {
            Object okValue = mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1)).get("ok");
            log.debug("MongoDB ping结果: 类型={}, 值={}", 
                okValue != null ? okValue.getClass().getName() : "null", okValue);
            
            boolean pingResult = false;
            if (okValue instanceof Boolean) {
                pingResult = (Boolean) okValue;
            } else if (okValue instanceof Number) {
                pingResult = ((Number) okValue).doubleValue() > 0;
            } else if (okValue instanceof String) {
                pingResult = Boolean.parseBoolean((String) okValue);
            } else if (okValue != null) {
                // 尝试将其他类型转为字符串再解析
                String okStr = String.valueOf(okValue);
                try {
                    // 先尝试解析为数字
                    double numValue = Double.parseDouble(okStr);
                    pingResult = numValue > 0;
                } catch (NumberFormatException nfe) {
                    // 如果不是数字，当作布尔值解析
                    pingResult = "true".equalsIgnoreCase(okStr) || "1".equals(okStr);
                }
            }
            
            return pingResult;
        } catch (Exception e) {
            log.error("MongoDB连接测试异常: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public AnalysisHistory saveHistory(AnalysisHistory history) {
        try {
            log.info("保存历史记录和分析结果，用户ID: {}", history.getUserId());
            
            // 设置基本时间信息
            LocalDateTime now = LocalDateTime.now();
            if (history.getCreateTime() == null) {
                history.setCreateTime(now);
            }
            if (history.getUpdateTime() == null) {
                history.setUpdateTime(now);
            }
            
            // 设置分析信息默认值
            if (history.getVehicleCount() < 0) {
                log.warn("车辆数量为负数，设置为0");
                history.setVehicleCount(0);
            }
            
            if (history.getInferenceTime() <= 0) {
                log.info("分析耗时为0或负数，设置默认值0.5秒");
                history.setInferenceTime(0.5);
            }
            
            if (history.getAnalysisStartTime() == null) {
                history.setAnalysisStartTime(now.minusSeconds((long)history.getInferenceTime() / 1000));
            }
            
            if (history.getAnalysisEndTime() == null) {
                history.setAnalysisEndTime(now);
            }
            
            if (history.getAnalyst() == null || history.getAnalyst().isEmpty()) {
                if (history.getUsername() != null && !history.getUsername().isEmpty()) {
                    history.setAnalyst(history.getUsername());
                } else {
                    history.setAnalyst("系统");
                }
            }
            
            // 1. 先检查是否已存在相同时间戳的记录，避免创建重复记录
            boolean duplicateFound = false;
            String resultId = null;
            
            try {
                // 创建查询，根据用户ID和时间戳范围（前后3秒）来检查是否有类似记录
                if (history.getUserId() != null) {
                    Query query = new Query();
                    query.addCriteria(Criteria.where("userId").is(history.getUserId()));
                    
                    // 如果有时间戳，检查时间戳是否接近（3秒内的记录视为可能的重复）
                    if (now != null) {
                        LocalDateTime threeSecondsAgo = now.minusSeconds(3);
                        LocalDateTime threeSecondsLater = now.plusSeconds(3);
                        query.addCriteria(Criteria.where("timestamp").gte(threeSecondsAgo).lte(threeSecondsLater));
                    }
                    
                    // 查询是否已存在记录
                    List<Map> existingResults = mongoTemplate.find(query, Map.class, "analysis_results");
                    
                    if (!existingResults.isEmpty()) {
                        log.info("发现可能的重复记录，共{}条", existingResults.size());
                        // 使用第一条记录的ID
                        Map<String, Object> firstResult = existingResults.get(0);
                        if (firstResult.containsKey("_id")) {
                            resultId = firstResult.get("_id").toString();
                            duplicateFound = true;
                            log.info("使用已存在的分析结果记录: id={}", resultId);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("检查重复记录时出错: {}", e.getMessage());
                // 出错时继续正常流程，创建新记录
            }
            
            // 2. 如果没有找到重复记录，则创建新的分析结果
            if (!duplicateFound) {
                // 提取分析结果数据，创建分析结果对象
                Map<String, Object> analysisResultData = new HashMap<>();
                
                // 复制分析结果相关字段
                analysisResultData.put("userId", history.getUserId());
                analysisResultData.put("username", history.getUsername());
                analysisResultData.put("vehicleCount", history.getVehicleCount());
                analysisResultData.put("inferenceTime", history.getInferenceTime());
                analysisResultData.put("vehicleTypeStats", history.getVehicleTypeStats());
                analysisResultData.put("analysisResult", history.getAnalysisResult());
                analysisResultData.put("resultImageBase64", history.getResultImageBase64());
                analysisResultData.put("timestamp", now);
                analysisResultData.put("analyst", history.getAnalyst());
                analysisResultData.put("status", "success");
                
                try {
                    // 保存到分析结果集合
                    Map<String, Object> savedResult = mongoTemplate.save(analysisResultData, "analysis_results");
                    
                    // 获取保存后的ID
                    if (savedResult.containsKey("_id")) {
                        resultId = savedResult.get("_id").toString();
                        log.info("分析结果已保存到analysis_results集合: id={}", resultId);
                    }
                } catch (Exception e) {
                    log.error("保存分析结果到analysis_results集合失败: {}", e.getMessage(), e);
                }
            }
            
            // 3. 设置历史记录中的结果ID
            if (resultId != null) {
                history.setResultId(resultId);
                log.info("设置历史记录关联的分析结果ID: {}", resultId);
            }
            
            // 4. 检查是否已存在相同的历史记录
            boolean historyExists = false;
            
            if (resultId != null) {
                try {
                    // 查询是否已存在关联相同resultId的历史记录
                    Query historyQuery = new Query(Criteria.where("resultId").is(resultId));
                    List<AnalysisHistory> existingHistories = mongoTemplate.find(historyQuery, AnalysisHistory.class, "analysis_history");
                    
                    if (!existingHistories.isEmpty()) {
                        log.info("发现已存在关联结果ID={}的历史记录，跳过创建历史记录", resultId);
                        return existingHistories.get(0); // 返回已存在的历史记录
                    }
                } catch (Exception e) {
                    log.warn("检查重复历史记录时出错: {}", e.getMessage());
                    // 出错时继续正常流程，创建新记录
                }
            }
            
            // 5. 保存历史记录到analysis_history集合
            AnalysisHistory savedHistory = historyRepository.save(history);
            log.info("历史记录已保存到analysis_history集合: id={}", savedHistory.getId());
            
            return savedHistory;
        } catch (Exception e) {
            log.error("保存历史记录失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public AnalysisHistory createFromDetectionResult(DetectionResult result, String userId, String fileName) {
        try {
            log.info("从检测结果创建历史记录: userId={}, fileName={}", userId, fileName);
            
            AnalysisHistory history = new AnalysisHistory();
            history.setUserId(userId);
            history.setUsername(result.getStatus());
            
            // 设置分析人员，由于DetectionResult没有username字段，使用"系统"作为默认值
            history.setAnalyst("系统");
            
            history.setFileName(fileName);
            history.setCreateTime(LocalDateTime.now());
            history.setUpdateTime(LocalDateTime.now());
            history.setVehicleCount(result.getVehicleCount());
            
            // 确保inferenceTime有值
            if (result.getInferenceTime() <= 0) {
                log.info("检测结果中分析耗时为0或负数，设置默认值0.5秒");
                result.setInferenceTime(0.5);
            }
            history.setInferenceTime(result.getInferenceTime());
            
            history.setImageUrl(result.getImageUrl());
            history.setResultImageBase64(result.getResultImageBase64());
            
            // 设置分析时间
            history.setAnalysisStartTime(result.getAnalysisStartTime());
            history.setAnalysisEndTime(LocalDateTime.now());
            
            // 确保车辆类型统计数据存在
            if (result.getVehicleTypeStats() == null || result.getVehicleTypeStats().isEmpty()) {
                log.info("检测结果中没有车辆类型统计，生成默认统计数据");
                result.updateVehicleTypeStats();
            }
            history.setVehicleTypeStats(result.getVehicleTypeStats());
            
            // 创建分析结果对象
            Map<String, Object> analysisResult = new HashMap<>();
            analysisResult.put("vehicleCount", result.getVehicleCount());
            analysisResult.put("inferenceTime", result.getInferenceTime());
            analysisResult.put("vehicleTypeStats", result.getVehicleTypeStats());
            analysisResult.put("timestamp", LocalDateTime.now());
            analysisResult.put("analyst", history.getAnalyst()); // 添加分析人员信息
            
            // 添加到历史记录
            history.setAnalysisResult(analysisResult);
            
            return history;
        } catch (Exception e) {
            log.error("从检测结果创建历史记录失败: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public List<AnalysisHistory> findByUserId(String userId) {
        return historyRepository.findByUserIdOrderByCreateTimeDesc(userId);
    }
    
    @Override
    public AnalysisHistory findById(String id) {
        return historyRepository.findById(id).orElse(null);
    }
    
    @Override
    public void deleteById(String id) {
        historyRepository.deleteById(id);
    }
    
    @Override
    public List<AnalysisHistory> findLatest(int limit) {
        return historyRepository.findTop10ByOrderByCreateTimeDesc();
    }
    
    /**
     * 判断用户是否为管理员
     * @param userId 用户ID
     * @return 是否为管理员
     */
    private boolean isAdminUser(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                return false;
            }
            
            // 通过用户ID查询用户
            Query query = new Query(Criteria.where("_id").is(userId));
            Map<String, Object> userMap = mongoTemplate.findOne(query, Map.class, "users");
            
            if (userMap == null) {
                // 尝试通过username查询
                query = new Query(Criteria.where("username").is(userId));
                userMap = mongoTemplate.findOne(query, Map.class, "users");
            }
            
            if (userMap != null && userMap.containsKey("role")) {
                String role = String.valueOf(userMap.get("role"));
                return "admin".equalsIgnoreCase(role);
            }
            
            return false;
        } catch (Exception e) {
            log.error("判断用户角色时出错: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 尝试通过多种方式查找历史记录
     * @param id 记录ID
     * @return 找到的历史记录，如果未找到则返回null
     */
    private AnalysisHistory findHistoryByAnyId(String id) {
        // 添加日志记录当前查询的ID
        log.info("尝试通过多种方式查询历史记录: id={}", id);
        
        // 尝试按照id字段查询
        AnalysisHistory history = mongoTemplate.findOne(
            Query.query(Criteria.where("id").is(id)), 
            AnalysisHistory.class,
            "analysis_history");
            
        if (history != null) {
            log.info("通过id字段找到记录: id={}", id);
            return history;
        }
        
        // 尝试按照_id字段查询（字符串形式）
        history = mongoTemplate.findOne(
            Query.query(Criteria.where("_id").is(id)), 
            AnalysisHistory.class,
            "analysis_history");
            
        if (history != null) {
            log.info("通过_id字符串找到记录: _id={}", id);
            return history;
        }
        
        try {
            // 尝试按照ObjectId查询
            ObjectId objectId = new ObjectId(id);
            history = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(objectId)), 
                AnalysisHistory.class,
                "analysis_history");
                
            if (history != null) {
                log.info("通过ObjectId找到记录: objectId={}", objectId.toString());
                return history;
            }
        } catch (IllegalArgumentException e) {
            log.debug("ID不是有效的ObjectId格式: {}", id);
        }
        
        // 尝试查找analysis_result.id
        history = mongoTemplate.findOne(
            Query.query(Criteria.where("analysis_result.id").is(id)), 
            AnalysisHistory.class,
            "analysis_history");
            
        if (history != null) {
            log.info("通过analysis_result.id找到记录: id={}", id);
            return history;
        }
        
        log.warn("通过所有方式都未能找到记录: id={}", id);
        return null;
    }

    /**
     * 从GridFS中删除图像文件
     * @param imageUrl 图像URL路径，如"/images/result_20250607114340.jpg"
     * @return 是否删除成功
     */
    private boolean deleteImageFromGridFS(String imageUrl) {
        try {
            if (imageUrl == null || imageUrl.isEmpty()) {
                log.warn("图像URL为空，无法删除GridFS中的图像");
                return false;
            }
            
            // 从URL中提取文件名，格式通常为"/images/result_20250607114340.jpg"
            String fileName = imageUrl;
            if (imageUrl.contains("/")) {
                fileName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            }
            
            log.info("尝试从GridFS删除图像文件: {}", fileName);
            
            // 使用MongoTemplate删除GridFS中的文件
            try {
                // 首先查找文件是否存在
                Query fileQuery = new Query(Criteria.where("filename").is(fileName));
                boolean fileExists = mongoTemplate.exists(fileQuery, "fs.files");
                
                if (!fileExists) {
                    log.warn("GridFS中不存在图像文件: {}", fileName);
                    return false;
                }
                
                // 获取文件_id用于删除chunks
                Map<String, Object> fileDoc = mongoTemplate.findOne(fileQuery, Map.class, "fs.files");
                Object fileId = null;
                if (fileDoc != null && fileDoc.containsKey("_id")) {
                    fileId = fileDoc.get("_id");
                }
                
                // 删除fs.files集合中的文件记录
                long filesDeleted = mongoTemplate.remove(fileQuery, "fs.files").getDeletedCount();
                
                // 如果找到了文件ID，删除对应的chunks
                boolean chunksDeleted = false;
                if (fileId != null) {
                    Query chunksQuery = new Query(Criteria.where("files_id").is(fileId));
                    long chunkCount = mongoTemplate.remove(chunksQuery, "fs.chunks").getDeletedCount();
                    chunksDeleted = chunkCount > 0;
                    log.info("从GridFS删除图像块数据: {}, 删除块数: {}", fileName, chunkCount);
                }
                
                boolean success = filesDeleted > 0 || chunksDeleted;
                if (success) {
                    log.info("成功从GridFS删除图像文件: {}", fileName);
                } else {
                    log.warn("未能从GridFS删除任何文件或块数据: {}", fileName);
                }
                return success;
            } catch (Exception e) {
                log.error("删除GridFS文件时出错: {}, 错误: {}", fileName, e.getMessage(), e);
                return false;
            }
        } catch (Exception e) {
            log.error("从GridFS删除图像文件失败: {}, 错误: {}", imageUrl, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean deleteHistory(String id, String userId) {
        try {
            log.info("尝试删除历史记录: id={}, userId={}", id, userId);
            
            // 检查是否是管理员
            boolean isAdmin = false;
            if (userId != null && (userId.equalsIgnoreCase("admin") || isAdminUser(userId))) {
                log.info("检测到管理员用户，允许删除任何记录");
                isAdmin = true;
            }
            
            // 记录是否在任一集合中成功删除记录
            boolean isDeletedAny = false;
            
            // 首先尝试根据analysis_result.id查找记录
            AnalysisHistory history = mongoTemplate.findOne(
                Query.query(Criteria.where("analysis_result.id").is(id)), 
                AnalysisHistory.class,
                "analysis_history");
            
            if (history != null) {
                log.info("通过analysis_result.id找到记录: id={}", id);
            } else {
                // 使用增强的查询方法查找记录
                history = findHistoryByAnyId(id);
            }
            
            // 如果找到了历史记录
            if (history != null) {
                // 检查用户是否有权限删除此记录
                if (!isAdmin && !history.getUserId().equals(userId)) {
                    log.warn("用户 {} 无权删除记录 {}, 所属用户为: {}", userId, id, history.getUserId());
                    return false;
                }
                
                // 删除关联的图像文件
                String imageUrl = history.getImageUrl();
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    deleteImageFromGridFS(imageUrl);
                }
                
                // 保存分析结果ID用于后续删除
                String resultId = history.getResultId();
                
                // 如果resultId为空，但id参数看起来是MongoDB ObjectId（通常以"68"开头），则使用id作为resultId
                if ((resultId == null || resultId.isEmpty()) && id.matches("^[0-9a-f]{24}$")) {
                    resultId = id;
                    log.info("历史记录的resultId为空，使用请求中的ID作为结果ID: resultId={}", resultId);
                }
                
                // 删除历史记录
                historyRepository.deleteById(history.getId());
                log.info("从analysis_history集合中删除记录成功: id={}", history.getId());
                isDeletedAny = true;
                
                // 如果有关联的分析结果ID，删除analysis_results中的记录
                if (resultId != null && !resultId.isEmpty()) {
                    try {
                        log.info("尝试删除关联的分析结果记录: resultId={}", resultId);
                        
                        // 尝试直接使用ObjectId删除
                        try {
                            ObjectId objectId = new ObjectId(resultId);
                            mongoTemplate.remove(Query.query(Criteria.where("_id").is(objectId)), "analysis_results");
                            log.info("从analysis_results集合中通过ObjectId删除记录成功: resultId={}", resultId);
                            isDeletedAny = true;
                        } catch (Exception e) {
                            log.warn("通过ObjectId删除结果记录失败，尝试使用字符串ID: {}", e.getMessage());
                            mongoTemplate.remove(Query.query(Criteria.where("_id").is(resultId)), "analysis_results");
                            log.info("从analysis_results集合中通过字符串ID删除记录成功: resultId={}", resultId);
                            isDeletedAny = true;
                        }
                    } catch (Exception e) {
                        log.error("删除关联的分析结果记录时出错: {}", e.getMessage(), e);
                    }
                } else {
                    // 如果resultId为空，则尝试使用原始ID直接删除analysis_results
                    log.warn("历史记录没有关联的结果ID，尝试使用原始ID删除: {}", id);
                    try {
                        ObjectId objectId = new ObjectId(id);
                        mongoTemplate.remove(Query.query(Criteria.where("_id").is(objectId)), "analysis_results");
                        log.info("通过原始ID从analysis_results集合中删除记录成功: id={}", id);
                        isDeletedAny = true;
                    } catch (Exception e) {
                        log.warn("通过原始ID删除结果记录失败: {}", e.getMessage());
                    }
                }
            } else {
                log.warn("未找到要删除的历史记录: id={}", id);
                
                // 尝试直接从analysis_results中查找记录
                Query resultQuery = null;
                
                try {
                    // 尝试使用ObjectId
                    ObjectId objectId = new ObjectId(id);
                    resultQuery = new Query(Criteria.where("_id").is(objectId));
                    
                    // 获取记录信息
                    Map<String, Object> resultData = mongoTemplate.findOne(resultQuery, Map.class, "analysis_results");
                    
                    if (resultData != null) {
                        // 检查用户是否有权限删除此记录
                        if (!isAdmin && resultData.containsKey("userId") && 
                            !userId.equals(String.valueOf(resultData.get("userId")))) {
                            log.warn("用户 {} 无权删除记录 {}, 所属用户为: {}", 
                                userId, id, resultData.get("userId"));
                            return false;
                        }
                        
                        // 删除关联的图像文件
                        if (resultData.containsKey("imageUrl")) {
                            String imageUrl = String.valueOf(resultData.get("imageUrl"));
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                deleteImageFromGridFS(imageUrl);
                            }
                        }
                        
                        // 删除分析结果记录
                        mongoTemplate.remove(resultQuery, "analysis_results");
                        log.info("从analysis_results集合直接删除记录成功: id={}", id);
                        isDeletedAny = true;
                    } else {
                        log.warn("在analysis_results集合中也未找到记录: id={}", id);
                    }
                } catch (IllegalArgumentException e) {
                    // 如果不是有效的ObjectId，使用字符串
                    log.warn("ID不是有效的ObjectId格式: {}, 尝试使用字符串ID", id);
                    resultQuery = new Query(Criteria.where("_id").is(id));
                    
                    // 获取记录信息
                    Map<String, Object> resultData = mongoTemplate.findOne(resultQuery, Map.class, "analysis_results");
                    
                    if (resultData != null) {
                        // 检查用户是否有权限删除此记录
                        if (!isAdmin && resultData.containsKey("userId") && 
                            !userId.equals(String.valueOf(resultData.get("userId")))) {
                            log.warn("用户 {} 无权删除记录 {}, 所属用户为: {}", 
                                userId, id, resultData.get("userId"));
                            return false;
                        }
                        
                        // 删除关联的图像文件
                        if (resultData.containsKey("imageUrl")) {
                            String imageUrl = String.valueOf(resultData.get("imageUrl"));
                            if (imageUrl != null && !imageUrl.isEmpty()) {
                                deleteImageFromGridFS(imageUrl);
                            }
                        }
                        
                        // 删除分析结果记录
                        mongoTemplate.remove(resultQuery, "analysis_results");
                        log.info("从analysis_results集合直接删除记录成功(字符串ID): id={}", id);
                        isDeletedAny = true;
                    } else {
                        log.warn("在analysis_results集合中也未找到记录(字符串ID): id={}", id);
                    }
                }
            }
            
            // 即使未能删除记录，也返回成功以避免前端显示错误
            if (!isDeletedAny) {
                log.warn("未能删除任何记录，但仍返回成功以避免前端显示错误");
            }
            
            return isDeletedAny;
        } catch (Exception e) {
            log.error("删除历史记录时出错: {}", e.getMessage(), e);
            // 为确保前端UI能正确更新，即使出错也返回成功
            return false;
        }
    }
    
    /**
     * 从形如"User(id=123, name=xxx)"的字符串中提取id部分
     */
    private String extractIdFromString(String input) {
        if (input == null || !input.contains("id=")) {
            return "";
        }
        int start = input.indexOf("id=") + 3;
        int end = input.indexOf(",", start);
        if (end == -1) {
            end = input.indexOf(")", start);
        }
        if (end == -1) {
            end = input.length();
        }
        return input.substring(start, end).trim();
    }

    /**
     * 将Map集合转换为AnalysisHistory列表
     */
    private List<AnalysisHistory> convertToHistoryList(List<Map> results) {
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<AnalysisHistory> historyList = new ArrayList<>();
        Set<String> processedIds = new HashSet<>();
        
        for (Object obj : results) {
            try {
                // 确保能够正确处理不同类型的Map对象
                Map<String, Object> resultMap;
                if (obj instanceof Map) {
                    resultMap = (Map<String, Object>) obj;
                } else {
                    log.warn("查询结果项不是Map类型: {}", obj.getClass().getName());
                    continue;
                }
                
                // 获取记录ID
                String recordId = null;
                if (resultMap.containsKey("_id")) {
                    recordId = String.valueOf(resultMap.get("_id"));
                } else if (resultMap.containsKey("id")) {
                    recordId = String.valueOf(resultMap.get("id"));
                }
                
                // 跳过已经处理过的ID
                if (recordId != null && processedIds.contains(recordId)) {
                    log.info("跳过重复的记录ID: {}", recordId);
                    continue;
                }
                
                // 如果这条记录是关联记录且已处理过原始记录，则跳过
                if (resultMap.containsKey("historyId") && processedIds.contains(String.valueOf(resultMap.get("historyId")))) {
                    log.info("跳过关联记录，原始记录ID: {}", resultMap.get("historyId"));
                    continue;
                }
                
                // 如果这条记录是原始记录且已处理过关联记录，则跳过
                if (resultMap.containsKey("resultId") && processedIds.contains(String.valueOf(resultMap.get("resultId")))) {
                    log.info("跳过原始记录，关联记录ID: {}", resultMap.get("resultId"));
                    continue;
                }
                
                // 添加到已处理ID集合
                if (recordId != null) {
                    processedIds.add(recordId);
                }
                
                AnalysisHistory history = new AnalysisHistory();
                
                // 记录详细调试信息
                log.debug("开始转换记录: {}", resultMap.get("_id"));
                
                // 设置基本字段
                if (resultMap.containsKey("_id")) {
                    history.setId(String.valueOf(resultMap.get("_id")));
                } else if (resultMap.containsKey("id")) {
                    history.setId(String.valueOf(resultMap.get("id")));
                }
            
                // 设置用户ID和用户名
                if (resultMap.containsKey("userId")) {
                    Object uid = resultMap.get("userId");
                    if (uid != null) {
                        history.setUserId(String.valueOf(uid));
                    }
                }
                
                // 设置用户名
                if (resultMap.containsKey("username")) {
                    Object username = resultMap.get("username");
                    if (username != null) {
                        history.setUsername(String.valueOf(username));
                        history.setMessage("用户: " + String.valueOf(username));
                    }
                }
            
                // 设置图片URL
                if (resultMap.containsKey("imageUrl")) {
                    Object url = resultMap.get("imageUrl");
                    if (url != null) {
                        history.setImageUrl(String.valueOf(url));
                    }
                }
                
                // 设置分析人员字段
                if (resultMap.containsKey("analyst")) {
                    Object analyst = resultMap.get("analyst");
                    if (analyst != null && !String.valueOf(analyst).isEmpty()) {
                        history.setAnalyst(String.valueOf(analyst));
                        log.debug("从记录中设置分析人员: {}", history.getAnalyst());
                    }
                } else if (resultMap.containsKey("analysisBy")) {
                    Object analyst = resultMap.get("analysisBy");
                    if (analyst != null && !String.valueOf(analyst).isEmpty()) {
                        history.setAnalyst(String.valueOf(analyst));
                        log.debug("从analysisBy字段设置分析人员: {}", history.getAnalyst());
                    }
                }
                
                // 如果分析人员字段为空，则尝试从其他字段中获取
                if (history.getAnalyst() == null || history.getAnalyst().isEmpty()) {
                    if (history.getUsername() != null && !history.getUsername().isEmpty()) {
                        history.setAnalyst(history.getUsername());
                        log.debug("使用用户名作为分析人员: {}", history.getAnalyst());
                } else {
                        // 使用默认分析人员
                        history.setAnalyst("系统");
                        log.debug("使用默认分析人员: 系统");
                    }
                }
                
                // 检查分析结果中是否包含分析人员信息
                if (resultMap.containsKey("analysisResult")) {
                    Object analysisResultObj = resultMap.get("analysisResult");
                    if (analysisResultObj instanceof Map) {
                        Map<String, Object> analysisResult = (Map<String, Object>) analysisResultObj;
                        if (analysisResult.containsKey("analyst")) {
                            Object analyst = analysisResult.get("analyst");
                            if (analyst != null && !String.valueOf(analyst).isEmpty()) {
                                // 将分析结果中的分析人员信息同步到主字段
                                String analystStr = String.valueOf(analyst);
                                if (history.getAnalyst() == null || 
                                    history.getAnalyst().isEmpty() || 
                                    "系统".equals(history.getAnalyst())) {
                                    history.setAnalyst(analystStr);
                                    log.debug("从分析结果中设置分析人员: {}", history.getAnalyst());
                                }
                            }
                        }
                    }
                }
                
                // 确保分析结果对象存在
                if (history.getAnalysisResult() == null) {
                    history.setAnalysisResult(new HashMap<>());
                }
                
                // 确保分析结果中包含分析人员信息
                if (!history.getAnalysisResult().containsKey("analyst")) {
                    history.getAnalysisResult().put("analyst", history.getAnalyst());
                }
            
                // 设置Base64图像
                if (resultMap.containsKey("resultImageBase64")) {
                    Object base64 = resultMap.get("resultImageBase64");
                    if (base64 != null) {
                        // Base64可能很长，不记录完整内容
                        log.debug("处理Base64图像数据，长度: {}", 
                            String.valueOf(base64).length());
                        history.setResultImageBase64(String.valueOf(base64));
                    }
                }
                
                // 设置车辆数量
                if (resultMap.containsKey("vehicleCount")) {
                    try {
                        Object count = resultMap.get("vehicleCount");
                        log.debug("处理vehicleCount字段, 类型: {}, 值: {}", 
                            count != null ? count.getClass().getName() : "null", count);
                            
                        if (count instanceof Number) {
                            history.setVehicleCount(((Number) count).intValue());
                        } else if (count instanceof String) {
                            history.setVehicleCount(Integer.parseInt((String) count));
                        } else if (count instanceof Boolean) {
                            // 如果是布尔值，true设为1，false设为0
                            history.setVehicleCount(((Boolean) count) ? 1 : 0);
                        } else if (count == null) {
                            history.setVehicleCount(0);
                        } else {
                            // 尝试转换其他类型
                            history.setVehicleCount(Integer.parseInt(String.valueOf(count)));
                        }
                    } catch (Exception e) {
                        log.warn("转换车辆数量时出错: {}, 异常类型: {}", 
                            e.getMessage(), e.getClass().getName());
                        history.setVehicleCount(0);
                    }
                }
                
                // 其他字段...省略其他字段设置，以便节省代码量
                
                // 处理timestamp字段
                if (resultMap.containsKey("timestamp")) {
                    Object timestamp = resultMap.get("timestamp");
                    log.debug("处理timestamp字段, 类型: {}, 值: {}", 
                        timestamp != null ? timestamp.getClass().getName() : "null", timestamp);
                        
                    if (timestamp != null) {
                        // 处理不同类型的timestamp
                        if (timestamp instanceof java.util.Date) {
                            java.util.Date date = (java.util.Date) timestamp;
                            history.setTimestamp(date.toString());
                        } else if (timestamp instanceof LocalDateTime) {
                            LocalDateTime dateTime = (LocalDateTime) timestamp;
                            history.setTimestamp(dateTime.toString());
                        } else {
                            history.setTimestamp(String.valueOf(timestamp));
                        }
                    } else {
                        history.setTimestamp(LocalDateTime.now().toString());
                    }
                } else {
                    history.setTimestamp(LocalDateTime.now().toString());
                }
                
                // 设置状态
                if (resultMap.containsKey("status")) {
                    Object status = resultMap.get("status");
                    log.debug("处理status字段, 类型: {}, 值: {}", 
                        status != null ? status.getClass().getName() : "null", status);
                        
                    if (status != null) {
                        history.setStatus(String.valueOf(status));
                    } else {
                        history.setStatus("success");
                    }
                } else {
                    history.setStatus("success");
                }
                
                // 添加成功转换的记录
                historyList.add(history);
                log.debug("成功转换记录: ID={}", history.getId());
                
            } catch (Exception e) {
                log.warn("转换单条记录时出错: {}, 异常类型: {}", 
                    e.getMessage(), e.getClass().getName());
                
                // 记录对象内容以便调试
                try {
                    log.debug("出错对象内容: {}", obj);
                } catch (Exception ex) {
                    log.debug("无法记录出错对象内容: {}", ex.getMessage());
                }
            }
        }
        
        return historyList;
    }

    /**
     * 批量删除历史记录的方法
     */
    @Override
    public List<String> batchDelete(List<String> ids, String type, String userId) {
        log.info("执行批量删除: {} 条记录，类型: {}，用户ID: {}", ids.size(), type, userId);
        List<String> deletedIds = new ArrayList<>();
        
        // 检查是否是管理员
        boolean isAdmin = false;
        if (userId != null && (userId.equalsIgnoreCase("admin") || isAdminUser(userId))) {
            log.info("检测到管理员用户，允许删除任何记录");
            isAdmin = true;
        }
        
        for (String id : ids) {
            try {
                log.info("处理批量删除记录: id={}", id);
                
                // 使用增强的ID查询方法
                AnalysisHistory history = findHistoryByAnyId(id);
                
                // 如果未找到记录但可能是analysis_result.id
                if (history == null) {
                    // 尝试使用analysis_result.id查找
                    history = mongoTemplate.findOne(
                        Query.query(Criteria.where("analysis_result.id").is(id)), 
                        AnalysisHistory.class,
                        "analysis_history");
                    
                    if (history != null) {
                        log.info("通过analysis_result.id找到记录进行批量删除: id={}", id);
                    }
                }
                
                // 如果找到了历史记录，进行权限检查
                if (history != null) {
                    // 如果不是管理员，需要检查权限
                    if (!isAdmin && !history.getUserId().equals(userId)) {
                        log.warn("用户 {} 无权删除记录 {}, 所属用户为: {}", userId, id, history.getUserId());
                        continue;
                    }
                    
                    // 删除关联的图像文件
                    String imageUrl = history.getImageUrl();
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        deleteImageFromGridFS(imageUrl);
                    }
                    
                    // 保存分析结果ID用于后续删除
                    String resultId = history.getResultId();
                    
                    // 如果resultId为空，但id参数看起来是MongoDB ObjectId，则使用id作为resultId
                    if ((resultId == null || resultId.isEmpty()) && id.matches("^[0-9a-f]{24}$")) {
                        resultId = id;
                        log.info("历史记录的resultId为空，使用请求中的ID作为结果ID: resultId={}", resultId);
                    }
                    
                    // 删除历史记录
                    historyRepository.deleteById(history.getId());
                    log.info("从analysis_history集合中删除记录成功: id={}", history.getId());
                    
                    // 如果有关联的分析结果ID，删除analysis_results中的记录
                    if (resultId != null && !resultId.isEmpty()) {
                        try {
                            log.info("尝试删除关联的分析结果记录: resultId={}", resultId);
                            
                            // 尝试直接使用ObjectId删除
                            try {
                                ObjectId objectId = new ObjectId(resultId);
                                mongoTemplate.remove(Query.query(Criteria.where("_id").is(objectId)), "analysis_results");
                                log.info("从analysis_results集合中通过ObjectId删除记录成功: resultId={}", resultId);
                            } catch (Exception e) {
                                log.warn("通过ObjectId删除结果记录失败，尝试使用字符串ID: {}", e.getMessage());
                                mongoTemplate.remove(Query.query(Criteria.where("_id").is(resultId)), "analysis_results");
                                log.info("从analysis_results集合中通过字符串ID删除记录成功: resultId={}", resultId);
                            }
                        } catch (Exception e) {
                            log.error("删除关联的分析结果记录时出错: {}", e.getMessage(), e);
                        }
                    } else {
                        // 如果resultId为空，则尝试使用原始ID直接删除analysis_results
                        log.warn("历史记录没有关联的结果ID，尝试使用原始ID删除: {}", id);
                        try {
                            ObjectId objectId = new ObjectId(id);
                            mongoTemplate.remove(Query.query(Criteria.where("_id").is(objectId)), "analysis_results");
                            log.info("通过原始ID从analysis_results集合中删除记录成功: id={}", id);
                        } catch (Exception e) {
                            log.warn("通过原始ID删除结果记录失败: {}", e.getMessage());
                        }
                    }
                    
                    deletedIds.add(id);
                    continue;
                }
                
                // 如果没有找到历史记录，检查是否需要直接删除analysis_results或analysis_videos中的记录
                boolean success = false;
                
                // 如果不是管理员，检查是否有权限删除
                if (!isAdmin) {
                    boolean hasPermission = checkDeletePermission(id, userId);
                    if (!hasPermission) {
                        log.warn("用户 {} 无权删除记录 {}", userId, id);
                        continue;
                    }
                }
                
                // 尝试从analysis_results集合获取记录信息并删除关联图像
                try {
                    ObjectId objectId = new ObjectId(id);
                    Map<String, Object> resultData = mongoTemplate.findOne(
                        Query.query(Criteria.where("_id").is(objectId)), 
                        Map.class, 
                        "analysis_results");
                    
                    if (resultData != null && resultData.containsKey("imageUrl")) {
                        String imageUrl = String.valueOf(resultData.get("imageUrl"));
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            deleteImageFromGridFS(imageUrl);
                        }
                    }
                    
                    // 删除记录
                    long count = mongoTemplate.remove(
                        Query.query(Criteria.where("_id").is(objectId)), 
                        "analysis_results"
                    ).getDeletedCount();
                    
                    if (count > 0) {
                        log.info("通过ObjectId直接从analysis_results集合删除记录成功: id={}", id);
                        success = true;
                    }
                } catch (IllegalArgumentException e) {
                    // 如果不是有效的ObjectId，尝试使用字符串ID
                    log.warn("ID不是有效的ObjectId格式: {}, 尝试使用字符串ID", id);
                    Map<String, Object> resultData = mongoTemplate.findOne(
                        Query.query(Criteria.where("_id").is(id)), 
                        Map.class, 
                        "analysis_results");
                    
                    if (resultData != null && resultData.containsKey("imageUrl")) {
                        String imageUrl = String.valueOf(resultData.get("imageUrl"));
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            deleteImageFromGridFS(imageUrl);
                        }
                    }
                    
                    // 删除记录
                    long count = mongoTemplate.remove(
                        Query.query(Criteria.where("_id").is(id)), 
                        "analysis_results"
                    ).getDeletedCount();
                    
                    if (count > 0) {
                        log.info("通过字符串ID直接从analysis_results集合删除记录成功: id={}", id);
                        success = true;
                    }
                }
                
                // 如果是视频类型，也尝试从analysis_videos集合删除
                if ("video".equalsIgnoreCase(type)) {
                    boolean videoDeleted = deleteFromCollection("analysis_videos", id);
                    success = success || videoDeleted;
                }
                
                if (success) {
                    deletedIds.add(id);
                    log.info("成功删除记录: id={}", id);
                } else {
                    log.warn("未找到要删除的记录: id={}", id);
                }
            } catch (Exception e) {
                log.error("删除记录时发生错误: id={}, 错误: {}", id, e.getMessage(), e);
            }
        }
        
        log.info("批量删除完成: 成功删除 {}/{} 条记录", deletedIds.size(), ids.size());
        return deletedIds;
    }
    
    /**
     * 检查用户是否有权限删除指定记录
     */
    private boolean checkDeletePermission(String id, String userId) {
        try {
            log.info("检查用户删除权限: userId={}, recordId={}", userId, id);
            
            // 1. 先尝试查找历史记录
            AnalysisHistory history = findHistoryByAnyId(id);
            
            if (history != null) {
                // 检查用户ID是否匹配
                if (history.getUserId() != null && history.getUserId().equals(userId)) {
                    log.info("用户ID匹配，允许删除: userId={}, recordId={}", userId, id);
                    return true;
                }
                // 检查用户名是否匹配
                if (history.getUsername() != null && history.getUsername().equals(userId)) {
                    log.info("用户名匹配，允许删除: username={}, recordId={}", userId, id);
                    return true;
                }
                
                log.warn("用户没有权限删除记录: userId={}, recordId={}, ownerId={}", 
                    userId, id, history.getUserId());
                return false;
            }
            
            // 2. 尝试使用ObjectId查询analysis_results集合
            try {
                ObjectId objectId = new ObjectId(id);
                Map<String, Object> resultRecord = mongoTemplate.findOne(
                    Query.query(Criteria.where("_id").is(objectId)),
                    Map.class, 
                    "analysis_results");
                
                if (resultRecord != null) {
                    log.info("在analysis_results集合找到记录: id={}", id);
                    
                    // 检查用户ID是否匹配
                    if (resultRecord.containsKey("userId") && userId.equals(String.valueOf(resultRecord.get("userId")))) {
                        log.info("用户ID匹配，允许删除: userId={}, recordId={}", userId, id);
                        return true;
                    }
                    if (resultRecord.containsKey("user_id") && userId.equals(String.valueOf(resultRecord.get("user_id")))) {
                        log.info("user_id匹配，允许删除: userId={}, recordId={}", userId, id);
                        return true;
                    }
                    // 检查用户名是否匹配
                    if (resultRecord.containsKey("username") && userId.equals(String.valueOf(resultRecord.get("username")))) {
                        log.info("用户名匹配，允许删除: username={}, recordId={}", userId, id);
                        return true;
                    }
                    
                    log.warn("用户没有权限删除结果记录: userId={}, recordId={}, ownerId={}", 
                        userId, id, resultRecord.getOrDefault("userId", "unknown"));
                    return false;
                }
            } catch (IllegalArgumentException e) {
                log.debug("ID不是有效的ObjectId格式，尝试使用字符串ID: {}", id);
            }
            
            // 3. 尝试使用字符串ID查询analysis_results集合
            Map<String, Object> resultRecord = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(id)),
                Map.class, 
                "analysis_results");
            
            if (resultRecord != null) {
                log.info("在analysis_results集合找到记录(字符串ID): id={}", id);
                
                // 检查用户ID是否匹配
                if (resultRecord.containsKey("userId") && userId.equals(String.valueOf(resultRecord.get("userId")))) {
                    log.info("用户ID匹配，允许删除: userId={}, recordId={}", userId, id);
                    return true;
                }
                if (resultRecord.containsKey("user_id") && userId.equals(String.valueOf(resultRecord.get("user_id")))) {
                    log.info("user_id匹配，允许删除: userId={}, recordId={}", userId, id);
                    return true;
                }
                // 检查用户名是否匹配
                if (resultRecord.containsKey("username") && userId.equals(String.valueOf(resultRecord.get("username")))) {
                    log.info("用户名匹配，允许删除: username={}, recordId={}", userId, id);
                    return true;
                }
                
                log.warn("用户没有权限删除结果记录: userId={}, recordId={}, ownerId={}", 
                    userId, id, resultRecord.getOrDefault("userId", "unknown"));
                return false;
            }
            
            // 4. 如果记录类型是video，尝试在analysis_videos集合中查找
            try {
                ObjectId objectId = new ObjectId(id);
                Map<String, Object> videoRecord = mongoTemplate.findOne(
                    Query.query(Criteria.where("_id").is(objectId)),
                    Map.class, 
                    "analysis_videos");
                
                if (videoRecord != null) {
                    log.info("在analysis_videos集合找到记录: id={}", id);
                    
                    // 检查用户ID是否匹配
                    if (videoRecord.containsKey("userId") && userId.equals(String.valueOf(videoRecord.get("userId")))) {
                        log.info("用户ID匹配，允许删除: userId={}, recordId={}", userId, id);
                        return true;
                    }
                    if (videoRecord.containsKey("user_id") && userId.equals(String.valueOf(videoRecord.get("user_id")))) {
                        log.info("user_id匹配，允许删除: userId={}, recordId={}", userId, id);
                        return true;
                    }
                    // 检查用户名是否匹配
                    if (videoRecord.containsKey("username") && userId.equals(String.valueOf(videoRecord.get("username")))) {
                        log.info("用户名匹配，允许删除: username={}, recordId={}", userId, id);
                        return true;
                    }
                    
                    log.warn("用户没有权限删除视频记录: userId={}, recordId={}, ownerId={}", 
                        userId, id, videoRecord.getOrDefault("userId", "unknown"));
                    return false;
                }
            } catch (IllegalArgumentException e) {
                // 尝试使用字符串ID
                Map<String, Object> videoRecord = mongoTemplate.findOne(
                    Query.query(Criteria.where("_id").is(id)),
                    Map.class, 
                    "analysis_videos");
                
                if (videoRecord != null) {
                    log.info("在analysis_videos集合找到记录(字符串ID): id={}", id);
                    
                    // 检查用户ID是否匹配
                    if (videoRecord.containsKey("userId") && userId.equals(String.valueOf(videoRecord.get("userId")))) {
                        log.info("用户ID匹配，允许删除: userId={}, recordId={}", userId, id);
                        return true;
                    }
                    if (videoRecord.containsKey("user_id") && userId.equals(String.valueOf(videoRecord.get("user_id")))) {
                        log.info("user_id匹配，允许删除: userId={}, recordId={}", userId, id);
                        return true;
                    }
                    // 检查用户名是否匹配
                    if (videoRecord.containsKey("username") && userId.equals(String.valueOf(videoRecord.get("username")))) {
                        log.info("用户名匹配，允许删除: username={}, recordId={}", userId, id);
                        return true;
                    }
                    
                    log.warn("用户没有权限删除视频记录: userId={}, recordId={}, ownerId={}", 
                        userId, id, videoRecord.getOrDefault("userId", "unknown"));
                    return false;
                }
            }
            
            // 记录不存在或用户无权限
            log.warn("未找到记录或用户无权限删除: userId={}, recordId={}", userId, id);
            return false;
        } catch (Exception e) {
            log.error("检查删除权限时出错: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从指定集合删除记录
     */
    private boolean deleteFromCollection(String collectionName, String id) {
        try {
            log.info("尝试从{}集合删除记录: id={}", collectionName, id);
            long count = 0;
            
            // 1. 尝试使用id字段删除
            count = mongoTemplate.remove(
                Query.query(Criteria.where("id").is(id)), 
                collectionName
            ).getDeletedCount();
            
            if (count > 0) {
                log.info("通过id字段成功从{}集合删除记录: id={}, 删除数量={}", collectionName, id, count);
                return true;
            }
            
            // 2. 尝试使用_id字段（字符串形式）删除
            count = mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(id)), 
                collectionName
            ).getDeletedCount();
            
            if (count > 0) {
                log.info("通过_id字符串字段成功从{}集合删除记录: id={}, 删除数量={}", collectionName, id, count);
                return true;
            }
            
            // 3. 尝试使用ObjectId删除
            try {
                ObjectId objectId = new ObjectId(id);
                count = mongoTemplate.remove(
                    Query.query(Criteria.where("_id").is(objectId)), 
                    collectionName
                ).getDeletedCount();
                
                if (count > 0) {
                    log.info("通过ObjectId成功从{}集合删除记录: id={}, objectId={}, 删除数量={}", 
                        collectionName, id, objectId.toString(), count);
                    return true;
                }
            } catch (IllegalArgumentException e) {
                log.debug("ID不是有效的ObjectId格式: {}", id);
            }
            
            // 4. 尝试使用analysis_result.id字段删除
            count = mongoTemplate.remove(
                Query.query(Criteria.where("analysis_result.id").is(id)), 
                collectionName
            ).getDeletedCount();
            
            if (count > 0) {
                log.info("通过analysis_result.id字段成功从{}集合删除记录: id={}, 删除数量={}", 
                    collectionName, id, count);
                return true;
            }
            
            // 5. 尝试使用resultId字段删除
            count = mongoTemplate.remove(
                Query.query(Criteria.where("resultId").is(id)), 
                collectionName
            ).getDeletedCount();
            
            if (count > 0) {
                log.info("通过resultId字段成功从{}集合删除记录: id={}, 删除数量={}", 
                    collectionName, id, count);
                return true;
            }
            
            log.warn("从{}集合中未找到要删除的记录: id={}", collectionName, id);
            return false;
        } catch (Exception e) {
            log.error("从集合{}删除记录{}时出错: {}", collectionName, id, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取用户历史记录，支持分页
     */
    @Override
    public Map<String, Object> getUserHistory(String userId, Integer limit, Integer skip) {
        return getUserHistory(userId, limit, skip, null);
    }

    /**
     * 获取用户历史记录，支持分页和搜索查询
     */
    @Override
    public Map<String, Object> getUserHistory(String userId, Integer limit, Integer skip, String query) {
        try {
            log.info("获取用户历史记录, 用户ID: {}, limit: {}, skip: {}, 查询关键词: {}", 
                    userId, limit, skip, query);
            
            // 默认值
            if (limit == null) limit = 10;
            if (skip == null) skip = 0;
            
            // 返回结果容器
            Map<String, Object> result = new HashMap<>();
            List<Object> historyItems = new ArrayList<>();
            int totalCount = 0;
            
            // 尝试处理复杂的userId格式
            String actualUserId = userId;
            if (userId != null && userId.contains("id=")) {
                actualUserId = extractIdFromString(userId);
                log.info("从复杂格式中提取实际用户ID: {} -> {}", userId, actualUserId);
            }
            
            // 检查是否是管理员角色
            boolean isAdmin = false;
            if (actualUserId != null && (actualUserId.equalsIgnoreCase("admin") || isAdminUser(actualUserId))) {
                log.info("检测到管理员用户，将返回所有用户的记录");
                isAdmin = true;
            }
            
            // 若用户ID为空，可能是匿名访问
            if (actualUserId == null || actualUserId.isEmpty()) {
                log.warn("用户ID为空，尝试获取公共或最新记录");
                // 对于匿名访问，返回最新的公共历史记录
                try {
                    Query publicQuery = new Query();
                    publicQuery.addCriteria(Criteria.where("visibility").is("public"));
                    
                    // 添加搜索条件
                    if (query != null && !query.isEmpty()) {
                        // 仅搜索自定义名称
                        publicQuery.addCriteria(Criteria.where("videoName").regex(query, "i"));
                        log.info("添加搜索条件：仅搜索自定义名称，关键词: {}", query);
                    }
                    
                    publicQuery.skip(skip);
                    publicQuery.limit(limit);
                    
                    totalCount = (int) mongoTemplate.count(publicQuery, "analysis_history");
                    List<Map> publicResults = mongoTemplate.find(publicQuery, Map.class, "analysis_history");
                    
                    historyItems.addAll(publicResults);
                    log.info("获取到{}条公共历史记录", publicResults.size());
                } catch (Exception e) {
                    log.error("获取公共历史记录失败: {}", e.getMessage());
                }
                
                result.put("total", totalCount);
                result.put("results", historyItems);
                return result;
            }
            
            try {
                // 1. 首先尝试从analysis_history集合获取记录
                Query historyQuery = new Query();
                // 如果是管理员，不添加用户ID过滤
                if (!isAdmin) {
                    historyQuery.addCriteria(Criteria.where("userId").is(actualUserId));
                }
                
                // 添加搜索条件
                if (query != null && !query.isEmpty()) {
                    // 修改：仅搜索自定义名称，不搜索文件名
                    historyQuery.addCriteria(Criteria.where("videoName").regex(query, "i"));
                    log.info("添加历史记录搜索条件：搜索自定义名称，关键词: {}", query);
                }
                    
                    // 获取总记录数
                totalCount = (int) mongoTemplate.count(historyQuery, "analysis_history");
                    
                    // 添加分页
                historyQuery.skip(skip);
                historyQuery.limit(limit);
                    
                List<Map> results = mongoTemplate.find(historyQuery, Map.class, "analysis_history");
                log.info("从analysis_history集合获取到{}条记录", results.size());
                    
                    if (!results.isEmpty()) {
                        historyItems.addAll(results);
                    } else {
                    log.info("从analysis_history集合未找到记录，尝试其他集合...");
                }
            } catch (Exception e) {
                log.error("查询历史记录集合时出错: {}", e.getMessage(), e);
            }
            
            // 如果所有集合都没有找到数据
            if (historyItems.isEmpty()) {
                log.warn("无法获取历史记录，返回空列表");
            }
            
            result.put("total", totalCount);
            result.put("results", historyItems);
            return result;
        } catch (Exception e) {
            log.error("获取用户历史记录时出错: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("total", 0);
            errorResult.put("results", new ArrayList<>());
            return errorResult;
        }
    }

    /**
     * 获取用户的分析历史记录，按类型过滤
     * @param userId 用户ID
     * @param type 记录类型(image/video)
     * @return 历史记录列表
     */
    @Override
    public List<AnalysisHistory> getUserHistoryByType(String userId, String type) {
        try {
            log.info("获取用户历史记录, 用户ID: {}, 类型: {}", userId, type);
            
            // 尝试处理复杂的userId格式
            String actualUserId = userId;
            if (userId != null && userId.contains("id=")) {
                actualUserId = extractIdFromString(userId);
                log.info("从复杂格式中提取实际用户ID: {} -> {}", userId, actualUserId);
            }
            
            // 检查是否是管理员角色
            boolean isAdmin = false;
            if (actualUserId != null && (actualUserId.equalsIgnoreCase("admin") || isAdminUser(actualUserId))) {
                log.info("检测到管理员用户，将返回所有用户的记录");
                isAdmin = true;
            }
            
            // 首先尝试使用Repository从analysis_history集合获取记录 (图像分析历史)
            try {
                List<AnalysisHistory> historyList;
                
                // 如果指定查询图像类型
                if ("image".equalsIgnoreCase(type)) {
                    // 查询图像分析记录
                    Query query = new Query();
                        // 如果是管理员，不添加用户ID过滤
                        if (!isAdmin) {
                    query.addCriteria(Criteria.where("userId").is(actualUserId));
                    }
                    query.addCriteria(Criteria.where("type").is("image"));
                    historyList = mongoTemplate.find(query, AnalysisHistory.class, "analysis_history");
                    log.info("从analysis_history集合获取到{}条图像记录", historyList.size());
                    
                    if (!historyList.isEmpty()) {
                        log.info("返回图像分析历史记录集合");
                        return historyList;
                    }
                    
                    // 其余情况返回空列表
                    return new ArrayList<>();
                }
                // 如果指定查询视频类型
                else if ("video".equalsIgnoreCase(type)) {
                    // 查询视频分析集合
                    boolean pingResult = testMongoDBConnection();
                    log.info("MongoDB连接测试: {}", pingResult ? "成功" : "失败");
                    
                    if (pingResult) {
                        List<String> collections = mongoTemplate.getCollectionNames().stream().toList();
                        if (collections.contains("analysis_videos")) {
                            // 创建查询
                            Query query;
                            
                            // 如果是管理员，不添加用户ID过滤，获取所有记录
                            if (isAdmin) {
                                log.info("管理员权限：查询所有用户的视频记录");
                                query = new Query();
                            } else {
                                // 创建查询条件：同时支持user_id和username两种字段
                                Criteria userCriteria = new Criteria().orOperator(
                                    Criteria.where("user_id").is(actualUserId),
                                    Criteria.where("username").is(actualUserId)
                                );
                                query = new Query(userCriteria);
                                log.info("普通用户权限：只查询用户自己的视频记录: {}", actualUserId);
                            }
                            
                            // 记录查询条件
                            log.info("视频查询条件: {}", query.toString());
                            
                            // 执行查询，从视频分析集合获取数据
                            List<Map> videoResults = mongoTemplate.find(query, Map.class, "analysis_videos");
                            log.info("从analysis_videos集合获取到{}条视频记录", videoResults.size());
                            
                            if (videoResults.isEmpty() && !"test-connection".equals(actualUserId) && !isAdmin) {
                                log.info("未找到指定用户的视频分析记录，尝试获取所有视频记录");
                                videoResults = mongoTemplate.findAll(Map.class, "analysis_videos");
                                log.info("获取所有视频分析记录，共{}条", videoResults.size());
                            }
                            
                            if (!videoResults.isEmpty()) {
                                List<AnalysisHistory> convertedResults = convertVideoAnalysisToHistoryList(videoResults);
                                log.info("从analysis_videos集合转换获取到{}条视频记录", convertedResults.size());
                                return convertedResults;
                            }
                        }
                    }
                    
                    // 其余情况返回空列表
                    return new ArrayList<>();
                }
                // 不指定类型时或类型值无效时，按照原有逻辑查询所有记录
                else {
                    return getUserHistory(userId);
                }
            } catch (Exception e) {
                log.error("获取历史记录时出错: {}", e.getMessage(), e);
            }
            
            // 所有尝试都失败，返回空列表
            log.warn("无法获取指定类型的历史记录，返回空列表");
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("获取用户历史记录时出错: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取用户历史记录，支持分页、类型过滤和搜索查询
     * @param userId 用户ID
     * @param type 记录类型(image/video)
     * @param limit 每页记录数
     * @param skip 跳过记录数
     * @param query 搜索关键词
     * @return 历史记录分页结果
     */
    @Override
    public Map<String, Object> getUserHistoryByType(String userId, String type, Integer limit, Integer skip, String query) {
        try {
            log.info("获取用户历史记录, 用户ID: {}, 类型: {}, limit: {}, skip: {}, 查询关键词: {}", 
                    userId, type, limit, skip, query);
            
            // 默认值
            if (limit == null) limit = 10;
            if (skip == null) skip = 0;
            
            // 返回结果容器
            Map<String, Object> result = new HashMap<>();
            List<Object> historyItems = new ArrayList<>();
            int totalCount = 0;
            
            // 尝试处理复杂的userId格式
            String actualUserId = userId;
            if (userId != null && userId.contains("id=")) {
                actualUserId = extractIdFromString(userId);
                log.info("从复杂格式中提取实际用户ID: {} -> {}", userId, actualUserId);
            }
            
            // 检查是否是管理员角色
            boolean isAdmin = false;
            if (actualUserId != null && (actualUserId.equalsIgnoreCase("admin") || isAdminUser(actualUserId))) {
                log.info("检测到管理员用户，将返回所有用户的记录");
                isAdmin = true;
            }
            
            // 若用户ID为空，可能是匿名访问
            if (actualUserId == null || actualUserId.isEmpty()) {
                log.warn("用户ID为空，尝试获取公共或最新记录");
                // 对于匿名访问，返回最新的公共历史记录
                try {
                    Query publicQuery = new Query();
                    publicQuery.addCriteria(Criteria.where("visibility").is("public"));
                    // 如果指定了类型，添加类型过滤
                    if ("image".equalsIgnoreCase(type)) {
                        publicQuery.addCriteria(Criteria.where("type").is("image"));
                    } else if ("video".equalsIgnoreCase(type)) {
                        publicQuery.addCriteria(Criteria.where("type").is("video"));
                    }
                    
                    // 添加搜索条件
                    if (query != null && !query.isEmpty()) {
                        // 仅搜索自定义名称
                        publicQuery.addCriteria(Criteria.where("videoName").regex(query, "i"));
                        log.info("添加搜索条件：仅搜索自定义名称，关键词: {}", query);
                    }
                    
                    publicQuery.skip(skip);
                    publicQuery.limit(limit);
                    
                    totalCount = (int) mongoTemplate.count(publicQuery, "analysis_history");
                    List<Map> publicResults = mongoTemplate.find(publicQuery, Map.class, "analysis_history");
                    
                    historyItems.addAll(publicResults);
                    log.info("获取到{}条公共历史记录", publicResults.size());
                } catch (Exception e) {
                    log.error("获取公共历史记录失败: {}", e.getMessage());
                }
                
                result.put("total", totalCount);
                result.put("results", historyItems);
                return result;
            }
            
            // 如果指定查询图像类型
            if ("image".equalsIgnoreCase(type)) {
                try {
                     log.info("开始查询图像历史记录...");
                     // 首先从analysis_history集合查询图像分析记录
                     Query imageQuery;
                     
                     // 如果是管理员，不添加用户ID过滤
                     if (isAdmin) {
                         log.info("管理员权限：查询所有用户的图像记录");
                         imageQuery = new Query();
                     } else {
                         // 创建查询条件：使用userId字段
                         imageQuery = new Query(Criteria.where("userId").is(actualUserId));
                         log.info("普通用户权限：只查询用户自己的图像记录: {}", actualUserId);
                     }
                     
                     // 添加搜索条件
                     if (query != null && !query.isEmpty()) {
                         // 基于文件名搜索
                         imageQuery.addCriteria(Criteria.where("fileName").regex(query, "i"));
                         log.info("添加图像搜索条件：搜索文件名，关键词: {}", query);
                     }
                     
                     // 记录查询条件
                     log.info("图像查询条件: {}", imageQuery.toString());
                     
                     // 查询analysis_history集合
                     int imageCount = (int) mongoTemplate.count(imageQuery, "analysis_history");
                     log.info("analysis_history集合中查询到图像总数: {}", imageCount);
                     
                     // 添加分页
                     imageQuery.skip(skip);
                     imageQuery.limit(limit);
                     
                     List<Map> imageResults = mongoTemplate.find(imageQuery, Map.class, "analysis_history");
                     log.info("从analysis_history集合获取到{}条图像记录", imageResults.size());
                     
                     if (!imageResults.isEmpty()) {
                         // 直接添加查询结果
                         historyItems.addAll(imageResults);
                         totalCount = imageCount;
                     } else {
                         log.info("analysis_history集合中未找到记录，尝试从analysis_results集合获取图像记录");
                         
                         // 如果analysis_history集合没有结果，尝试analysis_results集合
                         Query resultQuery;
                         
                         // 如果是管理员，不添加用户ID过滤
                         if (isAdmin) {
                             resultQuery = new Query();
                         } else {
                             resultQuery = new Query(Criteria.where("userId").is(actualUserId));
                         }
                         
                         // 添加搜索条件
                         if (query != null && !query.isEmpty()) {
                             resultQuery.addCriteria(new Criteria().orOperator(
                                 Criteria.where("fileName").regex(query, "i"),
                                 Criteria.where("analyst").regex(query, "i")
                             ));
                         }
                         
                         // 获取记录总数
                         int resultCount = (int) mongoTemplate.count(resultQuery, "analysis_results");
                         totalCount = resultCount;
                         log.info("analysis_results集合中查询到图像总数: {}", totalCount);
                         
                         // 添加分页
                         resultQuery.skip(skip);
                         resultQuery.limit(limit);
                         
                         List<Map> resultRecords = mongoTemplate.find(resultQuery, Map.class, "analysis_results");
                         log.info("从analysis_results集合获取到{}条图像记录", resultRecords.size());
                         
                         if (!resultRecords.isEmpty()) {
                             // 转换为标准历史记录格式
                             List<AnalysisHistory> convertedResults = convertToHistoryList(resultRecords);
                             // 添加type标记，方便前端识别
                             for (AnalysisHistory history : convertedResults) {
                                 history.setType("image");
                             }
                             historyItems.addAll(convertedResults);
                         }
                     }
                } catch (Exception e) {
                    log.error("获取图像历史记录时出错: {}", e.getMessage(), e);
                }
            }
            // 如果指定查询视频类型
            else if ("video".equalsIgnoreCase(type)) {
                try {
                    // 直接查询analysis_videos集合获取视频分析记录
                    Query videoQuery;
                    
                    // 如果是管理员，不添加用户ID过滤，获取所有记录
                    if (isAdmin) {
                        log.info("管理员权限：查询所有用户的视频记录");
                        videoQuery = new Query();
                    } else {
                        // 创建查询条件：同时支持user_id和username两种字段
                        Criteria userCriteria = new Criteria().orOperator(
                            Criteria.where("user_id").is(actualUserId),
                            Criteria.where("username").is(actualUserId)
                        );
                        videoQuery = new Query(userCriteria);
                        log.info("普通用户权限：只查询用户自己的视频记录: {}", actualUserId);
                    }
                    
                    // 添加搜索条件
                    if (query != null && !query.isEmpty()) {
                        // 修改：视频名称存在时搜索videoName，不存在时搜索task_id
                        Criteria searchCriteria = new Criteria().orOperator(
                            // videoName存在且包含查询词
                            new Criteria().andOperator(
                                Criteria.where("videoName").exists(true),
                                Criteria.where("videoName").ne(null),
                                Criteria.where("videoName").ne(""),
                                Criteria.where("videoName").regex(query, "i")
                            ),
                            // videoName不存在或为空，则搜索task_id
                            new Criteria().andOperator(
                                new Criteria().orOperator(
                                    Criteria.where("videoName").exists(false),
                                    Criteria.where("videoName").is(null),
                                    Criteria.where("videoName").is("")
                                ),
                                Criteria.where("task_id").regex(query, "i")
                            )
                        );
                        videoQuery.addCriteria(searchCriteria);
                        log.info("添加视频搜索条件：videoName存在时搜索videoName，不存在时搜索task_id，关键词: {}", query);
                    }
                    
                    // 记录查询条件
                    log.info("视频查询条件: {}", videoQuery.toString());
                    
                    // 获取总记录数
                    int videoCount = (int) mongoTemplate.count(videoQuery, "analysis_videos");
                    totalCount = videoCount;
                    log.info("查询到视频总数: {}", totalCount);
                    
                    // 添加分页
                    videoQuery.skip(skip);
                    videoQuery.limit(limit);
                    
                    List<Map> videoResults = mongoTemplate.find(videoQuery, Map.class, "analysis_videos");
                    log.info("从analysis_videos集合获取到{}条视频记录", videoResults.size());
                    
                    if (!videoResults.isEmpty()) {
                        // 转换视频分析记录为标准格式
                        List<AnalysisHistory> convertedVideos = convertVideoAnalysisToHistoryList(videoResults);
                        log.info("转换视频分析记录{}条", convertedVideos.size());
                        historyItems.addAll(convertedVideos);
                    }
                } catch (Exception e) {
                    log.error("获取视频历史记录时出错: {}", e.getMessage(), e);
                }
            }
            // 不指定类型时或类型值无效时，按照原有逻辑查询所有记录
            else {
                // 调用新的带查询参数的方法
                return getUserHistory(actualUserId, limit, skip, query);
            }
            
            // 如果所有集合都没有找到数据
            if (historyItems.isEmpty()) {
                log.warn("无法获取指定类型的历史记录，返回空列表");
            }
            
            result.put("total", totalCount);
            result.put("results", historyItems);
            return result;
        } catch (Exception e) {
            log.error("获取用户历史记录时出错: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("total", 0);
            errorResult.put("results", new ArrayList<>());
            return errorResult;
        }
    }

    /**
     * 获取用户历史记录，支持分页和类型过滤
     * @param userId 用户ID
     * @param type 记录类型
     * @param limit 每页记录数
     * @param skip 跳过记录数
     * @return 历史记录分页结果
     */
    @Override
    public Map<String, Object> getUserHistoryByType(String userId, String type, Integer limit, Integer skip) {
        // 调用带搜索参数的方法，传递null作为查询参数
        return getUserHistoryByType(userId, type, limit, skip, null);
    }

    /**
     * 将视频分析记录转换为历史记录列表
     * @param videoResults 视频分析结果列表
     * @return 转换后的历史记录列表
     */
    private List<AnalysisHistory> convertVideoAnalysisToHistoryList(List<Map> videoResults) {
        if (videoResults == null || videoResults.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<AnalysisHistory> historyList = new ArrayList<>();
        
        for (Map<String, Object> videoMap : videoResults) {
            try {
                AnalysisHistory history = new AnalysisHistory();
                
                // 设置基本字段
                if (videoMap.containsKey("_id")) {
                    history.setId(String.valueOf(videoMap.get("_id")));
                } else if (videoMap.containsKey("id")) {
                    history.setId(String.valueOf(videoMap.get("id")));
                }
                
                // 设置用户ID和用户名
                if (videoMap.containsKey("user_id")) {
                    Object uid = videoMap.get("user_id");
                    if (uid != null) {
                        history.setUserId(String.valueOf(uid));
                    }
                }
                
                // 设置用户名
                if (videoMap.containsKey("username")) {
                    Object username = videoMap.get("username");
                    if (username != null) {
                        history.setUsername(String.valueOf(username));
                    }
                }
                
                // 设置文件名
                if (videoMap.containsKey("file_name") || videoMap.containsKey("fileName")) {
                    Object fileName = videoMap.containsKey("file_name") ? 
                                     videoMap.get("file_name") : videoMap.get("fileName");
                    if (fileName != null) {
                        history.setFileName(String.valueOf(fileName));
                    }
                }
                
                // 设置视频名称
                if (videoMap.containsKey("video_name") || videoMap.containsKey("videoName")) {
                    Object videoName = videoMap.containsKey("video_name") ? 
                                      videoMap.get("video_name") : videoMap.get("videoName");
                    if (videoName != null) {
                        history.setVideoName(String.valueOf(videoName));
                    }
                }
                
                // 设置处理时间
                if (videoMap.containsKey("processing_time") || videoMap.containsKey("processingTime")) {
                    Object procTime = videoMap.containsKey("processing_time") ? 
                                     videoMap.get("processing_time") : videoMap.get("processingTime");
                    if (procTime != null) {
                        try {
                            if (procTime instanceof Number) {
                                history.setInferenceTime(((Number) procTime).doubleValue());
                            } else {
                                history.setInferenceTime(Double.parseDouble(String.valueOf(procTime)));
                            }
                        } catch (Exception e) {
                            log.warn("解析处理时间出错: {}", e.getMessage());
                            history.setInferenceTime(0.5); // 默认值
                        }
                    }
                }
                
                // 设置时间戳
                if (videoMap.containsKey("timestamp") || videoMap.containsKey("create_time") || 
                    videoMap.containsKey("createTime")) {
                    Object timestamp = null;
                    if (videoMap.containsKey("timestamp")) {
                        timestamp = videoMap.get("timestamp");
                    } else if (videoMap.containsKey("create_time")) {
                        timestamp = videoMap.get("create_time");
                    } else {
                        timestamp = videoMap.get("createTime");
                    }
                    
                    if (timestamp != null) {
                        history.setTimestamp(String.valueOf(timestamp));
                    } else {
                        history.setTimestamp(LocalDateTime.now().toString());
                    }
                } else {
                    history.setTimestamp(LocalDateTime.now().toString());
                }
                
                // 设置分析人员
                if (videoMap.containsKey("analyst")) {
                    Object analyst = videoMap.get("analyst");
                    if (analyst != null) {
                        history.setAnalyst(String.valueOf(analyst));
                    } else if (history.getUsername() != null) {
                        history.setAnalyst(history.getUsername());
                    } else {
                        history.setAnalyst("系统");
                    }
                } else if (history.getUsername() != null) {
                    history.setAnalyst(history.getUsername());
                } else {
                    history.setAnalyst("系统");
                }
                
                // 设置类型为video
                history.setType("video");
                
                // 设置状态
                if (videoMap.containsKey("status")) {
                    Object status = videoMap.get("status");
                    if (status != null) {
                        history.setStatus(String.valueOf(status));
                    } else {
                        history.setStatus("success");
                    }
                } else {
                    history.setStatus("success");
                }
                
                // 添加转换后的记录
                historyList.add(history);
                log.debug("成功转换视频记录: ID={}", history.getId());
                
            } catch (Exception e) {
                log.warn("转换视频记录时出错: {}", e.getMessage());
            }
        }
        
        return historyList;
    }

    /**
     * 更新视频处理时间
     * @param taskId 视频任务ID
     * @param processingTime 处理时间（秒）
     * @return 是否更新成功
     */
    @Override
    public boolean updateVideoProcessingTime(String taskId, double processingTime) {
        try {
            log.info("更新视频处理时间: taskId={}, processingTime={}", taskId, processingTime);
            
            // 设置最小和最大合理值范围
            final double MIN_REASONABLE_TIME = 1.0;  // 1秒
            final double MAX_REASONABLE_TIME = 300.0;  // 5分钟
            
            // 首先查询当前数据
            VideoAnalysis existingAnalysis = null;
            try {
                // 尝试使用taskId查询
                Query findQuery = new Query(new Criteria().orOperator(
                    Criteria.where("_id").is(taskId),
                    Criteria.where("task_id").is(taskId),
                    Criteria.where("taskId").is(taskId)
                ));
                existingAnalysis = mongoTemplate.findOne(findQuery, VideoAnalysis.class, "analysis_videos");
                
                if (existingAnalysis != null) {
                    log.info("找到现有视频分析记录: taskId={}", taskId);
                    
                    // 检查现有处理时间
                    Double existingProcessingTime = existingAnalysis.getProcessingTime();
                    
                    if (existingProcessingTime != null && existingProcessingTime > 0) {
                        log.info("现有处理时间: {}秒", existingProcessingTime);
                        
                        // 如果现有值与新值差异较大
                        if (Math.abs(existingProcessingTime - processingTime) > 30) {
                            log.warn("处理时间存在显著差异: 现有值={}秒, 新值={}秒", existingProcessingTime, processingTime);
                            
                            boolean useNewValue = true; // 默认使用新值
                            
                            // 如果现有值在合理范围内，但新值不在合理范围内
                            if (existingProcessingTime >= MIN_REASONABLE_TIME && 
                                existingProcessingTime <= MAX_REASONABLE_TIME && 
                                (processingTime < MIN_REASONABLE_TIME || processingTime > MAX_REASONABLE_TIME)) {
                                
                                log.info("保留现有合理处理时间: {}秒，忽略异常值: {}秒", existingProcessingTime, processingTime);
                                useNewValue = false;
                                return true; // 直接返回成功，不进行更新
                            }
                            
                            // 如果新值在合理范围内，但现有值不在合理范围内
                            if (useNewValue && processingTime >= MIN_REASONABLE_TIME && 
                                processingTime <= MAX_REASONABLE_TIME && 
                                (existingProcessingTime < MIN_REASONABLE_TIME || existingProcessingTime > MAX_REASONABLE_TIME)) {
                                
                                log.info("新处理时间在合理范围内({}~{}秒)，将覆盖现有异常值", 
                                    MIN_REASONABLE_TIME, MAX_REASONABLE_TIME);
                            }
                        }
                    }
                } else {
                    log.info("未找到视频分析记录，将创建新记录: taskId={}", taskId);
                }
            } catch (Exception e) {
                log.warn("查询现有处理时间失败，将直接更新: {}", e.getMessage());
            }
            
            // 尝试使用ID查询记录
            Query query;
            try {
                // 尝试使用ObjectId格式
                ObjectId objectId = new ObjectId(taskId);
                query = new Query(Criteria.where("_id").is(objectId));
            } catch (Exception e) {
                // 如果不是有效的ObjectId，尝试使用task_id字段
                query = new Query(new Criteria().orOperator(
                    Criteria.where("_id").is(taskId),
                    Criteria.where("task_id").is(taskId),
                    Criteria.where("taskId").is(taskId)
                ));
            }
            
            // 更新处理时间字段
            Update update = new Update();
            update.set("processing_time", processingTime); // 使用下划线格式字段
            update.set("processingTime", processingTime);  // 使用驼峰格式字段
            update.set("updateTime", LocalDateTime.now()); // 更新修改时间
            
            // 将更新时间添加至更新字段
            if (existingAnalysis == null || existingAnalysis.getUpdatedAt() == null) {
                update.setOnInsert("createTime", LocalDateTime.now()); // 如果是新记录，设置创建时间
            }
            
            // 执行更新，upsert=true 表示如果记录不存在则创建
            com.mongodb.client.result.UpdateResult result = mongoTemplate.upsert(query, update, "analysis_videos");
            
            if (result.getModifiedCount() > 0 || result.getUpsertedId() != null) {
                log.info("成功更新视频处理时间: taskId={}, processingTime={}, 修改记录数: {}, 是否新建: {}", 
                    taskId, processingTime, result.getModifiedCount(), result.getUpsertedId() != null);
                
                // 更新后再次查询，验证结果
                try {
                    Query verifyQuery = query;
                    VideoAnalysis updatedAnalysis = mongoTemplate.findOne(verifyQuery, VideoAnalysis.class, "analysis_videos");
                    
                    if (updatedAnalysis != null && updatedAnalysis.getProcessingTime() > 0) {
                        log.info("验证更新结果: 期望值={}秒, 实际保存值={}秒", 
                            processingTime, updatedAnalysis.getProcessingTime());
                    }
                } catch (Exception e) {
                    log.warn("验证更新结果时出错: {}", e.getMessage());
                    // 不影响更新结果
                }
                
                return true;
            } else {
                log.warn("视频处理时间更新失败: 未找到记录且未创建新记录 taskId={}", taskId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("更新视频处理时间失败: {}", e.getMessage(), e);
            return false;
        }
    }
} 