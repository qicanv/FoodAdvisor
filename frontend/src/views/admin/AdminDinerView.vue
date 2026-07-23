<template>
  <AdminLayout title="食客管理" subtitle="管理平台注册用户和食客信息">
    <div class="diners-container">
      <div class="search-bar">
        <div class="search-group">
          <input 
            type="text" 
            v-model="searchKeyword" 
            placeholder="搜索用户名、昵称或手机号" 
            class="search-input"
            @keyup.enter="loadDiners"
          />
        </div>
        <div class="status-filter">
          <select v-model="statusFilter" class="filter-select" @change="loadDiners">
            <option value="">全部状态</option>
            <option value="ACTIVE">活跃</option>
            <option value="DISABLED">不活跃</option>
            <option value="LOCKED">已暂停</option>
          </select>
        </div>
        <button class="add-btn" @click="showAddModal = true">
          <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#fff" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"></line>
            <line x1="5" y1="12" x2="19" y2="12"></line>
          </svg>
          <span>新增食客</span>
        </button>
      </div>

      <div class="stats-row">
        <div class="stat-item">
          <span class="stat-number">{{ diners.length }}</span>
          <span class="stat-label">总食客数</span>
        </div>
        <div class="stat-item">
          <span class="stat-number">{{ activeCount }}</span>
          <span class="stat-label">活跃用户</span>
        </div>
        <div class="stat-item">
          <span class="stat-number">{{ reviewCount }}</span>
          <span class="stat-label">评价总数</span>
        </div>
      </div>

      <div class="table-wrapper">
        <table class="diners-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>用户名</th>
              <th>昵称</th>
              <th>手机号</th>
              <th>邮箱</th>
              <th>注册时间</th>
              <th>最近登录</th>
              <th>评价数</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="diner in diners" :key="diner.id">
              <td>{{ diner.id }}</td>
              <td>{{ diner.username }}</td>
              <td>{{ diner.nickname }}</td>
              <td>{{ diner.phone }}</td>
              <td>{{ diner.email }}</td>
              <td>{{ diner.registerTime }}</td>
              <td>{{ diner.lastLoginTime }}</td>
              <td>{{ diner.reviewCount }}</td>
              <td>
                <span :class="['status-tag', diner.status.toLowerCase()]">{{ getStatusText(diner.status) }}</span>
              </td>
              <td>
                <div class="action-buttons">
                  <button class="action-btn view-btn" @click="viewDiner(diner)">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"></path>
                      <path d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"></path>
                    </svg>
                  </button>
                  <button class="action-btn edit-btn" @click="editDiner(diner)">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                    </svg>
                  </button>
                  <button 
                    :class="['action-btn', diner.status === 'ACTIVE' ? 'suspend-btn' : 'activate-btn']" 
                    @click="toggleStatus(diner)"
                  >
                    <svg v-if="diner.status === 'ACTIVE'" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M12 1v2M12 21v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M1 12h2M21 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"></path>
                    </svg>
                    <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M12 1v2M12 21v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M1 12h2M21 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41"></path>
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>

        <div v-if="diners.length === 0" class="empty-state">
          <svg viewBox="0 0 24 24" width="64" height="64" fill="none" stroke="#ccc" stroke-width="1.5">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
            <circle cx="12" cy="7" r="4"></circle>
          </svg>
          <h3>暂无食客数据</h3>
          <p>当前没有注册的食客用户，点击上方按钮添加新食客</p>
        </div>
      </div>
    </div>

    <div v-if="showDetailModal" class="modal-overlay" @click.self="closeDetailModal">
      <div class="detail-modal-content">
        <div class="detail-modal-header">
          <div class="detail-header-left">
            <div class="avatar">
              <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="#1890ff" stroke-width="1.5">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                <circle cx="12" cy="7" r="4"></circle>
              </svg>
            </div>
            <div class="header-info">
              <h3>{{ selectedDiner?.nickname }}</h3>
              <p class="username">@{{ selectedDiner?.username }}</p>
            </div>
          </div>
          <button class="modal-close" @click="closeDetailModal">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>

        <div class="detail-modal-body">
          <div class="detail-section">
            <h4 class="section-title">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
              </svg>
              基本信息
            </h4>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">用户ID</span>
                <span class="info-value">{{ selectedDiner?.id }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">用户名</span>
                <span class="info-value">{{ selectedDiner?.username }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">昵称</span>
                <span class="info-value">{{ selectedDiner?.nickname }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">手机号</span>
                <span class="info-value">{{ selectedDiner?.phone }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">邮箱</span>
                <span class="info-value">{{ selectedDiner?.email }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">状态</span>
                <span :class="['info-value', 'status', selectedDiner?.status.toLowerCase()]">
                  {{ getStatusText(selectedDiner?.status) }}
                </span>
              </div>
              <div class="info-item">
                <span class="info-label">注册时间</span>
                <span class="info-value">{{ selectedDiner?.createdAt }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">更新时间</span>
                <span class="info-value">{{ selectedDiner?.updatedAt }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">最近登录</span>
                <span class="info-value">{{ selectedDiner?.lastLoginTime }}</span>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <h4 class="section-title">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z"></path>
              </svg>
              统计数据
            </h4>
            <div class="stats-grid">
              <div class="stat-card">
                <div class="stat-icon stat-icon-blue">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
                  </svg>
                </div>
                <div class="stat-content">
                  <span class="stat-value">{{ selectedDiner?.reviewCount || 0 }}</span>
                  <span class="stat-label">评价数量</span>
                </div>
              </div>
              <div class="stat-card">
                <div class="stat-icon stat-icon-green">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                  </svg>
                </div>
                <div class="stat-content">
                  <span class="stat-value">{{ selectedDiner?.avgRating || 0.0 }}</span>
                  <span class="stat-label">平均评分</span>
                </div>
              </div>
              <div class="stat-card">
                <div class="stat-icon stat-icon-orange">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
                    <path d="M9 12l2 2 4-4"></path>
                  </svg>
                </div>
                <div class="stat-content">
                  <span class="stat-value">{{ selectedDiner?.likeCount || 0 }}</span>
                  <span class="stat-label">获赞数量</span>
                </div>
              </div>
              <div class="stat-card">
                <div class="stat-icon stat-icon-purple">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path>
                  </svg>
                </div>
                <div class="stat-content">
                  <span class="stat-value">{{ selectedDiner?.followCount || 0 }}</span>
                  <span class="stat-label">关注商家</span>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <h4 class="section-title">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <polyline points="12 6 12 12 16 14"></polyline>
              </svg>
              最近活动
            </h4>
            <div v-if="selectedDiner?.recentActivities?.length" class="activity-list">
              <div v-for="(activity, index) in selectedDiner.recentActivities" :key="index" class="activity-item">
                <div :class="['activity-icon', activity.type]">
                  {{ getActivityIcon(activity.type) }}
                </div>
                <div class="activity-content">
                  <p class="activity-text">{{ activity.content }}</p>
                  <span class="activity-time">{{ activity.time }}</span>
                </div>
              </div>
            </div>
            <div v-else class="empty-activity">
              <p>暂无活动记录</p>
            </div>
          </div>

          <div class="detail-section">
            <h4 class="section-title">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
              </svg>
              评价统计
            </h4>
            <div class="rating-distribution">
              <div v-for="rating in [5, 4, 3, 2, 1]" :key="rating" class="rating-bar">
                <span class="rating-label">{{ rating }}星</span>
                <div class="rating-bar-container">
                  <div 
                    class="rating-bar-fill" 
                    :style="{ width: getRatingPercent(rating) + '%' }"
                  ></div>
                </div>
                <span class="rating-count">{{ getRatingCount(rating) }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="detail-modal-footer">
          <button class="btn btn-secondary" @click="closeDetailModal">关闭</button>
          <button class="btn btn-primary" @click="editDiner(selectedDiner)">编辑信息</button>
          <button 
            :class="['btn', selectedDiner?.status === 'ACTIVE' ? 'btn-danger' : 'btn-success']" 
            @click="toggleStatus(selectedDiner)"
          >
            {{ selectedDiner?.status === 'ACTIVE' ? '暂停账号' : '激活账号' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="showAddModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal-content">
        <div class="modal-header">
          <h3>{{ isEditing ? '编辑食客' : '新增食客' }}</h3>
          <button class="modal-close" @click="closeModal">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12"></path>
            </svg>
          </button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>用户名</label>
            <input type="text" v-model="formData.username" placeholder="请输入用户名" class="form-input" />
          </div>
          <div class="form-group">
            <label>昵称</label>
            <input type="text" v-model="formData.nickname" placeholder="请输入昵称" class="form-input" />
          </div>
          <div class="form-group">
            <label>手机号</label>
            <input type="text" v-model="formData.phone" placeholder="请输入手机号" class="form-input" />
          </div>
          <div class="form-group">
            <label>邮箱</label>
            <input type="email" v-model="formData.email" placeholder="请输入邮箱" class="form-input" />
          </div>
          <div class="form-group">
            <label>状态</label>
            <select v-model="formData.status" class="form-select">
              <option value="ACTIVE">活跃</option>
              <option value="DISABLED">不活跃</option>
              <option value="LOCKED">已暂停</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="saveDiner">{{ isEditing ? '保存修改' : '确认添加' }}</button>
        </div>
      </div>
    </div>

    <div v-if="showConfirmModal" class="modal-overlay" @click.self="closeConfirmModal">
      <div class="confirm-modal-content">
        <div class="confirm-modal-header">
          <h3>{{ confirmTitle }}</h3>
        </div>
        <div class="confirm-modal-body">
          <div class="confirm-icon">{{ confirmTitle.includes('失败') ? '❌' : confirmTitle.includes('暂停') ? '⚠️' : '✅' }}</div>
          <p v-html="confirmMessage"></p>
        </div>
        <div class="confirm-modal-footer">
          <button v-if="confirmAction" class="btn btn-secondary" @click="closeConfirmModal">取消</button>
          <button 
            v-if="confirmAction" 
            :class="['btn', confirmTitle.includes('暂停') ? 'btn-danger' : 'btn-primary']" 
            @click="confirmAction"
          >
            {{ confirmTitle.includes('暂停') ? '确认暂停' : '确认' }}
          </button>
          <button v-else class="btn btn-primary" @click="closeConfirmModal">确定</button>
        </div>
      </div>
    </div>
  </AdminLayout>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import AdminLayout from '../../components/AdminLayout.vue'

const searchKeyword = ref('')
const statusFilter = ref('')
const showAddModal = ref(false)
const showDetailModal = ref(false)
const showConfirmModal = ref(false)
const isEditing = ref(false)
const selectedDiner = ref(null)
const confirmAction = ref(null)
const confirmTitle = ref('')
const confirmMessage = ref('')
const total = ref(0)
const currentPage = ref(1)
const loading = ref(false)

const diners = ref([])

const formData = ref({
  id: null,
  username: '',
  nickname: '',
  phone: '',
  email: '',
  status: 'ACTIVE'
})

const activeCount = computed(() => diners.value.filter(d => d.status === 'ACTIVE').length)
const reviewCount = computed(() => diners.value.reduce((sum, d) => (d.reviewCount || 0), 0))

const loadDiners = async () => {
  loading.value = true
  try {
    const params = new URLSearchParams()
    params.append('pageNum', currentPage.value)
    params.append('pageSize', 10)
    params.append('role', 'USER')
    if (statusFilter.value) params.append('status', statusFilter.value)
    if (searchKeyword.value) params.append('keyword', searchKeyword.value)

    const response = await fetch(`/api/admin/users?${params}`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || localStorage.getItem('accessToken')}`
      }
    })
    const data = await response.json()

    if (data.code === 'SUCCESS' && data.data) {
      diners.value = data.data.records || []
      total.value = data.data.total || 0
    } else {
      diners.value = []
      total.value = 0
      if (data.code === 'UNAUTHORIZED') {
        openConfirmModal('登录过期', '请重新登录后重试', null)
      }
    }
  } catch (error) {
    console.error('加载食客列表失败:', error)
    diners.value = []
    total.value = 0
    openConfirmModal('加载失败', '加载食客列表时发生错误', null)
  } finally {
    loading.value = false
  }
}

const viewDiner = async (diner) => {
  try {
    const response = await fetch(`/api/admin/users/${diner.id}`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token') || localStorage.getItem('accessToken')}`
      }
    })
    const data = await response.json()

    if (data.code === 'SUCCESS' && data.data) {
      selectedDiner.value = data.data
    } else {
      selectedDiner.value = diner
    }
  } catch (error) {
    console.error('加载食客详情失败:', error)
    selectedDiner.value = diner
  }
  showDetailModal.value = true
}

const getStatusText = (status) => {
  switch (status) {
    case 'ACTIVE': return '活跃'
    case 'DISABLED': return '不活跃'
    case 'LOCKED': return '已暂停'
    default: return status
  }
}

const openConfirmModal = (title, message, action) => {
  confirmTitle.value = title
  confirmMessage.value = message
  confirmAction.value = action
  showConfirmModal.value = true
}

const closeConfirmModal = () => {
  showConfirmModal.value = false
  confirmAction.value = null
}

const getActivityIcon = (type) => {
  const icons = {
    review: '📝',
    like: '👍',
    follow: '👤',
    search: '🔍',
    view: '👀'
  }
  return icons[type] || '📌'
}

const getRatingPercent = (rating) => {
  if (!selectedDiner.value?.ratingDistribution) return 0
  const total = selectedDiner.value.reviewCount || 1
  return Math.round((selectedDiner.value.ratingDistribution[rating] || 0) / total * 100)
}

const getRatingCount = (rating) => {
  if (!selectedDiner.value?.ratingDistribution) return 0
  return selectedDiner.value.ratingDistribution[rating] || 0
}

const editDiner = (diner) => {
  isEditing.value = true
  formData.value = { ...diner }
  showAddModal.value = true
  showDetailModal.value = false
}

const toggleStatus = async (diner) => {
  if (!diner) return
  const newStatus = diner.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE'
  const actionText = diner.status === 'ACTIVE' ? '暂停' : '激活'
  const title = diner.status === 'ACTIVE' ? '确认暂停账号' : '确认激活账号'
  const icon = diner.status === 'ACTIVE' ? '⚠️' : '✅'
  
  openConfirmModal(
    title,
    `${icon} 确定要${actionText}食客「${diner.nickname}」的账号吗？`,
    async () => {
      try {
        const response = await fetch(`/api/admin/users/${diner.id}/status`, {
          method: 'PUT',
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('token') || localStorage.getItem('accessToken')}`,
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ status: newStatus })
        })
        const data = await response.json()
        if (data.code === 'SUCCESS') {
          diner.status = newStatus
          if (showDetailModal.value && selectedDiner.value) {
            selectedDiner.value.status = newStatus
          }
          loadDiners()
        } else {
          openConfirmModal('操作失败', data.message || '操作失败', null)
        }
      } catch (error) {
        console.error('修改状态失败:', error)
        openConfirmModal('操作失败', '修改状态时发生错误', null)
      } finally {
        closeConfirmModal()
      }
    }
  )
}

const closeDetailModal = () => {
  showDetailModal.value = false
  selectedDiner.value = null
}

const closeModal = () => {
  showAddModal.value = false
  isEditing.value = false
  formData.value = { id: null, username: '', nickname: '', phone: '', email: '', status: 'ACTIVE' }
}

const saveDiner = async () => {
  const errors = []
  
  if (!formData.value.username) {
    errors.push('用户名不能为空')
  }
  
  if (!formData.value.nickname) {
    errors.push('昵称不能为空')
  }
  
  if (formData.value.phone && !/^1\d{10}$/.test(formData.value.phone)) {
    errors.push('手机号必须是11位数字')
  }
  
  if (formData.value.email && !formData.value.email.includes('@')) {
    errors.push('邮箱格式不正确，必须包含@')
  }
  
  if (errors.length > 0) {
    openConfirmModal('输入验证失败', errors.join('<br>'), null)
    return
  }
  
  try {
    if (isEditing.value) {
      const response = await fetch(`/api/admin/users/${formData.value.id}/status`, {
        method: 'PUT',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token') || localStorage.getItem('accessToken')}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ status: formData.value.status })
      })
      const data = await response.json()
      if (data.code !== 'SUCCESS') {
        openConfirmModal('操作失败', data.message || '保存失败', null)
        return
      }
    } else {
      const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          username: formData.value.username,
          password: 'Demo@123456',
          confirmPassword: 'Demo@123456',
          nickname: formData.value.nickname,
          email: formData.value.email,
          phone: formData.value.phone,
          role: 'USER'
        })
      })
      const data = await response.json()
      if (data.code !== 'SUCCESS') {
        openConfirmModal('操作失败', data.message || '创建失败', null)
        return
      }
    }
    
    loadDiners()
    closeModal()
  } catch (error) {
    console.error('保存食客失败:', error)
    openConfirmModal('操作失败', '保存食客时发生错误', null)
  }
}

onMounted(() => {
  loadDiners()
})
</script>

<style scoped>
.diners-container {
  width: 100%;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}

.search-group {
  flex: 1;
  min-width: 200px;
  max-width: 400px;
}

.search-input {
  width: 100%;
  padding: 10px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  transition: border-color 0.2s;
}

.search-input:focus {
  outline: none;
  border-color: #1890ff;
}

.status-filter {
  flex-shrink: 0;
}

.filter-select {
  padding: 10px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  background: #fff;
  cursor: pointer;
}

.filter-select:focus {
  outline: none;
  border-color: #1890ff;
}

.add-btn {
  padding: 10px 20px;
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: opacity 0.2s;
}

.add-btn:hover {
  opacity: 0.9;
}

.stats-row {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
}

.stat-item {
  flex: 1;
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.stat-number {
  font-size: 32px;
  font-weight: 700;
  color: #1890ff;
}

.stat-label {
  font-size: 14px;
  color: #667085;
  margin-top: 4px;
}

.table-wrapper {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.diners-table {
  width: 100%;
  border-collapse: collapse;
}

.diners-table th,
.diners-table td {
  padding: 16px 20px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}

.diners-table th {
  background: #fafafa;
  font-weight: 600;
  color: #667085;
  font-size: 14px;
}

.diners-table td {
  font-size: 14px;
  color: #1f2d3d;
}

.status-tag {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-tag.active {
  background: #f6ffed;
  color: #52c41a;
}

.status-tag.disabled {
  background: #f5f5f5;
  color: #999;
}

.status-tag.locked {
  background: #fff2f0;
  color: #ff4d4f;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 6px 10px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.view-btn {
  background: #f0f5ff;
  color: #1890ff;
}

.view-btn:hover {
  background: #d6e4ff;
}

.edit-btn {
  background: #fff7e6;
  color: #fa8c16;
}

.edit-btn:hover {
  background: #ffe5b3;
}

.suspend-btn {
  background: #fff2f0;
  color: #ff4d4f;
}

.suspend-btn:hover {
  background: #ffccc7;
}

.activate-btn {
  background: #f6ffed;
  color: #52c41a;
}

.activate-btn:hover {
  background: #d9f7be;
}

.empty-state {
  text-align: center;
  padding: 60px 20px;
}

.empty-state svg {
  margin-bottom: 16px;
}

.empty-state h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 8px 0;
}

.empty-state p {
  font-size: 14px;
  color: #999;
  margin: 0;
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
  border-radius: 12px;
  width: 90%;
  max-width: 500px;
  overflow: hidden;
}

.detail-modal-content {
  background: #fff;
  border-radius: 16px;
  width: 90%;
  max-width: 800px;
  max-height: 85vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.detail-modal-header {
  padding: 24px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.detail-header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-info h3 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
}

.username {
  margin: 0;
  font-size: 14px;
  color: #999;
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

.detail-modal-body {
  padding: 24px;
  overflow-y: auto;
  flex: 1;
}

.detail-section {
  margin-bottom: 28px;
}

.detail-section:last-child {
  margin-bottom: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0 0 16px 0;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 13px;
  color: #667085;
}

.info-value {
  font-size: 14px;
  color: #1f2d3d;
  font-weight: 500;
}

.info-value.status.active {
  color: #52c41a;
}

.info-value.status.disabled {
  color: #999;
}

.info-value.status.locked {
  color: #ff4d4f;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card {
  background: #fafafa;
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.stat-icon-blue {
  color: #1890ff;
  background: rgba(24, 144, 255, 0.1);
}

.stat-icon-green {
  color: #52c41a;
  background: rgba(82, 196, 26, 0.1);
}

.stat-icon-orange {
  color: #ff6700;
  background: rgba(255, 103, 0, 0.1);
}

.stat-icon-purple {
  color: #722ed1;
  background: rgba(114, 46, 209, 0.1);
}

.stat-content {
  text-align: center;
}

.stat-content .stat-value {
  font-size: 20px;
  font-weight: 700;
  color: #1f2d3d;
}

.stat-content .stat-label {
  font-size: 12px;
  color: #667085;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
}

.activity-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.activity-content {
  flex: 1;
}

.activity-text {
  margin: 0 0 4px 0;
  font-size: 14px;
  color: #1f2d3d;
}

.activity-time {
  font-size: 12px;
  color: #999;
}

.empty-activity {
  text-align: center;
  padding: 20px;
  background: #fafafa;
  border-radius: 8px;
}

.empty-activity p {
  margin: 0;
  font-size: 14px;
  color: #999;
}

.rating-distribution {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rating-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rating-label {
  width: 32px;
  font-size: 13px;
  color: #667085;
}

.rating-bar-container {
  flex: 1;
  height: 12px;
  background: #f0f0f0;
  border-radius: 6px;
  overflow: hidden;
}

.rating-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #1890ff 0%, #40a9ff 100%);
  border-radius: 6px;
  transition: width 0.3s ease;
}

.rating-count {
  width: 32px;
  font-size: 13px;
  color: #667085;
  text-align: right;
}

.detail-modal-footer {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-shrink: 0;
}

.modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
  margin: 0;
}

.modal-body {
  padding: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #1f2d3d;
  margin-bottom: 8px;
}

.form-input,
.form-select {
  width: 100%;
  padding: 10px 16px;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  font-size: 14px;
  box-sizing: border-box;
}

.form-input:focus,
.form-select:focus {
  outline: none;
  border-color: #1890ff;
}

.modal-footer {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.btn {
  padding: 10px 24px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}

.btn-secondary {
  background: #f5f5f5;
  color: #666;
}

.btn-secondary:hover {
  opacity: 0.8;
}

.btn-primary {
  background: linear-gradient(135deg, #1890ff 0%, #40a9ff 100%);
  color: #fff;
}

.btn-primary:hover {
  opacity: 0.9;
}

.btn-danger {
  background: linear-gradient(135deg, #ff4d4f 0%, #ff7875 100%);
  color: #fff;
}

.btn-danger:hover {
  opacity: 0.9;
}

.btn-success {
  background: linear-gradient(135deg, #52c41a 0%, #73d13d 100%);
  color: #fff;
}

.btn-success:hover {
  opacity: 0.9;
}

.confirm-modal-content {
  background: #fff;
  border-radius: 16px;
  width: 90%;
  max-width: 420px;
  overflow: hidden;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
}

.confirm-modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
  text-align: center;
}

.confirm-modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2d3d;
}

.confirm-modal-body {
  padding: 24px;
  text-align: center;
}

.confirm-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.confirm-modal-body p {
  margin: 0;
  font-size: 14px;
  color: #667085;
  line-height: 1.6;
}

.confirm-modal-footer {
  padding: 16px 24px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  justify-content: center;
  gap: 12px;
}

@media (max-width: 768px) {
  .search-bar {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-group {
    max-width: none;
  }
  
  .stats-row {
    flex-direction: column;
  }
  
  .diners-table {
    display: block;
    overflow-x: auto;
  }
  
  .info-grid {
    grid-template-columns: 1fr;
  }
  
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .detail-modal-content {
    width: 95%;
  }
}
</style>