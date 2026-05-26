<template>
  <footer class="footer">
    <div class="container">
      <div class="footer-content">
        <div class="footer-brand">
          <h3 class="footer-title">交通分析系统</h3>
          <p class="footer-desc">基于深度学习技术的智能交通数据分析平台</p>
        </div>
      </div>
      <div class="footer-bottom">
        <p>© 2024 交通分析系统 - 智能出行的数据助手</p>
      </div>
    </div>
  </footer>
</template>

<script>
export default {
  name: 'AppFooter'
};
</script>

<style scoped>
.footer {
  background-color: #111827;
  color: #e5e7eb;
  padding: 1.5rem 0;
  margin-top: 0;
}

.footer-content {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  margin-bottom: 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 1rem;
}

.footer-brand {
  flex: 1;
  min-width: 300px;
}

.footer-title {
  font-weight: 700;
  font-size: 1.5rem;
  background: linear-gradient(90deg, #3b82f6, #8b5cf6);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  margin-bottom: 1rem;
}

.footer-desc {
  color: #9ca3af;
  max-width: 400px;
}

.footer-bottom {
  padding-top: 0.5rem;
}

.footer-bottom p {
  color: #6b7280;
  margin: 0;
}

@media (max-width: 768px) {
  .footer-content {
    flex-direction: column;
  }
}
</style> 