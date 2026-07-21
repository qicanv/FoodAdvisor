<template>
  <AdminLayout title="用户行为分析" subtitle="分析用户搜索、点击和反馈行为，优化推荐功能和运营内容">
    <div class="filter-section">
      <div class="filter-row">
        <div class="filter-item">
          <label>开始时间</label>
          <input type="datetime-local" v-model="filters.startTime" class="filter-input" @change="loadData" />
        </div>
        <div class="filter-item">
          <label>结束时间</label>
          <input type="datetime-local" v-model="filters.endTime" class="filter-input" @change="loadData" />
        </div>
        <div class="filter-item filter-actions">
          <button class="btn-reset" @click="resetFilters">重置</button>
          <button class="btn-search" @click="loadData">查询</button>
        </div>
      </div>
    </div>

    <div class="stats-cards">
      <div class="stat-card stat-card-blue">
        <div class="stat-icon">🔍</div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.totalSearches || 0 }}</div>
          <div class="stat-label">搜索次数</div>
        </div>
      </div>
      <div class="stat-card stat-card-green">
        <div class="stat-icon">👆</div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.totalClicks || 0 }}</div>
          <div class="stat-label">点击次数</div>
        </div>
      </div>
      <div class="stat-card stat-card-orange">
        <div class="stat-icon">💬</div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.totalFeedbacks || 0 }}</div>
          <div class="stat-label">反馈次数</div>
        </div>
      </div>
      <div class="stat-card stat-card-purple">
        <div class="stat-icon">👤</div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.activeUsers || 0 }}</div>
          <div class="stat-label">活跃用户</div>
        </div>
      </div>
      <div class="stat-card stat-card-cyan">
        <div class="stat-icon">📊</div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.avgSearchesPerUser || 0 }}</div>
          <div class="stat-label">人均搜索</div>
        </div>
      </div>
      <div class="stat-card stat-card-pink">
        <div class="stat-icon">📈</div>
        <div class="stat-info">
          <div class="stat-value">{{ overview.avgClicksPerSearch || 0 }}%</div>
          <div class="stat-label">搜索转点击</div>
        </div>
      </div>
    </div>

    <div class="section-row">
      <div class="section-card">
        <div class="section-header">
          <h2 class="section-title">🔥 热门搜索词</h2>
          <span class="section-count">TOP 10</span>
        </div>
        <div class="keyword-list">
          <div v-for="(item, index) in hotKeywords" :key="index" class="keyword-item">
            <span :class="['keyword-rank', 'rank-' + (index + 1)]">{{ index + 1 }}</span>
            <span class="keyword-text">{{ item.keyword }}</span>
            <span class="keyword-count">{{ item.count }} 次</span>
            <span :class="['keyword-trend', item.trend >= 0 ? 'up' : 'down']">
              {{ item.trend >= 0 ? '↑' : '↓' }} {{ Math.abs(item.trend) }}%
            </span>
          </div>
        </div>
      </div>

      <div class="section-card">
        <div class="section-header">
          <h2 class="section-title">🎯 热门场景</h2>
          <span class="section-count">共 {{ scenarioTotal }} 次</span>
        </div>
        <div class="scenario-list">
          <div v-for="(item, index) in hotScenarios" :key="index" class="scenario-item">
            <div class="scenario-info">
              <span class="scenario-name">{{ item.scenario }}</span>
              <span class="scenario-count">{{ item.count }} 次 ({{ item.percentage }}%)</span>
            </div>
            <div class="scenario-bar">
              <div class="scenario-fill" :style="{ width: item.percentage + '%' }"></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="section-row">
      <div class="section-card full-width">
        <div class="section-header">
          <h2 class="section-title">🏪 热门商家推荐点击</h2>
          <span class="section-count">共 {{ merchantTotalClicks }} 次点击</span>
        </div>
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>排名</th>
                <th>商家名称</th>
                <th>分类</th>
                <th>推荐点击</th>
                <th>推荐展示</th>
                <th>转化率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in hotMerchants" :key="item.merchantId" class="table-row">
                <td>
                  <span :class="['rank-badge', 'rank-' + (index + 1)]">{{ index + 1 }}</span>
                </td>
                <td class="cell-merchant">{{ item.merchantName }}</td>
                <td>
                  <span class="category-badge">{{ item.category }}</span>
                </td>
                <td>{{ item.clickCount }}</td>
                <td>{{ item.viewCount }}</td>
                <td>
                  <span :class="['conversion-badge', item.conversionRate >= 15 ? 'high' : (item.conversionRate >= 10 ? 'medium' : 'low')]">
                    {{ item.conversionRate }}%
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="section-row">
      <div class="section-card">
        <div class="section-header">
          <h2 class="section-title">📈 推荐统计</h2>
        </div>
        <div class="recommendation-stats">
          <div class="rec-stat-item">
            <div class="rec-stat-label">总推荐次数</div>
            <div class="rec-stat-value">{{ recStats.totalRecommendations || 0 }}</div>
          </div>
          <div class="rec-stat-item">
            <div class="rec-stat-label">总点击次数</div>
            <div class="rec-stat-value rec-value-click">{{ recStats.totalClicks || 0 }}</div>
          </div>
          <div class="rec-stat-item">
            <div class="rec-stat-label">总反馈次数</div>
            <div class="rec-stat-value rec-value-feedback">{{ recStats.totalFeedbacks || 0 }}</div>
          </div>
          <div class="rec-stat-item">
            <div class="rec-stat-label">点击转化率</div>
            <div class="rec-stat-value rec-value-rate">{{ recStats.clickConversionRate || 0 }}%</div>
          </div>
          <div class="rec-stat-item">
            <div class="rec-stat-label">反馈率</div>
            <div class="rec-stat-value">{{ recStats.feedbackRate || 0 }}%</div>
          </div>
          <div class="rec-stat-item">
            <div class="rec-stat-label">好评率</div>
            <div class="rec-stat-value rec-value-positive">{{ recStats.positiveRate || 0 }}%</div>
          </div>
        </div>
      </div>

      <div class="section-card">
        <div class="section-header">
          <h2 class="section-title">📅 每日趋势</h2>
        </div>
        <div class="trend-chart">
          <div v-for="(item, index) in recStats.dailyTrend" :key="index" class="trend-bar-item">
            <div class="trend-bar-container">
              <div class="trend-bar trend-bar-rec" :style="{ height: (item.recommendations / maxRecommendations * 100) + '%' }" title="推荐次数: {{ item.recommendations }}"></div>
              <div class="trend-bar trend-bar-click" :style="{ height: (item.clicks / maxClicks * 100) + '%' }" title="点击次数: {{ item.clicks }}"></div>
            </div>
            <span class="trend-label">{{ item.date.slice(5) }}</span>
          </div>
        </div>
        <div class="trend-legend">
          <span class="legend-item"><span class="legend-color rec"></span> 推荐</span>
          <span class="legend-item"><span class="legend-color click"></span> 点击</span>
        </div>
      </div>
    </div>

    <div class="section-row">
      <div class="section-card">
        <div class="section-header">
          <h2 class="section-title">🍽️ 搜索按菜系分布</h2>
        </div>
        <div class="pie-chart-container">
          <div class="pie-visual">
            <svg viewBox="0 0 200 200" class="pie-svg">
              <circle cx="100" cy="100" r="70" fill="none" stroke="#f0f0f0" stroke-width="30" />
              <circle
                v-for="(segment, index) in cuisineSegments"
                :key="index"
                cx="100"
                cy="100"
                r="70"
                fill="none"
                :stroke="segment.color"
                stroke-width="30"
                :stroke-dasharray="segment.dashArray"
                :stroke-dashoffset="segment.dashOffset"
                :transform="`rotate(-90 100 100)`"
                class="pie-segment"
              />
              <text x="100" y="95" text-anchor="middle" class="pie-center-label">{{ cuisineTotal }}</text>
              <text x="100" y="115" text-anchor="middle" class="pie-center-sub">总搜索</text>
            </svg>
          </div>
          <div class="pie-legend">
            <div v-for="(item, index) in cuisineDataList" :key="index" class="pie-legend-item">
              <span class="pie-legend-color" :style="{ backgroundColor: item.color }"></span>
              <span class="pie-legend-label">{{ item.name }}</span>
              <span class="pie-legend-value">{{ item.count }}</span>
              <span class="pie-legend-percent">{{ item.percent }}%</span>
            </div>
            <div v-if="Object.keys(overview.searchByCuisine).length === 0" class="empty-state">
              <span class="empty-icon">📊</span>
              <span class="empty-text">暂无数据</span>
            </div>
          </div>
        </div>
      </div>

      <div class="section-card">
        <div class="section-header">
          <h2 class="section-title">💰 搜索按价格分布</h2>
        </div>
        <div class="pie-chart-container">
          <div class="pie-visual">
            <svg viewBox="0 0 200 200" class="pie-svg">
              <circle cx="100" cy="100" r="70" fill="none" stroke="#f0f0f0" stroke-width="30" />
              <circle
                v-for="(segment, index) in priceSegments"
                :key="index"
                cx="100"
                cy="100"
                r="70"
                fill="none"
                :stroke="segment.color"
                stroke-width="30"
                :stroke-dasharray="segment.dashArray"
                :stroke-dashoffset="segment.dashOffset"
                :transform="`rotate(-90 100 100)`"
                class="pie-segment"
              />
              <text x="100" y="95" text-anchor="middle" class="pie-center-label">{{ priceTotal }}</text>
              <text x="100" y="115" text-anchor="middle" class="pie-center-sub">总搜索</text>
            </svg>
          </div>
          <div class="pie-legend">
            <div v-for="(item, index) in priceDataList" :key="index" class="pie-legend-item">
              <span class="pie-legend-color" :style="{ backgroundColor: item.color }"></span>
              <span class="pie-legend-label">{{ item.name }}</span>
              <span class="pie-legend-value">{{ item.count }}</span>
              <span class="pie-legend-percent">{{ item.percent }}%</span>
            </div>
            <div v-if="Object.keys(overview.searchByPriceRange).length === 0" class="empty-state">
              <span class="empty-icon">📊</span>
              <span class="empty-text">暂无数据</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showMessage" class="message-toast" :class="messageType">
      {{ messageText }}
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getBehaviorOverview, getHotSearchKeywords, getHotScenarios, getHotMerchants, getRecommendationStats } from '../../api/behavior'

const overview = reactive({
  totalSearches: 0,
  totalClicks: 0,
  totalFeedbacks: 0,
  activeUsers: 0,
  avgSearchesPerUser: 0,
  avgClicksPerSearch: 0,
  searchByCuisine: {},
  searchByPriceRange: {}
})

const hotKeywords = ref([])
const hotScenarios = ref([])
const hotMerchants = ref([])
const recStats = reactive({
  totalRecommendations: 0,
  totalClicks: 0,
  totalFeedbacks: 0,
  positiveFeedbacks: 0,
  negativeFeedbacks: 0,
  clickConversionRate: 0,
  feedbackRate: 0,
  positiveRate: 0,
  dailyTrend: []
})

const filters = reactive({
  startTime: '',
  endTime: ''
})

const showMessage = ref(false)
const messageText = ref('')
const messageType = ref('success')

const scenarioTotal = computed(() => {
  return hotScenarios.value.reduce((sum, item) => sum + item.count, 0)
})

const merchantTotalClicks = computed(() => {
  return hotMerchants.value.reduce((sum, item) => sum + item.clickCount, 0)
})

const maxRecommendations = computed(() => {
  const trend = recStats.dailyTrend || []
  return trend.length > 0 ? Math.max(...trend.map(t => t.recommendations)) : 1
})

const maxClicks = computed(() => {
  const trend = recStats.dailyTrend || []
  return trend.length > 0 ? Math.max(...trend.map(t => t.clicks)) : 1
})

const maxPriceCount = computed(() => {
  const prices = overview.searchByPriceRange || {}
  const counts = Object.values(prices)
  return counts.length > 0 ? Math.max(...counts) : 1
})

const cuisineTotal = computed(() => {
  const cuisines = overview.searchByCuisine || {}
  return Object.values(cuisines).reduce((sum, count) => sum + count, 0)
})

const priceTotal = computed(() => {
  const prices = overview.searchByPriceRange || {}
  return Object.values(prices).reduce((sum, count) => sum + count, 0)
})

const cuisineColors = {
  '川菜': '#f5222d',
  '火锅': '#fa8c16',
  '日料': '#eb2f96',
  '烧烤': '#13c2c2',
  '粤菜': '#1890ff',
  '西餐': '#5c3377',
  '其他': '#8f959e'
}

const priceColors = {
  '0-50': '#52c41a',
  '50-100': '#1890ff',
  '100-200': '#fa8c16',
  '200-500': '#eb2f96',
  '500+': '#5c3377'
}

const cuisineDataList = computed(() => {
  const cuisines = overview.searchByCuisine || {}
  return Object.entries(cuisines).map(([name, count]) => ({
    name,
    count,
    color: cuisineColors[name] || '#8f959e',
    percent: cuisineTotal.value > 0 ? ((count / cuisineTotal.value) * 100).toFixed(1) : '0'
  }))
})

const priceDataList = computed(() => {
  const prices = overview.searchByPriceRange || {}
  return Object.entries(prices).map(([name, count]) => ({
    name,
    count,
    color: priceColors[name] || '#8f959e',
    percent: priceTotal.value > 0 ? ((count / priceTotal.value) * 100).toFixed(1) : '0'
  }))
})

const generatePieSegments = (dataList, total) => {
  if (total === 0) return []
  const circumference = 2 * Math.PI * 70
  let offset = 0
  return dataList.map(item => {
    const percent = item.count / total
    const dashArray = `${percent * circumference} ${circumference}`
    const dashOffset = -offset
    offset += percent * circumference
    return {
      color: item.color,
      dashArray,
      dashOffset
    }
  })
}

const cuisineSegments = computed(() => {
  return generatePieSegments(cuisineDataList.value, cuisineTotal.value)
})

const priceSegments = computed(() => {
  return generatePieSegments(priceDataList.value, priceTotal.value)
})

const getCuisineColor = (cuisine) => {
  const colors = {
    '川菜': '#f5222d',
    '火锅': '#fa8c16',
    '日料': '#eb2f96',
    '烧烤': '#13c2c2',
    '粤菜': '#1890ff',
    '西餐': '#5c3377',
    '其他': '#8f959e'
  }
  return colors[cuisine] || '#8f959e'
}

const showToast = (text, type = 'success') => {
  messageText.value = text
  messageType.value = type
  showMessage.value = true
  setTimeout(() => {
    showMessage.value = false
  }, 3000)
}

const resetFilters = () => {
  filters.startTime = ''
  filters.endTime = ''
  loadData()
}

const loadData = async () => {
  try {
    const params = {
      startTime: filters.startTime || undefined,
      endTime: filters.endTime || undefined
    }

    const [overviewRes, keywordsRes, scenariosRes, merchantsRes, recStatsRes] = await Promise.all([
      getBehaviorOverview(params),
      getHotSearchKeywords(params),
      getHotScenarios(params),
      getHotMerchants(params),
      getRecommendationStats(params)
    ])

    if (overviewRes.success && overviewRes.data) {
      Object.assign(overview, overviewRes.data)
    }

    if (keywordsRes.success && keywordsRes.data) {
      hotKeywords.value = keywordsRes.data.keywords || []
    }

    if (scenariosRes.success && scenariosRes.data) {
      hotScenarios.value = scenariosRes.data.scenarios || []
    }

    if (merchantsRes.success && merchantsRes.data) {
      hotMerchants.value = merchantsRes.data.merchants || []
    }

    if (recStatsRes.success && recStatsRes.data) {
      Object.assign(recStats, recStatsRes.data)
    }
  } catch (error) {
    console.error('加载行为分析数据失败:', error)
    showToast('加载失败，请重试', 'error')
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.filter-section {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: flex-end;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-item label {
  font-size: 13px;
  font-weight: 500;
  color: #4e5969;
}

.filter-input {
  padding: 10px 12px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  font-size: 14px;
  color: #1f2329;
  background: #fff;
}

.filter-input:focus {
  outline: none;
  border-color: #1890ff;
}

.filter-actions {
  flex-direction: row;
  gap: 10px;
}

.btn-reset, .btn-search {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid #e5e6eb;
}

.btn-reset {
  background: #f7f8fa;
  color: #4e5969;
}

.btn-reset:hover {
  background: #f0f0f0;
}

.btn-search {
  background: #1890ff;
  color: #fff;
  border-color: #1890ff;
}

.btn-search:hover {
  background: #40a9ff;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.stat-card-blue .stat-icon {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
}

.stat-card-green .stat-icon {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
}

.stat-card-orange .stat-icon {
  background: linear-gradient(135deg, #fa8c16 0%, #ffa940 100%);
}

.stat-card-purple .stat-icon {
  background: linear-gradient(135deg, #5c3377 0%, #7b5ca5 100%);
}

.stat-card-cyan .stat-icon {
  background: linear-gradient(135deg, #13c2c2 0%, #36cfc9 100%);
}

.stat-card-pink .stat-icon {
  background: linear-gradient(135deg, #eb2f96 0%, #ff7eb3 100%);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1f2329;
}

.stat-label {
  font-size: 13px;
  color: #8f959e;
  margin-top: 4px;
}

.section-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  margin-bottom: 24px;
}

.section-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.section-card.full-width {
  grid-column: span 2;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
  margin: 0;
}

.section-count {
  font-size: 14px;
  color: #8f959e;
}

.keyword-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.keyword-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #f7f8fa;
  border-radius: 8px;
}

.keyword-rank {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
}

.rank-1 {
  background: linear-gradient(135deg, #f5222d 0%, #ff4d4f 100%);
}

.rank-2 {
  background: linear-gradient(135deg, #fa8c16 0%, #ffa940 100%);
}

.rank-3 {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
}

.rank-4, .rank-5 {
  background: #8f959e;
}

.rank-6, .rank-7, .rank-8, .rank-9, .rank-10 {
  background: #d9d9d9;
  color: #8f959e;
}

.keyword-text {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: #1f2329;
}

.keyword-count {
  font-size: 14px;
  color: #8f959e;
}

.keyword-trend {
  font-size: 13px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
}

.keyword-trend.up {
  color: #52c41a;
  background: rgba(82, 196, 26, 0.1);
}

.keyword-trend.down {
  color: #f5222d;
  background: rgba(245, 34, 45, 0.1);
}

.scenario-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scenario-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.scenario-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.scenario-name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2329;
}

.scenario-count {
  font-size: 13px;
  color: #8f959e;
}

.scenario-bar {
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}

.scenario-fill {
  height: 100%;
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.table-container {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table thead th {
  padding: 14px 12px;
  text-align: left;
  font-size: 13px;
  font-weight: 600;
  color: #4e5969;
  background: #f7f8fa;
  border-bottom: 2px solid #e5e6eb;
}

.data-table tbody td {
  padding: 14px 12px;
  font-size: 14px;
  color: #1f2329;
  border-bottom: 1px solid #f0f0f0;
}

.table-row:hover {
  background: #f7f8fa;
}

.rank-badge {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
}

.category-badge {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  background: #f0f0f0;
  color: #4e5969;
}

.conversion-badge {
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.conversion-badge.high {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.conversion-badge.medium {
  background: rgba(250, 140, 22, 0.1);
  color: #fa8c16;
}

.conversion-badge.low {
  background: rgba(245, 34, 45, 0.1);
  color: #f5222d;
}

.recommendation-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.rec-stat-item {
  text-align: center;
  padding: 16px;
  background: #f7f8fa;
  border-radius: 8px;
}

.rec-stat-label {
  font-size: 13px;
  color: #8f959e;
  margin-bottom: 8px;
}

.rec-stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1f2329;
}

.rec-value-click {
  color: #1890ff;
}

.rec-value-feedback {
  color: #fa8c16;
}

.rec-value-rate {
  color: #52c41a;
}

.rec-value-positive {
  color: #52c41a;
}

.trend-chart {
  display: flex;
  justify-content: space-around;
  align-items: flex-end;
  height: 200px;
  padding: 20px 0;
}

.trend-bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.trend-bar-container {
  width: 32px;
  height: 150px;
  background: #f0f0f0;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 4px;
  padding: 4px;
}

.trend-bar {
  width: 100%;
  border-radius: 2px;
  transition: height 0.3s ease;
}

.trend-bar-rec {
  background: linear-gradient(180deg, #1890ff 0%, #40a9ff 100%);
}

.trend-bar-click {
  background: linear-gradient(180deg, #52c41a 0%, #73d13d 100%);
}

.trend-label {
  font-size: 12px;
  color: #8f959e;
}

.trend-legend {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #8f959e;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 2px;
}

.legend-color.rec {
  background: #1890ff;
}

.legend-color.click {
  background: #52c41a;
}

.pie-chart-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.pie-visual {
  width: 180px;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pie-svg {
  width: 100%;
  height: 100%;
}

.pie-segment {
  transition: stroke-dasharray 0.5s ease, stroke-dashoffset 0.5s ease;
}

.pie-center-label {
  font-size: 24px;
  font-weight: 700;
  fill: #1f2329;
}

.pie-center-sub {
  font-size: 12px;
  fill: #8f959e;
}

.pie-legend {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pie-legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pie-legend-color {
  width: 16px;
  height: 16px;
  border-radius: 4px;
}

.pie-legend-label {
  flex: 1;
  font-size: 14px;
  color: #1f2329;
}

.pie-legend-value {
  font-size: 14px;
  font-weight: 600;
  color: #4e5969;
  width: 50px;
  text-align: right;
}

.pie-legend-percent {
  font-size: 14px;
  font-weight: 500;
  color: #1890ff;
  width: 50px;
  text-align: right;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 30px;
  background: #f7f8fa;
  border-radius: 8px;
}

.empty-icon {
  font-size: 40px;
}

.empty-text {
  font-size: 14px;
  color: #8f959e;
}

.price-label {
  width: 60px;
  font-size: 13px;
  color: #4e5969;
  text-align: right;
}

.price-count {
  width: 50px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  text-align: right;
}

.message-toast {
  position: fixed;
  top: 20px;
  right: 20px;
  padding: 16px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  z-index: 2000;
  animation: fadeIn 0.3s ease;
}

.message-toast.success {
  background: rgba(82, 196, 26, 0.9);
  color: #fff;
}

.message-toast.error {
  background: rgba(245, 34, 45, 0.9);
  color: #fff;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1200px) {
  .stats-cards {
    grid-template-columns: repeat(3, 1fr);
  }

  .section-row {
    grid-template-columns: 1fr;
  }

  .section-card.full-width {
    grid-column: span 1;
  }
}

@media (max-width: 768px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .recommendation-stats {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>