<template>
  <AdminLayout>
    <div class="feedback-admin-container">
      <div class="page-header">
        <h1>分析结果反馈</h1>
        <p class="page-subtitle">查看商家对 AI 分析功能的反馈情况（EPIC-06 Story 5）</p>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-row" v-if="!statsLoading">
        <div class="stat-card">
          <div class="stat-icon total">📊</div>
          <div class="stat-body">
            <div class="stat-value">{{ stats.totalCount || 0 }}</div>
            <div class="stat-label">总反馈数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon accurate">✅</div>
          <div class="stat-body">
            <div class="stat-value accurate">{{ stats.accurateCount || 0 }}</div>
            <div class="stat-label">准确</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon inaccurate">❌</div>
          <div class="stat-body">
            <div class="stat-value inaccurate">{{ stats.inaccurateCount || 0 }}</div>
            <div class="stat-label">不准确</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon rate">📈</div>
          <div class="stat-body">
            <div class="stat-value" :class="accuracyRateClass">
              {{ stats.accuracyRate != null ? (stats.accuracyRate * 100).toFixed(1) + '%' : 'N/A' }}
            </div>
            <div class="stat-label">准确率</div>
          </div>
        </div>
      </div>
      <div v-else class="stats-loading">
        <span class="loading-spinner"></span> 加载统计中...
      </div>

      <!-- 按分析类型统计 -->
      <div class="section-card" v-if="stats.byAnalysisType?.length">
        <h3 class="section-title">按分析类型统计</h3>
        <div class="type-stats-table">
          <table>
            <thead>
              <tr>
                <th>分析类型</th>
                <th>总反馈</th>
                <th>准确</th>
                <th>不准确</th>
                <th>准确率</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in stats.byAnalysisType" :key="row.analysisType">
                <td class="type-name">{{ row.analysisTypeText || row.analysisType }}</td>
                <td>{{ row.totalCount }}</td>
                <td class="count-accurate">{{ row.accurateCount }}</td>
                <td class="count-inaccurate">{{ row.inaccurateCount }}</td>
                <td>
                  <span :class="['rate-badge', row.accuracyRate != null ? (row.accuracyRate >= 0.7 ? 'good' : row.accuracyRate >= 0.4 ? 'warn' : 'bad') : 'na']">
                    {{ row.accuracyRate != null ? (row.accuracyRate * 100).toFixed(0) + '%' : 'N/A' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 筛选 + 列表 -->
      <div class="section-card">
        <div class="list-header">
          <h3 class="section-title">反馈列表</h3>
          <div class="filter-row">
            <select v-model="filterAnalysisType" @change="loadList('reset')" class="filter-select">
              <option value="">全部分析类型</option>
              <option v-for="t in analysisTypeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>
            </select>
            <select v-model="filterFeedbackType" @change="loadList('reset')" class="filter-select">
              <option value="">全部反馈类型</option>
              <option value="ACCURATE">准确</option>
              <option value="INACCURATE">不准确</option>
            </select>
            <button class="refresh-btn" @click="loadList()">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/>
              </svg>
              刷新
            </button>
          </div>
        </div>

        <!-- 加载 -->
        <div v-if="listLoading" class="list-loading">
          <span class="loading-spinner"></span> 加载中...
        </div>

        <!-- 空 -->
        <div v-else-if="listData.length === 0" class="list-empty">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#d9d9d9" stroke-width="1.5">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          <p>暂无反馈记录</p>
        </div>

        <!-- 列表 -->
        <div v-else class="feedback-table-wrapper">
          <table class="feedback-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>商家</th>
                <th>分析类型</th>
                <th>分析ID</th>
                <th>反馈类型</th>
                <th>问题说明</th>
                <th>反馈人</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="fb in listData" :key="fb.id">
                <td class="cell-id">{{ fb.id }}</td>
                <td class="cell-merchant">{{ fb.merchantName || '商家 #' + fb.merchantId }}</td>
                <td>{{ fb.analysisTypeText || fb.analysisType }}</td>
                <td class="cell-id">{{ fb.analysisId || '-' }}</td>
                <td>
                  <span :class="['type-badge', fb.feedbackType === 'ACCURATE' ? 'accurate' : 'inaccurate']">
                    {{ fb.feedbackTypeText || fb.feedbackType }}
                  </span>
                </td>
                <td class="cell-content">
                  <span v-if="fb.content" class="content-text">{{ fb.content }}</span>
                  <span v-else class="no-content">-</span>
                </td>
                <td>{{ fb.createdByUsername || '用户 #' + fb.createdBy }}</td>
                <td class="cell-time">{{ formatTime(fb.createdAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div class="pagination" v-if="totalPages > 1">
          <button :disabled="pageNum <= 1" @click="pageNum--; loadList()">上一页</button>
          <span v-for="p in visiblePages" :key="p"
                :class="['page-btn', { active: p === pageNum }]"
                @click="pageNum = p; loadList()">{{ p }}</span>
          <button :disabled="pageNum >= totalPages" @click="pageNum++; loadList()">下一页</button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getAnalysisFeedbackStatistics, getAllAnalysisFeedback } from '../../api/analysisFeedback'

// 统计
const stats = ref({ totalCount: 0, accurateCount: 0, inaccurateCount: 0, accuracyRate: null, byAnalysisType: [] })
const statsLoading = ref(false)

// 列表
const listData = ref([])
const listLoading = ref(false)
const pageNum = ref(1)
const totalPages = ref(0)
const currentTotal = ref(0)
const filterAnalysisType = ref('')
const filterFeedbackType = ref('')

const analysisTypeOptions = [
  { value: 'SENTIMENT', label: '情感分析' },
  { value: 'KEYWORD', label: '关键词提取' },
  { value: 'ISSUE_ATTRIBUTION', label: '差评归因' },
  { value: 'COMPETITOR', label: '竞品对比' },
  { value: 'BUSINESS_SUGGESTION', label: '经营建议' },
  { value: 'REVIEW_SUMMARY', label: '评价摘要' },
  { value: 'HIGHLIGHT', label: '商家亮点' }
]

const accuracyRateClass = computed(() => {
  if (stats.value.accuracyRate == null) return 'na'
  return stats.value.accuracyRate >= 0.7 ? 'accurate' : stats.value.accuracyRate >= 0.4 ? 'warn' : 'inaccurate'
})

const visiblePages = computed(() => {
  const pages = []
  const start = Math.max(1, pageNum.value - 2)
  const end = Math.min(totalPages.value, pageNum.value + 2)
  for (let i = start; i <= end; i++) pages.push(i)
  return pages
})

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await getAnalysisFeedbackStatistics()
    if (res.success) stats.value = res.data
  } catch (_) { /* ignore */ }
  finally { statsLoading.value = false }
}

async function loadList(resetPage) {
  if (resetPage === 'reset') pageNum.value = 1
  listLoading.value = true
  try {
    const res = await getAllAnalysisFeedback({
      analysisType: filterAnalysisType.value || undefined,
      feedbackType: filterFeedbackType.value || undefined,
      pageNum: pageNum.value,
      pageSize: 20
    })
    if (res.success && res.data) {
      listData.value = res.data.records || []
      currentTotal.value = res.data.total || 0
      totalPages.value = Math.ceil(currentTotal.value / 20) || 1
    }
  } catch (_) { /* ignore */ }
  finally { listLoading.value = false }
}

function formatTime(t) {
  if (!t) return '-'
  return new Date(t).toLocaleString('zh-CN')
}

onMounted(() => {
  loadStats()
  loadList()
})
</script>

<style scoped>
.feedback-admin-container {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}

.page-subtitle {
  margin: 6px 0 0;
  color: #909399;
  font-size: 14px;
}

/* 统计卡片 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.stat-icon {
  font-size: 32px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.stat-value.accurate { color: #52c41a; }
.stat-value.inaccurate { color: #ff4d4f; }
.stat-value.warn { color: #faad14; }
.stat-value.na { color: #c0c4cc; }

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}

.stats-loading, .list-loading, .list-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: #909399;
  font-size: 14px;
}

/* Section */
.section-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  padding: 20px 24px;
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 16px 0;
}

/* 类型统计表 */
.type-stats-table table {
  width: 100%;
  border-collapse: collapse;
}

.type-stats-table th {
  text-align: left;
  padding: 8px 12px;
  font-size: 13px;
  color: #909399;
  font-weight: 500;
  border-bottom: 2px solid #f0f0f0;
}

.type-stats-table td {
  padding: 10px 12px;
  font-size: 14px;
  color: #303133;
  border-bottom: 1px solid #f5f5f5;
}

.type-name {
  font-weight: 600;
}

.count-accurate { color: #52c41a; font-weight: 600; }
.count-inaccurate { color: #ff4d4f; font-weight: 600; }

.rate-badge {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
}

.rate-badge.good { background: #f6ffed; color: #52c41a; }
.rate-badge.warn { background: #fffbe6; color: #faad14; }
.rate-badge.bad { background: #fff2f0; color: #ff4d4f; }
.rate-badge.na { background: #f5f5f5; color: #c0c4cc; }

/* 列表头部 */
.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.list-header .section-title {
  margin: 0;
}

.filter-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.filter-select {
  padding: 6px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  background: #fff;
  outline: none;
}

.filter-select:focus {
  border-color: #409eff;
}

.refresh-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
}

.refresh-btn:hover {
  background: #ecf5ff;
  border-color: #c6e2ff;
  color: #409eff;
}

/* 反馈列表 */
.feedback-table-wrapper {
  overflow-x: auto;
}

.feedback-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 800px;
}

.feedback-table th {
  text-align: left;
  padding: 10px 12px;
  font-size: 12px;
  color: #909399;
  font-weight: 600;
  text-transform: uppercase;
  border-bottom: 2px solid #f0f0f0;
  white-space: nowrap;
}

.feedback-table td {
  padding: 10px 12px;
  font-size: 13px;
  color: #303133;
  border-bottom: 1px solid #f5f5f5;
  vertical-align: middle;
}

.cell-id {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
}

.cell-merchant {
  font-weight: 500;
  white-space: nowrap;
}

.cell-content {
  max-width: 240px;
}

.content-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.no-content {
  color: #c0c4cc;
}

.cell-time {
  white-space: nowrap;
  font-size: 12px;
  color: #909399;
}

.type-badge {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.type-badge.accurate { background: #f6ffed; color: #52c41a; border: 1px solid #b7eb8f; }
.type-badge.inaccurate { background: #fff2f0; color: #ff4d4f; border: 1px solid #ffccc7; }

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 20px;
}

.pagination button {
  padding: 6px 12px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
}

.pagination button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-btn {
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  min-width: 32px;
  text-align: center;
}

.page-btn.active {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #f0f0f0;
  border-top-color: #409eff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  display: inline-block;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
