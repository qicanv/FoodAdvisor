<template>
  <AdminLayout title="举报审核" subtitle="处理用户提交的评价举报">
    <div class="reports-container">
      <!-- 筛选栏 -->
      <div class="search-bar">
        <div class="search-item">
          <select v-model="filterStatus" class="search-select" @change="loadReports">
            <option value="">全部状态</option>
            <option value="PENDING">待处理</option>
            <option value="RESOLVED">已处理</option>
            <option value="REJECTED">已驳回</option>
          </select>
        </div>
        <div class="search-item">
          <select v-model="filterReason" class="search-select" @change="loadReports">
            <option value="">全部原因</option>
            <option value="ADVERTISING">广告引流</option>
            <option value="FALSE_REVIEW">虚假评价</option>
            <option value="MALICIOUS_ATTACK">恶意攻击</option>
            <option value="SEXUAL_OR_VULGAR">色情低俗</option>
            <option value="PRIVACY_LEAK">泄露隐私</option>
            <option value="OTHER">其他</option>
          </select>
        </div>
        <button class="search-btn" @click="loadReports">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="M21 21l-4.35-4.35"></path>
          </svg>
          <span>筛选</span>
        </button>
      </div>

      <!-- 表格 -->
      <div class="table-container">
        <table class="reports-table">
          <thead>
            <tr>
              <th>举报时间</th>
              <th>举报人</th>
              <th>商家</th>
              <th>举报原因</th>
              <th>评价内容</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="report in reports" :key="report.id">
              <td>{{ formatTime(report.createdAt) }}</td>
              <td>{{ report.reporterUsername }}</td>
              <td>{{ report.merchantName }}</td>
              <td><span class="reason-tag">{{ report.reasonText }}</span></td>
              <td class="review-cell">
                <div class="review-preview">
                  <span v-if="report.reviewRating" class="review-stars">
                    {{ '⭐'.repeat(report.reviewRating) }}
                  </span>
                  <span v-if="report.reviewStatus === 'DELETED'" class="deleted-badge">已删除</span>
                  <p class="review-text">{{ truncateText(report.reviewContent, 60) }}</p>
                </div>
              </td>
              <td><span :class="['status-tag', report.status]">{{ report.statusText }}</span></td>
              <td>
                <button
                  v-if="report.status === 'PENDING'"
                  class="action-btn resolve-btn"
                  @click="openResolveDialog(report)"
                >
                  处理
                </button>
                <button
                  v-else
                  class="action-btn view-btn"
                  @click="openResolveDialog(report)"
                >
                  查看
                </button>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="reports.length === 0" class="empty-state">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="1.5">
            <path d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
          </svg>
          <p>暂无举报记录</p>
        </div>
      </div>

      <!-- 分页 -->
      <div class="pagination" v-if="total > pageSize">
        <button class="pagination-btn" :disabled="pageNum <= 1" @click="pageNum--; loadReports()">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 19l-7-7 7-7"></path>
          </svg>
        </button>
        <span class="pagination-info">第 {{ pageNum }} / {{ totalPages }} 页</span>
        <button class="pagination-btn" :disabled="pageNum >= totalPages" @click="pageNum++; loadReports()">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 5l7 7-7 7"></path>
          </svg>
        </button>
      </div>
    </div>

    <!-- 处理弹窗 -->
    <div v-if="resolveDialogOpen" class="modal-mask" @click.self="closeResolveDialog">
      <div class="modal-dialog" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h2>{{ resolvingReport.status === 'PENDING' ? '处理举报' : '举报详情' }}</h2>
          <button class="modal-close" @click="closeResolveDialog">✕</button>
        </div>
        <div class="modal-body">
          <div class="detail-section">
            <div class="detail-row">
              <span class="detail-label">举报人</span>
              <span class="detail-value">{{ resolvingReport.reporterUsername }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">商家</span>
              <span class="detail-value">{{ resolvingReport.merchantName }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">举报原因</span>
              <span class="detail-value">{{ resolvingReport.reasonText }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">举报说明</span>
              <span class="detail-value">{{ resolvingReport.description || '（无补充说明）' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">被举报评价</span>
              <div class="review-block">
                <span v-if="resolvingReport.reviewRating" class="review-stars">
                  {{ '⭐'.repeat(resolvingReport.reviewRating) }}
                </span>
                <span v-if="resolvingReport.reviewStatus === 'DELETED'" class="deleted-badge">已删除</span>
                <p class="review-full">{{ resolvingReport.reviewContent }}</p>
              </div>
            </div>
            <div class="detail-row" v-if="resolvingReport.resolution">
              <span class="detail-label">处理结果</span>
              <span class="detail-value resolution-text">{{ resolvingReport.resolution }}</span>
            </div>
          </div>

          <div v-if="resolvingReport.status === 'PENDING'" class="resolve-section">
            <h3>处理操作</h3>
            <div class="resolve-field">
              <label class="resolve-label">处理结论 <span class="required">*</span></label>
              <div class="resolve-options">
                <button
                  class="resolve-option-btn"
                  :class="{ active: resolveForm.status === 'RESOLVED' }"
                  @click="resolveForm.status = 'RESOLVED'"
                >
                  ✓ 通过（标记已处理）
                </button>
                <button
                  class="resolve-option-btn"
                  :class="{ active: resolveForm.status === 'REJECTED' }"
                  @click="resolveForm.status = 'REJECTED'"
                >
                  ✕ 驳回
                </button>
              </div>
            </div>
            <div class="resolve-field">
              <label class="resolve-label">处理说明</label>
              <textarea
                v-model="resolveForm.resolution"
                class="resolve-textarea"
                rows="3"
                placeholder="选填，填写处理备注"
              ></textarea>
            </div>
            <div v-if="resolveError" class="error-msg">{{ resolveError }}</div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="modal-btn cancel" @click="closeResolveDialog">关闭</button>
          <button
            v-if="resolvingReport.status === 'PENDING'"
            class="modal-btn submit"
            :disabled="!resolveForm.status || resolveSubmitting"
            @click="submitResolve"
          >
            {{ resolveSubmitting ? '提交中...' : '确认处理' }}
          </button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import request from '../../api/request'
import AdminLayout from '../../components/AdminLayout.vue'

const reports = ref([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const filterStatus = ref('')
const filterReason = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const loadReports = async () => {
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filterStatus.value) params.status = filterStatus.value
    if (filterReason.value) params.reason = filterReason.value

    const response = await request.get('/api/admin/reports', { params })
    if (response.success && response.data) {
      reports.value = response.data.records || []
      total.value = response.data.total || 0
    } else {
      reports.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('获取举报列表失败:', error)
    reports.value = []
    total.value = 0
  }
}

// ---- 处理弹窗 ----
const resolveDialogOpen = ref(false)
const resolveSubmitting = ref(false)
const resolveError = ref('')
const resolvingReport = ref({})

const resolveForm = reactive({
  status: '',
  resolution: ''
})

const openResolveDialog = (report) => {
  resolvingReport.value = { ...report }
  resolveForm.status = ''
  resolveForm.resolution = ''
  resolveError.value = ''
  resolveDialogOpen.value = true
}

const closeResolveDialog = () => {
  if (resolveSubmitting.value) return
  resolveDialogOpen.value = false
}

const submitResolve = async () => {
  if (!resolveForm.status) {
    resolveError.value = '请选择处理结论'
    return
  }
  resolveError.value = ''
  resolveSubmitting.value = true

  try {
    const response = await request.put(
      `/api/admin/reports/${resolvingReport.value.id}/resolve`,
      {
        status: resolveForm.status,
        resolution: resolveForm.resolution || undefined
      }
    )
    if (response.success) {
      resolveSubmitting.value = false
      closeResolveDialog()
      await loadReports()
    } else {
      resolveSubmitting.value = false
      resolveError.value = response.message || '处理失败'
    }
  } catch (error) {
    resolveError.value = '处理失败，请稍后重试'
  } finally {
    resolveSubmitting.value = false
  }
}

// ---- 工具函数 ----
const formatTime = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const truncateText = (text, maxLen) => {
  if (!text) return '-'
  return text.length > maxLen ? text.substring(0, maxLen) + '…' : text
}

onMounted(() => {
  loadReports()
})
</script>

<style scoped>
.reports-container {
  max-width: 1400px;
}

.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.search-input {
  padding: 10px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 14px;
  width: 220px;
}

.search-select {
  padding: 10px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 14px;
  background: #fff;
  min-width: 140px;
}

.search-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 20px;
  background: #1890ff;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.search-btn:hover {
  background: #40a9ff;
}

.table-container {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow-x: auto;
}

.reports-table {
  width: 100%;
  border-collapse: collapse;
}

.reports-table th,
.reports-table td {
  padding: 14px 16px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
  font-size: 14px;
}

.reports-table th {
  background: #fafafa;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
}

.reports-table tbody tr:hover {
  background: #f5f7fa;
}

.reason-tag {
  padding: 4px 10px;
  background: #fff5f0;
  color: #ff6700;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.review-cell {
  max-width: 300px;
}

.review-preview {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.review-stars {
  font-size: 13px;
}

.review-text {
  margin: 0;
  font-size: 13px;
  color: #666;
  line-height: 1.4;
}

.deleted-badge {
  display: inline-block;
  padding: 2px 8px;
  background: #fff2f0;
  color: #ff4d4f;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 500;
}

.status-tag {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.status-tag.PENDING {
  background: #fffbe6;
  color: #faad14;
}

.status-tag.RESOLVED {
  background: #f6ffed;
  color: #52c41a;
}

.status-tag.REJECTED {
  background: #fff2f0;
  color: #ff4d4f;
}

.action-btn {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.resolve-btn {
  background: #1890ff;
  color: #fff;
}

.resolve-btn:hover {
  background: #40a9ff;
}

.view-btn {
  background: #f5f5f5;
  color: #666;
}

.view-btn:hover {
  background: #e8e8e8;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.empty-state p {
  color: #999;
  margin-top: 12px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
}

.pagination-btn {
  padding: 8px 14px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.pagination-btn:hover:not(:disabled) {
  border-color: #1890ff;
  color: #1890ff;
}

.pagination-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination-info {
  font-size: 14px;
  color: #666;
}

/* 处理弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-dialog {
  width: min(640px, 90vw);
  max-height: 85vh;
  overflow-y: auto;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.2);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h2 {
  font-size: 18px;
  font-weight: 700;
  color: #1f2d3d;
  margin: 0;
}

.modal-close {
  padding: 4px 10px;
  background: none;
  border: none;
  font-size: 20px;
  color: #999;
  cursor: pointer;
}

.modal-close:hover {
  color: #333;
}

.modal-body {
  padding: 24px;
}

.detail-section {
  margin-bottom: 20px;
}

.detail-row {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
  align-items: flex-start;
}

.detail-label {
  min-width: 80px;
  font-size: 14px;
  font-weight: 600;
  color: #666;
  flex-shrink: 0;
}

.detail-value {
  font-size: 14px;
  color: #333;
  flex: 1;
}

.resolution-text {
  color: #1890ff;
  font-weight: 500;
}

.review-block {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.review-full {
  margin: 0;
  font-size: 14px;
  color: #333;
  line-height: 1.6;
  padding: 10px 12px;
  background: #f9fafb;
  border-radius: 6px;
}

.resolve-section {
  border-top: 1px solid #f0f0f0;
  padding-top: 20px;
}

.resolve-section h3 {
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 16px 0;
}

.resolve-field {
  margin-bottom: 16px;
}

.resolve-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin-bottom: 8px;
}

.required {
  color: #ff4d4f;
}

.resolve-options {
  display: flex;
  gap: 12px;
}

.resolve-option-btn {
  flex: 1;
  padding: 12px 16px;
  background: #f5f5f5;
  border: 2px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.resolve-option-btn:hover {
  border-color: #1890ff;
}

.resolve-option-btn.active {
  background: #e6f7ff;
  border-color: #1890ff;
  color: #1890ff;
}

.resolve-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font: inherit;
  font-size: 14px;
  resize: vertical;
}

.error-msg {
  padding: 10px 12px;
  background: #fef0f0;
  color: #f56c6c;
  border-radius: 6px;
  font-size: 13px;
  margin-top: 8px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
}

.modal-btn {
  padding: 10px 24px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}

.modal-btn.cancel {
  background: #f5f5f5;
  color: #666;
}

.modal-btn.cancel:hover {
  background: #e8e8e8;
}

.modal-btn.submit {
  background: #1890ff;
  color: #fff;
}

.modal-btn.submit:hover:not(:disabled) {
  background: #40a9ff;
}

.modal-btn.submit:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .reports-table {
    font-size: 12px;
  }
  .reports-table th,
  .reports-table td {
    padding: 10px 8px;
  }
}
</style>
