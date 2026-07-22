<template>
  <AdminLayout title="AI功能监控" subtitle="实时监控AI服务运行状态">
    <section class="filter-section">
      <div class="filter-row">
        <div class="filter-item">
          <label>功能类型</label>
          <select v-model="filters.functionType" class="filter-select">
            <option value="">全部</option>
            <option value="RECOMMENDATION">探店推荐</option>
            <option value="SEARCH">语义搜索</option>
            <option value="SENTIMENT">情感分析</option>
            <option value="SUMMARY">评价摘要</option>
            <option value="REPLY">回复生成</option>
          </select>
        </div>
        <div class="filter-item">
          <label>模型名称</label>
          <select v-model="filters.model" class="filter-select">
            <option value="">全部</option>
            <option value="gpt-4o">GPT-4o</option>
            <option value="gpt-4">GPT-4</option>
            <option value="gpt-3.5">GPT-3.5 Turbo</option>
            <option value="qwen-plus">通义千问 Plus</option>
          </select>
        </div>
        <div class="filter-item">
          <label>时间范围</label>
          <select v-model="filters.timeRange" class="filter-select">
            <option value="1h">最近1小时</option>
            <option value="24h">最近24小时</option>
            <option value="7d">最近7天</option>
            <option value="30d">最近30天</option>
          </select>
        </div>
        <div class="filter-actions">
          <button class="btn btn-primary" @click="loadData">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"/><path d="M3 3v5h5"/><path d="M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16"/><path d="M16 21h5v-5"/>
            </svg>
            刷新
          </button>
        </div>
      </div>
    </section>

    <section class="stats-section">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon stat-icon-blue">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"></path>
            </svg>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ totalStats.totalCalls.toLocaleString() }}</span>
            <span class="stat-label">调用总数</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon stat-icon-green">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 12l2 2 4-4"></path>
              <circle cx="12" cy="12" r="10"></circle>
            </svg>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ totalStats.successCalls.toLocaleString() }}</span>
            <span class="stat-label">成功次数</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon stat-icon-red">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="15" y1="9" x2="9" y2="15"></line>
              <line x1="9" y1="9" x2="15" y2="15"></line>
            </svg>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ totalStats.failureCalls.toLocaleString() }}</span>
            <span class="stat-label">失败次数</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon stat-icon-orange">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
            </svg>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ totalStats.successRate }}%</span>
            <span class="stat-label">成功率</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon stat-icon-purple">
            <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ totalStats.avgResponseTime }}ms</span>
            <span class="stat-label">平均响应时间</span>
          </div>
        </div>
      </div>
    </section>

    <section class="charts-section">
      <div class="chart-card">
        <h3 class="chart-title">各功能调用统计</h3>
        <div class="function-stats">
          <div 
            v-for="item in functionStats" 
            :key="item.functionType" 
            class="function-bar-item"
          >
            <div class="function-info">
              <span class="function-icon">{{ getFunctionIcon(item.functionType) }}</span>
              <span class="function-name">{{ getFunctionName(item.functionType) }}</span>
            </div>
            <div class="function-bar-container">
              <div 
                class="function-bar" 
                :style="{ width: getBarWidth(item.totalCalls) + '%' }"
              >
                <span class="bar-value">{{ item.totalCalls }}</span>
              </div>
            </div>
            <div class="function-meta">
              <span class="meta-item success">成功: {{ item.successCalls }}</span>
              <span class="meta-item failure">失败: {{ item.failureCalls }}</span>
              <span class="meta-item rate">{{ item.successRate }}%</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="failure-section">
      <div class="section-header">
        <h3 class="section-title">失败请求记录</h3>
        <span class="section-count">共 {{ failureLogs.length }} 条</span>
      </div>

      <div v-if="failureLogs.length > 0" class="failure-table">
        <table>
          <thead>
            <tr>
              <th>追踪编号</th>
              <th>功能类型</th>
              <th>模型名称</th>
              <th>错误类型</th>
              <th>发生时间</th>
              <th>错误信息</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in failureLogs" :key="log.traceId">
              <td class="trace-id">{{ log.traceId }}</td>
              <td>{{ getFunctionName(log.functionType) }}</td>
              <td>{{ log.model }}</td>
              <td :class="['error-type', getErrorClass(log.errorType)]">{{ log.errorType }}</td>
              <td>{{ formatTime(log.createdAt) }}</td>
              <td class="error-message">{{ log.errorMessage }}</td>
              <td>
                <button class="detail-btn" @click="showLogDetail(log)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-else class="empty-state">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"></circle>
          <path d="M9 12l2 2 4-4"></path>
        </svg>
        <p>暂无失败请求记录</p>
        <span class="empty-hint">所有AI功能运行正常</span>
      </div>
    </section>

    <div v-if="showDetailModal" class="modal-overlay" @click.self="showDetailModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h3>错误详情</h3>
          <button class="modal-close" @click="showDetailModal = false">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <div class="modal-body" v-if="selectedLog">
          <div class="detail-row">
            <span class="detail-label">追踪编号</span>
            <span class="detail-value">{{ selectedLog.traceId }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">功能类型</span>
            <span class="detail-value">{{ getFunctionName(selectedLog.functionType) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">模型名称</span>
            <span class="detail-value">{{ selectedLog.model }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">错误类型</span>
            <span :class="['detail-value', 'error', getErrorClass(selectedLog.errorType)]">{{ selectedLog.errorType }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">发生时间</span>
            <span class="detail-value">{{ formatTime(selectedLog.createdAt) }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">错误信息</span>
            <span class="detail-value error-message">{{ selectedLog.errorMessage }}</span>
          </div>
          <div class="detail-row">
            <span class="detail-label">响应时间</span>
            <span class="detail-value">{{ selectedLog.responseTime }}ms</span>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="showDetailModal = false">关闭</button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getAiMonitorStats, getAiFailureLogs } from '../../api/aiMonitor'

const filters = reactive({
  functionType: '',
  model: '',
  timeRange: '24h'
})

const totalStats = ref({
  totalCalls: 0,
  successCalls: 0,
  failureCalls: 0,
  successRate: 0,
  avgResponseTime: 0
})

const functionStats = ref([])
const failureLogs = ref([])

const showDetailModal = ref(false)
const selectedLog = ref(null)

const getFunctionName = (type) => {
  const names = {
    RECOMMENDATION: '探店推荐',
    SEARCH: '语义搜索',
    SENTIMENT: '情感分析',
    SUMMARY: '评价摘要',
    REPLY: '回复生成'
  }
  return names[type] || type
}

const getFunctionIcon = (type) => {
  const icons = {
    RECOMMENDATION: '🍽️',
    SEARCH: '🔍',
    SENTIMENT: '😀',
    SUMMARY: '📝',
    REPLY: '💬'
  }
  return icons[type] || '🤖'
}

const getErrorClass = (type) => {
  if (type.includes('Timeout')) return 'error-timeout'
  if (type.includes('Quota')) return 'error-quota'
  if (type.includes('RateLimit')) return 'error-rate-limit'
  return 'error-other'
}

const getBarWidth = (value) => {
  const max = Math.max(...functionStats.value.map(s => s.totalCalls), 1)
  return (value / max) * 100
}

const formatTime = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const showLogDetail = (log) => {
  selectedLog.value = log
  showDetailModal.value = true
}

const loadData = async () => {
  try {
    const statsResponse = await getAiMonitorStats(filters)
    if (statsResponse.success) {
      totalStats.value = statsResponse.data.totalStats || {
        totalCalls: 0,
        successCalls: 0,
        failureCalls: 0,
        successRate: 0,
        avgResponseTime: 0
      }
      functionStats.value = statsResponse.data.functionStats || []
    }

    const logsResponse = await getAiFailureLogs(filters)
    if (logsResponse.success) {
      failureLogs.value = logsResponse.data || []
    }
  } catch (error) {
    console.error('加载AI监控数据失败：', error)
  }
}

loadData()
</script>

<style scoped>
.filter-section {
  margin-bottom: 24px;
  padding: 20px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(24, 144, 255, 0.15);
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 24px;
}

.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-item label {
  font-size: 13px;
  color: #667085;
  font-weight: 500;
}

.filter-select {
  padding: 10px 14px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 14px;
  color: #333;
  background: #fff;
  min-width: 160px;
  cursor: pointer;
}

.filter-select:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

.filter-actions {
  margin-left: auto;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.btn-primary {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff;
}

.btn-primary:hover {
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

.btn-secondary {
  background: #f5f5f5;
  color: #666;
  border: 1px solid #d9d9d9;
}

.btn-secondary:hover {
  background: #e8e8e8;
}

.stats-section {
  margin-bottom: 24px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(24, 144, 255, 0.15);
  box-shadow: 0 4px 20px rgba(24, 144, 255, 0.08);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon-blue {
  color: #1890ff;
  background: linear-gradient(135deg, rgba(24, 144, 255, 0.2) 0%, rgba(64, 169, 255, 0.1) 100%);
}

.stat-icon-green {
  color: #52c41a;
  background: linear-gradient(135deg, rgba(82, 196, 26, 0.2) 0%, rgba(115, 209, 61, 0.1) 100%);
}

.stat-icon-red {
  color: #ff4d4f;
  background: linear-gradient(135deg, rgba(255, 77, 79, 0.2) 0%, rgba(255, 117, 117, 0.1) 100%);
}

.stat-icon-orange {
  color: #ff6700;
  background: linear-gradient(135deg, rgba(255, 103, 0, 0.2) 0%, rgba(255, 149, 0, 0.1) 100%);
}

.stat-icon-purple {
  color: #722ed1;
  background: linear-gradient(135deg, rgba(114, 46, 209, 0.2) 0%, rgba(146, 84, 222, 0.1) 100%);
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2d3d;
}

.stat-label {
  font-size: 13px;
  color: #667085;
}

.charts-section {
  margin-bottom: 24px;
}

.chart-card {
  padding: 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(24, 144, 255, 0.15);
  box-shadow: 0 4px 20px rgba(24, 144, 255, 0.08);
}

.chart-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 20px;
}

.function-stats {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.function-bar-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.function-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.function-icon {
  font-size: 20px;
}

.function-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.function-bar-container {
  height: 24px;
  background: #f5f5f5;
  border-radius: 12px;
  overflow: hidden;
}

.function-bar {
  height: 100%;
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  padding-left: 12px;
  transition: width 0.3s ease;
}

.bar-value {
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.function-meta {
  display: flex;
  gap: 16px;
}

.meta-item {
  font-size: 12px;
}

.meta-item.success {
  color: #52c41a;
}

.meta-item.failure {
  color: #ff4d4f;
}

.meta-item.rate {
  color: #ff6700;
}

.failure-section {
  padding: 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  border-radius: 16px;
  border: 1px solid rgba(24, 144, 255, 0.15);
  box-shadow: 0 4px 20px rgba(24, 144, 255, 0.08);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.section-count {
  font-size: 13px;
  color: #999;
  padding: 4px 12px;
  background: #f5f5f5;
  border-radius: 20px;
}

.failure-table {
  overflow-x: auto;
}

.failure-table table {
  width: 100%;
  border-collapse: collapse;
}

.failure-table th,
.failure-table td {
  padding: 14px 16px;
  text-align: left;
  font-size: 14px;
  border-bottom: 1px solid #f0f0f0;
}

.failure-table th {
  background: #fafafa;
  font-weight: 600;
  color: #667085;
}

.failure-table tr:hover {
  background: rgba(24, 144, 255, 0.03);
}

.trace-id {
  font-family: monospace;
  font-size: 13px;
  color: #1890ff;
  font-weight: 500;
}

.error-type {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.error-timeout {
  color: #ff4d4f;
  background: rgba(255, 77, 79, 0.1);
}

.error-quota {
  color: #ffb100;
  background: rgba(255, 177, 0, 0.1);
}

.error-rate-limit {
  color: #722ed1;
  background: rgba(114, 46, 209, 0.1);
}

.error-other {
  color: #666;
  background: #f5f5f5;
}

.error-message {
  color: #ff4d4f;
  font-size: 13px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-btn {
  padding: 6px 14px;
  background: #1890ff;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.detail-btn:hover {
  background: #40a9ff;
}

.empty-state {
  padding: 48px;
  text-align: center;
  background: rgba(82, 196, 26, 0.03);
  border-radius: 12px;
  border: 1px dashed rgba(82, 196, 26, 0.2);
}

.empty-state p {
  margin: 16px 0 8px;
  font-size: 16px;
  color: #52c41a;
  font-weight: 500;
}

.empty-hint {
  font-size: 13px;
  color: #999;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  width: 500px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.modal-close {
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s;
}

.modal-close:hover {
  color: #666;
  background: #f5f5f5;
}

.modal-body {
  padding: 24px;
}

.detail-row {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-label {
  width: 100px;
  font-size: 14px;
  color: #667085;
  font-weight: 500;
}

.detail-value {
  flex: 1;
  font-size: 14px;
  color: #333;
}

.detail-value.error {
  font-weight: 500;
}

.detail-value.error-message {
  color: #ff4d4f;
  word-break: break-word;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  
  .filter-row {
    flex-wrap: wrap;
  }
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .filter-select {
    min-width: 140px;
  }
  
  .failure-table {
    font-size: 12px;
  }
}
</style>