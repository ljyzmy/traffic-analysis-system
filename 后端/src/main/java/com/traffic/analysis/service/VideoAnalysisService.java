package com.traffic.analysis.service;

import com.traffic.analysis.model.VideoAnalysis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 视频分析服务接口
 * 提供视频分析相关的服务
 */
public interface VideoAnalysisService {

    /**
     * 保存视频分析任务
     * @param videoAnalysis 视频分析任务
     * @return 保存后的视频分析任务
     */
    VideoAnalysis saveVideoAnalysis(VideoAnalysis videoAnalysis);

    /**
     * 通过ID查找视频分析任务
     * @param id 视频分析任务ID
     * @return 视频分析任务
     */
    Optional<VideoAnalysis> findById(String id);

    /**
     * 通过任务ID查找视频分析任务
     * @param taskId 任务ID
     * @return 视频分析任务
     */
    Optional<VideoAnalysis> findByTaskId(String taskId);

    /**
     * 获取用户的视频分析任务列表
     * 根据用户角色决定返回全部任务或仅自己的任务
     * @param userId 用户ID
     * @param role 用户角色
     * @param pageable 分页参数
     * @return 用户视频分析任务列表
     */
    Page<VideoAnalysis> findByUserIdAndRole(String userId, String role, Pageable pageable);
    
    /**
     * 根据方向和角色查询视频分析记录
     * @param direction 方向
     * @param role 角色
     * @param pageable 分页参数
     * @return 视频分析记录
     */
    Page<VideoAnalysis> findByDirectionAndRole(String direction, String role, Pageable pageable);
    
    /**
     * 根据用户ID、方向和角色查询视频分析记录
     * @param userId 用户ID
     * @param direction 方向
     * @param role 角色
     * @param pageable 分页参数
     * @return 视频分析记录
     */
    Page<VideoAnalysis> findByUserIdDirectionAndRole(String userId, String direction, String role, Pageable pageable);
    
    /**
     * 上传并分析视频
     * @param videoFile 视频文件
     * @param userId 用户ID
     * @param username 用户名
     * @param role 用户角色
     * @param direction 方向(horizontal/vertical/intersection)
     * @return 视频分析任务
     * @throws IOException 文件操作异常
     */
    VideoAnalysis uploadAndAnalyzeVideo(MultipartFile videoFile, String userId, String username, String role, String direction) throws IOException;
    
    /**
     * 处理十字路口视频分析
     * @param horizontalFile 横向视频文件
     * @param verticalFile 纵向视频文件
     * @param userId 用户ID
     * @param username 用户名
     * @param role 用户角色
     * @return 视频分析任务
     * @throws IOException 文件操作异常
     */
    VideoAnalysis processIntersectionVideos(MultipartFile horizontalFile, MultipartFile verticalFile, String userId, String username, String role) throws IOException;
    
    /**
     * 更新视频分析任务进度
     * @param taskId 任务ID
     * @param progress 进度(0-100)
     * @return 更新后的视频分析任务
     */
    VideoAnalysis updateProgress(String taskId, int progress);
    
    /**
     * 更新视频分析任务状态
     * @param taskId 任务ID
     * @param status 状态
     * @param message 状态消息
     * @return 更新后的视频分析任务
     */
    VideoAnalysis updateStatus(String taskId, String status, String message);
    
    /**
     * 完成视频分析任务并保存结果
     * @param taskId 任务ID
     * @param resultData 结果数据
     * @return 更新后的视频分析任务
     */
    VideoAnalysis completeAnalysis(String taskId, Map<String, Object> resultData);
    
    /**
     * 获取视频分析结果
     * @param resultId 结果ID
     * @return 视频分析结果数据
     */
    Map<String, Object> getVideoAnalysisResult(String resultId);
    
    /**
     * 重新分析视频
     * @param taskId 任务ID
     * @return 重新分析的视频分析任务
     */
    VideoAnalysis retryAnalysis(String taskId);
    
    /**
     * 删除视频分析任务
     * @param taskId 任务ID
     * @param userId 当前用户ID
     * @param role 当前用户角色
     * @return 是否删除成功
     */
    boolean deleteAnalysis(String taskId, String userId, String role);
    
    /**
     * 重命名视频分析任务
     * @param taskId 任务ID
     * @param videoName 新的视频名称
     * @return 是否重命名成功
     */
    boolean renameVideo(String taskId, String videoName);
} 