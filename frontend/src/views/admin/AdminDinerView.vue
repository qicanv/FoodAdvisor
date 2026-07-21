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
            <option value="INACTIVE">不活跃</option>
            <option value="SUSPENDED">已暂停</option>
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
              <option value="INACTIVE">不活跃</option>
              <option value="SUSPENDED">已暂停</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="saveDiner">{{ isEditing ? '保存修改' : '确认添加' }}</button>
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
const isEditing = ref(false)

const diners = ref([
  { id: 1, username: 'foodie001', nickname: '美食达人小王', phone: '138****0001', email: 'foodie001@example.com', registerTime: '2026-01-15 10:30', lastLoginTime: '2026-07-18 09:15', reviewCount: 23, status: 'ACTIVE' },
  { id: 2, username: 'reviewking', nickname: '评价小王子', phone: '139****0002', email: 'reviewking@example.com', registerTime: '2026-02-20 14:20', lastLoginTime: '2026-07-17 16:45', reviewCount: 56, status: 'ACTIVE' },
  { id: 3, username: 'hungrycat', nickname: '馋嘴猫', phone: '137****0003', email: 'hungrycat@example.com', registerTime: '2026-03-10 09:00', lastLoginTime: '2026-07-16 11:30', reviewCount: 12, status: 'INACTIVE' },
  { id: 4, username: 'dinnerlover', nickname: '晚餐爱好者', phone: '136****0004', email: 'dinnerlover@example.com', registerTime: '2026-04-05 15:45', lastLoginTime: '2026-07-15 20:00', reviewCount: 8, status: 'ACTIVE' },
  { id: 5, username: 'foodcritic', nickname: '美食评论家', phone: '135****0005', email: 'foodcritic@example.com', registerTime: '2026-05-12 11:15', lastLoginTime: '2026-07-10 14:30', reviewCount: 45, status: 'SUSPENDED' },
])

const formData = ref({
  id: null,
  username: '',
  nickname: '',
  phone: '',
  email: '',
  status: 'ACTIVE'
})

const activeCount = computed(() => diners.value.filter(d => d.status === 'ACTIVE').length)
const reviewCount = computed(() => diners.value.reduce((sum, d) => sum + d.reviewCount, 0))

const loadDiners = () => {
}

const getStatusText = (status) => {
  switch (status) {
    case 'ACTIVE': return '活跃'
    case 'INACTIVE': return '不活跃'
    case 'SUSPENDED': return '已暂停'
    default: return status
  }
}

const viewDiner = (diner) => {
  alert(`查看食客: ${diner.nickname}`)
}

const editDiner = (diner) => {
  isEditing.value = true
  formData.value = { ...diner }
  showAddModal.value = true
}

const toggleStatus = (diner) => {
  const newStatus = diner.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'
  if (confirm(`${diner.status === 'ACTIVE' ? '暂停' : '激活'}食客 ${diner.nickname}？`)) {
    diner.status = newStatus
  }
}

const closeModal = () => {
  showAddModal.value = false
  isEditing.value = false
  formData.value = { id: null, username: '', nickname: '', phone: '', email: '', status: 'ACTIVE' }
}

const saveDiner = () => {
  if (!formData.value.username || !formData.value.nickname) {
    alert('请填写用户名和昵称')
    return
  }
  
  if (isEditing.value) {
    const index = diners.value.findIndex(d => d.id === formData.value.id)
    if (index !== -1) {
      diners.value[index] = { ...formData.value }
    }
  } else {
    const newId = Math.max(...diners.value.map(d => d.id), 0) + 1
    diners.value.unshift({
      ...formData.value,
      id: newId,
      registerTime: new Date().toLocaleString(),
      lastLoginTime: '-',
      reviewCount: 0
    })
  }
  
  closeModal()
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

.status-tag.inactive {
  background: #f5f5f5;
  color: #999;
}

.status-tag.suspended {
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

.modal-close {
  background: none;
  border: none;
  color: #999;
  cursor: pointer;
  padding: 4px;
}

.modal-close:hover {
  color: #666;
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
}
</style>