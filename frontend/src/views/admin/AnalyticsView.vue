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
      <div class="time-filter-bar">
        <div class="time-options">
          <button 
            v-for="option in timeRangeOptions" 
            :key="option.value"
            :class="['time-option', { active: selectedTimeRange === option.value }]"
            @click="selectTimeRange(option.value)"
          >
            {{ option.label }}
          </button>
        </div>
        <div class="custom-time">
          <input type="datetime-local" v-model="customStartTime" />
          <span class="time-separator">至</span>
          <input type="datetime-local" v-model="customEndTime" />
          <button class="apply-time-btn" @click="applyCustomTime">应用</button>
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
                  <span class="merchant-name">{{ item.merchantName || '未知商家' }}</span>
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
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getBehaviorStats, getBehaviorLogs } from '../../api/behavior'

const sidebarItems = [
  { key: 'overview', label: '数据概览', icon: '📊' },
  { key: 'tags', label: '热门标签', icon: '🏷️' },
  { key: 'logs', label: '事件日志', icon: '📝' },
]

const activeTab = ref('overview')
const selectedTimeRange = ref('7days')
const customStartTime = ref('')
const customEndTime = ref('')
const eventTypeFilter = ref('')

const stats = ref({})
const logs = ref([])
const loading = ref(false)

const timeRangeOptions = [
  { label: '今日', value: 'today' },
  { label: '昨日', value: 'yesterday' },
  { label: '近7天', value: '7days' },
  { label: '近30天', value: '30days' },
  { label: '自定义', value: 'custom' },
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
  return Object.values(dailyMap).slice(-7)
})

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

const selectTimeRange = (range) => {
  selectedTimeRange.value = range
  loadStats()
}

const applyCustomTime = () => {
  if (customStartTime.value && customEndTime.value) {
    selectedTimeRange.value = 'custom'
    loadStats()
  }
}

const buildTimeParams = () => {
  const now = new Date()
  let startTime, endTime

  switch (selectedTimeRange.value) {
    case 'today':
      startTime = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      endTime = now
      break
    case 'yesterday':
      startTime = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
      endTime = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      break
    case '7days':
      startTime = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
      endTime = now
      break
    case '30days':
      startTime = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)
      endTime = now
      break
    case 'custom':
      startTime = customStartTime.value ? new Date(customStartTime.value) : new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
      endTime = customEndTime.value ? new Date(customEndTime.value) : now
      break
    default:
      startTime = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
      endTime = now
  }

  return {
    startTime: formatDateTime(startTime),
    endTime: formatDateTime(endTime),
  }
}

const formatDateTime = (date) => {
  return date.toISOString().slice(0, 19)
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
    }
    const response = await getBehaviorLogs(params)
    if (response.success && response.data) {
      logs.value = response.data
    }
  } catch (error) {
    console.error('加载日志失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<style scoped>
.tab-content {
  width: 100%;
}

.time-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
  padding: 16px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  flex-wrap: wrap;
}

.time-options {
  display: flex;
  gap: 8px;
}

.time-option {
  padding: 8px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 20px;
  background: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.time-option:hover {
  border-color: #1890ff;
  color: #1890ff;
}

.time-option.active {
  background: #1890ff;
  color: #fff;
  border-color: #1890ff;
}

.custom-time {
  display: flex;
  align-items: center;
  gap: 8px;
}

.custom-time input {
  padding: 8px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
}

.time-separator {
  color: #999;
}

.apply-time-btn {
  padding: 8px 16px;
  background: #1890ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
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
  height: 200px;
}

.trend-bars {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  height: 160px;
  padding-top: 20px;
}

.trend-bar-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.trend-bar {
  width: 32px;
  background: linear-gradient(180deg, #1890ff 0%, #40a9ff 100%);
  border-radius: 4px 4px 0 0;
  transition: height 0.5s ease;
  min-height: 5px;
}

.trend-date {
  font-size: 12px;
  color: #999;
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
</style>
