# traffic-analysis-system

基于 YOLO 的交通视频分析系统（Vue 前端 + Spring Boot 后端 + Python 模型服务）。

## 技术栈

- 前端：Vue.js
- 后端：Spring Boot、MongoDB
- 模型：Python、YOLOv12

## 本地配置（敏感信息）

仓库中**不包含**真实密码。首次克隆后请：

1. 复制 `application-local.properties.example` 为 `application-local.properties`，填写 MongoDB 密码与 JWT 密钥。
2. 复制 `.env.example` 为 `.env`（Python 服务），填写 `MONGODB_URI` 等。
3. 启动 Java 后端时附加配置：
   ```bash
   mvn spring-boot:run -Dspring-boot.run.arguments="--spring.config.additional-location=application-local.properties"
   ```

## 目录结构

```
├── 前端/          # Vue 前端
├── 后端/          # Spring Boot + Python 模型
├── 模块划分与功能说明.md
└── 系统测试情况总结.md
```

## 许可证

MIT（可按需修改）
