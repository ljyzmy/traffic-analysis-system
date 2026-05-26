#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
交通分析系统配置文件
"""

import os
from pathlib import Path
import logging

# 日志配置
LOG_LEVEL = os.environ.get("LOG_LEVEL", "INFO")
LOG_FORMAT = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'

# API配置
API_HOST = os.environ.get("API_HOST", "0.0.0.0")
API_PORT = int(os.environ.get("API_PORT", 5000))
API_DEBUG = os.environ.get("API_DEBUG", "True").lower() in ('true', '1', 't')

# 模型服务配置
MODEL_HOST = os.environ.get("MODEL_HOST", "0.0.0.0")
MODEL_PORT = int(os.environ.get("MODEL_PORT", 5001))
MODEL_DEBUG = os.environ.get("MODEL_DEBUG", "True").lower() in ('true', '1', 't')

# 模型服务URL
MODEL_API_URL = os.environ.get("MODEL_API_URL", f"http://localhost:{MODEL_PORT}")

# 数据库配置
MONGODB_URI = os.environ.get(
    "MONGODB_URI",
    "mongodb://127.0.0.1:27017/traffic_analysis",
)
DB_NAME = os.environ.get("DB_NAME", "traffic_analysis")

# MongoDB配置
MONGODB_CONFIG = {
    "uri": MONGODB_URI,
    "database": DB_NAME,
    "collections": {
        "analysis_history": "analysis_history",
        "users": "users"
    }
}

# JWT配置
JWT_SECRET_KEY = os.environ.get("JWT_SECRET_KEY", "changeme-use-a-long-random-secret-key")
JWT_ACCESS_TOKEN_EXPIRES = int(os.environ.get("JWT_ACCESS_TOKEN_EXPIRES", 3600))  # 1小时

# 文件存储配置
UPLOAD_FOLDER = os.environ.get("UPLOAD_FOLDER", "static/uploads")
MAX_CONTENT_LENGTH = int(os.environ.get("MAX_CONTENT_LENGTH", 50 * 1024 * 1024))  # 50MB

# 缓存配置
CACHE_TYPE = os.environ.get("CACHE_TYPE", "simple")
CACHE_DEFAULT_TIMEOUT = int(os.environ.get("CACHE_DEFAULT_TIMEOUT", 300))  # 5分钟

# 日志配置
def setup_logger(name, level=LOG_LEVEL):
    """设置日志配置"""
    logger = logging.getLogger(name)
    handler = logging.StreamHandler()
    formatter = logging.Formatter(LOG_FORMAT)
    handler.setFormatter(formatter)
    logger.addHandler(handler)
    logger.setLevel(getattr(logging, level))
    return logger

# 前端配置
FRONTEND_URLS = [
    "http://localhost:3000",  # 标准React开发服务器
    "http://127.0.0.1:3000",
    "http://localhost:5173",  # Vite开发服务器
    "http://localhost:8080",  # 常用前端端口
    "http://localhost:5000",  # Flask默认端口
    "http://localhost",       # 通用本地主机
    "file://"                 # 本地文件协议（用于直接打开HTML文件）
]

# CORS配置
CORS_ORIGINS = FRONTEND_URLS
CORS_SUPPORTS_CREDENTIALS = True
CORS_ALLOW_HEADERS = ["*"]
CORS_EXPOSE_HEADERS = ["*"]

# 项目根目录
ROOT_DIR = Path(os.path.dirname(os.path.abspath(__file__)))

# 模型配置
MODEL_CONFIG = {
    "model_path": str(ROOT_DIR / "yolov12x.pt"),  # YOLOv12x模型路径
    "conf_threshold": 0.35,  # 置信度阈值
    "iou_threshold": 0.45,   # IoU阈值
    # "device": None,          # 自动选择设备 (None=自动选择GPU/CPU)
    "class_names": {         # 类别ID映射
        0: "人",
        1: "自行车",
        2: "汽车",
        3: "摩托车",
        5: "公交车",
        7: "卡车",
        9: "交通灯"
    },
    "vehicle_classes": [2, 3, 5, 7]  # 车辆类别ID
}

# 视频配置
VIDEO_CONFIG = {
    "video_dir": str(ROOT_DIR / "videos"),
    "horizontal_video": str(ROOT_DIR / "videos" / "horizontal.mp4"),
    "vertical_video": str(ROOT_DIR / "videos" / "vertical.mp4"),
    "frame_skip": 3,         # 帧跳过数（每隔多少帧处理一次）
    "resize_width": 640,     # 调整宽度
    "resize_height": 480     # 调整高度
}

# 输出配置
OUTPUT_CONFIG = {
    "results_dir": str(ROOT_DIR / "结果"),
    "log_file": str(ROOT_DIR / "交通分析系统.log"),
    "update_interval": 1,    # 可视化更新间隔（秒）
    "html_output": str(ROOT_DIR / "结果" / "实时交通分析.html"),
    "json_output": str(ROOT_DIR / "结果" / "traffic_data.json")
}

# 系统配置
SYSTEM_CONFIG = {
    "max_error_count": 10,   # 最大允许错误次数
    "cache_limit": 100,      # 检测结果缓存限制
    "log_level": "INFO"      # 日志级别 (DEBUG, INFO, WARNING, ERROR, CRITICAL)
}

# 交通分析配置
TRAFFIC_CONFIG = {
    "crowd_levels": {
        "不拥挤": (0, 5),       # 0-5辆车
        "一般": (5, 10),       # 5-10辆车
        "较拥挤": (10, 20),     # 10-20辆车
        "拥挤": (20, float('inf'))  # 20+辆车
    },
    "solutions": {
        "方案一": "正常红绿灯交换",
        "方案二": "延长横向绿灯时间",
        "方案三": "延长纵向绿灯时间",
        "方案四": "发出提醒（需人为干预）",
        "方案五": "数据异常，需要手动检查"
    }
}

# 可视化配置
VIZ_CONFIG = {
    "plot_height": 900,      # 图表高度
    "plot_width": 1200,      # 图表宽度
    "theme": "plotly_dark",  # 主题
    "h_color": "firebrick",  # 横向颜色
    "v_color": "royalblue",  # 纵向颜色
    "window_size": 10        # 移动平均窗口大小
} 