<template>
  <div class="merchant-detail">
    <nav class="detail-nav">
      <div class="nav-container">
        <button class="back-btn" @click="goBack">
          <span>← 返回榜单</span>
        </button>
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋</span>
        </div>
        <div class="user-info">
          <button class="profile-btn" @click="goToProfile">
            <span class="profile-icon">👤</span>
            <span class="user-name">{{ userInfo.username }}</span>
          </button>
          <button class="logout-btn" @click="handleLogout">退出登录</button>
        </div>
      </div>
    </nav>

    <main class="detail-main" v-if="merchant">
      <section class="hero-section">
        <div class="container">
          <div class="hero-content">
            <div class="merchant-icon" :style="{ background: merchant.bgColor }">
              <span class="icon-emoji">{{ merchant.emoji }}</span>
            </div>
            <div class="merchant-header">
              <h1 class="merchant-name">{{ merchant.name }}</h1>
              <div class="merchant-rating">
                <span class="star">⭐</span>
                <span class="rating-value">{{ merchant.rating }}</span>
                <span class="review-count">（{{ merchant.reviewCount }}条评价）</span>
              </div>
            </div>
            <div class="merchant-tags">
              <span class="tag cuisine">{{ merchant.cuisine }}</span>
              <span class="tag price">人均 ￥{{ merchant.averagePrice }}</span>
              <span class="tag status" :class="{ open: merchant.isOpen }">{{ merchant.isOpen ? '营业中' : '已打烊' }}</span>
            </div>
            <p class="merchant-desc">{{ merchant.description }}</p>
          </div>
        </div>
      </section>

      <section class="info-section">
        <div class="container">
          <div class="info-grid">
            <div class="info-card">
              <div class="info-icon">📍</div>
              <div class="info-content">
                <h3 class="info-title">店铺地址</h3>
                <p class="info-text">{{ merchant.address }}</p>
              </div>
            </div>
            <div class="info-card">
              <div class="info-icon">⏰</div>
              <div class="info-content">
                <h3 class="info-title">营业时间</h3>
                <p class="info-text">{{ formatBusinessHours(merchant.businessHoursList) }}</p>
              </div>
            </div>
            <div class="info-card">
              <div class="info-icon">📞</div>
              <div class="info-content">
                <h3 class="info-title">联系电话</h3>
                <p class="info-text">{{ merchant.phone || '暂无信息' }}</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="highlights-section">
        <div class="container">
          <h2 class="section-title">✨ 特色亮点</h2>
          <div class="highlights-grid">
            <div v-for="(highlight, index) in merchant.highlights" :key="index" class="highlight-item">
              <span class="highlight-icon">{{ highlight.icon }}</span>
              <span class="highlight-text">{{ highlight.text }}</span>
            </div>
          </div>
        </div>
      </section>

      <section class="dishes-section">
        <div class="container">
          <h2 class="section-title">🍽️ 推荐菜品</h2>
          <div class="dishes-grid">
            <div v-for="(dish, index) in dishes" :key="index" class="dish-card">
              <div class="dish-emoji">{{ getDishEmoji(dish.category) }}</div>
              <div class="dish-info">
                <div class="dish-header">
                  <h3 class="dish-name">{{ dish.name }}</h3>
                  <span class="dish-category">{{ dish.category }}</span>
                </div>
                <p class="dish-desc">{{ dish.description }}</p>
                <div class="dish-tags">
                  <span v-for="(taste, i) in dish.taste" :key="i" class="dish-taste-tag">{{ taste }}</span>
                </div>
                <div class="dish-price">￥{{ dish.price }}</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ========== 评价列表 & 标签筛选 ========== -->
      <section class="review-list-section">
        <div class="container">
          <div class="review-list-card">
            <h2 class="section-title">💬 用户评价</h2>

            <!-- 标签筛选栏 -->
            <div class="review-tag-bar" v-if="reviewTags.length > 0">
              <span class="tag-bar-label">筛选：</span>
              <button
                :class="['tag-chip', { active: activeTag === '' }]"
                @click="filterReviewsByTag('')"
              >全部</button>
              <button
                v-for="tag in reviewTags"
                :key="tag.tagCode"
                :class="['tag-chip', getTagChipClass(tag), { active: activeTag === tag.tagCode }]"
                @click="filterReviewsByTag(tag.tagCode)"
              >
                {{ tag.tagName || tag.tagCode }}
                <span class="tag-chip-count">{{ tag.totalCount || tag.count || 0 }}</span>
              </button>
            </div>

            <!-- 评价列表 -->
            <div v-if="filteredReviews.length > 0" class="review-items">
              <div v-for="rv in filteredReviews" :key="rv.id" class="review-item">
                <div class="review-item-header">
                  <span class="review-user-avatar">{{ (rv.username || '用户')[0] }}</span>
                  <span class="review-username">{{ rv.username || '匿名用户' }}</span>
                  <span class="review-stars">{{ '⭐'.repeat(rv.rating || 0) }}</span>
                  <span class="review-time">{{ formatReviewTime(rv.reviewTime || rv.createdAt) }}</span>
                </div>
                <p class="review-item-content">{{ rv.content }}</p>
                <!-- AI 情感标签 -->
                <div class="review-item-tags" v-if="rv.analysis">
                  <span :class="['review-sentiment-tag', (rv.analysis.sentiment || '').toLowerCase()]">
                    {{ sentimentLabel(rv.analysis.sentiment) }}
                  </span>
                  <span v-for="tag in (rv.analysis.tags || []).slice(0,4)" :key="tag.tagCode"
                        :class="['review-aspect-tag', (tag.sentiment || '').toLowerCase()]">
                    {{ tag.tagName || tag.tagCode }}
                  </span>
                </div>
                <!-- 商家回复 -->
                <div v-if="rv.replyContent" class="review-reply">
                  <span class="reply-label">商家回复：</span>{{ rv.replyContent }}
                </div>
              </div>
            </div>
            <div v-else class="review-empty">暂无符合条件的评价</div>

            <!-- 举报弹窗 -->
            <div v-if="reportDialogOpen" class="report-mask" @click.self="closeReportDialog">
              <div class="report-dialog">
                <h3>举报评价</h3>
                <select v-model="reportForm.reason" class="report-select">
                  <option value="">请选择举报原因</option>
                  <option value="ADVERTISING">广告引流</option>
                  <option value="FALSE_REVIEW">虚假评价</option>
                  <option value="MALICIOUS_ATTACK">恶意攻击</option>
                  <option value="SEXUAL_OR_VULGAR">色情低俗</option>
                  <option value="PRIVACY_LEAK">泄露隐私</option>
                  <option value="OTHER">其他</option>
                </select>
                <textarea v-model="reportForm.description" placeholder="补充说明（选填，最多500字）" maxlength="500" rows="3"></textarea>
                <div v-if="reportError" class="review-message error">{{ reportError }}</div>
                <div v-if="reportSuccess" class="review-message success">{{ reportSuccess }}</div>
                <div class="report-actions">
                  <button class="action-btn secondary" @click="closeReportDialog" :disabled="reportSubmitting">取消</button>
                  <button class="action-btn primary" @click="submitReportAction" :disabled="!reportForm.reason || reportSubmitting">
                    {{ reportSubmitting ? '提交中...' : '提交举报' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="review-submit-section">
        <div class="container">
          <div class="review-submit-card">
            <h2 class="section-title">发表评价</h2>

            <div v-if="!isLoggedIn" class="review-message warning">
              请先登录后再发表评价。
            </div>
            <div v-else-if="!canReview" class="review-message">
              商家停业或禁用后不可新增评价。
            </div>

            <form class="review-form" @submit.prevent="submitReview">
              <div class="review-field">
                <label for="review-rating">评分 <span class="required">*</span></label>
                <select
                  id="review-rating"
                  v-model.number="reviewForm.rating"
                  :disabled="!formEnabled"
                >
                  <option :value="0">请选择评分</option>
                  <option v-for="score in 5" :key="score" :value="score">
                    {{ score }} 星
                  </option>
                </select>
              </div>

              <div class="review-field">
                <label for="review-content">评价内容 <span class="required">*</span></label>
                <textarea
                  id="review-content"
                  v-model="reviewForm.content"
                  :disabled="!formEnabled"
                  minlength="10"
                  maxlength="2000"
                  rows="6"
                  placeholder="写下真实的消费体验，至少 10 个字符"
                ></textarea>
                <span class="field-hint">
                  {{ reviewForm.content.length }}/2000
                </span>
              </div>

              <div class="review-meta-grid">
                <div class="review-field">
                  <label for="review-spend">人均消费</label>
                  <input
                    id="review-spend"
                    v-model.number="reviewForm.averageSpend"
                    :disabled="!formEnabled"
                    type="number"
                    min="0"
                    step="0.01"
                    placeholder="可选"
                  />
                </div>

                <div class="review-field">
                  <label for="review-date">到店日期</label>
                  <input
                    id="review-date"
                    v-model="reviewForm.consumptionDate"
                    :disabled="!formEnabled"
                    type="date"
                  />
                </div>
              </div>

              <div class="review-field">
                <label for="review-images">评价图片</label>
                <input
                  id="review-images"
                  ref="imageInput"
                  :disabled="!formEnabled || selectedImages.length >= 9"
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  multiple
                  @change="selectImages"
                />
                <span class="field-hint">
                  最多 9 张，支持 JPEG、PNG、WebP，单张不超过 10MB
                </span>
              </div>

              <div v-if="selectedImages.length" class="selected-image-grid">
                <div
                  v-for="(image, index) in selectedImages"
                  :key="image.key"
                  class="selected-image"
                >
                  <img :src="image.previewUrl" :alt="`评价图片 ${index + 1}`" />
                  <button
                    type="button"
                    class="remove-image-btn"
                    :disabled="submitting"
                    @click="removeSelectedImage(index)"
                  >
                    删除
                  </button>
                </div>
              </div>

              <div v-if="submitError" class="review-message error">
                {{ submitError }}
              </div>
              <div v-if="submitSuccess" class="review-message success">
                {{ submitSuccess }}
              </div>

              <div class="review-submit-actions">
                <button
                  type="submit"
                  class="action-btn primary"
                  :disabled="!formEnabled"
                >
                  {{ submitting ? '提交中...' : '提交评价' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      </section>

      <section class="reviews-section">
        <div class="container">
          <h2 class="section-title">💬 用户评价</h2>
          <div class="reviews-list">
            <div v-for="(review, index) in reviews" :key="review.id || index" class="review-card">
              <div class="review-header">
                <div class="reviewer-info">
                  <div class="reviewer-avatar">{{ review.avatar }}</div>
                  <div class="reviewer-detail">
                    <span class="reviewer-name">{{ review.username }}</span>
                    <span class="review-date">{{ review.date }}</span>
                  </div>
                </div>
                <div class="review-rating">
                  <span v-for="i in 5" :key="i" class="review-star">{{ i <= review.rating ? '⭐' : '☆' }}</span>
                </div>
              </div>
              <p class="review-content">{{ review.content }}</p>
              <div class="review-actions">
                <button
                  v-if="isLoggedIn && review.userId !== currentUserId"
                  class="report-btn"
                  @click="openReportDialog(review)"
                >
                  🚩 举报
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section v-if="reviewSummary?.status === 'SUCCESS'" class="summary-section">
        <div class="container">
          <div class="summary-card">
            <div class="summary-header">
              <h2 class="section-title">AI 评价摘要</h2>
              <button
                type="button"
                class="summary-evidence-button"
                @click="openSummaryEvidence"
              >
                查看依据
              </button>
            </div>
            <p>{{ reviewSummary.summaryText || '暂无摘要内容' }}</p>
          </div>
        </div>
      </section>

      <section class="recommend-section">
        <div class="container">
          <h2 class="section-title">📌 AI推荐理由</h2>
          <div class="recommend-card">
            <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="recommend-ai-img" />
            <div class="recommend-content">
              <p>{{ merchant.recommendReason }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="action-section">
        <div class="container">
          <div class="action-buttons">
            <button class="action-btn primary">立即预订</button>
            <button class="action-btn secondary">导航到店</button>
            <button class="action-btn secondary">收藏店铺</button>
          </div>
        </div>
      </section>
    </main>

    <main class="detail-main" v-else-if="loading">
      <section class="loading-section">
        <div class="container">
          <div class="loading-card">
            <div class="loading-spinner"></div>
            <p>正在加载商家信息...</p>
          </div>
        </div>
      </section>
    </main>

    <div
      v-if="summaryEvidenceOpen"
      class="evidence-mask"
      @click.self="closeSummaryEvidence"
    >
      <section class="evidence-dialog" role="dialog" aria-modal="true">
        <header>
          <h2>评价摘要依据</h2>
          <button type="button" @click="closeSummaryEvidence">关闭</button>
        </header>
        <div v-if="summaryEvidenceLoading" class="evidence-state">
          正在加载评价依据...
        </div>
        <div v-else-if="summaryEvidenceError" class="evidence-error">
          {{ summaryEvidenceError }}
        </div>
        <div v-else-if="!summaryEvidences.length" class="evidence-state">
          该摘要暂无可查看依据
        </div>
        <article
          v-for="evidence in summaryEvidences"
          :key="evidence.evidenceId"
          class="evidence-item"
        >
          <div class="evidence-meta">
            <strong>用户评价</strong>
            <span>{{ evidence.merchantName || merchant?.name }}</span>
            <span>{{ evidenceTypeText(evidence.evidenceType) }}</span>
          </div>
          <template v-if="evidence.available">
            <p>{{ evidence.evidenceExcerpt || evidence.reviewContent }}</p>
            <div class="evidence-footer">
              <span v-if="evidence.rating != null">
                评分：{{ evidence.rating }} 星
              </span>
              <span v-if="evidence.reviewTime">
                {{ formatDate(evidence.reviewTime) }}
              </span>
            </div>
          </template>
          <template v-else>
            <p class="evidence-unavailable">来源不可用</p>
            <small>该评价已删除或当前无权查看</small>
          </template>
        </article>
      </section>
    </div>

  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../../api/request'
import { submitMerchantReview } from '../../api/review'
import {
  getMerchantReviewSummary,
  getMerchantReviewSummaryEvidences
} from '../../api/restaurant'
import { getMerchantReviews, getMerchantReviewTags, getReviewAnalysis } from '../../api/reviewAnalysis'

const router = useRouter()
const route = useRoute()
const merchant = ref(null)
const reviews = ref([])
const dishes = ref([])

// ---- 标签筛选 & 评价列表 ----
const reviewTags = ref([])
const activeTag = ref('')
const filteredReviews = ref([])

async function loadReviewsWithTags() {
  const mId = merchant.value?.merchantId || merchant.value?.id
  if (!mId) return
  try {
    const [revRes, tagRes] = await Promise.all([
      getMerchantReviews(mId, { pageNum: 1, pageSize: 50 }),
      getMerchantReviewTags(mId),
    ])
    if (revRes.success) {
      const raw = revRes.data?.records || revRes.data?.value || []
      // 为每条评价加载分析结果
      reviews.value = await Promise.all(raw.map(async (r) => {
        try {
          const ar = await getReviewAnalysis(r.id)
          if (ar.success && ar.data) return { ...r, analysis: ar.data }
        } catch (_) { /* ignore */ }
        return { ...r, analysis: null }
      }))
      applyTagFilter()
    }
    if (tagRes.success) reviewTags.value = tagRes.data || []
  } catch (e) { console.error('加载评价列表失败', e) }
}

function filterReviewsByTag(tagCode) {
  activeTag.value = tagCode
  applyTagFilter()
}

function applyTagFilter() {
  if (!activeTag.value) {
    filteredReviews.value = reviews.value
  } else {
    filteredReviews.value = reviews.value.filter(r =>
      r.analysis?.tags?.some(t => t.tagCode === activeTag.value)
    )
  }
}

function getTagChipClass(tag) {
  if (tag.sentiment === 'POSITIVE' || (tag.positiveCount || 0) > (tag.negativeCount || 0)) return 'tag-positive'
  if (tag.sentiment === 'NEGATIVE' || (tag.negativeCount || 0) > (tag.positiveCount || 0)) return 'tag-negative'
  return 'tag-neutral'
}

function sentimentLabel(s) {
  return { POSITIVE: '👍 正面', NEGATIVE: '👎 负面', NEUTRAL: '➖ 中性', MIXED: '🔄 混合' }[s] || s || ''
}

function formatReviewTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return d.toLocaleDateString('zh-CN')
}
const loading = ref(true)
const submitting = ref(false)
const submitError = ref('')
const submitSuccess = ref('')
const imageInput = ref(null)
const selectedImages = ref([])
const reviewSummary = ref(null)
const summaryEvidenceOpen = ref(false)
const summaryEvidenceLoading = ref(false)
const summaryEvidenceError = ref('')
const summaryEvidences = ref([])

const reviewForm = reactive({
  rating: 0,
  content: '',
  averageSpend: null,
  consumptionDate: '',
})

const currentUser = computed(() => {
  const raw = localStorage.getItem('user') || localStorage.getItem('userInfo')
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
})

const isLoggedIn = computed(() => Boolean(
  localStorage.getItem('token') ||
  localStorage.getItem('accessToken') ||
  currentUser.value
))
const canReview = computed(() => merchant.value?.isOpen && merchant.value?.platformStatus === 'ACTIVE')
const formEnabled = computed(() => isLoggedIn.value && canReview.value && !submitting.value)

const userInfo = ref({
  username: localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')).username : ''
})

const cuisineEmojis = {
  '川菜': '🌶️',
  '粤菜': '🍵',
  '烧烤': '🍢',
  '轻食': '🥗',
  '日料': '🍣',
  '西餐': '🍝',
  '火锅': '🍲'
}

const cuisineColors = {
  '川菜': 'linear-gradient(135deg, #ff6b6b 0%, #ffa502 100%)',
  '粤菜': 'linear-gradient(135deg, #7bed9f 0%, #70a1ff 100%)',
  '烧烤': 'linear-gradient(135deg, #ff9a9e 0%, #fecfef 100%)',
  '轻食': 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  '日料': 'linear-gradient(135deg, #dfe6e9 0%, #b2bec3 100%)',
  '西餐': 'linear-gradient(135deg, #a29bfe 0%, #6c5ce7 100%)',
  '火锅': 'linear-gradient(135deg, #fd79a8 0%, #e84393 100%)'
}

const avatarList = ['👨', '👩', '👴', '👵', '👨‍💼', '👩‍💼', '🧑', '👨‍🍳', '👩‍🍳', '🥷']

const getUserAvatar = (userId) => {
  return avatarList[userId % avatarList.length] || '👤'
}

const categoryEmojis = {
  '热菜': '🔥',
  '凉菜': '🥗',
  '点心': '🍰',
  '烧腊': '🍖',
  '烤串': '🍢',
  '蔬菜': '🥬',
  '沙拉': '🥗',
  '饮品': '🧋',
  '刺身': '🍣',
  '烤物': '🍢',
  '主食': '🍚',
  '汤羹': '🍲',
  '甜点': '🍧'
}

const getDishEmoji = (category) => {
  return categoryEmojis[category] || '🍽️'
}

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return dateStr
  return date.toLocaleDateString('zh-CN')
}

const evidenceTypeText = type => ({
  ADVANTAGE: '优势',
  DISADVANTAGE: '不足',
  DISH: '推荐菜品',
  ENVIRONMENT: '环境',
  SERVICE: '服务',
  RECENT_CHANGE: '近期变化'
}[type] || '摘要依据')

const formatBusinessHours = (hoursList) => {
  if (!hoursList || hoursList.length === 0) return '暂无信息'
  
  const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const formattedHours = []

  const sortedHours = [...hoursList].sort((left, right) => {
    const dayDifference = (left.dayOfWeek || 0) - (right.dayOfWeek || 0)
    if (dayDifference !== 0) return dayDifference
    return String(left.openTime || '').localeCompare(String(right.openTime || ''))
  })

  for (const hour of sortedHours) {
    if (hour.isClosed) {
      continue
    }
    const dayName = weekDays[hour.dayOfWeek - 1] || `周${hour.dayOfWeek}`
    const openTime = hour.openTime ? hour.openTime.substring(0, 5) : ''
    const closeTime = hour.closeTime ? hour.closeTime.substring(0, 5) : ''
    const displayedCloseTime = hour.crossesMidnight ? `次日${closeTime}` : closeTime
    formattedHours.push(`${dayName}: ${openTime}-${displayedCloseTime}`)
  }
  
  return formattedHours.join('，') || '暂无信息'
}

const parseTags = (tags) => {
  if (!tags) return []
  if (Array.isArray(tags)) return tags
  try {
    return JSON.parse(tags)
  } catch {
    return []
  }
}

const loadMerchant = async () => {
  loading.value = true
  const merchantId = parseInt(route.params.id)
  
  try {
    const detailResponse = await request.get(`/api/merchants/${merchantId}`)
    
    if (detailResponse.success && detailResponse.data) {
      const data = detailResponse.data
      const environmentTags = parseTags(data.environmentTags)
      
      merchant.value = {
        id: data.merchantId,
        name: data.merchantName,
        emoji: cuisineEmojis[data.cuisine] || '🍽️',
        bgColor: cuisineColors[data.cuisine] || 'linear-gradient(135deg, #ff6b35 0%, #ff8c42 100%)',
        rating: data.rating || data.averageRating || 0,
        reviewCount: data.reviewCount || data.ratingCount || 0,
        cuisine: data.cuisine,
        averagePrice: data.averagePrice || 0,
        isOpen: data.businessStatus === 'OPERATING',
        description: data.description,
        address: data.address,
        phone: data.phone,
        platformStatus: data.platformStatus,
        businessHoursList: data.businessHours || [],
        highlights: environmentTags.map(tag => ({
          icon: ['朋友聚会', '家庭聚餐', '情侣约会'].includes(tag) ? '👥' :
                ['环境优雅', '安静舒适', '新中式'].includes(tag) ? '🏠' :
                ['商务宴请', '包间'].includes(tag) ? '💼' :
                ['早茶', '夜宵', '深夜营业'].includes(tag) ? '⏰' : '⭐',
          text: tag
        })),
        recommendReason: `根据AI分析，${data.merchantName}是一家${data.cuisine}餐厅，评分${data.rating || data.averageRating || 0}分，人均消费${data.averagePrice || 0}元，${environmentTags.length > 0 ? '适合' + environmentTags.join('、') : ''}。`
      }
    }
    
    const reviewsResponse = await request.get('/api/reviews', {
      params: {
        merchantId: merchantId,
        pageNum: 1,
        pageSize: 10
      }
    })

    if (reviewsResponse.success && reviewsResponse.data) {
      const reviewData = reviewsResponse.data
      reviews.value = reviewData.records ? reviewData.records.map(review => ({
        id: review.id,
        userId: review.userId || (review.user ? review.user.id : null),
        username: review.user ? review.user.username : review.userId ? `用户${review.userId}` : '匿名用户',
        avatar: getUserAvatar(review.userId || 0),
        date: formatDate(review.publishedAt || review.createdAt),
        rating: review.rating,
        content: review.content
      })) : []
    }
    
    const dishesResponse = await request.get('/api/dishes', {
      params: {
        merchantId: merchantId
      }
    })
    
    if (dishesResponse.success && dishesResponse.data) {
      dishes.value = dishesResponse.data.map(dish => ({
        name: dish.name,
        description: dish.description,
        price: dish.price,
        category: dish.category,
        taste: parseTags(dish.tasteTags)
      }))
    }

    const summaryResponse = await getMerchantReviewSummary(merchantId)
    reviewSummary.value = summaryResponse.success
      ? summaryResponse.data
      : null
  } catch (error) {
    console.error('加载商家信息失败:', error)
  } finally {
    loading.value = false
  }
}

const openSummaryEvidence = async () => {
  if (!reviewSummary.value?.summaryId || !merchant.value?.id) return
  summaryEvidenceOpen.value = true
  summaryEvidenceLoading.value = true
  summaryEvidenceError.value = ''
  summaryEvidences.value = []
  try {
    const response = await getMerchantReviewSummaryEvidences(
      merchant.value.id,
      reviewSummary.value.summaryId
    )
    if (!response.success) {
      throw new Error(response.message || '评价依据加载失败')
    }
    summaryEvidences.value = response.data || []
  } catch (error) {
    summaryEvidenceError.value =
      error.message || '评价依据加载失败，请稍后重试'
  } finally {
    summaryEvidenceLoading.value = false
  }
}

const closeSummaryEvidence = () => {
  summaryEvidenceOpen.value = false
}

// ==================== 举报功能 ====================

const reportDialogOpen = ref(false)
const reportSubmitting = ref(false)
const reportError = ref('')
const reportSuccess = ref('')
const reportingReview = ref(null)

const reportReasons = [
  { label: '广告引流', value: 'ADVERTISING' },
  { label: '虚假评价', value: 'FALSE_REVIEW' },
  { label: '恶意攻击', value: 'MALICIOUS_ATTACK' },
  { label: '色情低俗', value: 'SEXUAL_OR_VULGAR' },
  { label: '泄露隐私', value: 'PRIVACY_LEAK' },
  { label: '其他', value: 'OTHER' }
]

const reportForm = reactive({
  reason: '',
  description: ''
})

const currentUserId = computed(() => {
  const user = currentUser.value
  return user ? (user.id || user.userId) : null
})

const openReportDialog = (review) => {
  if (!isLoggedIn.value) {
    router.push('/diner')
    return
  }
  reportingReview.value = review
  reportForm.reason = ''
  reportForm.description = ''
  reportError.value = ''
  reportSuccess.value = ''
  reportDialogOpen.value = true
}

const closeReportDialog = () => {
  if (reportSubmitting.value) return
  reportDialogOpen.value = false
  reportingReview.value = null
}

const submitReportAction = async () => {
  if (!reportForm.reason) {
    reportError.value = '请选择举报原因'
    return
  }
  if (!reportingReview.value) return

  reportError.value = ''
  reportSuccess.value = ''
  reportSubmitting.value = true

  try {
    const response = await request.post('/api/reports', {
      reportedReviewId: reportingReview.value.id,
      merchantId: merchant.value.id,
      reason: reportForm.reason,
      description: reportForm.description || undefined
    })

    if (response.success) {
      reportSuccess.value = '举报已提交'
      setTimeout(() => {
        closeReportDialog()
      }, 1500)
    } else {
      reportError.value = response.message || '举报提交失败，请稍后重试'
    }
  } catch (error) {
    reportError.value = '举报提交失败，请稍后重试'
  } finally {
    reportSubmitting.value = false
  }
}

// ==================== 举报功能结束 ====================

const validateReview = () => {
  const contentLength = reviewForm.content.trim().length
  if (!reviewForm.rating || reviewForm.rating < 1 || reviewForm.rating > 5) {
    return '请选择 1～5 星评分'
  }
  if (contentLength < 10) {
    return '评价内容至少需要 10 个字符'
  }
  if (contentLength > 2000) {
    return '评价内容不能超过 2000 个字符'
  }
  if (reviewForm.averageSpend !== null && reviewForm.averageSpend < 0) {
    return '人均消费不能为负数'
  }
  return ''
}

const selectImages = event => {
  submitError.value = ''
  const files = Array.from(event.target.files || [])

  for (const file of files) {
    if (selectedImages.value.length >= 9) {
      submitError.value = '最多只能上传 9 张图片'
      break
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      submitError.value = '图片仅支持 JPEG、PNG 和 WebP 格式'
      continue
    }
    if (file.size > 10 * 1024 * 1024) {
      submitError.value = '单张图片不能超过 10MB'
      continue
    }

    selectedImages.value.push({
      key: `${file.name}-${file.lastModified}-${Math.random()}`,
      file,
      previewUrl: URL.createObjectURL(file),
    })
  }

  event.target.value = ''
}

const removeSelectedImage = index => {
  const [removed] = selectedImages.value.splice(index, 1)
  if (removed) {
    URL.revokeObjectURL(removed.previewUrl)
  }
}

const clearReviewForm = () => {
  reviewForm.rating = 0
  reviewForm.content = ''
  reviewForm.averageSpend = null
  reviewForm.consumptionDate = ''
  selectedImages.value.forEach(image => URL.revokeObjectURL(image.previewUrl))
  selectedImages.value = []
  if (imageInput.value) {
    imageInput.value.value = ''
  }
}

const submitReview = async () => {
  submitError.value = validateReview()
  submitSuccess.value = ''
  if (submitError.value || !formEnabled.value) return

  submitting.value = true
  try {
    const formData = new FormData()
    formData.append('content', reviewForm.content.trim())
    formData.append('rating', reviewForm.rating)
    if (reviewForm.averageSpend !== null && reviewForm.averageSpend !== '') {
      formData.append('averageSpend', reviewForm.averageSpend)
    }
    if (reviewForm.consumptionDate) {
      formData.append('consumptionDate', reviewForm.consumptionDate)
    }
    selectedImages.value.forEach(image => {
      formData.append('images', image.file)
    })

    const response = await submitMerchantReview(route.params.id, formData)
    if (!response.success) {
      throw new Error(response.message || '评价提交失败')
    }

    const status = response.data?.status
    clearReviewForm()
    submitSuccess.value = status === 'PENDING'
      ? '评价已提交，正在审核'
      : '评价发表成功'
    await loadMerchant()
  } catch (error) {
    submitError.value = error.message || '评价提交失败'
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  router.push('/diner/ranking')
}

const goToProfile = () => {
  router.push('/diner/profile')
}

const handleLogout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('userRole')
  router.push('/diner')
}

onMounted(() => {
  loadMerchant()
  loadReviewsWithTags()
})

onBeforeUnmount(() => {
  selectedImages.value.forEach(image => URL.revokeObjectURL(image.previewUrl))
})
</script>

<style scoped>
.merchant-detail {
  min-height: 100vh;
  background: #fafafa;
}

.container {
  max-width: 1150px;
  margin: 0 auto;
  padding: 0 24px;
}

.detail-nav {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  padding: 16px 0;
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.back-btn {
  padding: 8px 16px;
  background: #fff5f0;
  color: #ff6b35;
  border: 1px solid #ffccb3;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.back-btn:hover {
  background: #ffe8d9;
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
  color: #ff6b35;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #f5f5f5;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
}

.profile-icon {
  font-size: 16px;
}

.user-name {
  color: #333;
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

.logout-btn:hover {
  background: #fff2f0;
}

.detail-main {
  padding-top: 20px;
}

.hero-section {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c42 100%);
  padding: 40px 0;
  text-align: center;
}

.hero-content {
  max-width: 800px;
  margin: 0 auto;
}

.merchant-icon {
  width: 100px;
  height: 100px;
  border-radius: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
}

.icon-emoji {
  font-size: 50px;
}

.merchant-header {
  margin-bottom: 16px;
}

.merchant-name {
  font-size: 32px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 8px;
}

.merchant-rating {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.star {
  font-size: 24px;
}

.rating-value {
  font-size: 24px;
  font-weight: 700;
  color: #fff;
}

.review-count {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.8);
}

.merchant-tags {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 16px;
}

.tag {
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

.tag.cuisine {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.tag.price {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.tag.status {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.tag.status.open {
  background: rgba(82, 196, 26, 0.9);
}

.merchant-desc {
  font-size: 17px;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.7;
}

.info-section {
  padding: 30px 0;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.info-icon {
  font-size: 28px;
}

.info-content {
  flex: 1;
}

.info-title {
  font-size: 14px;
  font-weight: 600;
  color: #999;
  margin-bottom: 4px;
}

.info-text {
  font-size: 16px;
  color: #333;
}

.highlights-section {
  padding: 20px 0;
}

.section-title {
  font-size: 22px;
  font-weight: 700;
  color: #333;
  margin-bottom: 20px;
}

.highlights-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.highlight-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: #fff;
  border-radius: 20px;
  border: 1px solid #e8e8e8;
}

.highlight-icon {
  font-size: 18px;
}

.highlight-text {
  font-size: 14px;
  color: #666;
}

.dishes-section {
  padding: 20px 0;
}

.dishes-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.dish-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  transition: all 0.2s;
}

.dish-card:hover {
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.dish-emoji {
  font-size: 36px;
}

.dish-info {
  flex: 1;
}

.dish-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.dish-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.dish-category {
  padding: 3px 10px;
  background: #f0f5ff;
  color: #4080ff;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.dish-desc {
  font-size: 14px;
  color: #666;
  margin-bottom: 8px;
}

.dish-tags {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}

.dish-taste-tag {
  padding: 3px 8px;
  background: #fff5f0;
  color: #ff6b35;
  border-radius: 4px;
  font-size: 12px;
}

.dish-price {
  font-size: 18px;
  font-weight: 600;
  color: #ff6b35;
}

.popularity-value {
  font-size: 16px;
  font-weight: 600;
  color: #52c41a;
}

.review-submit-section {
  padding: 20px 0;
}

.review-submit-card {
  padding: 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.review-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.review-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.review-field label {
  color: #333;
  font-size: 15px;
  font-weight: 600;
}

.required {
  color: #ff4d4f;
}

.review-field input,
.review-field select,
.review-field textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 11px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  color: #333;
  font: inherit;
  background: #fff;
}

.review-field textarea {
  resize: vertical;
  line-height: 1.6;
}

.review-field input:disabled,
.review-field select:disabled,
.review-field textarea:disabled {
  color: #999;
  cursor: not-allowed;
  background: #f5f5f5;
}

.review-meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.field-hint {
  color: #999;
  font-size: 13px;
}

.selected-image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 12px;
}

.selected-image {
  overflow: hidden;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fff;
}

.selected-image img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
}

.remove-image-btn {
  width: 100%;
  padding: 8px;
  border: 0;
  color: #ff4d4f;
  cursor: pointer;
  background: #fff;
}

.review-message {
  margin-bottom: 18px;
  padding: 12px 14px;
  border-radius: 8px;
  color: #606266;
  background: #f4f4f5;
}

.review-message.warning {
  color: #e6a23c;
  background: #fdf6ec;
}

.review-message.error {
  margin-bottom: 0;
  color: #f56c6c;
  background: #fef0f0;
}

.review-message.success {
  margin-bottom: 0;
  color: #67c23a;
  background: #f0f9eb;
}

.review-submit-actions {
  display: flex;
  justify-content: flex-end;
}

.review-submit-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
  transform: none;
  box-shadow: none;
}

.reviews-section {
  padding: 20px 0;
}

.summary-section {
  padding: 20px 0;
}

.summary-card {
  padding: 24px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.summary-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.summary-header .section-title {
  margin-bottom: 0;
}

.summary-evidence-button {
  padding: 8px 14px;
  border: 1px solid #ff6b35;
  border-radius: 8px;
  color: #ff6b35;
  background: #fff;
  cursor: pointer;
}

.evidence-mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(17, 24, 39, 0.45);
}

.evidence-dialog {
  width: min(680px, 100%);
  max-height: 78vh;
  overflow-y: auto;
  padding: 20px;
  border-radius: 16px;
  background: #fff;
}

.evidence-dialog header,
.evidence-meta,
.evidence-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.evidence-dialog header h2 {
  margin: 0;
}

.evidence-item {
  margin-top: 14px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.evidence-meta,
.evidence-footer,
.evidence-item small {
  color: #667085;
  font-size: 13px;
}

.evidence-unavailable,
.evidence-error {
  color: #d92d20;
}

.evidence-state,
.evidence-error {
  padding: 24px 0;
  text-align: center;
}

.reviews-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.reviewer-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.reviewer-avatar {
  font-size: 32px;
}

.reviewer-detail {
  display: flex;
  flex-direction: column;
}

.reviewer-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.review-date {
  font-size: 13px;
  color: #999;
}

.review-rating {
  font-size: 18px;
}

.review-star {
  margin-left: 2px;
}

.review-content {
  font-size: 16px;
  color: #666;
  line-height: 1.6;
}

.review-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}

.report-btn {
  padding: 6px 14px;
  background: transparent;
  color: #999;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.report-btn:hover {
  color: #ff4d4f;
  border-color: #ffccc7;
  background: #fff2f0;
}

/* 举报弹窗 */
.report-dialog {
  max-width: 520px;
}

.report-form {
  margin-top: 16px;
}

.report-field {
  margin-bottom: 18px;
}

.report-label {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.reason-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.reason-btn {
  padding: 10px 12px;
  background: #f5f5f5;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.reason-btn:hover {
  border-color: #ff6700;
  color: #ff6700;
}

.reason-btn.active {
  background: #fff5f0;
  border-color: #ff6700;
  color: #ff6700;
  font-weight: 600;
}

.report-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 11px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font: inherit;
  font-size: 14px;
  resize: vertical;
  line-height: 1.6;
}

.report-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}

.report-actions .action-btn {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.report-actions .action-btn.primary {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c42 100%);
  color: #fff;
}

.report-actions .action-btn.primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(255, 107, 53, 0.4);
}

.report-actions .action-btn.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.report-actions .action-btn.secondary {
  background: #fff;
  color: #666;
  border: 1px solid #e8e8e8;
}

.report-actions .action-btn.secondary:hover:not(:disabled) {
  border-color: #ff6700;
  color: #ff6700;
}

@media (max-width: 480px) {
  .reason-options {
    grid-template-columns: repeat(2, 1fr);
  }
}

.review-images {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.review-img {
  font-size: 40px;
}

.recommend-section {
  padding: 20px 0;
}

.recommend-card {
  display: flex;
  gap: 16px;
  background: linear-gradient(135deg, #fff5f0 0%, #fff 100%);
  border-radius: 12px;
  padding: 24px;
  border-left: 4px solid #ff6b35;
}

.recommend-ai-img {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  flex-shrink: 0;
}

.recommend-content {
  flex: 1;
}

.recommend-content p {
  font-size: 16px;
  color: #666;
  line-height: 1.7;
}

.action-section {
  padding: 30px 0 50px;
}

.action-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
}

.action-btn {
  padding: 14px 32px;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.action-btn.primary {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c42 100%);
  color: #fff;
}

.action-btn.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(255, 107, 53, 0.4);
}

.action-btn.secondary {
  background: #fff;
  color: #666;
  border: 2px solid #e8e8e8;
}

.action-btn.secondary:hover {
  border-color: #ff6b35;
  color: #ff6b35;
}

.loading-section {
  padding: 100px 0;
  text-align: center;
}

.loading-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #ff6b35;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-card p {
  font-size: 16px;
  color: #666;
}

@media (max-width: 768px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
  
  .dishes-grid {
    grid-template-columns: 1fr;
  }

  .review-meta-grid {
    grid-template-columns: 1fr;
  }
  
  .action-buttons {
    flex-direction: column;
  }
  
  .merchant-name {
    font-size: 26px;
  }
  
  .hero-section {
    padding: 30px 0;
  }
}

/* ===== 评价列表 & 标签筛选 ===== */
.review-list-section { margin-top: 32px; }
.review-list-card {
  background: #fff; border-radius: 16px; padding: 24px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.06);
}
.review-tag-bar {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  margin-bottom: 20px; padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}
.tag-bar-label { font-size: 14px; color: #999; font-weight: 500; }
.tag-chip {
  padding: 6px 14px; border-radius: 20px; font-size: 13px;
  border: 1px solid #e0e0e0; background: #fafafa; color: #555;
  cursor: pointer; transition: all 0.2s;
}
.tag-chip:hover { border-color: #1890ff; color: #1890ff; }
.tag-chip.active { background: #1890ff; color: #fff; border-color: #1890ff; }
.tag-chip.tag-positive { border-color: #b7eb8f; }
.tag-chip.tag-positive:hover, .tag-chip.tag-positive.active { background: #52c41a; color: #fff; border-color: #52c41a; }
.tag-chip.tag-negative { border-color: #ffccc7; }
.tag-chip.tag-negative:hover, .tag-chip.tag-negative.active { background: #ff4d4f; color: #fff; border-color: #ff4d4f; }
.tag-chip-count { margin-left: 3px; opacity: 0.7; font-size: 11px; }

.review-items { display: flex; flex-direction: column; gap: 16px; }
.review-item {
  padding: 16px; background: #fafafa; border-radius: 12px;
  border: 1px solid #f0f0f0; transition: all 0.2s;
}
.review-item:hover { background: #f5f5f5; }
.review-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.review-user-avatar {
  width: 28px; height: 28px; border-radius: 50%; background: #1890ff;
  color: #fff; font-size: 12px; display: flex; align-items: center; justify-content: center;
}
.review-username { font-size: 14px; font-weight: 600; color: #1f2d3d; }
.review-stars { font-size: 12px; }
.review-time { margin-left: auto; font-size: 12px; color: #ccc; }
.review-item-content { font-size: 14px; color: #444; line-height: 1.7; margin: 8px 0; }
.review-item-tags { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
.review-sentiment-tag {
  padding: 2px 10px; border-radius: 4px; font-size: 11px; font-weight: 600;
}
.review-sentiment-tag.positive { color: #52c41a; background: #f6ffed; }
.review-sentiment-tag.negative { color: #ff4d4f; background: #fff2f0; }
.review-sentiment-tag.neutral { color: #1890ff; background: #e6f7ff; }
.review-sentiment-tag.mixed { color: #faad14; background: #fffbe6; }
.review-aspect-tag {
  padding: 2px 8px; border-radius: 3px; font-size: 11px;
  background: #f5f5f5; color: #667085;
}
.review-aspect-tag.positive { color: #52c41a; }
.review-aspect-tag.negative { color: #ff4d4f; }
.review-reply {
  margin-top: 10px; padding: 10px 14px; background: #f6ffed;
  border-left: 3px solid #52c41a; border-radius: 0 8px 8px 0;
  font-size: 13px; color: #555; line-height: 1.6;
}
.reply-label { font-weight: 600; color: #52c41a; }
.review-empty { text-align: center; color: #ccc; padding: 32px; font-size: 14px; }

.report-mask {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center;
  z-index: 1000;
}
.report-dialog {
  background: #fff; border-radius: 16px; padding: 24px; width: 440px; max-width: 90vw;
  box-shadow: 0 16px 48px rgba(0,0,0,0.15);
}
.report-dialog h3 { margin: 0 0 16px; font-size: 18px; color: #1f2d3d; }
.report-select {
  width: 100%; padding: 10px 12px; border: 1px solid #d9d9d9; border-radius: 8px;
  font-size: 14px; color: #1f2d3d; margin-bottom: 12px; outline: none;
}
.report-select:focus { border-color: #1890ff; }
.report-dialog textarea {
  width: 100%; padding: 10px 12px; border: 1px solid #d9d9d9; border-radius: 8px;
  font-size: 14px; resize: vertical;
}
.report-dialog textarea:focus { border-color: #1890ff; outline: none; }
.report-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 16px; }
</style>
