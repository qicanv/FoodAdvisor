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
              <span class="tag price">人均 ¥{{ merchant.averagePrice }}</span>
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
                <div class="dish-price">¥{{ dish.price }}</div>
              </div>
            </div>
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
            </div>
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const route = useRoute()
const merchant = ref(null)
const reviews = ref([])
const dishes = ref([])
const loading = ref(true)

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

const formatBusinessHours = (hoursList) => {
  if (!hoursList || hoursList.length === 0) return '暂无信息'
  
  const weekDays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const formattedHours = []
  
  for (const hour of hoursList) {
    if (hour.isClosed) {
      continue
    }
    const dayName = weekDays[hour.dayOfWeek - 1] || `周${hour.dayOfWeek}`
    const openTime = hour.openTime ? hour.openTime.substring(0, 5) : ''
    const closeTime = hour.closeTime ? hour.closeTime.substring(0, 5) : ''
    formattedHours.push(`${dayName}: ${openTime}-${closeTime}`)
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
  } catch (error) {
    console.error('加载商家信息失败:', error)
  } finally {
    loading.value = false
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

.reviews-section {
  padding: 20px 0;
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
</style>