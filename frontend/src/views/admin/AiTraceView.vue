<template>
  <AdminLayout title="AI 请求追踪" subtitle="查询 AI 请求的执行阶段、检索来源和结果摘要">
    <section class="trace-page">
      <el-card class="filter-card" shadow="never">
        <form @submit.prevent="search">
          <el-form :model="filters" label-width="72px" class="trace-filter-form">
            <el-form-item label="Trace ID"><el-input v-model="filters.traceId" clearable placeholder="trc-..." /></el-form-item>
            <el-form-item label="Request ID"><el-input v-model="filters.requestId" clearable placeholder="请求编号" /></el-form-item>
            <el-form-item label="场景"><el-input v-model="filters.scene" clearable placeholder="例如 RECOMMENDATION" /></el-form-item>
            <el-form-item label="状态">
              <el-select v-model="filters.status" clearable placeholder="全部状态">
                <el-option v-for="item in statuses" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="模型"><el-input v-model="filters.modelName" clearable placeholder="模型名称" /></el-form-item>
            <el-form-item label="开始时间" class="time-range">
              <el-date-picker v-model="filters.timeRange" type="datetimerange" range-separator="至"
                start-placeholder="开始时间" end-placeholder="结束时间" clearable />
            </el-form-item>
            <el-form-item class="filter-actions">
              <el-button type="primary" native-type="submit">查询</el-button>
              <el-button @click="reset">重置</el-button>
            </el-form-item>
          </el-form>
        </form>
      </el-card>

      <el-alert v-if="errorMessage" class="notice" type="error" :title="errorMessage" show-icon closable @close="errorMessage = ''" />

      <el-card class="list-card" shadow="never">
        <el-table v-loading="loading" :data="records" empty-text="暂无 AI 请求追踪记录" class="trace-table" row-key="traceId">
          <el-table-column label="Trace ID" min-width="180">
            <template #default="{ row }">
              <el-tooltip :content="displayValue(row.traceId)" placement="top">
                <span class="trace-id">{{ displayValue(row.traceId) }}</span>
              </el-tooltip>
              <el-button link type="primary" size="small" :disabled="!row.traceId" @click="copyTraceId(row.traceId)">复制</el-button>
            </template>
          </el-table-column>
          <el-table-column prop="requestId" label="Request ID" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ displayValue(row.requestId) }}</template>
          </el-table-column>
          <el-table-column prop="scene" label="场景" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.scene) }}</template></el-table-column>
          <el-table-column prop="intent" label="意图" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.intent) }}</template></el-table-column>
          <el-table-column label="状态" width="108"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ displayValue(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="provider" label="提供方" min-width="120"><template #default="{ row }">{{ displayValue(row.provider) }}</template></el-table-column>
          <el-table-column prop="modelName" label="模型" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.modelName) }}</template></el-table-column>
          <el-table-column prop="promptVersion" label="提示词版本" min-width="145" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.promptVersion) }}</template></el-table-column>
          <el-table-column label="总耗时" width="105"><template #default="{ row }">{{ formatDuration(row.totalDurationMs) }}</template></el-table-column>
          <el-table-column label="开始时间" min-width="175"><template #default="{ row }">{{ formatTime(row.startedAt) }}</template></el-table-column>
          <el-table-column label="完成时间" min-width="175"><template #default="{ row }">{{ formatTime(row.completedAt) }}</template></el-table-column>
          <el-table-column label="操作" width="120" fixed="right"><template #default="{ row }"><el-button link type="primary" :disabled="!row.traceId" @click="openDetail(row.traceId)">查看详情</el-button></template></el-table-column>
        </el-table>
        <div class="pagination-row">
          <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next" :total="total" @size-change="changePageSize" @current-change="loadTraces" />
        </div>
      </el-card>
    </section>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="82%" direction="rtl" destroy-on-close class="trace-drawer">
      <div v-loading="detailLoading" class="trace-detail">
        <template v-if="trace">
          <section class="detail-section">
            <h3>基本信息</h3>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="Trace ID"><span class="breakable">{{ displayValue(trace.traceId) }}</span></el-descriptions-item>
              <el-descriptions-item label="Request ID"><span class="breakable">{{ displayValue(trace.requestId) }}</span></el-descriptions-item>
              <el-descriptions-item label="Session ID">{{ displayValue(trace.sessionId) }}</el-descriptions-item>
              <el-descriptions-item label="用户 ID">{{ displayValue(trace.userId) }}</el-descriptions-item>
              <el-descriptions-item label="场景">{{ displayValue(trace.scene) }}</el-descriptions-item>
              <el-descriptions-item label="意图">{{ displayValue(trace.intent) }}</el-descriptions-item>
              <el-descriptions-item label="状态"><el-tag :type="statusTagType(trace.status)">{{ displayValue(trace.status) }}</el-tag></el-descriptions-item>
              <el-descriptions-item label="提供方">{{ displayValue(trace.provider) }}</el-descriptions-item>
              <el-descriptions-item label="模型名称">{{ displayValue(trace.modelName) }}</el-descriptions-item>
              <el-descriptions-item label="模型版本">{{ displayValue(trace.modelVersion) }}</el-descriptions-item>
              <el-descriptions-item label="提示词版本">{{ displayValue(trace.promptVersion) }}</el-descriptions-item>
              <el-descriptions-item label="总耗时">{{ formatDuration(trace.totalDurationMs) }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ formatTime(trace.startedAt) }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ formatTime(trace.completedAt) }}</el-descriptions-item>
              <el-descriptions-item label="错误码">{{ displayValue(trace.errorCode) }}</el-descriptions-item>
              <el-descriptions-item label="错误信息"><span class="breakable error-text">{{ displayValue(trace.errorMessage) }}</span></el-descriptions-item>
            </el-descriptions>
          </section>

          <section class="detail-section"><h3>结构化条件</h3><pre class="json-block">{{ formatJson(trace.structuredConditions) }}</pre></section>

          <section class="detail-section">
            <h3>处理阶段</h3>
            <el-table :data="sortedStages" class="stage-table" empty-text="本次请求没有阶段记录">
              <el-table-column type="expand" width="50">
                <template #default="{ row }"><div class="stage-expand"><div><strong>输入摘要</strong><pre class="json-block compact">{{ formatJson(row.inputSummary) }}</pre></div><div><strong>输出摘要</strong><pre class="json-block compact">{{ formatJson(row.outputSummary) }}</pre></div></div></template>
              </el-table-column>
              <el-table-column prop="sequenceNo" label="序号" width="72"><template #default="{ row }">{{ displayValue(row.sequenceNo) }}</template></el-table-column>
              <el-table-column prop="stageName" label="阶段" min-width="150" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.stageName) }}</template></el-table-column>
              <el-table-column prop="attemptNo" label="尝试" width="70"><template #default="{ row }">{{ displayValue(row.attemptNo) }}</template></el-table-column>
              <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ displayValue(row.status) }}</el-tag></template></el-table-column>
              <el-table-column prop="provider" label="提供方" min-width="110"><template #default="{ row }">{{ displayValue(row.provider) }}</template></el-table-column>
              <el-table-column prop="modelName" label="模型" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.modelName) }}</template></el-table-column>
              <el-table-column prop="promptVersion" label="提示词版本" min-width="135" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.promptVersion) }}</template></el-table-column>
              <el-table-column label="耗时" width="95"><template #default="{ row }">{{ formatDuration(row.durationMs) }}</template></el-table-column>
              <el-table-column label="开始时间" min-width="170"><template #default="{ row }">{{ formatTime(row.startedAt) }}</template></el-table-column>
              <el-table-column label="完成时间" min-width="170"><template #default="{ row }">{{ formatTime(row.completedAt) }}</template></el-table-column>
              <el-table-column label="错误" min-width="180" show-overflow-tooltip><template #default="{ row }"><span :class="{ 'error-text': row.status === 'FAILED' || row.status === 'FALLBACK' }">{{ displayValue(row.errorCode) }}{{ row.errorMessage ? `：${row.errorMessage}` : '' }}</span></template></el-table-column>
            </el-table>
          </section>

          <section class="detail-section">
            <h3>检索来源</h3>
            <el-table :data="retrievalSources" class="source-table" empty-text="本次请求没有检索来源">
              <el-table-column prop="rankNo" label="排名" width="70"><template #default="{ row }">{{ displayValue(row.rankNo) }}</template></el-table-column>
              <el-table-column prop="sourceType" label="类型" min-width="110"><template #default="{ row }">{{ displayValue(row.sourceType) }}</template></el-table-column>
              <el-table-column prop="sourceId" label="来源 ID" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.sourceId) }}</template></el-table-column>
              <el-table-column prop="documentId" label="文档 ID" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.documentId) }}</template></el-table-column>
              <el-table-column prop="chunkId" label="分片 ID" min-width="120" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.chunkId) }}</template></el-table-column>
              <el-table-column prop="merchantId" label="商家 ID" width="100"><template #default="{ row }">{{ displayValue(row.merchantId) }}</template></el-table-column>
              <el-table-column prop="merchantName" label="商家名称" min-width="130" show-overflow-tooltip><template #default="{ row }">{{ displayValue(row.merchantName) }}</template></el-table-column>
              <el-table-column prop="summary" label="摘要" min-width="260" show-overflow-tooltip><template #default="{ row }"><span class="source-summary">{{ displayValue(row.summary) }}</span></template></el-table-column>
              <el-table-column label="相关度" width="100"><template #default="{ row }">{{ formatPercentage(row.relevanceScore) }}</template></el-table-column>
            </el-table>
          </section>

          <section class="detail-section"><h3>最终结果和错误</h3><pre class="json-block">{{ formatJson(trace.finalOutputSummary) }}</pre></section>
        </template>
        <el-empty v-else-if="!detailLoading" description="未能加载追踪详情" />
      </div>
    </el-drawer>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AdminLayout from '../../components/AdminLayout.vue'
import { getAiTraceDetail, getAiTraces } from '../../api/aiTrace'
import { displayTraceValue, formatTraceDuration, formatTraceJson, formatTracePercentage, formatTraceTime, sortTraceStages, traceStatusTagType } from '../../utils/aiTrace'

const statuses = ['SUCCESS', 'FAILED', 'FALLBACK', 'RUNNING']
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const loading = ref(false)
const errorMessage = ref('')
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const reloadPending = ref(false)
const filters = reactive({ traceId: '', requestId: '', scene: '', status: '', modelName: '', timeRange: [] })

const trace = computed(() => detail.value?.trace || null)
const sortedStages = computed(() => sortTraceStages(detail.value?.stages))
const retrievalSources = computed(() => Array.isArray(detail.value?.retrievalSources) ? detail.value.retrievalSources : [])
const detailTitle = computed(() => trace.value?.traceId ? `追踪详情：${trace.value.traceId}` : '追踪详情')

const displayValue = displayTraceValue
const formatDuration = formatTraceDuration
const formatJson = formatTraceJson
const formatPercentage = formatTracePercentage
const formatTime = formatTraceTime
const statusTagType = traceStatusTagType

const optional = value => value === '' || value === null || value === undefined ? undefined : value
const toIsoTime = value => {
  if (!value) return undefined
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString()
}

const loadTraces = async () => {
  if (loading.value) {
    reloadPending.value = true
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getAiTraces({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      traceId: optional(filters.traceId),
      requestId: optional(filters.requestId),
      scene: optional(filters.scene),
      status: optional(filters.status),
      modelName: optional(filters.modelName),
      startTime: toIsoTime(filters.timeRange?.[0]),
      endTime: toIsoTime(filters.timeRange?.[1]),
    })
    if (!response?.success) {
      errorMessage.value = response?.message || '加载 AI 请求追踪列表失败'
      return
    }
    records.value = Array.isArray(response.data?.records) ? response.data.records : []
    total.value = Number(response.data?.total) || 0
  } catch {
    errorMessage.value = '加载 AI 请求追踪列表失败，请稍后重试'
  } finally {
    loading.value = false
    if (reloadPending.value) {
      reloadPending.value = false
      loadTraces()
    }
  }
}

const search = () => { pageNum.value = 1; loadTraces() }
const reset = () => {
  Object.assign(filters, { traceId: '', requestId: '', scene: '', status: '', modelName: '', timeRange: [] })
  search()
}
const changePageSize = () => { pageNum.value = 1; loadTraces() }

const openDetail = async traceId => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const response = await getAiTraceDetail(traceId)
    if (!response?.success) {
      errorMessage.value = response?.message || '加载 AI 请求追踪详情失败'
      return
    }
    detail.value = response.data || null
  } catch {
    errorMessage.value = '加载 AI 请求追踪详情失败，请稍后重试'
  } finally {
    detailLoading.value = false
  }
}

const copyTraceId = async traceId => {
  if (!traceId || !navigator.clipboard?.writeText) {
    ElMessage.warning('当前环境不支持复制，请手动复制 Trace ID')
    return
  }
  try {
    await navigator.clipboard.writeText(traceId)
    ElMessage.success('Trace ID 已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

onMounted(loadTraces)
</script>

<style scoped>
.trace-page { display: flex; flex-direction: column; gap: 16px; }
.filter-card, .list-card { border-radius: 12px; }
.trace-filter-form { display: flex; flex-wrap: wrap; gap: 0 14px; }
.trace-filter-form :deep(.el-form-item) { margin-bottom: 14px; }
.time-range :deep(.el-date-editor) { width: 360px; }
.filter-actions { flex: 0 0 auto; min-width: 150px; margin-left: auto; }
.filter-actions :deep(.el-form-item__content) { display: flex; gap: 8px; }
.filter-actions :deep(.el-button) { min-width: 68px; }
.notice { margin-bottom: 0; }
.trace-table, .stage-table, .source-table { width: 100%; }
.trace-id { display: inline-block; max-width: 132px; overflow: hidden; text-overflow: ellipsis; vertical-align: middle; white-space: nowrap; font-family: monospace; }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 18px; }
.trace-detail { min-height: 160px; padding: 0 4px 24px; }
.detail-section { margin-bottom: 26px; }
.detail-section h3 { margin: 0 0 12px; color: #1f2d3d; font-size: 16px; }
.json-block { max-height: 280px; margin: 0; padding: 14px; overflow: auto; border-radius: 8px; background: #f5f7fa; color: #334155; font-family: Consolas, Monaco, monospace; font-size: 12px; line-height: 1.55; white-space: pre-wrap; word-break: break-word; }
.json-block.compact { max-height: 190px; margin-top: 8px; }
.stage-expand { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; padding: 8px 12px; }
.breakable { word-break: break-all; }
.error-text { color: #d03050; }
.source-summary { white-space: normal; line-height: 1.5; }
@media (max-width: 900px) { .time-range :deep(.el-date-editor) { width: 100%; } .stage-expand { grid-template-columns: 1fr; } .filter-actions { margin-left: 0; } }
</style>
