#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
MongoDB数据库配置
"""

from pymongo import MongoClient
import logging
from gridfs import GridFS
from config import MONGODB_URI, DB_NAME

class DatabaseConfig:
    def __init__(self):
        # MongoDB连接配置
        self.mongo_uri = MONGODB_URI
        self.db_name = DB_NAME
        
        try:
            # 创建MongoDB客户端
            self.client = MongoClient(self.mongo_uri)
            # 获取数据库
            self.db = self.client[self.db_name]
            # 获取集合
            self.users = self.db["users"]
            self.analysis_results = self.db["analysis_results"]
            
            # 初始化GridFS
            self.fs = GridFS(self.db)
            
            # 创建索引
            self.users.create_index([("username", 1)], unique=True)
            self.analysis_results.create_index([("userId", 1)])
            self.analysis_results.create_index([("timestamp", -1)])
            
            logging.info(f"MongoDB连接成功 (URI: {self.mongo_uri.split('@')[1] if '@' in self.mongo_uri else self.mongo_uri})")
            logging.info(f"使用数据库: {self.db_name}")
            logging.info("GridFS已初始化, 用于存储图片和视频文件")
            
            # 测试数据库连接
            self.db.command('ping')
            logging.info("数据库连接测试成功")
            
        except Exception as e:
            logging.error(f"MongoDB连接失败: {str(e)}")
            raise
    
    def check_connection(self):
        """检查数据库连接是否有效"""
        try:
            self.db.command('ping')
            return True
        except Exception as e:
            logging.error(f"数据库连接检查失败: {str(e)}")
            return False 