<template>
  <div class="my-reviews-view">
    <nav class="diner-nav">
      <div class="nav-container">
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋 - 我的评价</span>
        </div>
        <div class="nav-links">
          <button class="back-btn" @click="goBack">← 返回个人中心</button>
          <button class="logout-btn" @click="handleLogout">退出登录</button>
        </div>
      </div>
    </nav>

    <main class="reviews-main">
      <div class="container">
        <div class="filter-section">
          <div class="filter-row">
            <div class="filter-group">
              <span class="filter-label">状态筛选</span>
              <div class="filter-options">
                <button
                  v-for="opt in statusOptions"
                  :key="opt.value"
                  class="filter-btn"
                  :class="{ active: selectedStatus === opt.value }"
                  @click="selectedStatus = opt.value"
                >
                  {{ opt.label }}
                </button>
              </div>
            </div>
            <div class="filter-group">
              <span class="filter-label">评分筛选</span>
              <div class="filter-options">
                <button
                  v-for="star in [0, 1, 2, 3, 4, 5]"
                  :key="star"
                  class="filter-btn star-btn"
                  :class="{ active: selectedRating === star }"
                  @click="selectedRating = star"
                >
                  {{ star === 0 ? '全部' : '⭐'.repeat(star) }}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="stats-bar">
          <span class="stats-text">共 {{ total }} 条评价</span>
        </div>

        <div class="reviews-list">
          <div
            v-for="review in reviews"
            :key="review.id"
            class="review-item"
            @click="goToDetail(review.id)"
          >
            <div class="review-header">
              <div class="merchant-info">
                <h3 class="merchant-name">{{ review.merchantName }}</h3>
                <div class="rating-stars">
                  <span v-for="i in 5" :key="i" class="star">
                    {{ i <= Math.round(review.rating || 0) ? '⭐' : '☆' }}
                  </span>
                </div>
              </div>
              <span class="status-tag" :class="review.status">
                {{ review.statusText }}
              </span>
            </div>
            <p class="review-content">{{ review.contentSummary }}</p>
            <div class="review-footer">
              <span class="time-text">{{ formatDate(review.createdAt) }}</span>
              <span v-if="review.currentVersion && review.currentVersion > 1" class="version-text">
                已编辑 {{ review.currentVersion - 1 }} 次
              </span>
              <span class="arrow-icon">→</span>
            </div>
          </div>

          <div v-if="reviews.length === 0" class="empty-state">
            <div class="empty-icon-wrapper">
              <span class="empty-icon">📝</span>
            </div>
            <h3 class="empty-title">暂无评价记录</h3>
            <p class="empty-desc">
              {{ selectedRating > 0 ? `没有找到${selectedRating}星评价` : selectedStatus ? `当前筛选条件下没有评价` : '快去发现美味，写下你的第一条评价吧~' }}
            </p>
            <button v-if="!selectedStatus && !selectedRating" class="empty-btn" @click="goBack">去逛逛</button>
          </div>
        </div>

        <div v-if="total > pageSize" class="pagination">
          <button
            class="page-btn"
            :disabled="pageNum <= 1"
            @click="pageNum--"
          >
            ← 上一页
          </button>
          <span class="page-info">第 {{ pageNum }} / {{ totalPages }} 页</span>
          <button
            class="page-btn"
            :disabled="pageNum >= totalPages"
            @click="pageNum++"
          >
            下一页 →
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const reviews = ref([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedStatus = ref('')
const selectedRating = ref(0)

const statusOptions = [
  { label: '全部', value: '' },
  { label: '正常', value: 'PUBLISHED' },
  { label: '已删除', value: 'DELETED' },
  { label: '待审核', value: 'PENDING' }
]

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

const loadReviews = async () => {
  try {
    const response = await request.get('/api/reviews/my-reviews', {
      params: {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        status: selectedStatus.value || undefined,
        rating: selectedRating.value > 0 ? selectedRating.value : undefined
      }
    })

    if (response.success && response.data) {
      reviews.value = response.data.records || []
      total.value = response.data.total || 0
    } else {
      console.error('获取评价列表失败:', response.message)
      reviews.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('获取评价列表失败:', error)
    reviews.value = []
    total.value = 0
    if (error.status === 401 || error.code === 401) {
      router.push('/diner')
    }
  }
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

const goToDetail = (id) => {
  router.push(`/diner/my-reviews/${id}`)
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

watch([selectedStatus, selectedRating], () => {
  pageNum.value = 1
  loadReviews()
})

watch(pageNum, () => {
  loadReviews()
})

onMounted(() => {
  loadReviews()
})
</script>

<style scoped>
.my-reviews-view {
  min-height: 100vh;
  background: #f5f7fa;
}

.container {
  max-width: 1000px;
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
  max-width: 1000px;
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

.reviews-main {
  padding-top: 80px;
  padding-bottom: 40px;
}

.filter-section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-label {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.filter-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-btn {
  padding: 8px 16px;
  background: #f5f5f5;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.filter-btn:hover {
  border-color: #ff6700;
  color: #ff6700;
}

.filter-btn.active {
  background: #ff6700;
  border-color: #ff6700;
  color: #fff;
}

.star-btn {
  padding: 8px 12px;
}

.stats-bar {
  margin-bottom: 16px;
}

.stats-text {
  font-size: 15px;
  color: #666;
}

.reviews-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-item {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: all 0.2s;
}

.review-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.merchant-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.merchant-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.rating-stars {
  font-size: 16px;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
}

.status-tag.PUBLISHED {
  background: #f6ffed;
  color: #52c41a;
}

.status-tag.DELETED {
  background: #fff2f0;
  color: #ff4d4f;
}

.status-tag.PENDING {
  background: #fffbe6;
  color: #faad14;
}

.status-tag.HIDDEN {
  background: #f5f5f5;
  color: #999;
}

.review-content {
  font-size: 15px;
  color: #666;
  line-height: 1.6;
  margin: 0 0 12px 0;
}

.review-footer {
  display: flex;
  align-items: center;
  gap: 16px;
}

.time-text {
  font-size: 13px;
  color: #999;
}

.version-text {
  font-size: 13px;
  color: #ff6700;
}

.arrow-icon {
  margin-left: auto;
  font-size: 18px;
  color: #ccc;
}

.review-item:hover .arrow-icon {
  color: #ff6700;
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
  background: linear-gradient(135deg, #fff5f0 0%, #fff0e6 100%);
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
  margin: 0 0 24px 0;
  line-height: 1.6;
}

.empty-btn {
  padding: 12px 32px;
  background: #ff6700;
  color: #fff;
  border: none;
  border-radius: 25px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.empty-btn:hover {
  background: #e55a00;
  transform: translateY(-2px);
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 30px;
}

.page-btn {
  padding: 10px 20px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  font-size: 15px;
  cursor: pointer;
}

.page-btn:hover:not(:disabled) {
  border-color: #ff6700;
  color: #ff6700;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 15px;
  color: #666;
}

@media (max-width: 768px) {
  .filter-row {
    gap: 16px;
  }
  
  .review-header {
    flex-direction: column;
    gap: 8px;
  }
  
  .status-tag {
    align-self: flex-start;
  }
}
</style>