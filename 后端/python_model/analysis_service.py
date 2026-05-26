#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
分析结果服务模块 - 处理分析结果的存储与检索
"""

from bson import ObjectId
from datetime import datetime
import os
import base64
from db_config import DatabaseConfig

class AnalysisService:
    def __init__(self):
        db_config = DatabaseConfig()
        self.analysis_results = db_config.analysis_results
        self.fs = db_config.fs  # 获取GridFS引用
        
        # 确保图像存储目录存在 (仅用于兼容旧代码，新代码不使用)
        self.image_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "static", "images")
        os.makedirs(self.image_dir, exist_ok=True)
    
    def save_analysis_result(self, user_id, username, result_data, request_ip):
        """保存分析结果"""
        try:
            print(f"开始保存分析结果: user_id={user_id}, username={username}")
            
            # 确保result_data是字典类型
            if not isinstance(result_data, dict):
                print(f"警告: result_data不是字典，而是{type(result_data)}")
                if hasattr(result_data, '__dict__'):
                    result_data = result_data.__dict__
                else:
                    print("无法转换为字典，创建空字典")
                    result_data = {}
            
            # 处理字段名称可能存在的不一致性（snake_case vs camelCase）
            vehicle_count = result_data.get("vehicleCount", 0)
            if vehicle_count == 0:
                # 尝试snake_case格式
                vehicle_count = result_data.get("vehicle_count", 0)
            
            inference_time = result_data.get("inferenceTime", 0)
            if inference_time == 0:
                # 尝试snake_case格式
                inference_time = result_data.get("inference_time", 0)
            
            # 确保数值类型正确
            try:
                vehicle_count = int(vehicle_count)
            except (ValueError, TypeError):
                print(f"警告: 车辆数量({vehicle_count})不是有效整数，设为0")
                vehicle_count = 0
            
            try:
                inference_time = float(inference_time)
            except (ValueError, TypeError):
                print(f"警告: 推理时间({inference_time})不是有效浮点数，设为0")
                inference_time = 0.0
            
            # 确保检测数据格式正确
            detections = result_data.get("detections", [])
            if not isinstance(detections, list):
                print(f"检测数据不是数组格式，将其设置为空数组 (实际类型: {type(detections)})")
                detections = []
            
            # 规范化检测对象
            normalized_detections = []
            for detection in detections:
                if not isinstance(detection, dict):
                    print(f"跳过无效检测对象: {detection}")
                    continue
                    
                try:
                    normalized_detection = {
                        "className": detection.get("className", "unknown"),
                        "confidence": float(detection.get("confidence", 0))
                    }
                    
                    # 处理坐标数据
                    if "bbox" in detection and isinstance(detection["bbox"], list):
                        try:
                            normalized_detection["bbox"] = [float(x) for x in detection["bbox"][:4]]
                        except (ValueError, TypeError):
                            print(f"无法转换bbox值: {detection['bbox']}")
                    
                    normalized_detections.append(normalized_detection)
                except Exception as e:
                    print(f"规范化检测对象出错: {e}")
            
            # 替换原有检测数据
            detections = normalized_detections
            
            # 处理时间戳
            try:
                if "timestamp" in result_data and result_data["timestamp"]:
                    if isinstance(result_data["timestamp"], str):
                        # 尝试解析ISO格式字符串
                        timestamp = datetime.fromisoformat(result_data["timestamp"].replace('Z', '+00:00'))
                    else:
                        timestamp = result_data["timestamp"]
                else:
                    timestamp = datetime.now()
            except Exception as e:
                print(f"时间戳解析错误: {str(e)}，使用当前时间")
                timestamp = datetime.now()
            
            # 记录数据情况，用于调试
            print(f"保存分析结果: vehicleCount={vehicle_count}, inferenceTime={inference_time}, 检测数={len(detections)}")
            
            # 处理图像URL
            image_url = result_data.get("imageUrl", "")
            if not image_url:
                # 尝试从base64保存图像 (仅在小型数据时有效)
                result_image_base64 = result_data.get("result_image_base64", "")
                if not result_image_base64:
                    result_image_base64 = result_data.get("resultImageBase64", "")
                    
                if result_image_base64 and len(result_image_base64) < 100000:  # 限制大小
                    image_url = self._save_image(result_image_base64)
                    print(f"从base64数据生成图像URL: {image_url}")
            
            # 验证user_id格式
            try:
                user_id_obj = ObjectId(user_id)
            except Exception as e:
                print(f"转换user_id为ObjectId失败: {e}, user_id={user_id}")
                user_id_obj = None
            
            # 设置默认用户名
            if not username:
                username = "unknown"
                print("Warning: 使用默认用户名'unknown'")
            
            # 构建分析结果文档
            analysis_result = {
                "userId": user_id_obj if user_id_obj else user_id,
                "username": username,
                "vehicleCount": vehicle_count,
                "inferenceTime": inference_time,
                "timestamp": timestamp,
                "detections": detections,
                "imageUrl": image_url,
                "requestIp": request_ip or "unknown"
            }
            
            # 添加任何额外的有用元数据，排除base64图像和大型对象
            excluded_keys = [
                "imageUrl", "result_image_base64", "resultImageBase64", 
                "originalImageBase64", "image", "resultImage"
            ]
            for key, value in result_data.items():
                if (key not in analysis_result and 
                    key not in excluded_keys and 
                    not isinstance(value, (dict, list)) and
                    (not isinstance(value, str) or len(value) < 10000)):
                    # 只加入简单类型的其他字段
                    analysis_result[key] = value
            
            print(f"准备插入数据库的分析结果字段: {list(analysis_result.keys())}")
            
            # 插入数据库前检查连接
            if not hasattr(self, 'analysis_results') or self.analysis_results is None:
                print("错误: 数据库集合对象未初始化")
                # 尝试重新初始化
                try:
                    from db_config import DatabaseConfig
                    db_config = DatabaseConfig()
                    self.analysis_results = db_config.analysis_results
                    print("成功重新初始化数据库连接")
                except Exception as conn_error:
                    print(f"重新初始化数据库连接失败: {conn_error}")
                    return "error_db_not_initialized"
            
            # 插入数据库
            try:
                result = self.analysis_results.insert_one(analysis_result)
                result_id = str(result.inserted_id)
                print(f"分析结果成功插入数据库，ID: {result_id}")
                return result_id
            except Exception as db_error:
                print(f"数据库插入操作失败: {db_error}")
                # 返回一个错误标识符
                return f"error_{hash(str(db_error)) % 10000}"
            
        except Exception as e:
            print(f"保存分析结果出错: {str(e)}")
            import traceback
            traceback.print_exc()
            # 返回一个错误标识符而不是引发异常
            return f"error_general_{hash(str(e)) % 10000}"
    
    def _save_image(self, image_base64):
        """保存图像到MongoDB GridFS并返回GridFS ID"""
        if not image_base64:
            return ""
        
        try:
            # 从Base64解码
            if ',' in image_base64:
                image_base64 = image_base64.split(',')[1]
            
            # 解码图像数据
            image_data = base64.b64decode(image_base64)
            
            # 生成文件名
            timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
            filename = f"result_{timestamp}.jpg"
            
            # 存储到GridFS
            file_id = self.fs.put(
                image_data, 
                filename=filename,
                content_type="image/jpeg",
                timestamp=datetime.now()
            )
            
            # 返回GridFS ID作为URL
            return str(file_id)
            
        except Exception as e:
            print(f"保存图像到GridFS出错: {str(e)}")
            return ""
    
    def get_user_analysis_results(self, user_id, limit=10, skip=0):
        """获取用户的分析结果"""
        cursor = self.analysis_results.find({"userId": ObjectId(user_id)})
        cursor = cursor.sort("timestamp", -1).skip(skip).limit(limit)
        return list(cursor)
    
    def get_analysis_result_by_id(self, result_id):
        """通过ID获取分析结果"""
        try:
            return self.analysis_results.find_one({"_id": ObjectId(result_id)})
        except:
            return None
    
    def get_recent_analysis_results(self, limit=20):
        """获取最近的分析结果"""
        cursor = self.analysis_results.find().sort("timestamp", -1).limit(limit)
        return list(cursor) 