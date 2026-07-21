<template>
  <AdminLayout title="内容审核工作台" subtitle="集中查看和处理待审核内容，提高审核效率">
    <div class="filter-section">
      <div class="filter-row">
        <div class="filter-item">
          <label>风险等级</label>
          <select v-model="filters.riskLevel" class="filter-select" @change="loadReviewList">
            <option value="">全部</option>
            <option value="HIGH">高风险</option>
            <option value="MEDIUM">中风险</option>
            <option value="LOW">低风险</option>
          </select>
        </div>
        <div class="filter-item">
          <label>处理状态</label>
          <select v-model="filters.moderationStatus" class="filter-select" @change="loadReviewList">
            <option value="">全部</option>
            <option value="PENDING">待审核</option>
            <option value="APPROVED">已通过</option>
            <option value="REJECTED">已驳回</option>
          </select>
        </div>
        <div class="filter-item">
          <label>商家</label>
          <select v-model="filters.merchantId" class="filter-select" @change="loadReviewList">
            <option value="">全部商家</option>
            <option v-for="merchant in merchants" :key="merchant.id" :value="merchant.id">
              {{ merchant.name }}
            </option>
          </select>
        </div>
        <div class="filter-item">
          <label>开始时间</label>
          <input type="datetime-local" v-model="filters.startTime" class="filter-input" @change="loadReviewList" />
        </div>
        <div class="filter-item">
          <label>结束时间</label>
          <input type="datetime-local" v-model="filters.endTime" class="filter-input" @change="loadReviewList" />
        </div>
        <div class="filter-item filter-actions">
          <button class="btn-reset" @click="resetFilters">重置</button>
          <button class="btn-search" @click="loadReviewList">查询</button>
        </div>
      </div>
    </div>

    <div class="stats-cards">
      <div class="stat-card stat-card-pending">
        <div class="stat-icon">⏳</div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.pending }}</div>
          <div class="stat-label">待审核</div>
        </div>
      </div>
      <div class="stat-card stat-card-high">
        <div class="stat-icon">🔴</div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.highRisk }}</div>
          <div class="stat-label">高风险</div>
        </div>
      </div>
      <div class="stat-card stat-card-medium">
        <div class="stat-icon">🟡</div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.mediumRisk }}</div>
          <div class="stat-label">中风险</div>
        </div>
      </div>
      <div class="stat-card stat-card-low">
        <div class="stat-icon">🟢</div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.lowRisk }}</div>
          <div class="stat-label">低风险</div>
        </div>
      </div>
    </div>

    <div class="table-section">
      <div class="table-header">
        <h2 class="section-title">📋 待审核内容列表</h2>
        <span class="table-count">共 {{ pagination.total }} 条记录</span>
      </div>

      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>内容摘要</th>
              <th>评分</th>
              <th>风险等级</th>
              <th>审核状态</th>
              <th>商家</th>
              <th>用户</th>
              <th>提交时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in reviewList" :key="item.id" class="table-row" @click="showDetail(item)">
              <td class="cell-id">#{{ item.id }}</td>
              <td class="cell-content">{{ truncateContent(item.content) }}</td>
              <td class="cell-rating">
                <span class="rating-star" v-for="n in 5" :key="n">
                  {{ n <= Math.round(item.rating || 0) ? '★' : '☆' }}
                </span>
              </td>
              <td>
                <span :class="['risk-badge', item.riskLevel?.toLowerCase()]">
                  {{ getRiskLevelText(item.riskLevel) }}
                </span>
              </td>
              <td>
                <span :class="['status-badge', item.moderationStatus?.toLowerCase()]">
                  {{ getModerationStatusText(item.moderationStatus) }}
                </span>
              </td>
              <td class="cell-merchant">{{ item.merchantName || '-' }}</td>
              <td class="cell-user">{{ item.userNickname || item.username || '-' }}</td>
              <td class="cell-time">{{ formatTime(item.createdAt) }}</td>
              <td class="cell-actions">
                <button class="action-btn btn-detail" @click.stop="showDetail(item)">详情</button>
                <button v-if="item.moderationStatus === 'PENDING'" class="action-btn btn-approve" @click.stop="handleAction(item, 'APPROVE')">通过</button>
                <button v-if="item.moderationStatus === 'PENDING'" class="action-btn btn-reject" @click.stop="handleAction(item, 'REJECT')">驳回</button>
                <button v-if="item.moderationStatus === 'PENDING'" class="action-btn btn-delete" @click.stop="handleAction(item, 'DELETE')">删除</button>
              </td>
            </tr>
            <tr v-if="reviewList.length === 0" class="empty-row">
              <td colspan="9" class="empty-cell">暂无数据</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="pagination.total > 0" class="pagination-section">
        <button class="page-btn" :disabled="pagination.pageNum <= 1" @click="changePage(pagination.pageNum - 1)">上一页</button>
        <span class="page-info">第 {{ pagination.pageNum }} / {{ pagination.totalPages }} 页</span>
        <button class="page-btn" :disabled="pagination.pageNum >= pagination.totalPages" @click="changePage(pagination.pageNum + 1)">下一页</button>
      </div>
    </div>

    <div v-if="showDetailModal" class="modal-overlay" @click="closeDetail">
      <div class="modal-container" @click.stop>
        <div class="modal-header">
          <h3>评价详情</h3>
          <button class="modal-close" @click="closeDetail">×</button>
        </div>
        <div class="modal-body">
          <div v-if="detailLoading" class="loading-state">加载中...</div>
          <div v-else-if="currentDetail" class="detail-content">
            <div class="detail-row">
              <span class="detail-label">风险等级</span>
              <span :class="['risk-badge', currentDetail.riskLevel?.toLowerCase()]">
                {{ getRiskLevelText(currentDetail.riskLevel) }}
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-label">审核状态</span>
              <span :class="['status-badge', currentDetail.moderationStatus?.toLowerCase()]">
                {{ getModerationStatusText(currentDetail.moderationStatus) }}
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-label">内容状态</span>
              <span>{{ getStatusText(currentDetail.status) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">商家名称</span>
              <span>{{ currentDetail.merchantName || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">商家分类</span>
              <span>{{ currentDetail.category || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">菜系</span>
              <span>{{ currentDetail.cuisine || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">商家地址</span>
              <span>{{ currentDetail.address || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">区域</span>
              <span>{{ currentDetail.regionCode || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">用户昵称</span>
              <span>{{ currentDetail.userNickname || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">用户名</span>
              <span>{{ currentDetail.username || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">用户电话</span>
              <span>{{ currentDetail.phone || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">用户邮箱</span>
              <span>{{ currentDetail.email || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">评价类型</span>
              <span>{{ getReviewTypeText(currentDetail.reviewType) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">评分</span>
              <div class="rating-display">
                <span class="rating-star" v-for="n in 5" :key="n">
                  {{ n <= Math.round(currentDetail.rating || 0) ? '★' : '☆' }}
                </span>
                <span class="rating-value">{{ currentDetail.rating || 0 }}</span>
              </div>
            </div>
            <div class="detail-row">
              <span class="detail-label">口味评分</span>
              <span>{{ currentDetail.tasteRating || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">环境评分</span>
              <span>{{ currentDetail.environmentRating || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">服务评分</span>
              <span>{{ currentDetail.serviceRating || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">人均消费</span>
              <span>{{ currentDetail.averageSpend ? '￥' + currentDetail.averageSpend : '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">消费日期</span>
              <span>{{ currentDetail.consumptionDate || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">评价来源</span>
              <span>{{ getSourceText(currentDetail.source) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">评价时间</span>
              <span>{{ formatTime(currentDetail.reviewTime) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">提交时间</span>
              <span>{{ formatTime(currentDetail.createdAt) }}</span>
            </div>
            <div class="detail-row detail-content-full">
              <span class="detail-label">原始内容</span>
              <div class="content-box">{{ currentDetail.content || '-' }}</div>
            </div>
            <div class="detail-row detail-content-full">
              <span class="detail-label">触发规则</span>
              <div class="content-box">
                <div v-if="currentDetail.riskLevel" class="rule-item">
                  <span class="rule-icon">⚠️</span>
                  <span class="rule-text">风险等级检测：{{ getRiskLevelText(currentDetail.riskLevel) }}</span>
                </div>
                <div v-if="currentDetail.riskLevel === 'HIGH'" class="rule-item">
                  <span class="rule-icon">🔍</span>
                  <span class="rule-text">内容包含敏感词或疑似违规内容</span>
                </div>
                <div v-if="!currentDetail.riskLevel" class="rule-item">
                  <span class="rule-icon">✅</span>
                  <span class="rule-text">系统自动检测未触发风险规则</span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="currentDetail && currentDetail.moderationStatus === 'PENDING'" class="modal-footer">
          <textarea v-model="reviewRemark" class="remark-input" placeholder="请输入审核备注（可选）" rows="3"></textarea>
          <div class="footer-actions">
            <button class="footer-btn btn-approve" @click="handleAction(currentDetail, 'APPROVE')">通过</button>
            <button class="footer-btn btn-reject" @click="handleAction(currentDetail, 'REJECT')">驳回</button>
            <button class="footer-btn btn-delete" @click="handleAction(currentDetail, 'DELETE')">删除</button>
            <button class="footer-btn btn-return" @click="handleAction(currentDetail, 'RETURN_FOR_MODIFICATION')">退回修改</button>
            <button class="footer-btn btn-cancel" @click="closeDetail">取消</button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="showActionModal" class="modal-overlay" @click="closeActionModal">
      <div class="modal-container action-modal" @click.stop>
        <div class="modal-header">
          <h3>{{ getActionTitle(actionType) }}</h3>
          <button class="modal-close" @click="closeActionModal">×</button>
        </div>
        <div class="modal-body">
          <p class="action-desc">{{ getActionDesc(actionType) }}</p>
          <textarea v-model="actionRemark" class="remark-input" placeholder="请输入审核备注（可选）" rows="3"></textarea>
        </div>
        <div class="modal-footer">
          <button class="footer-btn btn-cancel" @click="closeActionModal">取消</button>
          <button :class="['footer-btn', 'btn-confirm', 'btn-' + actionType.toLowerCase()]" @click="confirmAction">确认{{ getActionText(actionType) }}</button>
        </div>
      </div>
    </div>

    <div v-if="showMessage" class="message-toast" :class="messageType">
      {{ messageText }}
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getReviewList, getReviewDetail, getActiveMerchants, getPendingCount, moderateReview } from '../../api/moderation'

const reviewList = ref([])
const merchants = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const currentDetail = ref(null)
const showDetailModal = ref(false)
const showActionModal = ref(false)
const showMessage = ref(false)
const messageText = ref('')
const messageType = ref('success')
const reviewRemark = ref('')
const actionRemark = ref('')
const actionType = ref('')
const actionTarget = ref(null)

const filters = reactive({
  riskLevel: '',
  moderationStatus: '',
  merchantId: '',
  startTime: '',
  endTime: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 20,
  total: 0,
  totalPages: 0
})

const stats = reactive({
  pending: 0,
  highRisk: 0,
  mediumRisk: 0,
  lowRisk: 0
})

const truncateContent = (content) => {
  if (!content) return '-'
  return content.length > 50 ? content.substring(0, 50) + '...' : content
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getRiskLevelText = (level) => {
  const map = { HIGH: '高风险', MEDIUM: '中风险', LOW: '低风险' }
  return map[level] || '未知'
}

const getModerationStatusText = (status) => {
  const map = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
  return map[status] || '未知'
}

const getStatusText = (status) => {
  const map = { PENDING: '待发布', PUBLISHED: '已发布', HIDDEN: '已隐藏', DELETED: '已删除' }
  return map[status] || '未知'
}

const getReviewTypeText = (type) => {
  const map = { ORIGINAL: '原评价', FOLLOW_UP: '追评' }
  return map[type] || '未知'
}

const getSourceText = (source) => {
  const map = { SYSTEM: '系统录入', IMPORT: '外部导入' }
  return map[source] || '未知'
}

const getActionTitle = (type) => {
  const map = { APPROVE: '确认通过', REJECT: '确认驳回', DELETE: '确认删除', RETURN_FOR_MODIFICATION: '确认退回修改' }
  return map[type] || '确认操作'
}

const getActionDesc = (type) => {
  const map = {
    APPROVE: '通过后该评价将进入已发布状态，用户可正常查看。',
    REJECT: '驳回后该评价将被隐藏，用户不可查看。',
    DELETE: '删除后该评价将被永久移除，请谨慎操作。',
    RETURN_FOR_MODIFICATION: '退回后用户可修改评价内容后重新提交。'
  }
  return map[type] || ''
}

const getActionText = (type) => {
  const map = { APPROVE: '通过', REJECT: '驳回', DELETE: '删除', RETURN_FOR_MODIFICATION: '退回修改' }
  return map[type] || '操作'
}

const showToast = (text, type = 'success') => {
  messageText.value = text
  messageType.value = type
  showMessage.value = true
  setTimeout(() => {
    showMessage.value = false
  }, 3000)
}

const resetFilters = () => {
  filters.riskLevel = ''
  filters.moderationStatus = ''
  filters.merchantId = ''
  filters.startTime = ''
  filters.endTime = ''
  pagination.pageNum = 1
  loadReviewList()
}

const loadReviewList = async () => {
  loading.value = true
  try {
    const params = {
      riskLevel: filters.riskLevel || undefined,
      moderationStatus: filters.moderationStatus || undefined,
      merchantId: filters.merchantId ? Number(filters.merchantId) : undefined,
      startTime: filters.startTime || undefined,
      endTime: filters.endTime || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    const response = await getReviewList(params)
    if (response.success && response.data) {
      reviewList.value = response.data.records || []
      pagination.total = response.data.total || 0
      pagination.totalPages = response.data.totalPages || 0
      calculateStats()
    }
  } catch (error) {
    console.error('加载审核列表失败:', error)
    showToast('加载失败，请重试', 'error')
  } finally {
    loading.value = false
  }
}

const calculateStats = () => {
  stats.pending = reviewList.value.filter(item => item.moderationStatus === 'PENDING').length
  stats.highRisk = reviewList.value.filter(item => item.riskLevel === 'HIGH').length
  stats.mediumRisk = reviewList.value.filter(item => item.riskLevel === 'MEDIUM').length
  stats.lowRisk = reviewList.value.filter(item => item.riskLevel === 'LOW').length
}

const loadMerchants = async () => {
  try {
    const response = await getActiveMerchants()
    if (response.success && response.data) {
      merchants.value = response.data
    }
  } catch (error) {
    console.error('加载商家列表失败:', error)
  }
}

const loadPendingCount = async () => {
  try {
    const response = await getPendingCount()
    if (response.success && response.data) {
      stats.pending = response.data
    }
  } catch (error) {
    console.error('加载待审核数量失败:', error)
  }
}

const changePage = (pageNum) => {
  if (pageNum < 1 || pageNum > pagination.totalPages) return
  pagination.pageNum = pageNum
  loadReviewList()
}

const showDetail = async (item) => {
  detailLoading.value = true
  showDetailModal.value = true
  try {
    const response = await getReviewDetail(item.id)
    if (response.success && response.data) {
      currentDetail.value = response.data
    }
  } catch (error) {
    console.error('加载详情失败:', error)
    showToast('加载详情失败', 'error')
    closeDetail()
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => {
  showDetailModal.value = false
  currentDetail.value = null
  reviewRemark.value = ''
}

const handleAction = (item, action) => {
  if (item.moderationStatus !== 'PENDING') {
    showToast('该评价已处理，无法重复操作', 'error')
    return
  }
  actionType.value = action
  actionTarget.value = item
  actionRemark.value = ''
  showActionModal.value = true
}

const closeActionModal = () => {
  showActionModal.value = false
  actionType.value = ''
  actionTarget.value = null
  actionRemark.value = ''
}

const confirmAction = async () => {
  if (!actionTarget.value) return

  const loadingToast = true
  try {
    const response = await moderateReview(actionTarget.value.id, actionType.value, actionRemark.value)
    if (response.success) {
      showToast(getActionText(actionType.value) + '成功')
      closeActionModal()
      closeDetail()
      loadReviewList()
    } else {
      showToast(response.message || '操作失败', 'error')
    }
  } catch (error) {
    console.error('审核操作失败:', error)
    showToast('操作失败，请重试', 'error')
  }
}

onMounted(() => {
  loadMerchants()
  loadPendingCount()
  loadReviewList()
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

.filter-actions {
  flex-direction: row;
  gap: 10px;
}

.btn-reset, .btn-search {
  padding: 10px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid #e5e6eb;
}

.btn-reset {
  background: #f7f8fa;
  color: #4e5969;
}

.btn-reset:hover {
  background: #f0f0f0;
}

.btn-search {
  background: #1890ff;
  color: #fff;
  border-color: #1890ff;
}

.btn-search:hover {
  background: #40a9ff;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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

.stat-card-pending .stat-icon {
  background: linear-gradient(135deg, #fa8c16 0%, #ffa940 100%);
}

.stat-card-high .stat-icon {
  background: linear-gradient(135deg, #f5222d 0%, #ff4d4f 100%);
}

.stat-card-medium .stat-icon {
  background: linear-gradient(135deg, #fa8c16 0%, #ffa940 100%);
}

.stat-card-low .stat-icon {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
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

.table-section {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
  margin: 0;
}

.table-count {
  font-size: 14px;
  color: #8f959e;
}

.table-container {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table thead th {
  padding: 14px 12px;
  text-align: left;
  font-size: 13px;
  font-weight: 600;
  color: #4e5969;
  background: #f7f8fa;
  border-bottom: 2px solid #e5e6eb;
  white-space: nowrap;
}

.data-table tbody td {
  padding: 14px 12px;
  font-size: 14px;
  color: #1f2329;
  border-bottom: 1px solid #f0f0f0;
  white-space: nowrap;
}

.table-row:hover {
  background: #f7f8fa;
  cursor: pointer;
}

.cell-id {
  color: #8f959e;
  font-size: 13px;
}

.cell-content {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rating-star {
  color: #faad14;
  font-size: 14px;
}

.risk-badge, .status-badge {
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.risk-badge.high {
  background: rgba(245, 34, 45, 0.1);
  color: #f5222d;
}

.risk-badge.medium {
  background: rgba(250, 140, 22, 0.1);
  color: #fa8c16;
}

.risk-badge.low {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.status-badge.pending {
  background: rgba(250, 140, 22, 0.1);
  color: #fa8c16;
}

.status-badge.approved {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.status-badge.rejected {
  background: rgba(245, 34, 45, 0.1);
  color: #f5222d;
}

.cell-actions {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.action-btn {
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border: none;
}

.btn-detail {
  background: #f0f0f0;
  color: #4e5969;
}

.btn-detail:hover {
  background: #e5e6eb;
}

.btn-approve {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.btn-approve:hover {
  background: rgba(82, 196, 26, 0.2);
}

.btn-reject {
  background: rgba(250, 140, 22, 0.1);
  color: #fa8c16;
}

.btn-reject:hover {
  background: rgba(250, 140, 22, 0.2);
}

.btn-delete {
  background: rgba(245, 34, 45, 0.1);
  color: #f5222d;
}

.btn-delete:hover {
  background: rgba(245, 34, 45, 0.2);
}

.empty-row {
  background: #f7f8fa;
}

.empty-cell {
  text-align: center;
  padding: 40px;
  color: #8f959e;
}

.pagination-section {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.page-btn {
  padding: 8px 16px;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  font-size: 14px;
  color: #4e5969;
  background: #fff;
  cursor: pointer;
}

.page-btn:hover:not(:disabled) {
  background: #f7f8fa;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 14px;
  color: #8f959e;
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

.modal-container {
  background: #fff;
  border-radius: 12px;
  width: 90%;
  max-width: 700px;
  max-height: 90vh;
  overflow: hidden;
}

.action-modal {
  max-width: 500px;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2329;
  margin: 0;
}

.modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: #f0f0f0;
  border-radius: 6px;
  font-size: 20px;
  color: #8f959e;
  cursor: pointer;
}

.modal-close:hover {
  background: #e5e6eb;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  max-height: 50vh;
}

.loading-state {
  text-align: center;
  padding: 40px;
  color: #8f959e;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-row {
  display: flex;
  gap: 16px;
}

.detail-label {
  width: 120px;
  font-size: 14px;
  font-weight: 500;
  color: #8f959e;
  flex-shrink: 0;
}

.detail-row > span:last-child {
  font-size: 14px;
  color: #1f2329;
  flex: 1;
}

.detail-content-full {
  flex-direction: column;
  gap: 8px;
}

.content-box {
  padding: 16px;
  background: #f7f8fa;
  border-radius: 8px;
  font-size: 14px;
  color: #1f2329;
  line-height: 1.6;
  white-space: pre-wrap;
}

.rating-display {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rating-value {
  font-size: 14px;
  font-weight: 600;
  color: #faad14;
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.rule-item:last-child {
  border-bottom: none;
}

.rule-icon {
  font-size: 16px;
}

.rule-text {
  font-size: 14px;
  color: #4e5969;
}

.modal-footer {
  padding: 20px 24px;
  border-top: 1px solid #f0f0f0;
}

.remark-input {
  width: 100%;
  padding: 12px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  font-size: 14px;
  color: #1f2329;
  resize: none;
  margin-bottom: 16px;
}

.remark-input:focus {
  outline: none;
  border-color: #1890ff;
}

.footer-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.footer-btn {
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: none;
}

.btn-cancel {
  background: #f0f0f0;
  color: #4e5969;
}

.btn-cancel:hover {
  background: #e5e6eb;
}

.btn-confirm {
  color: #fff;
}

.btn-confirm.btn-approve {
  background: #52c41a;
}

.btn-confirm.btn-approve:hover {
  background: #73d13d;
}

.btn-confirm.btn-reject {
  background: #fa8c16;
}

.btn-confirm.btn-reject:hover {
  background: #ffa940;
}

.btn-confirm.btn-delete {
  background: #f5222d;
}

.btn-confirm.btn-delete:hover {
  background: #ff4d4f;
}

.btn-confirm.btn-return_for_modification {
  background: #1890ff;
}

.btn-confirm.btn-return_for_modification:hover {
  background: #40a9ff;
}

.btn-return {
  background: #1890ff;
  color: #fff;
}

.btn-return:hover {
  background: #40a9ff;
}

.action-desc {
  font-size: 14px;
  color: #4e5969;
  line-height: 1.6;
  margin-bottom: 16px;
}

.message-toast {
  position: fixed;
  top: 20px;
  right: 20px;
  padding: 16px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  z-index: 2000;
  animation: fadeIn 0.3s ease;
}

.message-toast.success {
  background: rgba(82, 196, 26, 0.9);
  color: #fff;
}

.message-toast.error {
  background: rgba(245, 34, 45, 0.9);
  color: #fff;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1200px) {
  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .stats-cards {
    grid-template-columns: 1fr;
  }

  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-actions {
    justify-content: flex-end;
  }

  .table-section {
    overflow-x: auto;
  }

  .modal-container {
    width: 95%;
    max-height: 95vh;
  }
}
</style>