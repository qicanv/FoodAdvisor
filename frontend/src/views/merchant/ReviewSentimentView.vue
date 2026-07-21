<template>
  <MerchantLayout title="评价情感分析" subtitle="AI分析顾客评价的情感倾向、关键标签和差评归因">
    <template #sidebar>
      <div class="page-sidebar-nav">
        <span class="page-sidebar-title">分析导航</span>
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

    <!-- ==================== 顶部概览卡片 ==================== -->
    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon stat-icon-blue">📊</div>
        <div class="stat-info">
          <span class="stat-value">{{ reviews.length }}</span>
          <span class="stat-label">评价总数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-green">✅</div>
        <div class="stat-info">
          <span class="stat-value">{{ positiveCount }}</span>
          <span class="stat-label">正面评价</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-orange">⚠️</div>
        <div class="stat-info">
          <span class="stat-value">{{ negativeCount }}</span>
          <span class="stat-label">负面评价</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon-purple">🤖</div>
        <div class="stat-info">
          <span class="stat-value">{{ analyzedCount }}</span>
          <span class="stat-label">已AI分析</span>
        </div>
      </div>
    </div>

    <!-- ==================== Tab 1: 评价列表与分析 ==================== -->
    <div v-if="activeTab === 'reviews'" class="tab-content">
      <!-- 操作栏 -->
      <div class="action-bar">
        <el-button
          type="primary"
          :loading="batchLoading"
          @click="handleBatchAnalyze"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="margin-right:4px">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"/>
          </svg>
          一键批量分析全部评价
        </el-button>
        <span class="action-hint" v-if="unanalyzedCount > 0">
          还有 <strong>{{ unanalyzedCount }}</strong> 条评价待分析
        </span>
        <span class="action-hint done" v-else-if="reviews.length > 0">
          全部评价已分析完成 ✅
        </span>
      </div>

      <!-- 标签筛选栏 -->
      <div class="filter-bar" v-if="tagStats.length > 0">
        <span class="filter-label">按标签筛选：</span>
        <el-tag
          v-for="tag in tagStats"
          :key="tag.tagCode"
          :type="selectedTag === tag.tagCode ? 'primary' : 'info'"
          :effect="selectedTag === tag.tagCode ? 'dark' : 'plain'"
          class="filter-tag"
          @click="onTagFilterClick(tag)"
        >
          {{ tag.tagName || tag.tagCode }}
          <span class="tag-count">({{ tag.totalCount || tag.count || 0 }})</span>
        </el-tag>
        <el-tag
          v-if="selectedTag"
          type="danger"
          effect="plain"
          class="filter-tag"
          @click="clearTagFilter"
        >
          清除 ✕
        </el-tag>
      </div>

      <!-- 评价列表 -->
      <div class="review-table-container" v-if="reviews.length > 0">
        <el-table
          :data="reviews"
          stripe
          v-loading="reviewLoading"
          @row-click="onReviewClick"
          highlight-current-row
          :row-class-name="getRowClass"
        >
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="username" label="用户" width="100" />
          <el-table-column label="评分" width="80">
            <template #default="{ row }">
              <span class="rating-stars">{{ '⭐'.repeat(row.rating || 0) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="content" label="评价内容" min-width="220">
            <template #default="{ row }">
              <span class="review-content-text">{{ truncateText(row.content, 60) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="情感分析" width="120">
            <template #default="{ row }">
              <span v-if="row.analysis" :class="['sentiment-badge', getSentimentClass(row.analysis.sentiment)]">
                {{ getSentimentText(row.analysis.sentiment) }}
              </span>
              <span v-else class="sentiment-badge sentiment-none">未分析</span>
            </template>
          </el-table-column>
          <el-table-column label="置信度" width="85">
            <template #default="{ row }">
              <span v-if="row.analysis" :class="row.analysis.lowConfidence ? 'confidence-low' : 'confidence-ok'">
                {{ (row.analysis.confidence * 100).toFixed(0) }}%
              </span>
              <span v-else class="confidence-na">-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button
                size="small"
                type="primary"
                :loading="analyzingId === row.id"
                @click.stop="handleAnalyze(row)"
              >
                {{ row.analysis ? '重新分析' : '分析' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else-if="!reviewLoading" class="empty-state">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <p>暂无评价数据</p>
      </div>

      <!-- 分析详情弹窗 -->
      <el-dialog
        v-model="detailVisible"
        title="评价情感分析详情"
        width="750px"
        destroy-on-close
      >
        <div v-if="selectedReview" class="analysis-detail">
          <!-- 原始评价 -->
          <div class="detail-section">
            <h4>📝 评价原文</h4>
            <div class="review-original">
              <div class="review-meta">
                <span class="review-user">{{ selectedReview.username }}</span>
                <span class="review-rating">{{ '⭐'.repeat(selectedReview.rating || 0) }}</span>
              </div>
              <p class="review-text-full">{{ selectedReview.content }}</p>
            </div>
          </div>

          <!-- AI分析结果 -->
          <div class="detail-section" v-if="selectedReview.analysis">
            <h4>🤖 AI分析结果</h4>

            <div class="result-card sentiment-card">
              <div class="result-row">
                <span class="result-label">整体情感：</span>
                <span :class="['sentiment-badge', 'sentiment-large', getSentimentClass(selectedReview.analysis.sentiment)]">
                  {{ getSentimentText(selectedReview.analysis.sentiment) }}
                </span>
                <span class="confidence-text">
                  置信度：<strong>{{ (selectedReview.analysis.confidence * 100).toFixed(1) }}%</strong>
                  <el-tag v-if="selectedReview.analysis.lowConfidence" type="warning" size="small" style="margin-left:8px">低置信度</el-tag>
                </span>
              </div>
              <div class="result-row" v-if="selectedReview.analysis.modelName">
                <span class="result-label">模型：</span>
                <span class="model-name">{{ selectedReview.analysis.modelName }}</span>
              </div>
            </div>

            <div class="result-card" v-if="selectedReview.analysis.keywords && selectedReview.analysis.keywords.length">
              <span class="result-label">关键词：</span>
              <el-tag v-for="kw in selectedReview.analysis.keywords" :key="kw" size="small" class="keyword-tag" style="margin: 2px 4px 2px 0">
                {{ kw }}
              </el-tag>
            </div>

            <div class="result-card" v-if="selectedReview.analysis.aspects && selectedReview.analysis.aspects.length">
              <span class="result-label">方面分析：</span>
              <div class="aspect-list">
                <div v-for="aspect in selectedReview.analysis.aspects" :key="aspect.category" class="aspect-item">
                  <span class="aspect-category">{{ getAspectName(aspect.category) }}</span>
                  <span :class="['aspect-sentiment', getSentimentClass(aspect.sentiment)]">{{ getSentimentText(aspect.sentiment) }}</span>
                  <span class="aspect-text" v-if="aspect.text">"{{ aspect.text }}"</span>
                </div>
              </div>
            </div>

            <div class="result-card" v-if="selectedReview.analysis.tags && selectedReview.analysis.tags.length">
              <span class="result-label">评价标签：</span>
              <div class="tag-list">
                <div v-for="tag in selectedReview.analysis.tags" :key="tag.tagCode" class="tag-item">
                  <el-tag :type="getTagSentimentType(tag.sentiment)" size="small" effect="dark">{{ tag.tagName || tag.tagCode }}</el-tag>
                  <span class="tag-evidence" v-if="tag.evidenceText">"{{ tag.evidenceText }}"</span>
                  <span class="tag-confidence">{{ (tag.confidence * 100).toFixed(0) }}%</span>
                </div>
              </div>
            </div>

            <div class="result-card issue-card" v-if="selectedReview.analysis.issueCategories && selectedReview.analysis.issueCategories.length">
              <span class="result-label">⚠️ 差评归因：</span>
              <div class="issue-list">
                <div v-for="issue in selectedReview.analysis.issueCategories" :key="issue.category" class="issue-item">
                  <el-tag type="danger" size="small" effect="dark">{{ issue.categoryName || issue.category }}</el-tag>
                  <span class="issue-evidence" v-if="issue.evidenceText">"{{ issue.evidenceText }}"</span>
                  <span class="tag-confidence">{{ (issue.confidence * 100).toFixed(0) }}%</span>
                </div>
              </div>
            </div>

            <div class="result-card" v-if="selectedReview.analysis.negativeReason && !selectedReview.analysis.issueCategories?.length">
              <span class="result-label">差评原因：</span>
              <el-tag type="danger" size="small">{{ selectedReview.analysis.negativeReason }}</el-tag>
            </div>

            <div class="result-card" v-if="selectedReview.analysis.status === 'FAILED'">
              <el-alert type="error" :title="'分析失败'" :description="selectedReview.analysis.errorMessage || '未知错误'" show-icon :closable="false" />
            </div>
          </div>

          <div v-else class="detail-section">
            <div class="no-analysis-tip">
              <p>该评价尚未进行AI情感分析</p>
              <el-button type="primary" :loading="analyzingId === selectedReview.id" @click="handleAnalyze(selectedReview)">立即分析</el-button>
            </div>
          </div>
        </div>
      </el-dialog>
    </div>

    <!-- ==================== Tab 2: 统计分析 ==================== -->
    <div v-if="activeTab === 'stats'" class="tab-content">
      <div class="time-filter-bar">
        <span class="filter-label">时间范围：</span>
        <el-date-picker
          v-model="statsDateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="loadIssueStats"
        />
      </div>

      <div class="charts-row">
        <div class="chart-card">
          <h3 class="chart-title">情感倾向分布</h3>
          <div ref="sentimentChartRef" class="chart-container"></div>
        </div>
        <div class="chart-card" v-if="tagStats.length > 0">
          <h3 class="chart-title">评价标签分布</h3>
          <div ref="tagChartRef" class="chart-container"></div>
        </div>
        <div class="chart-card chart-card-full" v-if="issueStats.length > 0">
          <h3 class="chart-title">差评归因分析</h3>
          <div ref="issueChartRef" class="chart-container chart-container-tall"></div>
        </div>
        <div class="chart-card chart-card-full" v-if="reviews.length === 0 && !reviewLoading">
          <div class="empty-state" style="border:none;background:transparent">
            <p>暂无分析数据</p>
          </div>
        </div>
      </div>
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import MerchantLayout from '../../components/MerchantLayout.vue'
import {
  getMerchantReviews,
  analyzeReview,
  getReviewAnalysis,
  batchAnalyzeReviews,
  getMerchantReviewTags,
  getMerchantIssueStats,
} from '../../api/reviewAnalysis'

// ==================== 侧边栏 ====================
const activeTab = ref('reviews')
const sidebarItems = [
  { key: 'reviews', label: '评价列表与分析', icon: '📋' },
  { key: 'stats', label: '情感统计概览', icon: '📊' },
]

// ==================== 商家ID ====================
const currentMerchantId = computed(() => {
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.id || null
  } catch {
    return null
  }
})

// ==================== 评价列表 ====================
const reviews = ref([])
const reviewLoading = ref(false)
const analyzingId = ref(null)
const batchLoading = ref(false)
const tagStats = ref([])
const selectedTag = ref('')

const positiveCount = computed(() =>
  reviews.value.filter(r => r.analysis && r.analysis.sentiment === 'POSITIVE').length
)
const negativeCount = computed(() =>
  reviews.value.filter(r => r.analysis && r.analysis.sentiment === 'NEGATIVE').length
)
const analyzedCount = computed(() =>
  reviews.value.filter(r => r.analysis).length
)
const unanalyzedCount = computed(() =>
  reviews.value.filter(r => !r.analysis).length
)

const loadReviews = async () => {
  if (!currentMerchantId.value) return
  reviewLoading.value = true
  try {
    const params = { pageNum: 1, pageSize: 100 }
    if (selectedTag.value) params.tagCode = selectedTag.value
    const res = await getMerchantReviews(currentMerchantId.value, params)
    if (res.success) {
      const rawReviews = res.data?.value || res.data?.records || []
      reviews.value = await Promise.all(
        rawReviews.map(async (r) => {
          try {
            const ar = await getReviewAnalysis(r.id)
            if (ar.success && ar.data) return { ...r, analysis: ar.data }
          } catch (_) { /* ignore */ }
          return { ...r, analysis: null }
        })
      )
    }
  } catch (e) {
    console.error('加载评价列表失败', e)
  } finally {
    reviewLoading.value = false
  }
}

const loadTagStats = async () => {
  if (!currentMerchantId.value) return
  try {
    const res = await getMerchantReviewTags(currentMerchantId.value)
    if (res.success) tagStats.value = res.data || []
  } catch (e) {
    console.error('加载标签统计失败', e)
  }
}

const onTagFilterClick = (tag) => {
  selectedTag.value = selectedTag.value === tag.tagCode ? '' : tag.tagCode
  loadReviews()
}

const clearTagFilter = () => {
  selectedTag.value = ''
  loadReviews()
}

// ==================== 分析详情 ====================
const detailVisible = ref(false)
const selectedReview = ref(null)

const onReviewClick = (row) => {
  selectedReview.value = row
  detailVisible.value = true
}

const handleAnalyze = async (row) => {
  analyzingId.value = row.id
  try {
    const res = await analyzeReview(row.id)
    if (res.success) {
      ElMessage.success('AI分析完成')
      const idx = reviews.value.findIndex(r => r.id === row.id)
      if (idx >= 0) {
        reviews.value[idx] = { ...reviews.value[idx], analysis: res.data }
        if (selectedReview.value?.id === row.id) selectedReview.value = reviews.value[idx]
      }
      loadTagStats()
      loadIssueStats()
    } else {
      ElMessage.error(res.message || '分析失败')
    }
  } catch (e) {
    ElMessage.error('分析请求失败')
  } finally {
    analyzingId.value = null
  }
}

const handleBatchAnalyze = async () => {
  if (!currentMerchantId.value) return
  batchLoading.value = true
  try {
    const res = await batchAnalyzeReviews(currentMerchantId.value)
    if (res.success) {
      const d = res.data
      ElMessage.success(`批量分析完成：成功 ${d.successCount || 0}，跳过 ${d.skippedCount || 0}，失败 ${d.failedCount || 0}`)
      await loadReviews()
      await loadTagStats()
      await loadIssueStats()
    } else {
      ElMessage.error(res.message || '批量分析失败')
    }
  } catch (e) {
    ElMessage.error('批量分析请求失败')
  } finally {
    batchLoading.value = false
  }
}

// ==================== 统计图表 ====================
const statsDateRange = ref([])
const issueStats = ref([])

const loadIssueStats = async () => {
  if (!currentMerchantId.value) return
  try {
    const params = {}
    if (statsDateRange.value?.length === 2) {
      params.startDate = statsDateRange.value[0]
      params.endDate = statsDateRange.value[1]
    }
    const res = await getMerchantIssueStats(currentMerchantId.value, params)
    if (res.success) issueStats.value = res.data || []
  } catch (e) { console.error('加载差评归因失败', e) }
}

const sentimentChartRef = ref(null)
const tagChartRef = ref(null)
const issueChartRef = ref(null)
let sentimentChart = null, tagChart = null, issueChart = null

const renderSentimentChart = () => {
  if (!sentimentChartRef.value) return
  if (sentimentChart) sentimentChart.dispose()
  const s = { POSITIVE: 0, NEGATIVE: 0, NEUTRAL: 0, MIXED: 0 }
  reviews.value.forEach(r => {
    if (r.analysis?.sentiment) s[r.analysis.sentiment] = (s[r.analysis.sentiment] || 0) + 1
  })
  sentimentChart = echarts.init(sentimentChartRef.value)
  sentimentChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 条 ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie', radius: ['45%', '75%'], center: ['50%', '45%'],
      itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 3 },
      label: { show: true, formatter: '{b}\n{c} 条' },
      data: [
        { value: s.POSITIVE, name: '正面', itemStyle: { color: '#52c41a' } },
        { value: s.NEGATIVE, name: '负面', itemStyle: { color: '#ff4d4f' } },
        { value: s.NEUTRAL, name: '中性', itemStyle: { color: '#1890ff' } },
        { value: s.MIXED, name: '混合', itemStyle: { color: '#fa8c16' } },
      ].filter(d => d.value > 0),
    }],
  })
}

const renderTagChart = () => {
  if (!tagChartRef.value) return
  if (tagChart) tagChart.dispose()
  tagChart = echarts.init(tagChartRef.value)
  tagChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '10%', bottom: '3%', containLabel: true },
    xAxis: { type: 'value', name: '数量' },
    yAxis: {
      type: 'category',
      data: tagStats.value.map(t => t.tagName || t.tagCode),
      axisLabel: { fontSize: 11 },
    },
    series: [{
      type: 'bar',
      data: tagStats.value.map(t => t.totalCount || t.count || 0),
      itemStyle: {
        borderRadius: [0, 6, 6, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#52c41a' }, { offset: 1, color: '#73d13d' },
        ]),
      },
      label: { show: true, position: 'right' },
    }],
  })
}

const renderIssueChart = () => {
  if (!issueChartRef.value) return
  if (issueChart) issueChart.dispose()
  issueChart = echarts.init(issueChartRef.value)
  issueChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '8%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: issueStats.value.map(s => s.categoryName || s.category), axisLabel: { rotate: 30, fontSize: 11 } },
    yAxis: { type: 'value', name: '数量' },
    series: [{
      type: 'bar',
      data: issueStats.value.map(s => ({
        value: s.count || 0,
        itemStyle: {
          borderRadius: [8, 8, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#ff7a45' }, { offset: 1, color: '#ff4d4f' },
          ]),
        },
      })),
      label: { show: true, position: 'top' },
    }],
  })
}

watch(activeTab, async val => {
  if (val === 'stats') {
    await nextTick()
    setTimeout(() => { renderSentimentChart(); renderTagChart(); renderIssueChart() }, 200)
  }
})
watch(reviews, () => { if (activeTab.value === 'stats') nextTick(() => { renderSentimentChart(); renderTagChart() }) })
watch(issueStats, () => { if (activeTab.value === 'stats') nextTick(() => renderIssueChart()) })

// ==================== 工具函数 ====================
const truncateText = (t, n) => t ? (t.length > n ? t.slice(0, n) + '...' : t) : ''
const getSentimentText = s => ({ POSITIVE: '正面 👍', NEGATIVE: '负面 👎', NEUTRAL: '中性 😐', MIXED: '混合 🤔' })[s] || s || '-'
const getSentimentClass = s => ({ POSITIVE: 'sentiment-positive', NEGATIVE: 'sentiment-negative', NEUTRAL: 'sentiment-neutral', MIXED: 'sentiment-mixed' })[s] || ''
const getRowClass = ({ row }) => row.analysis ? 'row-' + (row.analysis.sentiment || '').toLowerCase() : ''
const getAspectName = c => ({ TASTE: '口味', ENVIRONMENT: '环境', SERVICE: '服务', PRICE: '价格', QUEUE_TIME: '排队', HYGIENE: '卫生', PORTION: '分量', SPEED: '上菜速度', PARKING: '停车' })[c] || c
const getTagSentimentType = s => s === 'POSITIVE' ? 'success' : s === 'NEGATIVE' ? 'danger' : 'info'

onMounted(() => {
  if (currentMerchantId.value) {
    loadReviews()
    loadTagStats()
    loadIssueStats()
  }
})
</script>

<style scoped>
/* 概览卡片 */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(20px);
  border-radius: 14px;
  border: 1px solid rgba(82,196,26,0.12);
  box-shadow: 0 4px 16px rgba(82,196,26,0.06), inset 0 1px 0 rgba(255,255,255,0.7);
}
.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  font-size: 24px;
}
.stat-icon-blue { background: linear-gradient(135deg, rgba(24,144,255,0.2), rgba(64,169,255,0.1)); }
.stat-icon-green { background: linear-gradient(135deg, rgba(82,196,26,0.2), rgba(115,209,61,0.1)); }
.stat-icon-orange { background: linear-gradient(135deg, rgba(255,103,0,0.2), rgba(255,149,64,0.1)); }
.stat-icon-purple { background: linear-gradient(135deg, rgba(114,46,209,0.2), rgba(146,84,222,0.1)); }
.stat-info { display: flex; flex-direction: column; }
.stat-value { font-size: 26px; font-weight: 700; color: #1f2d3d; }
.stat-label { font-size: 12px; color: #667085; }

/* 操作栏 */
.action-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}
.action-hint { font-size: 13px; color: #667085; }
.action-hint strong { color: #1890ff; }
.action-hint.done { color: #52c41a; }

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: rgba(255,255,255,0.8);
  border-radius: 12px;
  border: 1px solid rgba(82,196,26,0.08);
}
.filter-label { font-size: 13px; font-weight: 600; color: #667085; }
.filter-tag { cursor: pointer; transition: transform 0.2s; }
.filter-tag:hover { transform: scale(1.05); }
.tag-count { margin-left: 2px; opacity: 0.8; }

/* 表格 */
.review-table-container {
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(82,196,26,0.12);
  box-shadow: 0 4px 20px rgba(82,196,26,0.06);
  overflow: hidden;
}
.rating-stars { font-size: 12px; white-space: nowrap; }
.review-content-text { font-size: 13px; color: #555; line-height: 1.5; }

/* 情感标签 */
.sentiment-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
}
.sentiment-large { padding: 4px 14px; font-size: 14px; }
.sentiment-positive { color: #52c41a; background: rgba(82,196,26,0.12); }
.sentiment-negative { color: #ff4d4f; background: rgba(255,77,79,0.12); }
.sentiment-neutral { color: #1890ff; background: rgba(24,144,255,0.12); }
.sentiment-mixed { color: #fa8c16; background: rgba(250,140,22,0.12); }
.sentiment-none { color: #999; background: rgba(0,0,0,0.04); }
.confidence-ok { color: #52c41a; font-weight: 600; font-size: 13px; }
.confidence-low { color: #fa8c16; font-weight: 600; font-size: 13px; }
.confidence-na { color: #ccc; }

/* 详情弹窗 */
.analysis-detail { max-height: 65vh; overflow-y: auto; }
.detail-section { margin-bottom: 20px; }
.detail-section h4 { margin: 0 0 12px; font-size: 16px; font-weight: 700; color: #1f2d3d; }
.review-original { background: #f9fafb; border-radius: 12px; padding: 16px; border: 1px solid #eef0f4; }
.review-meta { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.review-user { font-weight: 600; color: #1f2d3d; }
.review-rating { font-size: 13px; }
.review-text-full { margin: 0; font-size: 14px; color: #444; line-height: 1.7; white-space: pre-wrap; }
.result-card {
  background: #f9fafb;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 12px;
  border: 1px solid #eef0f4;
}
.result-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.result-label { font-size: 13px; font-weight: 600; color: #667085; }
.confidence-text { font-size: 13px; color: #555; margin-left: 8px; }
.model-name { font-size: 12px; color: #999; font-family: monospace; }
.keyword-tag { margin: 2px 4px 2px 0; }

/* 方面 */
.aspect-list { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; }
.aspect-item { display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: #fff; border-radius: 8px; border: 1px solid #eef0f4; }
.aspect-category { font-size: 12px; font-weight: 600; color: #555; min-width: 50px; }
.aspect-sentiment { font-size: 11px; padding: 1px 8px; border-radius: 10px; font-weight: 600; }
.aspect-text { font-size: 12px; color: #888; font-style: italic; }

/* 标签 */
.tag-list, .issue-list { display: flex; flex-direction: column; gap: 6px; margin-top: 8px; }
.tag-item, .issue-item { display: flex; align-items: center; gap: 8px; }
.tag-evidence, .issue-evidence { font-size: 12px; color: #888; font-style: italic; }
.tag-confidence { font-size: 11px; color: #aaa; }

.no-analysis-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 32px;
  color: #999;
}
.no-analysis-tip p { margin: 0; }

/* 统计 */
.time-filter-bar { display: flex; align-items: center; gap: 8px; margin-bottom: 20px; }
.charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.chart-card {
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(82,196,26,0.12);
  box-shadow: 0 4px 20px rgba(82,196,26,0.06);
  padding: 20px;
}
.chart-card-full { grid-column: 1 / -1; }
.chart-title { margin: 0 0 12px; font-size: 16px; font-weight: 700; color: #1f2d3d; }
.chart-container { width: 100%; height: 320px; }
.chart-container-tall { height: 380px; }

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px;
  background: rgba(255,255,255,0.6);
  border-radius: 16px;
  border: 1px dashed rgba(82,196,26,0.2);
}
.empty-state p { margin: 0; font-size: 14px; color: #999; }

@media (max-width: 1200px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .charts-row { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .stats-grid { grid-template-columns: 1fr; }
}

:deep(.row-positive) { border-left: 3px solid #52c41a; }
:deep(.row-negative) { border-left: 3px solid #ff4d4f; }
</style>
