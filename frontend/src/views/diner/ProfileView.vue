<template>
  <div class="profile-page">
    <nav class="diner-nav">
      <div class="nav-container">
        <button
          type="button"
          class="brand-button"
          @click="goBack"
        >
          <span class="brand-logo-shell">
            <img
              src="../../assets/images/greedy-cat.png"
              alt="食尚参谋"
              class="logo-img"
            />
          </span>

          <span class="brand-copy">
            <strong>食尚参谋</strong>
            <small>个人中心</small>
          </span>
        </button>

        <div class="nav-links">
          <button
            type="button"
            class="back-btn"
            @click="goBack"
          >
            <span>←</span>
            返回首页
          </button>

          <button
            type="button"
            class="logout-btn"
            @click="handleLogout"
          >
            退出登录
          </button>
        </div>
      </div>
    </nav>

    <main class="profile-main">
      <div class="container">
        <section class="profile-hero">
          <div class="profile-identity">
            <div class="avatar">
              <span class="avatar-icon">👤</span>
            </div>

            <div class="user-details">
              <span class="identity-eyebrow">MY PROFILE</span>
              <h1>{{ userInfo.username || '食客用户' }}</h1>
              <p>在这里管理你的评价、消息、举报记录和账户信息</p>
            </div>
          </div>

          <span class="role-badge">食客</span>
        </section>

        <section class="primary-entry-section">
          <div class="section-heading">
            <div>
              <span class="section-eyebrow">CONTENT MANAGEMENT</span>
              <h2>我的内容</h2>
              <p>查看并管理你在食尚参谋中的个人内容</p>
            </div>
          </div>

          <div class="primary-entry-grid">
            <button
              type="button"
              class="primary-entry review-entry"
              @click="goToMyReviews"
            >
              <span class="entry-icon-shell">📝</span>

              <span class="entry-copy">
                <strong>我的评价</strong>
                <small>查看、编辑和管理我发表的商家评价</small>
              </span>

              <span class="entry-arrow">→</span>
            </button>

            <button
              type="button"
              class="primary-entry"
              @click="goToNotifications"
            >
              <span class="entry-icon-shell">💬</span>

              <span class="entry-copy">
                <strong>消息中心</strong>
                <small>查看回复、审核结果和系统通知</small>
              </span>

              <span
                v-if="unreadCount > 0"
                class="unread-badge"
              >
                {{ unreadCount }}
              </span>

              <span class="entry-arrow">→</span>
            </button>

            <button
              type="button"
              class="primary-entry"
              @click="goToMyReports"
            >
              <span class="entry-icon-shell">🛡️</span>

              <span class="entry-copy">
                <strong>我的举报</strong>
                <small>查看已提交举报及其处理进度</small>
              </span>

              <span class="entry-arrow">→</span>
            </button>
          </div>
        </section>

        <section class="dashboard-grid">
          <div class="dashboard-card info-section">
            <div class="card-heading">
              <span class="card-icon">👤</span>

              <div>
                <h2>基本信息</h2>
                <p>当前登录账户的基础资料</p>
              </div>
            </div>

            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">用户名</span>
                <span class="info-value">
                  {{ userInfo.username || '食客用户' }}
                </span>
              </div>

              <div class="info-item">
                <span class="info-label">用户角色</span>
                <span class="info-value">食客</span>
              </div>

              <div class="info-item">
                <span class="info-label">注册时间</span>
                <span class="info-value">
                  {{ formatDate(userInfo.createdAt) }}
                </span>
              </div>

              <div class="info-item">
                <span class="info-label">登录次数</span>
                <span class="info-value">
                  {{ userInfo.loginCount || 1 }}
                </span>
              </div>
            </div>
          </div>

          <div class="dashboard-card stats-section">
            <div class="card-heading">
              <span class="card-icon">📊</span>

              <div>
                <h2>我的统计</h2>
                <p>个人使用情况概览</p>
              </div>
            </div>

            <div class="stats-grid">
              <div class="stat-card">
                <div class="stat-icon">🍽️</div>
                <div class="stat-content">
                  <div class="stat-value">12</div>
                  <div class="stat-label">已评价餐厅</div>
                </div>
              </div>

              <div class="stat-card">
                <div class="stat-icon">⭐</div>
                <div class="stat-content">
                  <div class="stat-value">4.8</div>
                  <div class="stat-label">平均评分</div>
                </div>
              </div>

              <div class="stat-card">
                <div class="stat-icon">❤️</div>
                <div class="stat-content">
                  <div class="stat-value">8</div>
                  <div class="stat-label">收藏餐厅</div>
                </div>
              </div>

              <div class="stat-card">
                <div class="stat-icon">💬</div>
                <div class="stat-content">
                  <div class="stat-value">24</div>
                  <div class="stat-label">互动评论</div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="account-section">
          <div class="section-heading">
            <div>
              <span class="section-eyebrow">ACCOUNT SETTINGS</span>
              <h2>账户设置</h2>
              <p>完善账户安全和联系方式</p>
            </div>
          </div>

          <div class="action-grid">
            <button type="button" class="action-btn">
              <span class="action-icon">✏️</span>

              <span class="action-copy">
                <strong>修改密码</strong>
                <small>定期更新密码，保护账户安全</small>
              </span>

              <span class="action-arrow">→</span>
            </button>

            <button type="button" class="action-btn">
              <span class="action-icon">📧</span>

              <span class="action-copy">
                <strong>绑定邮箱</strong>
                <small>用于找回账户和接收重要提醒</small>
              </span>

              <span class="action-arrow">→</span>
            </button>

            <button type="button" class="action-btn">
              <span class="action-icon">📱</span>

              <span class="action-copy">
                <strong>绑定手机</strong>
                <small>提升账户安全性和身份验证能力</small>
              </span>

              <span class="action-arrow">→</span>
            </button>

            <button type="button" class="action-btn">
              <span class="action-icon">🔒</span>

              <span class="action-copy">
                <strong>隐私设置</strong>
                <small>管理个人信息和内容展示范围</small>
              </span>

              <span class="action-arrow">→</span>
            </button>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const userInfo = ref({ username: '', createdAt: new Date(), loginCount: 1 })
const unreadCount = ref(0)

onMounted(() => {
  const user = localStorage.getItem('user')
  if (user) {
    userInfo.value = JSON.parse(user)
  }
  loadUnreadCount()
})

const loadUnreadCount = async () => {
  try {
    const response = await request.get('/api/notifications/count-unread')
    if (response.success && response.data) {
      unreadCount.value = response.data.count || 0
    }
  } catch (error) {
    console.error('获取未读消息数量失败:', error)
  }
}

const goBack = () => {
  router.push('/diner/home')
}

const goToMyReviews = () => {
  router.push('/diner/my-reviews')
}

const goToNotifications = () => {
  router.push('/diner/notifications')
}

const goToMyReports = () => {
  router.push('/diner/my-reports')
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
.profile-page,
.profile-page *,
.profile-page *::before,
.profile-page *::after {
  box-sizing: border-box;
}

.profile-page {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 100vh;
  overflow-x: hidden;
  overflow-x: clip;
  color: #2f2924;
  font-family:
    "Microsoft YaHei",
    "PingFang SC",
    "Noto Sans SC",
    Arial,
    sans-serif;
  background:
    radial-gradient(
      circle at 8% 3%,
      rgba(255, 228, 204, 0.56),
      transparent 24%
    ),
    radial-gradient(
      circle at 92% 7%,
      rgba(254, 240, 214, 0.68),
      transparent 25%
    ),
    #f8f6f2;
}

.profile-page button {
  font-family: inherit;
}

.diner-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  width: 100%;
  border-bottom: 1px solid rgba(229, 222, 212, 0.85);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(18px);
}

.nav-container,
.container {
  width: calc(100% - 48px);
  max-width: 1180px;
  min-width: 0;
  margin-right: auto;
  margin-left: auto;
}

.nav-container {
  display: flex;
  min-height: 72px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.brand-button {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
  padding: 0;
  border: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.brand-logo-shell {
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  place-items: center;
  overflow: hidden;
  border: 1px solid #f2dfd0;
  border-radius: 14px;
  background: #fff8f1;
}

.logo-img {
  width: 38px;
  height: 38px;
  object-fit: cover;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.brand-copy strong {
  color: #2d2925;
  font-size: 18px;
  font-weight: 750;
  line-height: 1.25;
}

.brand-copy small {
  color: #9a8f85;
  font-size: 12px;
  line-height: 1.35;
}

.nav-links {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.back-btn,
.logout-btn {
  min-height: 40px;
  padding: 0 13px;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition:
    border-color 0.2s,
    color 0.2s,
    background 0.2s,
    transform 0.2s;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid #fed7aa;
  color: #c2410c;
  background: #fff8f1;
}

.back-btn:hover {
  transform: translateX(-1px);
  border-color: #fb923c;
  background: #fff1e6;
}

.logout-btn {
  border: 1px solid #fecaca;
  color: #dc2626;
  background: #fff;
}

.logout-btn:hover {
  background: #fef2f2;
}

.profile-main {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 24px 0 46px;
}

.profile-hero {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 30px 34px;
  overflow: hidden;
  border: 1px solid #f1dcc8;
  border-radius: 26px;
  background:
    radial-gradient(
      circle at 87% 16%,
      rgba(251, 146, 60, 0.2),
      transparent 28%
    ),
    linear-gradient(135deg, #fffaf4 0%, #fff1e5 100%);
  box-shadow: 0 18px 44px rgba(111, 75, 43, 0.09);
}

.profile-identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 18px;
}

.avatar {
  display: grid;
  width: 76px;
  height: 76px;
  flex: 0 0 76px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.9);
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 12px 28px rgba(133, 83, 39, 0.1);
}

.avatar-icon {
  font-size: 36px;
}

.user-details {
  min-width: 0;
}

.identity-eyebrow,
.section-eyebrow {
  display: block;
  color: #ea580c;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.11em;
}

.user-details h1 {
  margin: 5px 0 4px;
  color: #29231e;
  font-size: 31px;
  font-weight: 800;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.user-details p {
  margin: 0;
  color: #776d64;
  font-size: 15px;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.role-badge {
  flex: 0 0 auto;
  padding: 7px 12px;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #c2410c;
  font-size: 13px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.75);
}

.primary-entry-section,
.account-section {
  width: 100%;
  min-width: 0;
  margin-top: 26px;
}

.section-heading {
  display: flex;
  min-width: 0;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
}

.section-heading > div {
  min-width: 0;
}

.section-heading h2 {
  margin: 4px 0 0;
  color: #29231e;
  font-size: 25px;
  font-weight: 750;
  line-height: 1.35;
}

.section-heading p {
  margin: 4px 0 0;
  color: #938980;
  font-size: 14px;
  line-height: 1.55;
}

.primary-entry-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: 1.35fr 1fr 1fr;
  gap: 14px;
}

.primary-entry {
  position: relative;
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 108px;
  align-items: center;
  gap: 14px;
  padding: 18px;
  overflow: hidden;
  border: 1px solid #e9e2db;
  border-radius: 18px;
  color: #39332e;
  text-align: left;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 7px 20px rgba(80, 61, 43, 0.05);
  cursor: pointer;
  transition:
    transform 0.22s,
    border-color 0.22s,
    box-shadow 0.22s;
}

.primary-entry:hover {
  transform: translateY(-3px);
  border-color: #fdba74;
  box-shadow: 0 14px 28px rgba(91, 66, 43, 0.1);
}

.review-entry {
  border-color: #fdba74;
  background:
    radial-gradient(
      circle at 92% 18%,
      rgba(251, 146, 60, 0.16),
      transparent 32%
    ),
    linear-gradient(135deg, #fffaf4, #fff3e7);
}

.entry-icon-shell {
  display: grid;
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  place-items: center;
  border-radius: 15px;
  font-size: 23px;
  background: #fff3e8;
}

.entry-copy {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 4px;
}

.entry-copy strong {
  color: #312a25;
  font-size: 18px;
  line-height: 1.4;
}

.entry-copy small {
  color: #8a8178;
  font-size: 13px;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.entry-arrow,
.action-arrow {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 10px;
  transition: transform 0.2s;
}

.entry-arrow {
  width: 34px;
  height: 34px;
  border: 1px solid #ebe5df;
  color: #c2410c;
  font-size: 18px;
  background: #fff;
}

.primary-entry:hover .entry-arrow {
  transform: translateX(3px);
}

.unread-badge {
  display: grid;
  min-width: 24px;
  height: 24px;
  flex: 0 0 auto;
  place-items: center;
  padding: 0 7px;
  border-radius: 999px;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  background: #ef4444;
}

.dashboard-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: minmax(0, 0.86fr) minmax(0, 1.14fr);
  gap: 16px;
  margin-top: 26px;
}

.dashboard-card {
  width: 100%;
  min-width: 0;
  padding: 22px;
  border: 1px solid #ebe4dd;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 7px 22px rgba(80, 61, 43, 0.05);
}

.card-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee8e1;
}

.card-icon {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  border-radius: 13px;
  font-size: 20px;
  background: #fff3e8;
}

.card-heading > div {
  min-width: 0;
}

.card-heading h2 {
  margin: 0;
  color: #342e29;
  font-size: 19px;
  line-height: 1.4;
}

.card-heading p {
  margin: 2px 0 0;
  color: #9a9087;
  font-size: 12px;
  line-height: 1.45;
}

.info-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.info-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 5px;
  padding: 13px 14px;
  border: 1px solid #eee8e1;
  border-radius: 11px;
  background: #faf8f5;
}

.info-label {
  color: #9a9087;
  font-size: 12px;
}

.info-value {
  color: #3d3630;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.stats-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.stat-card {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
  padding: 13px;
  border: 1px solid #eee8e1;
  border-radius: 12px;
  background: #faf8f5;
}

.stat-icon {
  display: grid;
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  place-items: center;
  border-radius: 12px;
  font-size: 20px;
  background: #fff;
}

.stat-content {
  min-width: 0;
}

.stat-value {
  color: #ea580c;
  font-size: 21px;
  font-weight: 800;
  line-height: 1.25;
}

.stat-label {
  margin-top: 2px;
  color: #80766e;
  font-size: 12px;
  line-height: 1.4;
}

.action-grid {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.action-btn {
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 84px;
  align-items: center;
  gap: 13px;
  padding: 15px;
  overflow: hidden;
  border: 1px solid #e9e2db;
  border-radius: 15px;
  color: #39332e;
  text-align: left;
  background: rgba(255, 255, 255, 0.92);
  cursor: pointer;
  transition:
    transform 0.2s,
    border-color 0.2s,
    box-shadow 0.2s;
}

.action-btn:hover {
  transform: translateY(-2px);
  border-color: #fdba74;
  box-shadow: 0 10px 22px rgba(91, 66, 43, 0.08);
}

.action-icon {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  border-radius: 13px;
  font-size: 20px;
  background: #fff3e8;
}

.action-copy {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 3px;
}

.action-copy strong {
  color: #3c3530;
  font-size: 15px;
  line-height: 1.4;
}

.action-copy small {
  color: #91877e;
  font-size: 12px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.action-arrow {
  width: 30px;
  height: 30px;
  color: #9a9087;
  font-size: 17px;
  background: #faf8f5;
}

.action-btn:hover .action-arrow {
  transform: translateX(2px);
  color: #c2410c;
}

@media (max-width: 980px) {
  .primary-entry-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .dashboard-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 700px) {
  .nav-container,
  .container {
    width: calc(100% - 32px);
  }

  .logout-btn {
    display: none;
  }

  .profile-main {
    padding-top: 16px;
  }

  .profile-hero {
    align-items: flex-start;
    padding: 24px;
  }

  .avatar {
    width: 62px;
    height: 62px;
    flex-basis: 62px;
    border-radius: 18px;
  }

  .avatar-icon {
    font-size: 30px;
  }

  .user-details h1 {
    font-size: 26px;
  }

  .role-badge {
    display: none;
  }

  .action-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 520px) {
  .brand-copy small,
  .back-btn span {
    display: none;
  }

  .back-btn {
    padding: 0 10px;
  }

  .profile-hero {
    padding: 20px 17px;
  }

  .profile-identity {
    align-items: flex-start;
    gap: 13px;
  }

  .avatar {
    width: 52px;
    height: 52px;
    flex-basis: 52px;
    border-radius: 16px;
  }

  .avatar-icon {
    font-size: 25px;
  }

  .user-details h1 {
    font-size: 23px;
  }

  .user-details p {
    font-size: 13px;
  }

  .primary-entry {
    min-height: 96px;
    padding: 15px;
  }

  .entry-icon-shell {
    width: 42px;
    height: 42px;
    flex-basis: 42px;
    font-size: 20px;
  }

  .entry-copy strong {
    font-size: 16px;
  }

  .entry-arrow {
    width: 30px;
    height: 30px;
  }

  .dashboard-card {
    padding: 17px;
  }

  .info-grid,
  .stats-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>

<style>
html,
body,
#app {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  margin: 0;
  overflow-x: hidden;
  overflow-x: clip;
}
</style>
