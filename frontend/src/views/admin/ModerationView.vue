<template>
  <AdminLayout title="内容审核工作台" subtitle="集中查看和处理待审核内容 — 含违规文本识别与审核操作">
    <div class="filter-section">
      <div class="filter-row">
        <div class="filter-item">
          <label>风险类型</label>
          <select v-model="filters.riskType" class="filter-select" @change="loadReviewList">
            <option value="">全部</option>
            <option value="AD_SPAM">广告引流</option>
            <option value="ABUSE">恶意谩骂</option>
            <option value="FALSE_AD">虚假宣传</option>
            <option value="SPAM">无关灌水</option>
            <option value="OTHER">其他违规</option>
          </select>
        </div>
        <div class="filter-item">
          <label>风险等级</label>
          <select v-model="filters.riskLevel" class="filter-select" @change="handleFilterChange">
            <option value="">全部</option>
            <option value="HIGH">高风险</option>
            <option value="MEDIUM">中风险</option>
          </select>
        </div>
        <div class="filter-item">
          <label>处理状态</label>
          <select v-model="filters.moderationStatus" class="filter-select" @change="handleFilterChange">
            <option value="">全部</option>
            <option value="PENDING">待审核</option>
            <option value="APPROVED">已通过</option>
            <option value="REJECTED">已驳回</option>
          </select>
        </div>
        <div class="filter-item">
          <label>商家</label>
          <select v-model="filters.merchantId" class="filter-select" @change="handleFilterChange">
            <option value="">全部商家</option>
            <option v-for="merchant in merchants" :key="merchant.id" :value="merchant.id">
              {{ merchant.name }}
            </option>
          </select>
        </div>
        <div class="filter-item">
          <label>开始时间</label>
          <input type="datetime-local" v-model="filters.startTime" class="filter-input" @change="handleFilterChange" />
        </div>
        <div class="filter-item">
          <label>结束时间</label>
          <input type="datetime-local" v-model="filters.endTime" class="filter-input" @change="handleFilterChange" />
        </div>
        <div class="filter-item filter-actions">
          <button class="btn-reset" @click="resetFilters">重置</button>
          <button class="btn-search" @click="loadReviewList">查询</button>
        </div>
      </div>
    </div>

    <!-- 统计卡片：内容审核 + 违规检测 -->
    <div class="stats-cards">
    <div class="stat-cards">
      <div class="stat-card stat-card-pending">
        <div class="stat-icon">⏳</div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.pending }}</div>
          <div class="stat-label">待审核</div>
        </div>
      </div>
      <div class="stat-card stat-card-blocked">
        <div class="stat-icon">🚫</div>
        <div class="stat-info">
          <div class="stat-value">{{ vStats.highBlocked }}</div>
          <div class="stat-label">已拦截（30天）</div>
        </div>
      </div>
      <div class="stat-card stat-card-ai">
        <div class="stat-icon">🤖</div>
        <div class="stat-info">
          <div class="stat-value">{{ vStats.aiSuccess }}</div>
          <div class="stat-label">AI 检测（30天）</div>
        </div>
      </div>
      <div class="stat-card stat-card-fallback-warn">
        <div class="stat-icon">⚠️</div>
        <div class="stat-info">
          <div class="stat-value">{{ vStats.fallback }}</div>
          <div class="stat-label">降级检测（30天）</div>
        </div>
      </div>
    </div>
    </div>

    <!-- 违规类型分布 -->
    <div class="violation-summary" v-if="vStats.totalDetections > 0">
      <span class="violation-summary-title">近30天违规类型：</span>
      <span v-for="t in vStats.riskTypes" :key="t.riskType"
        :class="['violation-tag', 'tag-' + t.riskType.toLowerCase()]">
        {{ t.name }} {{ t.count }}
      </span>
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
              <th>审核人员</th>
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
                <span :class="['status-badge', modStatus(item)?.toLowerCase()]">
                  {{ getModerationStatusText(modStatus(item)) }}
                </span>
              </td>
              <td class="cell-operator">{{ item.moderationOperator || '尚未审核' }}</td>
              <td class="cell-merchant">{{ item.merchantName || '-' }}</td>
              <td class="cell-user">{{ item.userNickname || item.username || '-' }}</td>
              <td class="cell-time">{{ formatTime(item.createdAt) }}</td>
              <td class="cell-actions">
                <button class="action-btn btn-detail" @click.stop="showDetail(item)">详情</button>
                <button v-if="item.moderationStatus === 'PENDING'" class="action-btn btn-approve" @click.stop="handleAction(item, 'APPROVE')">通过</button>
                <button v-if="item.moderationStatus === 'PENDING'" class="action-btn btn-reject" @click.stop="handleAction(item, 'REJECT')">驳回</button>
                <button v-if="item.moderationStatus === 'APPROVED'" class="action-btn btn-undo-approve" @click.stop="handleAction(item, 'UNDO_APPROVE')">撤销通过</button>
                <button v-if="item.moderationStatus === 'REJECTED'" class="action-btn btn-undo-reject" @click.stop="handleAction(item, 'UNDO_REJECT')">撤销驳回</button>
              </td>
            </tr>
            <tr v-if="reviewList.length === 0" class="empty-row">
              <td colspan="10" class="empty-cell">暂无数据</td>
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
            <!-- PENDING 状态：操作栏放在最显眼的位置 -->
            <div v-if="modStatus(currentDetail) === 'PENDING'" class="detail-action-bar">
              <span class="action-bar-title">⚠️ 待审核 — 请选择操作：</span>
              <div class="action-bar-buttons">
                <button class="footer-btn btn-approve" @click="handleAction(currentDetail, 'APPROVE')">✓ 通过</button>
                <button class="footer-btn btn-reject" @click="handleAction(currentDetail, 'REJECT')">✗ 驳回</button>
                <button class="footer-btn btn-delete" @click="handleAction(currentDetail, 'DELETE')">🗑 删除</button>
                <button class="footer-btn btn-return" @click="handleAction(currentDetail, 'RETURN_FOR_MODIFICATION')">↩ 退回修改</button>
              </div>
              <textarea v-model="reviewRemark" class="remark-inline" placeholder="审核备注（可选）" rows="1"></textarea>
            </div>
            <div class="detail-row">
              <span class="detail-label">风险等级</span>
              <span :class="['risk-badge', currentDetail.riskLevel?.toLowerCase()]">
                {{ getRiskLevelText(currentDetail.riskLevel) }}
              </span>
            </div>
            <div class="detail-row">
              <span class="detail-label">审核状态</span>
              <span :class="['status-badge', modStatus(currentDetail)?.toLowerCase()]">
                {{ getModerationStatusText(modStatus(currentDetail)) }}
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
              <span class="detail-label">审核人员</span>
              <span>{{ currentDetail.moderationOperator || '尚未审核' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">提交时间</span>
              <span>{{ formatTime(currentDetail.createdAt) }}</span>
            </div>
            <div class="detail-row detail-content-full">
              <span class="detail-label">原始内容</span>
              <div class="content-box">{{ currentDetail.content || '-' }}</div>
            </div>
            <!-- 违规检测详情（EPIC-03 故事3） -->
            <div class="detail-row detail-content-full">
              <span class="detail-label">违规检测</span>
              <div class="content-box">
                <div v-if="riskRecords.length > 0">
                  <div v-for="(record, idx) in riskRecords" :key="record.id" class="risk-record">
                    <div class="risk-record-header">
                      <span class="risk-record-ver">检测 #{{ riskRecords.length - idx }}</span>
                      <span :class="['detection-badge', 'detection-' + (record.detectionStatus || '').toLowerCase()]">
                        {{ detectionStatusText(record.detectionStatus) }}
                      </span>
                    </div>
                    <div class="risk-record-body">
                      <div class="risk-record-row">
                        <span class="risk-record-label">风险类型</span>
                        <span :class="['risk-type-tag', 'risk-type-' + (record.riskType || 'none').toLowerCase()]">
                          {{ riskTypeText(record.riskType) }}
                        </span>
                      </div>
                      <div class="risk-record-row">
                        <span class="risk-record-label">风险分值</span>
                        <span class="risk-score-bar-wrapper">
                          <span class="risk-score-bar" :style="{ width: (record.riskScore || 0) + '%' }"
                            :class="'score-' + (record.riskLevel || 'low').toLowerCase()"></span>
                        </span>
                        <span class="risk-score-value">{{ record.riskScore || 0 }} / 100</span>
                      </div>
                      <div class="risk-record-row">
                        <span class="risk-record-label">检测方式</span>
                        <span>{{ record.detectionStatus === 'FALLBACK' ? '关键词匹配（AI 服务不可用）' : 'AI 大模型检测' }}</span>
                      </div>
                      <div class="risk-record-row" v-if="record.modelName">
                        <span class="risk-record-label">模型</span>
                        <span>{{ record.modelName }}</span>
                      </div>
                      <div class="risk-record-row" v-if="record.ruleVersion">
                        <span class="risk-record-label">规则版本</span>
                        <span>{{ record.ruleVersion }}</span>
                      </div>
                      <div v-if="hasMatchedRules(record)" class="matched-rules">
                        <div class="risk-record-label" style="margin-bottom: 6px">命中规则</div>
                        <div v-for="(rule, ri) in parseMatchedRules(record.matchedRules)" :key="ri" class="matched-rule-item">
                          <div class="matched-rule-header">
                            <span class="rule-confidence">{{ Math.round(rule.confidence * 100) }}%</span>
                            <span class="rule-code">{{ rule.ruleCode }}</span>
                          </div>
                          <div class="rule-name">{{ rule.ruleName }}</div>
                          <div v-if="rule.evidenceExcerpt" class="rule-evidence">
                            <span class="evidence-label">原文片段：</span>
                            <span class="evidence-text">"{{ rule.evidenceExcerpt }}"</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <div v-if="currentDetail.matchedRules && currentDetail.matchedRules.length > 0" class="rules-container">
                  <div v-for="(rule, index) in currentDetail.matchedRules" :key="index" class="rule-item">
                    <span :class="['rule-icon', getRiskLevelClass(rule.riskLevel)]">{{ getRiskLevelIcon(rule.riskLevel) }}</span>
                    <div class="rule-info">
                      <div class="rule-name">{{ rule.ruleName }}</div>
                      <div class="rule-desc">{{ rule.description }}</div>
                      <div v-if="rule.keyword" class="rule-keyword">
                        <span class="keyword-label">匹配关键词：</span>
                        <span class="keyword-value">{{ rule.keyword }}</span>
                      </div>
                    </div>
                    <span :class="['risk-badge', (rule.riskLevel || '').toLowerCase()]">
                      {{ getRiskLevelText(rule.riskLevel) }}
                    </span>
                  </div>
                </div>
                <div v-else class="rule-item">
                  <span class="rule-icon">✅</span>
                  <span class="rule-text">
                    暂无违规检测记录
                    <template v-if="currentDetail.riskLevel === 'LOW'">（系统判定为低风险，自动通过）</template>
                    <template v-else-if="currentDetail.riskLevel">（风险等级：{{ getRiskLevelText(currentDetail.riskLevel) }}）</template>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="currentDetail && (currentDetail.moderationStatus === 'PENDING' || currentDetail.moderationStatus === 'APPROVED')" class="modal-footer">
          <textarea v-model="reviewRemark" class="remark-input" placeholder="请输入审核备注（可选）" rows="3"></textarea>
          <div class="footer-actions">
            <button v-if="currentDetail.moderationStatus === 'PENDING'" class="footer-btn btn-approve" @click="handleAction(currentDetail, 'APPROVE')">通过</button>
            <button v-if="currentDetail.moderationStatus === 'APPROVED'" class="footer-btn btn-undo-approve" @click="handleAction(currentDetail, 'UNDO_APPROVE')">撤销通过</button>
            <button v-if="currentDetail.moderationStatus === 'PENDING'" class="footer-btn btn-reject" @click="handleAction(currentDetail, 'REJECT')">驳回</button>
            <button v-if="currentDetail.moderationStatus === 'REJECTED'" class="footer-btn btn-undo-reject" @click="handleAction(currentDetail, 'UNDO_REJECT')">撤销驳回</button>
            <button v-if="currentDetail.moderationStatus === 'PENDING'" class="footer-btn btn-return" @click="handleAction(currentDetail, 'RETURN_FOR_MODIFICATION')">退回修改</button>
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
import { getReviewList, getReviewDetail, getActiveMerchants, getPendingCount, moderateReview, getStats } from '../../api/moderation'
import { getRiskRecords, getViolationStats } from '../../api/violationText'

const reviewList = ref([])
const merchants = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const currentDetail = ref(null)
const riskRecords = ref([])
const showDetailModal = ref(false)
const showActionModal = ref(false)
const showMessage = ref(false)
const messageText = ref('')
const messageType = ref('success')
const reviewRemark = ref('')
const actionRemark = ref('')
const actionType = ref('')
const actionTarget = ref(null)

const violationRaw = ref({})

const vStats = computed(() => {
  const raw = violationRaw.value
  const byLevel = raw.byRiskLevel || []
  const byStatus = raw.byDetectionStatus || []
  const byType = (raw.byRiskType || []).filter(t => t.count > 0)

  return {
    totalDetections: raw.totalDetections || 0,
    highBlocked: byLevel.find(l => l.riskLevel === 'HIGH')?.count || 0,
    aiSuccess: byStatus.find(s => s.status === 'SUCCESS')?.count || 0,
    fallback: byStatus.find(s => s.status === 'FALLBACK')?.count || 0,
    riskTypes: byType
  }
})

const filters = reactive({
  riskType: '',
  riskLevel: '',
  moderationStatus: 'PENDING',
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
  mediumRisk: 0
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

const getRiskLevelIcon = (level) => {
  const map = { HIGH: '🔴', MEDIUM: '🟡', LOW: '🟢' }
  return map[level] || '⚪'
}

const getRiskLevelClass = (level) => {
  const map = { HIGH: 'risk-high', MEDIUM: 'risk-medium', LOW: 'risk-low' }
  return map[level] || 'risk-default'
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

// moderationStatus 可能以 moderationStatus 或 moderation_status 返回
const modStatus = (item) => {
  if (!item) return ''
  return item.moderationStatus || item.moderation_status || ''
}
const isPending = (item) => modStatus(item) === 'PENDING'

const getActionTitle = (type) => {
  const map = { 
    APPROVE: '确认通过', 
    REJECT: '确认驳回', 
    DELETE: '确认删除', 
    RETURN_FOR_MODIFICATION: '确认退回修改', 
    UNDO_APPROVE: '确认撤销通过',
    UNDO_REJECT: '确认撤销驳回'
  }
  return map[type] || '确认操作'
}

const getActionDesc = (type) => {
  const map = {
    APPROVE: '通过后该评价将进入已发布状态，用户可正常查看。',
    REJECT: '驳回后该评价将被隐藏，用户不可查看。',
    DELETE: '删除后该评价将被永久移除，请谨慎操作。',
    RETURN_FOR_MODIFICATION: '退回后用户可修改评价内容后重新提交。',
    UNDO_APPROVE: '撤销通过后该评价将回到待审核状态，需重新审核。',
    UNDO_REJECT: '撤销驳回后该评价将回到待审核状态，需重新审核。'
  }
  return map[type] || ''
}

const getActionText = (type) => {
  const map = { 
    APPROVE: '通过', 
    REJECT: '驳回', 
    DELETE: '删除', 
    RETURN_FOR_MODIFICATION: '退回修改', 
    UNDO_APPROVE: '撤销通过',
    UNDO_REJECT: '撤销驳回'
  }
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

// ==================== 违规检测详情辅助方法 ====================

const riskTypeText = (type) => {
  const map = {
    AD_SPAM: '广告引流',
    ABUSE: '恶意谩骂',
    FALSE_AD: '虚假宣传',
    SPAM: '无关灌水',
    OTHER: '其他违规'
  }
  return map[type] || (type || '无')
}

const detectionStatusText = (status) => {
  const map = {
    SUCCESS: 'AI 检测',
    FALLBACK: '关键词降级',
    ERROR: '检测异常',
    TIMEOUT: '检测超时'
  }
  return map[status] || (status || '未知')
}

const hasMatchedRules = (record) => {
  if (!record.matchedRules) return false
  if (typeof record.matchedRules === 'string') {
    try {
      const parsed = JSON.parse(record.matchedRules)
      return Array.isArray(parsed) && parsed.length > 0
    } catch {
      return false
    }
  }
  if (Array.isArray(record.matchedRules)) {
    return record.matchedRules.length > 0
  }
  return false
}

const parseMatchedRules = (matchedRules) => {
  if (!matchedRules) return []
  if (typeof matchedRules === 'string') {
    try {
      return JSON.parse(matchedRules)
    } catch {
      return []
    }
  }
  if (Array.isArray(matchedRules)) return matchedRules
  return []
}

const resetFilters = () => {
  filters.riskType = ''
  filters.riskLevel = ''
  filters.moderationStatus = 'PENDING'
  filters.merchantId = ''
  filters.startTime = ''
  filters.endTime = ''
  pagination.pageNum = 1
  loadReviewList()
}

const loadViolationStats = async () => {
  try {
    const res = await getViolationStats()
    if (res.data) {
      violationRaw.value = res.data
    }
  } catch (e) {
    console.error('加载违规统计失败:', e)
  }
}

const handleFilterChange = () => {
  pagination.pageNum = 1
  loadReviewList()
}

const formatDateTime = (dateTimeStr) => {
  if (!dateTimeStr) return undefined
  return dateTimeStr + ':00'
}

const loadReviewList = async () => {
  loading.value = true
  try {
    const params = {}
    if (filters.riskLevel) params.riskLevel = filters.riskLevel
    if (filters.moderationStatus) params.moderationStatus = filters.moderationStatus
    if (filters.merchantId) params.merchantId = Number(filters.merchantId)
    if (filters.startTime) params.startTime = formatDateTime(filters.startTime)
    if (filters.endTime) params.endTime = formatDateTime(filters.endTime)
    params.pageNum = pagination.pageNum
    params.pageSize = pagination.pageSize
    
    console.log('Loading review list with params:', params)
    
    const response = await getReviewList(params)
    if (response.success && response.data) {
      reviewList.value = []
      reviewList.value = response.data.records || []
      pagination.total = response.data.total || 0
      pagination.totalPages = response.data.totalPages || 0
      await calculateStats()
    }
  } catch (error) {
    console.error('加载审核列表失败:', error)
    showToast('加载失败，请重试', 'error')
  } finally {
    loading.value = false
  }
}

const calculateStats = async () => {
  try {
    const response = await getStats()
    if (response.success && response.data) {
      stats.pending = response.data.pending || 0
      stats.highRisk = response.data.highRisk || 0
      stats.mediumRisk = response.data.mediumRisk || 0
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
    stats.pending = reviewList.value.filter(item => item.moderationStatus === 'PENDING').length
    stats.highRisk = reviewList.value.filter(item => item.riskLevel === 'HIGH').length
    stats.mediumRisk = reviewList.value.filter(item => item.riskLevel === 'MEDIUM').length
  }
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
  riskRecords.value = []
  try {
    const response = await getReviewDetail(item.id)
    if (response.success && response.data) {
      currentDetail.value = response.data
    }
    // 同时加载违规检测记录
    try {
      const riskRes = await getRiskRecords('REVIEW', item.id)
      if (riskRes.success && riskRes.data) {
        riskRecords.value = riskRes.data
      }
    } catch (e) {
      console.error('加载违规检测记录失败:', e)
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
  const status = item.moderationStatus
  
  if (status === 'APPROVED' && action === 'UNDO_APPROVE') {
    actionType.value = action
    actionTarget.value = item
    actionRemark.value = ''
    showActionModal.value = true
    return
  }
  
  if (status === 'REJECTED' && action === 'UNDO_REJECT') {
    actionType.value = action
    actionTarget.value = item
    actionRemark.value = ''
    showActionModal.value = true
    return
  }
  
  if (status === 'PENDING') {
    actionType.value = action
    actionTarget.value = item
    actionRemark.value = ''
    showActionModal.value = true
    return
  }
  
  showToast('该评价已处理，无法重复操作', 'error')
}

const closeActionModal = () => {
  showActionModal.value = false
  actionType.value = ''
  actionTarget.value = null
  actionRemark.value = ''
}

const confirmAction = async () => {
  if (!actionTarget.value) return

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
    if (error.response && error.response.status === 403) {
      showToast('您没有权限执行此操作', 'error')
    } else {
      showToast('操作失败，请重试', 'error')
    }
  }
}

onMounted(() => {
  loadMerchants()
  loadPendingCount()
  loadReviewList()
  loadViolationStats()
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

.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.stat-card-pending {
  background: linear-gradient(135deg, #fff7e6 0%, #fff1cc 100%);
}

.stat-card-pending .stat-icon {
  background: linear-gradient(135deg, #fa8c16 0%, #ffa940 100%);
}

.stat-card-high {
  background: linear-gradient(135deg, #fff2f0 0%, #ffccc7 100%);
}

.stat-card-high .stat-icon {
  background: linear-gradient(135deg, #f5222d 0%, #ff4d4f 100%);
}

.stat-card-medium {
  background: linear-gradient(135deg, #fffbe6 0%, #fff1b8 100%);
}

.stat-card-medium .stat-icon {
  background: linear-gradient(135deg, #faad14 0%, #ffc53d 100%);
}

.stat-icon {
  width: 44px;
  height: 44px;
  border-radius: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2329;
}

.stat-label {
  font-size: 14px;
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

/* 违规统计卡片 */
.stat-card-blocked { border-left: 4px solid #f5222d; }
.stat-card-ai { border-left: 4px solid #52c41a; }
.stat-card-fallback-warn { border-left: 4px solid #fa8c16; }

/* 违规类型摘要 */
.violation-summary {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 16px;
  margin-bottom: 16px;
  background: #fafafa;
  border-radius: 8px;
  font-size: 13px;
}
.violation-summary-title {
  color: #666;
  font-weight: 500;
}
.violation-tag {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}
.tag-ad_spam { background: #fff1f0; color: #cf1322; }
.tag-abuse { background: #fff7e6; color: #d46b08; }
.tag-false_ad { background: #fff0f6; color: #c41d7f; }
.tag-spam { background: #f6ffed; color: #389e0d; }
.tag-other { background: #f5f5f5; color: #595959; }

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

.btn-undo-approve {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.btn-undo-approve:hover {
  background: rgba(24, 144, 255, 0.2);
}

.btn-undo-reject {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.btn-undo-reject:hover {
  background: rgba(24, 144, 255, 0.2);
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

.rules-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rule-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e6eb;
}

.rule-icon {
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 2px;
}

.rule-icon.risk-high {
  color: #f5222d;
}

.rule-icon.risk-medium {
  color: #fa8c16;
}

.rule-icon.risk-low {
  color: #52c41a;
}

.rule-icon.risk-default {
  color: #8f959e;
}

.rule-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.rule-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}

.rule-desc {
  font-size: 13px;
  color: #8f959e;
}

.rule-keyword {
  display: flex;
  align-items: center;
  gap: 4px;
}

.keyword-label {
  font-size: 12px;
  color: #8f959e;
}

.keyword-value {
  font-size: 12px;
  color: #f5222d;
  font-weight: 500;
  background: rgba(245, 34, 45, 0.1);
  padding: 2px 8px;
  border-radius: 4px;
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

.btn-confirm.btn-undo_approve {
  background: #1890ff;
}

.btn-confirm.btn-undo_approve:hover {
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

@media (max-width: 768px) {
  .stat-cards {
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

/* 列表已处理标签 */
.action-done {
  font-size: 12px;
  color: #bbb;
  padding: 4px 8px;
}

/* 详情页操作栏 */
.detail-action-bar {
  background: linear-gradient(135deg, #fff7e6, #fffbe6);
  border: 2px solid #ffd591;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 20px;
}
.action-bar-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #d46b08;
  margin-bottom: 12px;
}
.action-bar-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.remark-inline {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  font-size: 13px;
  resize: none;
  box-sizing: border-box;
}

/* ==================== 违规检测详情样式 ==================== */

.risk-record {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  margin-bottom: 12px;
  overflow: hidden;
}
.risk-record:last-child { margin-bottom: 0; }
.risk-record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 14px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
}
.risk-record-ver {
  font-size: 12px;
  color: #888;
  font-weight: 500;
}
.detection-badge {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
}
.detection-success { background: #f6ffed; color: #52c41a; }
.detection-fallback { background: #fff7e6; color: #fa8c16; }
.detection-error, .detection-timeout { background: #fff1f0; color: #f5222d; }

.risk-record-body {
  padding: 12px 14px;
}
.risk-record-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
}
.risk-record-row:last-child { margin-bottom: 0; }
.risk-record-label {
  width: 70px;
  flex-shrink: 0;
  color: #888;
  font-size: 12px;
}

.risk-type-tag {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
}
.risk-type-ad_spam { background: #fff1f0; color: #cf1322; }
.risk-type-abuse { background: #fff7e6; color: #d46b08; }
.risk-type-false_ad { background: #fff0f6; color: #c41d7f; }
.risk-type-spam { background: #f6ffed; color: #389e0d; }
.risk-type-other { background: #f5f5f5; color: #595959; }
.risk-type-none { background: #f6ffed; color: #52c41a; }

.risk-score-bar-wrapper {
  flex: 1;
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
}
.risk-score-bar {
  height: 100%;
  border-radius: 4px;
  transition: width 0.5s ease;
}
.score-low { background: linear-gradient(90deg, #52c41a, #95de64); }
.score-medium { background: linear-gradient(90deg, #fa8c16, #ffc069); }
.score-high { background: linear-gradient(90deg, #f5222d, #ff7875); }
.risk-score-value {
  width: 60px;
  flex-shrink: 0;
  text-align: right;
  font-weight: 600;
  font-size: 13px;
}

.matched-rules {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e8e8e8;
}
.matched-rule-item {
  padding: 8px 10px;
  margin-bottom: 6px;
  background: #fafafa;
  border-radius: 6px;
  border-left: 3px solid #667eea;
}
.matched-rule-item:last-child { margin-bottom: 0; }
.matched-rule-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.rule-confidence {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  background: #f0f5ff;
  color: #2f54eb;
}
.rule-code {
  font-size: 12px;
  color: #666;
  font-family: monospace;
}
.rule-name {
  font-size: 13px;
  color: #333;
  margin-bottom: 2px;
}
.rule-evidence {
  font-size: 12px;
  color: #888;
  margin-top: 4px;
  padding: 4px 8px;
  background: #fffbe6;
  border-radius: 4px;
}
.evidence-label { color: #d46b08; }
.evidence-text {
  color: #d48806;
  font-style: italic;
}
</style>