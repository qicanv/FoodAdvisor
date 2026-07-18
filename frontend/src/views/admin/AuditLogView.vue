<template>
  <AdminLayout title="审计日志" subtitle="查看系统操作记录和安全审计信息">
    <div class="logs-container">
      <div class="search-bar">
        <div class="search-item">
          <input 
            type="text" 
            v-model="searchKeyword" 
            placeholder="搜索用户名、操作或资源..."
            class="search-input"
            @keyup.enter="loadLogs"
          />
        </div>
        <div class="search-item">
          <select v-model="searchActorRole" class="search-select" @change="loadLogs">
            <option value="">全部角色</option>
            <option value="ADMIN">管理员</option>
            <option value="DINER">食客</option>
            <option value="MERCHANT">商家</option>
          </select>
        </div>
        <div class="search-item">
          <select v-model="searchAction" class="search-select" @change="loadLogs">
            <option value="">全部操作</option>
            <option value="LOGIN">登录</option>
            <option value="LOGOUT">登出</option>
            <option value="CREATE">创建</option>
            <option value="UPDATE">更新</option>
            <option value="DELETE">删除</option>
            <option value="REPLY_REVIEW">回复评价</option>
          </select>
        </div>
        <button class="search-btn" @click="loadLogs">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"></circle>
            <path d="M21 21l-4.35-4.35"></path>
          </svg>
          <span>搜索</span>
        </button>
      </div>

      <div class="table-container">
        <table class="logs-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>操作人</th>
              <th>角色</th>
              <th>操作</th>
              <th>资源类型</th>
              <th>资源ID</th>
              <th>客户端IP</th>
              <th>详情</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td>{{ formatTime(log.createdAt) }}</td>
              <td>{{ log.actorName || '-' }}</td>
              <td><span :class="['role-tag', log.actorRole?.toLowerCase()]">{{ getRoleText(log.actorRole) }}</span></td>
              <td><span :class="['action-tag', log.action?.toLowerCase()]">{{ getActionText(log.action) }}</span></td>
              <td>{{ log.resourceType || '-' }}</td>
              <td>{{ log.resourceId || '-' }}</td>
              <td>{{ log.clientIp || '-' }}</td>
              <td class="detail-cell">
                <button class="detail-btn" @click="showDetail(log)">查看</button>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="logs.length === 0" class="empty-state">
          <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#ccc" stroke-width="1.5">
            <path d="M4 4h16v16H4z"></path>
            <path d="M8 9h8"></path>
            <path d="M8 13h8"></path>
            <path d="M8 17h5"></path>
          </svg>
          <p>暂无审计日志数据</p>
        </div>
      </div>

      <div class="pagination">
        <button 
          class="pagination-btn" 
          :disabled="currentPage <= 1"
          @click="prevPage"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M15 19l-7-7 7-7"></path>
          </svg>
        </button>
        <span class="pagination-info">第 {{ currentPage }} 页 / 共 {{ totalPages }} 页</span>
        <button 
          class="pagination-btn" 
          :disabled="currentPage >= totalPages"
          @click="nextPage"
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 5l7 7-7 7"></path>
          </svg>
        </button>
      </div>

      <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
        <div class="modal-content">
          <div class="modal-header">
            <h3>日志详情</h3>
            <button class="close-btn" @click="showModal = false">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 6L6 18M6 6l12 12"></path>
              </svg>
            </button>
          </div>
          <div class="modal-body" v-if="selectedLog">
            <div class="detail-row">
              <span class="detail-label">日志ID</span>
              <span class="detail-value">{{ selectedLog.id }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">操作人ID</span>
              <span class="detail-value">{{ selectedLog.actorId || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">操作人名称</span>
              <span class="detail-value">{{ selectedLog.actorName || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">角色</span>
              <span class="detail-value">{{ getRoleText(selectedLog.actorRole) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">操作</span>
              <span class="detail-value">{{ getActionText(selectedLog.action) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">资源类型</span>
              <span class="detail-value">{{ selectedLog.resourceType || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">资源ID</span>
              <span class="detail-value">{{ selectedLog.resourceId || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">资源名称</span>
              <span class="detail-value">{{ selectedLog.resourceName || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">客户端IP</span>
              <span class="detail-value">{{ selectedLog.clientIp || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">用户代理</span>
              <span class="detail-value">{{ selectedLog.userAgent || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">操作结果</span>
              <span :class="['result-tag', selectedLog.success ? 'success' : 'failed']">{{ selectedLog.success ? '成功' : '失败' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">错误信息</span>
              <span class="detail-value">{{ selectedLog.errorMessage || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">创建时间</span>
              <span class="detail-value">{{ formatTime(selectedLog.createdAt) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">变更内容</span>
              <pre class="detail-json">{{ selectedLog.changes || '-' }}</pre>
            </div>
          </div>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'
import { getAdminLogs } from '../../api/adminLogs'

const logs = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const searchKeyword = ref('')
const searchActorRole = ref('')
const searchAction = ref('')

const showModal = ref(false)
const selectedLog = ref(null)

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

const getRoleText = (role) => {
  const map = { ADMIN: '管理员', DINER: '食客', MERCHANT: '商家' }
  return map[role] || role || '-'
}

const getActionText = (action) => {
  const map = {
    LOGIN: '登录',
    LOGOUT: '登出',
    CREATE: '创建',
    UPDATE: '更新',
    DELETE: '删除',
    REPLY_REVIEW: '回复评价',
    UPDATE_PROFILE: '更新资料',
    VIEW_STATS: '查看统计',
  }
  return map[action] || action || '-'
}

const formatTime = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}

const loadLogs = async () => {
  loading.value = true
  try {
    const response = await getAdminLogs({
      pageNum: currentPage.value,
      pageSize: pageSize.value,
      keyword: searchKeyword.value || undefined,
      actorRole: searchActorRole.value || undefined,
      action: searchAction.value || undefined,
    })

    if (response.success) {
      logs.value = response.data?.value || response.data?.records || []
      total.value = response.data?.total || 0
    }
  } catch (error) {
    console.error('加载审计日志失败:', error)
  } finally {
    loading.value = false
  }
}

const prevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value--
    loadLogs()
  }
}

const nextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value++
    loadLogs()
  }
}

const showDetail = (log) => {
  selectedLog.value = log
  showModal.value = true
}

onMounted(() => {
  loadLogs()
})
</script>

<style scoped>
.logs-container {
  padding: 24px;
}

.search-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  padding: 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.search-item {
  flex: 1;
  min-width: 150px;
}

.search-input {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  font-size: 14px;
  transition: all 0.3s;
}

.search-input:focus {
  outline: none;
  border-color: #1890ff;
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.1);
}

.search-select {
  width: 100%;
  padding: 12px 16px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  font-size: 14px;
  background: #fff;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 24 24' width='14' height='14' fill='none' stroke='%23909399' stroke-width='2'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 14px center;
}

.search-select:focus {
  outline: none;
  border-color: #1890ff;
}

.search-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 24px;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s;
}

.search-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

.table-container {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.logs-table {
  width: 100%;
  border-collapse: collapse;
}

.logs-table th,
.logs-table td {
  padding: 16px 20px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}

.logs-table th {
  background: linear-gradient(135deg, #f8f9fa 0%, #f0f2f5 100%);
  font-weight: 600;
  color: #5a6a7a;
  font-size: 14px;
  position: sticky;
  top: 0;
  z-index: 1;
}

.logs-table tr:hover {
  background: #fafafa;
}

.role-tag {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.role-tag.admin {
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

.role-tag.diner {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.role-tag.merchant {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.action-tag {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.action-tag.login {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.action-tag.logout {
  background: rgba(255, 177, 0, 0.1);
  color: #ffb100;
}

.action-tag.create {
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
}

.action-tag.update {
  background: rgba(114, 46, 209, 0.1);
  color: #722ed1;
}

.action-tag.delete {
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

.action-tag.reply_review {
  background: rgba(255, 103, 0, 0.1);
  color: #ff6700;
}

.detail-cell {
  text-align: center;
}

.detail-btn {
  padding: 6px 16px;
  background: rgba(24, 144, 255, 0.1);
  color: #1890ff;
  border: none;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.detail-btn:hover {
  background: rgba(24, 144, 255, 0.2);
}

.empty-state {
  padding: 60px;
  text-align: center;
  color: #909399;
}

.empty-state p {
  margin-top: 12px;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 20px;
  padding: 24px;
  border-top: 1px solid #f0f0f0;
  background: #fafafa;
}

.pagination-btn {
  width: 40px;
  height: 40px;
  border: 2px solid #e8e8e8;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #667085;
  transition: all 0.3s;
}

.pagination-btn:hover:not(:disabled) {
  border-color: #1890ff;
  color: #1890ff;
}

.pagination-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pagination-info {
  font-size: 14px;
  color: #667085;
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
  background: #fff;
  border-radius: 16px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow: hidden;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
}

.modal-header h3 {
  margin: 0;
  font-size: 18px;
}

.close-btn {
  background: none;
  border: none;
  color: #909399;
  cursor: pointer;
  padding: 4px;
}

.close-btn:hover {
  color: #667085;
}

.modal-body {
  padding: 24px;
  overflow-y: auto;
  max-height: 60vh;
}

.detail-row {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid #f5f5f5;
}

.detail-label {
  width: 120px;
  font-weight: 600;
  color: #667085;
  font-size: 14px;
}

.detail-value {
  flex: 1;
  color: #1f2d3d;
  font-size: 14px;
}

.detail-json {
  flex: 1;
  background: #f5f7fa;
  padding: 12px;
  border-radius: 8px;
  font-family: monospace;
  font-size: 12px;
  color: #667085;
  max-height: 150px;
  overflow-y: auto;
}

.result-tag {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.result-tag.success {
  background: rgba(82, 196, 26, 0.1);
  color: #52c41a;
}

.result-tag.failed {
  background: rgba(255, 77, 79, 0.1);
  color: #ff4d4f;
}

@media (max-width: 768px) {
  .search-bar {
    flex-direction: column;
  }

  .logs-table {
    font-size: 12px;
  }

  .logs-table th,
  .logs-table td {
    padding: 12px 8px;
  }
}
</style>