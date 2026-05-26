package com.traffic.analysis.service;

import com.traffic.analysis.model.User;
import com.traffic.analysis.exception.UserDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl implements UserService, UserDetailsService {
    
    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);
    
    private final MongoTemplate mongoTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    
    @Autowired
    public UserServiceImpl(MongoTemplate mongoTemplate, BCryptPasswordEncoder passwordEncoder) {
        this.mongoTemplate = mongoTemplate;
        this.passwordEncoder = passwordEncoder;
    }
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        
        // 检查用户是否被禁用
        if (user.getActive() != null && !user.getActive()) {
            log.warn("尝试登录被禁用的用户账户: {}", username);
            throw new UserDisabledException("用户账户已被禁用");
        }
        
        // 确保角色不为null，如果为null则使用默认角色
        String role = user.getRole();
        if (role == null || role.isEmpty()) {
            role = "USER";
            // 更新用户角色
            user.setRole(role);
            mongoTemplate.save(user);
            log.info("已为用户{}设置默认角色USER", username);
        }
        
        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
    
    @Override
    public User authenticate(String username, String password) {
        User user = findByUsername(username);
        
        // 用户不存在
        if (user == null) {
            log.warn("认证失败: 用户不存在 - {}", username);
            return null;
        }
        
        // 检查用户是否被禁用
        if (user.getActive() != null && !user.getActive()) {
            log.warn("认证失败: 用户已被禁用 - {}", username);
            throw new UserDisabledException("用户账户已被禁用");
        }
        
        // 检查密码是否匹配
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("认证失败: 密码错误 - {}", username);
            return null;
        }
        
            log.info("用户验证成功: {}", username);
            // 更新最后登录时间
            user.setLastLoginAt(LocalDateTime.now());
            
            // 确保用户角色不为null
            if (user.getRole() == null || user.getRole().isEmpty()) {
                user.setRole("USER");
                log.info("为用户{}设置默认角色USER", username);
            }
            
            return mongoTemplate.save(user);
    }
    
    @Override
    public boolean isUserExists(String username) {
        return findByUsername(username) != null;
    }
    
    @Override
    public User registerUser(String username, String password) {
        if (isUserExists(username)) {
            log.warn("尝试注册已存在的用户名: {}", username);
            throw new RuntimeException("用户名已存在");
        }
        
        User newUser = new User();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setRole("USER"); // 默认角色
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setActive(true); // 设置为启用状态
        
        User savedUser = mongoTemplate.save(newUser);
        log.info("新用户注册成功: {}", username);
        return savedUser;
    }
    
    @Override
    public User registerUser(String username, String password, String email) {
        if (isUserExists(username)) {
            log.warn("尝试注册已存在的用户名: {}", username);
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查email是否为空，如果为空则设置为一个唯一值，避免MongoDB唯一性约束错误
        if (email == null || email.trim().isEmpty()) {
            log.warn("用户注册时未提供邮箱，将设置为唯一值");
            email = username + "@example.com"; // 使用用户名构造一个临时邮箱地址
        }
        
        User newUser = new User();
        newUser.setId(UUID.randomUUID().toString());
        newUser.setUsername(username);
        newUser.setPassword(passwordEncoder.encode(password));
        newUser.setEmail(email); // 设置email
        newUser.setRole("USER"); // 默认角色
        newUser.setCreatedAt(LocalDateTime.now());
        newUser.setActive(true); // 设置为启用状态
        
        User savedUser = mongoTemplate.save(newUser);
        log.info("新用户注册成功: {}, 邮箱: {}", username, email);
        return savedUser;
    }
    
    @Override
    public User findById(String id) {
        return mongoTemplate.findById(id, User.class);
    }
    
    @Override
    public User findByUsername(String username) {
        Query query = new Query(Criteria.where("username").is(username));
        return mongoTemplate.findOne(query, User.class);
    }
    
    @Override
    public List<User> getAllUsers() {
        return mongoTemplate.findAll(User.class);
    }
    
    @Override
    public User updateUser(User user) {
        return mongoTemplate.save(user);
    }
    
    @Override
    public List<User> getUsers(String search, Integer limit, int skip) {
        Query query = new Query();
        
        // 添加搜索条件
        if (search != null && !search.trim().isEmpty()) {
            // 使用正则表达式进行模糊搜索
            Pattern pattern = Pattern.compile(search, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                Criteria.where("username").regex(pattern),
                Criteria.where("email").regex(pattern),
                Criteria.where("phone").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }
        
        // 分页处理
        query.skip(skip);
        if (limit != null) {
            query.limit(limit);
        }
        
        return mongoTemplate.find(query, User.class);
    }
    
    @Override
    public int countUsers(String search) {
        Query query = new Query();
        
        // 添加搜索条件
        if (search != null && !search.trim().isEmpty()) {
            // 使用正则表达式进行模糊搜索
            Pattern pattern = Pattern.compile(search, Pattern.CASE_INSENSITIVE);
            Criteria searchCriteria = new Criteria().orOperator(
                Criteria.where("username").regex(pattern),
                Criteria.where("email").regex(pattern),
                Criteria.where("phone").regex(pattern)
            );
            query.addCriteria(searchCriteria);
        }
        
        return (int) mongoTemplate.count(query, User.class);
    }
    
    @Override
    public User getUserById(String id) {
        return findById(id);
    }
    
    @Override
    public boolean isUsernameExists(String username) {
        return isUserExists(username);
    }
    
    @Override
    public User createUser(User user) {
        // 确保ID是唯一的
        if (user.getId() == null || user.getId().trim().isEmpty()) {
            user.setId(UUID.randomUUID().toString());
        }
        
        // 确保密码已加密
        if (user.getPassword() != null && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        
        // 设置创建时间
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }
        
        // 确保用户状态为启用
        if (user.getActive() == null) {
            user.setActive(true);
        }
        
        // 保存用户
        User savedUser = mongoTemplate.save(user);
        log.info("新用户创建成功: {}", user.getUsername());
        return savedUser;
    }
    
    @Override
    public boolean resetPassword(String id, String newPassword) {
        try {
            // 获取用户
            User user = findById(id);
            if (user == null) {
                log.warn("尝试重置不存在的用户密码: id={}", id);
                return false;
            }
            
            // 更新密码
            user.setPassword(passwordEncoder.encode(newPassword));
            mongoTemplate.save(user);
            
            log.info("用户密码重置成功: id={}, username={}", id, user.getUsername());
            return true;
        } catch (Exception e) {
            log.error("重置用户密码时发生错误: id={}, error={}", id, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean setUserStatus(String id, Boolean active) {
        try {
            // 先检查用户是否存在
            User user = findById(id);
            if (user == null) {
                log.warn("尝试设置不存在的用户状态: id={}", id);
                return false;
            }
            
            // 使用Update更新特定字段，而不是整个文档
            Query query = new Query(Criteria.where("id").is(id));
            Update update = new Update().set("active", active);
            
            // 执行更新
            mongoTemplate.updateFirst(query, update, User.class);
            
            log.info("用户状态设置成功: id={}, username={}, active={}", id, user.getUsername(), active);
            return true;
        } catch (Exception e) {
            log.error("设置用户状态时发生错误: id={}, error={}", id, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public boolean deleteUser(String id) {
        try {
            // 先查找用户是否存在
            User user = mongoTemplate.findById(id, User.class);
            if (user == null) {
                log.warn("尝试删除不存在的用户: id={}", id);
                return false;
            }
            
            log.info("准备删除用户: id={}, username={}", id, user.getUsername());
            
            // 删除用户
            Query query = new Query(Criteria.where("id").is(id));
            mongoTemplate.remove(query, User.class);
            
            // 验证删除结果
            User checkUser = mongoTemplate.findById(id, User.class);
            if (checkUser != null) {
                log.error("用户删除失败，用户仍然存在: id={}, username={}", id, user.getUsername());
                return false;
            }
            
            log.info("用户删除成功: id={}, username={}", id, user.getUsername());
            return true;
        } catch (Exception e) {
            log.error("删除用户时发生错误: id={}, error={}", id, e.getMessage());
            return false;
        }
    }
} 