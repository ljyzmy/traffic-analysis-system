#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
YOLOv12x模型API服务
提供REST API用于图片和视频分析
"""

import os
import time
import base64
import json
import logging
import traceback
import numpy as np
import cv2
from datetime import datetime
from pathlib import Path
from io import BytesIO
from flask import Flask, request, jsonify, send_file
from flask_cors import CORS
from ultralytics import YOLO
from bson import ObjectId
from pymongo import MongoClient
from gridfs import GridFS
import tempfile
import uuid
import subprocess
import shutil

# 导入配置
from config import MODEL_CONFIG, VIDEO_CONFIG, MONGODB_CONFIG
from db_config import DatabaseConfig

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)
logger = logging.getLogger("模型API服务")

# 创建Flask应用
app = Flask(__name__)
CORS(app)  # 允许跨域请求

# 设置最大请求体积为500MB (视频文件较大)
app.config['MAX_CONTENT_LENGTH'] = 500 * 1024 * 1024

# 初始化数据库
db_config = DatabaseConfig()
fs = db_config.fs  # GridFS引用

# 连接MongoDB
def get_mongodb_client():
    try:
        client = MongoClient(MONGODB_CONFIG["uri"])
        return client
    except Exception as e:
        logger.error(f"MongoDB连接失败: {str(e)}")
        return None

# 获取MongoDB客户端
mongodb_client = get_mongodb_client()

# 加载YOLOv11x模型
def load_model():
    try:
        model_path = MODEL_CONFIG["model_path"]
        logger.info(f"正在加载YOLOv11x模型: {model_path}")
        
        if not os.path.exists(model_path):
            logger.error(f"模型文件不存在: {model_path}")
            return None
        
        model = YOLO(model_path)
        logger.info(f"YOLOv11x模型加载成功")
        return model
    except Exception as e:
        logger.error(f"模型加载失败: {str(e)}")
        logger.error(traceback.format_exc())
        return None

# 全局模型变量
model = load_model()

# 视频分析函数
def analyze_video_file(video_path, task_id=None, save_video=True):
    """
    分析视频文件并生成带有检测框的结果视频
    
    Args:
        video_path: 视频文件路径
        task_id: 任务ID
        save_video: 是否保存结果视频
        
    Returns:
        dict: 包含分析结果的字典
    """
    if model is None:
        logger.error("模型未加载，无法分析视频")
        return {"status": "error", "message": "模型未加载"}
    
    try:
        logger.info(f"开始分析视频: {video_path}")
        start_time = time.time()
        
        # 打开视频文件
        cap = cv2.VideoCapture(video_path)
        if not cap.isOpened():
            logger.error(f"无法打开视频文件: {video_path}")
            return {"status": "error", "message": "无法打开视频文件"}
        
        # 获取视频属性
        fps = cap.get(cv2.CAP_PROP_FPS)
        width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        duration = total_frames / fps if fps > 0 else 0
        
        logger.info(f"视频属性: fps={fps}, width={width}, height={height}, frames={total_frames}, duration={duration:.2f}秒")
        
        # 创建临时输出视频文件
        output_path = None
        video_writer = None
        
        if save_video:
            temp_dir = tempfile.gettempdir()
            file_id = task_id or str(uuid.uuid4())[:8]
            
            # 直接创建MP4文件，尝试多种浏览器兼容编码器
            output_path = os.path.join(temp_dir, f"analyzed_video_{file_id}.mp4")
            
            # 尝试各种常见的编码器，按浏览器兼容性排序
            encoders = [
                ('avc1', 'AVC1/H.264编码器'),  # 常用H.264实现
                ('H264', 'H.264编码器'),       # 直接H.264编码器
                ('X264', 'X264编码器'),        # 另一种H.264实现
                ('mp4v', 'MP4V编码器'),        # MP4V编码器，大多数平台支持
                ('XVID', 'XVID编码器'),        # 备选编码器
                ('DIVX', 'DIVX编码器'),        # 备选编码器
            ]
            
            # 尝试所有编码器直到找到一个可用的
            video_writer = None
            for code, name in encoders:
                try:
                    logger.info(f"尝试使用{name}({code})创建视频")
                    fourcc = cv2.VideoWriter_fourcc(*code)
                    temp_writer = cv2.VideoWriter(output_path, fourcc, fps, (width, height))
                    
                    if temp_writer.isOpened():
                        logger.info(f"成功使用{name}({code})编码器")
                        video_writer = temp_writer
                        break
                    else:
                        logger.warning(f"{name}({code})编码器不可用")
                        # 确保关闭失败的writer
                        temp_writer.release()
                except Exception as e:
                    logger.warning(f"尝试{name}({code})编码器时出错: {str(e)}")
            
            # 如果所有编码器都失败，尝试使用系统默认编码器
            if video_writer is None or not video_writer.isOpened():
                logger.warning("所有指定编码器都失败，尝试使用系统默认编码器")
                # 使用-1表示系统默认编码器
                try:
                    video_writer = cv2.VideoWriter(output_path, -1, fps, (width, height))
                    if video_writer.isOpened():
                        logger.info("成功使用系统默认编码器")
                    else:
                        logger.error("所有编码器都失败，无法创建视频")
                        save_video = False
                except Exception as e:
                    logger.error(f"使用系统默认编码器时出错: {str(e)}")
                    save_video = False
            
            if video_writer and video_writer.isOpened():
                logger.info(f"创建输出视频: {output_path}")
            else:
                logger.error("无法创建视频写入器，放弃视频保存")
                save_video = False
        
        # 初始化统计信息
        frame_count = 0
        vehicle_counts = []
        vehicle_types = {}
        all_detections = []
        
        # 处理进度报告频率
        progress_interval = max(1, total_frames // 20)  # 每5%报告一次
        
        # 开始逐帧处理
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            # 增加帧计数
            frame_count += 1
            
            # 计算进度
            progress = (frame_count / total_frames) * 100 if total_frames > 0 else 0
            
            # 定期输出进度
            if frame_count % progress_interval == 0:
                logger.info(f"视频处理进度: {progress:.1f}% ({frame_count}/{total_frames})")
            
            # 使用模型进行检测
            results = model(frame, conf=MODEL_CONFIG["conf_threshold"], iou=MODEL_CONFIG["iou_threshold"])
            
            # 提取当前帧的检测结果
            frame_detections = []
            frame_vehicle_count = 0
            
            if results and len(results) > 0:
                result = results[0]
                
                # 获取检测框和类别
                if hasattr(result, 'boxes') and result.boxes is not None:
                    for box in result.boxes:
                        cls_id = int(box.cls.item())
                        confidence = float(box.conf.item())
                        xyxy = box.xyxy.cpu().numpy()[0]
                        
                        # 获取类别名称
                        class_name = MODEL_CONFIG["class_names"].get(cls_id, result.names[cls_id])
                        
                        # 计算车辆数量
                        is_vehicle = False
                        if cls_id in MODEL_CONFIG["vehicle_classes"]:
                            frame_vehicle_count += 1
                            is_vehicle = True
                            
                            # 更新车辆类型统计
                            if class_name in vehicle_types:
                                vehicle_types[class_name] += 1
                            else:
                                vehicle_types[class_name] = 1
                        
                        # 添加到检测结果
                        detection = {
                            "frame": frame_count,
                            "class_id": cls_id,
                            "class_name": class_name,
                            "confidence": confidence,
                            "bbox": xyxy.tolist(),
                            "is_vehicle": is_vehicle
                        }
                        
                        frame_detections.append(detection)
                        all_detections.append(detection)
            
            # 存储当前帧车辆数量
            vehicle_counts.append(frame_vehicle_count)
            
            # 如果需要保存视频
            if save_video and video_writer is not None:
                # 获取带有检测框的帧
                result_frame = results[0].plot() if results and len(results) > 0 else frame
                
                # 添加帧号和统计信息
                timestamp = frame_count / fps
                cv2.putText(result_frame, f"Frame: {frame_count}/{total_frames} Time: {timestamp:.2f}s", 
                           (10, 30), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
                cv2.putText(result_frame, f"Vehicles: {frame_vehicle_count}", 
                           (10, 60), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 255, 0), 2)
                
                # 写入视频帧
                video_writer.write(result_frame)
        
        # 释放资源
        cap.release()
        
        # 处理视频文件
        result_file_id = None
        if save_video and video_writer is not None:
            # 确保正确关闭视频写入器
            video_writer.release()
            logger.info(f"视频编辑完成，已保存到: {output_path}")
            
            # 检查输出文件是否有效
            if os.path.exists(output_path) and os.path.getsize(output_path) > 100000:  # 至少100KB
                logger.info(f"视频文件生成成功: {output_path}, 大小: {os.path.getsize(output_path)/1024:.2f}KB")
                
                # 验证生成的视频文件是否可以打开
                try:
                    test_cap = cv2.VideoCapture(output_path)
                    if test_cap.isOpened():
                        # 获取视频的编解码器信息
                        fourcc_int = int(test_cap.get(cv2.CAP_PROP_FOURCC))
                        fourcc_str = "".join([chr((fourcc_int >> 8 * i) & 0xFF) for i in range(4)])
                        logger.info(f"视频编码器: {fourcc_str}")
                        
                        test_cap.release()
                        # 文件有效，准备保存到GridFS
                        with open(output_path, 'rb') as f:
                            file_data = f.read()
                            # 再次检查数据大小
                            if len(file_data) > 0:
                                file_size = os.path.getsize(output_path)
                                result_file_id = fs.put(
                                    file_data,
                                    filename=f"analyzed_video_{file_id}.mp4",
                                    content_type="video/mp4",  # 使用标准MIME类型
                                    file_size=file_size,
                                    codec=fourcc_str,  # 记录编码器信息
                                    task_id=task_id,
                                    analysis_date=datetime.now(),
                                    vehicle_count=max(vehicle_counts) if vehicle_counts else 0,
                                    vehicle_types=json.dumps(vehicle_types),
                                    processing_time=time.time() - start_time,
                                    total_frames=total_frames,
                                    duration=duration
                                )
                                logger.info(f"分析结果视频保存到GridFS: {result_file_id}, 大小: {file_size/1024:.2f}KB")
                            else:
                                logger.error(f"读取到的文件数据为空，不保存到GridFS")
                    else:
                        logger.error(f"无法打开生成的视频文件，不保存到GridFS")
                except Exception as e:
                    logger.error(f"验证视频文件时出错: {str(e)}")
            else:
                logger.error(f"视频文件无效或为空: {output_path}")
                if os.path.exists(output_path):
                    logger.error(f"文件大小: {os.path.getsize(output_path)} 字节")
        
        # 计算处理时间
        processing_time = time.time() - start_time
        
        # 计算总车辆数量 (取每帧检测到的最大值)
        max_vehicles = max(vehicle_counts) if vehicle_counts else 0
        avg_vehicles = sum(vehicle_counts) / len(vehicle_counts) if vehicle_counts else 0
        
        logger.info(f"视频分析完成: 处理时间={processing_time:.2f}秒, 最大车辆数={max_vehicles}, 平均车辆数={avg_vehicles:.2f}")
        
        # 构建结果
        result = {
            "status": "success",
            "task_id": task_id,
            "processing_time": processing_time,
            "total_frames": total_frames,
            "fps": fps,
            "duration": duration,
            "width": width,
            "height": height,
            "vehicle_count": max_vehicles,
            "vehicle_types": vehicle_types,
            "result_file_id": str(result_file_id) if result_file_id else None,
            "output_path": output_path if output_path and os.path.exists(output_path) else None
        }
        
        return result
    
    except Exception as e:
        logger.error(f"视频分析出错: {str(e)}")
        logger.error(traceback.format_exc())
        return {"status": "error", "message": f"视频分析出错: {str(e)}"}

@app.route('/analyze_video', methods=['POST'])
def analyze_video_api():
    """视频分析API"""
    if model is None:
        return jsonify({"status": "error", "message": "模型未加载"}), 500
    
    try:
        # 检查请求
        if 'video' not in request.files:
            return jsonify({"status": "error", "message": "未提供视频文件"}), 400
        
        # 获取视频文件
        video_file = request.files['video']
        
        # 获取任务ID (如果有)
        task_id = request.form.get('task_id', str(uuid.uuid4()))
        
        # 保存视频到临时文件
        temp_video_path = os.path.join(tempfile.gettempdir(), f"temp_video_{task_id}.mp4")
        video_file.save(temp_video_path)
        
        logger.info(f"收到视频分析请求: task_id={task_id}, 视频已保存到 {temp_video_path}")
        
        # 分析视频
        result = analyze_video_file(temp_video_path, task_id)
        
        # 清理临时文件
        try:
            os.remove(temp_video_path)
        except:
            pass
        
        return jsonify(result)
    
    except Exception as e:
        logger.error(f"处理视频分析请求出错: {str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({"status": "error", "message": f"处理视频分析请求出错: {str(e)}"}), 500

@app.route('/analyze_video_from_gridfs', methods=['POST'])
def analyze_video_from_gridfs():
    """从GridFS中获取视频并分析"""
    if model is None:
        return jsonify({"status": "error", "message": "模型未加载"}), 500
    
    try:
        # 检查请求
        data = request.json
        if not data or 'video_id' not in data:
            return jsonify({"status": "error", "message": "未提供视频ID"}), 400
        
        video_id = data['video_id']
        task_id = data.get('task_id', str(uuid.uuid4()))
        
        logger.info(f"收到GridFS视频分析请求: video_id={video_id}, task_id={task_id}")
        
        # 从GridFS获取视频
        try:
            obj_id = ObjectId(video_id)
            if not fs.exists(obj_id):
                return jsonify({"status": "error", "message": f"GridFS中未找到视频: {video_id}"}), 404
            
            # 获取视频文件
            video_file = fs.get(obj_id)
            
            # 保存到临时文件
            temp_video_path = os.path.join(tempfile.gettempdir(), f"gridfs_video_{task_id}.mp4")
            with open(temp_video_path, 'wb') as f:
                f.write(video_file.read())
            
            logger.info(f"已从GridFS获取视频并保存到 {temp_video_path}")
            
            # 分析视频
            result = analyze_video_file(temp_video_path, task_id)
            
            # 清理临时文件
            try:
                os.remove(temp_video_path)
            except:
                pass
            
            return jsonify(result)
            
        except Exception as e:
            logger.error(f"从GridFS获取视频失败: {str(e)}")
            return jsonify({"status": "error", "message": f"从GridFS获取视频失败: {str(e)}"}), 500
        
    except Exception as e:
        logger.error(f"处理GridFS视频分析请求出错: {str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({"status": "error", "message": f"处理GridFS视频分析请求出错: {str(e)}"}), 500

@app.route('/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    if model is not None:
        return jsonify({"status": "ok", "message": "服务正常运行"})
    else:
        return jsonify({"status": "error", "message": "模型加载失败"}), 500

@app.route('/analyze', methods=['POST'])
def analyze_image():
    """分析图片接口"""
    if model is None:
        return jsonify({"status": "error", "message": "模型未加载"}), 500
    
    try:
        # 检查请求中是否包含图片
        if 'image' not in request.files and 'image_base64' not in request.json:
            return jsonify({"status": "error", "message": "未提供图片"}), 400
        
        # 从请求中获取图片
        if 'image' in request.files:
            # 文件上传方式
            image_file = request.files['image']
            image_data = image_file.read()
            nparr = np.frombuffer(image_data, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        else:
            # Base64编码方式
            image_base64 = request.json['image_base64']
            # 去除可能存在的Base64头
            if ',' in image_base64:
                image_base64 = image_base64.split(',')[1]
            image_data = base64.b64decode(image_base64)
            nparr = np.frombuffer(image_data, np.uint8)
            image = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        if image is None:
            return jsonify({"status": "error", "message": "图片解码失败"}), 400
        
        # 调整图像大小
        image = cv2.resize(image, (VIDEO_CONFIG["resize_width"], VIDEO_CONFIG["resize_height"]))
        
        # 记录开始时间
        start_time = time.time()
        
        # 使用模型预测
        results = model(image, conf=MODEL_CONFIG["conf_threshold"], iou=MODEL_CONFIG["iou_threshold"])
        
        # 计算预测时间
        inference_time = time.time() - start_time
        
        # 提取检测结果
        detections = []
        vehicle_count = 0
        
        if results and len(results) > 0:
            result = results[0]
            
            # 获取检测框和类别
            if hasattr(result, 'boxes') and result.boxes is not None:
                for box in result.boxes:
                    cls_id = int(box.cls.item())
                    confidence = float(box.conf.item())
                    xyxy = box.xyxy.cpu().numpy()[0]
                    
                    # 获取类别名称
                    class_name = MODEL_CONFIG["class_names"].get(cls_id, result.names[cls_id])
                    
                    # 输出调试信息
                    logger.info(f"检测到对象: class_id={cls_id}, class_name={class_name}, confidence={confidence:.4f}")
                    
                    # 车辆类别统计 - 使用ID判断
                    if cls_id in MODEL_CONFIG["vehicle_classes"]:
                        vehicle_count += 1
                        logger.info(f"计入车辆: class_id={cls_id}, class_name={class_name}")
                    # 备用判断 - 使用类别名称判断
                    elif class_name.lower() in ["car", "truck", "bus", "motorcycle", "汽车", "卡车", "公交车", "摩托车"]:
                        vehicle_count += 1
                        logger.info(f"通过名称计入车辆: class_id={cls_id}, class_name={class_name}")
                    
                    # 添加到检测结果
                    detections.append({
                        "class_id": cls_id,
                        "class_name": class_name,
                        "confidence": confidence,
                        "bbox": xyxy.tolist()
                    })
        
        # 记录最终计算的车辆数量
        logger.info(f"检测到对象总数: {len(detections)}, 车辆数量: {vehicle_count}")
        
        # 创建结果图像
        result_image = results[0].plot() if results and len(results) > 0 else image
        
        # 将结果图像转换为Base64
        _, buffer = cv2.imencode('.jpg', result_image)
        result_image_base64 = base64.b64encode(buffer).decode('utf-8')
        
        # 保存结果图像到GridFS
        timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
        image_filename = f"result_{timestamp}.jpg"
        
        # 将图像数据保存到GridFS
        file_id = fs.put(
            buffer.tobytes(), 
            filename=image_filename,
            content_type="image/jpeg",
            timestamp=datetime.now(),
            vehicle_count=vehicle_count,
            inference_time=inference_time
        )
        
        logger.info(f"保存结果图像到GridFS, 文件ID: {file_id}")
        
        # 构建图像URL (使用GridFS ID)
        image_url = str(file_id)  # 直接使用GridFS ID作为URL标识
        
        # 构建响应数据
        response_data = {
            "status": "success",
            "vehicleCount": vehicle_count,
            "detections": detections,
            "inferenceTime": inference_time,
            "result_image_base64": result_image_base64,
            "imageUrl": image_url,  # 这里是GridFS ID
            "timestamp": datetime.now().isoformat()
        }
        
        return jsonify(response_data)
    
    except Exception as e:
        logger.error(f"图像分析出错: {str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({"status": "error", "message": f"处理图像时出错: {str(e)}"}), 500

@app.route('/status', methods=['GET'])
def status():
    """状态检查接口"""
    return jsonify({
        "status": "online",
        "model_status": "online" if model is not None else "offline",
        "version": "1.0.0",
        "timestamp": datetime.now().isoformat()
    })

@app.route('/api/history', methods=['GET'])
def get_history():
    """获取历史记录接口"""
    try:
        # 获取查询参数
        limit = request.args.get('limit', default=10, type=int)
        skip = request.args.get('skip', default=0, type=int)
        
        # 获取数据库集合
        db = mongodb_client[MONGODB_CONFIG["database"]]
        collection = db["analysis_history"]
        
        # 查询历史记录
        cursor = collection.find().sort("timestamp", -1).skip(skip).limit(limit)
        
        # 转换结果
        history = []
        for doc in cursor:
            doc['_id'] = str(doc['_id'])  # 转换ObjectId为字符串
            history.append(doc)
        
        return jsonify({
            "status": "success",
            "data": history,
            "total": collection.count_documents({}),
            "limit": limit,
            "skip": skip
        })
        
    except Exception as e:
        logger.error(f"获取历史记录失败: {str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({"status": "error", "message": f"获取历史记录失败: {str(e)}"}), 500

@app.route('/api/history/<history_id>', methods=['DELETE'])
def delete_history(history_id):
    """删除历史记录接口"""
    try:
        # 获取数据库集合
        db = mongodb_client[MONGODB_CONFIG["database"]]
        collection = db["analysis_history"]
        
        # 删除记录
        result = collection.delete_one({"_id": ObjectId(history_id)})
        
        if result.deleted_count > 0:
            return jsonify({"status": "success", "message": "历史记录删除成功"})
        else:
            return jsonify({"status": "error", "message": "历史记录不存在"}), 404
            
    except Exception as e:
        logger.error(f"删除历史记录失败: {str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({"status": "error", "message": f"删除历史记录失败: {str(e)}"}), 500

@app.route('/api/media/image/<image_id>', methods=['GET'])
def get_image(image_id):
    """从GridFS获取图像接口"""
    try:
        # 将字符串ID转换为ObjectId
        obj_id = ObjectId(image_id)
        
        # 检查文件是否存在
        if not fs.exists(obj_id):
            logger.error(f"GridFS中没有找到图像: {image_id}")
            return jsonify({"error": "图像不存在"}), 404
        
        # 从GridFS获取文件
        file_data = fs.get(obj_id)
        
        # 获取文件类型
        content_type = file_data.content_type or "image/jpeg"
        
        # 返回图像数据
        response = app.response_class(
            response=file_data.read(),
            status=200,
            mimetype=content_type
        )
        
        # 添加缓存控制头
        response.headers["Cache-Control"] = "public, max-age=31536000"
        
        return response
        
    except Exception as e:
        logger.error(f"获取图像时出错: {str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({"error": str(e)}), 500

@app.route('/api/media/video/<video_id>', methods=['GET'])
def get_video(video_id):
    """从GridFS获取视频接口"""
    try:
        # 将字符串ID转换为ObjectId
        obj_id = ObjectId(video_id)
        
        # 检查文件是否存在
        if not fs.exists(obj_id):
            logger.error(f"GridFS中没有找到视频: {video_id}")
            return jsonify({"error": "视频不存在"}), 404
        
        # 从GridFS获取文件
        file_data = fs.get(obj_id)
        
        # 获取文件类型
        content_type = file_data.content_type or "video/mp4"
        
        # 返回视频数据
        response = app.response_class(
            response=file_data.read(),
            status=200,
            mimetype=content_type
        )
        
        # 添加缓存控制头
        response.headers["Cache-Control"] = "public, max-age=31536000"
        
        return response
        
    except Exception as e:
        logger.error(f"获取视频时出错: {str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({"error": str(e)}), 500

# 为了测试，添加直接下载分析结果视频的接口
@app.route('/download_analyzed_video/<video_id>', methods=['GET'])
def download_analyzed_video(video_id):
    """下载分析后的视频"""
    try:
        # 将字符串ID转换为ObjectId
        obj_id = ObjectId(video_id)
        
        # 检查文件是否存在
        if not fs.exists(obj_id):
            logger.error(f"GridFS中没有找到视频: {video_id}")
            return jsonify({"error": "视频不存在"}), 404
        
        # 从GridFS获取文件
        file_data = fs.get(obj_id)
        
        # 获取文件名和内容类型
        filename = file_data.filename or f"analyzed_video_{video_id}.mp4"
        content_type = file_data.content_type or "video/mp4"
        
        # 创建临时文件
        temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.mp4')
        temp_file.write(file_data.read())
        temp_file.close()
        
        # 返回视频文件下载
        return send_file(
            temp_file.name,
            mimetype=content_type,
            as_attachment=True,
            download_name=filename
        )
        
    except Exception as e:
        logger.error(f"下载分析视频时出错: {str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({"error": str(e)}), 500

# 添加新的接口，用于将视频转换为MP4格式并下载
@app.route('/convert_to_mp4/<video_id>', methods=['GET'])
def convert_to_mp4(video_id):
    """转换视频为MP4格式并下载"""
    try:
        # 将字符串ID转换为ObjectId
        obj_id = ObjectId(video_id)
        
        # 检查文件是否存在
        if not fs.exists(obj_id):
            logger.error(f"GridFS中没有找到视频: {video_id}")
            return jsonify({"error": "视频不存在"}), 404
        
        # 从GridFS获取文件
        file_data = fs.get(obj_id)
        
        # 创建临时输入文件
        temp_input = tempfile.NamedTemporaryFile(delete=False, suffix='.mp4')
        temp_input.write(file_data.read())
        temp_input.close()
        
        # 构建文件名
        filename = file_data.filename
        if not filename:
            filename = f"converted_video_{video_id}.mp4"
        else:
            # 确保文件扩展名为.mp4
            name_parts = filename.rsplit('.', 1)
            filename = f"{name_parts[0]}.mp4"
        
        # 检查视频格式和编码器
        is_already_compatible = False
        try:
            cap = cv2.VideoCapture(temp_input.name)
            if cap.isOpened():
                # 获取视频编码器
                fourcc_int = int(cap.get(cv2.CAP_PROP_FOURCC))
                fourcc_str = "".join([chr((fourcc_int >> 8 * i) & 0xFF) for i in range(4)])
                
                # 获取视频属性
                fps = cap.get(cv2.CAP_PROP_FPS)
                width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
                height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
                
                cap.release()
                
                # 检查是否已经是MP4格式且使用了Web兼容编码器
                is_already_compatible = fourcc_str.lower() in ['avc1', 'h264', 'x264', 'mp4v']
                logger.info(f"视频格式检查: 编码器={fourcc_str}, 是否兼容={is_already_compatible}")
        except Exception as e:
            logger.error(f"检查视频编码器时出错: {str(e)}")
        
        # 如果已经是兼容格式，直接使用原文件
        if is_already_compatible:
            logger.info(f"视频已经是Web兼容的MP4格式 (编码器: {fourcc_str})，无需转码")
            return send_file(
                temp_input.name,
                mimetype="video/mp4",
                as_attachment=True,
                download_name=filename
            )
            
        # 创建临时输出文件
        temp_output = tempfile.NamedTemporaryFile(delete=False, suffix='.mp4')
        temp_output.close()
        
        # 使用OpenCV进行视频转换
        logger.info("使用OpenCV转换视频格式")
        try:
            # 打开源视频
            cap = cv2.VideoCapture(temp_input.name)
            if not cap.isOpened():
                logger.error("无法打开源视频文件进行转换")
                return send_file(
                    temp_input.name,
                    mimetype="video/mp4",
                    as_attachment=True,
                    download_name=filename
                )
            
            # 获取视频属性
            fps = cap.get(cv2.CAP_PROP_FPS)
            width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            
            # 尝试创建MP4编码器
            success = False
            for codec in ['avc1', 'mp4v', 'H264', 'X264', 'XVID']:
                try:
                    fourcc = cv2.VideoWriter_fourcc(*codec)
                    out = cv2.VideoWriter(temp_output.name, fourcc, fps, (width, height))
                    if out.isOpened():
                        logger.info(f"成功使用{codec}编码器创建MP4文件")
                        success = True
                        break
                except Exception as e:
                    logger.warning(f"尝试{codec}编码器失败: {str(e)}")
            
            if not success:
                logger.error("所有编码器都失败，返回原始文件")
                return send_file(
                    temp_input.name,
                    mimetype="video/mp4",
                    as_attachment=True,
                    download_name=filename
                )
            
            # 逐帧转换
            frame_count = 0
            while True:
                ret, frame = cap.read()
                if not ret:
                    break
                out.write(frame)
                frame_count += 1
                if frame_count % 100 == 0:
                    logger.info(f"已转换 {frame_count} 帧")
            
            # 释放资源
            cap.release()
            out.release()
            logger.info(f"视频转换完成，共处理 {frame_count} 帧")
            
            # 检查输出文件
            if os.path.exists(temp_output.name) and os.path.getsize(temp_output.name) > 100000:
                logger.info(f"转换后的视频大小: {os.path.getsize(temp_output.name)/1024:.2f}KB")
                return send_file(
                    temp_output.name,
                    mimetype="video/mp4",
                    as_attachment=True,
                    download_name=filename
                )
            else:
                logger.error("转换后的视频无效或太小")
                return send_file(
                    temp_input.name,
                    mimetype="video/mp4",
                    as_attachment=True,
                    download_name=filename
                )
        except Exception as e:
            logger.error(f"使用OpenCV转换视频失败: {str(e)}")
            return send_file(
                temp_input.name,
                mimetype="video/mp4",
                as_attachment=True,
                download_name=filename
            )
        
    except Exception as e:
        logger.error(f"转换视频为MP4格式失败: {str(e)}")
        logger.error(traceback.format_exc())
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    # 启动Flask应用
    port = int(os.environ.get('PORT', 5001))
    app.run(host='0.0.0.0', port=port, debug=False)
    logger.info(f"服务已启动在 http://localhost:{port}") 