<template>
  <div class="my-reviews-view">
    <nav class="diner-nav">
      <div class="nav-container">
        <button
          type="button"
          class="brand-button"
          @click="goToHome"
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
            <small>我的评价</small>
          </span>
        </button>

        <div class="nav-links">
          <button
            type="button"
            class="back-btn"
            @click="goBack"
          >
            <span>←</span>
            返回个人中心
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

    <main class="reviews-main">
      <div class="container">
        <section class="reviews-hero">
          <div class="hero-copy">
            <span class="hero-eyebrow">MY REVIEWS</span>
            <h1>我的评价</h1>
            <p>
              查看你发表过的商家评价，了解审核状态、商家回复和编辑记录。
            </p>
          </div>

          <div class="hero-summary">
            <span class="summary-label">评价总数</span>
            <strong>{{ total }}</strong>
            <small>条评价记录</small>
          </div>
        </section>

        <section class="filter-section">
          <div class="filter-heading">
            <div>
              <span class="section-eyebrow">FILTER</span>
              <h2>筛选评价</h2>
            </div>

            <button
              v-if="selectedStatus || selectedRating"
              type="button"
              class="clear-filter-btn"
              @click="clearFilters"
            >
              清除筛选
            </button>
          </div>

          <div class="filter-row">
            <div class="filter-group">
              <span class="filter-label">状态</span>

              <div class="filter-options">
                <button
                  v-for="opt in statusOptions"
                  :key="opt.value"
                  type="button"
                  class="filter-btn"
                  :class="{ active: selectedStatus === opt.value }"
                  @click="selectedStatus = opt.value"
                >
                  {{ opt.label }}
                </button>
              </div>
            </div>

            <div class="filter-group">
              <span class="filter-label">评分</span>

              <div class="filter-options rating-options">
                <button
                  v-for="star in [0, 1, 2, 3, 4, 5]"
                  :key="star"
                  type="button"
                  class="filter-btn star-btn"
                  :class="{ active: selectedRating === star }"
                  @click="selectedRating = star"
                >
                  <span v-if="star === 0">全部</span>
                  <span v-else>
                    {{ star }}
                    <span class="star-symbol">★</span>
                  </span>
                </button>
              </div>
            </div>
          </div>
        </section>

        <section class="result-section">
          <div class="result-heading">
            <div>
              <span class="section-eyebrow">RESULTS</span>
              <h2>评价记录</h2>
            </div>

            <span class="result-count">
              共 {{ total }} 条
            </span>
          </div>

          <div v-if="loading" class="loading-state">
            <span class="loading-spinner"></span>
            <p>正在加载评价记录...</p>
          </div>

          <div
            v-else-if="reviews.length > 0"
            class="reviews-list"
          >
            <article
              v-for="review in reviews"
              :key="review.id"
              class="review-item"
              tabindex="0"
              @click="goToDetail(review.id)"
              @keydown.enter="goToDetail(review.id)"
            >
              <div class="review-main">
                <div class="review-header">
                  <div class="merchant-info">
                    <div class="merchant-title-row">
                      <span class="merchant-icon">🍽️</span>
                      <h3 class="merchant-name">
                        {{ review.merchantName || '未知商家' }}
                      </h3>
                    </div>

                    <div class="rating-row">
                      <span class="rating-stars">
                        <span
                          v-for="i in 5"
                          :key="i"
                          class="star"
                          :class="{ filled: i <= Math.round(review.rating || 0) }"
                        >
                          ★
                        </span>
                      </span>

                      <span class="rating-value">
                        {{ Number(review.rating || 0).toFixed(1) }}
                      </span>
                    </div>
                  </div>

                  <span
                    class="status-tag"
                    :class="review.status"
                  >
                    {{ review.statusText || getStatusText(review.status) }}
                  </span>
                </div>

                <p class="review-content">
                  {{ review.contentSummary || '暂无评价内容' }}
                </p>

                <div class="review-meta">
                  <span class="meta-item">
                    <span>🕒</span>
                    {{ formatDate(review.createdAt) }}
                  </span>

                  <span
                    v-if="review.currentVersion && review.currentVersion > 1"
                    class="meta-item version-text"
                  >
                    <span>✏️</span>
                    已编辑 {{ review.currentVersion - 1 }} 次
                  </span>

                  <span
                    v-if="review.hasReply"
                    class="meta-item reply-badge"
                  >
                    <span>💬</span>
                    商家已回复
                  </span>
                </div>

                <div
                  v-if="review.followUp"
                  class="follow-up-preview"
                >
                  <div class="follow-up-preview-header">
                    <div class="follow-up-title">
                      <span class="follow-up-label">追加评价</span>

                      <span
                        v-if="review.followUp.rating"
                        class="follow-up-score"
                      >
                        {{ Number(review.followUp.rating).toFixed(1) }} ★
                      </span>
                    </div>

                    <span
                      class="status-tag"
                      :class="review.followUp.status"
                    >
                      {{ getStatusText(review.followUp.status) }}
                    </span>
                  </div>

                  <p class="follow-up-preview-content">
                    {{ review.followUp.content || '暂无追评内容' }}
                  </p>

                  <div class="follow-up-preview-meta">
                    <span>
                      🕒
                      {{
                        formatDate(
                          review.followUp.publishedAt ||
                          review.followUp.createdAt
                        )
                      }}
                    </span>

                    <span v-if="review.followUp.consumptionDate">
                      🍽️ 本次消费：{{ review.followUp.consumptionDate }}
                    </span>

                    <span
                      v-if="
                        review.followUp.averageSpend !== null &&
                        review.followUp.averageSpend !== undefined
                      "
                    >
                      💰 人均 ¥{{ Number(review.followUp.averageSpend).toFixed(0) }}
                    </span>
                  </div>
                </div>
              </div>

              <span class="detail-entry">
                查看详情
                <span class="arrow-icon">→</span>
              </span>
            </article>
          </div>

          <div v-else class="empty-state">
            <div class="empty-icon-wrapper">
              <span class="empty-icon">📝</span>
            </div>

            <h3 class="empty-title">暂无评价记录</h3>

            <p class="empty-desc">
              {{
                selectedRating > 0
                  ? `没有找到 ${selectedRating} 星评价`
                  : selectedStatus
                    ? '当前筛选条件下没有评价'
                    : '快去发现美味，写下你的第一条评价吧'
              }}
            </p>

            <div class="empty-actions">
              <button
                v-if="selectedStatus || selectedRating"
                type="button"
                class="secondary-empty-btn"
                @click="clearFilters"
              >
                清除筛选
              </button>

              <button
                type="button"
                class="empty-btn"
                @click="goToHome"
              >
                去首页逛逛
              </button>
            </div>
          </div>

          <div
            v-if="!loading && total > pageSize"
            class="pagination"
          >
            <button
              type="button"
              class="page-btn"
              :disabled="pageNum <= 1"
              @click="pageNum--"
            >
              ← 上一页
            </button>

            <span class="page-info">
              第 {{ pageNum }} / {{ totalPages }} 页
            </span>

            <button
              type="button"
              class="page-btn"
              :disabled="pageNum >= totalPages"
              @click="pageNum++"
            >
              下一页 →
            </button>
          </div>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'

const router = useRouter()

const reviews = ref([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const selectedStatus = ref('')
const selectedRating = ref(0)
const loading = ref(false)

const statusOptions = [
  { label: '全部', value: '' },
  { label: '正常', value: 'PUBLISHED' },
  { label: '已删除', value: 'DELETED' },
  { label: '待审核', value: 'PENDING' }
]

const totalPages = computed(() => {
  return Math.max(1, Math.ceil(total.value / pageSize.value))
})

const loadReviews = async () => {
  loading.value = true

  try {
    const response = await request.get('/api/reviews/my-reviews', {
      params: {
        pageNum: pageNum.value,
        pageSize: pageSize.value,
        status: selectedStatus.value || undefined,
        rating:
          selectedRating.value > 0
            ? selectedRating.value
            : undefined
      }
    })

    if (response.success && response.data) {
      reviews.value = response.data.records || []
      total.value = response.data.total || 0
      return
    }

    console.error('获取评价列表失败:', response.message)
    reviews.value = []
    total.value = 0
  } catch (error) {
    console.error('获取评价列表失败:', error)
    reviews.value = []
    total.value = 0

    if (error.status === 401 || error.code === 401) {
      router.push('/diner')
    }
  } finally {
    loading.value = false
  }
}

const clearFilters = () => {
  selectedStatus.value = ''
  selectedRating.value = 0
}

const goBack = () => {
  router.push('/diner/profile')
}

const goToHome = () => {
  router.push('/diner/home')
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  router.push('/diner')
}

const goToDetail = id => {
  router.push(`/diner/my-reviews/${id}`)
}

const getStatusText = status => {
  const statusMap = {
    PUBLISHED: '正常',
    DELETED: '已删除',
    PENDING: '待审核',
    HIDDEN: '已隐藏'
  }

  return statusMap[status] || '未知状态'
}

const formatDate = dateStr => {
  if (!dateStr) return '-'

  const date = new Date(dateStr)

  if (Number.isNaN(date.getTime())) {
    return '-'
  }

  return [
    [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, '0'),
      String(date.getDate()).padStart(2, '0')
    ].join('-'),
    [
      String(date.getHours()).padStart(2, '0'),
      String(date.getMinutes()).padStart(2, '0')
    ].join(':')
  ].join(' ')
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
.my-reviews-view,
.my-reviews-view *,
.my-reviews-view *::before,
.my-reviews-view *::after {
  box-sizing: border-box;
}

.my-reviews-view {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 100vh;
  overflow-x: hidden;
  overflow-x: clip;
  color: #302a25;
  font-family:
    "Microsoft YaHei",
    "PingFang SC",
    "Noto Sans SC",
    Arial,
    sans-serif;
  background:
    radial-gradient(
      circle at 8% 4%,
      rgba(255, 230, 208, 0.62),
      transparent 26%
    ),
    radial-gradient(
      circle at 94% 2%,
      rgba(255, 244, 218, 0.68),
      transparent 23%
    ),
    #f8f6f2;
}

.my-reviews-view button {
  font-family: inherit;
}

.diner-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  width: 100%;
  border-bottom: 1px solid rgba(229, 222, 212, 0.86);
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(18px);
}

.nav-container,
.container {
  width: calc(100% - 48px);
  max-width: 1080px;
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
    transform 0.2s,
    border-color 0.2s,
    color 0.2s,
    background 0.2s;
}

.back-btn {
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

.reviews-main {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  padding: 24px 0 48px;
}

.reviews-hero {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 30px 34px;
  overflow: hidden;
  border: 1px solid #f1dcc8;
  border-radius: 26px;
  background:
    radial-gradient(
      circle at 88% 15%,
      rgba(251, 146, 60, 0.18),
      transparent 31%
    ),
    linear-gradient(135deg, #fffaf4 0%, #fff1e5 100%);
  box-shadow: 0 18px 44px rgba(111, 75, 43, 0.09);
}

.hero-copy {
  min-width: 0;
}

.hero-eyebrow,
.section-eyebrow {
  display: block;
  color: #ea580c;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.hero-copy h1 {
  margin: 6px 0 7px;
  color: #29231e;
  font-size: 34px;
  font-weight: 800;
  line-height: 1.25;
}

.hero-copy p {
  max-width: 620px;
  margin: 0;
  color: #786f66;
  font-size: 15px;
  line-height: 1.7;
}

.hero-summary {
  display: flex;
  min-width: 126px;
  flex: 0 0 auto;
  flex-direction: column;
  align-items: center;
  padding: 16px 20px;
  border: 1px solid rgba(255, 255, 255, 0.92);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: 0 10px 24px rgba(125, 77, 34, 0.08);
}

.summary-label {
  color: #998c81;
  font-size: 12px;
}

.hero-summary strong {
  margin: 3px 0;
  color: #ea580c;
  font-size: 31px;
  font-weight: 800;
  line-height: 1.2;
}

.hero-summary small {
  color: #8f8378;
  font-size: 12px;
}

.filter-section,
.result-section {
  width: 100%;
  min-width: 0;
  margin-top: 22px;
  padding: 22px;
  border: 1px solid #ebe4dd;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 7px 22px rgba(80, 61, 43, 0.05);
}

.filter-heading,
.result-heading {
  display: flex;
  min-width: 0;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 15px;
  border-bottom: 1px solid #eee8e1;
}

.filter-heading h2,
.result-heading h2 {
  margin: 4px 0 0;
  color: #322c27;
  font-size: 22px;
  line-height: 1.35;
}

.clear-filter-btn {
  flex: 0 0 auto;
  padding: 7px 11px;
  border: 1px solid #fed7aa;
  border-radius: 10px;
  color: #c2410c;
  font-size: 13px;
  font-weight: 600;
  background: #fff8f1;
  cursor: pointer;
}

.clear-filter-btn:hover {
  border-color: #fb923c;
  background: #fff1e6;
}

.filter-row {
  display: grid;
  width: 100%;
  min-width: 0;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 24px;
  margin-top: 18px;
}

.filter-group {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 9px;
}

.filter-label {
  color: #6f665e;
  font-size: 14px;
  font-weight: 700;
}

.filter-options {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-btn {
  min-height: 38px;
  padding: 0 14px;
  border: 1px solid #e8e1da;
  border-radius: 10px;
  color: #6d635b;
  font-size: 14px;
  font-weight: 600;
  background: #faf8f5;
  cursor: pointer;
  transition:
    transform 0.2s,
    border-color 0.2s,
    color 0.2s,
    background 0.2s;
}

.filter-btn:hover {
  transform: translateY(-1px);
  border-color: #fdba74;
  color: #c2410c;
}

.filter-btn.active {
  border-color: #ea580c;
  color: #fff;
  background: #ea580c;
  box-shadow: 0 7px 16px rgba(234, 88, 12, 0.18);
}

.star-btn {
  min-width: 56px;
  padding: 0 12px;
}

.star-symbol {
  color: #f59e0b;
}

.filter-btn.active .star-symbol {
  color: #fff7ed;
}

.result-count {
  flex: 0 0 auto;
  padding: 6px 10px;
  border: 1px solid #e9e2db;
  border-radius: 999px;
  color: #8c8178;
  font-size: 13px;
  background: #faf8f5;
}

.reviews-list {
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  gap: 13px;
  margin-top: 17px;
}

.review-item {
  display: flex;
  width: 100%;
  min-width: 0;
  align-items: center;
  gap: 18px;
  padding: 19px;
  border: 1px solid #ebe5df;
  border-radius: 17px;
  background: #fff;
  cursor: pointer;
  outline: none;
  transition:
    transform 0.22s,
    border-color 0.22s,
    box-shadow 0.22s;
}

.review-item:hover,
.review-item:focus-visible {
  transform: translateY(-2px);
  border-color: #fdba74;
  box-shadow: 0 12px 26px rgba(91, 66, 43, 0.09);
}

.review-main {
  min-width: 0;
  flex: 1 1 auto;
}

.review-header {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.merchant-info {
  min-width: 0;
}

.merchant-title-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 9px;
}

.merchant-icon {
  display: grid;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  place-items: center;
  border-radius: 10px;
  font-size: 17px;
  background: #fff3e8;
}

.merchant-name {
  min-width: 0;
  margin: 0;
  overflow: hidden;
  color: #322c27;
  font-size: 18px;
  font-weight: 750;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rating-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.rating-stars {
  display: flex;
  gap: 2px;
}

.star {
  color: #ddd6cf;
  font-size: 15px;
  line-height: 1;
}

.star.filled {
  color: #f59e0b;
}

.rating-value {
  color: #7c7168;
  font-size: 13px;
  font-weight: 700;
}

.status-tag {
  flex: 0 0 auto;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.status-tag.PUBLISHED {
  border: 1px solid #bbf7d0;
  color: #15803d;
  background: #f0fdf4;
}

.status-tag.DELETED {
  border: 1px solid #fecaca;
  color: #dc2626;
  background: #fef2f2;
}

.status-tag.PENDING {
  border: 1px solid #fde68a;
  color: #b45309;
  background: #fffbeb;
}

.status-tag.HIDDEN {
  border: 1px solid #e5e7eb;
  color: #6b7280;
  background: #f9fafb;
}

.review-content {
  display: -webkit-box;
  margin: 14px 0 0;
  overflow: hidden;
  color: #6f665e;
  font-size: 14px;
  line-height: 1.65;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.review-meta {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 13px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #988d83;
  font-size: 12px;
  line-height: 1.4;
}

.version-text {
  color: #c2410c;
}

.reply-badge {
  color: #2563eb;
}

.follow-up-preview {
  margin-top: 16px;
  padding: 14px 15px;
  border: 1px solid #fed7aa;
  border-left: 4px solid #fb923c;
  border-radius: 12px;
  background: #fffaf5;
}

.follow-up-preview-header {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.follow-up-title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.follow-up-label {
  color: #c2410c;
  font-size: 13px;
  font-weight: 750;
}

.follow-up-score {
  color: #d97706;
  font-size: 12px;
  font-weight: 700;
}

.follow-up-preview-content {
  display: -webkit-box;
  margin: 10px 0 0;
  overflow: hidden;
  color: #625951;
  font-size: 13px;
  line-height: 1.65;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.follow-up-preview-meta {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 7px 14px;
  margin-top: 10px;
  color: #9a8f85;
  font-size: 12px;
  line-height: 1.5;
}

.detail-entry {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  color: #9a8f85;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.arrow-icon {
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border-radius: 9px;
  color: #c2410c;
  font-size: 16px;
  background: #fff3e8;
  transition: transform 0.2s;
}

.review-item:hover .arrow-icon {
  transform: translateX(3px);
}

.loading-state,
.empty-state {
  display: flex;
  min-height: 290px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  margin-top: 17px;
  padding: 44px 20px;
  border: 1px dashed #e4d9cf;
  border-radius: 17px;
  text-align: center;
  background: #fcfaf7;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f4e8de;
  border-top-color: #ea580c;
  border-radius: 50%;
  animation: spin 0.85s linear infinite;
}

.loading-state p {
  margin: 14px 0 0;
  color: #8c8178;
  font-size: 14px;
}

.empty-icon-wrapper {
  display: grid;
  width: 82px;
  height: 82px;
  place-items: center;
  border: 1px solid #f3dcc9;
  border-radius: 24px;
  background: linear-gradient(135deg, #fff8f1, #ffecdb);
}

.empty-icon {
  font-size: 38px;
}

.empty-title {
  margin: 18px 0 7px;
  color: #3b342e;
  font-size: 20px;
}

.empty-desc {
  max-width: 420px;
  margin: 0;
  color: #91867d;
  font-size: 14px;
  line-height: 1.65;
}

.empty-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}

.empty-btn,
.secondary-empty-btn {
  min-height: 42px;
  padding: 0 17px;
  border-radius: 11px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
}

.empty-btn {
  border: 0;
  color: #fff;
  background: #ea580c;
  box-shadow: 0 8px 18px rgba(234, 88, 12, 0.2);
}

.empty-btn:hover {
  background: #c2410c;
}

.secondary-empty-btn {
  border: 1px solid #fed7aa;
  color: #c2410c;
  background: #fff8f1;
}

.pagination {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 22px;
}

.page-btn {
  min-height: 40px;
  padding: 0 15px;
  border: 1px solid #e6dfd8;
  border-radius: 10px;
  color: #625950;
  font-size: 14px;
  font-weight: 600;
  background: #fff;
  cursor: pointer;
}

.page-btn:hover:not(:disabled) {
  border-color: #fdba74;
  color: #c2410c;
  background: #fff8f1;
}

.page-btn:disabled {
  color: #b9b0a8;
  background: #f7f5f2;
  cursor: not-allowed;
}

.page-info {
  color: #7f756d;
  font-size: 14px;
  white-space: nowrap;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 820px) {
  .filter-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .review-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .detail-entry {
    align-self: flex-end;
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

  .reviews-main {
    padding-top: 16px;
  }

  .reviews-hero {
    align-items: flex-start;
    padding: 24px;
  }

  .hero-summary {
    min-width: 108px;
    padding: 13px 15px;
  }

  .hero-summary strong {
    font-size: 27px;
  }
}

@media (max-width: 540px) {
  .brand-copy small,
  .back-btn span {
    display: none;
  }

  .back-btn {
    padding: 0 10px;
  }

  .reviews-hero {
    align-items: stretch;
    flex-direction: column;
    gap: 18px;
    padding: 21px 18px;
  }

  .hero-copy h1 {
    font-size: 29px;
  }

  .hero-summary {
    align-items: flex-start;
  }

  .filter-section,
  .result-section {
    padding: 17px;
  }

  .filter-heading,
  .result-heading {
    align-items: flex-start;
  }

  .review-header {
    flex-direction: column;
  }

  .status-tag {
    align-self: flex-start;
  }

  .detail-entry {
    width: 100%;
    justify-content: space-between;
  }

  .pagination {
    flex-wrap: wrap;
  }

  .page-info {
    order: -1;
    width: 100%;
    text-align: center;
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
