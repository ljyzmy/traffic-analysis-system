package com.traffic.analysis.service;

import com.traffic.analysis.model.AnalysisResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalysisService {
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    public AnalysisResult saveResult(AnalysisResult result) {
        return mongoTemplate.save(result);
    }
    
    public AnalysisResult findById(String id) {
        return mongoTemplate.findById(id, AnalysisResult.class);
    }
    
    /**
     * 根据图片URL查找分析结果
     * @param imageUrl 图片URL
     * @return 分析结果
     */
    public AnalysisResult findByImageUrl(String imageUrl) {
        Query query = new Query(Criteria.where("imageUrl").is(imageUrl));
        return mongoTemplate.findOne(query, AnalysisResult.class);
    }
    
    public List<AnalysisResult> findByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        return mongoTemplate.find(query, AnalysisResult.class);
    }
    
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, AnalysisResult.class);
    }
    
    public void deleteByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        mongoTemplate.remove(query, AnalysisResult.class);
    }
    
    /**
     * 获取最近的分析结果
     * @param limit 限制数量
     * @return 分析结果列表
     */
    public List<AnalysisResult> getRecentResults(int limit) {
        Query query = new Query();
        query.limit(limit);
        // 按时间倒序排序，最新的在前面
        query.with(org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "timestamp"));
        return mongoTemplate.find(query, AnalysisResult.class);
    }
} 