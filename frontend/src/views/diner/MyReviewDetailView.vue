<template>
  <div class="review-detail-view">
    <nav class="diner-nav">
      <div class="nav-container">
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋 - 评价详情</span>
        </div>
        <div class="nav-links">
          <button class="back-btn" @click="goBack">← 返回我的评价</button>
          <button class="logout-btn" @click="handleLogout">退出登录</button>
        </div>
      </div>
    </nav>

    <main class="detail-main">
      <div class="container" v-if="review">
        <div class="detail-card">
          <div class="detail-header">
            <div class="merchant-section">
              <h2 class="merchant-name">{{ review.merchantName }}</h2>
              <div class="merchant-tags">
                <span class="tag">{{ review.merchantCategory }}</span>
                <span class="tag">{{ review.merchantCuisine }}</span>
              </div>
            </div>
            <span class="status-badge" :class="review.status">
              {{ review.statusText }}
            </span>
          </div>

          <div class="rating-section">
            <div class="overall-rating">
              <span class="rating-value">{{ review.rating }}</span>
              <div class="rating-stars">
                <span v-for="i in 5" :key="i" class="star">
                  {{ i <= Math.round(review.rating || 0) ? '⭐' : '☆' }}
                </span>
              </div>
            </div>
            <div class="sub-ratings" v-if="hasSubRatings">
              <div class="sub-rating">
                <span class="sub-label">口味</span>
                <span class="sub-stars">{{ getStars(review.tasteRating) }}</span>
                <span class="sub-value">{{ review.tasteRating || '-' }}</span>
              </div>
              <div class="sub-rating">
                <span class="sub-label">环境</span>
                <span class="sub-stars">{{ getStars(review.environmentRating) }}</span>
                <span class="sub-value">{{ review.environmentRating || '-' }}</span>
              </div>
              <div class="sub-rating">
                <span class="sub-label">服务</span>
                <span class="sub-stars">{{ getStars(review.serviceRating) }}</span>
                <span class="sub-value">{{ review.serviceRating || '-' }}</span>
              </div>
            </div>
          </div>

          <div class="info-row">
            <div v-if="review.averageSpend" class="info-item">
              <span class="info-label">人均消费</span>
              <span class="info-value">¥{{ review.averageSpend }}</span>
            </div>
            <div v-if="review.consumptionDate" class="info-item">
              <span class="info-label">消费日期</span>
              <span class="info-value">{{ formatDate(review.consumptionDate) }}</span>
            </div>
            <div class="info-item">
              <span class="info-label">发布时间</span>
              <span class="info-value">{{ formatDateTime(review.createdAt) }}</span>
            </div>
            <div v-if="review.editedAt" class="info-item">
              <span class="info-label">最后编辑</span>
              <span class="info-value">{{ formatDateTime(review.editedAt) }}</span>
            </div>
          </div>

          <div class="content-section">
            <h3 class="section-title">评价内容</h3>
            <p class="content-text">{{ review.content }}</p>
          </div>

          <div v-if="review.images && review.images.length > 0" class="images-section">
            <h3 class="section-title">评价图片</h3>
            <div class="images-grid">
              <img
                v-for="img in review.images"
                :key="img.id"
                :src="img.imageUrl"
                class="review-image"
                :alt="'评价图片' + img.id"
              />
            </div>
          </div>

          <div v-if="review.merchantReply" class="reply-section">
            <h3 class="section-title">商家回复</h3>
            <div class="reply-card">
              <div class="reply-header">
                <span class="reply-merchant">{{ review.merchantReply.merchantName }}</span>
                <span class="reply-time">{{ formatDateTime(review.merchantReply.replyTime) }}</span>
              </div>
              <p class="reply-content">{{ review.merchantReply.replyContent }}</p>
            </div>
          </div>

          <div v-if="review.versionHistory && review.versionHistory.length > 0" class="history-section">
            <h3 class="section-title">编辑历史</h3>
            <div class="history-list">
              <div
                v-for="ver in review.versionHistory"
                :key="ver.version"
                class="history-item"
              >
                <div class="history-header">
                  <span class="version-label">版本 {{ ver.version }}</span>
                  <span class="change-type" :class="ver.changeType">
                    {{ getChangeTypeText(ver.changeType) }}
                  </span>
                  <span class="history-time">{{ formatDateTime(ver.createdAt) }}</span>
                </div>
                <div class="history-content">
                  <div v-if="ver.rating" class="history-rating">
                    评分: {{ ver.rating }}分 {{ getStars(ver.rating) }}
                  </div>
                  <p class="history-text">{{ ver.content }}</p>
                </div>
              </div>
            </div>
          </div>

          <div class="action-section">
            <button
              v-if="review.status !== 'DELETED'"
              class="action-btn primary"
              @click="goToEdit"
            >
              ✏️ 编辑评价
            </button>
            <button
              v-if="review.status !== 'DELETED'"
              class="action-btn danger"
              @click="handleDelete"
            >
              🗑️ 删除评价
            </button>
            <div v-if="review.status === 'DELETED'" class="deleted-hint">
              已删除的评价仅可查看，无法编辑
            </div>
          </div>
        </div>
      </div>

      <div v-else class="loading-state">
        <span class="loading-icon">⏳</span>
        <p>加载中...</p>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const route = useRoute()
const review = ref(null)

const hasSubRatings = computed(() => {
  return review.value && (review.value.tasteRating || review.value.environmentRating || review.value.serviceRating)
})

const getStars = (rating) => {
  if (!rating) return ''
  const stars = Math.round(rating)
  return '⭐'.repeat(stars) + '☆'.repeat(5 - stars)
}

const getChangeTypeText = (type) => {
  if (!type) return '编辑'
  return type === 'CREATE' ? '创建' : type === 'EDIT' ? '编辑' : type === 'DELETE' ? '删除' : '修改'
}

const loadReview = async () => {
  const reviewId = route.params.id
  if (!reviewId) return

  try {
    const response = await request.get(`/api/reviews/my-reviews/${reviewId}`)
    if (response.success && response.data) {
      review.value = response.data
    } else {
      console.error('获取评价详情失败:', response.message)
      alert(response.message || '获取评价详情失败')
      goBack()
    }
  } catch (error) {
    console.error('获取评价详情失败:', error)
    if (error.status === 401 || error.code === 401) {
      router.push('/diner')
    } else if (error.status === 404 || error.code === 404) {
      alert('评价不存在')
      goBack()
    } else if (error.status === 403 || error.code === 403) {
      alert('无权查看此评价')
      goBack()
    } else {
      alert(error.message || '获取评价详情失败')
    }
  }
}

const goBack = () => {
  router.push('/diner/my-reviews')
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  router.push('/diner')
}

const goToEdit = () => {
  router.push(`/diner/review/edit/${route.params.id}`)
}

const handleDelete = () => {
  if (!confirm('确定要删除这条评价吗？删除后将无法恢复。')) return

  request.delete(`/api/reviews/${route.params.id}`)
    .then(() => {
      alert('评价已删除')
      goBack()
    })
    .catch(error => {
      console.error('删除评价失败:', error)
      alert('删除失败，请重试')
    })
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(() => {
  loadReview()
})
</script>

<style scoped>
.review-detail-view {
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

.detail-main {
  padding-top: 80px;
  padding-bottom: 40px;
}

.detail-card {
  background: #fff;
  border-radius: 16px;
  padding: 30px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.merchant-section {
  flex: 1;
}

.merchant-name {
  font-size: 24px;
  font-weight: 700;
  color: #333;
  margin: 0 0 8px 0;
}

.merchant-tags {
  display: flex;
  gap: 8px;
}

.tag {
  padding: 4px 12px;
  background: #f5f5f5;
  border-radius: 4px;
  font-size: 13px;
  color: #666;
}

.status-badge {
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

.status-badge.PUBLISHED {
  background: #f6ffed;
  color: #52c41a;
}

.status-badge.DELETED {
  background: #fff2f0;
  color: #ff4d4f;
}

.status-badge.PENDING {
  background: #fffbe6;
  color: #faad14;
}

.rating-section {
  display: flex;
  align-items: center;
  gap: 40px;
  margin-bottom: 24px;
  padding: 20px;
  background: #fafafa;
  border-radius: 12px;
}

.overall-rating {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.rating-value {
  font-size: 48px;
  font-weight: 700;
  color: #ff6700;
}

.rating-stars {
  font-size: 24px;
}

.sub-ratings {
  display: flex;
  gap: 24px;
}

.sub-rating {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.sub-label {
  font-size: 13px;
  color: #999;
}

.sub-stars {
  font-size: 16px;
}

.sub-value {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.info-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  margin-bottom: 24px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
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

.content-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.content-text {
  font-size: 16px;
  color: #333;
  line-height: 1.8;
  margin: 0;
  white-space: pre-wrap;
}

.images-section {
  margin-bottom: 24px;
}

.images-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}

.review-image {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: 8px;
}

.reply-section {
  margin-bottom: 24px;
}

.reply-card {
  padding: 20px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 12px;
  border-left: 4px solid #1890ff;
}

.reply-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.reply-merchant {
  font-size: 15px;
  font-weight: 600;
  color: #1890ff;
}

.reply-time {
  font-size: 13px;
  color: #999;
}

.reply-content {
  font-size: 15px;
  color: #333;
  line-height: 1.7;
  margin: 0;
}

.history-section {
  margin-bottom: 24px;
  padding: 20px;
  background: #fafafa;
  border-radius: 12px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.history-item {
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  border-left: 4px solid #ff6700;
}

.history-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.version-label {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.change-type {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.change-type.CREATE {
  background: #f6ffed;
  color: #52c41a;
}

.change-type.EDIT {
  background: #e6f7ff;
  color: #1890ff;
}

.change-type.DELETE {
  background: #fff2f0;
  color: #ff4d4f;
}

.history-time {
  font-size: 13px;
  color: #999;
  margin-left: auto;
}

.history-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-rating {
  font-size: 14px;
  color: #ff6700;
}

.history-text {
  font-size: 14px;
  color: #666;
  line-height: 1.6;
  margin: 0;
}

.action-section {
  display: flex;
  gap: 16px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.action-btn {
  flex: 1;
  padding: 14px 20px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.2s;
}

.action-btn.primary {
  background: #ff6700;
  color: #fff;
}

.action-btn.primary:hover {
  background: #e55a00;
}

.action-btn.danger {
  background: #fff;
  color: #ff4d4f;
  border: 1px solid #ffccc7;
}

.action-btn.danger:hover {
  background: #fff2f0;
}

.deleted-hint {
  flex: 1;
  text-align: center;
  padding: 14px;
  font-size: 15px;
  color: #999;
}

.loading-state {
  text-align: center;
  padding: 100px 20px;
}

.loading-icon {
  font-size: 48px;
  display: block;
  margin-bottom: 16px;
}

.loading-state p {
  font-size: 16px;
  color: #999;
}

@media (max-width: 768px) {
  .detail-header {
    flex-direction: column;
    gap: 12px;
  }
  
  .rating-section {
    flex-direction: column;
    gap: 20px;
  }
  
  .sub-ratings {
    gap: 16px;
  }
  
  .info-row {
    gap: 16px;
  }
  
  .action-section {
    flex-direction: column;
  }
}
</style>