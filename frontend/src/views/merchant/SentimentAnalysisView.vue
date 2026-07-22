<template>
  <MerchantLayout title="评论情感分析" subtitle="AI 驱动的评论情感洞察，快速提取海量用户反馈的关键优缺点">
    <div class="sentiment-container">
      <!-- ========== 顶部操作栏 ========== -->
      <div class="toolbar">
        <div class="toolbar-left">
          <div class="store-selector">
            <label class="toolbar-label">店铺</label>
            <select v-model="selectedStoreId" class="toolbar-select" @change="loadAll">
              <option v-for="s in stores" :key="s.id" :value="s.id">{{ s.name }}</option>
            </select>
          </div>
          <div class="time-filter">
            <label class="toolbar-label">时间</label>
            <select v-model="timeRange" class="toolbar-select" @change="loadAll">
              <option value="7d">近 7 天</option>
              <option value="30d">近 30 天</option>
              <option value="90d">近 90 天</option>
              <option value="all">全部</option>
            </select>
          </div>
          <button class="btn-analyze" @click="triggerAnalysis" :disabled="analyzing">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
            <span>{{ analyzing ? '分析中...' : '一键分析' }}</span>
          </button>
        </div>
        <div class="toolbar-right">
          <span class="last-update" v-if="lastUpdateTime">
            最近更新：{{ lastUpdateTime }}
          </span>
          <button class="btn-refresh" @click="loadAll">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 消息提示 -->
      <div v-if="messageText" :class="['message-banner', messageType]">
        <span>{{ messageText }}</span>
        <button class="message-close" @click="messageText = ''">✕</button>
      </div>

      <!-- ========== 概览卡片 ========== -->
      <div class="summary-cards">
        <div class="summary-card">
          <div class="summary-icon total">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
              <polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/>
              <line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">已分析评价</p>
            <p class="summary-value">{{ summary.totalAnalyzed }}</p>
            <p class="summary-sub">共 {{ summary.totalReviews }} 条评价</p>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-icon positive">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><path d="M8 14s1.5 2 4 2 4-2 4-2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">好评率</p>
            <p class="summary-value">{{ summary.positiveRate }}%</p>
            <p :class="['summary-sub', summary.positiveTrend >= 0 ? 'trend-up' : 'trend-down']">
              {{ summary.positiveTrend >= 0 ? '↑' : '↓' }} {{ Math.abs(summary.positiveTrend) }}%
            </p>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-icon negative">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <circle cx="12" cy="12" r="10"/><path d="M8 15s1.5-2 4-2 4 2 4 2"/><line x1="9" y1="9" x2="9.01" y2="9"/><line x1="15" y1="9" x2="15.01" y2="9"/>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">差评率</p>
            <p class="summary-value">{{ summary.negativeRate }}%</p>
            <p :class="['summary-sub', summary.negativeTrend <= 0 ? 'trend-up' : 'trend-down']">
              {{ summary.negativeTrend <= 0 ? '↓' : '↑' }} {{ Math.abs(summary.negativeTrend) }}%
            </p>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-icon complaint">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" stroke-width="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
              <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
            </svg>
          </div>
          <div class="summary-info">
            <p class="summary-label">主要差评维度</p>
            <p class="summary-value" style="font-size:18px">{{ summary.topComplaintDimension }}</p>
            <p class="summary-sub">{{ summary.topComplaintCount }} 条提及</p>
          </div>
        </div>
      </div>

      <!-- ========== 图表区域 ========== -->
      <div class="charts-row">
        <!-- 整体情感分布 -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>整体情感分布</h3>
          </div>
          <div class="chart-body">
            <div class="donut-chart-wrapper">
              <svg viewBox="0 0 200 200" class="donut-chart">
                <circle cx="100" cy="100" r="70" fill="none" stroke="#f0f0f0" stroke-width="24"/>
                <circle
                  v-for="(seg, i) in sentimentDonutSegments"
                  :key="i"
                  cx="100" cy="100" r="70" fill="none"
                  :stroke="seg.color" stroke-width="24"
                  :stroke-dasharray="seg.dashArray"
                  :stroke-dashoffset="seg.dashOffset"
                  transform="rotate(-90 100 100)"
                  class="donut-segment"
                />
                <text x="100" y="95" text-anchor="middle" class="donut-center-value">{{ summary.totalAnalyzed }}</text>
                <text x="100" y="115" text-anchor="middle" class="donut-center-label">总分析数</text>
              </svg>
              <div class="donut-legend">
                <div v-for="item in sentimentDist" :key="item.label" class="legend-row">
                  <span class="legend-dot" :style="{background: item.color}"></span>
                  <span class="legend-label">{{ item.label }}</span>
                  <span class="legend-value">{{ item.count }}</span>
                  <span class="legend-pct">{{ item.percentage }}%</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 维度情感雷达 -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>各维度情感分析</h3>
            <div class="chart-tabs">
              <button
                v-for="d in dimensionTabs"
                :key="d.key"
                :class="['dim-tab', { active: activeDimension === d.key }]"
                @click="activeDimension = d.key"
              >{{ d.label }}</button>
            </div>
          </div>
          <div class="chart-body">
            <div class="dimension-bars">
              <div v-for="dim in dimensionData" :key="dim.key" class="dimension-item">
                <div class="dim-header">
                  <span class="dim-name">{{ dim.label }}</span>
                  <span class="dim-score">{{ dim.coverage }}% 提及率</span>
                </div>
                <div class="dim-bar-track">
                  <div class="dim-bar-positive" :style="{width: dim.positivePct + '%'}">
                    <span v-if="dim.positivePct > 15">{{ dim.positivePct }}%</span>
                  </div>
                  <div class="dim-bar-neutral" :style="{width: dim.neutralPct + '%', left: dim.positivePct + '%'}">
                    <span v-if="dim.neutralPct > 15">{{ dim.neutralPct }}%</span>
                  </div>
                  <div class="dim-bar-negative" :style="{width: dim.negativePct + '%', left: (dim.positivePct + dim.neutralPct) + '%'}">
                    <span v-if="dim.negativePct > 15">{{ dim.negativePct }}%</span>
                  </div>
                </div>
                <div class="dim-legend-row">
                  <span class="dim-legend-pos">👍 {{ dim.positiveCount }}</span>
                  <span class="dim-legend-neg">👎 {{ dim.negativeCount }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 关键洞察 ========== -->
      <div class="charts-row">
        <!-- 好评关键词云 -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>🏆 好评关键词 TOP10</h3>
          </div>
          <div class="chart-body">
            <div class="keyword-cloud">
              <span
                v-for="kw in positiveKeywords"
                :key="kw.word"
                class="keyword-tag positive"
                :style="{fontSize: (14 + kw.count * 2.5) + 'px', opacity: 0.5 + kw.count / (maxPositiveKwCount * 2)}"
              >{{ kw.word }}<small>{{ kw.count }}</small></span>
            </div>
          </div>
        </div>

        <!-- 差评问题归类 -->
        <div class="chart-card">
          <div class="chart-header">
            <h3>⚠️ 差评问题归类</h3>
          </div>
          <div class="chart-body">
            <div class="issue-list">
              <div v-for="(issue, idx) in complaintIssues" :key="issue.category" class="issue-row">
                <span class="issue-rank" :style="{background: rankColor(idx)}">{{ idx + 1 }}</span>
                <div class="issue-info">
                  <span class="issue-name">{{ issue.categoryName }}</span>
                  <div class="issue-bar-track">
                    <div class="issue-bar-fill" :style="{width: issue.percentage + '%', background: rankColor(idx)}"></div>
                  </div>
                </div>
                <span class="issue-count">{{ issue.count }}条</span>
                <span class="issue-pct">{{ issue.percentage }}%</span>
              </div>
              <div v-if="complaintIssues.length === 0" class="empty-hint">暂无差评数据</div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== AI 口碑摘要 ========== -->
      <div class="ai-summary-card" v-if="aiSummary">
        <div class="ai-summary-header">
          <h3>
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"/>
            </svg>
            AI 口碑总结
          </h3>
          <span class="ai-badge">AI 生成</span>
        </div>
        <div class="ai-summary-body">
          <div class="ai-summary-section">
            <h4>✅ 主要优点</h4>
            <ul>
              <li v-for="(adv, i) in aiSummary.advantages" :key="i">
                <strong>{{ adv.name }}</strong>（{{ adv.mentionCount }} 条评价提及）
              </li>
            </ul>
          </div>
          <div class="ai-summary-section">
            <h4>❌ 主要不足</h4>
            <ul>
              <li v-for="(dis, i) in aiSummary.disadvantages" :key="i">
                <strong>{{ dis.name }}</strong>（{{ dis.mentionCount }} 条评价提及）
              </li>
            </ul>
          </div>
          <div class="ai-summary-section" v-if="aiSummary.recommendedDishes && aiSummary.recommendedDishes.length">
            <h4>🍽️ 推荐菜品</h4>
            <div class="recommend-tags">
              <span v-for="(d, i) in aiSummary.recommendedDishes" :key="i" class="recommend-tag">
                {{ d.name }} <small>({{ d.mentionCount }})</small>
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 评论明细列表 ========== -->
      <div class="reviews-card">
        <div class="chart-header">
          <h3>评论分析明细</h3>
          <div class="list-filters">
            <select v-model="filterSentiment" class="filter-select" @change="loadReviews">
              <option value="">全部情感</option>
              <option value="POSITIVE">正面</option>
              <option value="NEGATIVE">负面</option>
              <option value="NEUTRAL">中性</option>
              <option value="MIXED">混合</option>
            </select>
            <select v-model="filterDimension" class="filter-select" @change="loadReviews">
              <option value="">全部维度</option>
              <option value="SERVICE">服务</option>
              <option value="TASTE">口味</option>
              <option value="PRICE">价格</option>
              <option value="ENVIRONMENT">环境</option>
            </select>
            <div class="search-box">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="#999" stroke-width="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input v-model="searchKeyword" placeholder="搜索关键词..." class="filter-search" @input="onSearchInput" />
            </div>
          </div>
        </div>
        <div class="reviews-table-wrapper">
          <table class="reviews-table">
            <thead>
              <tr>
                <th style="width:8%">评分</th>
                <th style="width:32%">评价内容</th>
                <th style="width:10%">整体情感</th>
                <th style="width:10%">置信度</th>
                <th style="width:22%">维度分析</th>
                <th style="width:10%">关键词</th>
                <th style="width:8%">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="rv in reviewList" :key="rv.reviewId" @click="openDetail(rv)">
                <td>
                  <span :class="['rating-badge', 'r' + rv.rating]">{{ rv.rating }}分</span>
                </td>
                <td class="content-cell" :title="rv.content">{{ truncate(rv.content, 60) }}</td>
                <td>
                  <span :class="['sentiment-tag', rv.sentiment.toLowerCase()]">{{ sentimentLabel(rv.sentiment) }}</span>
                </td>
                <td>
                  <div class="confidence-bar" :title="'置信度: ' + (rv.confidence * 100).toFixed(0) + '%'">
                    <div class="confidence-fill" :style="{width: rv.confidence * 100 + '%', background: confidenceColor(rv.confidence)}"></div>
                  </div>
                </td>
                <td>
                  <div class="aspect-mini-tags">
                    <span
                      v-for="asp in rv.aspects"
                      :key="asp.category"
                      :class="['aspect-mini-tag', asp.sentiment.toLowerCase()]"
                      :title="asp.text"
                    >{{ aspectLabel(asp.category) }}</span>
                  </div>
                </td>
                <td>
                  <div class="keyword-mini">
                    <span v-for="kw in rv.keywords.slice(0, 3)" :key="kw" class="kw-tag">{{ kw }}</span>
                  </div>
                </td>
                <td>
                  <button class="btn-detail" @click.stop="openDetail(rv)">详情</button>
                </td>
              </tr>
              <tr v-if="reviewList.length === 0">
                <td colspan="7" class="empty-cell">暂无评价数据</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="pagination" v-if="totalPages > 1">
          <button :disabled="page <= 1" @click="page--; loadReviews()">上一页</button>
          <span v-for="p in visiblePages" :key="p"
                :class="['page-btn', { active: p === page }]"
                @click="page = p; loadReviews()">{{ p }}</span>
          <button :disabled="page >= totalPages" @click="page++; loadReviews()">下一页</button>
        </div>
      </div>

      <!-- ========== 评价详情弹窗 ========== -->
      <div v-if="detailVisible" class="modal-overlay" @click.self="detailVisible = false">
        <div class="modal-panel">
          <div class="modal-header">
            <h3>评价情感分析详情</h3>
            <button class="modal-close" @click="detailVisible = false">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="modal-body" v-if="currentDetail">
            <div class="detail-original">
              <div class="detail-label">📝 评价原文</div>
              <p>{{ currentDetail.content }}</p>
              <div class="detail-meta">
                <span>评分：{{ currentDetail.rating }}分</span>
                <span>时间：{{ currentDetail.reviewTime || '-' }}</span>
              </div>
            </div>
            <div class="detail-analysis">
              <div class="detail-label">🔍 AI 分析结果</div>
              <div class="detail-sentiment-row">
                <span>整体情感：</span>
                <span :class="['sentiment-tag', currentDetail.sentiment.toLowerCase()]">{{ sentimentLabel(currentDetail.sentiment) }}</span>
                <span>置信度：{{ (currentDetail.confidence * 100).toFixed(0) }}%</span>
              </div>
              <div class="detail-aspects">
                <span class="detail-label-sm">维度详情：</span>
                <div class="aspect-cards">
                  <div v-for="asp in currentDetail.aspects" :key="asp.category" :class="['aspect-card', asp.sentiment.toLowerCase()]">
                    <span class="aspect-card-label">{{ aspectLabel(asp.category) }}</span>
                    <span :class="['aspect-card-sentiment', asp.sentiment.toLowerCase()]">{{ sentimentLabel(asp.sentiment) }}</span>
                    <span class="aspect-card-text">{{ asp.text }}</span>
                  </div>
                </div>
              </div>
              <div class="detail-keywords" v-if="currentDetail.keywords && currentDetail.keywords.length">
                <span class="detail-label-sm">关键词：</span>
                <span v-for="kw in currentDetail.keywords" :key="kw" class="kw-tag">{{ kw }}</span>
              </div>
              <div class="detail-issues" v-if="currentDetail.issueCategories && currentDetail.issueCategories.length">
                <span class="detail-label-sm">⚠️ 问题归因：</span>
                <div v-for="iss in currentDetail.issueCategories" :key="iss.category" class="issue-item">
                  <span class="issue-tag">{{ iss.categoryName }}</span>
                  <span class="issue-conf">置信度 {{ (iss.confidence * 100).toFixed(0) }}%</span>
                  <span class="issue-evidence" v-if="iss.evidenceText">"{{ iss.evidenceText }}"</span>
                </div>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-secondary" @click="detailVisible = false">关闭</button>
          </div>
        </div>
      </div>
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import MerchantLayout from '../../components/MerchantLayout.vue'
import { getSentimentSummary, getSentimentReviews, triggerBatchAnalysis } from '../../api/sentiment'
import { getMyMerchants } from '../../api/merchantConsole'

// ========== 状态 ==========
const stores = ref([{ id: 0, name: '全部店铺' }])
const selectedStoreId = ref(0)
const timeRange = ref('all')
const analyzing = ref(false)
const lastUpdateTime = ref('')

// 汇总
const summary = ref({
  totalReviews: 0, totalAnalyzed: 0,
  positiveRate: 0, negativeRate: 0,
  positiveTrend: 0, negativeTrend: 0,
  topComplaintDimension: '-', topComplaintCount: 0,
})

// 情感分布
const sentimentDist = ref([
  { label: '正面', key: 'POSITIVE', count: 0, percentage: 0, color: '#52c41a' },
  { label: '负面', key: 'NEGATIVE', count: 0, percentage: 0, color: '#ff4d4f' },
  { label: '中性', key: 'NEUTRAL', count: 0, percentage: 0, color: '#1890ff' },
  { label: '混合', key: 'MIXED', count: 0, percentage: 0, color: '#faad14' },
])

// 维度
const dimensionTabs = [
  { key: 'all', label: '全部' },
  { key: 'SERVICE', label: '服务' },
  { key: 'TASTE', label: '口味' },
  { key: 'PRICE', label: '价格' },
  { key: 'ENVIRONMENT', label: '环境' },
]
const activeDimension = ref('all')
const dimensionData = ref([
  { key: 'SERVICE', label: '服务', positivePct: 0, neutralPct: 0, negativePct: 0, positiveCount: 0, negativeCount: 0, coverage: 0 },
  { key: 'TASTE', label: '口味', positivePct: 0, neutralPct: 0, negativePct: 0, positiveCount: 0, negativeCount: 0, coverage: 0 },
  { key: 'PRICE', label: '价格', positivePct: 0, neutralPct: 0, negativePct: 0, positiveCount: 0, negativeCount: 0, coverage: 0 },
  { key: 'ENVIRONMENT', label: '环境', positivePct: 0, neutralPct: 0, negativePct: 0, positiveCount: 0, negativeCount: 0, coverage: 0 },
])

// 关键词
const positiveKeywords = ref([])
const maxPositiveKwCount = ref(1)
const complaintIssues = ref([])

// AI 摘要
const aiSummary = ref(null)

// 评价列表
const reviewList = ref([])
const filterSentiment = ref('')
const filterDimension = ref('')
const searchKeyword = ref('')
const page = ref(1)
const pageSize = ref(15)
const totalPages = ref(1)
const totalCount = ref(0)

// 详情弹窗
const detailVisible = ref(false)
const currentDetail = ref(null)

// ========== 计算属性 ==========
const sentimentDonutSegments = computed(() => {
  let offset = 0
  const total = 2 * Math.PI * 70
  return sentimentDist.value.map(item => {
    const length = (item.percentage / 100) * total
    const seg = {
      color: item.color,
      dashArray: `${length} ${total - length}`,
      dashOffset: -offset,
    }
    offset += length
    return seg
  })
})

const visiblePages = computed(() => {
  const pages = []
  for (let p = Math.max(1, page.value - 2); p <= Math.min(totalPages.value, page.value + 2); p++) {
    pages.push(p)
  }
  return pages
})

// ========== 方法 ==========
function sentimentLabel(s) {
  const map = { POSITIVE: '👍 正面', NEGATIVE: '👎 负面', NEUTRAL: '➖ 中性', MIXED: '🔄 混合' }
  return map[s] || s
}

function aspectLabel(c) {
  const map = { TASTE: '口味', ENVIRONMENT: '环境', SERVICE: '服务', PRICE: '价格', SPEED: '速度', PORTION: '分量', HYGIENE: '卫生', QUEUE_TIME: '排队', PARKING: '停车' }
  return map[c] || c
}

function confidenceColor(v) {
  if (v >= 0.8) return '#52c41a'
  if (v >= 0.6) return '#faad14'
  return '#ff4d4f'
}

function rankColor(idx) {
  const colors = ['#ff4d4f', '#ff7a45', '#faad14', '#ffc53d', '#ffd666']
  return colors[idx] || '#bbb'
}

function truncate(text, len) {
  if (!text) return ''
  return text.length > len ? text.slice(0, len) + '...' : text
}

async function loadAll() {
  await Promise.all([loadSummary(), loadReviews()])
}

// 消息提示
const messageText = ref('')
const messageType = ref('info') // 'info' | 'error' | 'success'
let messageTimer = null

function showMessage(text, type = 'info') {
  messageText.value = text
  messageType.value = type
  clearTimeout(messageTimer)
  messageTimer = setTimeout(() => { messageText.value = '' }, 5000)
}

async function loadSummary() {
  try {
    const res = await getSentimentSummary({
      merchantId: selectedStoreId.value,
      timeRange: timeRange.value,
    })
    if (res.success && res.data) {
      const d = res.data
      summary.value = {
        totalReviews: d.totalReviews || 0,
        totalAnalyzed: d.totalAnalyzed || 0,
        positiveRate: d.positiveRate || 0,
        negativeRate: d.negativeRate || 0,
        positiveTrend: d.positiveTrend || 0,
        negativeTrend: d.negativeTrend || 0,
        topComplaintDimension: d.topComplaintDimension || '-',
        topComplaintCount: d.topComplaintCount || 0,
      }
      // 情感分布
      if (d.sentimentDistribution) {
        for (const item of sentimentDist.value) {
          const match = d.sentimentDistribution[item.key]
          item.count = match?.count || 0
          item.percentage = match?.percentage || 0
        }
      }
      // 维度数据
      if (d.dimensions) {
        for (const dim of dimensionData.value) {
          const match = d.dimensions[dim.key]
          if (match) {
            dim.positivePct = match.positivePct || 0
            dim.neutralPct = match.neutralPct || 0
            dim.negativePct = match.negativePct || 0
            dim.positiveCount = match.positiveCount || 0
            dim.negativeCount = match.negativeCount || 0
            dim.coverage = match.coverage || 0
          }
        }
      }
      // 关键词
      if (d.positiveKeywords) {
        positiveKeywords.value = d.positiveKeywords
        maxPositiveKwCount.value = Math.max(1, ...d.positiveKeywords.map(k => k.count))
      }
      // 差评问题
      if (d.complaintIssues) {
        complaintIssues.value = d.complaintIssues
      }
      // AI 摘要
      if (d.aiSummary) {
        aiSummary.value = d.aiSummary
      }
      lastUpdateTime.value = d.updateTime || new Date().toLocaleString('zh-CN')

      // 提示数据分析状态
      if (d.totalAnalyzed === 0 && d.totalReviews > 0) {
        showMessage(`共有 ${d.totalReviews} 条评价待分析，请点击"一键分析"按钮开始 AI 情感分析`, 'info')
      }
    }
  } catch (e) {
    console.error('加载汇总数据失败', e)
    showMessage('加载汇总数据失败，请确认后端服务正常运行', 'error')
  }
}

async function loadReviews() {
  try {
    const res = await getSentimentReviews({
      merchantId: selectedStoreId.value,
      timeRange: timeRange.value,
      sentiment: filterSentiment.value,
      dimension: filterDimension.value,
      keyword: searchKeyword.value,
      page: page.value,
      pageSize: pageSize.value,
    })
    if (res.success && res.data) {
      reviewList.value = res.data.records || []
      totalPages.value = res.data.totalPages || 1
      totalCount.value = res.data.totalCount || 0
    }
  } catch (e) {
    console.error('加载评论列表失败', e)
  }
}

async function triggerAnalysis() {
  if (analyzing.value) return
  analyzing.value = true
  showMessage('', '') // 清除旧消息
  try {
    const res = await triggerBatchAnalysis({
      merchantId: selectedStoreId.value,
      timeRange: timeRange.value,
    })
    if (res.success) {
      const d = res.data
      const msg = d.message
        || (d.analyzedCount > 0
          ? `分析完成！成功分析 ${d.analyzedCount} 条评价`
          : '所有评价已分析完成')
      showMessage(msg, 'success')
      lastUpdateTime.value = new Date().toLocaleString('zh-CN')
      await loadAll()
    } else {
      showMessage(res.message || '批量分析请求失败，请确认 AI 服务已启动', 'error')
    }
  } catch (e) {
    console.error('批量分析失败', e)
    showMessage('批量分析失败：无法连接到 AI 服务，请确认 ai-service 已启动且 internal-token 配置正确', 'error')
  } finally {
    analyzing.value = false
  }
}

function openDetail(rv) {
  currentDetail.value = rv
  detailVisible.value = true
}

let searchTimer = null
function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    loadReviews()
  }, 400)
}

async function loadStores() {
  try {
    const res = await getMyMerchants()
    if (res.success && res.data) {
      stores.value = [{ id: 0, name: '全部店铺' }, ...res.data]
    }
  } catch (e) { /* use default */ }
}

onMounted(async () => {
  await loadStores()
  // 默认选中第一个真实店铺
  const realStores = stores.value.filter(s => s.id !== 0)
  if (realStores.length > 0 && selectedStoreId.value === 0) {
    selectedStoreId.value = realStores[0].id
  }
  loadAll()
})
</script>

<style scoped>
.sentiment-container { width: 100%; }

/* 消息提示 */
.message-banner {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 20px; border-radius: 8px; margin-bottom: 16px;
  font-size: 14px; font-weight: 500;
}
.message-banner.info { background: #e6f7ff; color: #0958d9; border: 1px solid #91d5ff; }
.message-banner.error { background: #fff2f0; color: #cf1322; border: 1px solid #ffccc7; }
.message-banner.success { background: #f6ffed; color: #389e0d; border: 1px solid #b7eb8f; }
.message-close { padding: 4px 8px; background: none; border: none; cursor: pointer; font-size: 14px; opacity: 0.6; }
.message-close:hover { opacity: 1; }

/* ===== 操作栏 ===== */
.toolbar {
  display: flex; justify-content: space-between; align-items: center;
  background: #fff; border-radius: 12px; padding: 16px 24px;
  margin-bottom: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  flex-wrap: wrap; gap: 12px;
}
.toolbar-left { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.toolbar-right { display: flex; align-items: center; gap: 12px; }
.toolbar-label { font-size: 13px; color: #999; margin-right: 6px; }
.toolbar-select {
  padding: 8px 12px; border: 1px solid #d9d9d9; border-radius: 6px;
  font-size: 14px; color: #1f2d3d; background: #fff; cursor: pointer; outline: none;
}
.toolbar-select:focus { border-color: #52c41a; }
.btn-analyze {
  display: flex; align-items: center; gap: 6px;
  padding: 9px 20px; font-size: 14px; font-weight: 500; color: #fff;
  background: linear-gradient(135deg, #ff7a45 0%, #ff4d4f 100%);
  border: none; border-radius: 8px; cursor: pointer; transition: all 0.2s;
}
.btn-analyze:hover:not(:disabled) { opacity: 0.9; transform: translateY(-1px); }
.btn-analyze:disabled { opacity: 0.6; cursor: not-allowed; }
.last-update { font-size: 13px; color: #999; }
.btn-refresh {
  padding: 8px; background: #f5f7fa; border: 1px solid #d9d9d9;
  border-radius: 6px; color: #667085; cursor: pointer; transition: all 0.2s;
}
.btn-refresh:hover { background: #eef2f7; }

/* ===== 概览卡片 ===== */
.summary-cards {
  display: grid; grid-template-columns: repeat(4, 1fr);
  gap: 20px; margin-bottom: 24px;
}
.summary-card {
  background: #fff; border-radius: 12px; padding: 20px 24px;
  display: flex; align-items: center; gap: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.summary-icon {
  width: 52px; height: 52px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.summary-icon.total { background: linear-gradient(135deg, #1890ff, #40a9ff); }
.summary-icon.positive { background: linear-gradient(135deg, #52c41a, #73d13d); }
.summary-icon.negative { background: linear-gradient(135deg, #ff4d4f, #ff7875); }
.summary-icon.complaint { background: linear-gradient(135deg, #faad14, #ffc53d); }
.summary-info { flex: 1; min-width: 0; }
.summary-label { font-size: 13px; color: #999; margin: 0; }
.summary-value { font-size: 26px; font-weight: 700; color: #1f2d3d; margin: 2px 0 0; }
.summary-sub { font-size: 12px; color: #999; margin: 2px 0 0; }
.summary-sub.trend-up { color: #52c41a; }
.summary-sub.trend-down { color: #ff4d4f; }

/* ===== 图表行 ===== */
.charts-row {
  display: grid; grid-template-columns: repeat(2, 1fr);
  gap: 24px; margin-bottom: 24px;
}
.chart-card {
  background: #fff; border-radius: 12px; padding: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.chart-header {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;
}
.chart-header h3 { font-size: 15px; font-weight: 600; color: #1f2d3d; margin: 0; }
.chart-body { }

/* 情感分布甜甜圈 */
.donut-chart-wrapper { display: flex; align-items: center; gap: 24px; }
.donut-chart { width: 180px; height: 180px; flex-shrink: 0; }
.donut-segment { transition: all 0.3s; }
.donut-center-value { font-size: 22px; font-weight: 700; fill: #1f2d3d; }
.donut-center-label { font-size: 12px; fill: #999; }
.donut-legend { flex: 1; }
.legend-row {
  display: flex; align-items: center; gap: 8px; padding: 6px 0;
  font-size: 13px;
}
.legend-dot { width: 10px; height: 10px; border-radius: 3px; flex-shrink: 0; }
.legend-label { color: #667085; min-width: 36px; }
.legend-value { color: #1f2d3d; font-weight: 600; min-width: 30px; text-align: right; }
.legend-pct { color: #999; }

/* 维度标签切换 */
.chart-tabs { display: flex; gap: 6px; }
.dim-tab {
  padding: 4px 12px; font-size: 12px; color: #667085;
  background: #f5f7fa; border: none; border-radius: 4px; cursor: pointer; transition: all 0.2s;
}
.dim-tab.active { background: #52c41a; color: #fff; }
.dim-tab:hover:not(.active) { background: #eef2f7; }

/* 维度条形图 */
.dimension-bars { display: flex; flex-direction: column; gap: 16px; }
.dimension-item { }
.dim-header { display: flex; justify-content: space-between; margin-bottom: 6px; }
.dim-name { font-size: 14px; font-weight: 500; color: #1f2d3d; }
.dim-score { font-size: 12px; color: #999; }
.dim-bar-track {
  position: relative; height: 28px; background: #f5f5f5;
  border-radius: 14px; overflow: hidden; display: flex;
}
.dim-bar-positive {
  height: 100%; background: linear-gradient(90deg, #52c41a, #73d13d);
  border-radius: 14px 0 0 14px; display: flex; align-items: center;
  justify-content: center; font-size: 12px; color: #fff; font-weight: 600;
  transition: width 0.4s; position: absolute; left: 0; top: 0;
}
.dim-bar-neutral {
  height: 100%; background: linear-gradient(90deg, #1890ff, #40a9ff);
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; color: #fff; font-weight: 600;
  transition: width 0.4s; position: absolute; top: 0;
}
.dim-bar-negative {
  height: 100%; background: linear-gradient(90deg, #ff7875, #ff4d4f);
  border-radius: 0 14px 14px 0; display: flex; align-items: center;
  justify-content: center; font-size: 12px; color: #fff; font-weight: 600;
  transition: width 0.4s; position: absolute; top: 0;
}
.dim-legend-row { display: flex; justify-content: space-between; margin-top: 4px; }
.dim-legend-pos { font-size: 12px; color: #52c41a; }
.dim-legend-neg { font-size: 12px; color: #ff4d4f; }

/* 关键词云 */
.keyword-cloud {
  display: flex; flex-wrap: wrap; gap: 10px; align-items: center;
  justify-content: center; padding: 12px 0; min-height: 120px;
}
.keyword-tag.positive {
  display: inline-flex; align-items: baseline; gap: 2px;
  color: #52c41a; font-weight: 600; cursor: default;
  transition: transform 0.2s;
}
.keyword-tag.positive:hover { transform: scale(1.15); }
.keyword-tag.positive small { font-size: 11px; opacity: 0.7; }

/* 差评问题归类 */
.issue-list { display: flex; flex-direction: column; gap: 10px; padding: 4px 0; }
.issue-row { display: flex; align-items: center; gap: 10px; }
.issue-rank {
  width: 24px; height: 24px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700; color: #fff; flex-shrink: 0;
}
.issue-info { flex: 1; min-width: 0; }
.issue-name { font-size: 13px; color: #1f2d3d; display: block; margin-bottom: 3px; }
.issue-bar-track { height: 8px; background: #f0f0f0; border-radius: 4px; overflow: hidden; }
.issue-bar-fill { height: 100%; border-radius: 4px; transition: width 0.4s; }
.issue-count { font-size: 13px; color: #667085; min-width: 36px; text-align: right; }
.issue-pct { font-size: 13px; font-weight: 600; color: #1f2d3d; min-width: 36px; text-align: right; }
.empty-hint { text-align: center; color: #ccc; padding: 24px 0; font-size: 14px; }

/* AI 口碑摘要 */
.ai-summary-card {
  background: linear-gradient(135deg, #fffbe6 0%, #fff7e6 50%, #f6ffed 100%);
  border: 1px solid #ffe58f; border-radius: 12px; padding: 20px 24px;
  margin-bottom: 24px;
}
.ai-summary-header {
  display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
}
.ai-summary-header h3 {
  font-size: 16px; font-weight: 600; color: #d48806; margin: 0;
  display: flex; align-items: center; gap: 6px;
}
.ai-badge {
  font-size: 11px; color: #d48806; background: #fffbe6;
  border: 1px solid #ffe58f; padding: 2px 10px; border-radius: 12px;
}
.ai-summary-body { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 20px; }
.ai-summary-section h4 { font-size: 14px; font-weight: 600; color: #1f2d3d; margin: 0 0 8px; }
.ai-summary-section ul { margin: 0; padding-left: 20px; }
.ai-summary-section li { font-size: 13px; color: #667085; margin-bottom: 4px; line-height: 1.6; }
.ai-summary-section li strong { color: #1f2d3d; }
.recommend-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.recommend-tag {
  padding: 6px 14px; background: #fff; border: 1px solid #ffe58f;
  border-radius: 20px; font-size: 13px; color: #d48806; font-weight: 500;
}
.recommend-tag small { opacity: 0.7; font-weight: 400; }

/* 评论列表 */
.reviews-card {
  background: #fff; border-radius: 12px; padding: 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.list-filters { display: flex; align-items: center; gap: 10px; }
.filter-select {
  padding: 6px 12px; border: 1px solid #d9d9d9; border-radius: 6px;
  font-size: 13px; color: #1f2d3d; background: #fff; cursor: pointer; outline: none;
}
.filter-select:focus { border-color: #52c41a; }
.search-box {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 10px; border: 1px solid #d9d9d9; border-radius: 6px;
  background: #fff;
}
.search-box:focus-within { border-color: #52c41a; }
.filter-search {
  border: none; outline: none; font-size: 13px; color: #1f2d3d; width: 140px;
}
.filter-search::placeholder { color: #ccc; }

.reviews-table-wrapper { overflow-x: auto; margin-top: 12px; }
.reviews-table { width: 100%; border-collapse: collapse; }
.reviews-table th {
  padding: 12px 14px; text-align: left; font-size: 13px;
  color: #999; font-weight: 600; background: #fafafa; border-bottom: 2px solid #f0f0f0;
  white-space: nowrap;
}
.reviews-table td {
  padding: 14px; font-size: 13px; color: #1f2d3d;
  border-bottom: 1px solid #f0f0f0; vertical-align: middle;
}
.reviews-table tbody tr { cursor: pointer; transition: background 0.15s; }
.reviews-table tbody tr:hover td { background: #fafafa; }
.content-cell { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty-cell { text-align: center; color: #ccc; padding: 40px 14px; cursor: default; }

/* 评分标签 */
.rating-badge {
  display: inline-block; padding: 3px 10px; border-radius: 4px;
  font-size: 12px; font-weight: 600; color: #fff;
}
.rating-badge.r1,.rating-badge.r2 { background: #bbb; }
.rating-badge.r3 { background: #faad14; }
.rating-badge.r4,.rating-badge.r5 { background: #52c41a; }

/* 情感标签 */
.sentiment-tag {
  display: inline-block; padding: 3px 10px; border-radius: 4px;
  font-size: 12px; font-weight: 600;
}
.sentiment-tag.positive { color: #52c41a; background: #f6ffed; border: 1px solid #b7eb8f; }
.sentiment-tag.negative { color: #ff4d4f; background: #fff2f0; border: 1px solid #ffccc7; }
.sentiment-tag.neutral { color: #1890ff; background: #e6f7ff; border: 1px solid #91d5ff; }
.sentiment-tag.mixed { color: #faad14; background: #fffbe6; border: 1px solid #ffe58f; }

/* 置信度条 */
.confidence-bar { width: 60px; height: 6px; background: #f0f0f0; border-radius: 3px; overflow: hidden; }
.confidence-fill { height: 100%; border-radius: 3px; transition: width 0.3s; }

/* 维度小标签 */
.aspect-mini-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.aspect-mini-tag {
  display: inline-block; padding: 2px 8px; border-radius: 3px;
  font-size: 11px; font-weight: 500;
}
.aspect-mini-tag.positive { color: #52c41a; background: #f6ffed; }
.aspect-mini-tag.negative { color: #ff4d4f; background: #fff2f0; }
.aspect-mini-tag.neutral { color: #999; background: #f5f5f5; }

/* 关键词小标签 */
.keyword-mini { display: flex; gap: 4px; flex-wrap: wrap; }
.kw-tag {
  display: inline-block; padding: 2px 8px; background: #f5f7fa;
  border-radius: 3px; font-size: 11px; color: #667085;
}

.btn-detail {
  padding: 4px 12px; font-size: 12px; color: #1890ff; background: #e6f7ff;
  border: 1px solid #91d5ff; border-radius: 4px; cursor: pointer; transition: all 0.2s;
}
.btn-detail:hover { background: #bae7ff; }

/* 分页 */
.pagination {
  display: flex; justify-content: center; align-items: center; gap: 6px;
  margin-top: 20px; padding-top: 16px; border-top: 1px solid #f0f0f0;
}
.pagination button {
  padding: 6px 14px; border: 1px solid #d9d9d9; border-radius: 6px;
  background: #fff; font-size: 13px; color: #1f2d3d; cursor: pointer; transition: all 0.2s;
}
.pagination button:hover:not(:disabled) { border-color: #52c41a; color: #52c41a; }
.pagination button:disabled { opacity: 0.4; cursor: not-allowed; }
.page-btn {
  width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
  font-size: 13px; color: #1f2d3d; border: 1px solid #d9d9d9; border-radius: 6px;
  cursor: pointer; transition: all 0.2s;
}
.page-btn:hover { border-color: #52c41a; color: #52c41a; }
.page-btn.active { background: #52c41a; color: #fff; border-color: #52c41a; }

/* ===== 详情弹窗 ===== */
.modal-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5); display: flex; align-items: center;
  justify-content: center; z-index: 1000; padding: 32px;
}
.modal-panel {
  background: #fff; border-radius: 16px; width: 100%; max-width: 700px;
  max-height: 85vh; overflow-y: auto; box-shadow: 0 16px 48px rgba(0,0,0,0.15);
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 20px 28px; border-bottom: 1px solid #f0f0f0;
}
.modal-header h3 { font-size: 17px; font-weight: 600; color: #1f2d3d; margin: 0; }
.modal-close {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  border: none; background: #f5f5f5; border-radius: 50%; cursor: pointer;
  color: #999; transition: all 0.2s;
}
.modal-close:hover { background: #eee; color: #333; }
.modal-body { padding: 24px 28px; }
.modal-footer { padding: 16px 28px; border-top: 1px solid #f0f0f0; display: flex; justify-content: flex-end; }

.detail-original { margin-bottom: 20px; }
.detail-original p {
  font-size: 14px; color: #1f2d3d; line-height: 1.8; margin: 8px 0;
  padding: 12px 16px; background: #fafafa; border-radius: 8px; border-left: 3px solid #1890ff;
}
.detail-meta { display: flex; gap: 20px; font-size: 13px; color: #999; }
.detail-label { font-size: 14px; font-weight: 600; color: #1f2d3d; margin-bottom: 8px; }
.detail-label-sm { font-size: 13px; font-weight: 600; color: #667085; display: block; margin: 10px 0 6px; }
.detail-sentiment-row {
  display: flex; align-items: center; gap: 12px;
  font-size: 14px; color: #667085; margin-bottom: 12px;
}
.aspect-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 8px; }
.aspect-card {
  padding: 10px 14px; border-radius: 8px; font-size: 12px;
  display: flex; flex-direction: column; gap: 4px;
}
.aspect-card.positive { background: #f6ffed; border: 1px solid #b7eb8f; }
.aspect-card.negative { background: #fff2f0; border: 1px solid #ffccc7; }
.aspect-card.neutral { background: #f5f5f5; border: 1px solid #e0e0e0; }
.aspect-card-label { font-weight: 600; color: #1f2d3d; }
.aspect-card-sentiment.positive { color: #52c41a; }
.aspect-card-sentiment.negative { color: #ff4d4f; }
.aspect-card-sentiment.neutral { color: #999; }
.aspect-card-text { color: #999; font-size: 11px; }

.detail-issues .issue-item {
  display: flex; align-items: center; gap: 10px; margin-top: 6px;
  font-size: 12px; flex-wrap: wrap;
}
.issue-tag {
  padding: 2px 10px; background: #fff2f0; color: #ff4d4f;
  border-radius: 4px; font-weight: 600;
}
.issue-conf { color: #999; }
.issue-evidence { color: #667085; font-style: italic; }

.btn-secondary {
  padding: 8px 20px; background: #f5f5f5; border: 1px solid #d9d9d9;
  border-radius: 6px; font-size: 14px; color: #667085; cursor: pointer; transition: all 0.2s;
}
.btn-secondary:hover { background: #eee; }

/* 响应式 */
@media (max-width: 1200px) {
  .summary-cards { grid-template-columns: repeat(2, 1fr); }
  .charts-row { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .summary-cards { grid-template-columns: 1fr; }
  .toolbar { flex-direction: column; align-items: flex-start; }
  .toolbar-left { flex-direction: column; align-items: flex-start; width: 100%; }
  .list-filters { flex-wrap: wrap; }
  .donut-chart-wrapper { flex-direction: column; }
}
</style>
