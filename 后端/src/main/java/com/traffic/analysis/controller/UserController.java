package com.traffic.analysis.controller;

import com.traffic.analysis.exception.UserDisabledException;
import com.traffic.analysis.model.User;
import com.traffic.analysis.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {
    
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    
    @Autowired
    private UserService userService;
    
    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        // 如果用户已登录，重定向到首页
        if (session.getAttribute("user") != null) {
            return "redirect:/";
        }
        return "login";
    }
    
    @GetMapping("/register")
    public String registerPage(HttpSession session) {
        // 如果用户已登录，重定向到首页
        if (session.getAttribute("user") != null) {
            return "redirect:/";
        }
        return "register";
    }
    
    @PostMapping("/login")
    public String processLogin(@RequestParam("username") String username,
                              @RequestParam("password") String password,
                              HttpSession session,
                              Model model) {
        log.info("处理登录请求: username={}", username);
        
        try {
            log.debug("尝试认证用户: {}", username);
            User user = userService.authenticate(username, password);
            if (user != null) {
                // 登录成功，将用户存入会话
                session.setAttribute("user", user);
                log.info("登录成功: username={}, userId={}, role={}, sessionId={}", 
                        user.getUsername(), user.getId(), user.getRole(), session.getId());
                
                // 检查会话中的用户信息
                User storedUser = (User) session.getAttribute("user");
                if (storedUser != null) {
                    log.info("验证会话存储: 成功存储用户 {} 到会话 {}", storedUser.getUsername(), session.getId());
                } else {
                    log.warn("验证会话存储: 会话 {} 中未找到用户", session.getId());
                }
                
                return "redirect:/index";
            } else {
                // 登录失败
                model.addAttribute("error", "用户名或密码错误");
                log.warn("登录失败: username={}, reason=认证失败", username);
                return "login";
            }
        } catch (UserDisabledException e) {
            // 用户被禁用的特殊处理
            model.addAttribute("error", "您的账户已被禁用，请联系管理员");
            log.warn("登录失败: username={}, reason=账户已被禁用", username);
            return "login";
        } catch (Exception e) {
            model.addAttribute("error", "登录失败: " + e.getMessage());
            log.error("登录错误: {}", e.getMessage(), e);
            return "login";
        }
    }
    
    @PostMapping("/register")
    public String processRegistration(@RequestParam("username") String username,
                                    @RequestParam("password") String password,
                                    @RequestParam("confirmPassword") String confirmPassword,
                                    Model model) {
        log.info("处理注册请求: username={}", username);
        
        // 验证密码
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "两次输入的密码不一致");
            return "register";
        }
        
        try {
            // 检查用户名是否已存在
            if (userService.isUserExists(username)) {
                model.addAttribute("error", "用户名已存在");
                return "register";
            }
            
            // 创建新用户
            User newUser = userService.registerUser(username, password);
            log.info("注册成功: username={}, userId={}", newUser.getUsername(), newUser.getId());
            
            // 注册成功，重定向到登录页面
            model.addAttribute("message", "注册成功，请登录");
            return "redirect:/login?registered=true";
        } catch (Exception e) {
            model.addAttribute("error", "注册失败: " + e.getMessage());
            log.error("注册错误: ", e);
            return "register";
        }
    }
    
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user != null) {
            log.info("用户登出: username={}, userId={}", user.getUsername(), user.getId());
        }
        
        // 清除会话
        session.invalidate();
        
        return "redirect:/login?logout=true";
    }
    
    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("user", user);
        return "profile";
    }
    
    @GetMapping("/admin")
    public String adminPage(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return "redirect:/login";
        }
        
        if (!"ADMIN".equals(user.getRole())) {
            return "redirect:/";
        }
        
        // 获取所有用户列表
        model.addAttribute("users", userService.getAllUsers());
        return "admin";
    }
} 