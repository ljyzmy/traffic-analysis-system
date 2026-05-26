package com.traffic.analysis.exception;

import org.springframework.security.core.AuthenticationException;

/**
 * 用户已禁用异常
 */
public class UserDisabledException extends AuthenticationException {
    
    public UserDisabledException(String msg) {
        super(msg);
    }
    
    public UserDisabledException(String msg, Throwable cause) {
        super(msg, cause);
    }
} 