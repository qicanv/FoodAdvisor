<template>
  <MerchantLayout title="经营统计" subtitle="查看店铺营收数据，支持多维度数据分析">
    <div class="statistics-container">
      <div class="time-selector">
        <div class="time-tabs">
          <button 
            v-for="tab in timeTabs" 
            :key="tab.value"
            :class="['time-tab', { active: activeTimeUnit === tab.value }]"
            @click="switchTimeUnit(tab.value)"
          >
            <svg :viewBox="tab.iconViewBox" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
              <path :d="tab.iconPath" />
            </svg>
            <span>{{ tab.label }}</span>
          </button>
        </div>
        <div class="date-selector">
          <button v-if="activeTimeUnit !== 'custom'" class="date-picker-btn" @click="showDatePicker = !showDatePicker">
            <span class="selected-date">{{ selectedDateLabel }}</span>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 9l-7 7-7-7"></path>
            </svg>
          </button>
          <div v-else class="custom-date-range">
            <input type="date" v-model="customStartDate" class="date-input" @change="onCustomDateChange" />
            <span class="date-separator">至</span>
            <input type="date" v-model="customEndDate" class="date-input" @change="onCustomDateChange" />
          </div>
          <button class="refresh-btn" @click="loadData">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/>
            </svg>
          </button>
        </div>
      </div>

      <div v-if="showDatePicker" class="date-picker-overlay" @click="showDatePicker = false">
        <div class="date-picker-panel" @click.stop>
          <div class="picker-header">
            <button class="nav-btn" @click="navigatePicker(-1)">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M15 19l-7-7 7-7"></path>
              </svg>
            </button>
            <div class="picker-title-wrap">
              <span v-if="activeTimeUnit === 'day' || activeTimeUnit === 'week'" class="picker-title">{{ pickerYear }}年{{ pickerMonth + 1 }}月</span>
              <span v-else-if="activeTimeUnit === 'month'" class="picker-title">{{ pickerYear }}年</span>
              <span v-else class="picker-title">{{ pickerYear - 5 }}年 - {{ pickerYear + 6 }}年</span>
              <button v-if="activeTimeUnit === 'day' || activeTimeUnit === 'week'" class="year-select-btn" @click="showYearPicker = !showYearPicker">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M19 9l-7 7-7-7"></path>
                </svg>
              </button>
            </div>
            <button class="nav-btn" @click="navigatePicker(1)">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M9 5l7 7-7 7"></path>
              </svg>
            </button>
          </div>
          
          <div v-if="showYearPicker" class="year-picker-popup">
            <div class="year-picker-grid">
              <div 
                v-for="(year, index) in yearPickerYears" 
                :key="index"
                :class="['year-picker-cell', { 'selected': year.isSelected, 'current-year': year.isCurrent }]"
                @click="selectYearFromPicker(year)"
              >{{ year.year }}</div>
            </div>
          </div>
          
          <div v-if="activeTimeUnit === 'day'" class="day-calendar">
            <div class="weekday-header">
              <span v-for="day in weekdays" :key="day">{{ day }}</span>
            </div>
            <div class="days-grid">
              <div 
                v-for="(day, index) in calendarDays" 
                :key="index"
                :class="['day-cell', { 'other-month': !day.currentMonth, 'today': day.isToday, 'selected': day.isSelected }]"
                @click="selectDay(day)"
              >{{ day.date }}</div>
            </div>
          </div>
          
          <div v-else-if="activeTimeUnit === 'week'" class="week-calendar">
            <div class="weekday-header">
              <span v-for="day in weekdays" :key="day">{{ day }}</span>
            </div>
            <div class="weeks-grid">
              <div 
                v-for="(week, index) in calendarWeeks" 
                :key="index"
                :class="['week-row', { 'selected': week.isSelected }]"
                @click="selectWeek(week)"
              >
                <div v-for="(day, dIndex) in week.days" :key="dIndex" :class="['week-day', { 'other-month': !day.currentMonth }]">
                  {{ day.date }}
                </div>
                <span class="week-range">{{ formatWeekRange(week) }}</span>
              </div>
            </div>
          </div>
          
          <div v-else-if="activeTimeUnit === 'month'" class="month-calendar">
            <div class="months-grid">
              <div 
                v-for="(month, index) in calendarMonths" 
                :key="index"
                :class="['month-cell', { 'selected': month.isSelected, 'current-month': month.isCurrent }]"
                @click="selectMonth(month)"
              >
                <span class="month-name">{{ month.name }}</span>
                <span v-if="month.isCurrent" class="current-badge">今</span>
              </div>
            </div>
          </div>
          
          <div v-else-if="activeTimeUnit === 'year'" class="year-calendar">
            <div class="years-grid">
              <div 
                v-for="(year, index) in calendarYears" 
                :key="index"
                :class="['year-cell', { 'selected': year.isSelected, 'current-year': year.isCurrent }]"
                @click="selectYear(year)"
              >{{ year.year }}</div>
            </div>
          </div>
          
          <div class="picker-footer">
            <button v-if="activeTimeUnit === 'day'" class="today-btn" @click="selectToday">今天</button>
            <button v-if="activeTimeUnit === 'week'" class="today-btn" @click="selectThisWeek">本周</button>
            <button v-if="activeTimeUnit === 'month'" class="today-btn" @click="selectThisMonth">本月</button>
            <button v-if="activeTimeUnit === 'year'" class="today-btn" @click="selectThisYear">本年</button>
          </div>
        </div>
      </div>

      <div class="summary-cards">
        <div class="summary-card">
          <div class="summary-icon revenue">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <line x1="12" y1="1" x2="12" y2="23"></line>
              <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">总营收</p>
            <p class="summary-value">￥{{ summary.totalRevenue.toLocaleString() }}</p>
            <p :class="['summary-change', summary.revenueChange >= 0 ? 'positive' : 'negative']">
              {{ summary.revenueChange >= 0 ? '↑' : '↓' }} {{ Math.abs(summary.revenueChange) }}%
            </p>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-icon orders">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">总订单</p>
            <p class="summary-value">{{ summary.totalOrders.toLocaleString() }}</p>
            <p :class="['summary-change', summary.orderChange >= 0 ? 'positive' : 'negative']">
              {{ summary.orderChange >= 0 ? '↑' : '↓' }} {{ Math.abs(summary.orderChange) }}%
            </p>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-icon avg-order">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
              <polyline points="9 12 12 15 15 12"></polyline>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">客单价</p>
            <p class="summary-value">￥{{ summary.avgOrderValue.toFixed(2) }}</p>
            <p :class="['summary-change', summary.avgOrderChange >= 0 ? 'positive' : 'negative']">
              {{ summary.avgOrderChange >= 0 ? '↑' : '↓' }} {{ Math.abs(summary.avgOrderChange) }}%
            </p>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-icon reviews">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">平均评分</p>
            <p class="summary-value">{{ summary.avgRating.toFixed(1) }}</p>
            <p :class="['summary-change', summary.ratingChange >= 0 ? 'positive' : 'negative']">
              {{ summary.ratingChange >= 0 ? '↑' : '↓' }} {{ Math.abs(summary.ratingChange) }}
            </p>
          </div>
        </div>
      </div>

      <div class="charts-grid">
        <div class="chart-card large">
          <div class="chart-header">
            <h3>营收趋势</h3>
            <div class="chart-legend">
              <div class="legend-item">
                <span class="legend-color" style="background: #52c41a"></span>
                <span>营收</span>
              </div>
              <div class="legend-item">
                <span class="legend-color" style="background: #1890ff"></span>
                <span>订单数</span>
              </div>
            </div>
          </div>
          <div class="chart-content">
            <svg viewBox="0 0 700 350" class="trend-chart">
              <defs>
                <linearGradient id="revenueArea" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#52c41a;stop-opacity:0.2" />
                  <stop offset="100%" style="stop-color:#52c41a;stop-opacity:0" />
                </linearGradient>
                <linearGradient id="ordersArea" x1="0%" y1="0%" x2="0%" y2="100%">
                  <stop offset="0%" style="stop-color:#1890ff;stop-opacity:0.2" />
                  <stop offset="100%" style="stop-color:#1890ff;stop-opacity:0" />
                </linearGradient>
              </defs>
              <g class="grid-lines">
                <line v-for="i in 5" :key="'h'+i" x1="60" :y1="30 + i * 60" x2="640" :y2="30 + i * 60" stroke="#eee" stroke-width="1" />
              </g>
              <g class="y-axis">
                <text v-for="i in 5" :key="'y'+i" x="50" :y="35 + i * 60" fill="#999" font-size="12" text-anchor="end">{{ formatCurrency((5 - i) * maxRevenueValue / 5) }}</text>
              </g>
              <g class="x-axis">
                <text v-for="(item, index) in trendData" :key="'x'+index" :x="60 + index * xStep" y="330" fill="#999" font-size="12" text-anchor="middle" :transform="`rotate(-45 ${60 + index * xStep} 330)`">{{ item.label }}</text>
              </g>
              <path :d="revenueAreaPath" fill="url(#revenueArea)" />
              <path :d="revenueLinePath" fill="none" stroke="#52c41a" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <circle v-for="(item, index) in trendData" :key="'r'+index" :cx="60 + index * xStep" :cy="revenueY(item.revenue)" r="4" fill="#52c41a" />
              <path :d="ordersAreaPath" fill="url(#ordersArea)" />
              <path :d="ordersLinePath" fill="none" stroke="#1890ff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              <circle v-for="(item, index) in trendData" :key="'o'+index" :cx="60 + index * xStep" :cy="ordersY(item.orders)" r="4" fill="#1890ff" />
            </svg>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>营收构成</h3>
          </div>
          <div class="chart-content">
            <div class="pie-chart-wrapper">
              <svg viewBox="0 0 200 200" class="pie-chart">
                <circle cx="100" cy="100" r="70" fill="none" stroke="#f0f0f0" stroke-width="30" />
                <circle
                  v-for="(segment, index) in revenueSegments"
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
                <text x="100" y="95" text-anchor="middle" class="pie-center-value">￥{{ summary.totalRevenue.toLocaleString() }}</text>
                <text x="100" y="115" text-anchor="middle" class="pie-center-label">总收入</text>
              </svg>
            </div>
            <div class="pie-legend">
              <div v-for="item in revenueComposition" :key="item.name" class="legend-row">
                <span class="legend-color" :style="{ background: item.color }"></span>
                <span class="legend-name">{{ item.name }}</span>
                <span class="legend-value">￥{{ item.value.toLocaleString() }}</span>
                <span class="legend-percent">{{ item.percentage }}%</span>
              </div>
            </div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>评价分布</h3>
          </div>
          <div class="chart-content">
            <div class="rating-dist">
              <div v-for="rating in ratingDist" :key="rating.star" class="rating-row">
                <span class="rating-star">{{ rating.star }}星</span>
                <div class="rating-bar">
                  <div class="rating-fill" :style="{ width: rating.percentage + '%', background: getRatingColor(rating.star) }"></div>
                </div>
                <span class="rating-count">{{ rating.count }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-header">
            <h3>时段分析</h3>
          </div>
          <div class="chart-content">
            <div class="time-bar-chart">
              <div v-for="period in timePeriods" :key="period.name" class="time-bar-item">
                <span class="time-label">{{ period.name }}</span>
                <div class="time-bar">
                  <div class="time-fill" :style="{ width: period.percentage + '%' }"></div>
                </div>
                <span class="time-value">￥{{ period.revenue.toLocaleString() }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="data-table-card">
        <div class="chart-header">
          <h3>详细数据</h3>
        </div>
        <div class="table-wrapper">
          <table class="data-table">
            <thead>
              <tr>
                <th>{{ timeUnitLabel }}</th>
                <th>订单数</th>
                <th>营收</th>
                <th>客单价</th>
                <th>评价数</th>
                <th>平均评分</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in trendData" :key="item.label">
                <td>{{ item.label }}</td>
                <td>{{ item.orders }}</td>
                <td>￥{{ item.revenue.toLocaleString() }}</td>
                <td>￥{{ item.avgOrder.toFixed(2) }}</td>
                <td>{{ item.reviews }}</td>
                <td>{{ item.rating.toFixed(1) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import MerchantLayout from '../../components/MerchantLayout.vue'

const activeTimeUnit = ref('month')
const showDatePicker = ref(false)
const showYearPicker = ref(false)
const pickerYear = ref(new Date().getFullYear())
const pickerMonth = ref(new Date().getMonth())
const selectedDate = ref(new Date())

const customStartDate = ref('')
const customEndDate = ref('')

const weekdays = ['日', '一', '二', '三', '四', '五', '六']
const monthNames = ['一月', '二月', '三月', '四月', '五月', '六月', '七月', '八月', '九月', '十月', '十一月', '十二月']

const timeTabs = [
  { label: '按天', value: 'day', iconViewBox: '0 0 24 24', iconPath: 'M19 4h-1V2h-2v2H8V2H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z' },
  { label: '按周', value: 'week', iconViewBox: '0 0 24 24', iconPath: 'M19 4h-1V2h-2v2H8V2H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11zM7 11h5v5H7z' },
  { label: '按月', value: 'month', iconViewBox: '0 0 24 24', iconPath: 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM9 10H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2z' },
  { label: '按年', value: 'year', iconViewBox: '0 0 24 24', iconPath: 'M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM7 10h5v5H7z' },
  { label: '自定义', value: 'custom', iconViewBox: '0 0 24 24', iconPath: 'M19 4h-1V2h-2v2H8V2H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z' },
]

const summary = ref({
  totalRevenue: 156800,
  totalOrders: 1256,
  avgOrderValue: 124.85,
  avgRating: 4.6,
  revenueChange: 15.2,
  orderChange: 12.8,
  avgOrderChange: 2.1,
  ratingChange: 0.1
})

const trendData = ref([
  { label: '1月', orders: 280, revenue: 34800, avgOrder: 124.29, reviews: 85, rating: 4.5 },
  { label: '2月', orders: 220, revenue: 27500, avgOrder: 125.00, reviews: 68, rating: 4.6 },
  { label: '3月', orders: 310, revenue: 38750, avgOrder: 125.00, reviews: 95, rating: 4.7 },
  { label: '4月', orders: 246, revenue: 30750, avgOrder: 125.00, reviews: 72, rating: 4.6 },
])

const revenueComposition = ref([
  { name: '堂食', value: 94080, percentage: 60, color: '#52c41a' },
  { name: '外卖', value: 47040, percentage: 30, color: '#1890ff' },
  { name: '其他', value: 15680, percentage: 10, color: '#faad14' },
])

const ratingDist = ref([
  { star: 5, count: 580, percentage: 65 },
  { star: 4, count: 240, percentage: 27 },
  { star: 3, count: 60, percentage: 7 },
  { star: 2, count: 10, percentage: 1 },
  { star: 1, count: 6, percentage: 0 },
])

const timePeriods = ref([
  { name: '早餐', revenue: 15680, percentage: 10 },
  { name: '午餐', revenue: 47040, percentage: 30 },
  { name: '晚餐', revenue: 62720, percentage: 40 },
  { name: '夜宵', revenue: 31360, percentage: 20 },
])

const pickerTitle = computed(() => {
  if (activeTimeUnit.value === 'day') {
    return `${pickerYear.value}年${pickerMonth.value + 1}月`
  } else if (activeTimeUnit.value === 'week') {
    return `${pickerYear.value}年${pickerMonth.value + 1}月`
  } else if (activeTimeUnit.value === 'month') {
    return `${pickerYear.value}年`
  } else {
    return `${pickerYear.value - 5}年 - ${pickerYear.value + 6}年`
  }
})

const selectedDateLabel = computed(() => {
  const year = selectedDate.value.getFullYear()
  const month = selectedDate.value.getMonth() + 1
  const day = selectedDate.value.getDate()
  
  if (activeTimeUnit.value === 'day') {
    return `${year}年${month}月${day}日`
  } else if (activeTimeUnit.value === 'week') {
    const weekNum = getWeekNumber(selectedDate.value)
    return `${year}年第${weekNum}周`
  } else if (activeTimeUnit.value === 'month') {
    return `${year}年${month}月`
  } else if (activeTimeUnit.value === 'year') {
    return `${year}年`
  } else {
    if (customStartDate.value && customEndDate.value) {
      return `${customStartDate.value} 至 ${customEndDate.value}`
    }
    return '请选择日期范围'
  }
})

const calendarDays = computed(() => {
  const year = pickerYear.value
  const month = pickerMonth.value
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  const startDay = firstDay.getDay()
  const days = []
  
  const prevMonthLastDay = new Date(year, month, 0).getDate()
  for (let i = startDay - 1; i >= 0; i--) {
    const date = prevMonthLastDay - i
    const d = new Date(year, month - 1, date)
    days.push({
      date,
      currentMonth: false,
      isToday: isToday(d),
      isSelected: isSelectedDay(d),
      fullDate: d
    })
  }
  
  for (let i = 1; i <= lastDay.getDate(); i++) {
    const d = new Date(year, month, i)
    days.push({
      date: i,
      currentMonth: true,
      isToday: isToday(d),
      isSelected: isSelectedDay(d),
      fullDate: d
    })
  }
  
  const remaining = 42 - days.length
  for (let i = 1; i <= remaining; i++) {
    const d = new Date(year, month + 1, i)
    days.push({
      date: i,
      currentMonth: false,
      isToday: isToday(d),
      isSelected: isSelectedDay(d),
      fullDate: d
    })
  }
  
  return days
})

const calendarWeeks = computed(() => {
  const year = pickerYear.value
  const month = pickerMonth.value
  const firstDay = new Date(year, month, 1)
  const lastDay = new Date(year, month + 1, 0)
  
  const weeks = []
  let currentWeek = []
  let weekStart = new Date(firstDay)
  
  if (weekStart.getDay() !== 1) {
    const diff = weekStart.getDay() === 0 ? 6 : weekStart.getDay() - 1
    weekStart = new Date(year, month, 1 - diff)
  }
  
  while (weekStart <= lastDay || currentWeek.length < 7) {
    const dayInfo = {
      date: weekStart.getDate(),
      currentMonth: weekStart.getMonth() === month,
      fullDate: new Date(weekStart)
    }
    currentWeek.push(dayInfo)
    
    if (currentWeek.length === 7) {
      const weekNumber = getWeekNumber(currentWeek[0].fullDate)
      const isSelected = isSelectedWeek(currentWeek)
      weeks.push({
        days: [...currentWeek],
        number: weekNumber,
        isSelected,
        startDate: new Date(currentWeek[0].fullDate),
        endDate: new Date(currentWeek[6].fullDate)
      })
      currentWeek = []
    }
    
    weekStart.setDate(weekStart.getDate() + 1)
  }
  
  return weeks
})

const calendarMonths = computed(() => {
  const year = pickerYear.value
  const today = new Date()
  return monthNames.map((name, index) => ({
    name,
    month: index,
    year,
    isSelected: selectedDate.value.getFullYear() === year && selectedDate.value.getMonth() === index,
    isCurrent: today.getFullYear() === year && today.getMonth() === index
  }))
})

const calendarYears = computed(() => {
  const year = pickerYear.value
  const today = new Date()
  const years = []
  for (let i = -5; i <= 6; i++) {
    const y = year + i
    years.push({
      year: y,
      isSelected: selectedDate.value.getFullYear() === y,
      isCurrent: today.getFullYear() === y
    })
  }
  return years
})

const maxRevenueValue = computed(() => {
  const max = Math.max(...trendData.value.map(d => d.revenue))
  return Math.ceil(max / 10000) * 10000
})

const xStep = computed(() => {
  const count = trendData.value.length
  return count > 0 ? (640 - 60) / (count - 1) : 0
})

const timeUnitLabel = computed(() => {
  const labels = { day: '日期', week: '周', month: '月份', year: '年份' }
  return labels[activeTimeUnit.value] || '时间'
})

const yearPickerYears = computed(() => {
  const year = pickerYear.value
  const today = new Date()
  const years = []
  for (let i = -5; i <= 5; i++) {
    const y = year + i
    years.push({
      year: y,
      isSelected: selectedDate.value.getFullYear() === y,
      isCurrent: today.getFullYear() === y
    })
  }
  return years
})

const formatCurrency = (value) => {
  return '￥' + Math.round(value).toLocaleString()
}

const isToday = (date) => {
  const today = new Date()
  return date.getFullYear() === today.getFullYear() &&
         date.getMonth() === today.getMonth() &&
         date.getDate() === today.getDate()
}

const isSelectedDay = (date) => {
  return selectedDate.value.getFullYear() === date.getFullYear() &&
         selectedDate.value.getMonth() === date.getMonth() &&
         selectedDate.value.getDate() === date.getDate()
}

const isSelectedWeek = (week) => {
  const selectedWeekNum = getWeekNumber(selectedDate.value)
  return selectedWeekNum === week.number &&
         selectedDate.value.getFullYear() === week.days[0].fullDate.getFullYear()
}

const getWeekNumber = (date) => {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const dayNum = d.getUTCDay() || 7
  d.setUTCDate(d.getUTCDate() + 4 - dayNum)
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1))
  return Math.ceil((((d - yearStart) / 86400000) + 1) / 7)
}

const revenueY = (value) => {
  return 300 - (value / maxRevenueValue.value) * 270
}

const ordersY = (value) => {
  const maxOrders = Math.max(...trendData.value.map(d => d.orders))
  return 300 - (value / maxOrders) * 270
}

const revenueLinePath = computed(() => {
  if (trendData.value.length === 0) return ''
  return trendData.value.map((item, index) => {
    const x = 60 + index * xStep.value
    const y = revenueY(item.revenue)
    return `${index === 0 ? 'M' : 'L'} ${x} ${y}`
  }).join(' ')
})

const revenueAreaPath = computed(() => {
  if (trendData.value.length === 0) return ''
  const line = trendData.value.map((item, index) => {
    const x = 60 + index * xStep.value
    const y = revenueY(item.revenue)
    return `${index === 0 ? 'M' : 'L'} ${x} ${y}`
  }).join(' ')
  const lastX = 60 + (trendData.value.length - 1) * xStep.value
  return `${line} L ${lastX} 300 L 60 300 Z`
})

const ordersLinePath = computed(() => {
  if (trendData.value.length === 0) return ''
  return trendData.value.map((item, index) => {
    const x = 60 + index * xStep.value
    const y = ordersY(item.orders)
    return `${index === 0 ? 'M' : 'L'} ${x} ${y}`
  }).join(' ')
})

const ordersAreaPath = computed(() => {
  if (trendData.value.length === 0) return ''
  const line = trendData.value.map((item, index) => {
    const x = 60 + index * xStep.value
    const y = ordersY(item.orders)
    return `${index === 0 ? 'M' : 'L'} ${x} ${y}`
  }).join(' ')
  const lastX = 60 + (trendData.value.length - 1) * xStep.value
  return `${line} L ${lastX} 300 L 60 300 Z`
})

const revenueSegments = computed(() => {
  let offset = 0
  const total = 2 * Math.PI * 70
  return revenueComposition.value.map(item => {
    const length = (item.percentage / 100) * total
    const segment = {
      color: item.color,
      dashArray: `${length} ${total - length}`,
      dashOffset: -offset
    }
    offset += length
    return segment
  })
})

const getRatingColor = (star) => {
  const colors = { 5: '#52c41a', 4: '#73d13d', 3: '#faad14', 2: '#ffa940', 1: '#ff4d4f' }
  return colors[star] || '#ccc'
}

const switchTimeUnit = (unit) => {
  activeTimeUnit.value = unit
  pickerYear.value = selectedDate.value.getFullYear()
  pickerMonth.value = selectedDate.value.getMonth()
  loadData()
}

const navigatePicker = (direction) => {
  if (activeTimeUnit.value === 'day' || activeTimeUnit.value === 'week') {
    pickerMonth.value += direction
    if (pickerMonth.value > 11) {
      pickerMonth.value = 0
      pickerYear.value++
    } else if (pickerMonth.value < 0) {
      pickerMonth.value = 11
      pickerYear.value--
    }
  } else if (activeTimeUnit.value === 'month') {
    pickerYear.value += direction
  } else {
    pickerYear.value += direction * 10
  }
}

const selectDay = (day) => {
  selectedDate.value = new Date(day.fullDate)
  showDatePicker.value = false
  loadData()
}

const selectWeek = (week) => {
  selectedDate.value = new Date(week.startDate)
  showDatePicker.value = false
  loadData()
}

const selectMonth = (month) => {
  selectedDate.value = new Date(month.year, month.month, 1)
  showDatePicker.value = false
  loadData()
}

const selectYear = (year) => {
  selectedDate.value = new Date(year.year, 0, 1)
  showDatePicker.value = false
  loadData()
}

const selectYearFromPicker = (year) => {
  pickerYear.value = year.year
  showYearPicker.value = false
}

const formatWeekRange = (week) => {
  const startMonth = week.startDate.getMonth() + 1
  const startDay = week.startDate.getDate()
  const endMonth = week.endDate.getMonth() + 1
  const endDay = week.endDate.getDate()
  if (startMonth === endMonth) {
    return `${startMonth}/${startDay}-${endDay}`
  } else {
    return `${startMonth}/${startDay}-${endMonth}/${endDay}`
  }
}

const selectToday = () => {
  const today = new Date()
  selectedDate.value = today
  pickerYear.value = today.getFullYear()
  pickerMonth.value = today.getMonth()
  showDatePicker.value = false
  loadData()
}

const selectThisWeek = () => {
  const today = new Date()
  const diff = today.getDay() === 0 ? 6 : today.getDay() - 1
  const weekStart = new Date(today.getFullYear(), today.getMonth(), today.getDate() - diff)
  selectedDate.value = weekStart
  pickerYear.value = today.getFullYear()
  pickerMonth.value = today.getMonth()
  showDatePicker.value = false
  loadData()
}

const selectThisMonth = () => {
  const today = new Date()
  selectedDate.value = new Date(today.getFullYear(), today.getMonth(), 1)
  pickerYear.value = today.getFullYear()
  showDatePicker.value = false
  loadData()
}

const selectThisYear = () => {
  const today = new Date()
  selectedDate.value = new Date(today.getFullYear(), 0, 1)
  pickerYear.value = today.getFullYear()
  showDatePicker.value = false
  loadData()
}

const onCustomDateChange = () => {
  if (customStartDate.value && customEndDate.value) {
    if (new Date(customStartDate.value) > new Date(customEndDate.value)) {
      const temp = customStartDate.value
      customStartDate.value = customEndDate.value
      customEndDate.value = temp
    }
    loadData()
  }
}

const loadData = () => {
}

onMounted(() => {
  pickerYear.value = selectedDate.value.getFullYear()
  pickerMonth.value = selectedDate.value.getMonth()
  
  const today = new Date()
  const lastWeek = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000)
  customStartDate.value = lastWeek.toISOString().split('T')[0]
  customEndDate.value = today.toISOString().split('T')[0]
})

watch(activeTimeUnit, () => {
  pickerYear.value = selectedDate.value.getFullYear()
  pickerMonth.value = selectedDate.value.getMonth()
  
  if (activeTimeUnit.value === 'custom') {
    const today = new Date()
    const lastWeek = new Date(today.getTime() - 7 * 24 * 60 * 60 * 1000)
    customStartDate.value = lastWeek.toISOString().split('T')[0]
    customEndDate.value = today.toISOString().split('T')[0]
  }
})
</script>

<style scoped>
.statistics-container {
  width: 100%;
}

.time-selector {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 12px;
  padding: 16px 24px;
  margin-bottom: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.time-tabs {
  display: flex;
  gap: 8px;
}

.time-tab {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  font-size: 14px;
  color: #667085;
  background: #f5f7fa;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.time-tab:hover {
  background: #eef2f7;
}

.time-tab.active {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
}

.date-selector {
  display: flex;
  align-items: center;
  gap: 12px;
}

.date-picker-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 14px;
  color: #1f2d3d;
  background: #f5f7fa;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.date-picker-btn:hover {
  background: #eef2f7;
  border-color: #52c41a;
}

.selected-date {
  font-weight: 500;
}

.refresh-btn {
  padding: 8px;
  background: #f5f7fa;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  color: #667085;
  cursor: pointer;
  transition: all 0.2s;
}

.refresh-btn:hover {
  background: #eef2f7;
}

.custom-date-range {
  display: flex;
  align-items: center;
  gap: 8px;
}

.date-input {
  padding: 8px 12px;
  font-size: 14px;
  color: #1f2d3d;
  background: #f5f7fa;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.date-input:focus {
  outline: none;
  border-color: #52c41a;
  background: #fff;
}

.date-separator {
  font-size: 14px;
  color: #667085;
}

.date-picker-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  z-index: 1000;
  padding: 100px 32px 32px;
}

.date-picker-panel {
  background: #fff;
  border-radius: 12px;
  width: 400px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.nav-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border: none;
  border-radius: 6px;
  color: #667085;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-btn:hover {
  background: #eef2f7;
  color: #1f2d3d;
}

.picker-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.picker-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
}

.year-select-btn {
  padding: 4px;
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  transition: color 0.2s;
}

.year-select-btn:hover {
  color: #52c41a;
}

.year-picker-popup {
  padding: 16px 24px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}

.year-picker-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}

.year-picker-cell {
  padding: 12px;
  text-align: center;
  font-size: 14px;
  color: #1f2d3d;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.year-picker-cell:hover {
  background: #f5f7fa;
}

.year-picker-cell.selected {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
  font-weight: 600;
}

.year-picker-cell.current-year {
  background: #f6ffed;
  color: #52c41a;
}

.day-calendar, .week-calendar {
  padding: 16px 24px;
}

.weekday-header {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  margin-bottom: 12px;
}

.weekday-header span {
  text-align: center;
  font-size: 13px;
  color: #999;
  padding: 8px 0;
}

.days-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.day-cell {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #1f2d3d;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.day-cell:hover {
  background: #f5f7fa;
}

.day-cell.other-month {
  color: #ccc;
}

.day-cell.today {
  background: #f6ffed;
  color: #52c41a;
  font-weight: 600;
}

.day-cell.selected {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
  font-weight: 600;
}

.weeks-grid {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.week-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr) 70px;
  align-items: center;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.week-row:hover {
  background: #f5f7fa;
}

.week-row.selected {
  background: #f6ffed;
}

.week-day {
  text-align: center;
  font-size: 13px;
  color: #1f2d3d;
}

.week-day.other-month {
  color: #ccc;
}

.week-number {
  text-align: center;
  font-size: 12px;
  color: #52c41a;
  font-weight: 500;
}

.month-calendar, .year-calendar {
  padding: 16px 24px;
}

.months-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.month-cell {
  padding: 16px;
  text-align: center;
  font-size: 14px;
  color: #1f2d3d;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.month-cell:hover {
  background: #f5f7fa;
}

.month-cell.selected {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
  font-weight: 600;
}

.month-cell.current-month {
  background: #f6ffed;
  color: #52c41a;
}

.years-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.year-cell {
  padding: 16px;
  text-align: center;
  font-size: 14px;
  color: #1f2d3d;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.year-cell:hover {
  background: #f5f7fa;
}

.year-cell.selected {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
  font-weight: 600;
}

.year-cell.current-year {
  background: #f6ffed;
  color: #52c41a;
}

.week-range {
  text-align: center;
  font-size: 12px;
  color: #52c41a;
  font-weight: 500;
}

.month-cell {
  padding: 16px;
  text-align: center;
  font-size: 14px;
  color: #1f2d3d;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.month-name {
  font-weight: 500;
}

.current-badge {
  font-size: 11px;
  color: #52c41a;
  background: #f6ffed;
  padding: 2px 6px;
  border-radius: 4px;
}

.picker-footer {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: center;
}

.today-btn {
  padding: 10px 24px;
  font-size: 14px;
  color: #52c41a;
  background: #f6ffed;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.today-btn:hover {
  background: #d9f7be;
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.summary-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.summary-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.summary-icon.revenue {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
}

.summary-icon.orders {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
}

.summary-icon.avg-order {
  background: linear-gradient(135deg, #faad14 0%, #ffc53d 100%);
}

.summary-icon.reviews {
  background: linear-gradient(135deg, #722ed1 0%, #9254de 100%);
}

.summary-info {
  flex: 1;
}

.summary-label {
  font-size: 14px;
  color: #667085;
  margin: 0;
}

.summary-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2d3d;
  margin: 4px 0 0;
}

.summary-change {
  font-size: 13px;
  margin: 4px 0 0;
}

.summary-change.positive {
  color: #52c41a;
}

.summary-change.negative {
  color: #ff4d4f;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  margin-bottom: 24px;
}

.chart-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.chart-card.large {
  grid-column: span 2;
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

.chart-legend {
  display: flex;
  gap: 20px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #667085;
}

.legend-color {
  width: 12px;
  height: 12px;
  border-radius: 3px;
}

.chart-content {
  height: 300px;
}

.trend-chart {
  width: 100%;
  height: 100%;
}

.pie-chart-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
}

.pie-chart {
  width: 180px;
  height: 180px;
}

.pie-center-value {
  font-size: 18px;
  font-weight: 700;
  fill: #1f2d3d;
}

.pie-center-label {
  font-size: 12px;
  fill: #999;
}

.pie-legend {
  margin-top: 16px;
}

.legend-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.legend-row:last-child {
  border-bottom: none;
}

.legend-name {
  font-size: 13px;
  color: #1f2d3d;
}

.legend-value {
  font-size: 13px;
  color: #667085;
}

.legend-percent {
  font-size: 13px;
  font-weight: 600;
  color: #52c41a;
}

.rating-dist {
  padding-top: 16px;
}

.rating-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.rating-row:last-child {
  margin-bottom: 0;
}

.rating-star {
  width: 40px;
  font-size: 13px;
  color: #667085;
}

.rating-bar {
  flex: 1;
  height: 10px;
  background: #f0f0f0;
  border-radius: 5px;
  overflow: hidden;
}

.rating-fill {
  height: 100%;
  border-radius: 5px;
  transition: width 0.3s;
}

.rating-count {
  width: 40px;
  font-size: 13px;
  color: #667085;
  text-align: right;
}

.time-bar-chart {
  padding-top: 16px;
}

.time-bar-item {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.time-bar-item:last-child {
  margin-bottom: 0;
}

.time-label {
  width: 60px;
  font-size: 13px;
  color: #667085;
}

.time-bar {
  flex: 1;
  height: 24px;
  background: #f0f0f0;
  border-radius: 12px;
  overflow: hidden;
}

.time-fill {
  height: 100%;
  background: linear-gradient(90deg, #52c41a 0%, #73d13d 100%);
  border-radius: 12px;
  transition: width 0.3s;
}

.time-value {
  width: 80px;
  font-size: 13px;
  color: #1f2d3d;
  text-align: right;
}

.data-table-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.table-wrapper {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th,
.data-table td {
  padding: 16px 24px;
  text-align: left;
  font-size: 14px;
}

.data-table th {
  background: #fafafa;
  color: #667085;
  font-weight: 600;
  border-bottom: 2px solid #f0f0f0;
}

.data-table td {
  color: #1f2d3d;
  border-bottom: 1px solid #f0f0f0;
}

.data-table tr:hover td {
  background: #fafafa;
}

@media (max-width: 1200px) {
  .summary-cards {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .charts-grid {
    grid-template-columns: 1fr;
  }
  
  .chart-card.large {
    grid-column: span 1;
  }
}

@media (max-width: 768px) {
  .summary-cards {
    grid-template-columns: 1fr;
  }
  
  .time-selector {
    flex-direction: column;
    gap: 16px;
  }
}
</style>