<template>
  <div class="profile-page">
    <nav class="diner-nav">
      <div class="nav-container">
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋 - 个人中心</span>
        </div>
        <div class="nav-links">
          <button class="back-btn" @click="goBack">返回首页</button>
          <button class="logout-btn" @click="handleLogout">退出登录</button>
        </div>
      </div>
    </nav>

    <main class="profile-main">
      <div class="container">
        <div class="profile-card">
          <div class="profile-header">
            <div class="avatar">
              <span class="avatar-icon">👤</span>
            </div>
            <div class="user-details">
              <h2>{{ userInfo.username }}</h2>
              <p class="user-role">食客</p>
            </div>
          </div>

          <div class="profile-content">
            <div class="info-section">
              <h3>基本信息</h3>
              <div class="info-grid">
                <div class="info-item">
                  <span class="info-label">用户名</span>
                  <span class="info-value">{{ userInfo.username }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">用户角色</span>
                  <span class="info-value">食客</span>
                </div>
                <div class="info-item">
                  <span class="info-label">注册时间</span>
                  <span class="info-value">{{ formatDate(userInfo.createdAt) }}</span>
                </div>
                <div class="info-item">
                  <span class="info-label">登录次数</span>
                  <span class="info-value">{{ userInfo.loginCount || 1 }}</span>
                </div>
              </div>
            </div>

            <div class="stats-section">
              <h3>我的统计</h3>
              <div class="stats-grid">
                <div class="stat-card">
                  <div class="stat-icon">🍽️</div>
                  <div class="stat-value">12</div>
                  <div class="stat-label">已评价餐厅</div>
                </div>
                <div class="stat-card">
                  <div class="stat-icon">⭐</div>
                  <div class="stat-value">4.8</div>
                  <div class="stat-label">平均评分</div>
                </div>
                <div class="stat-card">
                  <div class="stat-icon">❤️</div>
                  <div class="stat-value">8</div>
                  <div class="stat-label">收藏餐厅</div>
                </div>
                <div class="stat-card">
                  <div class="stat-icon">💬</div>
                  <div class="stat-value">24</div>
                  <div class="stat-label">互动评论</div>
                </div>
              </div>
            </div>

            <div class="actions-section">
              <h3>账户管理</h3>
              <div class="action-grid">
                <button class="action-btn" @click="goToMyReviews">
                  <span class="action-icon">📝</span>
                  <span class="action-text">我的评价</span>
                </button>
                <button class="action-btn">
                  <span class="action-icon">✏️</span>
                  <span class="action-text">修改密码</span>
                </button>
                <button class="action-btn">
                  <span class="action-icon">📧</span>
                  <span class="action-text">绑定邮箱</span>
                </button>
                <button class="action-btn">
                  <span class="action-icon">📱</span>
                  <span class="action-text">绑定手机</span>
                </button>
                <button class="action-btn">
                  <span class="action-icon">🔒</span>
                  <span class="action-text">隐私设置</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const userInfo = ref({ username: '', createdAt: new Date(), loginCount: 1 })

onMounted(() => {
  const user = localStorage.getItem('user')
  if (user) {
    userInfo.value = JSON.parse(user)
  }
})

const goBack = () => {
  router.push('/diner/home')
}

const goToMyReviews = () => {
  router.push('/diner/my-reviews')
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  router.push('/diner')
}

const formatDate = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.diner-nav {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
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
  color: #ff6700;
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-btn {
  padding: 8px 16px;
  background: #fff;
  color: #ff6700;
  border: 1px solid #ff6700;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  background: #fff7ed;
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
}

.profile-main {
  padding-top: 80px;
  padding-bottom: 40px;
}

.container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 20px;
}

.profile-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 30px;
  background: linear-gradient(135deg, #ff6700 0%, #ff9500 100%);
  color: #fff;
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-icon {
  font-size: 40px;
}

.user-details h2 {
  font-size: 24px;
  margin: 0 0 8px 0;
}

.user-role {
  font-size: 14px;
  opacity: 0.9;
  margin: 0;
}

.profile-content {
  padding: 30px;
}

.info-section,
.stats-section,
.actions-section {
  margin-bottom: 30px;
}

.info-section:last-child,
.stats-section:last-child,
.actions-section:last-child {
  margin-bottom: 0;
}

.profile-content h3 {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 20px 0;
  padding-bottom: 10px;
  border-bottom: 2px solid #f5f7fa;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 16px;
  background: #f9fafb;
  border-radius: 8px;
}

.info-label {
  font-size: 13px;
  color: #999;
}

.info-value {
  font-size: 15px;
  color: #333;
  font-weight: 500;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 16px;
  background: #f9fafb;
  border-radius: 12px;
}

.stat-icon {
  font-size: 28px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #ff6700;
}

.stat-label {
  font-size: 13px;
  color: #666;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 10px;
  font-size: 15px;
  color: #333;
  cursor: pointer;
  transition: all 0.2s;
}

.action-btn:hover {
  background: #f5f7fa;
  border-color: #ff6700;
  color: #ff6700;
}

.action-icon {
  font-size: 18px;
}

.action-text {
  font-weight: 500;
}

@media (max-width: 600px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .action-grid {
    grid-template-columns: 1fr;
  }
}
</style>