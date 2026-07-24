<template>
  <AdminLayout title="运营数据" subtitle="查看平台核心运营数据指标">
    <div class="dashboard-container">
      <div class="time-filter">
        <span class="filter-label">时间范围</span>
        <div class="filter-buttons">
          <button 
            v-for="range in timeRanges" 
            :key="range.value"
            :class="['filter-btn', { active: timeRange === range.value }]"
            @click="setTimeRange(range.value)"
          >
            {{ range.label }}
          </button>
        </div>
        <div class="date-picker-container">
          <template v-if="timeRange === 'day'">
            <input 
              type="date" 
              v-model="selectedDate" 
              class="date-picker"
              @change="onDateChange"
            />
          </template>
          <template v-else-if="timeRange === 'week'">
            <select 
              v-model="selectedYear" 
              class="date-picker"
              @change="onWeekChange"
            >
              <option v-for="year in availableYears" :key="year" :value="year">{{ year }}年</option>
            </select>
            <select 
              v-model="selectedWeek" 
              class="date-picker"
              @change="onWeekChange"
            >
              <option v-for="week in availableWeeks" :key="week.value" :value="week.value">第{{ week.value }}周 ({{ week.label }})</option>
            </select>
          </template>
          <template v-else-if="timeRange === 'month'">
            <input 
              type="month" 
              v-model="selectedMonth" 
              class="date-picker"
              @change="onMonthChange"
            />
          </template>
        </div>
      </div>

      <div class="metrics-grid">
        <div class="metric-card" v-for="metric in metricList" :key="metric.key">
          <div :class="['metric-icon', metric.iconClass]">
            <svg :viewBox="metric.iconViewBox" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
              <path :d="metric.iconPath" />
            </svg>
          </div>
          <div class="metric-info">
            <span class="metric-value">{{ formatValue(metric.value) }}</span>
            <span class="metric-label">{{ metric.label }}</span>
            <span class="metric-unit">{{ metric.unit }}</span>
          </div>
        </div>
      </div>

      <div class="charts-section">
        <div class="chart-card">
          <div class="chart-header">
            <h3>活跃用户趋势</h3>
          </div>
          <div class="chart-body">
            <div class="chart-scroll-container">
              <button class="scroll-btn scroll-left" @click="scrollChart('activeUsers', -300)">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M15 18l-6-6 6-6" />
                </svg>
              </button>
              <div class="chart-container">
                <div class="y-axis">
                  <span v-for="(tick, index) in getYAxisTicks(trends.activeUsers)" :key="index" class="y-tick">{{ tick }}</span>
                </div>
                <div class="chart-bars-scrollable" ref="activeUsersScrollRef">
                  <div 
                    v-for="(value, index) in trends.activeUsers" 
                    :key="index"
                    class="bar-wrapper"
                  >
                    <div 
                      class="bar" 
                      :style="{ height: getBarHeight(value, trends.activeUsers) + '%' }"
                    >
                      <span class="bar-value">{{ value }}</span>
                    </div>
                    <span class="bar-label">{{ getLabel(index) }}</span>
                  </div>
                </div>
              </div>
              <button class="scroll-btn scroll-right" @click="scrollChart('activeUsers', 300)">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 18l6-6-6-6" />
                </svg>
              </button>
            </div>
            <div v-if="isAllZero(trends.activeUsers)" class="empty-chart-tip">暂无数据</div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>评价数量趋势</h3>
          </div>
          <div class="chart-body">
            <div class="chart-scroll-container">
              <button class="scroll-btn scroll-left" @click="scrollChart('reviews', -300)">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M15 18l-6-6 6-6" />
                </svg>
              </button>
              <div class="chart-container">
                <div class="y-axis">
                  <span v-for="(tick, index) in getYAxisTicks(trends.reviews)" :key="index" class="y-tick">{{ tick }}</span>
                </div>
                <div class="chart-bars-scrollable" ref="reviewsScrollRef">
                  <div 
                    v-for="(value, index) in trends.reviews" 
                    :key="index"
                    class="bar-wrapper"
                  >
                    <div 
                      class="bar bar-orange" 
                      :style="{ height: getBarHeight(value, trends.reviews) + '%' }"
                    >
                      <span class="bar-value">{{ value }}</span>
                    </div>
                    <span class="bar-label">{{ getLabel(index) }}</span>
                  </div>
                </div>
              </div>
              <button class="scroll-btn scroll-right" @click="scrollChart('reviews', 300)">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 18l6-6-6-6" />
                </svg>
              </button>
            </div>
            <div v-if="isAllZero(trends.reviews)" class="empty-chart-tip">暂无数据</div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>AI调用次数趋势</h3>
          </div>
          <div class="chart-body">
            <div class="chart-scroll-container">
              <button class="scroll-btn scroll-left" @click="scrollChart('aiCalls', -300)">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M15 18l-6-6 6-6" />
                </svg>
              </button>
              <div class="chart-container">
                <div class="y-axis">
                  <span v-for="(tick, index) in getYAxisTicks(trends.aiCalls)" :key="index" class="y-tick">{{ tick }}</span>
                </div>
                <div class="chart-bars-scrollable" ref="aiCallsScrollRef">
                  <div 
                    v-for="(value, index) in trends.aiCalls" 
                    :key="index"
                    class="bar-wrapper"
                  >
                    <div 
                      class="bar bar-purple" 
                      :style="{ height: getBarHeight(value, trends.aiCalls) + '%' }"
                    >
                      <span class="bar-value">{{ value }}</span>
                    </div>
                    <span class="bar-label">{{ getLabel(index) }}</span>
                  </div>
                </div>
              </div>
              <button class="scroll-btn scroll-right" @click="scrollChart('aiCalls', 300)">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 18l6-6-6-6" />
                </svg>
              </button>
            </div>
            <div v-if="isAllZero(trends.aiCalls)" class="empty-chart-tip">暂无数据</div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>推荐次数趋势</h3>
          </div>
          <div class="chart-body">
            <div class="chart-scroll-container">
              <button class="scroll-btn scroll-left" @click="scrollChart('recommendations', -300)">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M15 18l-6-6 6-6" />
                </svg>
              </button>
              <div class="chart-container">
                <div class="y-axis">
                  <span v-for="(tick, index) in getYAxisTicks(trends.recommendations)" :key="index" class="y-tick">{{ tick }}</span>
                </div>
                <div class="chart-bars-scrollable" ref="recommendationsScrollRef">
                  <div 
                    v-for="(value, index) in trends.recommendations" 
                    :key="index"
                    class="bar-wrapper"
                  >
                    <div 
                      class="bar bar-green" 
                      :style="{ height: getBarHeight(value, trends.recommendations) + '%' }"
                    >
                      <span class="bar-value">{{ value }}</span>
                    </div>
                    <span class="bar-label">{{ getLabel(index) }}</span>
                  </div>
                </div>
              </div>
              <button class="scroll-btn scroll-right" @click="scrollChart('recommendations', 300)">
                <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 18l6-6-6-6" />
                </svg>
              </button>
            </div>
            <div v-if="isAllZero(trends.recommendations)" class="empty-chart-tip">暂无数据</div>
          </div>
        </div>
      </div>

      <div class="bottom-section">
        <div class="pie-chart-card">
          <div class="card-header">
            <h3>商家端功能使用分布</h3>
          </div>
          <div class="pie-chart-container">
            <div class="pie-wrapper">
              <svg viewBox="0 0 200 200" class="pie-chart">
                <circle cx="100" cy="100" r="70" fill="none" stroke="#f5f5f5" stroke-width="40" />
                <circle
                  v-for="(segment, index) in pieSegments"
                  :key="index"
                  cx="100"
                  cy="100"
                  r="70"
                  fill="none"
                  :stroke="segment.color"
                  :stroke-width="hoveredSegment === index ? 46 : 40"
                  :stroke-dasharray="segment.dashArray"
                  :stroke-dashoffset="segment.dashOffset"
                  :transform="'rotate(-90 100 100)'"
                  class="pie-segment"
                  @mouseenter="handlePieHover(index, $event)"
                  @mouseleave="handlePieLeave"
                />
              </svg>
              <div 
                v-if="hoveredSegment !== null" 
                class="pie-tooltip"
                :style="{ left: tooltipX + 'px', top: tooltipY + 'px' }"
              >
                <div class="tooltip-label">{{ pieLegendItems[hoveredSegment].label }}</div>
                <div class="tooltip-value">数量：{{ pieLegendItems[hoveredSegment].value }}</div>
                <div class="tooltip-percent">占比：{{ pieLegendItems[hoveredSegment].percent }}%</div>
              </div>
            </div>
            <div class="pie-legend">
              <div v-for="(item, index) in pieLegendItems" :key="index" class="legend-item">
                <span class="legend-color" :style="{ background: item.color }"></span>
                <span class="legend-label">{{ item.label }}</span>
                <span class="legend-value">{{ item.value }}</span>
                <span class="legend-percent">{{ item.percent }}%</span>
              </div>
            </div>
          </div>
          <div v-if="isMerchantActionsEmpty" class="empty-chart-tip">暂无数据</div>
        </div>

        <div class="merchant-actions-card">
          <div class="card-header">
            <h3>商家端功能使用情况</h3>
          </div>
          <div class="merchant-actions-grid">
            <div class="merchant-action-item" v-for="(value, key) in merchantActions" :key="key">
              <span class="action-icon">{{ getActionIcon(key) }}</span>
              <div class="action-info">
                <span class="action-value">{{ formatValue(value) }}</span>
                <span class="action-label">{{ getActionLabel(key) }}</span>
              </div>
              <div class="action-progress">
                <div 
                  class="progress-bar" 
                  :style="{ width: getActionPercent(key) + '%' }"
                  :class="getActionColor(key)"
                ></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getDashboardOverview, getDashboardTrends } from '../../api/operationsDashboard'

const timeRange = ref('week')
const timeRanges = [
  { label: '按日', value: 'day' },
  { label: '按周', value: 'week' },
  { label: '按月', value: 'month' },
]

const selectedDate = ref('')
const selectedMonth = ref('')
const selectedYear = ref(new Date().getFullYear())
const selectedWeek = ref(1)

const overview = ref(null)
const trends = ref({
  activeUsers: [],
  reviews: [],
  aiCalls: [],
  recommendations: [],
})

const labels = ref([])

const activeUsersScrollRef = ref(null)
const reviewsScrollRef = ref(null)
const aiCallsScrollRef = ref(null)
const recommendationsScrollRef = ref(null)

const scrollRefs = {
  activeUsers: activeUsersScrollRef,
  reviews: reviewsScrollRef,
  aiCalls: aiCallsScrollRef,
  recommendations: recommendationsScrollRef,
}

const scrollChart = (chartKey, delta) => {
  const ref = scrollRefs[chartKey]
  if (ref.value) {
    ref.value.scrollBy({ left: delta, behavior: 'smooth' })
  }
}

const metricList = computed(() => {
  if (!overview.value?.metrics) {
    return [
      { key: 'totalUsers', label: '用户总数', value: 0, unit: '人', iconPath: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 7a4 4 0 1 1 8 0', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-blue' },
      { key: 'totalReviews', label: '评论总数', value: 0, unit: '条', iconPath: 'M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-orange' },
      { key: 'merchantCount', label: '商家数量', value: 0, unit: '家', iconPath: 'M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-green' },
      { key: 'aiCallCount', label: '大模型调用次数', value: 0, unit: '次', iconPath: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-purple' },
      { key: 'storeConsultations', label: '探店咨询次数', value: 0, unit: '次', iconPath: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-cyan' },
      { key: 'semanticSearches', label: '语义搜索次数', value: 0, unit: '次', iconPath: 'M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-teal' },
      { key: 'recommendationClicks', label: '推荐点击次数', value: 0, unit: '次', iconPath: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-indigo' },
    ]
  }

  const m = overview.value.metrics
  return [
    { key: 'totalUsers', label: '用户总数', value: m.totalUsers || 0, unit: '人', iconPath: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2 M9 7a4 4 0 1 1 8 0', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-blue' },
    { key: 'totalReviews', label: '评论总数', value: m.totalReviews || 0, unit: '条', iconPath: 'M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-orange' },
    { key: 'merchantCount', label: '商家数量', value: m.merchantCount || 0, unit: '家', iconPath: 'M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-green' },
    { key: 'aiCallCount', label: '大模型调用次数', value: m.aiCallCount || 0, unit: '次', iconPath: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-purple' },
    { key: 'storeConsultations', label: '探店咨询次数', value: m.storeConsultations || 0, unit: '次', iconPath: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-cyan' },
    { key: 'semanticSearches', label: '语义搜索次数', value: m.semanticSearches || 0, unit: '次', iconPath: 'M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-teal' },
    { key: 'recommendationClicks', label: '推荐点击次数', value: m.recommendationClicks || 0, unit: '次', iconPath: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-indigo' },
  ]
})

const merchantActions = computed(() => {
  if (!overview.value?.metrics?.merchantActions) {
    return { loginCount: 0, replyCount: 0, updateCount: 0, viewStatsCount: 0 }
  }
  return overview.value.metrics.merchantActions
})

const totalMerchantActions = computed(() => {
  const actions = merchantActions.value
  return (actions.loginCount || 0) + (actions.replyCount || 0) + (actions.updateCount || 0) + (actions.viewStatsCount || 0)
})

const isMerchantActionsEmpty = computed(() => totalMerchantActions.value === 0)

const pieColors = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6']

const pieLegendItems = computed(() => {
  const actions = merchantActions.value
  const total = totalMerchantActions.value
  const items = [
    { key: 'loginCount', label: '登录次数', value: actions.loginCount || 0, color: pieColors[0] },
    { key: 'replyCount', label: '回复评价', value: actions.replyCount || 0, color: pieColors[1] },
    { key: 'updateCount', label: '更新资料', value: actions.updateCount || 0, color: pieColors[2] },
    { key: 'viewStatsCount', label: '查看统计', value: actions.viewStatsCount || 0, color: pieColors[3] },
  ]
  return items.map(item => ({
    ...item,
    percent: total > 0 ? Math.round((item.value / total) * 100) : 0
  }))
})

const pieSegments = computed(() => {
  const items = pieLegendItems.value
  const circumference = 2 * Math.PI * 70
  let offset = 0
  return items.map(item => {
    const percent = totalMerchantActions.value > 0 ? (item.value / totalMerchantActions.value) : 0
    const segment = {
      color: item.color,
      dashArray: `${percent * circumference} ${circumference}`,
      dashOffset: -offset
    }
    offset += percent * circumference
    return segment
  })
})

const hoveredSegment = ref(null)
const tooltipX = ref(0)
const tooltipY = ref(0)

const handlePieHover = (index, event) => {
  hoveredSegment.value = index
  const wrapperRect = event.currentTarget.closest('.pie-wrapper').getBoundingClientRect()
  tooltipX.value = event.clientX - wrapperRect.left + 15
  tooltipY.value = event.clientY - wrapperRect.top - 60
}

const handlePieLeave = () => {
  hoveredSegment.value = null
}

const formatValue = (value) => {
  if (!value) return '0'
  if (value >= 10000) {
    return (value / 10000).toFixed(1) + '万'
  }
  return value.toString()
}

const getBarHeight = (value, array) => {
  const max = Math.max(...array, 1)
  return (value / max) * 100
}

const getYAxisTicks = (array) => {
  const max = Math.max(...array, 1)
  const ticks = []
  for (let i = 4; i >= 0; i--) {
    ticks.push(Math.round(max * i / 4))
  }
  return ticks
}

const getLabel = (index) => {
  if (labels.value.length > index) {
    return labels.value[index]
  }
  return ''
}

const isAllZero = (array) => {
  return array.every(v => v === 0 || v === undefined || v === null)
}

const getActionIcon = (key) => {
  const icons = {
    loginCount: '🔑',
    replyCount: '💬',
    updateCount: '✏️',
    viewStatsCount: '📊',
  }
  return icons[key] || '📄'
}

const getActionLabel = (key) => {
  const labels = {
    loginCount: '登录次数',
    replyCount: '回复评价',
    updateCount: '更新资料',
    viewStatsCount: '查看统计',
  }
  return labels[key] || key
}

const getActionPercent = (key) => {
  if (totalMerchantActions.value === 0) return 0
  const value = merchantActions.value[key] || 0
  return Math.round((value / totalMerchantActions.value) * 100)
}

const getActionColor = (key) => {
  const colors = {
    loginCount: 'progress-blue',
    replyCount: 'progress-green',
    updateCount: 'progress-orange',
    viewStatsCount: 'progress-purple',
  }
  return colors[key] || 'progress-blue'
}

const formatDate = (date) => {
  const d = new Date(date)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const getTodayDate = () => {
  return formatDate(new Date())
}

const getMondayOfCurrentWeek = () => {
  const today = new Date()
  const day = today.getDay()
  const diff = today.getDate() - day + (day === 0 ? -6 : 1)
  return formatDate(new Date(today.setDate(diff)))
}

const getCurrentMonth = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const getCurrentWeek = () => {
  const now = new Date()
  const start = new Date(now.getFullYear(), 0, 1)
  const diff = now - start
  const oneWeek = 1000 * 60 * 60 * 24 * 7
  return Math.ceil(diff / oneWeek)
}

const availableYears = computed(() => {
  const currentYear = new Date().getFullYear()
  return [currentYear - 1, currentYear, currentYear + 1]
})

const availableWeeks = computed(() => {
  const options = []
  const year = selectedYear.value
  for (let i = 1; i <= 52; i++) {
    const firstDay = getFirstDayOfWeek(year, i)
    const lastDay = new Date(firstDay)
    lastDay.setDate(lastDay.getDate() + 6)
    const firstMonth = firstDay.getMonth() + 1
    const firstDayNum = firstDay.getDate()
    const lastMonth = lastDay.getMonth() + 1
    const lastDayNum = lastDay.getDate()
    options.push({
      value: i,
      label: `${firstMonth}月${firstDayNum}日-${lastMonth}月${lastDayNum}日`
    })
  }
  return options
})

function getFirstDayOfWeek(year, weekNum) {
  const date = new Date(year, 0, 1)
  const dayOfWeek = date.getDay() || 7
  const diff = (weekNum - 1) * 7 - (dayOfWeek - 1)
  date.setDate(date.getDate() + diff)
  return date
}

const getMondayOfWeek = (year, week) => {
  const date = getFirstDayOfWeek(year, week)
  return formatDate(date)
}

const setTimeRange = async (range) => {
  timeRange.value = range
  if (range === 'day') {
    selectedDate.value = getTodayDate()
  } else if (range === 'week') {
    selectedYear.value = new Date().getFullYear()
    selectedWeek.value = getCurrentWeek()
  } else if (range === 'month') {
    selectedMonth.value = getCurrentMonth()
  }
  await loadData()
}

watch(selectedYear, () => {
  if (selectedWeek.value > 52) {
    selectedWeek.value = 52
  }
})

const onDateChange = async () => {
  if (selectedDate.value) {
    await loadData()
  }
}

const onWeekChange = async () => {
  if (selectedYear.value && selectedWeek.value) {
    await loadData()
  }
}

const onMonthChange = async () => {
  if (selectedMonth.value) {
    await loadData()
  }
}

const loadData = async () => {
  try {
    const params = { timeRange: timeRange.value }
    
    if (timeRange.value === 'day' && selectedDate.value) {
      params.date = selectedDate.value
    } else if (timeRange.value === 'week') {
      const monday = getMondayOfWeek(selectedYear.value, selectedWeek.value)
      params.startDate = monday
      const start = new Date(monday)
      const end = new Date(start)
      end.setDate(start.getDate() + 6)
      params.endDate = formatDate(end)
    } else if (timeRange.value === 'month' && selectedMonth.value) {
      params.month = selectedMonth.value
    }

    const [overviewRes, trendsRes] = await Promise.all([
      getDashboardOverview(params),
      getDashboardTrends(params),
    ])

    if (overviewRes.success) {
      overview.value = overviewRes.data
    }

    if (trendsRes.success) {
      trends.value = trendsRes.data.trends || trends.value
      labels.value = trendsRes.data.labels || []
    }
  } catch (error) {
    console.error('加载运营数据失败:', error)
  }
}

onMounted(() => {
  selectedWeek.value = getCurrentWeek()
  setTimeRange('week')
})
</script>

<style scoped>
.dashboard-container {
  padding: 24px;
}

.time-filter {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.filter-label {
  font-size: 14px;
  font-weight: 600;
  color: #5a6a7a;
}

.filter-buttons {
  display: flex;
  gap: 8px;
}

.filter-btn {
  padding: 10px 20px;
  border: 2px solid #e8e8e8;
  border-radius: 20px;
  background: #fff;
  color: #5a6a7a;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.filter-btn:hover {
  border-color: #1890ff;
  color: #1890ff;
}

.filter-btn.active {
  border-color: #1890ff;
  background: #1890ff;
  color: #fff;
}

.date-picker-container {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.date-picker {
  padding: 10px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  color: #1f2d3d;
  background: #fff;
  cursor: pointer;
  transition: all 0.3s;
}

.date-picker:hover {
  border-color: #1890ff;
}

.date-picker:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.1);
}

.date-hint {
  font-size: 12px;
  color: #909399;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.metric-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 14px;
}

.metric-icon-blue {
  color: #1890ff;
  background: rgba(24, 144, 255, 0.1);
}

.metric-icon-green {
  color: #52c41a;
  background: rgba(82, 196, 26, 0.1);
}

.metric-icon-orange {
  color: #ff6700;
  background: rgba(255, 103, 0, 0.1);
}

.metric-icon-purple {
  color: #722ed1;
  background: rgba(114, 46, 209, 0.1);
}

.metric-icon-cyan {
  color: #13c2c2;
  background: rgba(19, 194, 194, 0.1);
}

.metric-icon-teal {
  color: #20c997;
  background: rgba(32, 201, 151, 0.1);
}

.metric-icon-indigo {
  color: #536dfe;
  background: rgba(83, 109, 254, 0.1);
}

.metric-info {
  display: flex;
  flex-direction: column;
}

.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2d3d;
}

.metric-label {
  font-size: 13px;
  color: #667085;
}

.metric-unit {
  font-size: 12px;
  color: #909399;
}

.charts-section {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.chart-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  position: relative;
}

.chart-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.chart-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
}

.chart-body {
  padding: 24px;
  position: relative;
}

.chart-scroll-container {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scroll-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: #f5f5f5;
  border-radius: 50%;
  color: #667085;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all 0.3s;
}

.scroll-btn:hover {
  background: #e8e8e8;
  color: #1890ff;
}

.scroll-btn:active {
  transform: scale(0.95);
}

.scroll-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.chart-container {
  height: 220px;
  display: flex;
  position: relative;
  flex: 1;
  overflow: hidden;
}

.y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 40px;
  padding-right: 12px;
  border-right: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.y-tick {
  font-size: 11px;
  color: #909399;
  text-align: right;
}

.chart-bars-scrollable {
  flex: 1;
  display: flex;
  align-items: flex-end;
  height: 100%;
  gap: 8px;
  padding-left: 12px;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: thin;
  scrollbar-color: #d9d9d9 transparent;
}

.chart-bars-scrollable::-webkit-scrollbar {
  height: 6px;
}

.chart-bars-scrollable::-webkit-scrollbar-track {
  background: transparent;
}

.chart-bars-scrollable::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

.chart-bars-scrollable::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}

.bar-wrapper {
  flex-shrink: 0;
  width: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  justify-content: flex-end;
  position: relative;
}

.bar {
  width: 100%;
  max-width: 40px;
  background: linear-gradient(180deg, #1890ff 0%, #40a9ff 100%);
  border-radius: 6px 6px 0 0;
  min-height: 4px;
  transition: height 0.5s ease;
  position: relative;
}

.bar-orange {
  background: linear-gradient(180deg, #ff6700 0%, #ff8c42 100%);
}

.bar-purple {
  background: linear-gradient(180deg, #722ed1 0%, #9254de 100%);
}

.bar-green {
  background: linear-gradient(180deg, #52c41a 0%, #73d13d 100%);
}

.bar-value {
  position: absolute;
  top: -24px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  font-weight: 600;
  color: #667085;
  white-space: nowrap;
}

.bar-label {
  font-size: 11px;
  color: #909399;
  margin-top: 8px;
  white-space: nowrap;
}

.empty-chart-tip {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #909399;
  font-size: 14px;
  background: #fff;
  padding: 8px 16px;
  border-radius: 8px;
}

.bottom-section {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.pie-chart-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  position: relative;
}

.card-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.card-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
}

.pie-chart-container {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 24px;
}

.pie-wrapper {
  position: relative;
  width: 200px;
  height: 200px;
}

.pie-chart {
  width: 100%;
  height: 100%;
}

.pie-segment {
  cursor: pointer;
  transition: opacity 0.3s ease;
}

.pie-segment:hover {
  opacity: 0.7;
}

.pie-legend {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.legend-color {
  width: 16px;
  height: 16px;
  border-radius: 4px;
}

.legend-label {
  flex: 1;
  font-size: 14px;
  color: #667085;
}

.legend-value {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
}

.legend-percent {
  font-size: 14px;
  color: #909399;
  min-width: 40px;
  text-align: right;
}

.pie-tooltip {
  position: absolute;
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 100;
  pointer-events: none;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.pie-tooltip .tooltip-label {
  font-weight: 600;
  margin-bottom: 4px;
}

.pie-tooltip .tooltip-value,
.pie-tooltip .tooltip-percent {
  opacity: 0.9;
  margin-top: 4px;
}

.merchant-actions-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.merchant-actions-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
}

.merchant-action-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fafafa;
  border-radius: 12px;
}

.action-icon {
  font-size: 24px;
}

.action-info {
  display: flex;
  flex-direction: column;
  width: 120px;
}

.action-value {
  font-size: 20px;
  font-weight: 700;
  color: #1f2d3d;
}

.action-label {
  font-size: 13px;
  color: #667085;
}

.action-progress {
  flex: 1;
  height: 8px;
  background: #e8e8e8;
  border-radius: 4px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}

.progress-blue {
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%);
}

.progress-green {
  background: linear-gradient(90deg, #52c41a 0%, #73d13d 100%);
}

.progress-orange {
  background: linear-gradient(90deg, #fa8c16 0%, #ffa940 100%);
}

.progress-purple {
  background: linear-gradient(90deg, #722ed1 0%, #9254de 100%);
}

@media (max-width: 1200px) {
  .metrics-grid {
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
  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .pie-chart-container {
    flex-direction: column;
  }

  .pie-chart {
    width: 150px;
    height: 150px;
  }
}
</style>