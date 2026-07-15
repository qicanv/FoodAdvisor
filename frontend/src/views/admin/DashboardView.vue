<template>
  <div class="admin-dashboard">
    <nav class="admin-nav">
      <div class="nav-container">
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋 - 管理端</span>
        </div>
        <div class="nav-links">
          <a href="/admin/home" class="nav-link active">首页</a>
          <button class="logout-btn" @click="handleLogout">退出登录</button>
        </div>
      </div>
    </nav>

    <main class="dashboard-main">
      <div class="container">
        <div class="page-header">
          <h1>欢迎回来，管理员</h1>
          <p>管理平台的核心功能入口</p>
        </div>

        <div class="quick-actions">
          <h2>快捷入口</h2>
          <div class="action-cards">
            <div class="action-card" @click="navigateTo('/admin/restaurants')">
              <div class="card-icon restaurant-icon">
                <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ffffff" stroke-width="2">
                  <path d="M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17"></path>
                  <path d="M12 9v4"></path>
                  <path d="M12 17h.01"></path>
                </svg>
              </div>
              <h3>餐厅列表</h3>
              <p>管理平台所有餐厅信息，查看和编辑餐厅数据</p>
              <div class="card-arrow">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#666666" stroke-width="2">
                  <path d="M5 12h14M12 5l7 7-7 7"></path>
                </svg>
              </div>
            </div>

            <div class="action-card" @click="navigateTo('/admin/model-configs')">
              <div class="card-icon model-icon">
                <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ffffff" stroke-width="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                  <path d="M9 12l2 2 4-4"></path>
                </svg>
              </div>
              <h3>模型配置</h3>
              <p>管理AI模型服务配置，绑定不同业务场景</p>
              <div class="card-arrow">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#666666" stroke-width="2">
                  <path d="M5 12h14M12 5l7 7-7 7"></path>
                </svg>
              </div>
            </div>
          </div>
        </div>

        <div class="stats-section">
          <h2>数据概览</h2>
          <div class="stats-grid">
            <div class="stat-card">
              <div class="stat-icon">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="#1890ff" stroke-width="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                  <circle cx="9" cy="7" r="4"></circle>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                </svg>
              </div>
              <div class="stat-info">
                <span class="stat-value">128</span>
                <span class="stat-label">注册用户</span>
              </div>
            </div>

            <div class="stat-card">
              <div class="stat-icon">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="#52c41a" stroke-width="2">
                  <path d="M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17"></path>
                  <path d="M12 9v4"></path>
                  <path d="M12 17h.01"></path>
                </svg>
              </div>
              <div class="stat-info">
                <span class="stat-value">25</span>
                <span class="stat-label">合作餐厅</span>
              </div>
            </div>

            <div class="stat-card">
              <div class="stat-icon">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="#ff6700" stroke-width="2">
                  <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                </svg>
              </div>
              <div class="stat-info">
                <span class="stat-value">1.2k</span>
                <span class="stat-label">AI调用次数</span>
              </div>
            </div>

            <div class="stat-card">
              <div class="stat-icon">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="#722ed1" stroke-width="2">
                  <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"></path>
                </svg>
              </div>
              <div class="stat-info">
                <span class="stat-value">3</span>
                <span class="stat-label">已配置模型</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const navigateTo = (path) => {
  router.push(path)
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  router.push('/admin')
}
</script>

<style scoped>
.admin-dashboard {
  min-height: 100vh;
  background: #f5f7fa;
}

.admin-nav {
  background: #ffffff;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  padding: 16px 0;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-img {
  width: 40px;
  height: 40px;
  border-radius: 8px;
}

.brand-name {
  font-size: 20px;
  font-weight: 700;
  color: #1890ff;
}

.nav-links {
  display: flex;
  gap: 24px;
  align-items: center;
}

.nav-link {
  font-size: 15px;
  color: #666666;
  text-decoration: none;
  padding: 8px 16px;
  border-radius: 8px;
  transition: all 0.2s;
}

.nav-link:hover {
  color: #1890ff;
  background: rgba(24, 144, 255, 0.1);
}

.nav-link.active {
  color: #ffffff;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
}

.logout-btn {
  padding: 8px 16px;
  background: #f5f5f5;
  color: #666666;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.logout-btn:hover {
  background: #e8e8e8;
  color: #333333;
}

.dashboard-main {
  padding-top: 80px;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.page-header {
  margin-bottom: 40px;
}

.page-header h1 {
  font-size: 28px;
  font-weight: 700;
  color: #1f2d3d;
  margin: 0;
}

.page-header p {
  font-size: 15px;
  color: #667085;
  margin: 8px 0 0;
}

.quick-actions {
  margin-bottom: 40px;
}

.quick-actions h2 {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 20px;
}

.action-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.action-card {
  background: #ffffff;
  border-radius: 16px;
  padding: 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.action-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.card-icon {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.restaurant-icon {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
}

.model-icon {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
}

.action-card h3 {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.action-card p {
  font-size: 14px;
  color: #667085;
  margin: 0;
  line-height: 1.6;
}

.card-arrow {
  margin-top: auto;
  display: flex;
  justify-content: flex-end;
}

.stats-section {
  margin-bottom: 40px;
}

.stats-section h2 {
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
  margin-bottom: 20px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.stat-card {
  background: #ffffff;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}

.stat-label {
  font-size: 13px;
  color: #667085;
}

@media (max-width: 992px) {
  .action-cards {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .nav-links {
    gap: 12px;
  }

  .nav-link {
    padding: 6px 12px;
    font-size: 14px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>