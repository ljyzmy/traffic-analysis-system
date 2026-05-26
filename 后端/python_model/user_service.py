#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
用户服务模块 - 处理用户认证与管理
"""

import bcrypt
from bson import ObjectId
from datetime import datetime
from db_config import DatabaseConfig

class UserService:
    def __init__(self):
        db_config = DatabaseConfig()
        self.users = db_config.users
        
    def create_user(self, username, password, role="user"):
        """创建新用户"""
        # 检查用户名是否已存在
        if self.users.find_one({"username": username}):
            return False, "用户名已存在"
        
        # 密码加密
        hashed_password = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt())
        
        # 创建用户文档
        user = {
            "username": username,
            "password": hashed_password,
            "role": role,
            "createdAt": datetime.now(),
            "lastLogin": datetime.now()
        }
        
        # 插入数据库
        result = self.users.insert_one(user)
        return True, str(result.inserted_id)
    
    def verify_user(self, username, password):
        """验证用户"""
        user = self.users.find_one({"username": username})
        if not user:
            return False, "用户不存在"
        
        # 验证密码
        if bcrypt.checkpw(password.encode('utf-8'), user["password"]):
            # 更新最后登录时间
            self.users.update_one({"_id": user["_id"]}, {"$set": {"lastLogin": datetime.now()}})
            return True, user
        
        return False, "密码错误"
    
    def get_user_by_id(self, user_id):
        """通过ID获取用户"""
        try:
            return self.users.find_one({"_id": ObjectId(user_id)})
        except:
            return None
    
    def get_user_by_username(self, username):
        """通过用户名获取用户"""
        return self.users.find_one({"username": username}) 