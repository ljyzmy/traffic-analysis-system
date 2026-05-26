import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import { SOCKJS_ENDPOINT, STOMP_TOPIC_PREFIX } from '@/config';

/**
 * STOMP客户端服务
 * 用于管理WebSocket连接和消息订阅
 */
class StompService {
  constructor() {
    this.client = null;
    this.subscriptions = new Map();
    this.connected = false;
    this.connectionPromise = null;
    this.connectAttempts = 0; // 连接尝试计数
    this.debug = true; // 启用详细调试
  }

  /**
   * 记录调试信息
   * @param {string} message - 调试消息
   * @param {Object} data - 附加数据
   */
  log(message, data = null) {
    if (this.debug) {
      if (data) {
        console.log(`[STOMP] ${message}`, data);
      } else {
        console.log(`[STOMP] ${message}`);
      }
    }
  }

  /**
   * 记录错误信息
   * @param {string} message - 错误消息
   * @param {Error} error - 错误对象
   */
  logError(message, error = null) {
    if (error) {
      console.error(`[STOMP ERROR] ${message}`, error);
    } else {
      console.error(`[STOMP ERROR] ${message}`);
    }
  }

  /**
   * 初始化STOMP客户端
   * @returns {Promise} 连接成功的Promise
   */
  init() {
    if (this.connectionPromise) {
      this.log('复用现有连接Promise');
      return this.connectionPromise;
    }

    this.connectAttempts++;
    this.log(`开始初始化STOMP客户端 (尝试 #${this.connectAttempts})`);
    this.log(`连接端点: ${SOCKJS_ENDPOINT}`);

    this.connectionPromise = new Promise((resolve, reject) => {
      try {
        // 创建SockJS连接
        this.log(`尝试创建SockJS连接到 ${SOCKJS_ENDPOINT}`);
        const socket = new SockJS(SOCKJS_ENDPOINT);
        
        socket.onopen = () => {
          this.log('SockJS连接已打开');
        };
        
        socket.onclose = (event) => {
          this.logError(`SockJS连接已关闭，code: ${event.code}, reason: ${event.reason}`);
        };
        
        socket.onerror = (error) => {
          this.logError('SockJS连接错误', error);
        };
        
        // 创建STOMP客户端
        this.log('创建STOMP客户端');
        this.client = new Client({
          webSocketFactory: () => socket,
          debug: (str) => {
            if (this.debug) {
              console.debug('[STOMP DEBUG] ' + str);
            }
          },
          reconnectDelay: 5000, // 5秒重连
          heartbeatIncoming: 4000,
          heartbeatOutgoing: 4000
        });

        // 连接成功回调
        this.client.onConnect = (frame) => {
          this.log('STOMP连接已建立', frame);
          this.connected = true;
          this.connectAttempts = 0; // 重置连接尝试计数
          resolve(true);
        };

        // 连接错误回调
        this.client.onStompError = (frame) => {
          this.logError('STOMP协议错误', frame);
          reject(new Error('STOMP协议错误: ' + frame.headers.message));
        };

        // 连接断开回调
        this.client.onDisconnect = () => {
          this.log('STOMP连接已断开');
          this.connected = false;
          this.connectionPromise = null;
        };
        
        // WebSocket错误回调
        this.client.onWebSocketError = (event) => {
          this.logError('WebSocket错误', event);
        };
        
        // WebSocket关闭回调
        this.client.onWebSocketClose = (event) => {
          this.logError(`WebSocket连接关闭: code=${event.code}, reason=${event.reason}`, event);
        };

        // 激活连接
        this.log('激活STOMP客户端连接');
        this.client.activate();
      } catch (error) {
        this.logError('STOMP初始化过程中发生错误', error);
        this.connectionPromise = null;
        reject(error);
      }
    });

    return this.connectionPromise;
  }

  /**
   * 订阅主题
   * @param {string} topic - 主题名称，不含前缀
   * @param {Function} callback - 收到消息的回调函数
   * @returns {Promise<Object>} 订阅对象
   */
  async subscribe(topic, callback) {
    this.log(`尝试订阅主题: ${topic}`);
    
    if (!this.connected) {
      this.log('STOMP客户端未连接，尝试初始化连接');
      try {
        await this.init();
      } catch (error) {
        this.logError('订阅前初始化STOMP客户端失败', error);
        throw error;
      }
    }

    const fullTopic = `${STOMP_TOPIC_PREFIX}/${topic}`;
    this.log(`完整主题路径: ${fullTopic}`);
    
    if (this.subscriptions.has(fullTopic)) {
      this.log(`已存在对 ${fullTopic} 的订阅，复用现有订阅`);
      return this.subscriptions.get(fullTopic);
    }

    try {
      this.log(`订阅主题: ${fullTopic}`);
      const subscription = this.client.subscribe(fullTopic, (message) => {
        try {
          this.log(`收到主题 ${fullTopic} 的消息`);
          const data = JSON.parse(message.body);
          callback(data);
        } catch (error) {
          this.logError(`解析 ${fullTopic} 的消息失败`, error);
        }
      });

      this.subscriptions.set(fullTopic, subscription);
      this.log(`订阅成功: ${fullTopic}, ID: ${subscription.id}`);
      return subscription;
    } catch (error) {
      this.logError(`订阅 ${fullTopic} 失败`, error);
      throw error;
    }
  }

  /**
   * 取消订阅
   * @param {string} topic - 主题名称，不含前缀
   */
  unsubscribe(topic) {
    const fullTopic = `${STOMP_TOPIC_PREFIX}/${topic}`;
    this.log(`尝试取消订阅: ${fullTopic}`);
    
    if (this.subscriptions.has(fullTopic)) {
      try {
        const subscription = this.subscriptions.get(fullTopic);
        this.log(`找到订阅，ID: ${subscription.id}，开始取消订阅`);
        subscription.unsubscribe();
        this.subscriptions.delete(fullTopic);
        this.log(`已成功取消订阅主题: ${fullTopic}`);
      } catch (error) {
        this.logError(`取消订阅 ${fullTopic} 失败`, error);
        // 即使发生错误，也移除订阅记录
        this.subscriptions.delete(fullTopic);
      }
    } else {
      this.log(`未找到对 ${fullTopic} 的订阅记录，无需取消`);
    }
  }

  /**
   * 断开连接
   */
  disconnect() {
    if (this.client && this.connected) {
      this.log('开始断开STOMP客户端连接');
      
      try {
        // 取消所有订阅
        this.log(`取消所有订阅，当前订阅数: ${this.subscriptions.size}`);
        this.subscriptions.forEach((subscription, topic) => {
          try {
            this.log(`取消订阅: ${topic}, ID: ${subscription.id}`);
            subscription.unsubscribe();
          } catch (error) {
            this.logError(`取消订阅 ${topic} 失败`, error);
          }
        });
        this.subscriptions.clear();
        this.log('所有订阅已清除');

        // 断开连接
        this.log('执行STOMP客户端断开连接');
        this.client.deactivate();
        this.connected = false;
        this.connectionPromise = null;
        this.log('STOMP客户端已成功断开连接');
      } catch (error) {
        this.logError('断开STOMP连接过程中发生错误', error);
        // 重置状态
        this.connected = false;
        this.connectionPromise = null;
      }
    } else {
      this.log('STOMP客户端未连接或不存在，无需断开');
    }
  }
}

// 导出单例实例
export default new StompService(); 