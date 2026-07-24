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
            <span>个人中心</span>
          </span>
        </button>

        <div class="nav-links">
          <button
            type="button"
            class="back-btn"
            @click="goBack"
          >
            ← 返回首页
          </button>

          <UserAccountMenu
            role="diner"
            profile-path="/diner/profile"
          />
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
              <h1>{{ userInfo.username || '食客用户' }}</h1>

              <div class="user-meta">
                <span class="role-badge">食客</span>
                <span>注册于 {{ formatDate(userInfo.createdAt) }}</span>
                <span class="meta-divider"></span>
                <span>登录 {{ userInfo.loginCount || 1 }} 次</span>
              </div>
            </div>
          </div>
        </section>

        <section class="content-section">
          <div class="section-heading">
            <h2>我的内容</h2>
          </div>

          <div class="content-grid">
            <button
              type="button"
              class="content-card review-card"
              @click="goToMyReviews"
            >
              <span class="content-icon">📝</span>
              <span class="content-title">我的评价</span>
              <span class="content-arrow">→</span>
            </button>

            <button
              type="button"
              class="content-card"
              @click="goToNotifications"
            >
              <span class="content-icon">💬</span>
              <span class="content-title">消息中心</span>

              <span class="content-end">
                <span
                  v-if="unreadCount > 0"
                  class="unread-badge"
                >
                  {{ unreadCount }}
                </span>
                <span class="content-arrow">→</span>
              </span>
            </button>

            <button
              type="button"
              class="content-card"
              @click="goToMyReports"
            >
              <span class="content-icon">🛡️</span>
              <span class="content-title">我的举报</span>
              <span class="content-arrow">→</span>
            </button>
          </div>
        </section>

        <section class="dashboard-grid">
          <div class="dashboard-card">
            <div class="card-heading">
              <span class="heading-icon">👤</span>
              <h2>账户信息</h2>
            </div>

            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">用户名</span>
                <strong>{{ userInfo.username || '食客用户' }}</strong>
              </div>

              <div class="info-item">
                <span class="info-label">用户角色</span>
                <strong>食客</strong>
              </div>

              <div class="info-item">
                <span class="info-label">注册时间</span>
                <strong>{{ formatDate(userInfo.createdAt) }}</strong>
              </div>

              <div class="info-item">
                <span class="info-label">登录次数</span>
                <strong>{{ userInfo.loginCount || 1 }}</strong>
              </div>
            </div>
          </div>

          <div class="dashboard-card">
            <div class="card-heading">
              <span class="heading-icon">⚙️</span>
              <h2>账户设置</h2>
            </div>

            <div class="setting-list">
              <button type="button" class="setting-item">
                <span class="setting-icon">✏️</span>
                <span class="setting-name">修改密码</span>
                <span class="pending-tag">待接入</span>
              </button>

              <button type="button" class="setting-item">
                <span class="setting-icon">📧</span>
                <span class="setting-name">绑定邮箱</span>
                <span class="pending-tag">待接入</span>
              </button>

              <button type="button" class="setting-item">
                <span class="setting-icon">📱</span>
                <span class="setting-name">绑定手机</span>
                <span class="pending-tag">待接入</span>
              </button>

              <button type="button" class="setting-item">
                <span class="setting-icon">🔒</span>
                <span class="setting-name">隐私设置</span>
                <span class="pending-tag">待接入</span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'
import UserAccountMenu from '../../components/UserAccountMenu.vue'

const router = useRouter()

const userInfo = ref({
  username: '',
  createdAt: null,
  loginCount: 1
})

const unreadCount = ref(0)

const readStoredUser = () => {
  const rawUser = localStorage.getItem('user')

  if (!rawUser) {
    return null
  }

  try {
    return JSON.parse(rawUser)
  } catch (error) {
    console.error('读取用户信息失败:', error)
    return null
  }
}

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

const formatDate = date => {
  if (!date) return '-'

  const parsedDate = new Date(date)

  if (Number.isNaN(parsedDate.getTime())) {
    return '-'
  }

  return [
    parsedDate.getFullYear(),
    String(parsedDate.getMonth() + 1).padStart(2, '0'),
    String(parsedDate.getDate()).padStart(2, '0')
  ].join('-')
}

onMounted(() => {
  const storedUser = readStoredUser()

  if (storedUser) {
    userInfo.value = {
      ...userInfo.value,
      ...storedUser
    }
  }

  loadUnreadCount()
})
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
  min-height: 100vh;
  color: #302a25;
  background:
    radial-gradient(
      circle at 8% 3%,
      rgba(255, 228, 204, 0.5),
      transparent 25%
    ),
    radial-gradient(
      circle at 92% 5%,
      rgba(254, 240, 214, 0.58),
      transparent 24%
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
  border-bottom: 1px solid rgba(229, 222, 212, 0.86);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
}

.nav-container,
.container {
  width: calc(100% - 48px);
  max-width: 1080px;
  min-width: 0;
  margin: 0 auto;
}

.nav-container {
  display: flex;
  min-height: 70px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.brand-button {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.brand-logo-shell {
  display: grid;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  place-items: center;
  overflow: hidden;
  border: 1px solid #f0ddce;
  border-radius: 13px;
  background: #fff8f1;
}

.logo-img {
  width: 37px;
  height: 37px;
  object-fit: cover;
}

.brand-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}

.brand-copy strong {
  color: #2e2925;
  font-size: 17px;
  font-weight: 750;
}

.brand-copy span {
  color: #978c82;
  font-size: 12px;
}

.nav-links {
  display: flex;
  gap: 10px;
}

.back-btn {
  min-height: 39px;
  padding: 0 13px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.back-btn {
  border: 1px solid #fed7aa;
  color: #c2410c;
  background: #fff8f1;
}

.back-btn:hover {
  border-color: #fb923c;
  background: #fff1e6;
}

.profile-main {
  padding: 25px 0 48px;
}

.profile-hero {
  padding: 28px 31px;
  border: 1px solid #f0dcc9;
  border-radius: 24px;
  background:
    radial-gradient(
      circle at 88% 16%,
      rgba(251, 146, 60, 0.16),
      transparent 30%
    ),
    linear-gradient(135deg, #fffaf4, #fff1e5);
  box-shadow: 0 16px 38px rgba(111, 75, 43, 0.08);
}

.profile-identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 18px;
}

.avatar {
  display: grid;
  width: 70px;
  height: 70px;
  flex: 0 0 70px;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 21px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 10px 24px rgba(125, 77, 34, 0.08);
}

.avatar-icon {
  font-size: 33px;
}

.user-details {
  min-width: 0;
}

.user-details h1 {
  margin: 0 0 8px;
  overflow: hidden;
  color: #2d2722;
  font-size: 29px;
  font-weight: 800;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: #83786f;
  font-size: 13px;
}

.role-badge {
  padding: 4px 9px;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  color: #c2410c;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.7);
}

.meta-divider {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #c5b9af;
}

.content-section,
.dashboard-grid {
  margin-top: 24px;
}

.section-heading h2,
.card-heading h2 {
  margin: 0;
  color: #342e29;
  font-size: 20px;
  line-height: 1.4;
}

.section-heading {
  margin-bottom: 13px;
}

.content-grid {
  display: grid;
  grid-template-columns: 1.3fr 1fr 1fr;
  gap: 13px;
}

.content-card {
  display: flex;
  min-width: 0;
  min-height: 88px;
  align-items: center;
  gap: 13px;
  padding: 16px 17px;
  border: 1px solid #e8e1da;
  border-radius: 17px;
  color: #39322d;
  text-align: left;
  background: rgba(255, 255, 255, 0.94);
  cursor: pointer;
  transition:
    transform 0.2s,
    border-color 0.2s,
    box-shadow 0.2s;
}

.content-card:hover {
  transform: translateY(-2px);
  border-color: #fdba74;
  box-shadow: 0 11px 24px rgba(90, 63, 39, 0.08);
}

.review-card {
  border-color: #fdba74;
  background: linear-gradient(135deg, #fffaf4, #fff2e6);
}

.content-icon {
  display: grid;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  place-items: center;
  border-radius: 13px;
  font-size: 21px;
  background: #fff3e8;
}

.content-title {
  min-width: 0;
  flex: 1 1 auto;
  color: #37302b;
  font-size: 17px;
  font-weight: 700;
}

.content-end {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
}

.content-arrow {
  flex: 0 0 auto;
  color: #c2410c;
  font-size: 18px;
}

.unread-badge {
  display: grid;
  min-width: 23px;
  height: 23px;
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 15px;
}

.dashboard-card {
  min-width: 0;
  padding: 21px;
  border: 1px solid #e9e2db;
  border-radius: 19px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 7px 21px rgba(80, 61, 43, 0.04);
}

.card-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  border-bottom: 1px solid #eee8e1;
}

.heading-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  place-items: center;
  border-radius: 12px;
  font-size: 18px;
  background: #fff3e8;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 15px;
}

.info-item {
  display: flex;
  min-width: 0;
  min-height: 70px;
  justify-content: center;
  flex-direction: column;
  gap: 5px;
  padding: 12px 13px;
  border: 1px solid #eee8e1;
  border-radius: 11px;
  background: #faf8f5;
}

.info-label {
  color: #988d84;
  font-size: 12px;
}

.info-item strong {
  color: #3d3630;
  font-size: 14px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.setting-list {
  margin-top: 4px;
}

.setting-item {
  display: flex;
  width: 100%;
  min-height: 50px;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  border-bottom: 1px solid #f0ebe6;
  color: #463e37;
  text-align: left;
  background: transparent;
  cursor: default;
}

.setting-item:last-child {
  border-bottom: 0;
}

.setting-icon {
  display: grid;
  width: 31px;
  height: 31px;
  flex: 0 0 31px;
  place-items: center;
  border-radius: 9px;
  font-size: 15px;
  background: #faf5f0;
}

.setting-name {
  flex: 1 1 auto;
  font-size: 14px;
  font-weight: 600;
}

.pending-tag {
  flex: 0 0 auto;
  padding: 3px 7px;
  border-radius: 999px;
  color: #978c82;
  font-size: 11px;
  font-weight: 600;
  background: #f5f2ee;
}

@media (max-width: 820px) {
  .content-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 700px) {
  .nav-container,
  .container {
    width: calc(100% - 32px);
  }

  .profile-main {
    padding-top: 17px;
  }
}

@media (max-width: 520px) {
  .brand-copy span {
    display: none;
  }

  .back-btn {
    padding: 0 10px;
    font-size: 13px;
  }

  .profile-hero {
    padding: 22px 18px;
  }

  .avatar {
    width: 56px;
    height: 56px;
    flex-basis: 56px;
    border-radius: 17px;
  }

  .avatar-icon {
    font-size: 27px;
  }

  .user-details h1 {
    font-size: 24px;
  }

  .meta-divider {
    display: none;
  }

  .content-card {
    min-height: 76px;
  }

  .dashboard-card {
    padding: 18px;
  }

  .info-grid {
    grid-template-columns: 1fr;
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
}
</style>
