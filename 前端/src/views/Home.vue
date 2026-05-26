<template>
  <div class="home-container">
    <!-- 主横幅 -->
    <div class="hero-section">
      <div class="container">
        <div class="hero-content">
          <h1 class="title">智能交通分析系统</h1>
          <div class="subtitle">
            <span class="typing-text">基于人工智能的交通数据精准分析平台</span>
          </div>
          <div class="action-buttons">
            <router-link to="/upload" class="btn btn-primary">
              <i class="bi bi-image"></i> 图像分析
            </router-link>
            <router-link to="/video-upload" class="btn btn-secondary">
              <i class="bi bi-camera-video"></i> 视频分析
            </router-link>
          </div>
          <div class="secondary-buttons">
            <router-link to="/history" class="btn btn-outline">
              <i class="bi bi-images"></i> 图像历史
            </router-link>
            <router-link to="/video-history" class="btn btn-outline">
              <i class="bi bi-film"></i> 视频历史
            </router-link>
          </div>
        </div>
      </div>
      <!-- 装饰元素 -->
      <div class="decoration-elements">
        <div class="circle circle-1"></div>
        <div class="circle circle-2"></div>
        <div class="circle circle-3"></div>
        <div class="line line-1"></div>
        <div class="line line-2"></div>
      </div>
    </div>

    <!-- 特性部分 -->
    <div class="features-section">
      <div class="container">
        <h2 class="section-title">系统功能</h2>
        <div class="features-grid">
          <div class="feature-card">
            <div class="feature-icon">
              <i class="bi bi-cpu"></i>
            </div>
            <h3>AI智能识别</h3>
            <p>采用先进深度学习算法，精准识别交通场景中的车辆、行人及路况数据</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon">
              <i class="bi bi-graph-up-arrow"></i>
            </div>
            <h3>实时数据分析</h3>
            <p>高效处理视频和图像数据，提供实时流量统计和路况分析</p>
          </div>
          
          <div class="feature-card">
            <div class="feature-icon">
              <i class="bi bi-bar-chart-steps"></i>
            </div>
            <h3>可视化报表</h3>
            <p>多维度数据可视化展示，直观呈现分析结果和交通趋势</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 技术栈部分 -->
    <div class="tech-section">
      <div class="container">
        <div class="tech-grid">
          <div class="tech-info">
            <h2>先进技术驱动</h2>
            <p>采用最新人工智能和计算机视觉技术，打造高精度交通分析系统</p>
            <ul class="tech-list">
              <li><i class="bi bi-check-circle"></i> 深度学习目标检测</li>
              <li><i class="bi bi-check-circle"></i> 实时视频处理</li>
              <li><i class="bi bi-check-circle"></i> 高精度追踪算法</li>
              <li><i class="bi bi-check-circle"></i> 智能数据分析</li>
            </ul>
          </div>
          <div class="tech-visual">
            <div class="tech-circle">
              <div class="pulse"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { onMounted } from 'vue';

export default {
  name: 'HomeView',
  setup() {
    onMounted(() => {
      console.log('主页已加载，用户已认证');
    });

    return {};
  }
};
</script>

<style scoped>
/* 全局样式 */
.home-container {
  min-height: 100vh;
  background-color: #111827;
  color: #e5e7eb;
  overflow-x: hidden;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 1.5rem;
}

/* 主横幅部分 */
.hero-section {
  position: relative;
  height: 80vh;
  min-height: 600px;
  display: flex;
  align-items: center;
  overflow: hidden;
}

.hero-content {
  position: relative;
  z-index: 10;
  max-width: 800px;
  text-align: left;
  padding-left: 2rem;
}

.title {
  font-size: 4rem;
  font-weight: 800;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin-bottom: 1.5rem;
  line-height: 1.1;
  letter-spacing: -1px;
}

.subtitle {
  font-size: 1.5rem;
  font-weight: 400;
  color: #9ca3af;
  margin-bottom: 2.5rem;
  letter-spacing: 0.5px;
}

.typing-text {
  border-right: 3px solid #3b82f6;
  animation: cursor-blink 1s step-end infinite;
}

@keyframes cursor-blink {
  0%, 100% { border-color: transparent; }
  50% { border-color: #3b82f6; }
}

/* 按钮样式 */
.action-buttons {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 2rem;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.secondary-buttons {
  display: flex;
  gap: 1.5rem;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 1rem 2rem;
  border-radius: 0.75rem;
  font-weight: 600;
  font-size: 1.15rem;
  transition: all 0.3s ease;
  gap: 0.8rem;
  letter-spacing: 0.5px;
  cursor: pointer;
  border: none;
  text-decoration: none;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  min-width: 180px;
}

.btn i {
  font-size: 1.1rem;
}

.btn-primary {
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
}

.btn-primary:hover {
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  transform: translateY(-2px);
}

.btn-secondary {
  background: linear-gradient(135deg, #8b5cf6, #7c3aed);
  color: white;
}

.btn-secondary:hover {
  background: linear-gradient(135deg, #7c3aed, #6d28d9);
  transform: translateY(-2px);
}

.btn-outline {
  background: rgba(255, 255, 255, 0.05);
  color: #e5e7eb;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.btn-outline:hover {
  background: rgba(255, 255, 255, 0.1);
  transform: translateY(-2px);
}

/* 装饰元素 */
.decoration-elements {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1;
}

.circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.15;
}

.circle-1 {
  width: 300px;
  height: 300px;
  background: radial-gradient(#3b82f6, transparent);
  top: 10%;
  right: 5%;
  animation: float 8s ease-in-out infinite;
}

.circle-2 {
  width: 200px;
  height: 200px;
  background: radial-gradient(#8b5cf6, transparent);
  bottom: 15%;
  right: 20%;
  animation: float 6s ease-in-out infinite;
  animation-delay: 1s;
}

.circle-3 {
  width: 150px;
  height: 150px;
  background: radial-gradient(#ec4899, transparent);
  top: 30%;
  left: 5%;
  animation: float 7s ease-in-out infinite;
  animation-delay: 2s;
}

.line {
  position: absolute;
  background: rgba(255, 255, 255, 0.05);
}

.line-1 {
  width: 100%;
  height: 1px;
  top: 35%;
  transform: rotate(-5deg);
  animation: lineGlow 4s infinite alternate;
}

.line-2 {
  width: 100%;
  height: 1px;
  bottom: 25%;
  transform: rotate(3deg);
  animation: lineGlow 4s infinite alternate-reverse;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-20px); }
}

@keyframes lineGlow {
  0% { opacity: 0.05; }
  100% { opacity: 0.2; }
}

/* 特性部分 */
.features-section {
  padding: 6rem 0;
  background-color: #1a2032;
}

.section-title {
  text-align: center;
  font-size: 2.5rem;
  font-weight: 700;
  margin-bottom: 4rem;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  letter-spacing: 0.5px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 2rem;
}

.feature-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 1rem;
  padding: 2rem;
  transition: all 0.3s ease;
  backdrop-filter: blur(5px);
}

.feature-card:hover {
  transform: translateY(-10px);
  background: rgba(255, 255, 255, 0.05);
  box-shadow: 0 10px 30px -15px rgba(0, 0, 0, 0.3);
}

.feature-icon {
  font-size: 2.5rem;
  margin-bottom: 1.5rem;
  color: #3b82f6;
  display: inline-block;
  border-radius: 1rem;
  padding: 1rem;
  background: rgba(59, 130, 246, 0.1);
}

.feature-card h3 {
  font-size: 1.5rem;
  font-weight: 600;
  margin-bottom: 1rem;
  color: white;
}

.feature-card p {
  color: #9ca3af;
  line-height: 1.6;
}

/* 技术栈部分 */
.tech-section {
  padding: 6rem 0 0;
  background-color: #111827;
}

.tech-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3rem;
  align-items: center;
}

.tech-info h2 {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 1.5rem;
  color: white;
}

.tech-info p {
  font-size: 1.1rem;
  color: #9ca3af;
  margin-bottom: 2rem;
  line-height: 1.6;
}

.tech-list {
  list-style: none;
  padding: 0;
}

.tech-list li {
  display: flex;
  align-items: center;
  margin-bottom: 1rem;
  color: #d1d5db;
}

.tech-list i {
  color: #3b82f6;
  margin-right: 0.75rem;
  font-size: 1.2rem;
}

.tech-visual {
  display: flex;
  justify-content: center;
  align-items: center;
}

.tech-circle {
  width: 300px;
  height: 300px;
  border-radius: 50%;
  background: radial-gradient(circle at center, rgba(59, 130, 246, 0.2), transparent 70%);
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
}

.pulse {
  width: 70%;
  height: 70%;
  border-radius: 50%;
  background: radial-gradient(circle at center, rgba(59, 130, 246, 0.1), transparent 70%);
  position: absolute;
  animation: pulse 3s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.8); opacity: 1; }
  50% { transform: scale(1.2); opacity: 0.5; }
  100% { transform: scale(0.8); opacity: 1; }
}

/* 响应式设计 */
@media (max-width: 992px) {
  .title {
    font-size: 3rem;
  }
  
  .tech-grid {
    grid-template-columns: 1fr;
  }
  
  .tech-visual {
    order: -1;
    margin-bottom: 2rem;
  }
}

@media (max-width: 768px) {
  .hero-section {
    height: auto;
    padding: 6rem 0;
  }
  
  .title {
    font-size: 2.5rem;
  }
  
  .subtitle {
    font-size: 1.2rem;
  }
  
  .action-buttons,
  .secondary-buttons {
    flex-direction: column;
    width: 100%;
  }
  
  .btn {
    width: 100%;
    justify-content: center;
  }
  
  .features-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 480px) {
  .title {
    font-size: 2rem;
  }
  
  .subtitle {
    font-size: 1rem;
  }
  
  .tech-circle {
    width: 200px;
    height: 200px;
  }
}
</style> 