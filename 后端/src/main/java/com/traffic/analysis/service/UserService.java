package com.traffic.analysis.service;

import com.traffic.analysis.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * 用户服务接口
 */
public interface UserService {
    
    /**
     * 验证用户登录
     */
    User authenticate(String username, String password);
    
    /**
     * 检查用户名是否已存在 (兼容旧API)
     */
    boolean isUserExists(String username);
    
    /**
     * 注册新用户
     */
    User registerUser(String username, String password);
    
    /**
     * 注册新用户（带邮箱）
     */
    User registerUser(String username, String password, String email);
    
    /**
     * 根据ID查找用户 (兼容旧API)
     */
    User findById(String id);
    
    /**
     * 根据用户名查找用户
     */
    User findByUsername(String username);
    
    /**
     * 获取所有用户列表 (兼容旧API)
     */
    List<User> getAllUsers();
    
    /**
     * 获取用户列表
     * 
     * @param search 搜索关键词
     * @param limit 每页数量
     * @param skip 跳过记录数
     * @return 用户列表
     */
    List<User> getUsers(String search, Integer limit, int skip);
    
    /**
     * 统计用户总数
     * 
     * @param search 搜索关键词
     * @return 用户总数
     */
    int countUsers(String search);
    
    /**
     * 根据ID获取用户
     * 
     * @param id 用户ID
     * @return 用户对象
     */
    User getUserById(String id);
    
    /**
     * 检查用户名是否已存在
     * 
     * @param username 用户名
     * @return 是否存在
     */
    boolean isUsernameExists(String username);
    
    /**
     * 创建新用户
     * 
     * @param user 用户对象
     * @return 创建的用户
     */
    User createUser(User user);
    
    /**
     * 更新用户信息
     * 
     * @param user 用户对象
     * @return 更新后的用户
     */
    User updateUser(User user);
    
    /**
     * 重置用户密码
     * 
     * @param id 用户ID
     * @param newPassword 新密码
     * @return 操作是否成功
     */
    boolean resetPassword(String id, String newPassword);
    
    /**
     * 设置用户状态
     * 
     * @param id 用户ID
     * @param active 是否启用
     * @return 操作是否成功
     */
    boolean setUserStatus(String id, Boolean active);
    
    /**
     * 删除用户
     * 
     * @param id 用户ID
     * @return 操作是否成功
     */
    boolean deleteUser(String id);
} 