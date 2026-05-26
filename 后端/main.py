#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
交通分析系统 - 使用YOLOv12模型
基于实时视频分析进行交通流量监控和信号灯管理
"""

import os
import time
import json
import logging
import traceback
import numpy as np
import pandas as pd
import cv2
from datetime import datetime
from pathlib import Path
from threading import Lock
import torch
from ultralytics import YOLO
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import plotly.express as px

# 导入配置
from config import (
    MODEL_CONFIG, 
    VIDEO_CONFIG, 
    OUTPUT_CONFIG, 
    SYSTEM_CONFIG,
    TRAFFIC_CONFIG,
    VIZ_CONFIG
)

# 创建必要的目录结构
def create_directories():
    """创建必要的目录结构"""
    # 创建结果目录
    results_dir = Path(OUTPUT_CONFIG["results_dir"])
    results_dir.mkdir(exist_ok=True)
    
    # 创建视频目录
    video_dir = Path(VIDEO_CONFIG["video_dir"])
    video_dir.mkdir(exist_ok=True)
    
    # 记录创建的目录
    logger.info(f"创建目录: {results_dir}")
    logger.info(f"创建目录: {video_dir}")

# 配置日志
log_file = OUTPUT_CONFIG["log_file"]
logging.basicConfig(
    level=getattr(logging, SYSTEM_CONFIG["log_level"]),
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(log_file),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("交通分析系统")

# 用于存储统计数据
stats_data = []
update_interval = OUTPUT_CONFIG["update_interval"]
visualization_active = True
error_count = 0
MAX_ERROR_COUNT = SYSTEM_CONFIG["max_error_count"]

def safe_execute(func_name=None):
    """装饰器: 安全执行函数并记录错误"""
    def decorator(func):
        def wrapper(*args, **kwargs):
            global error_count
            try:
                return func(*args, **kwargs)
            except Exception as e:
                error_count += 1
                func_n = func_name if func_name else func.__name__
                logger.error(f"{func_n}执行失败: {str(e)}")
                logger.debug(f"错误详情: {traceback.format_exc()}")
                if error_count >= MAX_ERROR_COUNT:
                    logger.critical(f"错误次数超过{MAX_ERROR_COUNT}次，程序将退出")
                    exit(1)
                # 返回安全的默认值
                if func_n == "process_video_frame":
                    return 0, args[0]  # 返回0车辆和原始帧
                return None
        return wrapper
    
    # 允许直接使用@safe_execute或@safe_execute("名称")
    if callable(func_name):
        fn = func_name
        func_name = fn.__name__
        return decorator(fn)
    return decorator

# =============== YOLO检测器类 ===============
class YOLODetector:
    """YOLO检测器类：用于车辆检测"""
    def __init__(self, model_path=None, device=None, conf_threshold=None, iou_threshold=None):
        """初始化YOLO检测器"""
        try:
            # 使用配置或默认值
            self.model_path = model_path or MODEL_CONFIG["model_path"]
            self.conf_threshold = conf_threshold or MODEL_CONFIG["conf_threshold"]
            self.iou_threshold = iou_threshold or MODEL_CONFIG["iou_threshold"]
            
            # 设置计算设备
            if device is None:
                device = MODEL_CONFIG["device"]
            
            if device is None:
                self.device = 'cuda' if torch.cuda.is_available() else 'cpu'
            else:
                self.device = device
            
            logger.info(f"初始化YOLO检测器，使用设备: {self.device}")
            
            # 加载YOLO模型
            if not os.path.exists(self.model_path):
                logger.error(f"YOLO模型文件不存在: {self.model_path}")
                raise FileNotFoundError(f"YOLO模型文件不存在: {self.model_path}")
            
            start_time = time.time()
            self.model = YOLO(self.model_path)
            load_time = time.time() - start_time
            
            logger.info(f"已加载YOLOv12模型: {self.model_path}, 用时: {load_time:.2f}秒")
            
            # 车辆类别ID
            self.vehicle_classes = MODEL_CONFIG["vehicle_classes"]
            
            # 添加锁以确保线程安全
            self.model_lock = Lock()
            
            # 缓存最近的检测结果，避免重复计算
            self.detection_cache = {}
            self.cache_limit = SYSTEM_CONFIG["cache_limit"]
            
        except Exception as e:
            logger.error(f"初始化YOLO检测器时出错: {e}")
            logger.debug(traceback.format_exc())
            raise
    
    @safe_execute
    def detect_vehicles(self, frame):
        """检测图像中的车辆"""
        with self.model_lock:
            # 计算帧的哈希值作为缓存键
            frame_hash = hash(frame.tobytes())
            
            # 检查缓存
            if frame_hash in self.detection_cache:
                return self.detection_cache[frame_hash]
            
            # 记录检测开始时间
            start_time = time.time()
            
            # 执行检测 - 使用YOLOv12的优化参数
            results = self.model(frame, verbose=False, conf=self.conf_threshold, 
                                iou=self.iou_threshold, augment=False)
            
            # 记录检测耗时
            detect_time = time.time() - start_time
            
            # 提取车辆检测结果
            vehicles = []
            vehicle_count = 0
            
            # 处理检测结果
            if results and len(results) > 0:
                # 获取第一张图像的检测结果
                result = results[0]
                
                # 获取边界框和类别
                if hasattr(result, 'boxes') and result.boxes is not None:
                    for box in result.boxes:
                        cls_id = int(box.cls.item())
                        conf = float(box.conf.item())
                        xyxy = box.xyxy.cpu().numpy()[0]
                        
                        # 如果是车辆类别
                        if cls_id in self.vehicle_classes:
                            vehicle_count += 1
                            
                            # 获取类别名称（优先使用配置中的中文名）
                            class_name = MODEL_CONFIG["class_names"].get(cls_id, result.names[cls_id])
                            
                            vehicles.append({
                                'bbox': xyxy.tolist(),
                                'class_id': cls_id,
                                'confidence': conf,
                                'class_name': class_name
                            })
            
            # 创建结果
            detection_result = {
                'vehicle_count': vehicle_count,
                'vehicles': vehicles,
                'detect_time': detect_time,
                'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S.%f')
            }
            
            # 更新缓存
            if len(self.detection_cache) >= self.cache_limit:
                # 清除最早的缓存项
                oldest_key = next(iter(self.detection_cache))
                del self.detection_cache[oldest_key]
            
            self.detection_cache[frame_hash] = detection_result
            
            return detection_result
    
    @safe_execute
    def draw_detections(self, frame, detections):
        """在图像上绘制检测结果"""
        # 创建图像副本
        img = frame.copy()
        
        # 获取车辆检测结果
        vehicles = detections.get('vehicles', [])
        vehicle_count = detections.get('vehicle_count', 0)
        detect_time = detections.get('detect_time', 0)
        
        # 颜色映射 (基于类别ID) - 使用更明亮的颜色
        color_map = {
            2: (50, 225, 50),    # 汽车 (亮绿色)
            3: (50, 50, 225),    # 摩托车 (亮蓝色)
            5: (225, 50, 50),    # 公交车 (亮红色)
            7: (225, 225, 50)    # 卡车 (亮青色)
        }
        
        # 在图像上绘制检测结果
        for vehicle in vehicles:
            bbox = vehicle.get('bbox')
            cls_id = vehicle.get('class_id')
            cls_name = vehicle.get('class_name')
            conf = vehicle.get('confidence')
            
            if bbox and cls_id is not None:
                # 获取边界框坐标
                x1, y1, x2, y2 = [int(coord) for coord in bbox]
                
                # 获取颜色
                color = color_map.get(cls_id, (255, 255, 0))
                
                # 绘制边界框和填充半透明区域
                cv2.rectangle(img, (x1, y1), (x2, y2), color, 2)
                
                # 创建标签背景
                text = f"{cls_name}: {conf:.2f}"
                text_size, _ = cv2.getTextSize(text, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 2)
                text_w, text_h = text_size
                cv2.rectangle(img, (x1, y1 - text_h - 8), (x1 + text_w + 5, y1), color, -1)
                
                # 绘制类别和置信度（白色文本以增加可读性）
                cv2.putText(img, text, (x1, y1 - 5), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 2)
        
        # 添加半透明信息面板
        info_panel_height = 60
        panel = img[:info_panel_height].copy()
        cv2.rectangle(panel, (0, 0), (img.shape[1], info_panel_height), (0, 0, 0), -1)
        alpha = 0.7
        img[:info_panel_height] = cv2.addWeighted(img[:info_panel_height], 1-alpha, panel, alpha, 0)
        
        # 在图像右上角显示车辆总数
        cv2.putText(img, f"车辆数量: {vehicle_count}", (img.shape[1] - 180, 25), 
                    cv2.FONT_HERSHEY_SIMPLEX, 0.7, (255, 255, 255), 2)
        
        # 在图像左上角显示时间戳和检测速度
        cv2.putText(img, f"检测时间: {detect_time*1000:.1f}ms", (10, 25), 
                    cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 2)
        
        # 添加时间戳
        cv2.putText(img, detections.get('timestamp', '')[:19], (10, 50), 
                    cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 200, 200), 1)
        
        return img
    
    @safe_execute
    def process_video_frame(self, frame, draw=True):
        """处理视频帧并检测车辆"""
        # 调整帧大小
        frame = cv2.resize(frame, (VIDEO_CONFIG["resize_width"], VIDEO_CONFIG["resize_height"]))
        
        # 检测车辆
        detections = self.detect_vehicles(frame)
        
        # 绘制检测结果 (如果需要)
        annotated_frame = None
        if draw and detections:
            annotated_frame = self.draw_detections(frame, detections)
        
        return detections, annotated_frame

@safe_execute
def classify_crowd_level(vehicle_count):
    """根据车辆数量分类拥挤等级"""
    try:
        count = float(vehicle_count)  # 确保可以转换为浮点数
        
        # 使用配置中的拥挤级别范围
        for level, (min_val, max_val) in TRAFFIC_CONFIG["crowd_levels"].items():
            if min_val <= count < max_val:
                return level
        
        return "未知"  # 如果没有匹配的级别
    except (ValueError, TypeError) as e:
        logger.error(f"车流量分类错误: {str(e)}, 输入值: {vehicle_count}")
        return "未知"

@safe_execute
def determine_traffic_solution(horizontal_crowd, vertical_crowd):
    """根据横向和纵向的拥挤等级确定交通方案"""
    valid_levels = list(TRAFFIC_CONFIG["crowd_levels"].keys())
    
    # 验证输入
    if horizontal_crowd not in valid_levels or vertical_crowd not in valid_levels:
        logger.warning(f"无效的拥挤等级输入: 横向={horizontal_crowd}, 纵向={vertical_crowd}")
        return "方案五：数据异常，需要手动检查"
    
    # 确定方案
    if (horizontal_crowd == "不拥挤" or horizontal_crowd == "一般") and (vertical_crowd == "不拥挤" or vertical_crowd == "一般"):
        return "方案一：正常红绿灯交换"
    elif (horizontal_crowd in ["拥挤", "较拥挤"]) and (vertical_crowd in ["不拥挤", "一般"]):
        return "方案二：延长横向绿灯时间"
    elif (horizontal_crowd in ["不拥挤", "一般"]) and (vertical_crowd in ["拥挤", "较拥挤"]):
        return "方案三：延长纵向绿灯时间"
    elif (horizontal_crowd in ["拥挤", "较拥挤"]) and (vertical_crowd in ["拥挤", "较拥挤"]):
        return "方案四：发出提醒（需人为干预）"
    else:
        return "方案五：数据异常，需要手动检查"

@safe_execute
def update_visualization():
    """更新并显示可视化数据"""
    global stats_data
    
    while visualization_active:
        if len(stats_data) < 5:  # 需要至少5个数据点来进行有意义的可视化
            logger.debug("数据点不足，等待更多数据...")
            time.sleep(1)
            continue
            
        try:
            logger.info(f"正在更新可视化，处理{len(stats_data)}个数据点...")
            
            # 将数据转换为DataFrame并添加时间索引
            df = pd.DataFrame(stats_data)
            if 'timestamp' in df.columns:
                df['datetime'] = pd.to_datetime(df['timestamp'])
                df.set_index('datetime', inplace=True)
            
            # 计算统计信息
            total_h = sum(entry['horizontal_count'] for entry in stats_data)
            total_v = sum(entry['vertical_count'] for entry in stats_data)
            avg_h = total_h / len(stats_data) if stats_data else 0
            avg_v = total_v / len(stats_data) if stats_data else 0
            max_h = max(entry['horizontal_count'] for entry in stats_data)
            max_v = max(entry['vertical_count'] for entry in stats_data)
            
            # 创建主图形对象
            fig = make_subplots(
                rows=3, cols=2,
                subplot_titles=(
                    '横纵向车辆总数对比', '拥挤等级分布', 
                    '实时车辆数量变化趋势', '交通方案分布',
                    '车流量热力图', '车流量直方图'
                ),
                specs=[
                    [{'type': 'bar'}, {'type': 'pie'}],
                    [{'type': 'scatter'}, {'type': 'pie'}],
                    [{'type': 'heatmap'}, {'type': 'histogram'}]
                ],
                vertical_spacing=0.1,
                horizontal_spacing=0.1
            )
            
            # 1. 横纵向车辆总数对比
            fig.add_trace(
                go.Bar(
                    x=['横向车辆', '纵向车辆'], 
                    y=[total_h, total_v],
                    text=[f"总数: {total_h}<br>平均: {avg_h:.1f}<br>最大: {max_h}", 
                          f"总数: {total_v}<br>平均: {avg_v:.1f}<br>最大: {max_v}"],
                    textposition='auto',
                    marker_color=['rgba(246, 78, 139, 0.6)', 'rgba(58, 71, 80, 0.6)']
                ),
                row=1, col=1
            )
            
            # 2. 拥挤等级分布
            crowd_counts = {}
            for crowd in df['horizontal_crowd']:
                if crowd not in crowd_counts:
                    crowd_counts[crowd] = 0
                crowd_counts[crowd] += 1
            for crowd in df['vertical_crowd']:
                if crowd not in crowd_counts:
                    crowd_counts[crowd] = 0
                crowd_counts[crowd] += 1
            
            fig.add_trace(
                go.Pie(
                    labels=list(crowd_counts.keys()), 
                    values=list(crowd_counts.values()), 
                    hole=.3,
                    textinfo='label+percent',
                    hoverinfo='label+value+percent',
                    marker=dict(colors=px.colors.sequential.Inferno)
                ),
                row=1, col=2
            )
            
            # 3. 实时车辆数量变化趋势
            timestamps = list(range(1, len(df) + 1))
            
            # 计算移动平均
            window_size = min(VIZ_CONFIG["window_size"], len(df))
            h_moving_avg = df['horizontal_count'].rolling(window=window_size).mean()
            v_moving_avg = df['vertical_count'].rolling(window=window_size).mean()
            
            fig.add_trace(
                go.Scatter(
                    x=timestamps, 
                    y=df['horizontal_count'], 
                    mode='lines+markers', 
                    name='横向车辆',
                    line=dict(color=VIZ_CONFIG["h_color"], width=1),
                    marker=dict(size=3)
                ),
                row=2, col=1
            )
            
            fig.add_trace(
                go.Scatter(
                    x=timestamps, 
                    y=h_moving_avg,
                    mode='lines', 
                    name='横向移动平均',
                    line=dict(color=VIZ_CONFIG["h_color"], width=2, dash='dash')
                ),
                row=2, col=1
            )
            
            fig.add_trace(
                go.Scatter(
                    x=timestamps, 
                    y=df['vertical_count'], 
                    mode='lines+markers', 
                    name='纵向车辆',
                    line=dict(color=VIZ_CONFIG["v_color"], width=1),
                    marker=dict(size=3)
                ),
                row=2, col=1
            )
            
            fig.add_trace(
                go.Scatter(
                    x=timestamps, 
                    y=v_moving_avg,
                    mode='lines', 
                    name='纵向移动平均',
                    line=dict(color=VIZ_CONFIG["v_color"], width=2, dash='dash')
                ),
                row=2, col=1
            )
            
            # 4. 交通方案分布
            solution_counts = {}
            for solution in df['solution']:
                if solution not in solution_counts:
                    solution_counts[solution] = 0
                solution_counts[solution] += 1
            
            fig.add_trace(
                go.Pie(
                    labels=list(solution_counts.keys()), 
                    values=list(solution_counts.values()), 
                    textinfo='label+percent',
                    marker=dict(colors=px.colors.sequential.Viridis)
                ),
                row=2, col=2
            )
            
            # 5. 车流量热力图
            fig.add_trace(
                go.Heatmap(
                    z=[df['horizontal_count'].values, df['vertical_count'].values],
                    y=['横向', '纵向'],
                    x=timestamps,
                    colorscale='Hot',
                    showscale=True
                ),
                row=3, col=1
            )
            
            # 6. 车流量直方图
            fig.add_trace(
                go.Histogram(
                    x=df['horizontal_count'].tolist() + df['vertical_count'].tolist(),
                    nbinsx=20,
                    marker_color='rgba(0, 128, 128, 0.7)',
                    name='车流量分布'
                ),
                row=3, col=2
            )
            
            # 更新布局
            fig.update_layout(
                title_text='实时交通分析系统 - YOLOv12模型',
                height=VIZ_CONFIG["plot_height"],
                width=VIZ_CONFIG["plot_width"],
                showlegend=False,
                template=VIZ_CONFIG["theme"]
            )
            
            # 保存为HTML文件
            html_path = OUTPUT_CONFIG["html_output"]
            fig.write_html(str(html_path))
            
            # 同时保存数据为JSON
            with open(OUTPUT_CONFIG["json_output"], 'w', encoding='utf-8') as f:
                json.dump(stats_data, f, ensure_ascii=False, indent=2)
            
            logger.info(f"可视化已更新并保存至: {html_path}")
            
            # 打印当前状态
            current_time = datetime.now().strftime('%H:%M:%S')
            h_avg = avg_h
            v_avg = avg_v
            h_crowd = classify_crowd_level(h_avg)
            v_crowd = classify_crowd_level(v_avg)
            current_solution = determine_traffic_solution(h_crowd, v_crowd)
            
            logger.info(f"实时统计（{current_time}）:")
            logger.info(f"横向平均车流量: {h_avg:.2f} - {h_crowd}")
            logger.info(f"纵向平均车流量: {v_avg:.2f} - {v_crowd}")
            logger.info(f"当前建议方案: {current_solution}")
            
            # 等待更新间隔
            time.sleep(update_interval)
            
        except Exception as e:
            logger.error(f"更新可视化时出错: {e}")
            logger.debug(traceback.format_exc())
            time.sleep(2)  # 发生错误时稍微延长等待时间

def process_video_stream(video_path, detector, is_horizontal=True):
    """处理视频流"""
    global stats_data
    
    try:
        # 打开视频文件或摄像头
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            logger.error(f"无法打开视频: {video_path}")
            return
        
        logger.info(f"开始处理视频: {video_path}, 是横向: {is_horizontal}")
        
        # 读取并处理每一帧
        frame_count = 0
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            # 每N帧处理一次（减少计算量）
            if frame_count % VIDEO_CONFIG["frame_skip"] == 0:
                # 检测车辆
                detections, annotated_frame = detector.process_video_frame(frame)
                
                if detections:
                    # 获取车辆数量
                    vehicle_count = detections.get('vehicle_count', 0)
                    
                    # 确定拥挤等级
                    crowd_level = classify_crowd_level(vehicle_count)
                    
                    # 根据方向存储统计数据
                    entry = {
                        'timestamp': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                        'horizontal_count': vehicle_count if is_horizontal else 0,
                        'vertical_count': 0 if is_horizontal else vehicle_count,
                        'horizontal_crowd': crowd_level if is_horizontal else "未检测",
                        'vertical_crowd': "未检测" if is_horizontal else crowd_level,
                        'solution': '处理中...'
                    }
                    
                    # 获取最新的交通方案
                    if len(stats_data) > 0:
                        # 合并横向和纵向的数据
                        last_entry = stats_data[-1].copy()
                        if is_horizontal:
                            entry['vertical_count'] = last_entry.get('vertical_count', 0)
                            entry['vertical_crowd'] = last_entry.get('vertical_crowd', "未检测")
                        else:
                            entry['horizontal_count'] = last_entry.get('horizontal_count', 0)
                            entry['horizontal_crowd'] = last_entry.get('horizontal_crowd', "未检测")
                    
                    # 确定交通方案
                    entry['solution'] = determine_traffic_solution(
                        entry['horizontal_crowd'], 
                        entry['vertical_crowd']
                    )
                    
                    stats_data.append(entry)
                    
                    # 显示处理结果
                    if annotated_frame is not None:
                        cv2.imshow('交通分析系统 - YOLOv12', annotated_frame)
                        
                        # 按ESC键退出
                        if cv2.waitKey(1) == 27:
                            break
            
            frame_count += 1
        
        # 释放资源
        cap.release()
        logger.info(f"视频处理完成: {video_path}")
        
    except Exception as e:
        logger.error(f"处理视频流时出错: {e}")
        logger.debug(traceback.format_exc())

def main():
    """主函数"""
    try:
        # 创建必要的目录
        create_directories()
        
        # 加载YOLOv12模型
        model_path = MODEL_CONFIG["model_path"]
        logger.info(f"正在加载YOLOv12模型: {model_path}...")
        
        # 初始化检测器
        detector = YOLODetector()
        logger.info("YOLOv12模型加载成功!")
        
        # 启动可视化更新线程
        import threading
        vis_thread = threading.Thread(target=update_visualization)
        vis_thread.daemon = True
        vis_thread.start()
        
        # 获取视频路径
        horizontal_video = VIDEO_CONFIG["horizontal_video"]
        vertical_video = VIDEO_CONFIG["vertical_video"]
        
        # 交替处理横向和纵向视频
        while True:
            if os.path.exists(horizontal_video):
                process_video_stream(horizontal_video, detector, is_horizontal=True)
            else:
                logger.warning(f"横向视频不存在: {horizontal_video}")
            
            if os.path.exists(vertical_video):
                process_video_stream(vertical_video, detector, is_horizontal=False)
            else:
                logger.warning(f"纵向视频不存在: {vertical_video}")
            
            # 如果两个视频都不存在，使用摄像头
            if not os.path.exists(horizontal_video) and not os.path.exists(vertical_video):
                logger.info("未找到视频文件，尝试使用摄像头...")
                process_video_stream(0, detector, is_horizontal=True)
            
            # 等待一段时间再重新处理
            time.sleep(2)
        
    except KeyboardInterrupt:
        logger.info("用户中断，程序退出")
    except Exception as e:
        logger.critical(f"主程序出错: {e}")
        logger.debug(traceback.format_exc())
    finally:
        # 清理资源
        cv2.destroyAllWindows()
        visualization_active = False
        logger.info("程序已退出")

if __name__ == "__main__":
    main() 