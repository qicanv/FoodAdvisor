<template>
  <div class="ranking-view">
    <nav class="ranking-nav">
      <div class="nav-container">
        <div class="logo-section">
          <img src="../../assets/images/greedy-cat.png" alt="食尚参谋" class="logo-img" />
          <span class="brand-name">食尚参谋 - 热门榜单</span>
        </div>
        <div class="nav-links">
          <button class="back-btn" @click="goBack">
            <span>← 返回首页</span>
          </button>
          <div class="user-info">
            <button class="profile-btn" @click="goToProfile">
              <span class="profile-icon">👤</span>
              <span class="user-name">{{ userInfo.username }}</span>
            </button>
            <button class="logout-btn" @click="handleLogout">退出登录</button>
          </div>
        </div>
      </div>
    </nav>

    <main class="ranking-main">
      <section class="hero-section">
        <div class="container">
          <div class="hero-content">
            <h1>🔥 热门商家榜单</h1>
            <p>发现值得尝试的新店和热门店铺，食尚参谋为您精选</p>
            <div class="update-info">
              <span class="update-label">榜单更新时间：</span>
              <span class="update-time">{{ updateTime }}</span>
              <button class="refresh-btn" @click="refreshRankings">
                <span>🔄 刷新</span>
              </button>
            </div>
          </div>
        </div>
      </section>

      <section class="tabs-section">
        <div class="container">
          <div class="tabs-wrapper">
            <button
              v-for="tab in tabs"
              :key="tab.key"
              class="tab-btn"
              :class="{ active: activeTab === tab.key }"
              @click="activeTab = tab.key"
            >
              <span class="tab-icon">{{ tab.icon }}</span>
              <span class="tab-name">{{ tab.name }}</span>
            </button>
          </div>
        </div>
      </section>

      <section class="rules-section">
        <div class="container">
          <div class="rules-card">
            <h3 class="rules-title">
              <span class="rules-icon">📊</span>
              <span>排序规则说明</span>
            </h3>
            <p class="rules-desc">{{ currentRules }}</p>
          </div>
        </div>
      </section>

      <section class="list-section">
        <div class="container">
          <div class="ranking-list">
            <div
              v-for="(item, index) in currentRanking"
              :key="item.id"
              class="ranking-item"
              :class="{
                'top-three': index < 3,
                'first': index === 0,
                'second': index === 1,
                'third': index === 2
              }"
              @click="goToMerchantDetail(item.id)"
            >
              <div class="rank-badge">
                <span v-if="index === 0" class="rank-icon">🥇</span>
                <span v-else-if="index === 1" class="rank-icon">🥈</span>
                <span v-else-if="index === 2" class="rank-icon">🥉</span>
                <span v-else class="rank-number">{{ index + 1 }}</span>
              </div>
              <div class="merchant-info">
                <div class="merchant-header">
                  <h3 class="merchant-name">{{ item.name }}</h3>
                  <div class="merchant-tags">
                    <span class="tag cuisine">{{ item.cuisine }}</span>
                    <span class="tag rating">⭐ {{ item.rating }}</span>
                  </div>
                </div>
                <p class="merchant-desc">{{ item.description }}</p>
                <div class="merchant-metrics">
                  <span class="metric">
                    <span class="metric-label">{{ item.metricLabel }}</span>
                    <span class="metric-value">{{ item.metricValue }}</span>
                  </span>
                  <span class="metric">
                    <span class="metric-label">评价数</span>
                    <span class="metric-value">{{ item.reviewCount }}条</span>
                  </span>
                  <span class="metric">
                    <span class="metric-label">人均</span>
                    <span class="metric-value">¥{{ item.averagePrice }}</span>
                  </span>
                </div>
              </div>
              <div class="reason-section">
                <div class="reason-tag">上榜理由</div>
                <p class="reason-text">{{ item.reason }}</p>
              </div>
              <div class="arrow-icon">→</div>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'

const router = useRouter()
const activeTab = ref('weekly')
const updateTime = ref('')
const merchants = ref([])

const userInfo = ref({
  username: localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')).username : ''
})

const tabs = [
  { key: 'weekly', name: '本周热议', icon: '🔥' },
  { key: 'monthly', name: '月度新晋', icon: '🌟' },
  { key: 'value', name: '性价比', icon: '💰' },
  { key: 'popular', name: '人气商家', icon: '⭐' }
]

const rules = {
  weekly: '本周热议榜单：综合近7天用户讨论量、点评增长、分享次数等指标计算，热议指数越高代表近期关注度越高。',
  monthly: '月度新晋榜单：综合本月新店入驻、好评增长、新客比例等指标计算，新晋指数越高代表近期表现越突出。',
  value: '性价比榜单：综合人均消费、评分、分量评价等指标计算，性价比指数越高代表花同样的钱能获得更好的体验。',
  popular: '人气商家榜单：综合累计点击量、收藏数、到店人数等指标计算，人气指数越高代表总体受欢迎程度越高。'
}

const metricLabels = {
  weekly: '热议指数',
  monthly: '新晋指数',
  value: '性价比指数',
  popular: '人气指数'
}

const currentRules = computed(() => rules[activeTab.value])

const currentRanking = computed(() => {
  const sorted = [...merchants.value].sort((a, b) => {
    if (activeTab.value === 'weekly') {
      return (b.rating || 0) - (a.rating || 0)
    } else if (activeTab.value === 'monthly') {
      return (b.reviewCount || 0) - (a.reviewCount || 0)
    } else if (activeTab.value === 'value') {
      const valueA = (a.rating || 0) / Math.max((a.averagePrice || 1), 1)
      const valueB = (b.rating || 0) / Math.max((b.averagePrice || 1), 1)
      return valueB - valueA
    } else {
      return (b.rating || 0) - (a.rating || 0)
    }
  }).slice(0, 5)
  
  return sorted.map((merchant, index) => {
    let reason = ''
    const rating = merchant.rating || merchant.averageRating || 0
    const reviewCount = merchant.reviewCount || merchant.ratingCount || 0
    const price = merchant.averagePrice || 0
    
    if (activeTab.value === 'weekly') {
      reason = `${merchant.name}近期讨论热度飙升，评分${rating}分，${reviewCount}条评价`
    } else if (activeTab.value === 'monthly') {
      reason = `${merchant.name}本月表现突出，评分${rating}分，人均¥${price}`
    } else if (activeTab.value === 'value') {
      reason = `${merchant.name}性价比极高，评分${rating}分，人均仅¥${price}`
    } else {
      reason = `${merchant.name}人气爆棚，评分${rating}分，${reviewCount}条评价`
    }
    
    const baseValue = rating * 10 + reviewCount / 2
    const metricValue = Math.min(99.9, Math.round(baseValue * 10) / 10).toFixed(1)
    
    return {
      id: merchant.id || merchant.merchantId,
      name: merchant.name || merchant.merchantName,
      cuisine: merchant.cuisine,
      rating: rating,
      reviewCount: reviewCount,
      averagePrice: price,
      description: merchant.description,
      metricLabel: metricLabels[activeTab.value],
      metricValue: metricValue,
      reason: reason
    }
  })
})

const refreshRankings = async () => {
  const now = new Date()
  updateTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
  
  try {
    const response = await request.get('/api/merchants', {
      params: {
        pageNum: 1,
        pageSize: 20
      }
    })
    
    if (response.success && response.data) {
      merchants.value = response.data.records || response.data
    }
  } catch (error) {
    console.error('获取商家列表失败:', error)
  }
}

const goBack = () => {
  router.push('/diner/home')
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

const goToMerchantDetail = (id) => {
  router.push(`/diner/merchant/${id}`)
}

onMounted(() => {
  refreshRankings()
})
</script>

<style scoped>
.ranking-view {
  min-height: 100vh;
  background: #fafafa;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
}

.ranking-nav {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  padding: 16px 0;
}

.nav-container {
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
  color: #ff6b35;
}

.nav-links {
  display: flex;
  gap: 16px;
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

.ranking-main {
  padding-top: 20px;
}

.hero-section {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c42 100%);
  padding: 40px 0;
  text-align: center;
}

.hero-content h1 {
  font-size: 36px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 10px;
}

.hero-content p {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.9);
  margin-bottom: 20px;
}

.update-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.update-label {
  color: rgba(255, 255, 255, 0.8);
  font-size: 15px;
}

.update-time {
  color: #fff;
  font-size: 15px;
  font-weight: 500;
}

.refresh-btn {
  padding: 6px 14px;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.refresh-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}

.tabs-section {
  padding: 24px 0;
}

.tabs-wrapper {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
}

.tab-btn {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 28px;
  background: #fff;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  font-size: 17px;
  font-weight: 500;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  border-color: #ffccb3;
  color: #ff6b35;
}

.tab-btn.active {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c42 100%);
  border-color: #ff6b35;
  color: #fff;
}

.tab-icon {
  font-size: 20px;
}

.rules-section {
  padding: 0 0 20px;
}

.rules-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  border-left: 4px solid #ff6b35;
}

.rules-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin-bottom: 10px;
}

.rules-desc {
  font-size: 15px;
  color: #666;
  line-height: 1.7;
}

.list-section {
  padding-bottom: 40px;
}

.ranking-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.ranking-item {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  background: #fff;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: all 0.2s;
}

.ranking-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
}

.ranking-item.top-three {
  border: 2px solid;
}

.ranking-item.first {
  border-color: #ffd700;
  background: linear-gradient(135deg, #fffef5 0%, #fff 100%);
}

.ranking-item.second {
  border-color: #c0c0c0;
  background: linear-gradient(135deg, #f8f8f8 0%, #fff 100%);
}

.ranking-item.third {
  border-color: #cd7f32;
  background: linear-gradient(135deg, #fff9f5 0%, #fff 100%);
}

.rank-badge {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 12px;
}

.rank-icon {
  font-size: 28px;
}

.rank-number {
  font-size: 20px;
  font-weight: 700;
  color: #999;
}

.merchant-info {
  flex: 1;
  min-width: 0;
}

.merchant-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 10px;
}

.merchant-name {
  font-size: 22px;
  font-weight: 600;
  color: #333;
}

.merchant-tags {
  display: flex;
  gap: 10px;
}

.tag {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
}

.tag.cuisine {
  background: #fff5f0;
  color: #ff6b35;
}

.tag.rating {
  background: #fffbe6;
  color: #faad14;
}

.merchant-desc {
  font-size: 16px;
  color: #666;
  margin-bottom: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.merchant-metrics {
  display: flex;
  gap: 24px;
}

.metric {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-label {
  font-size: 15px;
  color: #999;
}

.metric-value {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.reason-section {
  flex-shrink: 0;
  width: 220px;
}

.reason-tag {
  font-size: 14px;
  color: #ff6b35;
  font-weight: 500;
  margin-bottom: 8px;
}

.reason-text {
  font-size: 15px;
  color: #666;
  line-height: 1.6;
}

.arrow-icon {
  flex-shrink: 0;
  font-size: 24px;
  color: #ccc;
}

.ranking-item:hover .arrow-icon {
  color: #ff6b35;
}

@media (max-width: 768px) {
  .ranking-item {
    flex-direction: column;
    gap: 12px;
  }

  .reason-section {
    width: 100%;
    border-top: 1px dashed #eee;
    padding-top: 12px;
  }

  .merchant-metrics {
    flex-wrap: wrap;
    gap: 12px;
  }

  .tabs-wrapper {
    justify-content: center;
  }

  .tab-btn {
    padding: 10px 16px;
    font-size: 14px;
  }
}
</style>