<template>
  <MerchantLayout title="店铺首页" subtitle="查看店铺运营数据和经营概览">
    <div class="dashboard-container">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon orders-icon">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>
          <div class="stat-info">
            <p class="stat-label">今日订单</p>
            <p class="stat-value">{{ stats.todayOrders || 0 }}</p>
            <p class="stat-change positive">↑ {{ stats.orderGrowth || 0 }}%</p>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon revenue-icon">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <line x1="12" y1="1" x2="12" y2="23"></line>
              <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
            </svg>
          </div>
          <div class="stat-info">
            <p class="stat-label">今日营收</p>
            <p class="stat-value">¥{{ stats.todayRevenue || 0 }}</p>
            <p class="stat-change positive">↑ {{ stats.revenueGrowth || 0 }}%</p>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon reviews-icon">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
              <polyline points="14 2 14 8 20 8"></polyline>
              <line x1="16" y1="13" x2="8" y2="13"></line>
              <line x1="16" y1="17" x2="8" y2="17"></line>
              <polyline points="10 9 9 9 8 9"></polyline>
            </svg>
          </div>
          <div class="stat-info">
            <p class="stat-label">本月评价</p>
            <p class="stat-value">{{ stats.monthReviews || 0 }}</p>
            <p class="stat-change positive">↑ {{ stats.reviewGrowth || 0 }}%</p>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon rating-icon">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
            </svg>
          </div>
          <div class="stat-info">
            <p class="stat-label">店铺评分</p>
            <p class="stat-value">{{ stats.avgRating || 0.0 }}</p>
            <p class="stat-change neutral">{{ stats.ratingChange || 0 }}</p>
          </div>
        </div>
      </div>

      <div class="charts-section">
        <div class="chart-card">
          <div class="chart-header">
            <h3>营收趋势</h3>
            <div class="chart-tabs">
              <button 
                v-for="tab in timeTabs" 
                :key="tab.value"
                :class="['tab-btn', { active: activeTimeTab === tab.value }]"
                @click="activeTimeTab = tab.value"
              >{{ tab.label }}</button>
            </div>
          </div>
          <div class="chart-content">
            <svg viewBox="0 0 600 300" class="line-chart">
              <defs>
                <linearGradient id="revenueGradient" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#52c41a;stop-opacity:0.3" />
                  <stop offset="100%" style="stop-color:#52c41a;stop-opacity:0" />
                </linearGradient>
              </defs>
              <g class="grid-lines">
                <line v-for="i in 5" :key="'h'+i" x1="50" :y1="30 + i * 50" x2="550" :y2="30 + i * 50" stroke="#eee" stroke-width="1" />
              </g>
              <g class="y-axis">
                <text v-for="i in 5" :key="'y'+i" x="40" :y="35 + i * 50" fill="#999" font-size="12" text-anchor="end">{{ (5 - i) * 2000 }}</text>
              </g>
              <g class="x-axis">
                <text v-for="(day, index) in revenueTrend" :key="'x'+index" :x="50 + index * 80" y="280" fill="#999" font-size="12" text-anchor="middle">{{ day.label }}</text>
              </g>
              <path 
                :d="areaPath" 
                fill="url(#revenueGradient)"
                stroke="#52c41a"
                stroke-width="2"
              />
              <circle 
                v-for="(day, index) in revenueTrend" 
                :key="'dot'+index"
                :cx="50 + index * 80"
                :cy="250 - (day.value / 10000) * 220"
                r="4"
                fill="#52c41a"
              />
            </svg>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>评价分布</h3>
          </div>
          <div class="chart-content">
            <div class="rating-distribution">
              <div v-for="rating in ratingDistribution" :key="rating.star" class="rating-bar-item">
                <span class="rating-label">{{ rating.star }}星</span>
                <div class="rating-bar">
                  <div class="rating-bar-fill" :style="{ width: rating.percentage + '%', background: getRatingColor(rating.star) }"></div>
                </div>
                <span class="rating-count">{{ rating.count }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="bottom-section">
        <div class="card">
          <div class="card-header">
            <h3>最新评价</h3>
          </div>
          <div class="card-content">
            <div v-if="latestReviews.length > 0" class="review-list">
              <div v-for="review in latestReviews" :key="review.id" class="review-item">
                <div class="review-rating">
                  <span v-for="i in 5" :key="i" class="star" :class="{ filled: i <= review.rating }">★</span>
                </div>
                <div class="review-content">
                  <p class="review-text">{{ review.content }}</p>
                  <p class="review-meta">{{ review.username }} · {{ review.createdAt }}</p>
                </div>
              </div>
            </div>
            <div v-else class="empty-state">
              <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
              </svg>
              <p>暂无评价</p>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <h3>经营建议</h3>
          </div>
          <div class="card-content">
            <div v-if="businessAdvice.length > 0" class="advice-list">
              <div v-for="(advice, index) in businessAdvice" :key="index" class="advice-item">
                <div class="advice-icon" :class="advice.type">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fff" stroke-width="2">
                    <path v-if="advice.type === 'warning'" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"></path>
                    <path v-else d="M9 12l2 2 4-4m6 2a9 9 0 1 1-18 0 9 9 0 0 1 18 0z"></path>
                  </svg>
                </div>
                <p class="advice-text">{{ advice.content }}</p>
              </div>
            </div>
            <div v-else class="empty-state">
              <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="2">
                <path d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 1 1 7.072 0l-.548.547A3.374 3.374 0 0 0 14 18.469V19a2 2 0 1 1-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z"></path>
              </svg>
              <p>暂无建议</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import MerchantLayout from '../../components/MerchantLayout.vue'

const stats = ref({
  todayOrders: 12,
  todayRevenue: 2380,
  monthReviews: 45,
  avgRating: 4.5,
  orderGrowth: 15,
  revenueGrowth: 12,
  reviewGrowth: 8,
  ratingChange: '+0.1'
})

const activeTimeTab = ref('week')
const timeTabs = [
  { label: '本周', value: 'week' },
  { label: '本月', value: 'month' },
  { label: '本季度', value: 'quarter' }
]

const revenueTrend = ref([
  { label: '周一', value: 3200 },
  { label: '周二', value: 4500 },
  { label: '周三', value: 3800 },
  { label: '周四', value: 5200 },
  { label: '周五', value: 6800 },
  { label: '周六', value: 8500 },
  { label: '周日', value: 7200 }
])

const ratingDistribution = ref([
  { star: 5, count: 28, percentage: 62 },
  { star: 4, count: 12, percentage: 27 },
  { star: 3, count: 4, percentage: 9 },
  { star: 2, count: 1, percentage: 2 },
  { star: 1, count: 0, percentage: 0 }
])

const latestReviews = ref([
  { id: 1, rating: 5, content: '菜品味道很好，服务态度也不错，下次还会再来！', username: '吃货小明', createdAt: '2026-07-18 14:30' },
  { id: 2, rating: 4, content: '环境整洁，上菜速度快，整体体验满意。', username: '美食家阿华', createdAt: '2026-07-18 12:15' },
  { id: 3, rating: 5, content: '性价比很高，推荐他们家的招牌菜！', username: '探店达人', createdAt: '2026-07-17 20:00' }
])

const businessAdvice = ref([
  { type: 'success', content: '周末客流量较高，建议增加人手准备' },
  { type: 'warning', content: '差评主要集中在上菜速度，建议优化后厨流程' },
  { type: 'success', content: '招牌菜好评率达95%，建议加大推广' }
])

const areaPath = computed(() => {
  const points = revenueTrend.value.map((day, index) => {
    const x = 50 + index * 80
    const y = 250 - (day.value / 10000) * 220
    return `${index === 0 ? 'M' : 'L'} ${x} ${y}`
  }).join(' ')
  return points + ' L 510 250 L 50 250 Z'
})

const getRatingColor = (star) => {
  const colors = {
    5: '#52c41a',
    4: '#73d13d',
    3: '#faad14',
    2: '#ffa940',
    1: '#ff4d4f'
  }
  return colors[star] || '#ccc'
}

onMounted(() => {
})
</script>

<style scoped>
.dashboard-container {
  width: 100%;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.orders-icon {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
}

.revenue-icon {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
}

.reviews-icon {
  background: linear-gradient(135deg, #faad14 0%, #ffc53d 100%);
}

.rating-icon {
  background: linear-gradient(135deg, #722ed1 0%, #9254de 100%);
}

.stat-info {
  flex: 1;
}

.stat-label {
  font-size: 14px;
  color: #667085;
  margin: 0;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2d3d;
  margin: 4px 0 0;
}

.stat-change {
  font-size: 13px;
  margin: 4px 0 0;
}

.stat-change.positive {
  color: #52c41a;
}

.stat-change.negative {
  color: #ff4d4f;
}

.stat-change.neutral {
  color: #667085;
}

.charts-section {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
  margin-bottom: 24px;
}

.chart-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.chart-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.chart-tabs {
  display: flex;
  gap: 8px;
}

.tab-btn {
  padding: 6px 12px;
  font-size: 13px;
  color: #667085;
  background: #f5f7fa;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn:hover {
  background: #eef2f7;
}

.tab-btn.active {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
}

.chart-content {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.line-chart {
  width: 100%;
  height: 100%;
}

.rating-distribution {
  width: 100%;
}

.rating-bar-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.rating-label {
  width: 40px;
  font-size: 13px;
  color: #667085;
}

.rating-bar {
  flex: 1;
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.rating-bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}

.rating-count {
  width: 30px;
  font-size: 13px;
  color: #667085;
  text-align: right;
}

.bottom-section {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
}

.card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.card-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.card-header h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.card-content {
  padding: 20px 24px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.review-item {
  display: flex;
  gap: 16px;
}

.review-rating {
  flex-shrink: 0;
}

.star {
  font-size: 16px;
  color: #e0e0e0;
}

.star.filled {
  color: #faad14;
}

.review-content {
  flex: 1;
}

.review-text {
  font-size: 14px;
  color: #1f2d3d;
  margin: 0 0 8px;
  line-height: 1.5;
}

.review-meta {
  font-size: 12px;
  color: #999;
  margin: 0;
}

.advice-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.advice-item {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.advice-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.advice-icon.warning {
  background: #fff7e6;
}

.advice-icon.warning svg {
  stroke: #faad14;
}

.advice-icon.success {
  background: #f6ffed;
}

.advice-icon.success svg {
  stroke: #52c41a;
}

.advice-text {
  font-size: 14px;
  color: #1f2d3d;
  margin: 0;
  line-height: 1.5;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
}

.empty-state svg {
  margin-bottom: 12px;
}

.empty-state p {
  font-size: 14px;
  color: #999;
  margin: 0;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .charts-section {
    grid-template-columns: 1fr;
  }
  
  .bottom-section {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>