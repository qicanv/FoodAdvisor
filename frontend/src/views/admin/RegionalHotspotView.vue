<template>
  <AdminLayout title="区域消费热点分析" subtitle="分析各区域的消费热点和变化趋势，助力制定内容和运营策略">
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

    <div class="trend-section">
      <h2 class="section-title">趋势变化</h2>
      <div class="trend-cards">
        <div v-for="trend in stats.trendChanges" :key="trend.name" class="trend-card">
          <div class="trend-header">
            <span class="trend-name">{{ trend.name }}</span>
            <span :class="['trend-badge', trend.trend.toLowerCase()]">
              {{ trend.trend === 'UP' ? '📈 上升' : trend.trend === 'DOWN' ? '📉 下降' : trend.trend === 'NEW' ? '✨ 新增' : '➡️ 稳定' }}
            </span>
          </div>
          <div class="trend-values">
            <span class="trend-current">{{ trend.current }}</span>
            <span class="trend-arrow">→</span>
            <span class="trend-previous">{{ trend.previous }}</span>
          </div>
          <div class="trend-percent" :class="trend.changePercent >= 0 ? 'positive' : 'negative'">
            {{ trend.changePercent >= 0 ? '+' : '' }}{{ trend.changePercent }}%
          </div>
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
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getAllRegions, getRegionalHotspots } from '../../api/regionalHotspot'

const regions = ref([])
const selectedRegion = ref('CD')
const timeRange = ref('last7days')
const customStart = ref('')
const customEnd = ref('')
const loading = ref(false)

const stats = ref({
  regionCode: '',
  regionName: '',
  hotMerchants: [],
  hotCuisines: [],
  hotKeywords: [],
  consumptionPeriods: [],
  trendChanges: [],
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
  }
}

onMounted(() => {
  loadRegions()
  loadHotspots()
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

.trend-section {
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

.trend-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.trend-card {
  background: #f7f8fa;
  border-radius: 10px;
  padding: 16px;
}

.trend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.trend-name {
  font-size: 14px;
  font-weight: 500;
  color: #4e5969;
}

.trend-badge {
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 12px;
}

.trend-badge.up {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.trend-badge.down {
  background: rgba(245, 34, 45, 0.1);
  color: #f5222d;
}

.trend-badge.new {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.trend-badge.stable {
  background: rgba(143, 149, 158, 0.1);
  color: #8f959e;
}

.trend-values {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.trend-current {
  font-size: 22px;
  font-weight: 700;
  color: #1f2329;
}

.trend-arrow {
  font-size: 14px;
  color: #8f959e;
}

.trend-previous {
  font-size: 14px;
  color: #8f959e;
}

.trend-percent {
  font-size: 14px;
  font-weight: 500;
}

.trend-percent.positive {
  color: #52c41a;
}

.trend-percent.negative {
  color: #f5222d;
}

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
}

.rank-1 {
  background: linear-gradient(135deg, #ffd700 0%, #ffb700 100%);
}

.rank-2 {
  background: linear-gradient(135deg, #c0c0c0 0%, #a8a8a8 100%);
}

.rank-3 {
  background: linear-gradient(135deg, #cd7f32 0%, #b87333 100%);
}

.rank-other {
  background: #d9d9d9;
}

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

.empty-state {
  text-align: center;
  padding: 30px;
  color: #8f959e;
  font-size: 14px;
}

@media (max-width: 1024px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .trend-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .section-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .stats-cards {
    grid-template-columns: 1fr;
  }
  
  .trend-cards {
    grid-template-columns: 1fr;
  }
  
  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
