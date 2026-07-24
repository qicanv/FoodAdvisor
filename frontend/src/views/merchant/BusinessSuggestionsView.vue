<template>
  <MerchantLayout title="经营改进建议" subtitle="基于口碑数据为您的店铺生成可执行的改进建议">
    <div class="suggestions-container">
      <!-- ========== 店铺选择 ========== -->
      <div class="store-select-section">
        <div class="store-select-card">
          <span class="store-select-label">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9z"></path>
              <polyline points="9 22 9 12 15 12 15 22"></polyline>
            </svg>
            选择店铺：
          </span>
          <select v-model="selectedStoreId" class="store-select" @change="onStoreChange">
            <option :value="null" disabled>请选择店铺</option>
            <option v-for="s in userStores" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
          <span v-if="userStores.length === 0" class="no-store-hint">暂无管理的店铺</span>
        </div>
      </div>

      <!-- ========== 操作栏 ========== -->
      <div class="action-bar" v-if="selectedStoreId">
        <div class="action-info">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"></path>
          </svg>
          <span>系统将结合口碑趋势、差评归因、商家亮点和竞品对比结果，为您生成阶段性经营改进建议</span>
        </div>
        <button
          class="generate-btn"
          :disabled="generating"
          @click="handleGenerate"
        >
          <svg v-if="generating" class="spinning" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"></circle>
            <path d="M12 6v6l4 2"></path>
          </svg>
          <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="23 4 23 10 17 10"></polyline>
            <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path>
          </svg>
          {{ generating ? '生成中...' : (hasSuggestions ? '刷新建议' : '生成建议') }}
        </button>
      </div>

      <!-- ========== 未选店铺 ========== -->
      <div v-if="!selectedStoreId" class="state-card empty-state">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#c0c4cc" stroke-width="1.5">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9z"></path>
          <polyline points="9 22 9 12 15 12 15 22"></polyline>
        </svg>
        <h3>请先选择店铺</h3>
        <p class="empty-desc">在上方下拉框中选择一个管理的店铺后，即可查看或生成经营改进建议</p>
      </div>

      <!-- ========== 加载状态 ========== -->
      <div v-if="loading" class="state-card">
        <span class="loading-spinner"></span>
        <p>正在加载建议数据...</p>
      </div>

      <!-- ========== 空状态：未生成 ========== -->
      <div v-else-if="generationStatus === 'NONE' && !hasSuggestions" class="state-card empty-state">
        <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#c0c4cc" stroke-width="1.5">
          <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"></path>
          <circle cx="12" cy="12" r="3"></circle>
        </svg>
        <h3>暂无经营改进建议</h3>
        <p class="empty-desc">{{ generationMessage }}</p>
        <button class="generate-btn primary" @click="handleGenerate" :disabled="generating">
          立即生成
        </button>
      </div>

      <!-- ========== 空状态：数据不足 ========== -->
      <div v-else-if="generationStatus === 'INSUFFICIENT_DATA'" class="state-card warning-state">
        <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#faad14" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="12" y1="8" x2="12" y2="12"></line>
          <line x1="12" y1="16" x2="12.01" y2="16"></line>
        </svg>
        <h3>数据不足</h3>
        <p class="warning-desc">{{ generationMessage }}</p>
        <!-- 数据源状态 -->
        <div v-if="dataSources && dataSources.length > 0" class="data-sources-status">
          <h4>数据源可用性</h4>
          <div class="ds-grid">
            <div
              v-for="ds in dataSources"
              :key="ds.sourceType"
              :class="['ds-item', { available: ds.available, unavailable: !ds.available }]"
            >
              <span class="ds-dot"></span>
              <span class="ds-label">{{ sourceTypeLabel(ds.sourceType) }}</span>
              <span class="ds-detail">{{ ds.message }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 建议列表 ========== -->
      <div v-else-if="hasSuggestions" class="suggestions-list">
        <!-- 生成信息 -->
        <div class="gen-info">
          <span class="gen-badge" :class="'confidence-' + (suggestions[0]?.confidence || 'medium').toLowerCase()">
            {{ confidenceLabel(suggestions[0]?.confidence) }}
          </span>
          <span class="gen-time" v-if="suggestions[0]?.generatedAt">
            生成于 {{ formatTime(suggestions[0].generatedAt) }}
          </span>
          <span class="gen-version" v-if="suggestions[0]?.version">
            版本 v{{ suggestions[0].version }}
          </span>
        </div>

        <div
          v-for="(item, index) in suggestions"
          :key="item.suggestionId || index"
          class="suggestion-card"
        >
          <!-- 卡片头部 -->
          <div class="card-header">
            <div class="header-left">
              <span class="priority-badge" :class="'priority-' + (item.priority || 'medium').toLowerCase()">
                {{ priorityLabel(item.priority) }}
              </span>
              <span class="category-badge">{{ categoryLabel(item.category) }}</span>
              <span class="timeframe-badge" :class="item.timeframe === 'LONG_TERM' ? 'long-term' : 'short-term'">
                {{ item.timeframe === 'LONG_TERM' ? '长期' : '短期' }}
              </span>
            </div>
            <div class="header-right">
              <span class="confidence-text" :class="'conf-' + (item.confidence || 'medium').toLowerCase()">
                置信度: {{ confidenceLabel(item.confidence) }}
              </span>
            </div>
          </div>

          <!-- 标题 -->
          <h3 class="suggestion-title">{{ item.title }}</h3>

          <!-- 描述 -->
          <p class="suggestion-desc">{{ item.description }}</p>

          <!-- 预期效果 -->
          <div v-if="item.expectedEffect" class="expected-effect">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="20 6 9 17 4 12"></polyline>
            </svg>
            <span>预期效果：{{ item.expectedEffect }}</span>
          </div>

          <!-- 数据依据 -->
          <div class="data-basis" v-if="item.dataBasisSummary || item.metricName">
            <div class="basis-header">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="20" x2="18" y2="10"></line>
                <line x1="12" y1="20" x2="12" y2="4"></line>
                <line x1="6" y1="20" x2="6" y2="14"></line>
              </svg>
              <span>数据依据</span>
              <button
                class="evidence-btn"
                @click="toggleEvidences(item.suggestionId)"
              >
                {{ expandedEvidences === item.suggestionId ? '收起依据' : '查看依据' }}
              </button>
            </div>
            <p class="basis-summary">{{ item.dataBasisSummary }}</p>
            <p v-if="item.metricName" class="basis-metric">
              {{ item.metricName }}：{{ item.metricValue }}
            </p>
          </div>

          <!-- 依据详情（可展开） -->
          <div v-if="expandedEvidences === item.suggestionId" class="evidences-panel">
            <div v-if="evidencesLoading" class="evidences-loading">
              <span class="loading-spinner small"></span> 加载依据中...
            </div>
            <div v-else-if="currentEvidences.length === 0" class="evidences-empty">
              暂无详细依据数据
            </div>
            <div v-else class="evidences-list">
              <div
                v-for="(ev, ei) in currentEvidences"
                :key="ei"
                class="evidence-item"
              >
                <div class="evidence-header">
                  <span class="evidence-type">{{ sourceTypeLabel(ev.sourceType) }}</span>
                  <span v-if="ev.changeDirection" :class="['change-badge', ev.changeDirection.toLowerCase()]">
                    {{ ev.changeDirection === 'UP' ? '↑ 上升' : ev.changeDirection === 'DOWN' ? '↓ 下降' : '→ 持平' }}
                  </span>
                </div>
                <p class="evidence-text" v-if="ev.evidenceExcerpt">{{ ev.evidenceExcerpt }}</p>
                <div class="evidence-meta" v-if="ev.metricName || ev.periodStart">
                  <span v-if="ev.metricName">指标：{{ ev.metricName }}</span>
                  <span v-if="ev.currentValue">当前：{{ ev.currentValue }}</span>
                  <span v-if="ev.previousValue">上期：{{ ev.previousValue }}</span>
                  <span v-if="ev.periodStart">{{ ev.periodStart }} ~ {{ ev.periodEnd }}</span>
                </div>
                <!-- 原始评价（如果有） -->
                <div v-if="ev.reviewAvailable" class="review-quote">
                  <div class="review-rating" v-if="ev.rating">
                    <span v-for="s in 5" :key="s" :class="['star', { filled: s <= ev.rating }]">★</span>
                  </div>
                  <p class="review-content">"{{ ev.reviewContent }}"</p>
                  <span class="review-time" v-if="ev.publishedAt">{{ formatTime(ev.publishedAt) }}</span>
                </div>
                <div v-else-if="ev.reviewAvailable === false" class="review-unavailable">
                  原评价已不可用（{{ ev.unavailableReason === 'SOURCE_DELETED' ? '已删除' : '已隐藏' }}）
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ========== 错误状态 ========== -->
      <div v-if="errorMsg" class="state-card error-state">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#f56c6c" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"></circle>
          <line x1="15" y1="9" x2="9" y2="15"></line>
          <line x1="9" y1="9" x2="15" y2="15"></line>
        </svg>
        <h3>{{ errorMsg }}</h3>
        <button class="retry-btn" @click="loadSuggestions()">重试</button>
      </div>

      <!-- 分析结果反馈 -->
      <AnalysisFeedbackPanel
        v-if="selectedStoreId && hasSuggestions"
        :merchantId="selectedStoreId"
        analysisType="BUSINESS_SUGGESTION"
      />
    </div>
  </MerchantLayout>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import MerchantLayout from '../../components/MerchantLayout.vue'
import AnalysisFeedbackPanel from '../../components/AnalysisFeedbackPanel.vue'
import { getMyMerchants } from '../../api/merchantConsole'
import {
  getBusinessSuggestions,
  getSuggestionEvidences,
  generateBusinessSuggestions,
} from '../../api/businessSuggestion'

// ---- 店铺管理 ----
const userStores = ref([])
const selectedStoreId = ref(null)

const loadStores = async () => {
  try {
    const resp = await getMyMerchants()
    if (resp.success && resp.data) {
      userStores.value = resp.data
      if (userStores.value.length > 0) {
        // 优先用 localStorage 缓存的店铺
        const savedId = localStorage.getItem('activeMerchantId')
        const savedIdNum = savedId ? Number(savedId) : null
        const exists = userStores.value.some(s => s.id === savedIdNum)
        if (exists) {
          selectedStoreId.value = savedIdNum
        } else {
          selectedStoreId.value = userStores.value[0].id
        }
      }
    }
  } catch (err) {
    console.error('加载店铺列表失败:', err)
  }
}

const onStoreChange = () => {
  if (selectedStoreId.value) {
    localStorage.setItem('activeMerchantId', String(selectedStoreId.value))
    resetState()
    loadSuggestions(selectedStoreId.value)
  }
}

// ---- 状态 ----
const loading = ref(false)
const generating = ref(false)
const errorMsg = ref('')
const suggestions = ref([])
const generationStatus = ref('')
const generationMessage = ref('')
const dataSources = ref([])

// 依据展开状态
const expandedEvidences = ref(null)
const evidencesLoading = ref(false)
const currentEvidences = ref([])

// ---- 计算属性 ----
const hasSuggestions = computed(() => {
  return suggestions.value.length > 0 && suggestions.value[0]?.suggestionId != null
})

// ---- 方法 ----

const resetState = () => {
  suggestions.value = []
  generationStatus.value = ''
  generationMessage.value = ''
  dataSources.value = []
  errorMsg.value = ''
  expandedEvidences.value = null
  currentEvidences.value = []
}

/** 加载建议列表 */
const loadSuggestions = async (merchantId) => {
  const id = merchantId || selectedStoreId.value
  if (!id) return

  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await getBusinessSuggestions(id)
    console.log('GET suggestions response:', resp)
    if (resp.success && resp.data && Array.isArray(resp.data)) {
      const list = resp.data
      if (list.length === 1 && list[0].suggestionId == null) {
        generationStatus.value = list[0].generationStatus || 'NONE'
        generationMessage.value = list[0].generationMessage || ''
        dataSources.value = list[0].dataSources || []
        suggestions.value = []
      } else {
        suggestions.value = list
        generationStatus.value = 'SUCCESS'
        generationMessage.value = ''
        dataSources.value = []
      }
    } else if (!resp.success) {
      errorMsg.value = resp.message || '加载建议数据失败'
    }
  } catch (err) {
    console.error('加载经营建议失败:', err)
    errorMsg.value = '加载建议数据失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

/** 生成/刷新建议 */
const handleGenerate = async () => {
  const id = selectedStoreId.value
  if (!id) {
    errorMsg.value = '请先选择一个店铺'
    return
  }

  generating.value = true
  errorMsg.value = ''
  console.log('开始生成建议，merchantId:', id)

  try {
    const resp = await generateBusinessSuggestions(id, true)
    console.log('生成建议 response:', resp)

    if (resp.success && resp.data && Array.isArray(resp.data)) {
      const list = resp.data
      if (list.length === 1 && list[0].suggestionId == null) {
        generationStatus.value = list[0].generationStatus || list[0].status || 'NONE'
        generationMessage.value = list[0].generationMessage || resp.message || ''
        dataSources.value = list[0].dataSources || []
        suggestions.value = []
      } else {
        suggestions.value = list
        generationStatus.value = 'SUCCESS'
        generationMessage.value = ''
        dataSources.value = []
      }
    } else if (!resp.success) {
      errorMsg.value = resp.message || '生成失败，请稍后重试'
    } else {
      errorMsg.value = '响应数据格式异常，请查看控制台'
      console.error('Unexpected response format:', resp)
    }
  } catch (err) {
    console.error('生成经营建议失败:', err)
    errorMsg.value = err?.response?.data?.message || err?.message || '生成失败，请稍后重试'
  } finally {
    generating.value = false
  }
}

/** 展开/收起依据 */
const toggleEvidences = async (suggestionId) => {
  if (expandedEvidences.value === suggestionId) {
    expandedEvidences.value = null
    currentEvidences.value = []
    return
  }

  expandedEvidences.value = suggestionId
  evidencesLoading.value = true
  currentEvidences.value = []
  try {
    const resp = await getSuggestionEvidences(selectedStoreId.value, suggestionId)
    if (resp.data && Array.isArray(resp.data)) {
      currentEvidences.value = resp.data
    }
  } catch (err) {
    console.error('加载依据失败:', err)
  } finally {
    evidencesLoading.value = false
  }
}

// ---- 标签转换 ----
const sourceTypeLabel = (type) => {
  const map = {
    REPUTATION_TREND: '口碑趋势',
    NEGATIVE_ISSUE: '差评归因',
    HIGHLIGHT: '商家亮点',
    COMPETITOR: '竞品对比',
    REVIEW: '原始评价',
  }
  return map[type] || type || '数据来源'
}

const categoryLabel = (cat) => {
  const map = {
    REPUTATION_TREND: '口碑趋势',
    NEGATIVE_ISSUE: '差评归因',
    HIGHLIGHT_GAP: '亮点差距',
    COMPETITOR_GAP: '竞品差距',
  }
  return map[cat] || cat || '综合分析'
}

const priorityLabel = (p) => {
  const map = { HIGH: '高优先', MEDIUM: '中优先', LOW: '低优先' }
  return map[p] || p || '中优先'
}

const confidenceLabel = (c) => {
  const map = { HIGH: '高', MEDIUM: '中', LOW: '低' }
  return map[c] || c || '中'
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  try {
    const date = new Date(timeStr)
    return date.toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    })
  } catch {
    return timeStr
  }
}

// ---- 生命周期 ----
onMounted(async () => {
  await loadStores()
  if (selectedStoreId.value) {
    loadSuggestions(selectedStoreId.value)
  }
})
</script>

<style scoped>
.suggestions-container {
  max-width: 960px;
  margin: 0 auto;
}

/* ---- 店铺选择 ---- */
.store-select-section {
  margin-bottom: 20px;
}

.store-select-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 10px;
  padding: 14px 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.store-select-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2d3d;
  white-space: nowrap;
}

.store-select {
  padding: 8px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  font-size: 14px;
  color: #1f2d3d;
  background: #fff;
  min-width: 220px;
  cursor: pointer;
}

.store-select:focus {
  border-color: #52c41a;
  outline: none;
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.15);
}

.no-store-hint {
  font-size: 13px;
  color: #999;
}

/* ---- 操作栏 ---- */
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  border-radius: 12px;
  padding: 16px 24px;
  margin-bottom: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.action-info {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #667085;
  font-size: 13px;
  flex: 1;
}

.generate-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 24px;
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
  margin-left: 16px;
}

.generate-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(82, 196, 26, 0.35);
}

.generate-btn:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.generate-btn.primary {
  margin-left: 0;
  margin-top: 20px;
}

/* ---- 状态卡片 ---- */
.state-card {
  background: #fff;
  border-radius: 12px;
  padding: 60px 40px;
  text-align: center;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.state-card h3 {
  font-size: 18px;
  color: #1f2d3d;
  margin: 16px 0 8px;
}

.state-card p {
  color: #667085;
  font-size: 14px;
  margin: 0;
}

.empty-desc, .warning-desc {
  max-width: 500px;
  margin: 0 auto !important;
  line-height: 1.6;
}

/* ---- 数据源状态 ---- */
.data-sources-status {
  margin-top: 24px;
  text-align: left;
  max-width: 600px;
  margin-left: auto;
  margin-right: auto;
}

.data-sources-status h4 {
  font-size: 14px;
  color: #1f2d3d;
  margin-bottom: 12px;
}

.ds-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ds-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #fafafa;
  border-radius: 8px;
  font-size: 13px;
}

.ds-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ds-item.available .ds-dot {
  background: #52c41a;
}

.ds-item.unavailable .ds-dot {
  background: #d9d9d9;
}

.ds-label {
  font-weight: 600;
  color: #1f2d3d;
  min-width: 80px;
}

.ds-detail {
  color: #667085;
}

/* ---- 建议列表 ---- */
.suggestions-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.gen-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 4px;
  font-size: 13px;
  color: #667085;
}

.gen-badge {
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.gen-badge.confidence-high {
  background: #f0f9ff;
  color: #1890ff;
}

.gen-badge.confidence-medium {
  background: #fffbe6;
  color: #d48806;
}

.gen-badge.confidence-low {
  background: #fff1f0;
  color: #cf1322;
}

.gen-time, .gen-version {
  color: #999;
}

/* ---- 建议卡片 ---- */
.suggestion-card {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  border: 1px solid #f0f0f0;
  transition: box-shadow 0.2s;
}

.suggestion-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}

.header-left {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.priority-badge {
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.priority-badge.priority-high {
  background: #fff1f0;
  color: #cf1322;
}

.priority-badge.priority-medium {
  background: #fffbe6;
  color: #d48806;
}

.priority-badge.priority-low {
  background: #f5f5f5;
  color: #999;
}

.category-badge {
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  background: #f0f5ff;
  color: #2f54eb;
  font-weight: 500;
}

.timeframe-badge {
  padding: 2px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.timeframe-badge.short-term {
  background: #e6fffb;
  color: #08979c;
}

.timeframe-badge.long-term {
  background: #f9f0ff;
  color: #722ed1;
}

.confidence-text {
  font-size: 12px;
  font-weight: 500;
}

.confidence-text.conf-high {
  color: #1890ff;
}

.confidence-text.conf-medium {
  color: #d48806;
}

.confidence-text.conf-low {
  color: #cf1322;
}

.suggestion-title {
  font-size: 17px;
  font-weight: 700;
  color: #1f2d3d;
  margin: 0 0 10px;
  line-height: 1.4;
}

.suggestion-desc {
  font-size: 14px;
  color: #475467;
  line-height: 1.7;
  margin: 0 0 16px;
  white-space: pre-wrap;
}

/* ---- 预期效果 ---- */
.expected-effect {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 16px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  margin-bottom: 14px;
  font-size: 13px;
  color: #389e0d;
}

.expected-effect svg {
  flex-shrink: 0;
  margin-top: 2px;
}

/* ---- 数据依据 ---- */
.data-basis {
  padding: 14px 16px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px solid #f0f0f0;
}

.basis-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2d3d;
}

.evidence-btn {
  margin-left: auto;
  padding: 4px 12px;
  background: transparent;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  color: #1890ff;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.evidence-btn:hover {
  border-color: #1890ff;
  background: #e6f7ff;
}

.basis-summary {
  font-size: 13px;
  color: #475467;
  line-height: 1.6;
  margin: 0 0 4px;
}

.basis-metric {
  font-size: 13px;
  color: #1890ff;
  font-weight: 500;
  margin: 4px 0 0;
}

/* ---- 依据详情面板 ---- */
.evidences-panel {
  margin-top: 14px;
  border-top: 1px solid #f0f0f0;
  padding-top: 14px;
}

.evidences-loading, .evidences-empty {
  font-size: 13px;
  color: #999;
  text-align: center;
  padding: 16px;
}

.evidences-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.evidence-item {
  padding: 14px;
  background: #fafafa;
  border-radius: 8px;
  border-left: 3px solid #1890ff;
}

.evidence-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.evidence-type {
  font-size: 12px;
  font-weight: 600;
  color: #1890ff;
}

.change-badge {
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.change-badge.up {
  background: #fff1f0;
  color: #cf1322;
}

.change-badge.down {
  background: #f6ffed;
  color: #389e0d;
}

.change-badge.stable {
  background: #f5f5f5;
  color: #999;
}

.evidence-text {
  font-size: 13px;
  color: #475467;
  line-height: 1.6;
  margin: 0 0 6px;
}

.evidence-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #999;
}

.review-quote {
  margin-top: 10px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}

.review-rating {
  margin-bottom: 4px;
}

.star {
  color: #d9d9d9;
}

.star.filled {
  color: #faad14;
}

.review-content {
  font-size: 13px;
  color: #475467;
  line-height: 1.6;
  margin: 0;
  font-style: italic;
}

.review-time {
  font-size: 12px;
  color: #999;
}

.review-unavailable {
  margin-top: 10px;
  padding: 8px 14px;
  background: #fff1f0;
  border-radius: 6px;
  font-size: 12px;
  color: #cf1322;
}

/* ---- 通用 ---- */
.loading-spinner {
  display: inline-block;
  width: 24px;
  height: 24px;
  border: 3px solid #f0f0f0;
  border-top-color: #52c41a;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-spinner.small {
  width: 16px;
  height: 16px;
  border-width: 2px;
}

.spinning {
  animation: spin 1.5s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.retry-btn {
  margin-top: 16px;
  padding: 8px 20px;
  background: #fff;
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  color: #1890ff;
  font-size: 13px;
  cursor: pointer;
}

.retry-btn:hover {
  border-color: #1890ff;
}

.error-state h3 {
  color: #f56c6c;
}

.warning-state h3 {
  color: #d48806;
}
</style>
