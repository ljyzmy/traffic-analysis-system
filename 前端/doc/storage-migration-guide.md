# 视频和图像存储迁移指南

## 背景

系统原先将处理后的图像和视频文件存储在文件系统中，具体位置为：
- 上传的原始视频：`/static/video/uploads/`
- 处理后的视频：`/static/video/results/`

这种存储方式存在以下问题：
1. 文件系统存储不便于集中管理和备份
2. 分布式部署时会导致文件不同步
3. 访问路径依赖于物理存储路径，不利于系统迁移

## 解决方案

将图像和视频存储在MongoDB数据库中，使用GridFS技术处理大型二进制文件。

## 前端修改内容

### 1. VideoResult.vue

修改`getVideoUrl`方法，添加对GridFS ID的支持，并增加`getImageUrl`方法：

```javascript
// 获取视频URL
const getVideoUrl = (path) => {
  if (!path) return ''
  
  // 检查是否为Base64数据
  if (isBase64(path)) {
    return path
  }
  
  // 检查是否为GridFS ID（24位十六进制字符串）
  if (/^[0-9a-f]{24}$/i.test(path)) {
    return `/api/media/video/${path}`;
  }
  
  // ... 原有逻辑 ...
}

// 获取图像URL
const getImageUrl = (imageId) => {
  // 检查是否为Base64数据
  if (imageId && imageId.startsWith('data:image')) {
    return imageId;
  }
  
  // 检查是否为GridFS ID（24位十六进制字符串）
  if (imageId && /^[0-9a-f]{24}$/i.test(imageId)) {
    return `/api/media/image/${imageId}`;
  }
  
  // 兼容旧版URL
  return imageId;
}
```

### 2. VideoPlayerPanel.vue

修改`getVideoUrl`方法，添加对GridFS ID的支持：

```javascript
// 处理视频路径，确保返回有效的URL
const getVideoUrl = (path) => {
  if (!path) return '';
  
  // 检查是否为Base64数据
  if (isBase64(path)) {
    return path;
  }
  
  // 检查是否为GridFS ID（24位十六进制字符串）
  if (/^[0-9a-f]{24}$/i.test(path)) {
    return `/api/media/video/${path}`;
  }
  
  // ... 原有逻辑 ...
};
```

### 3. HistoryList.vue

修改`getImageUrl`方法，添加对GridFS ID的支持：

```javascript
const getImageUrl = (url) => {
  if (!url) return '';
  
  // 检查是否为Base64数据
  if (url && url.startsWith('data:image')) {
    return url;
  }
  
  // 检查是否为GridFS ID（24位十六进制字符串）
  if (url && /^[0-9a-f]{24}$/i.test(url)) {
    return `/api/media/image/${url}`;
  }
  
  // ... 原有逻辑 ...
};
```

### 4. Result.vue

修改图像URL处理逻辑，支持GridFS ID：

```javascript
// 如果找到了图片URL
if (imageUrl) {
  // 检查是否为Base64数据
  if (imageUrl.startsWith('data:image')) {
    return imageUrl;
  }
  
  // 检查是否为GridFS ID（24位十六进制字符串）
  if (/^[0-9a-f]{24}$/i.test(imageUrl)) {
    return `/api/media/image/${imageUrl}`;
  }
  
  // ... 原有逻辑 ...
}
```

## 后续建议

1. **后端API开发**
   - 开发`/api/media/image/:id`和`/api/media/video/:id`端点，用于从GridFS获取图像和视频
   - 实现上传文件到GridFS的功能，返回GridFS ID
   - 实现文件迁移脚本，将现有文件系统中的文件迁移到GridFS

2. **缓存策略**
   - 为了提高性能，考虑在前端添加图像和视频缓存
   - 在服务器端添加适当的缓存头

3. **兼容性维护**
   - 保持对旧版URL格式的支持，确保无缝迁移
   - 记录哪些图像和视频已迁移到GridFS

4. **数据迁移**
   - 设计一个分批迁移策略，避免一次性迁移所有文件
   - 优先迁移最常访问的文件

5. **监控和日志**
   - 添加对GridFS访问的监控和日志记录
   - 记录存储使用情况和性能指标 