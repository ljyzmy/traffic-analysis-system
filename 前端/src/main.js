/* eslint-disable */
// 完全修复ResizeObserver循环错误
const patchResizeObserver = () => {
  // 保存原始的ResizeObserver
  const OriginalResizeObserver = window.ResizeObserver;
  
  if (!OriginalResizeObserver) return;
  
  // 创建一个增强的ResizeObserver
  window.ResizeObserver = class PatchedResizeObserver extends OriginalResizeObserver {
    constructor(callback) {
      // 包装回调以捕获并处理错误
      const patchedCallback = (entries, observer) => {
        try {
          // 正常调用原始回调
          return callback(entries, observer);
        } catch (e) {
          // 只记录非循环错误
          if (!e.message || !e.message.includes('ResizeObserver loop')) {
            console.error('ResizeObserver error:', e);
          }
          // 总是静默循环错误
          return [];
        }
      };
      
      // 使用修补后的回调调用原始构造函数
      super(patchedCallback);
    }
  };
  
  // 拦截console.error以过滤掉ResizeObserver循环错误
  const originalConsoleError = console.error;
  console.error = (...args) => {
    if (args.length > 0 && 
        typeof args[0] === 'string' && 
        args[0].includes('ResizeObserver loop')) {
      // 过滤掉ResizeObserver循环错误
      return;
    }
    originalConsoleError.apply(console, args);
  };
  
  // 捕获全局error事件
  window.addEventListener('error', (event) => {
    if (event.message && event.message.includes('ResizeObserver loop')) {
      // 阻止错误冒泡
      event.stopImmediatePropagation();
      event.preventDefault();
      return false;
    }
  }, true);
  
  // 额外捕获unhandledrejection
  window.addEventListener('unhandledrejection', (event) => {
    if (event.reason && event.reason.message && 
        event.reason.message.includes('ResizeObserver loop')) {
      // 阻止拒绝冒泡
      event.stopImmediatePropagation();
      event.preventDefault();
      return false;
    }
  }, true);
  
  // 禁用webpack-dev-server错误覆盖层中的ResizeObserver错误
  try {
    // 尝试直接访问webpack的错误处理函数
    if (window.__webpack_dev_server_client__) {
      const overlay = window.__webpack_dev_server_client__.overlay;
      if (overlay && overlay.errors) {
        const originalSend = overlay.socket && overlay.socket.send;
        if (originalSend) {
          overlay.socket.send = function(message) {
            try {
              const data = JSON.parse(message);
              if (data.type === 'error' && 
                  data.message && 
                  data.message.includes('ResizeObserver loop')) {
                return; // 不发送ResizeObserver错误
              }
            } catch (e) {
              // 解析失败，继续正常发送
            }
            return originalSend.apply(this, arguments);
          };
        }
      }
    }
    
    // 定期检查并清除错误覆盖层中的ResizeObserver错误
    const cleanupInterval = setInterval(() => {
      try {
        // 查找webpack错误覆盖层元素
        const overlays = document.querySelectorAll('div[class*="webpack"]');
        for (const overlay of overlays) {
          const errorMessages = overlay.querySelectorAll('pre');
          for (const errorMsg of errorMessages) {
            if (errorMsg.textContent.includes('ResizeObserver loop')) {
              // 找到了ResizeObserver错误，移除整个覆盖层
              overlay.style.display = 'none';
              // 如果可能，尝试从DOM中移除
              if (overlay.parentNode) {
                overlay.parentNode.removeChild(overlay);
              }
            }
          }
        }
      } catch (e) {
        // 忽略清理过程中的错误
      }
    }, 1000);
    
    // 防止内存泄漏
    window.addEventListener('beforeunload', () => {
      clearInterval(cleanupInterval);
    });
  } catch (e) {
    console.error('修补webpack dev server错误失败:', e);
  }
};

// 立即执行修复以尽早捕获错误
if (typeof window !== 'undefined') {
  patchResizeObserver();
}

// 显式定义Vue特性标志
window.__VUE_PROD_DEVTOOLS__ = false;
window.__VUE_PROD_HYDRATION_MISMATCH_DETAILS__ = false;

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
// import axios from 'axios'

// 导入Element Plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import zhCn from 'element-plus/es/locale/lang/zh-cn'

// 导入自定义样式
import './assets/styles.css'

// 添加全局样式，防止导航栏字体变色
import './styles/prevent-color-change.css'

// 导入Bootstrap样式和图标
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap-icons/font/bootstrap-icons.css'
// 导入Bootstrap脚本
import 'bootstrap/dist/js/bootstrap.bundle.min.js'

// 导入并初始化STOMP服务
import stompService from './utils/stomp-service'

// 创建Vue应用实例
const app = createApp(App)

// 添加全局错误处理器
app.config.errorHandler = (err, vm, info) => {
  console.error('Vue全局错误:', err);
  console.log('错误发生在组件:', vm);
  console.log('错误信息:', info);
  
  // 检查是否为块加载错误
  if (err.name === 'ChunkLoadError' || (err.message && err.message.includes('Loading chunk'))) {
    console.log('检测到组件加载错误，尝试清除缓存并重新加载');
    
    // 显示友好的错误消息
    const errorMessage = document.createElement('div');
    errorMessage.innerHTML = `
      <div style="position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.8); z-index: 9999; display: flex; justify-content: center; align-items: center;">
        <div style="background: #1f2937; padding: 20px; border-radius: 8px; max-width: 80%; text-align: center; color: white;">
          <h3>组件加载失败</h3>
          <p>应用遇到了加载问题，正在尝试恢复...</p>
          <div style="margin-top: 20px;">
            <button onclick="window.location.reload()" style="padding: 8px 16px; background: #3b82f6; border: none; border-radius: 4px; color: white; cursor: pointer;">刷新页面</button>
          </div>
        </div>
      </div>
    `;
    document.body.appendChild(errorMessage);
    
    // 清除相关缓存数据
    try {
      // 清除所有会话存储
      sessionStorage.clear();
      
      // 清除与缓存相关的本地存储项
      const cacheKeys = Object.keys(localStorage).filter(key => 
        key.includes('cache') || 
        key.includes('chunk') || 
        key.includes('vite') ||
        key.includes('webpack')
      );
      
      cacheKeys.forEach(key => localStorage.removeItem(key));
      
      // 延迟3秒后重新加载页面
      setTimeout(() => {
        window.location.reload();
      }, 3000);
    } catch (e) {
      console.error('清除缓存失败:', e);
    }
  }
};

// 全局性能监控
if (typeof window !== 'undefined') {
  // 监听加载性能
  window.addEventListener('load', () => {
    // 使用Performance API获取页面加载性能数据
    if (window.performance) {
      const perfData = window.performance.timing;
      const pageLoadTime = perfData.loadEventEnd - perfData.navigationStart;
      console.log(`页面加载时间: ${pageLoadTime}ms`);
      
      // 上报到错误监控系统（模拟）
      if (pageLoadTime > 5000) {
        console.warn('页面加载时间过长，可能影响用户体验');
      }
    }
  });
  
  // 捕获未处理的拒绝
  window.addEventListener('unhandledrejection', (event) => {
    console.error('未处理的Promise拒绝:', event.reason);
    
    // 特殊处理加载错误
    if (event.reason && 
        (event.reason.name === 'ChunkLoadError' || 
         (event.reason.message && event.reason.message.includes('Loading chunk')))) {
      console.warn('检测到组件加载失败，将尝试清除缓存并刷新');
      // 记录错误但允许全局处理器处理
    }
  });
}

// 注册Element Plus图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 使用插件
app.use(store)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn
})

// 初始化STOMP服务连接
stompService.init().catch(error => {
  console.error('初始化STOMP服务失败:', error);
});

// 挂载应用
app.mount('#app')
