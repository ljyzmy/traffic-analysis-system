package com.traffic.analysis.model;

import java.util.List;

/**
 * 批量删除请求模型
 */
public class BatchDeleteRequest {
    private List<String> ids;
    private String type;
    private String userId;
    
    // 构造函数
    public BatchDeleteRequest() {}
    
    public BatchDeleteRequest(List<String> ids, String type) {
        this.ids = ids;
        this.type = type;
    }
    
    public BatchDeleteRequest(List<String> ids, String type, String userId) {
        this.ids = ids;
        this.type = type;
        this.userId = userId;
    }
    
    // Getter和Setter
    public List<String> getIds() {
        return ids;
    }
    
    public void setIds(List<String> ids) {
        this.ids = ids;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
} 