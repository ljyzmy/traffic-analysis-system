# CSRF令牌处理指南

## 问题背景

后端日志显示视频上传请求被CSRF（跨站请求伪造）保护拦截，导致403错误：

```
Invalid CSRF token found for http://localhost:8080/api/video-analysis/upload
```

虽然后端可以配置特定API路径忽略CSRF检查，但为了更好的安全性，前端应该正确处理CSRF令牌。

## 解决方案

我们对前端代码进行了以下修改来解决CSRF令牌问题：

### 1. HTTP客户端增强

在`src/utils/http-common.js`中：

- 启用了跨域请求携带凭证（cookies）
- 添加了自动获取CSRF令牌并添加到请求头的拦截器
- 实现了多种方式获取CSRF令牌的函数

```javascript
// 创建axios实例
const apiClient = axios.create({
  // ...
  withCredentials: true // 启用跨域请求中携带凭证，确保CSRF令牌可用
});

// 请求拦截器
apiClient.interceptors.request.use(
  config => {
    // 获取CSRF令牌并添加到请求头
    const csrfToken = getCsrfToken();
    if (csrfToken) {
      config.headers['X-XSRF-TOKEN'] = csrfToken;
    }
    // ...其他逻辑
  }
);

// 获取CSRF令牌的辅助函数
function getCsrfToken() {
  // 尝试从多个来源获取令牌
  let token = getCookieValue('XSRF-TOKEN');
  if (!token) token = getCookieValue('X-CSRF-TOKEN');
  if (!token) {
    const metaTag = document.querySelector('meta[name="csrf-token"]');
    if (metaTag) token = metaTag.getAttribute('content');
  }
  return token;
}
```

### 2. 视频上传API增强

在`src/api/video.js`中：

- 添加了CSRF令牌辅助函数
- 修改了XMLHttpRequest上传方法，添加CSRF令牌头
- 添加了基于axios的新上传方法，自动处理CSRF令牌

```javascript
// 使用XMLHttpRequest添加CSRF令牌
const csrfToken = getCsrfToken();
if (csrfToken) {
  xhr.setRequestHeader('X-XSRF-TOKEN', csrfToken);
}

// 新增基于axios的上传方法
export function uploadWithAxios(formData) {
  // ...配置和检查逻辑
  
  // 使用apiClient发送请求（自动添加CSRF令牌）
  apiClient.post(url, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    // ...其他配置
  })
  .then(response => {
    // 处理成功响应
  })
  .catch(error => {
    // 处理错误响应
  });
}
```

## 使用建议

### 推荐方法

我们现在提供了两种上传视频的方法：

1. **推荐： `uploadWithAxios`** - 使用axios上传，自动处理CSRF令牌
   ```javascript
   import { uploadWithAxios } from '@/api/video';
   
   const formData = new FormData();
   formData.append('video', videoFile);
   formData.append('direction', 'horizontal');
   
   uploadWithAxios(formData)
     .then(response => {
       console.log('上传成功：', response);
     })
     .catch(error => {
       console.error('上传失败：', error);
     });
   ```

2. **传统： `uploadAndAnalyzeVideo`** - 已增强的原始方法，使用XMLHttpRequest
   ```javascript
   import { uploadAndAnalyzeVideo } from '@/api/video';
   
   // 与之前相同的使用方式
   ```

### CSRF令牌的来源

Spring Security通常通过以下方式提供CSRF令牌：

1. 在Cookie中设置`XSRF-TOKEN`
2. 在Cookie中设置`X-CSRF-TOKEN`
3. 在HTML文档中添加`<meta name="csrf-token" content="...">`标签

我们的实现会按顺序检查这些来源，确保能获取到令牌。

### 调试提示

如遇到CSRF相关问题，可以检查：

1. 浏览器开发者工具中的Cookie，确认是否存在CSRF令牌
2. 网络请求的请求头，确认是否包含`X-XSRF-TOKEN`头
3. 服务器日志，查看具体的CSRF错误信息

## 后端配置建议

如果仍然遇到CSRF问题，可以请求后端开发者：

1. 确认CSRF令牌生成和验证机制是否正常工作
2. 检查是否正确设置了`XSRF-TOKEN` Cookie
3. 对于不需要CSRF保护的API，可以考虑在安全配置中排除：

```java
http.csrf()
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .ignoringAntMatchers("/api/video-analysis/**");
```

## 参考资料

- [Spring Security CSRF 文档](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html)
- [Axios 文档 - 跨域请求凭证](https://axios-http.com/docs/req_config) 