<template>
  <AdminLayout title="商家统计" subtitle="查看商家使用平台功能的情况">
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
        
        <div class="date-selector">
          <div v-if="timeRange === 'day'" class="day-selector">
            <span class="selector-label">选择日期：</span>
            <input 
              type="date" 
              v-model="selectedDate" 
              :max="todayStr"
              class="date-input"
              @change="handleDateChange"
            />
          </div>
          
          <div v-else-if="timeRange === 'week'" class="week-selector">
            <span class="selector-label">选择周：</span>
            <select v-model="selectedYear" class="week-select" @change="handleWeekChange">
              <option v-for="year in availableYears" :key="year" :value="year">{{ year }}年</option>
            </select>
            <select v-model="selectedWeek" class="week-select" @change="handleWeekChange">
              <option v-for="week in availableWeeks" :key="week.value" :value="week.value">
                第{{ week.value }}周 ({{ week.label }})
              </option>
            </select>
          </div>
          
          <div v-else class="month-selector">
            <span class="selector-label">选择月份：</span>
            <select v-model="selectedMonth" class="month-select" @change="handleMonthChange">
              <option v-for="month in monthOptions" :key="month.value" :value="month.value">
                {{ month.label }}
              </option>
            </select>
          </div>
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
            <span class="metric-value">{{ metric.displayValue }}</span>
            <span class="metric-label">{{ metric.label }}</span>
            <span class="metric-unit">{{ metric.unit }}</span>
          </div>
        </div>
      </div>

      <div class="charts-section">
        <div class="chart-card">
          <div class="chart-header">
            <h3>活跃商家趋势</h3>
          </div>
          <div class="chart-body">
            <div class="chart-container-wrapper">
              <div class="chart-container">
                <div class="y-axis">
                  <span v-for="(tick, index) in getYAxisTicks(trends.activeMerchants)" :key="index" class="y-tick">{{ tick }}</span>
                </div>
                <div class="chart-bars-scroll">
                  <div 
                    v-for="(value, index) in trends.activeMerchants" 
                    :key="index"
                    class="bar-wrapper"
                  >
                    <div 
                      class="bar" 
                      :style="{ height: getBarHeight(value, trends.activeMerchants) + '%' }"
                    >
                      <span class="bar-value">{{ value }}</span>
                    </div>
                    <span class="bar-label">{{ getLabel(index) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="isAllZero(trends.activeMerchants)" class="empty-chart-tip">暂无数据</div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>口碑分析使用趋势</h3>
          </div>
          <div class="chart-body">
            <div class="chart-container-wrapper">
              <div class="chart-container">
                <div class="y-axis">
                  <span v-for="(tick, index) in getYAxisTicks(trends.reputationAnalysis)" :key="index" class="y-tick">{{ tick }}</span>
                </div>
                <div class="chart-bars-scroll">
                  <div 
                    v-for="(value, index) in trends.reputationAnalysis" 
                    :key="index"
                    class="bar-wrapper"
                  >
                    <div 
                      class="bar bar-orange" 
                      :style="{ height: getBarHeight(value, trends.reputationAnalysis) + '%' }"
                    >
                      <span class="bar-value">{{ value }}</span>
                    </div>
                    <span class="bar-label">{{ getLabel(index) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="isAllZero(trends.reputationAnalysis)" class="empty-chart-tip">暂无数据</div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>竞品对比使用趋势</h3>
          </div>
          <div class="chart-body">
            <div class="chart-container-wrapper">
              <div class="chart-container">
                <div class="y-axis">
                  <span v-for="(tick, index) in getYAxisTicks(trends.competitorAnalysis)" :key="index" class="y-tick">{{ tick }}</span>
                </div>
                <div class="chart-bars-scroll">
                  <div 
                    v-for="(value, index) in trends.competitorAnalysis" 
                    :key="index"
                    class="bar-wrapper"
                  >
                    <div 
                      class="bar bar-purple" 
                      :style="{ height: getBarHeight(value, trends.competitorAnalysis) + '%' }"
                    >
                      <span class="bar-value">{{ value }}</span>
                    </div>
                    <span class="bar-label">{{ getLabel(index) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="isAllZero(trends.competitorAnalysis)" class="empty-chart-tip">暂无数据</div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>经营建议查看趋势</h3>
          </div>
          <div class="chart-body">
            <div class="chart-container-wrapper">
              <div class="chart-container">
                <div class="y-axis">
                  <span v-for="(tick, index) in getYAxisTicks(trends.businessAdvice)" :key="index" class="y-tick">{{ tick }}</span>
                </div>
                <div class="chart-bars-scroll">
                  <div 
                    v-for="(value, index) in trends.businessAdvice" 
                    :key="index"
                    class="bar-wrapper"
                  >
                    <div 
                      class="bar bar-green" 
                      :style="{ height: getBarHeight(value, trends.businessAdvice) + '%' }"
                    >
                      <span class="bar-value">{{ value }}</span>
                    </div>
                    <span class="bar-label">{{ getLabel(index) }}</span>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="isAllZero(trends.businessAdvice)" class="empty-chart-tip">暂无数据</div>
          </div>
        </div>
      </div>

      <div class="bottom-section">
        <div class="pie-chart-card">
          <div class="card-header">
            <h3>功能使用分布</h3>
          </div>
          <div class="pie-chart-container">
            <div class="pie-wrapper">
              <svg viewBox="0 0 200 200" class="pie-chart">
                <circle cx="100" cy="100" r="70" fill="none" stroke="#f5f5f5" stroke-width="30" />
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
          <div v-if="isFunctionUsageEmpty" class="empty-chart-tip">暂无数据</div>
        </div>

        <div class="reply-rate-card">
          <div class="card-header">
            <h3>评价回复率分析</h3>
          </div>
          <div class="reply-rate-content">
            <div class="reply-rate-circle">
              <svg viewBox="0 0 100 100" class="rate-circle">
                <circle cx="50" cy="50" r="40" fill="none" stroke="#f0f0f0" stroke-width="8"/>
                <circle 
                  cx="50" cy="50" r="40" 
                  fill="none" 
                  :stroke="getReplyRateColor(replyRate)" 
                  stroke-width="8"
                  stroke-linecap="round"
                  :stroke-dasharray="251.2"
                  :stroke-dashoffset="251.2 - (251.2 * replyRate / 100)"
                  transform="rotate(-90 50 50)"
                />
              </svg>
              <div class="rate-value">{{ replyRate.toFixed(1) }}%</div>
            </div>
            <div class="reply-rate-details">
              <div class="detail-item">
                <span class="detail-label">已回复评价</span>
                <span class="detail-value">{{ repliedReviews }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">可回复评价</span>
                <span class="detail-value">{{ totalReviews }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-label">计算公式</span>
                <span class="detail-value">已回复 ÷ 可回复 × 100%</span>
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
import { getMerchantStatisticsOverview, getMerchantStatisticsTrends } from '../../api/merchantStatistics'

const timeRange = ref('week')
const timeRanges = [
  { label: '按日', value: 'day' },
  { label: '按周', value: 'week' },
  { label: '按月', value: 'month' },
]

const today = new Date()
const todayStr = today.toISOString().split('T')[0]

const selectedDate = ref(todayStr)
const selectedYear = ref(today.getFullYear())
const selectedWeek = ref(getCurrentWeekNumber())
const selectedMonth = ref(todayStr.substring(0, 7))

const overview = ref(null)
const trends = ref({
  activeMerchants: [],
  reputationAnalysis: [],
  competitorAnalysis: [],
  businessAdvice: [],
})

const labels = ref([])

function getCurrentWeekNumber() {
  const now = new Date()
  const startOfYear = new Date(now.getFullYear(), 0, 1)
  const diff = now - startOfYear
  const oneWeek = 1000 * 60 * 60 * 24 * 7
  return Math.ceil(diff / oneWeek)
}

const availableYears = computed(() => {
  const currentYear = today.getFullYear()
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

const monthOptions = computed(() => {
  const options = []
  const year = today.getFullYear()
  for (let i = 1; i <= 12; i++) {
    options.push({
      value: `${year}-${String(i).padStart(2, '0')}`,
      label: `${year}年${i}月`
    })
  }
  return options
})

const metricList = computed(() => {
  if (!overview.value?.metrics) {
    return [
      { key: 'totalMerchants', label: '商家总数', value: 0, unit: '家', displayValue: '0', iconPath: 'M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-blue' },
      { key: 'activeMerchants', label: '活跃商家数', value: 0, unit: '家', displayValue: '0', iconPath: 'M13 2L3 14h9l-1 8 10-12h-9l1-8z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-green' },
      { key: 'replyRate', label: '评价回复率', value: 0, unit: '%', displayValue: '0%', iconPath: 'M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-orange' },
      { key: 'reputationAnalysisCalls', label: '口碑分析使用', value: 0, unit: '次', displayValue: '0', iconPath: 'M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-purple' },
      { key: 'competitorAnalysisCalls', label: '竞品对比使用', value: 0, unit: '次', displayValue: '0', iconPath: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zM6 4h7v5h5v11H6V4z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-blue' },
      { key: 'businessAdviceCalls', label: '经营建议查看', value: 0, unit: '次', displayValue: '0', iconPath: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-green' },
    ]
  }

  const m = overview.value.metrics
  return [
    { key: 'totalMerchants', label: '商家总数', value: m.totalMerchants || 0, unit: '家', displayValue: formatValue(m.totalMerchants), iconPath: 'M8 21l1-17a4 4 0 0 1 4-4h4a4 4 0 0 1 4 4l1 17', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-blue' },
    { key: 'activeMerchants', label: '活跃商家数', value: m.activeMerchants || 0, unit: '家', displayValue: formatValue(m.activeMerchants), iconPath: 'M13 2L3 14h9l-1 8 10-12h-9l1-8z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-green' },
    { key: 'replyRate', label: '评价回复率', value: m.replyRate || 0, unit: '%', displayValue: (m.replyRate || 0).toFixed(1) + '%', iconPath: 'M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-orange' },
    { key: 'reputationAnalysisCalls', label: '口碑分析使用', value: m.reputationAnalysisCalls || 0, unit: '次', displayValue: formatValue(m.reputationAnalysisCalls), iconPath: 'M9 19v-6a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2zm0 0V9a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v10m-6 0a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2m0 0V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-purple' },
    { key: 'competitorAnalysisCalls', label: '竞品对比使用', value: m.competitorAnalysisCalls || 0, unit: '次', displayValue: formatValue(m.competitorAnalysisCalls), iconPath: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zM6 4h7v5h5v11H6V4z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-blue' },
    { key: 'businessAdviceCalls', label: '经营建议查看', value: m.businessAdviceCalls || 0, unit: '次', displayValue: formatValue(m.businessAdviceCalls), iconPath: 'M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z', iconViewBox: '0 0 24 24', iconClass: 'metric-icon-green' },
  ]
})

const replyRate = computed(() => {
  return overview.value?.metrics?.replyRate || 0
})

const totalReviews = computed(() => {
  return overview.value?.metrics?.totalReviews || 0
})

const repliedReviews = computed(() => {
  return overview.value?.metrics?.repliedReviews || 0
})

const totalFunctionUsage = computed(() => {
  const m = overview.value?.metrics || {}
  return (m.reputationAnalysisCalls || 0) + (m.competitorAnalysisCalls || 0) + (m.businessAdviceCalls || 0)
})

const isFunctionUsageEmpty = computed(() => totalFunctionUsage.value === 0)

const pieColors = ['#8b5cf6', '#3b82f6', '#10b981']

const pieLegendItems = computed(() => {
  const m = overview.value?.metrics || {}
  const total = totalFunctionUsage.value
  const items = [
    { key: 'reputationAnalysisCalls', label: '口碑分析', value: m.reputationAnalysisCalls || 0, color: pieColors[0] },
    { key: 'competitorAnalysisCalls', label: '竞品对比', value: m.competitorAnalysisCalls || 0, color: pieColors[1] },
    { key: 'businessAdviceCalls', label: '经营建议', value: m.businessAdviceCalls || 0, color: pieColors[2] },
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
    const percent = totalFunctionUsage.value > 0 ? (item.value / totalFunctionUsage.value) : 0
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
  const rect = event.currentTarget.getBoundingClientRect()
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

const getReplyRateColor = (rate) => {
  if (rate >= 80) return '#52c41a'
  if (rate >= 50) return '#fa8c16'
  return '#ff4d4f'
}

const setTimeRange = async (range) => {
  timeRange.value = range
  await loadData()
}

watch(selectedYear, () => {
  if (selectedWeek.value > 52) {
    selectedWeek.value = 52
  }
})

const handleDateChange = async () => {
  await loadData()
}

const handleWeekChange = async () => {
  await loadData()
}

const handleMonthChange = async () => {
  await loadData()
}

const loadData = async () => {
  try {
    const params = {
      timeRange: timeRange.value
    }
    
    if (timeRange.value === 'day') {
      params.date = selectedDate.value
    } else if (timeRange.value === 'week') {
      params.week = `${selectedYear.value}-W${String(selectedWeek.value).padStart(2, '0')}`
    } else {
      params.month = selectedMonth.value
    }

    const [overviewRes, trendsRes] = await Promise.all([
      getMerchantStatisticsOverview(params),
      getMerchantStatisticsTrends(params),
    ])

    if (overviewRes.success) {
      overview.value = overviewRes.data
    }

    if (trendsRes.success) {
      trends.value = trendsRes.data.trends || trends.value
      labels.value = trendsRes.data.labels || []
    }
  } catch (error) {
    console.error('加载商家统计数据失败:', error)
  }
}

onMounted(() => {
  loadData()
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

.date-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selector-label {
  font-size: 14px;
  color: #5a6a7a;
}

.date-input, .week-select, .month-select {
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 14px;
  color: #1f2d3d;
  background: #fff;
  cursor: pointer;
}

.date-input:focus, .week-select:focus, .month-select:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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

.chart-container-wrapper {
  overflow-x: auto;
  overflow-y: hidden;
  border-radius: 8px;
  scrollbar-width: thin;
  scrollbar-color: #d9d9d9 transparent;
}

.chart-container-wrapper::-webkit-scrollbar {
  height: 6px;
}

.chart-container-wrapper::-webkit-scrollbar-track {
  background: transparent;
}

.chart-container-wrapper::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

.chart-container-wrapper::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}

.chart-container {
  display: flex;
  position: relative;
  min-width: max-content;
  height: 220px;
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

.chart-bars-scroll {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 100%;
  padding-left: 12px;
  padding-bottom: 4px;
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
  max-width: 36px;
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

.reply-rate-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.reply-rate-content {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 24px;
}

.reply-rate-circle {
  position: relative;
  width: 120px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rate-circle {
  width: 100%;
  height: 100%;
}

.rate-value {
  position: absolute;
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}

.reply-rate-details {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  gap: 40px;
}

.detail-label {
  font-size: 14px;
  color: #667085;
}

.detail-value {
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
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

  .reply-rate-content {
    flex-direction: column;
    gap: 20px;
  }

  .time-filter {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>