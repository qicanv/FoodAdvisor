<template>
  <AdminLayout title="运营数据分析" subtitle="分析用户搜索、点击和反馈行为，优化推荐功能和运营内容">
    <template #sidebar>
      <div class="page-sidebar-nav">
        <span class="page-sidebar-title">数据分析</span>
        <div class="page-sidebar-items-wrapper">
          <div 
            v-for="item in sidebarItems" 
            :key="item.key"
            :class="['page-sidebar-item', { active: activeTab === item.key }]"
            @click="activeTab = item.key"
          >
            <span class="menu-icon">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </div>
    </template>

    <div class="tab-content">
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

      <div v-if="activeTab === 'overview'" class="overview-content">
        <div class="stats-cards">
          <div class="stat-card">
            <div class="stat-icon">📊</div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(stats.totalEvents || 0) }}</div>
              <div class="stat-label">总事件数</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">👥</div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(stats.activeUsers || 0) }}</div>
              <div class="stat-label">活跃用户数</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">🔍</div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(searchCount) }}</div>
              <div class="stat-label">搜索次数</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">👆</div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(clickCount) }}</div>
              <div class="stat-label">点击次数</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon">💬</div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(stats.feedbackCount || 0) }}</div>
              <div class="stat-label">反馈次数</div>
            </div>
          </div>
        </div>

        <div class="charts-row">
          <div class="chart-card">
            <h3 class="chart-title">事件类型分布</h3>
            <div class="event-distribution">
              <div 
                v-for="(event, index) in eventStats" 
                :key="index"
                class="event-bar-item"
              >
                <span class="event-label">{{ getEventTypeName(event.eventType) }}</span>
                <div class="event-bar-container">
                  <div 
                    class="event-bar" 
                    :style="{ width: getBarWidth(event.count), backgroundColor: getBarColor(index) }"
                  ></div>
                </div>
                <span class="event-count">{{ event.count }}</span>
              </div>
              <div v-if="eventStats.length === 0" class="empty-state">
                <p>暂无事件数据</p>
              </div>
            </div>
          </div>

          <div class="chart-card">
            <h3 class="chart-title">每日趋势</h3>
            <div class="daily-trend">
              <div class="trend-bars">
                <div 
                  v-for="(day, index) in dailyTrendData" 
                  :key="index"
                  class="trend-bar-wrapper"
                >
                  <div 
                    class="trend-bar" 
                    :style="{ height: getTrendHeight(day.total) }"
                    :title="`${day.date}: ${day.total}次`"
                  ></div>
                  <span class="trend-date">{{ day.date }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="charts-row">
          <div class="chart-card">
            <h3 class="chart-title">热门搜索词 TOP10</h3>
            <div class="rank-list">
              <div 
                v-for="(item, index) in hotKeywords.slice(0, 10)" 
                :key="index"
                class="rank-item"
              >
                <span :class="['rank-number', { top: index < 3 }]">{{ index + 1 }}</span>
                <span class="rank-keyword">{{ item.keyword }}</span>
                <span class="rank-count">{{ item.count }}次</span>
              </div>
              <div v-if="hotKeywords.length === 0" class="empty-state">
                <p>暂无搜索数据</p>
              </div>
            </div>
          </div>

          <div class="chart-card">
            <h3 class="chart-title">热门场景 TOP10</h3>
            <div class="rank-list">
              <div 
                v-for="(item, index) in hotScenes.slice(0, 10)" 
                :key="index"
                class="rank-item"
              >
                <span :class="['rank-number', { top: index < 3 }]">{{ index + 1 }}</span>
                <span class="rank-keyword">{{ getSceneName(item.scene) }}</span>
                <span class="rank-count">{{ item.count }}次</span>
              </div>
              <div v-if="hotScenes.length === 0" class="empty-state">
                <p>暂无场景数据</p>
              </div>
            </div>
          </div>
        </div>

        <div class="charts-row">
          <div class="chart-card full-width">
            <h3 class="chart-title">热门商家 TOP15</h3>
            <div class="merchant-rank-list">
              <div 
                v-for="(item, index) in hotMerchants.slice(0, 15)" 
                :key="index"
                class="merchant-rank-item"
              >
                <span :class="['rank-number', { top: index < 3 }]">{{ index + 1 }}</span>
                <div class="merchant-info">
                  <span class="merchant-name">{{ item.merchantName || item.name || item.shopName || '未知商家' }}</span>
                  <span class="merchant-id">#{{ item.merchantId }}</span>
                </div>
                <span class="merchant-count">{{ item.count }}次点击</span>
              </div>
              <div v-if="hotMerchants.length === 0" class="empty-state">
                <p>暂无商家点击数据</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="activeTab === 'tags'" class="tags-content">
        <div class="chart-card">
          <h3 class="chart-title">热门标签 TOP15</h3>
          <div class="rank-list">
            <div 
              v-for="(item, index) in hotTags.slice(0, 15)" 
              :key="index"
              class="rank-item"
            >
              <span :class="['rank-number', { top: index < 3 }]">{{ index + 1 }}</span>
              <span class="rank-keyword">{{ item.tagname || item.tagName || item.tag }}</span>
              <span class="rank-count">{{ item.count }}次</span>
            </div>
            <div v-if="hotTags.length === 0" class="empty-state">
              <p>暂无标签点击数据</p>
            </div>
          </div>
        </div>
      </div>

      <div v-if="activeTab === 'logs'" class="logs-content">
        <div class="search-bar">
          <select v-model="eventTypeFilter" class="search-select">
            <option value="">全部类型</option>
            <option value="SEARCH">搜索</option>
            <option value="MERCHANT_CLICK">商家点击</option>
            <option value="SCENE_ENTRY">场景入口</option>
            <option value="TOPIC_CLICK">专题点击</option>
            <option value="TAG_CLICK">标签点击</option>
            <option value="FEEDBACK">反馈</option>
          </select>
          <button class="search-btn" @click="loadLogs">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"></circle>
              <path d="M21 21l-4.35-4.35"></path>
            </svg>
            <span>筛选</span>
          </button>
        </div>

        <div class="logs-table">
          <table>
            <thead>
              <tr>
                <th>时间</th>
                <th>事件类型</th>
                <th>关键词/商家</th>
                <th>用户ID</th>
                <th>IP地址</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(log, index) in logs" :key="index">
                <td>{{ formatTime(log.createdAt) }}</td>
                <td><span :class="['event-type-badge', log.eventType.toLowerCase()]">{{ getEventTypeName(log.eventType) }}</span></td>
                <td>{{ getLogDetail(log) }}</td>
                <td>{{ log.userId || '-' }}</td>
                <td>{{ log.ipAddress || '-' }}</td>
              </tr>
              <tr v-if="logs.length === 0">
                <td colspan="5" class="empty-cell">暂无日志数据</td>
              </tr>
            </tbody>
          </table>
        </div>
        
        <div v-if="pagination.total > 0" class="pagination-section">
          <button class="page-btn" :disabled="pagination.pageNum <= 1" @click="changePage(pagination.pageNum - 1)">上一页</button>
          <span class="page-info">第 {{ pagination.pageNum }} / {{ pagination.totalPages }} 页</span>
          <button class="page-btn" :disabled="pagination.pageNum >= pagination.totalPages" @click="changePage(pagination.pageNum + 1)">下一页</button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getBehaviorStats, getBehaviorLogs } from '../../api/behavior'

function getCurrentWeekNumber() {
  const now = new Date()
  const startOfYear = new Date(now.getFullYear(), 0, 1)
  const diff = now - startOfYear
  const oneWeek = 1000 * 60 * 60 * 24 * 7
  return Math.ceil(diff / oneWeek)
}

function getFirstDayOfWeek(year, weekNum) {
  const date = new Date(year, 0, 1)
  const dayOfWeek = date.getDay() || 7
  const diff = (weekNum - 1) * 7 - (dayOfWeek - 1)
  date.setDate(date.getDate() + diff)
  return date
}

const sidebarItems = [
  { key: 'overview', label: '数据概览', icon: '📊' },
  { key: 'tags', label: '热门标签', icon: '🏷️' },
  { key: 'logs', label: '事件日志', icon: '📝' },
]

const activeTab = ref('overview')
const timeRange = ref('week')
const today = new Date()
const todayStr = today.toISOString().split('T')[0]
const selectedDate = ref(todayStr)
const selectedYear = ref(today.getFullYear())
const selectedWeek = ref(getCurrentWeekNumber())
const selectedMonth = ref(todayStr.substring(0, 7))
const eventTypeFilter = ref('')

const stats = ref({})
const logs = ref([])
const loading = ref(false)

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0,
  totalPages: 0
})

const timeRanges = [
  { label: '按日', value: 'day' },
  { label: '按周', value: 'week' },
  { label: '按月', value: 'month' },
]

const hotKeywords = computed(() => stats.value.hotKeywords || [])
const hotScenes = computed(() => stats.value.hotScenes || [])
const hotMerchants = computed(() => stats.value.hotMerchants || [])
const hotTags = computed(() => stats.value.hotTags || [])
const eventStats = computed(() => stats.value.eventStats || [])

const searchCount = computed(() => {
  return stats.value.searchCount || 0
})

const clickCount = computed(() => {
  return stats.value.clickCount || 0
})

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

const dailyTrendData = computed(() => {
  const dailyStats = stats.value.dailyStats || []
  const dailyMap = {}
  dailyStats.forEach(item => {
    const date = item.date
    if (!dailyMap[date]) {
      dailyMap[date] = { date, total: 0 }
    }
    dailyMap[date].total += item.count || 0
  })

  const dates = generateDateRange(timeRange.value)
  return dates.map(dateStr => ({
    date: dateStr,
    total: dailyMap[dateStr] ? dailyMap[dateStr].total : 0
  }))
})

const generateDateRange = (range) => {
  const dates = []
  let startDate
  let daysToGenerate

  switch (range) {
    case 'day':
      const dateParts = selectedDate.value.split('-')
      startDate = new Date(dateParts[0], dateParts[1] - 1, dateParts[2])
      daysToGenerate = 1
      break
    case 'week':
      startDate = getFirstDayOfWeek(selectedYear.value, selectedWeek.value)
      daysToGenerate = 7
      break
    case 'month':
      const monthParts = selectedMonth.value.split('-')
      startDate = new Date(monthParts[0], monthParts[1] - 1, 1)
      daysToGenerate = new Date(monthParts[0], monthParts[1], 0).getDate()
      break
    default:
      startDate = getFirstDayOfWeek(today.getFullYear(), getCurrentWeekNumber())
      daysToGenerate = 7
  }

  for (let i = 0; i < daysToGenerate; i++) {
    const d = new Date(startDate)
    d.setDate(startDate.getDate() + i)
    const year = d.getUTCFullYear()
    const month = String(d.getUTCMonth() + 1).padStart(2, '0')
    const day = String(d.getUTCDate()).padStart(2, '0')
    dates.push(`${year}-${month}-${day}`)
  }

  return dates
}

const formatNumber = (num) => {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toString()
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN')
}

const getEventTypeName = (type) => {
  const map = {
    'SEARCH': '搜索',
    'MERCHANT_CLICK': '商家点击',
    'SCENE_ENTRY': '场景入口',
    'TOPIC_CLICK': '专题点击',
    'TAG_CLICK': '标签点击',
    'FEEDBACK': '反馈',
  }
  return map[type] || type
}

const getSceneName = (scene) => {
  const map = {
    'DATE': '约会',
    'FRIENDS': '朋友聚会',
    'FAMILY': '家庭聚餐',
    'LATE_NIGHT': '夜宵',
    'BUSINESS': '商务宴请',
  }
  return map[scene] || scene
}

const getBarWidth = (count) => {
  const counts = eventStats.value.map(e => Number(e.count) || 0)
  const maxCount = Math.max(...counts, 1)
  return ((Number(count) / maxCount) * 100) + '%'
}

const getBarColor = (index) => {
  const colors = ['#1890ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#13c2c2']
  return colors[index % colors.length]
}

const getTrendHeight = (count) => {
  const totals = dailyTrendData.value.map(d => Number(d.total) || 0)
  const maxCount = Math.max(...totals, 1)
  return Math.max((Number(count) / maxCount) * 100, 5) + '%'
}

const getLogDetail = (log) => {
  if (log.searchKeyword) return `搜索: ${log.searchKeyword}`
  if (log.merchantId) return `商家ID: ${log.merchantId}`
  if (log.sceneType) return `场景: ${getSceneName(log.sceneType)}`
  if (log.topicId) return `专题ID: ${log.topicId}`
  if (log.tagCode) return `标签: ${log.tagCode}`
  if (log.feedbackType) return `反馈: ${log.feedbackType}`
  return '-'
}

const setTimeRange = async (range) => {
  timeRange.value = range
  await loadStats()
}

watch(selectedYear, () => {
  if (selectedWeek.value > 52) {
    selectedWeek.value = 52
  }
})

const handleDateChange = async () => {
  await loadStats()
}

const handleWeekChange = async () => {
  await loadStats()
}

const handleMonthChange = async () => {
  await loadStats()
}

const buildTimeParams = () => {
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
  
  return params
}

const loadStats = async () => {
  loading.value = true
  try {
    const params = buildTimeParams()
    
    const response = await getBehaviorStats(params)
    if (response.success && response.data) {
      stats.value = response.data
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  } finally {
    loading.value = false
  }
}

const loadLogs = async () => {
  loading.value = true
  try {
    const params = {
      ...buildTimeParams(),
      eventType: eventTypeFilter.value || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
    }
    const response = await getBehaviorLogs(params)
    if (response.success && response.data) {
      logs.value = response.data.list || response.data
      pagination.total = response.data.total || 0
      pagination.totalPages = response.data.totalPages || Math.ceil(pagination.total / pagination.pageSize)
    }
  } catch (error) {
    console.error('加载日志失败:', error)
  } finally {
    loading.value = false
  }
}

const changePage = (page) => {
  if (page >= 1 && page <= pagination.totalPages) {
    pagination.pageNum = page
    loadLogs()
  }
}

onMounted(() => {
  loadStats()
})

watch(activeTab, (newTab) => {
  if (newTab === 'logs') {
    loadLogs()
  } else if (newTab === 'overview') {
    loadStats()
  }
})
</script>

<style scoped>
.tab-content {
  width: 100%;
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

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.stat-icon {
  font-size: 40px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #333;
}

.stat-label {
  font-size: 14px;
  color: #999;
  margin-top: 4px;
}

.charts-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.chart-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 20px;
}

.event-distribution {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.event-bar-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.event-label {
  width: 100px;
  font-size: 14px;
  color: #666;
  flex-shrink: 0;
}

.event-bar-container {
  flex: 1;
  height: 20px;
  background: #f5f5f5;
  border-radius: 10px;
  overflow: hidden;
}

.event-bar {
  height: 100%;
  border-radius: 10px;
  transition: width 0.5s ease;
}

.event-count {
  width: 60px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  text-align: right;
}

.daily-trend {
  overflow-x: auto;
  overflow-y: hidden;
  border-radius: 8px;
  scrollbar-width: thin;
  scrollbar-color: #d9d9d9 transparent;
}

.daily-trend::-webkit-scrollbar {
  height: 6px;
}

.daily-trend::-webkit-scrollbar-track {
  background: transparent;
}

.daily-trend::-webkit-scrollbar-thumb {
  background: #d9d9d9;
  border-radius: 3px;
}

.daily-trend::-webkit-scrollbar-thumb:hover {
  background: #bfbfbf;
}

.trend-bars {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 180px;
  padding: 12px;
  min-width: max-content;
}

.trend-bar-wrapper {
  flex-shrink: 0;
  width: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  justify-content: flex-end;
  position: relative;
}

.trend-bar {
  width: 100%;
  max-width: 36px;
  background: linear-gradient(180deg, #1890ff 0%, #40a9ff 100%);
  border-radius: 6px 6px 0 0;
  transition: height 0.5s ease;
  min-height: 5px;
}

.trend-bar:hover {
  opacity: 0.8;
}

.trend-date {
  font-size: 11px;
  color: #909399;
  margin-top: 8px;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rank-number {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 600;
  color: #999;
}

.rank-number.top {
  background: linear-gradient(135deg, #ff6700 0%, #ff9500 100%);
  color: #fff;
}

.rank-keyword {
  flex: 1;
  font-size: 14px;
  color: #333;
}

.rank-count {
  font-size: 14px;
  font-weight: 600;
  color: #1890ff;
}

.merchant-rank-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.merchant-rank-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.merchant-rank-item:last-child {
  border-bottom: none;
}

.merchant-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.merchant-name {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.merchant-id {
  font-size: 12px;
  color: #999;
}

.merchant-count {
  font-size: 14px;
  font-weight: 600;
  color: #1890ff;
}

.logs-content {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  padding: 24px;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.search-select {
  padding: 10px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  background: #fff;
}

.search-btn {
  padding: 10px 20px;
  background: #f5f5f5;
  color: #666;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.logs-table {
  overflow-x: auto;
}

.logs-table table {
  width: 100%;
  border-collapse: collapse;
}

.logs-table th,
.logs-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #f5f5f5;
}

.logs-table th {
  background: #fafafa;
  font-size: 14px;
  font-weight: 600;
  color: #666;
}

.logs-table td {
  font-size: 14px;
  color: #333;
}

.empty-cell {
  text-align: center !important;
  color: #999;
}

.event-type-badge {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.event-type-badge.search {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.event-type-badge.merchant-click {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.event-type-badge.scene-entry {
  background: rgba(250, 173, 20, 0.1);
  color: #faad14;
}

.event-type-badge.topic-click {
  background: rgba(114, 46, 209, 0.1);
  color: #722ed1;
}

.event-type-badge.tag-click {
  background: rgba(19, 194, 194, 0.1);
  color: #13c2c2;
}

.event-type-badge.feedback {
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}

.pagination-section {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.page-btn {
  padding: 8px 16px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  background: #fff;
  color: #5a6a7a;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
}

.page-btn:hover:not(:disabled) {
  border-color: #1890ff;
  color: #1890ff;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: #5a6a7a;
}
</style>
