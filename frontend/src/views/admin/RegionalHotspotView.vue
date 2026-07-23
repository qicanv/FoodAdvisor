<template>
  <AdminLayout title="区域热点分析" subtitle="分析各区域消费热点与热词趋势，助力制定内容和运营策略">
    <!-- Tab 导航 -->
    <div class="tab-nav">
      <button
        :class="['tab-btn', { active: activeTab === 'hotspot' }]"
        @click="activeTab = 'hotspot'"
      >
        📊 消费热点
      </button>
      <button
        :class="['tab-btn', { active: activeTab === 'hotwords' }]"
        @click="switchToHotWords"
      >
        🔥 热词榜单
      </button>
    </div>

    <!-- ==================== Tab 1: 消费热点洞察 ==================== -->
    <div v-show="activeTab === 'hotspot'">
      <div class="filter-section">
        <div class="filter-row">
          <div class="filter-item">
            <label>区域选择</label>
            <select v-model="selectedRegion" class="filter-select" @change="loadHotspots">
              <option v-for="region in regions" :key="region.code" :value="region.code">
                {{ region.name }}
              </option>
            </select>
          </div>
          <div class="filter-item">
            <label>时间范围</label>
            <select v-model="timeRange" class="filter-select" @change="loadHotspots">
              <option value="today">今日</option>
              <option value="yesterday">昨日</option>
              <option value="last7days">近7天</option>
              <option value="last30days">近30天</option>
              <option value="custom">自定义</option>
            </select>
          </div>
          <div v-if="timeRange === 'custom'" class="filter-item">
            <label>开始时间</label>
            <input type="datetime-local" v-model="customStart" class="filter-input" @change="loadHotspots" />
          </div>
          <div v-if="timeRange === 'custom'" class="filter-item">
            <label>结束时间</label>
            <input type="datetime-local" v-model="customEnd" class="filter-input" @change="loadHotspots" />
          </div>
        </div>
      </div>

      <div class="stats-cards">
        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);">📊</div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalEvents }}</div>
            <div class="stat-label">总事件数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);">👥</div>
          <div class="stat-info">
            <div class="stat-value">{{ stats.totalUsers }}</div>
            <div class="stat-label">活跃用户数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #fa8c16 0%, #ffa940 100%);">📍</div>
          <div class="stat-info">
            <div class="stat-value">{{ currentRegionName }}</div>
            <div class="stat-label">当前区域</div>
          </div>
        </div>
      </div>

      <div class="trend-chart-section">
        <h2 class="section-title">📈 趋势变化</h2>
        <div class="chart-container">
          <div ref="trendChartRef" class="trend-chart"></div>
        </div>
        <div class="trend-summary">
          <div v-for="trend in stats.trendChanges" :key="trend.name" class="trend-summary-item">
            <span class="trend-summary-name">{{ trend.name }}</span>
            <span :class="['trend-summary-badge', trend.trend.toLowerCase()]">
              {{ trend.trend === 'UP' ? '↑' : trend.trend === 'DOWN' ? '↓' : trend.trend === 'NEW' ? '+' : '→' }}
            </span>
            <span class="trend-summary-value">{{ trend.current }}</span>
            <span class="trend-summary-percent" :class="trend.changePercent >= 0 ? 'positive' : 'negative'">
              {{ trend.changePercent >= 0 ? '+' : '' }}{{ trend.changePercent }}%
            </span>
          </div>
        </div>
      </div>

      <div class="section-grid">
        <div class="section-card">
          <h2 class="section-title">🔥 热门商家</h2>
          <div class="hot-list">
            <div v-for="(item, index) in stats.hotMerchants" :key="item.merchantId" class="hot-item">
              <span class="hot-rank" :class="getRankClass(index)">{{ index + 1 }}</span>
              <div class="hot-info">
                <span class="hot-name">{{ item.merchantName || '未知商家' }}</span>
                <span v-if="item.cuisine" class="hot-cuisine">{{ item.cuisine }}</span>
              </div>
              <span class="hot-count">{{ item.count }}</span>
            </div>
            <div v-if="!stats.hotMerchants || stats.hotMerchants.length === 0" class="empty-state">
              暂无数据
            </div>
          </div>
        </div>

        <div class="section-card">
          <h2 class="section-title">🍽️ 热门菜系</h2>
          <div class="hot-list">
            <div v-for="(item, index) in stats.hotCuisines" :key="item.cuisine" class="hot-item">
              <span class="hot-rank" :class="getRankClass(index)">{{ index + 1 }}</span>
              <span class="hot-name">{{ item.cuisine || '未知' }}</span>
              <span class="hot-count">{{ item.count }}</span>
            </div>
            <div v-if="!stats.hotCuisines || stats.hotCuisines.length === 0" class="empty-state">
              暂无数据
            </div>
          </div>
        </div>

        <div class="section-card">
          <h2 class="section-title">🔍 热门搜索词</h2>
          <div class="hot-list">
            <div v-for="(item, index) in stats.hotKeywords" :key="item.keyword" class="hot-item">
              <span class="hot-rank" :class="getRankClass(index)">{{ index + 1 }}</span>
              <span class="hot-name">{{ item.keyword || '未知' }}</span>
              <span class="hot-count">{{ item.count }}</span>
            </div>
            <div v-if="!stats.hotKeywords || stats.hotKeywords.length === 0" class="empty-state">
              暂无数据
            </div>
          </div>
        </div>

        <div class="section-card">
          <h2 class="section-title">⏰ 消费时段分布</h2>
          <div class="period-chart">
            <div v-for="period in stats.consumptionPeriods" :key="period.period" class="period-item">
              <div class="period-label">{{ period.period }}</div>
              <div class="period-bar-container">
                <div class="period-bar" :style="{ width: getBarWidth(period.count) + '%' }"></div>
              </div>
              <div class="period-count">{{ period.count }}</div>
            </div>
            <div v-if="!stats.consumptionPeriods || stats.consumptionPeriods.length === 0" class="empty-state">
              暂无数据
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== Tab 2: 区域热词榜单 ==================== -->
    <div v-show="activeTab === 'hotwords'">
      <!-- 筛选区域 -->
      <div class="filter-section">
        <div class="filter-row">
          <div class="filter-item">
            <label>区域选择</label>
            <select v-model="hwFilter.regionCode" class="filter-select" @change="onRegionChange">
              <option v-for="r in hwRegions" :key="r.regionCode" :value="r.regionCode">
                {{ r.regionCode }}
              </option>
              <option v-if="hwRegions.length === 0" value="" disabled>暂无有热词数据的区域</option>
            </select>
          </div>
          <div class="filter-item">
            <label>热词分类</label>
            <select v-model="hwFilter.category" class="filter-select" @change="loadHotWords">
              <option value="">全部分类</option>
              <option value="TASTE">😋 口味</option>
              <option value="SERVICE">🤝 服务</option>
              <option value="ENVIRONMENT">🌿 环境</option>
              <option value="PRICE">💰 价格</option>
              <option value="SPEED">⏱️ 速度</option>
              <option value="GENERAL">📋 综合</option>
            </select>
          </div>
          <div class="filter-item">
            <label>统计周期</label>
            <select v-model="hwFilter.periodType" class="filter-select" @change="loadHotWords">
              <option value="DAILY">日榜</option>
              <option value="WEEKLY">周榜</option>
              <option value="MONTHLY">月榜</option>
            </select>
          </div>
          <div class="filter-item">
            <label>回溯天数</label>
            <select v-model="hwFilter.daysBack" class="filter-select" style="min-width: 120px;">
              <option :value="7">近7天</option>
              <option :value="30">近30天</option>
              <option :value="90">近90天</option>
              <option :value="180">近180天</option>
              <option :value="365">近一年</option>
            </select>
          </div>
          <div class="filter-item" style="align-self: flex-end;">
            <button class="regenerate-btn" @click="handleRegenerate" :disabled="hwRegenerating">
              {{ hwRegenerating ? '生成中...' : '🔄 重新生成热词' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 统计概览卡片 -->
      <div class="stats-cards hw-stats">
        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #722ed1 0%, #9254de 100%);">🔥</div>
          <div class="stat-info">
            <div class="stat-value">{{ hwPagination.total }}</div>
            <div class="stat-label">热词总数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #eb2f96 0%, #f759ab 100%);">🏷️</div>
          <div class="stat-info">
            <div class="stat-value">{{ hwCategoryCount }}</div>
            <div class="stat-label">覆盖分类数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: linear-gradient(135deg, #13c2c2 0%, #36cfc9 100%);">📍</div>
          <div class="stat-info">
            <div class="stat-value">{{ hwRegions.length || 0 }}</div>
            <div class="stat-label">有数据区域</div>
          </div>
        </div>
      </div>

      <!-- 热词列表 -->
      <div class="hw-table-section">
        <div class="hw-table-header">
          <h2 class="section-title" style="margin: 0;">🔥 区域热词榜单</h2>
          <span class="hw-period-badge">{{ periodLabel }}</span>
        </div>

        <div v-if="hwLoading" class="loading-state">
          <div class="loading-spinner"></div>
          <span>加载中...</span>
        </div>

        <div v-else-if="hwList.length === 0" class="empty-state-large">
          <div class="empty-icon">📭</div>
          <div class="empty-title">暂无热词数据</div>
          <div class="empty-desc">
            {{ hwFilter.regionCode ? '该区域暂无热词数据，请尝试选择其他区域或点击"重新生成热词"' : '暂无热词数据，请点击"重新生成热词"生成数据' }}
          </div>
        </div>

        <div v-else class="hw-table-wrapper">
          <table class="hw-table">
            <thead>
              <tr>
                <th style="width: 60px;">排名</th>
                <th>热词</th>
                <th style="width: 90px;">分类</th>
                <th style="width: 90px;">情感</th>
                <th style="width: 160px;">热度</th>
                <th style="width: 90px;">提及次数</th>
                <th style="width: 100px;">正面占比</th>
                <th style="width: 80px;">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(word, index) in hwList"
                :key="word.id"
                :class="['hw-row', { 'hw-row-top3': index < 3 }]"
              >
                <td>
                  <span :class="['hw-rank-badge', getHwRankClass(index)]">
                    {{ index + 1 + (hwPagination.pageNum - 1) * hwPagination.pageSize }}
                  </span>
                </td>
                <td>
                  <span class="hw-word-text">{{ word.word }}</span>
                </td>
                <td>
                  <span :class="['hw-tag', 'hw-cat-' + (word.category || '').toLowerCase()]">
                    {{ categoryLabel(word.category) }}
                  </span>
                </td>
                <td>
                  <span :class="['hw-tag', 'hw-sentiment', getSentimentClass(word.sentiment)]">
                    {{ sentimentLabel(word.sentiment) }}
                  </span>
                </td>
                <td>
                  <div class="hw-heat-bar-container">
                    <div class="hw-heat-bar" :style="{ width: getHeatBarWidth(word.heatScore) + '%' }"></div>
                    <span class="hw-heat-value">{{ formatHeat(word.heatScore) }}</span>
                  </div>
                </td>
                <td class="hw-number">{{ word.mentionCount || 0 }}</td>
                <td>
                  <div class="hw-ratio-container">
                    <div class="hw-ratio-bar">
                      <div class="hw-ratio-fill" :style="{ width: getPositiveRatio(word.positiveRatio) + '%' }"></div>
                    </div>
                    <span class="hw-ratio-value">{{ formatRatio(word.positiveRatio) }}</span>
                  </div>
                </td>
                <td>
                  <button class="hw-detail-btn" @click="showMerchantsForWord(word)">
                    关联商家
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div v-if="hwPagination.total > hwPagination.pageSize" class="pagination">
          <button
            class="page-btn"
            :disabled="hwPagination.pageNum <= 1"
            @click="hwPagination.pageNum--; loadHotWords()"
          >
            上一页
          </button>
          <span class="page-info">
            {{ hwPagination.pageNum }} / {{ Math.ceil(hwPagination.total / hwPagination.pageSize) }}
          </span>
          <button
            class="page-btn"
            :disabled="hwPagination.pageNum >= Math.ceil(hwPagination.total / hwPagination.pageSize)"
            @click="hwPagination.pageNum++; loadHotWords()"
          >
            下一页
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 关联商家弹窗（第一层） ==================== -->
    <div v-if="hwMerchantModal.visible" class="modal-overlay" @click.self="hwMerchantModal.visible = false">
      <div class="modal-content modal-wide">
        <div class="modal-header">
          <h3>🏪 「{{ hwMerchantModal.word }}」关联商家</h3>
          <button class="modal-close" @click="hwMerchantModal.visible = false">✕</button>
        </div>
        <div class="modal-body">
          <div v-if="hwMerchantModal.loading" class="loading-state">
            <div class="loading-spinner"></div>
            <span>加载中...</span>
          </div>
          <div v-else-if="hwMerchantModal.merchants.length === 0" class="empty-state">
            暂无关联商家数据
          </div>
          <div v-else class="merchant-list">
            <div v-for="(m, idx) in hwMerchantModal.merchants" :key="m.merchantId" class="merchant-item">
              <span class="merchant-rank">{{ idx + 1 }}</span>
              <div class="merchant-info">
                <span class="merchant-name">{{ m.merchantName }}</span>
                <span v-if="m.category" class="merchant-cat">{{ m.category }}</span>
              </div>
              <span class="merchant-mentions">提及 {{ m.mentionCount }} 次</span>
              <button class="merchant-review-btn" @click="showReviewsForMerchant(m)">查看评价 →</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 评价详情弹窗（第二层） ==================== -->
    <div v-if="hwReviewModal.visible" class="modal-overlay" @click.self="hwReviewModal.visible = false">
      <div class="modal-content modal-wide">
        <div class="modal-header">
          <div>
            <h3>📝 「{{ hwReviewModal.word }}」— {{ hwReviewModal.merchantName }}</h3>
            <span class="modal-back-link" @click="backToMerchants">← 返回商家列表</span>
          </div>
          <button class="modal-close" @click="hwReviewModal.visible = false">✕</button>
        </div>
        <div class="modal-body">
          <div v-if="hwReviewModal.loading" class="loading-state">
            <div class="loading-spinner"></div>
            <span>加载中...</span>
          </div>
          <div v-else-if="hwReviewModal.reviews.length === 0" class="empty-state">
            该商家暂无此热词关联的评价
          </div>
          <div v-else class="review-list">
            <div v-for="(r, idx) in hwReviewModal.reviews" :key="r.reviewId" class="review-item">
              <div class="review-header">
                <span class="review-index">#{{ idx + 1 }}</span>
                <span class="review-rating">{{ '⭐'.repeat(r.rating) }}</span>
                <span class="review-time">{{ r.reviewTime }}</span>
              </div>
              <div class="review-content">{{ r.content }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import AdminLayout from '../../components/AdminLayout.vue'
import {
  getAllRegions,
  getRegionalHotspots,
  getHotWords,
  getHotWordRegions,
  getHotWordMerchants,
  getHotWordMerchantReviews,
  regenerateAllHotWords
} from '../../api/regionalHotspot'

// ==================== 通用 ====================
const activeTab = ref('hotspot')

// ==================== Tab 1: 消费热点洞察 ====================
const regions = ref([])
const selectedRegion = ref('CD')
const timeRange = ref('last7days')
const customStart = ref('')
const customEnd = ref('')
const loading = ref(false)
const trendChartRef = ref(null)
let trendChart = null

const stats = ref({
  regionCode: '',
  regionName: '',
  hotMerchants: [],
  hotCuisines: [],
  hotKeywords: [],
  consumptionPeriods: [],
  trendChanges: [],
  dailyTrend: [],
  totalEvents: 0,
  totalUsers: 0
})

const currentRegionName = computed(() => {
  const region = regions.value.find(r => r.code === selectedRegion.value)
  return region ? region.name : selectedRegion.value
})

const getRankClass = (index) => {
  if (index === 0) return 'rank-1'
  if (index === 1) return 'rank-2'
  if (index === 2) return 'rank-3'
  return 'rank-other'
}

const getBarWidth = (count) => {
  const maxCount = Math.max(...stats.value.consumptionPeriods.map(p => p.count), 1)
  return (count / maxCount) * 100
}

const buildTimeParams = () => {
  const now = new Date()
  let start, end

  switch (timeRange.value) {
    case 'today':
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      end = now
      break
    case 'yesterday':
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
      end = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      break
    case 'last7days':
      start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
      end = now
      break
    case 'last30days':
      start = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)
      end = now
      break
    case 'custom':
      if (customStart.value && customEnd.value) {
        start = new Date(customStart.value)
        end = new Date(customEnd.value)
      } else {
        start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
        end = now
      }
      break
    default:
      start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
      end = now
  }

  return {
    startTime: start.toISOString(),
    endTime: end.toISOString()
  }
}

const loadRegions = async () => {
  try {
    const response = await getAllRegions()
    if (response.success && response.data) {
      regions.value = response.data
    }
  } catch (error) {
    console.error('加载区域列表失败:', error)
  }
}

const loadHotspots = async () => {
  loading.value = true
  try {
    const params = {
      regionCode: selectedRegion.value,
      ...buildTimeParams()
    }
    const response = await getRegionalHotspots(params)
    if (response.success && response.data) {
      stats.value = response.data
    }
  } catch (error) {
    console.error('加载区域热点数据失败:', error)
  } finally {
    loading.value = false
    await nextTick()
    initTrendChart()
  }
}

const initTrendChart = () => {
  if (!trendChartRef.value) return

  if (trendChart) {
    trendChart.dispose()
  }

  trendChart = echarts.init(trendChartRef.value)

  const dailyTrend = stats.value.dailyTrend || []
  const dates = dailyTrend.map(item => {
    const dateStr = item.date
    if (dateStr) {
      const parts = dateStr.split('-')
      if (parts.length >= 3) {
        return `${parts[1]}-${parts[2]}`
      }
    }
    return dateStr || ''
  })
  const counts = dailyTrend.map(item => Number(item.count) || 0)

  let seriesData = []
  if (counts.length > 0) {
    seriesData = counts.map((count, index) => ({
      value: count,
      itemStyle: {
        color: index > 0 && count > counts[index - 1] ? '#52c41a' : index > 0 && count < counts[index - 1] ? '#f5222d' : '#1890ff'
      }
    }))
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e5e6eb',
      borderWidth: 1,
      textStyle: {
        color: '#1f2329'
      },
      formatter: (params) => {
        if (!params || params.length === 0) return ''
        const data = params[0]
        return `<div style="padding: 8px;">
          <div style="font-weight: 600; margin-bottom: 4px;">${data.name}</div>
          <div style="color: #4e5969;">事件数: <span style="color: #1890ff; font-weight: 600;">${data.value}</span></div>
        </div>`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates.length > 0 ? dates : ['暂无数据'],
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#8f959e', fontSize: 12 },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#8f959e', fontSize: 12 },
      splitLine: { lineStyle: { color: '#f0f0f0', type: 'dashed' } },
      min: 0
    },
    series: [{
      name: '事件数',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      data: seriesData.length > 0 ? seriesData : [0],
      lineStyle: { width: 3, color: '#1890ff' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(24, 144, 255, 0.25)' },
          { offset: 1, color: 'rgba(24, 144, 255, 0.02)' }
        ])
      },
      itemStyle: { borderWidth: 2, borderColor: '#fff' },
      emphasis: {
        itemStyle: {
          borderWidth: 3,
          borderColor: '#fff',
          shadowBlur: 10,
          shadowColor: 'rgba(24, 144, 255, 0.5)'
        }
      }
    }]
  }

  trendChart.setOption(option)
}

const handleResize = () => {
  if (trendChart) { trendChart.resize() }
}

watch(() => stats.value.dailyTrend, () => {
  nextTick(() => { initTrendChart() })
}, { deep: true })

// ==================== Tab 2: 区域热词榜单 ====================
const hwLoading = ref(false)
const hwRegenerating = ref(false)
const hwList = ref([])
const hwRegions = ref([])
const hwFilter = ref({
  regionCode: '',
  category: '',
  periodType: 'WEEKLY',
  daysBack: 180
})

const onRegionChange = () => {
  hwPagination.value.pageNum = 1
  loadHotWords()
}
const hwPagination = ref({
  pageNum: 1,
  pageSize: 20,
  total: 0,
  totalPages: 0
})

const hwCategoryCount = computed(() => {
  const cats = new Set(hwList.value.map(w => w.category).filter(Boolean))
  return cats.size
})

const periodLabel = computed(() => {
  const map = { DAILY: '日榜', WEEKLY: '周榜', MONTHLY: '月榜' }
  return map[hwFilter.value.periodType] || '周榜'
})

const categoryLabel = (cat) => {
  const map = {
    TASTE: '口味', SERVICE: '服务', ENVIRONMENT: '环境',
    PRICE: '价格', SPEED: '速度', GENERAL: '综合'
  }
  return map[cat] || cat || '综合'
}

const sentimentLabel = (s) => {
  const map = { POSITIVE: '正面', NEUTRAL: '中性', NEGATIVE: '负面', MIXED: '混合' }
  return map[s] || s || '中性'
}

const getSentimentClass = (s) => {
  return (s || '').toLowerCase()
}

const getHwRankClass = (index) => {
  if (index === 0) return 'rank-1'
  if (index === 1) return 'rank-2'
  if (index === 2) return 'rank-3'
  return ''
}

const getHeatBarWidth = (score) => {
  if (!score) return 0
  const val = typeof score === 'number' ? score : parseFloat(score)
  return Math.min(Math.max(val, 0), 100)
}

const formatHeat = (score) => {
  if (!score && score !== 0) return '0.00'
  const val = typeof score === 'number' ? score : parseFloat(score)
  return val.toFixed(2)
}

const getPositiveRatio = (ratio) => {
  if (ratio === null || ratio === undefined) return 0
  const val = typeof ratio === 'number' ? ratio : parseFloat(ratio)
  return Math.min(Math.max(val * 100, 0), 100)
}

const formatRatio = (ratio) => {
  if (ratio === null || ratio === undefined) return '--'
  const val = typeof ratio === 'number' ? ratio : parseFloat(ratio)
  return (val * 100).toFixed(0) + '%'
}

const loadHotWordRegions = async () => {
  try {
    const response = await getHotWordRegions()
    if (response.success && response.data) {
      hwRegions.value = response.data || []
    }
  } catch (error) {
    console.error('加载热词区域列表失败:', error)
  }
}

const loadHotWords = async () => {
  hwLoading.value = true
  try {
    const params = {
      pageNum: hwPagination.value.pageNum,
      pageSize: hwPagination.value.pageSize,
      periodType: hwFilter.value.periodType
    }
    if (hwFilter.value.regionCode) {
      params.regionCode = hwFilter.value.regionCode
    }
    if (hwFilter.value.category) {
      params.category = hwFilter.value.category
    }

    const response = await getHotWords(params)
    if (response.success && response.data) {
      hwList.value = response.data.records || []
      hwPagination.value.total = response.data.total || 0
    } else {
      hwList.value = []
      hwPagination.value.total = 0
      // 如果是接口层面的错误（非空数据），打印日志方便排查
      if (!response.success) {
        console.error('热词接口返回异常:', response.code, response.message)
      }
    }
  } catch (error) {
    console.error('加载热词列表失败:', error)
    hwList.value = []
    hwPagination.value.total = 0
  } finally {
    hwLoading.value = false
  }
}

const switchToHotWords = async () => {
  activeTab.value = 'hotwords'
  if (hwRegions.value.length === 0) {
    await loadHotWordRegions()
  }
  // 默认选中第一个区域
  if (!hwFilter.value.regionCode && hwRegions.value.length > 0) {
    hwFilter.value.regionCode = hwRegions.value[0].regionCode
  }
  if (hwList.value.length === 0) {
    await loadHotWords()
  }
}

const handleRegenerate = async () => {
  if (hwRegenerating.value) return
  const daysBack = hwFilter.value.daysBack
  if (!confirm(`确定要重新生成全部区域的热词数据吗？\n周期类型：${periodLabel.value}\n回溯天数：${daysBack} 天\n此操作将刷新热词榜单数据。`)) {
    return
  }

  hwRegenerating.value = true
  try {
    const params = {
      periodType: hwFilter.value.periodType,
      daysBack: daysBack
    }
    const response = await regenerateAllHotWords(params)
    if (response.success) {
      alert(`热词生成完成！共生成 ${response.data?.generatedCount || 0} 条热词数据。`)
      hwPagination.value.pageNum = 1
      await loadHotWordRegions()
      await loadHotWords()
    } else {
      alert('热词生成失败：' + (response.message || '未知错误'))
    }
  } catch (error) {
    console.error('热词生成失败:', error)
    alert('热词生成失败，请稍后重试')
  } finally {
    hwRegenerating.value = false
  }
}

// 关联商家弹窗（第一层）
const hwMerchantModal = ref({
  visible: false,
  loading: false,
  word: '',
  wordId: null,
  merchants: []
})

const showMerchantsForWord = async (word) => {
  hwMerchantModal.value = {
    visible: true,
    loading: true,
    word: word.word,
    wordId: word.id,
    merchants: []
  }
  try {
    const response = await getHotWordMerchants(word.id, 30)
    if (response.success && response.data) {
      hwMerchantModal.value.merchants = response.data || []
    }
  } catch (error) {
    console.error('加载关联商家失败:', error)
  } finally {
    hwMerchantModal.value.loading = false
  }
}

// 评价详情弹窗（第二层）
const hwReviewModal = ref({
  visible: false,
  loading: false,
  word: '',
  merchantName: '',
  reviews: []
})

const showReviewsForMerchant = async (merchant) => {
  hwReviewModal.value = {
    visible: true,
    loading: true,
    word: hwMerchantModal.value.word,
    merchantName: merchant.merchantName,
    reviews: []
  }
  try {
    const response = await getHotWordMerchantReviews(
      hwMerchantModal.value.wordId, merchant.merchantId, 30
    )
    if (response.success && response.data) {
      hwReviewModal.value.reviews = response.data || []
    }
  } catch (error) {
    console.error('加载商家评价失败:', error)
  } finally {
    hwReviewModal.value.loading = false
  }
}

const backToMerchants = () => {
  hwReviewModal.value.visible = false
}

// ==================== 生命周期 ====================
onMounted(() => {
  loadRegions()
  loadHotspots()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
})
</script>

<style scoped>
/* ==================== Tab 导航 ==================== */
.tab-nav {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  background: #fff;
  border-radius: 12px;
  padding: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.tab-btn {
  flex: 1;
  padding: 12px 24px;
  background: transparent;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #8f959e;
  cursor: pointer;
  transition: all 0.3s ease;
}

.tab-btn:hover {
  color: #4e5969;
  background: rgba(24, 144, 255, 0.04);
}

.tab-btn.active {
  color: #fff;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

/* ==================== 通用筛选 ==================== */
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

.filter-select {
  padding: 10px 16px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  font-size: 14px;
  color: #1f2329;
  background: #fff;
  cursor: pointer;
  min-width: 160px;
}

.filter-select:focus {
  outline: none;
  border-color: #1890ff;
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

/* ==================== 统计卡片 ==================== */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}

.stat-info { flex: 1; }

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

/* ==================== 趋势图表 ==================== */
.trend-chart-section {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
  margin: 0 0 16px 0;
}

.chart-container {
  width: 100%;
  margin-bottom: 16px;
}

.trend-chart {
  width: 100%;
  height: 320px;
}

.trend-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.trend-summary-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #f7f8fa;
  border-radius: 8px;
}

.trend-summary-name {
  font-size: 14px;
  font-weight: 500;
  color: #4e5969;
}

.trend-summary-badge {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.trend-summary-badge.up { background: rgba(82, 196, 26, 0.1); color: #52c41a; }
.trend-summary-badge.down { background: rgba(245, 34, 45, 0.1); color: #f5222d; }
.trend-summary-badge.new { background: rgba(24, 144, 255, 0.1); color: #1890ff; }
.trend-summary-badge.stable { background: rgba(143, 149, 158, 0.1); color: #8f959e; }

.trend-summary-value {
  font-size: 18px;
  font-weight: 700;
  color: #1f2329;
}

.trend-summary-percent { font-size: 14px; font-weight: 500; }
.trend-summary-percent.positive { color: #52c41a; }
.trend-summary-percent.negative { color: #f5222d; }

/* ==================== 网格卡片 ==================== */
.section-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.section-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.hot-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.hot-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  background: #f7f8fa;
  border-radius: 8px;
}

.hot-rank {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.rank-1 { background: linear-gradient(135deg, #ffd700 0%, #ffb700 100%); }
.rank-2 { background: linear-gradient(135deg, #c0c0c0 0%, #a8a8a8 100%); }
.rank-3 { background: linear-gradient(135deg, #cd7f32 0%, #b87333 100%); }
.rank-other { background: #d9d9d9; }

.hot-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.hot-name {
  font-size: 14px;
  font-weight: 500;
  color: #1f2329;
}

.hot-cuisine {
  font-size: 12px;
  color: #8f959e;
  margin-top: 2px;
}

.hot-count {
  font-size: 14px;
  font-weight: 600;
  color: #4e5969;
}

.period-chart {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.period-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.period-label {
  width: 80px;
  font-size: 13px;
  color: #4e5969;
  flex-shrink: 0;
}

.period-bar-container {
  flex: 1;
  height: 24px;
  background: #f0f0f0;
  border-radius: 12px;
  overflow: hidden;
}

.period-bar {
  height: 100%;
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%);
  border-radius: 12px;
  transition: width 0.3s ease;
}

.period-count {
  width: 48px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
  text-align: right;
  flex-shrink: 0;
}

/* ==================== 热词榜单 ==================== */
.hw-stats {
  margin-bottom: 24px;
}

.regenerate-btn {
  padding: 10px 20px;
  background: linear-gradient(135deg, #722ed1 0%, #9254de 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  white-space: nowrap;
}

.regenerate-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(114, 46, 209, 0.3);
}

.regenerate-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.hw-table-section {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.hw-table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f0f0f0;
}

.hw-period-badge {
  padding: 4px 14px;
  background: linear-gradient(135deg, #fff7e6 0%, #fffbe6 100%);
  border: 1px solid #ffd591;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  color: #d48806;
}

.hw-table-wrapper {
  overflow-x: auto;
}

.hw-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.hw-table thead th {
  padding: 12px 10px;
  text-align: left;
  font-weight: 600;
  color: #8f959e;
  font-size: 13px;
  border-bottom: 2px solid #f0f0f0;
  white-space: nowrap;
}

.hw-table tbody td {
  padding: 14px 10px;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: middle;
}

.hw-row:hover {
  background: #fafbff;
}

.hw-row-top3 {
  background: #fffbe6;
}

.hw-row-top3:hover {
  background: #fff7cc;
}

.hw-rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #8f959e;
  background: #f0f0f0;
}

.hw-rank-badge.rank-1 { background: linear-gradient(135deg, #ffd700, #f0c000); color: #fff; }
.hw-rank-badge.rank-2 { background: linear-gradient(135deg, #b8b8b8, #a0a0a0); color: #fff; }
.hw-rank-badge.rank-3 { background: linear-gradient(135deg, #cd7f32, #b8702a); color: #fff; }

.hw-word-text {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.hw-tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.hw-cat-taste { background: rgba(245, 34, 45, 0.1); color: #f5222d; }
.hw-cat-service { background: rgba(24, 144, 255, 0.1); color: #1890ff; }
.hw-cat-environment { background: rgba(82, 196, 26, 0.1); color: #52c41a; }
.hw-cat-price { background: rgba(250, 140, 22, 0.1); color: #fa8c16; }
.hw-cat-speed { background: rgba(114, 46, 209, 0.1); color: #722ed1; }
.hw-cat-general { background: rgba(143, 149, 158, 0.1); color: #8f959e; }

.hw-sentiment {
  font-size: 12px;
}
.hw-sentiment.positive { background: rgba(82, 196, 26, 0.1); color: #52c41a; }
.hw-sentiment.neutral { background: rgba(143, 149, 158, 0.1); color: #8f959e; }
.hw-sentiment.negative { background: rgba(245, 34, 45, 0.1); color: #f5222d; }
.hw-sentiment.mixed { background: rgba(250, 140, 22, 0.1); color: #fa8c16; }

.hw-heat-bar-container {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 120px;
}

.hw-heat-bar {
  height: 8px;
  background: linear-gradient(90deg, #ff4d4f 0%, #ff7a45 30%, #ffa940 60%, #52c41a 100%);
  border-radius: 4px;
  min-width: 4px;
  transition: width 0.6s ease;
}

.hw-heat-value {
  font-size: 13px;
  font-weight: 600;
  color: #1f2329;
  white-space: nowrap;
}

.hw-number {
  font-size: 14px;
  font-weight: 600;
  color: #4e5969;
  text-align: center;
}

.hw-ratio-container {
  display: flex;
  align-items: center;
  gap: 6px;
}

.hw-ratio-bar {
  width: 50px;
  height: 6px;
  background: #f0f0f0;
  border-radius: 3px;
  overflow: hidden;
}

.hw-ratio-fill {
  height: 100%;
  background: linear-gradient(90deg, #52c41a 0%, #73d13d 100%);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.hw-ratio-value {
  font-size: 12px;
  font-weight: 600;
  color: #52c41a;
  white-space: nowrap;
}

.hw-detail-btn {
  padding: 4px 12px;
  background: #f0f5ff;
  border: 1px solid #adc6ff;
  border-radius: 6px;
  color: #1890ff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.hw-detail-btn:hover {
  background: #e6f7ff;
  border-color: #69b1ff;
}

/* ==================== 加载和空状态 ==================== */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #8f959e;
  gap: 12px;
  font-size: 14px;
}

.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid #f0f0f0;
  border-top-color: #1890ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  text-align: center;
  padding: 30px;
  color: #8f959e;
  font-size: 14px;
}

.empty-state-large {
  text-align: center;
  padding: 60px 20px;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #4e5969;
  margin-bottom: 8px;
}

.empty-desc {
  font-size: 14px;
  color: #8f959e;
  max-width: 400px;
  margin: 0 auto;
  line-height: 1.6;
}

/* ==================== 分页 ==================== */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
}

.page-btn {
  padding: 8px 16px;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  color: #4e5969;
  cursor: pointer;
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  color: #1890ff;
  border-color: #1890ff;
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: #4e5969;
  font-weight: 500;
}

/* ==================== 弹窗 ==================== */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-content {
  background: #fff;
  border-radius: 16px;
  width: 90%;
  max-width: 600px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.15);
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: #1f2329;
}

.modal-close {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: none;
  background: #f5f7fa;
  color: #8f959e;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.modal-close:hover {
  background: #ff4d4f;
  color: #fff;
}

.modal-body {
  padding: 20px 24px;
  overflow-y: auto;
  flex: 1;
}

.modal-wide {
  max-width: 750px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-item {
  padding: 16px;
  background: #f7f8fa;
  border-radius: 10px;
  border-left: 3px solid #e5e6eb;
  transition: all 0.2s;
}

.review-item:hover {
  background: #eef2f7;
  border-left-color: #1890ff;
}

.review-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.review-index {
  font-size: 12px;
  font-weight: 700;
  color: #8f959e;
  background: #e5e6eb;
  padding: 2px 8px;
  border-radius: 4px;
}

.review-merchant {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}

.review-rating {
  font-size: 13px;
}

.review-source {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;
}

.src-tag {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.src-kw {
  background: rgba(114, 46, 209, 0.1);
  color: #722ed1;
}

.review-time {
  font-size: 12px;
  color: #8f959e;
  margin-left: auto;
}

.review-content {
  font-size: 14px;
  color: #4e5969;
  line-height: 1.7;
  padding: 10px 12px;
  background: #fff;
  border-radius: 8px;
}

.merchant-review-btn {
  padding: 6px 14px;
  background: #fff;
  border: 1px solid #1890ff;
  border-radius: 6px;
  color: #1890ff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.merchant-review-btn:hover {
  background: #1890ff;
  color: #fff;
}

.modal-back-link {
  font-size: 13px;
  color: #1890ff;
  cursor: pointer;
  margin-top: 2px;
  display: inline-block;
}

.modal-back-link:hover {
  text-decoration: underline;
}

.modal-footer-note {
  text-align: center;
  font-size: 12px;
  color: #8f959e;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid #f0f0f0;
}

.merchant-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.merchant-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: #f7f8fa;
  border-radius: 10px;
  transition: all 0.2s;
}

.merchant-item:hover {
  background: #eef2f7;
}

.merchant-rank {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #e5e6eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #4e5969;
  flex-shrink: 0;
}

.merchant-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.merchant-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
}

.merchant-cat {
  font-size: 12px;
  color: #8f959e;
}

.merchant-mentions {
  font-size: 13px;
  font-weight: 600;
  color: #722ed1;
  white-space: nowrap;
}

/* ==================== 响应式 ==================== */
@media (max-width: 1024px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .section-grid {
    grid-template-columns: 1fr;
  }

  .trend-summary {
    flex-direction: column;
  }

  .hw-table-wrapper {
    overflow-x: auto;
  }
}

@media (max-width: 640px) {
  .stats-cards {
    grid-template-columns: 1fr;
  }

  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }

  .trend-chart {
    height: 250px;
  }

  .tab-btn {
    padding: 10px 16px;
    font-size: 13px;
  }
}
</style>
