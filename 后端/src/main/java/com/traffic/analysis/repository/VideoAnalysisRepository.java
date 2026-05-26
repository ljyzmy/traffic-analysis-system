package com.traffic.analysis.repository;

import com.traffic.analysis.model.VideoAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 视频分析数据访问层接口
 * 提供对analysis_videos集合的CRUD操作
 */
@Repository
public interface VideoAnalysisRepository extends MongoRepository<VideoAnalysis, String> {
    
    // 根据任务ID查找视频分析记录
    Optional<VideoAnalysis> findByTaskId(String taskId);
    
    // 根据用户ID查找视频分析记录
    List<VideoAnalysis> findByUserId(String userId);
    
    // 分页查询用户的视频分析记录
    Page<VideoAnalysis> findByUserId(String userId, Pageable pageable);
    
    // 根据角色查找视频分析记录
    List<VideoAnalysis> findByRole(String role);
    
    // 分页查询特定角色的视频分析记录
    Page<VideoAnalysis> findByRole(String role, Pageable pageable);
    
    // 根据方向查找视频分析记录
    List<VideoAnalysis> findByDirection(String direction);
    
    // 根据状态查找视频分析记录
    List<VideoAnalysis> findByStatus(String status);
    
    // 根据用户ID和方向查找视频分析记录
    List<VideoAnalysis> findByUserIdAndDirection(String userId, String direction);
    
    // 根据用户ID和状态查找视频分析记录
    List<VideoAnalysis> findByUserIdAndStatus(String userId, String status);
    
    // 根据结果ID查找视频分析记录
    Optional<VideoAnalysis> findByResultId(String resultId);
    
    // 根据用户ID和角色查找视频分析记录
    List<VideoAnalysis> findByUserIdAndRole(String userId, String role);
    
    // 分页查询用户特定角色的视频分析记录
    Page<VideoAnalysis> findByUserIdAndRole(String userId, String role, Pageable pageable);
    
    // 根据方向和角色查找视频分析记录
    List<VideoAnalysis> findByDirectionAndRole(String direction, String role);
    
    // 分页查询方向和角色的视频分析记录
    Page<VideoAnalysis> findByDirectionAndRole(String direction, String role, Pageable pageable);
    
    // 根据用户ID、方向和角色查找视频分析记录
    List<VideoAnalysis> findByUserIdAndDirectionAndRole(String userId, String direction, String role);
    
    // 分页查询用户ID、方向和角色的视频分析记录
    Page<VideoAnalysis> findByUserIdAndDirectionAndRole(String userId, String direction, String role, Pageable pageable);
} 