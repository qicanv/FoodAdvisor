<template>
  <div class="notification-view">
    <nav class="diner-nav">
      <div class="nav-container">
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋 - 消息中心</span>
        </div>
        <div class="nav-links">
          <button class="back-btn" @click="goBack">← 返回个人中心</button>
          <button class="logout-btn" @click="handleLogout">退出登录</button>
        </div>
      </div>
    </nav>

    <main class="notification-main">
      <div class="container">
        <div class="header-section">
          <h1 class="page-title">消息中心</h1>
          <div class="header-actions">
            <span class="unread-count" v-if="unreadCount > 0">{{ unreadCount }} 条未读</span>
            <button v-if="notifications.length > 0" class="mark-all-btn" @click="markAllAsRead">
              全部标记为已读
            </button>
          </div>
        </div>

        <div class="notification-list">
          <div
            v-for="notification in notifications"
            :key="notification.id"
            class="notification-item"
            :class="{ unread: notification.status === 'UNREAD' }"
            @click="goToReview(notification)"
          >
            <div class="notification-icon">
              <span v-if="notification.status === 'UNREAD'" class="unread-dot"></span>
              💬
            </div>
            <div class="notification-content">
              <div class="notification-header">
                <span class="merchant-name">{{ notification.merchantName }}</span>
                <span class="notification-time">{{ formatDateTime(notification.createdAt) }}</span>
              </div>
              <p class="review-summary">
                <span class="label">评价：</span>{{ notification.reviewSummary }}
              </p>
              <p class="reply-summary">
                <span class="label">回复：</span>{{ notification.replySummary }}
              </p>
            </div>
            <div class="notification-actions">
              <button class="action-btn" @click.stop="markAsRead(notification.id)">
                {{ notification.status === 'UNREAD' ? '标为已读' : '已读' }}
              </button>
              <button class="action-btn unsubscribe" @click.stop="handleUnsubscribe(notification)">
                不再接收
              </button>
            </div>
          </div>

          <div v-if="notifications.length === 0" class="empty-state">
            <div class="empty-icon-wrapper">
              <span class="empty-icon">📭</span>
            </div>
            <h3 class="empty-title">暂无消息</h3>
            <p class="empty-desc">暂无商家回复通知，快去发表评价吧~</p>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const notifications = ref([])
const unreadCount = ref(0)

const loadNotifications = async () => {
  try {
    const response = await request.get('/api/notifications')
    if (response.success && response.data) {
      notifications.value = response.data
      unreadCount.value = notifications.value.filter(n => n.status === 'UNREAD').length
    } else {
      notifications.value = []
      unreadCount.value = 0
    }
  } catch (error) {
    console.error('获取通知列表失败:', error)
    notifications.value = []
    unreadCount.value = 0
    if (error.status === 401 || error.code === 401) {
      router.push('/diner')
    }
  }
}

const markAsRead = async (notificationId) => {
  try {
    await request.put(`/api/notifications/${notificationId}/read`)
    loadNotifications()
  } catch (error) {
    console.error('标记已读失败:', error)
  }
}

const markAllAsRead = async () => {
  try {
    await request.put('/api/notifications/read-all')
    loadNotifications()
  } catch (error) {
    console.error('标记全部已读失败:', error)
  }
}

const handleUnsubscribe = async (notification) => {
  if (confirm('确定不再接收该评价的回复提醒吗？')) {
    try {
      await request.put(`/api/notifications/review/${notification.reviewId}/disable`)
      loadNotifications()
      alert('已关闭该评价的回复通知')
    } catch (error) {
      console.error('关闭通知失败:', error)
      alert('关闭通知失败，请重试')
    }
  }
}

const goToReview = (notification) => {
  markAsRead(notification.id)
  router.push(`/diner/my-reviews/${notification.reviewId}`)
}

const goBack = () => {
  router.push('/diner/profile')
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  router.push('/diner')
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(() => {
  loadNotifications()
})
</script>

<style scoped>
.notification-view {
  min-height: 100vh;
  background: #f5f7fa;
}

.container {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 24px;
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
  max-width: 800px;
  margin: 0 auto;
  padding: 0 24px;
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
  gap: 16px;
}

.back-btn {
  padding: 8px 16px;
  background: #fff5f0;
  color: #ff6700;
  border: 1px solid #ffccb3;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.logout-btn {
  padding: 8px 16px;
  background: #fff;
  color: #ff4d4f;
  border: 1px solid #ffccc7;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.notification-main {
  padding-top: 80px;
  padding-bottom: 40px;
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #333;
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.unread-count {
  padding: 6px 12px;
  background: #ff4d4f;
  color: #fff;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 500;
}

.mark-all-btn {
  padding: 8px 16px;
  background: #1890ff;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.mark-all-btn:hover {
  background: #1677ff;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.notification-item {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  display: flex;
  gap: 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.notification-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.notification-item.unread {
  border-left: 4px solid #ff6700;
}

.notification-icon {
  font-size: 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.unread-dot {
  width: 8px;
  height: 8px;
  background: #ff6700;
  border-radius: 50%;
}

.notification-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.merchant-name {
  font-size: 16px;
  font-weight: 600;
  color: #ff6700;
}

.notification-time {
  font-size: 13px;
  color: #999;
}

.review-summary,
.reply-summary {
  font-size: 14px;
  color: #666;
  margin: 0;
  line-height: 1.6;
}

.review-summary .label,
.reply-summary .label {
  color: #999;
}

.notification-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-btn {
  padding: 6px 12px;
  background: #f5f5f5;
  color: #666;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.action-btn:hover {
  background: #fff5f0;
  color: #ff6700;
  border-color: #ffccb3;
}

.action-btn.unsubscribe {
  background: #fff2f0;
  color: #ff4d4f;
  border-color: #ffccc7;
}

.action-btn.unsubscribe:hover {
  background: #ffccc7;
}

.empty-state {
  text-align: center;
  padding: 80px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}

.empty-icon-wrapper {
  width: 100px;
  height: 100px;
  margin: 0 auto 24px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-icon {
  font-size: 48px;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.empty-desc {
  font-size: 15px;
  color: #999;
  margin: 0;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .notification-item {
    flex-direction: column;
  }

  .notification-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .notification-actions {
    flex-direction: row;
    justify-content: flex-end;
  }
}
</style>